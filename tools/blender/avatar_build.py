# -*- coding: utf-8 -*-
"""
avatar_build.py  –  Blender-Werkzeugkette des Avatars (Phase 1)

Baut aus MPFB2 den Grundkoerper des Avatars und exportiert ihn als glTF 2.0
binaer (.glb), dazu ein Protokoll (JSON) mit Ecken, Dreiecken, Knochen,
Morphzielen, Quellen, Lizenzen und der SHA-256-Pruefsumme.

Ohne Fenster:
    blender -b -P tools/blender/avatar_build.py -- [--out DIR] [--name NAME] [--twice]

Ueber die Blender-Verbindung: exec(open(<pfad>).read()) mit sys.argv gesetzt,
oder besser als Unterprozess (so bleibt die offene Szene unberuehrt).

  --out DIR    Zielordner (Vorgabe: <Projekt>/src/com/dan/avatar/res)
  --name NAME  Dateiname ohne Endung (Vorgabe: avatar_base)
  --twice      baut danach ein zweites Mal in einem frischen Blender-Prozess
               und vergleicht die Pruefsummen (Abnahme Phase 1)
  --vorschau   rendert die Posen mit Workbench nach <out>/_vorschau (zum Ansehen)

Was hinein geht (F1–F5, F24):
  * Grundnetz MPFB2 in Metern, Fuesse auf dem Boden, Hilfsgeometrie entfernt
  * Makro-Regler (Geschlecht, Alter, Muskeln, Gewicht, Groesse, Proportionen,
    Brust, Festigkeit) als Morphziele "macro.<regler>.min" / ".max":
    linearisiert um die Mitte (0,5) – Differenz zur Mitte bei 0 und bei 1
  * rund 30 Gesichtsregler als Paare "face.<regler>.decr/.incr" (links und
    rechts zu einem Ziel zusammengefasst)
  * Gesichtsformen: ARKit 52 aus faceunits01 und 15 Viseme aus visemes02,
    wenn installiert; sonst die 34 alten MakeHuman-Ausdrucks-Units als
    Platzhalter ("expr.<name>")
  * Rig "game_engine" mit Gewichten (4 Einfluesse je Ecke)
  * ohne Tangenten: Blender rechnet sie nicht bitgleich (3 von 53 520 Werten
    wichen ab); der Lader (Phase 2) rechnet sie selbst
  * Augen, Zaehne, Zunge, Brauen, Wimpern, eine Frisur, ein Outfit: wenn die
    Pakete installiert sind (sonst im Protokoll als fehlend gemeldet)

Gleiche Eingaben -> gleiche Datei. Das Skript nutzt keinen Zufall.
"""

import bpy, bmesh, sys, os, json, hashlib, time, importlib, subprocess, gzip, math

VERSION = "1.7"
T0 = time.time()

# ---------------------------------------------------------------- Argumente
def parse_args():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    here = os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else os.getcwd()
    project = os.path.abspath(os.path.join(here, "..", ".."))
    a = {"out": os.path.join(project, "src", "com", "dan", "avatar", "res"),
         "name": "avatar_base", "twice": False, "vorschau": False, "script": os.path.join(here, "avatar_build.py")}
    i = 0
    while i < len(argv):
        k = argv[i]
        if k == "--out": a["out"] = argv[i + 1]; i += 2
        elif k == "--name": a["name"] = argv[i + 1]; i += 2
        elif k == "--twice": a["twice"] = True; i += 1
        elif k == "--vorschau": a["vorschau"] = True; i += 1
        else: i += 1
    return a

ARGS = parse_args()
LOG = []
def log(msg):
    line = "[%6.1f s] %s" % (time.time() - T0, msg)
    LOG.append(line)
    print("avatar_build: " + line, flush=True)

# ---------------------------------------------------------------- MPFB finden
def find_mpfb():
    """Nimmt die MPFB-Installation, die die meisten Pakete kennt."""
    best = None
    for base in ("bl_ext.blender_org.mpfb", "bl_ext.user_default.mpfb", "mpfb"):
        try:
            importlib.import_module(base)
            A = importlib.import_module(base + ".services.assetservice").AssetService
            packs = A.get_pack_names()
        except Exception:
            continue
        if best is None or len(packs) > len(best[1]):
            best = (base, packs)
    if best is None:
        raise RuntimeError("MPFB2 ist nicht installiert oder nicht aktiv.")
    return best

MPFB, PACKS = find_mpfb()
def svc(mod, cls):
    return getattr(importlib.import_module(MPFB + "." + mod), cls)

HumanService = svc("services.humanservice", "HumanService")
TargetService = svc("services.targetservice", "TargetService")
AssetService = svc("services.assetservice", "AssetService")
LocationService = svc("services.locationservice", "LocationService")
HOP = svc("entities.objectproperties", "HumanObjectProperties")

def mpfb_version():
    try:
        m = importlib.import_module(MPFB)
        v = getattr(m, "bl_info", {}).get("version")
        if v: return ".".join(str(x) for x in v)
    except Exception:
        pass
    try:
        import addon_utils
        for m in addon_utils.modules():
            if m.__name__ == MPFB:
                return ".".join(str(x) for x in m.bl_info.get("version", ()))
    except Exception:
        pass
    return "?"

# ---------------------------------------------------------------- Auswahl
MACROS = ["gender", "age", "muscle", "weight", "height", "proportions", "cupsize", "firmness"]

# Makros wie MPFB (TargetService.calculate_target_stack_from_macro_info_dict): Zieldateien aus
# macrodetails/, gewichtet mit Stuetzkurven aus macro.json (auf 4 Stellen gerundet). Exportiert
# werden je Ecke Geschlecht x Alter: Grundform (universal + Volksgruppen zu je 1/3), Wirkung von
# Muskeln und Gewicht, ihr Kreuzterm, Groesse und Proportionen; dazu die Brust je Alter.
# Groesse, Proportionen und Brust haengen in MakeHuman auch von Muskeln und Gewicht ab; hier
# gelten sie fuer mittlere Muskeln und mittleres Gewicht (Naeherung, Abweichung im Protokoll).
TEILE = {
    "gender": [(-0.01, 1.01, "female", "male")],
    "age": [(-0.01, 0.1874998, "baby", "child"), (0.1874999, 0.49998, "child", "young"), (0.49999, 1.01, "young", "old")],
    "muscle": [(-0.01, 0.49998, "minmuscle", "averagemuscle"), (0.49999, 1.01, "averagemuscle", "maxmuscle")],
    "weight": [(-0.01, 0.49998, "minweight", "averageweight"), (0.49999, 1.01, "averageweight", "maxweight")],
    "proportions": [(-0.01, 0.4999, "uncommonproportions", None), (0.5, 1.01, None, "idealproportions")],
    "height": [(-0.01, 0.49, "minheight", None), (0.51, 1.01, None, "maxheight")],
    "cupsize": [(-0.01, 0.49998, "mincup", "averagecup"), (0.49999, 1.01, "averagecup", "maxcup")],
    "firmness": [(-0.01, 0.4998, "minfirmness", "averagefirmness"), (0.4999, 1.01, "averagefirmness", "maxfirmness")],
}
G = {"female": "w", "male": "m"}
A = {"baby": "baby", "child": "kind", "young": "jung", "old": "alt"}

def teil(name, v):
    """Stuetzkurve wie MPFB _interpolate_macro_components: {Komponente: Gewicht}."""
    out = {}
    for lo, hi, low, high in TEILE[name]:
        if lo < v < hi:
            pct = (v - lo) / (hi - lo)
            if low: out[low] = out.get(low, 0) + round(1 - pct, 4)
            if high: out[high] = out.get(high, 0) + round(pct, 4)
    return out

def makro_gewichte(makro):
    """Makro-Regler -> Gewichte der exportierten Makro-Ziele (wie com.dan.avatar.Person)."""
    c = {n: teil(n, makro.get(n, 0.5)) for n in TEILE}
    w = {"macro.basis": -1.0}
    def add(k, x, cut=False):
        # MPFB laesst Ziele mit Gewicht <= 0,01 weg (cutoff); fuer Groesse, Proportionen und Brust nachgebildet
        if x != 0 and not (cut and x <= 0.01): w[k] = w.get(k, 0) + x
    for g, wg in c["gender"].items():
        for a, wa in c["age"].items():
            e = "macro.%s.%s" % (G[g], A[a]); ga = wg * wa
            add(e, ga)
            for m in ("minmuscle", "maxmuscle"):
                add(e + ".muscle." + m[:3], ga * c["muscle"].get(m, 0))
            for x in ("minweight", "maxweight"):
                add(e + ".weight." + x[:3], ga * c["weight"].get(x, 0))
            for m in ("minmuscle", "maxmuscle"):
                for x in ("minweight", "maxweight"):
                    add(e + ".mw.%s.%s" % (m[:3], x[:3]), ga * c["muscle"].get(m, 0) * c["weight"].get(x, 0))
            for h in ("minheight", "maxheight"):
                add(e + ".height." + h[:3], ga * c["height"].get(h, 0), True)
            for pr in ("uncommonproportions", "idealproportions"):
                add(e + ".prop." + pr[:-11], ga * c["proportions"].get(pr, 0), True)
    for a, wa in c["age"].items():
        if a == "baby": continue
        for cu, wc in c["cupsize"].items():
            for fi, wf in c["firmness"].items():
                if cu == "averagecup" and fi == "averagefirmness": continue
                add("macro.brust.%s.%s.%s" % (A[a], cu[:-3], fi[:-8]), wa * wc * wf, True)
    return w

# Gesichtsregler: Name -> (Ordner, Ziel "decr"-Seite, Ziel "incr"-Seite); "l-/r-" wird verdoppelt
FACE = [
    ("nose.breite",        "nose",     "nose-scale-horiz-decr",   "nose-scale-horiz-incr"),
    ("nose.laenge",        "nose",     "nose-scale-vert-decr",    "nose-scale-vert-incr"),
    ("nose.tiefe",         "nose",     "nose-scale-depth-decr",   "nose-scale-depth-incr"),
    ("nose.hoecker",       "nose",     "nose-hump-decr",          "nose-hump-incr"),
    ("nose.spitze",        "nose",     "nose-point-width-decr",   "nose-point-width-incr"),
    ("nose.nasenloecher",  "nose",     "nose-nostrils-width-decr","nose-nostrils-width-incr"),
    ("nose.hoehe",         "nose",     "nose-trans-down",         "nose-trans-up"),
    ("mouth.breite",       "mouth",    "mouth-scale-horiz-decr",  "mouth-scale-horiz-incr"),
    ("mouth.oberlippe",    "mouth",    "mouth-upperlip-volume-decr","mouth-upperlip-volume-incr"),
    ("mouth.unterlippe",   "mouth",    "mouth-lowerlip-volume-decr","mouth-lowerlip-volume-incr"),
    ("mouth.winkel",       "mouth",    "mouth-angles-down",       "mouth-angles-up"),
    ("mouth.amorbogen",    "mouth",    "mouth-cupidsbow-decr",    "mouth-cupidsbow-incr"),
    ("mouth.hoehe",        "mouth",    "mouth-trans-down",        "mouth-trans-up"),
    ("chin.breite",        "chin",     "chin-width-decr",         "chin-width-incr"),
    ("chin.vorstehen",     "chin",     "chin-prominent-decr",     "chin-prominent-incr"),
    ("chin.hoehe",         "chin",     "chin-height-decr",        "chin-height-incr"),
    ("chin.kiefer",        "chin",     "chin-bones-decr",         "chin-bones-incr"),
    ("cheek.knochen",      "cheek",    "l-cheek-bones-decr",      "l-cheek-bones-incr"),
    ("cheek.fuelle",       "cheek",    "l-cheek-volume-decr",     "l-cheek-volume-incr"),
    ("eye.groesse",        "eyes",     "l-eye-scale-decr",        "l-eye-scale-incr"),
    ("eye.hoehe",          "eyes",     "l-eye-trans-down",        "l-eye-trans-up"),
    ("eye.abstand",        "eyes",     "l-eye-trans-in",          "l-eye-trans-out"),
    ("eye.lidfalte",       "eyes",     "l-eye-eyefold-down",      "l-eye-eyefold-up"),
    ("eye.traenensack",    "eyes",     "l-eye-bag-decr",          "l-eye-bag-incr"),
    ("brow.hoehe",         "eyebrows", "eyebrows-trans-down",     "eyebrows-trans-up"),
    ("brow.winkel",        "eyebrows", "eyebrows-angle-down",     "eyebrows-angle-up"),
    ("ear.groesse",        "ears",     "l-ear-scale-decr",        "l-ear-scale-incr"),
    ("ear.abstehen",       "ears",     "l-ear-wing-decr",         "l-ear-wing-incr"),
    ("forehead.hoehe",     "forehead", "forehead-scale-vert-decr","forehead-scale-vert-incr"),
    ("head.fuelle",        "head",     "head-fat-decr",           "head-fat-incr"),
    ("head.breite",        "head",     "head-scale-horiz-decr",   "head-scale-horiz-incr"),
    ("head.hoehe",         "head",     "head-scale-vert-decr",    "head-scale-vert-incr"),
    ("neck.breite",        "neck",     "neck-scale-horiz-decr",   "neck-scale-horiz-incr"),
]

# Referenzpersonen fuer den Abgleich mit dem Lader (Phase 2). Makro-Regler 0..1 (Mitte 0,5),
# Gesichtsregler -1..+1, Formen und Viseme 0..1. Abbildung auf Ziele wie in README von com.dan.avatar.
REFERENZ = [
    {"name": "Frau, jung", "makro": {"gender": 0.0, "age": 0.35, "weight": 0.45, "muscle": 0.4, "height": 0.45, "proportions": 0.6, "cupsize": 0.6, "firmness": 0.6},
     "gesicht": {"nose.breite": -0.4, "mouth.oberlippe": 0.5, "eye.groesse": 0.3},
     "formen": {"arkit.mouthSmileLeft": 0.7, "arkit.mouthSmileRight": 0.7, "arkit.eyeSquintLeft": 0.3, "arkit.eyeSquintRight": 0.3}},
    {"name": "Mann, alt", "makro": {"gender": 1.0, "age": 0.9, "weight": 0.7, "muscle": 0.5, "height": 0.55, "proportions": 0.4},
     "gesicht": {"chin.breite": 0.5, "nose.hoecker": 0.6, "brow.hoehe": -0.4},
     "formen": {"arkit.jawOpen": 0.4, "visem.viseme_O": 0.5}},
    {"name": "Kind", "makro": {"gender": 0.6, "age": 0.12, "weight": 0.5, "muscle": 0.5, "height": 0.3},
     "gesicht": {"head.fuelle": 0.4},
     "formen": {"arkit.eyeBlinkLeft": 1.0, "arkit.eyeBlinkRight": 1.0}},
    {"name": "Mann, kraeftig", "makro": {"gender": 0.9, "age": 0.45, "weight": 0.4, "muscle": 0.95, "height": 0.8, "proportions": 0.7},
     "gesicht": {"chin.kiefer": 0.8, "neck.breite": 0.6},
     "formen": {"arkit.browDownLeft": 0.5, "arkit.browDownRight": 0.5}},
    {"name": "Frau, rundlich", "makro": {"gender": 0.15, "age": 0.6, "weight": 0.9, "muscle": 0.3, "height": 0.4, "cupsize": 0.8, "firmness": 0.3},
     "gesicht": {"cheek.fuelle": 0.7, "mouth.breite": 0.3},
     "formen": {"arkit.mouthPucker": 0.6, "visem.viseme_FF": 0.4}},
]

def gewichte(person):
    """Regler -> Gewichte je Ziel (wie com.dan.avatar.Person#weights)."""
    w = {}
    w.update(makro_gewichte(person.get("makro", {})))
    for g, v in person.get("gesicht", {}).items():
        if v < 0: w["face.%s.decr" % g] = -v
        elif v > 0: w["face.%s.incr" % g] = v
    for f, v in person.get("formen", {}).items():
        if v != 0: w[f] = v
    return w

# Posen (Phase 3): Knochen werden auf Zielrichtungen ausgerichtet (kleinste Drehung), in
# Blender-Achsen: x = links der Person, y = hinten, z = oben; vorn ist -y.
POSEN = ["stehen", "sitzen", "lehnen", "hand_ans_gesicht", "unterarm_drehen"]

# Ausstattung, wenn installiert: (Art, Unterordner, Kandidaten)
EQUIP = [
    ("Eyes",      "eyes",      ["high-poly/high-poly.mhclo", "low-poly/low-poly.mhclo"]),
    ("Teeth",     "teeth",     ["teeth_base/teeth_base.mhclo"]),
    ("Tongue",    "tongue",    ["tongue01/tongue01.mhclo"]),
    ("Eyebrows",  "eyebrows",  ["eyebrow001/eyebrow001.mhclo"]),
    ("Eyelashes", "eyelashes", ["eyelashes01/eyelashes01.mhclo"]),
    ("Hair",      "hair",      ["bob02/bob02.mhclo", "long01/long01.mhclo", "ponytail01/ponytail01.mhclo"]),
    # Phase 6: Frisurenbibliothek (lang offen, Zopf, Dutt, kurz, Locken); Glatze mit Bart macht com.dan.hair
    ("Hair",      "hair",      ["long01/long01.mhclo"]),
    ("Hair",      "hair",      ["braid01/braid01.mhclo"]),
    ("Hair",      "hair",      ["elvs_reverse_french_braid_bun/elvs_reverse_french_braid_bun.mhclo"]),
    ("Hair",      "hair",      ["short02/short02.mhclo"]),
    ("Hair",      "hair",      ["afro01/afro01.mhclo"]),
    ("Clothes",   "clothes",   ["male_casualsuit01/male_casualsuit01.mhclo", "female_casualsuit01/female_casualsuit01.mhclo"]),
    # Phase 10: Kleiderschrank (nur CC0: shirts01, pants01, shoes01, suits02, hats01)
] + [("Clothes", "clothes", [n + "/" + n + ".mhclo"]) for n in [
    "toigo_fisherman_sweater", "donitz_monk_robe_hood", "donitz_monk_robe_hood_down", "donitz_monk_robe",
    "toigo_wool_pants", "cortu_cargo_pants", "cortu_jeans_shorts", "toigo_basic_tucked_t-shirt",
    "toigo_ankle_boots_female", "culturalibre_hero_boots_2", "toigo_mj_cloth_shoes",
    "rehmanpolanski_viking_tunic", "rehmanpolanski_viking_pants", "rehmanpolanski_viking_boots"]]

# Teile am Kopf folgen auch den Gesichtsreglern; alle anderen Kleider nur den Makros (spart Zeit und Platz)
KOPF_KLEIDUNG = ("donitz_monk_robe_hood",)

# ---------------------------------------------------------------- Hilfen
def mhclo_license(path):
    """Lizenz aus dem Kopf einer .mhclo-Datei (CC0, CC BY, AGPL …)."""
    try:
        with open(path, encoding="utf-8", errors="replace") as f:
            head = [next(f) for _ in range(30)]
    except Exception:
        return "unbekannt"
    for l in head:
        t = l.strip("# \n").strip()
        if t.lower().startswith("license"):
            return t[7:].strip()
        if "released as CC0" in t or t.endswith("- CC0") or t == "CC0":
            return "CC0"
    return "unbekannt"

def target_path(folder, name):
    p = os.path.join(LocationService.get_mpfb_data("targets"), folder, name + ".target.gz")
    return p if os.path.isfile(p) else TargetService.target_full_path(name)

def coords(obj):
    """Ecken der aktuellen Mischung aller Shape Keys (oder des Netzes)."""
    me = obj.data
    if me.shape_keys:
        k = obj.shape_key_add(name="__mix", from_mix=True)
        c = [tuple(v.co) for v in k.data]
        obj.shape_key_remove(k)
        return c
    return [tuple(v.co) for v in me.vertices]

def set_macros(basemesh, values):
    for k, v in values.items():
        if k == "race":
            for r, w in v.items():
                HOP.set_value(r, w, entity_reference=basemesh)
        else:
            HOP.set_value(k, v, entity_reference=basemesh)
    TargetService.reapply_macro_details(basemesh, remove_zero_weight_targets=True)

def add_key_from_delta(obj, name, base, delta):
    k = obj.shape_key_add(name=name, from_mix=False)
    for i, d in enumerate(delta):
        b = base[i]
        k.data[i].co = (b[0] + d[0], b[1] + d[1], b[2] + d[2])
    k.value = 0.0
    k.slider_min, k.slider_max = -1.0, 1.0
    return k

def load_delta(obj, path):
    """Ziel-Datei als Differenz zur Grundform lesen (ueber MPFB, dann wieder entfernen)."""
    base = [tuple(v.co) for v in obj.data.shape_keys.key_blocks[0].data]
    k = TargetService.load_target(obj, path, weight=0.0, name="__tmp")
    d = [(a.co[0] - b[0], a.co[1] - b[1], a.co[2] - b[2]) for a, b in zip(k.data, base)]
    obj.shape_key_remove(k)
    return d

def mirror_name(n):
    return n.replace("l-", "r-", 1) if n.startswith("l-") else None

def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for b in iter(lambda: f.read(1 << 20), b""):
            h.update(b)
    return h.hexdigest()

# ---------------------------------------------------------------- Bau
def build():
    log("Blender %s, MPFB %s (%s), Pakete: %s" % (bpy.app.version_string, mpfb_version(), MPFB, ", ".join(PACKS) or "keine"))
    missing = []

    # leere Szene
    for o in list(bpy.data.objects):
        bpy.data.objects.remove(o, do_unlink=True)
    scene = bpy.context.scene
    scene.unit_settings.system = "METRIC"

    # Grundkoerper (Mitte aller Regler)
    defaults = TargetService.get_default_macro_info_dict()
    defaults["race"] = {"asian": 1.0 / 3.0, "caucasian": 1.0 / 3.0, "african": 1.0 / 3.0}
    basemesh = HumanService.create_human(mask_helpers=False, detailed_helpers=True,
                                         extra_vertex_groups=True, feet_on_ground=True,
                                         scale=0.1, macro_detail_dict=defaults)
    basemesh.name = "Avatar"
    bpy.context.view_layer.objects.active = basemesh
    log("Grundnetz: %d Ecken (mit Hilfsgeometrie)" % len(basemesh.data.vertices))

    # Makro-Ziele aus den Zieldateien (Differenzen zur rohen Grundform base0)
    tdir = LocationService.get_mpfb_data("targets")
    def tf(*parts):
        return os.path.join(tdir, *parts) + ".target.gz"
    cache = {}
    def T(path):
        if path not in cache: cache[path] = load_delta(basemesh, path)
        return cache[path]
    def lin(*terms):
        n = len(terms[0][1]); out = [[0.0, 0.0, 0.0] for _ in range(n)]
        for f, d in terms:
            for i in range(n):
                out[i][0] += f * d[i][0]; out[i][1] += f * d[i][1]; out[i][2] += f * d[i][2]
        return [tuple(v) for v in out]
    set_macros(basemesh, defaults)
    mid = coords(basemesh)
    base0 = [tuple(v.co) for v in basemesh.data.shape_keys.key_blocks[0].data]
    macro_delta = {"macro.basis": [(m[0] - b[0], m[1] - b[1], m[2] - b[2]) for m, b in zip(mid, base0)]}
    for g in ("female", "male"):
        for a in ("baby", "child", "young", "old"):
            e = "macro.%s.%s" % (G[g], A[a])
            uni = lambda mu, we: T(tf("macrodetails", "universal-%s-%s-%s-%s" % (g, a, mu, we)))
            race = [(1.0 / 3.0, T(tf("macrodetails", "%s-%s-%s" % (r, g, a)))) for r in ("african", "asian", "caucasian")]
            avg = uni("averagemuscle", "averageweight")
            macro_delta[e] = lin((1.0, avg), *race)
            for mu in ("minmuscle", "maxmuscle"):
                macro_delta[e + ".muscle." + mu[:3]] = lin((1.0, uni(mu, "averageweight")), (-1.0, avg))
            for we in ("minweight", "maxweight"):
                macro_delta[e + ".weight." + we[:3]] = lin((1.0, uni("averagemuscle", we)), (-1.0, avg))
            for mu in ("minmuscle", "maxmuscle"):
                for we in ("minweight", "maxweight"):
                    macro_delta[e + ".mw.%s.%s" % (mu[:3], we[:3])] = lin((1.0, uni(mu, we)), (-1.0, uni(mu, "averageweight")),
                                                                         (-1.0, uni("averagemuscle", we)), (1.0, avg))
            for h in ("minheight", "maxheight"):
                p_ = tf("macrodetails", "height", "%s-%s-averagemuscle-averageweight-%s" % (g, a, h))
                if os.path.isfile(p_): macro_delta[e + ".height." + h[:3]] = T(p_)
            for pr in ("uncommonproportions", "idealproportions"):
                p_ = tf("macrodetails", "proportions", "%s-%s-averagemuscle-averageweight-%s" % (g, a, pr))
                if os.path.isfile(p_): macro_delta[e + ".prop." + pr[:-11]] = T(p_)
            log("Makro-Ecke %s/%s" % (G[g], A[a]))
    for a in ("child", "young", "old"):
        for cu in ("mincup", "averagecup", "maxcup"):
            for fi in ("minfirmness", "averagefirmness", "maxfirmness"):
                if cu == "averagecup" and fi == "averagefirmness": continue
                p_ = tf("breast", "female-%s-averagemuscle-averageweight-%s-%s" % (a, cu, fi))
                if os.path.isfile(p_): macro_delta["macro.brust.%s.%s.%s" % (A[a], cu[:-3], fi[:-8])] = T(p_)
    cache = None
    log("Makro-Ziele: %d" % len(macro_delta))

    # Wie gut ist die Mischung? Echte MPFB-Makros gegen Mitte + Summe der gewichteten Ziele
    linear = []
    for ref in REFERENZ:
        v = dict(defaults); v["race"] = dict(defaults["race"])
        v.update(ref["makro"])
        set_macros(basemesh, v)
        true = coords(basemesh)
        w = makro_gewichte(ref["makro"])
        mx, sm = 0.0, 0.0
        for i, t in enumerate(true):
            x, y, z = mid[i]
            for k, wk in w.items():
                if k not in macro_delta: continue
                d = macro_delta[k][i]
                x += wk * d[0]; y += wk * d[1]; z += wk * d[2]
            e = ((t[0] - x) ** 2 + (t[1] - y) ** 2 + (t[2] - z) ** 2) ** 0.5
            mx = max(mx, e); sm += e
        linear.append({"person": ref["name"], "max_mm": round(mx * 1000, 2), "mittel_mm": round(sm / len(true) * 1000, 3)})
    log("Makro-Mischung gegen MPFB: " + "; ".join("%s max %.1f mm" % (l["person"], l["max_mm"]) for l in linear))
    set_macros(basemesh, defaults)
    TargetService.bake_targets(basemesh)          # Mitte ist jetzt die Grundform
    basemesh.shape_key_add(name="Basis", from_mix=False)
    base = [tuple(v.co) for v in basemesh.data.vertices]

    for name in sorted(macro_delta):
        add_key_from_delta(basemesh, name, base, macro_delta[name])
    macro_delta = None

    # Gesichtsregler
    face_names = []
    for reg, folder, lo, hi in FACE:
        for side, t in (("decr", lo), ("incr", hi)):
            p = target_path(folder, t)
            if not p:
                missing.append("Ziel " + t); continue
            d = load_delta(basemesh, p)
            m = mirror_name(t)
            if m:
                pm = target_path(folder, m)
                if pm:
                    dm = load_delta(basemesh, pm)
                    d = [(a[0] + b[0], a[1] + b[1], a[2] + b[2]) for a, b in zip(d, dm)]
            n = "face.%s.%s" % (reg, side)
            add_key_from_delta(basemesh, n, base, d)
            face_names.append(n)
    log("Gesichtsregler: %d Ziele" % len(face_names))

    # Gesichtsformen
    arkit = svc("services.faceservice", "FaceService")
    fsmod = importlib.import_module(MPFB + ".services.faceservice")
    expr_names, expr_source = [], ""
    if arkit.is_faceunits01_installed(force_recheck=True):
        for n in fsmod.ARKIT_FACEUNITS if hasattr(fsmod, "ARKIT_FACEUNITS") else []:
            p = TargetService.target_full_path(n)
            if p:
                add_key_from_delta(basemesh, "arkit." + n, base, load_delta(basemesh, p)); expr_names.append("arkit." + n)
        expr_source = "faceunits01 (ARKit)"
    else:
        missing.append("Paket faceunits01 (52 ARKit-Formen)")
        units = os.path.join(LocationService.get_mpfb_data("targets"), "expression", "units", "caucasian")
        if os.path.isdir(units):
            for f in sorted(os.listdir(units)):
                if f.endswith(".target.gz"):
                    n = "expr." + f[:-10]
                    add_key_from_delta(basemesh, n, base, load_delta(basemesh, os.path.join(units, f)))
                    expr_names.append(n)
            expr_source = "MakeHuman expression units (Platzhalter)"
    vis_names = []
    if TargetService.target_full_path("viseme_aa"):
        for n in getattr(fsmod, "META_VISEMES", []):
            p = TargetService.target_full_path(n)
            if p:
                add_key_from_delta(basemesh, "visem." + n, base, load_delta(basemesh, p)); vis_names.append("visem." + n)
    else:
        missing.append("Paket visemes02 (15 Viseme)")
    log("Gesichtsformen: %d (%s), Viseme: %d" % (len(expr_names), expr_source, len(vis_names)))

    # Rig (vor der Ausstattung, damit sie Gewichte vom Koerper bekommt)
    rig = HumanService.add_builtin_rig(basemesh, "game_engine", import_weights=True)
    rig.name = rig.data.name = "Avatar.rig"
    log("Rig game_engine: %d Knochen" % len(rig.data.bones))

    # Ausstattung (nur wenn installiert)
    equipped, equip_info = [], []
    for atype, sub, cands in EQUIP:
        path = None
        for c in cands:
            path = AssetService.find_asset_absolute_path(c, sub)
            if path: break
        if not path:
            missing.append("%s (%s)" % (atype, sub)); continue
        try:
            obj = HumanService.add_mhclo_asset(path, basemesh, asset_type=atype, subdiv_levels=0,
                                               set_up_rigging=True, interpolate_weights=True,
                                               import_subrig=False, import_weights=True)
            for m in [m for m in obj.modifiers if m.type not in ("ARMATURE",)]:
                obj.modifiers.remove(m)
            equipped.append(obj)
            equip_info.append({"art": atype, "datei": os.path.basename(path), "quelle": path, "lizenz": mhclo_license(path),
                               "objekt": obj.name, "netz": obj.data.name})
        except Exception as e:
            missing.append("%s: %s" % (atype, e))
    log("Ausstattung: %s" % (", ".join(e["art"] for e in equip_info) or "keine"))

    # Ausstattung folgt den Morphs: je Ziel den Koerper auf 1 setzen, neu anpassen, Differenz merken
    ClothesService = svc("services.clothesservice", "ClothesService")
    keyblocks = basemesh.data.shape_keys.key_blocks
    for kb in keyblocks: kb.value = 0.0
    bpy.context.view_layer.update()
    eq_base = {}
    for o in equipped:
        ClothesService.fit_clothes_to_human(o, basemesh, set_parent=False)
        eq_base[o.name] = [tuple(v.co) for v in o.data.vertices]
    eq_delta = {o.name: {} for o in equipped}
    # welche Ziele je Teil angepasst werden: Haar, Augen usw. alle; Kleidung nur Makros, am Kopf auch Gesicht
    eq_filter = {}
    for o, e in zip(equipped, equip_info):
        if e["art"] != "Clothes" or "casualsuit" in e["datei"]:
            eq_filter[o.name] = None
        else:
            kopf = any(e["datei"].startswith(k + ".") for k in KOPF_KLEIDUNG)
            eq_filter[o.name] = ("macro.", "face.") if kopf else ("macro.",)
    RigService = svc("services.rigservice", "RigService")
    bone_names = [b.name for b in rig.data.bones]
    rest_heads = {b.name: b.head_local.copy() for b in rig.data.bones}
    joint_delta = {}
    t_fit = time.time()
    for kb in list(keyblocks)[1:]:
        kb.value = 1.0
        bpy.context.view_layer.update()
        RigService.refit_existing_armature(rig, basemesh)
        jd = []
        for i, b in enumerate(rig.data.bones):
            d = b.head_local - rest_heads[b.name]
            if d.length > 1e-6:
                jd.append([i, round(d.x, 7), round(d.z, 7), round(-d.y, 7)])     # glTF-Achsen
        if jd: joint_delta[kb.name] = jd
        for o in equipped:
            flt = eq_filter.get(o.name)
            if flt is not None and not kb.name.startswith(flt):
                continue
            ClothesService.fit_clothes_to_human(o, basemesh, set_parent=False)
            b = eq_base[o.name]
            d = [(v.co[0] - b[i][0], v.co[1] - b[i][1], v.co[2] - b[i][2]) for i, v in enumerate(o.data.vertices)]
            if max(abs(c) for t in d for c in t) > 1e-6:
                eq_delta[o.name][kb.name] = d
        kb.value = 0.0
    bpy.context.view_layer.update()
    RigService.refit_existing_armature(rig, basemesh)
    back = max((b.head_local - rest_heads[b.name]).length for b in rig.data.bones)
    log("Gelenke folgen den Morphs: %d Ziele bewegen Gelenke (Rueckweg %.2e m)" % (len(joint_delta), back))

    # Gelenke fuer die Referenzpersonen (Abgleich mit Skeleton.rest in Java)
    ref_joints = []
    for ref in REFERENZ:
        w = gewichte(ref)
        for kb in list(keyblocks)[1:]: kb.value = w.get(kb.name, 0.0)
        bpy.context.view_layer.update()
        RigService.refit_existing_armature(rig, basemesh)
        ref_joints.append({b.name: [round(b.head_local.x, 7), round(b.head_local.z, 7), round(-b.head_local.y, 7)] for b in rig.data.bones})
    for kb in list(keyblocks)[1:]: kb.value = 0.0
    bpy.context.view_layer.update()
    RigService.refit_existing_armature(rig, basemesh)
    for o in equipped:
        for i, v in enumerate(o.data.vertices):
            v.co = eq_base[o.name][i]
        o.shape_key_add(name="Basis", from_mix=False)
        for name in [kb.name for kb in list(keyblocks)[1:]]:      # Reihenfolge wie am Koerper
            if name in eq_delta[o.name]:
                add_key_from_delta(o, name, eq_base[o.name], eq_delta[o.name][name])
    log("Ausstattung folgt den Morphs: %s (%.1f s)" % (", ".join("%s %d" % (o.name.split(".")[-1], len(eq_delta[o.name])) for o in equipped), time.time() - t_fit))
    eq_delta = None

    # Hoechstens 4 Knochen je Ecke, normalisiert (wie im glTF; so rechnen Blender und Java gleich)
    for o in [basemesh] + equipped:
        bpy.ops.object.select_all(action="DESELECT")
        o.select_set(True); bpy.context.view_layer.objects.active = o
        bpy.ops.object.vertex_group_limit_total(group_select_mode="BONE_DEFORM", limit=4)
        bpy.ops.object.vertex_group_normalize_all(group_select_mode="BONE_DEFORM", lock_active=False)

    # Unter der Kleidung verdeckte Koerperecken (MPFB-Gruppen "Delete.<kleidung>") als Merkmal
    # _VERDECKT mitgeben: der Lader zeichnet Dreiecke nicht, deren Ecken alle verdeckt sind.
    # Seit Fassung 1.7 je Kleidungsstueck ein Bit (bis 23 Teile, als float exakt): der Lader blendet ein
    # Dreieck aus, wenn alle drei Ecken unter einem getragenen Teil liegen. Zuordnung in <name>.kleidung.json.
    kleidung = []
    del_bit = {}
    for o, e in zip(equipped, equip_info):
        if e["art"] != "Clothes": continue
        bit = len(kleidung)
        stem = e["datei"][:-6]
        # Gruppe genau zum Teil (die Kapuze der Kutte hat die Gruppe der Kutte: die verdeckt den Rumpf, nicht den Kopf)
        grp = [g for g in basemesh.vertex_groups if g.name.startswith("Delete.") and (g.name[7:] == stem or o.name == "Avatar." + g.name[7:])]
        for g in grp: del_bit[g.index] = bit
        kleidung.append({"netz": e["netz"], "datei": e["datei"], "bit": bit, "lizenz": e["lizenz"],
                         "gruppen": [g.name for g in grp]})
    verdeckt = [0.0] * len(basemesh.data.vertices)
    for v in basemesh.data.vertices:
        m = 0
        for g in v.groups:
            if g.group in del_bit and g.weight > 0.0:
                m |= 1 << del_bit[g.group]
        verdeckt[v.index] = float(m)
    a = basemesh.data.attributes.new("_VERDECKT", "FLOAT", "POINT")
    a.data.foreach_set("value", verdeckt)
    log("Unter der Kleidung verdeckt: %d Ecken, %d Teile (%s)" % (sum(1 for x in verdeckt if x > 0), len(kleidung),
        ", ".join("%s:%d" % (k["datei"][:-6], len(k["gruppen"])) for k in kleidung)))

    # Hilfsgeometrie entfernen (Shape Keys bleiben im Bearbeitungsmodus erhalten)
    groups = {g.index: g.name for g in basemesh.vertex_groups}
    helper_idx = {i for i, n in groups.items() if n.startswith("helper-") or n.startswith("joint-") or n == "HelperGeometry"}
    kill = set()
    for v in basemesh.data.vertices:
        for g in v.groups:
            if g.group in helper_idx and g.weight > 0.0:
                kill.add(v.index); break
    bpy.ops.object.select_all(action="DESELECT")
    basemesh.select_set(True); bpy.context.view_layer.objects.active = basemesh
    bpy.ops.object.mode_set(mode="EDIT")
    bm = bmesh.from_edit_mesh(basemesh.data)
    bm.verts.ensure_lookup_table()
    bmesh.ops.delete(bm, geom=[bm.verts[i] for i in sorted(kill)], context="VERTS")
    bmesh.update_edit_mesh(basemesh.data)
    bpy.ops.object.mode_set(mode="OBJECT")
    for g in [g for g in basemesh.vertex_groups if g.index in helper_idx]:
        basemesh.vertex_groups.remove(g)
    for mod in [m for m in basemesh.modifiers if m.type != "ARMATURE"]:
        basemesh.modifiers.remove(mod)
    log("Hilfsgeometrie entfernt: %d Ecken, bleiben %d" % (len(kill), len(basemesh.data.vertices)))

    # Blender-Nummer jeder Ecke als Attribut (glTF teilt Ecken an Naehten; so findet der Lader sie wieder)
    for o in [basemesh] + equipped:
        a = o.data.attributes.get("_BLIDX") or o.data.attributes.new("_BLIDX", "FLOAT", "POINT")
        a.data.foreach_set("value", [float(i) for i in range(len(o.data.vertices))])

    # Referenz: fuenf Personen, Mischung der Shape Keys in Blender, in glTF-Achsen (x, z, -y)
    import struct as _st
    ref_meshes = [basemesh] + equipped
    ref_bin = bytearray()
    ref_info = []
    for ref in REFERENZ:
        w = gewichte(ref)
        for o in ref_meshes:
            kbs = o.data.shape_keys.key_blocks if o.data.shape_keys else []
            for kb in kbs: kb.value = w.get(kb.name, 0.0) if kb.name != "Basis" else 0.0
            c = coords(o)
            for (x, y, z) in c:
                ref_bin += _st.pack("<3f", x, z, -y)
            for kb in kbs: kb.value = 0.0
        ref_info.append({"name": ref["name"], "regler": {k: ref[k] for k in ("makro", "gesicht", "formen")}, "gewichte": w})
    ref_layout = [{"netz": o.data.name, "objekt": o.name, "ecken": len(o.data.vertices)} for o in ref_meshes]

    # Posen: ausrichten, je Knochen D = Pose * Ruhe^-1 (Blender-Ruhe) in glTF-Achsen
    from mathutils import Matrix, Vector
    CB = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, -1, 0, 0), (0, 0, 0, 1)))   # Blender -> glTF
    CBi = CB.inverted()
    def upd(): bpy.context.view_layer.update()
    def reset():
        for pb in rig.pose.bones: pb.matrix_basis = Matrix.Identity(4)
        upd()
    def rot_about(name, rot, pivot):
        pb = rig.pose.bones[name]
        pb.matrix = Matrix.Translation(pivot) @ rot.to_4x4() @ Matrix.Translation(-pivot) @ pb.matrix
        upd()
    def aim(name, d):
        pb = rig.pose.bones[name]
        q = (pb.tail - pb.head).normalized().rotation_difference(Vector(d).normalized())
        rot_about(name, q.to_matrix(), pb.head.copy())
    def aim_at(name, point):
        pb = rig.pose.bones[name]
        aim(name, Vector(point) - pb.head)
    def turn(name, axis, deg):
        pb = rig.pose.bones[name]
        rot_about(name, Matrix.Rotation(math.radians(deg), 3, axis), pb.head.copy())
    def twist(name, deg):
        pb = rig.pose.bones[name]
        rot_about(name, Matrix.Rotation(math.radians(deg), 3, (pb.tail - pb.head).normalized()), pb.head.copy())
    def shift(name, v):
        pb = rig.pose.bones[name]
        pb.matrix = Matrix.Translation(Vector(v)) @ pb.matrix
        upd()
    def arme_haengen():
        for s_, sg in (("l", 1), ("r", -1)):
            aim("upperarm_" + s_, (0.17 * sg, 0.03, -1)); aim("lowerarm_" + s_, (0.10 * sg, -0.22, -1)); aim("hand_" + s_, (0.06 * sg, -0.12, -1))
    def pose(name):
        reset()
        if name == "stehen":
            arme_haengen()
        elif name == "sitzen":
            ankle = {s_: rig.pose.bones["foot_" + s_].head.copy() for s_ in "lr"}
            for s_, sg in (("l", 1), ("r", -1)):
                aim("thigh_" + s_, (0.07 * sg, -1, 0.0)); aim("calf_" + s_, (0.0, 0.06, -1))
                aim("upperarm_" + s_, (0.12 * sg, -0.45, -1)); aim("lowerarm_" + s_, (0.05 * sg, -1, -0.12)); aim("hand_" + s_, (0.02 * sg, -1, -0.3))
            dz = min(ankle[s_].z for s_ in "lr") - min(rig.pose.bones["foot_" + s_].head.z for s_ in "lr")
            shift("Root", (0, 0, dz))
        elif name == "lehnen":
            arme_haengen()
            turn("Root", "X", -6)
            aim("thigh_r", (-0.08, -0.35, -1)); aim("calf_r", (-0.02, 0.3, -1))
            turn("head", "Z", 12)
        elif name == "hand_ans_gesicht":
            arme_haengen()
            hd = rig.pose.bones["head"].head.copy()
            kinn = hd + Vector((-0.035, -0.10, -0.06))
            aim("upperarm_r", (-0.28, -0.55, -0.62))
            aim_at("lowerarm_r", kinn + Vector((-0.02, 0.02, -0.05)))
            aim_at("hand_r", kinn + Vector((0.0, 0.0, 0.02)))
            turn("head", "X", 6); turn("head", "Z", -8)
        elif name == "unterarm_drehen":
            arme_haengen()
            twist("lowerarm_l", 45); twist("hand_l", 70)
            twist("lowerarm_r", -45); twist("hand_r", -70)
    posen = {}
    for name in POSEN:
        pose(name)
        posen[name] = {}
        for pb in rig.pose.bones:
            D = CB @ (pb.matrix @ pb.bone.matrix_local.inverted()) @ CBi
            posen[name][pb.name] = [round(D[r][c], 7) for c in range(4) for r in range(4)]
    log("Posen: %s" % ", ".join(POSEN))

    # Referenz fuer das Skinning: Blender mit Volumenerhalt (Dual-Quaternion), Mitte aller Regler
    arm = [m for m in basemesh.modifiers if m.type == "ARMATURE"][0]
    arm.use_deform_preserve_volume = True
    pose_ref = bytearray()
    POSE_REF = ["hand_ans_gesicht", "sitzen", "unterarm_drehen"]
    for name in POSE_REF:
        pose(name)
        dg = bpy.context.evaluated_depsgraph_get()
        ev = basemesh.evaluated_get(dg)
        me = ev.to_mesh()
        for v in me.vertices:
            x, y, z = v.co
            pose_ref += _st.pack("<3f", x, z, -y)
        ev.to_mesh_clear()
    arm.use_deform_preserve_volume = False

    # Vorschau der Posen (Workbench), nur mit --vorschau
    if ARGS["vorschau"]:
        vdir = os.path.join(ARGS["out"], "_vorschau"); os.makedirs(vdir, exist_ok=True)
        sc = bpy.context.scene
        sc.render.engine = "BLENDER_WORKBENCH"
        sc.render.resolution_x, sc.render.resolution_y = 420, 560
        cam_data = bpy.data.cameras.new("vorschau"); cam = bpy.data.objects.new("vorschau", cam_data)
        sc.collection.objects.link(cam); sc.camera = cam
        cam_data.lens = 50
        for name in POSEN:
            pose(name)
            for view, loc, rotz in (("vorn", (0, -5.2, 0.95), 0), ("seite", (5.2, 0, 0.95), 90)):
                cam.location = loc; cam.rotation_euler = (math.radians(90), 0, math.radians(rotz))
                sc.render.filepath = os.path.join(vdir, "%s_%s.png" % (name, view))
                bpy.ops.render.render(write_still=True)
        bpy.data.objects.remove(cam, do_unlink=True)
    reset()

    # Kleidung ab Fassung 1.7: nur die Farbtextur (der Stoff kommt aus dem Shader), hoechstens 1024 Bildpunkte
    slim = []
    for o, e in zip(equipped, equip_info):
        if e["art"] != "Clothes" or "casualsuit" in e["datei"]: continue
        for slot in o.material_slots:
            m = slot.material
            if not m or not m.use_nodes: continue
            nt = m.node_tree
            keep = set()
            for b in [n for n in nt.nodes if n.type == "BSDF_PRINCIPLED"]:
                stack = [l.from_node for l in b.inputs["Base Color"].links]
                while stack:
                    n = stack.pop()
                    if n in keep: continue
                    keep.add(n)
                    for i in n.inputs:
                        stack += [l.from_node for l in i.links]
            for n in list(nt.nodes):
                if (n.type == "TEX_IMAGE" and n not in keep) or n.type in ("NORMAL_MAP", "BUMP"):
                    nt.nodes.remove(n)
            for n in keep:
                if n.type == "TEX_IMAGE" and n.image and max(n.image.size) > 1024:
                    w, h = n.image.size
                    k = 1024.0 / max(w, h)
                    n.image.scale(max(1, int(round(w * k))), max(1, int(round(h * k))))
                    n.image.pack()
                    slim.append("%s %dx%d" % (n.image.name, w, h))
    log("Kleidung: nur Farbtexturen, verkleinert: %s" % (", ".join(slim) or "keine"))

    # Material als Platzhalter (Haut kommt in Phase 4 aus eigenem Shader)
    mat = bpy.data.materials.get("Haut") or bpy.data.materials.new("Haut")
    mat.diffuse_color = (0.80, 0.60, 0.50, 1.0)
    basemesh.data.materials.clear(); basemesh.data.materials.append(mat)
    basemesh.data.shade_smooth() if hasattr(basemesh.data, "shade_smooth") else None

    # Eigenschaften fuer den Lader
    basemesh["avatar_version"] = VERSION
    basemesh["avatar_regler_mitte"] = 0.5

    # Export
    os.makedirs(ARGS["out"], exist_ok=True)
    glb = os.path.join(ARGS["out"], ARGS["name"] + ".glb")
    opts = dict(filepath=glb, export_format="GLB", use_selection=False, export_yup=True,
                export_apply=False, export_animations=False, export_morph=True,
                export_morph_normal=False, export_morph_tangent=False, export_skins=True,
                export_all_influences=False, export_def_bones=True, export_materials="EXPORT",
                export_texcoords=True, export_normals=True, export_tangents=False,
                export_extras=True, export_cameras=False, export_lights=False, export_attributes=True,
                export_rest_position_armature=True, export_reset_pose_bones=True,
                export_copyright="Grundnetz und Inhalte: MakeHuman/MPFB2, CC0")
    props = bpy.ops.export_scene.gltf.get_rna_type().properties.keys()
    unknown = [k for k in opts if k not in props]
    for k in unknown:
        opts.pop(k)
    bpy.ops.export_scene.gltf(**opts)
    log("Export: %s (%.1f MB)" % (glb, os.path.getsize(glb) / 1048576.0))

    refp = os.path.join(ARGS["out"], ARGS["name"] + ".referenz.bin")
    with open(refp, "wb") as f:
        f.write(bytes(ref_bin))
    rigp = os.path.join(ARGS["out"], ARGS["name"] + ".rig.json")
    with open(rigp, "w", encoding="utf-8") as f:
        json.dump({"knochen": bone_names, "achsen": "glTF (x, z, -y aus Blender)",
                   "ziele": joint_delta, "posen": posen,
                   "posen_hinweis": "je Knochen D = Pose * Ruhe^-1 (4x4 spaltenweise); Skin = D, Gelenk in Pose = D * Ruhe",
                   "referenz_gelenke": [{"person": REFERENZ[i]["name"], "koepfe": ref_joints[i]} for i in range(len(REFERENZ))]},
                  f, ensure_ascii=False, separators=(",", ":"))
    klp = os.path.join(ARGS["out"], ARGS["name"] + ".kleidung.json")
    with open(klp, "w", encoding="utf-8") as f:
        json.dump({"fassung": VERSION, "hinweis": "Bit je Kleidungsstueck im Merkmal _VERDECKT des Koerpers", "teile": kleidung},
                  f, ensure_ascii=False, indent=1)
    poserefp = os.path.join(ARGS["out"], ARGS["name"] + ".referenz_pose.bin")
    with open(poserefp, "wb") as f:
        f.write(bytes(pose_ref))

    # Was wirklich in der Datei steht (glTF teilt Ecken an UV-Naehten und Normalenkanten)
    import struct
    with open(glb, "rb") as f:
        head = f.read(20)
        jlen = struct.unpack("<I", head[12:16])[0]
        gj = json.loads(f.read(jlen).decode("utf-8"))
    body = max(gj["meshes"], key=lambda m: len(m["primitives"][0].get("targets", [])))
    prim = body["primitives"][0]
    glb_info = {"ecken": gj["accessors"][prim["attributes"]["POSITION"]]["count"],
                "dreiecke": gj["accessors"][prim["indices"]]["count"] // 3,
                "attribute": sorted(prim["attributes"].keys()),
                "morphziele": len(prim.get("targets", [])),
                "gelenke": len(gj["skins"][0]["joints"]) if gj.get("skins") else 0,
                "duenn_besetzt": sum(1 for a in gj["accessors"] if "sparse" in a),
                "netze": [m.get("name", "?") for m in gj["meshes"]],
                "bilder": len(gj.get("images", []))}

    # Protokoll
    me = basemesh.data
    me.calc_loop_triangles()
    keys = [k.name for k in me.shape_keys.key_blocks][1:]
    proto = {
        "werkzeug": "avatar_build.py " + VERSION,
        "blender": bpy.app.version_string,
        "mpfb": mpfb_version(), "mpfb_modul": MPFB,
        "pakete": PACKS,
        "datei": os.path.basename(glb),
        "sha256": sha256(glb),
        "bytes": os.path.getsize(glb),
        "einheit": "Meter, Y oben (glTF), Fuesse auf y = 0",
        "netz": {"ecken_blender": len(me.vertices), "dreiecke": len(me.loop_triangles),
                  "flaechen": len(me.polygons), "uv": len(me.uv_layers)},
        "glb": glb_info,
        "rig": {"name": "game_engine", "knochen": len(rig.data.bones),
                 "namen": [b.name for b in rig.data.bones]},
        "morphziele": {"anzahl": len(keys),
                        "makro": [k for k in keys if k.startswith("macro.")],
                        "gesicht": [k for k in keys if k.startswith("face.")],
                        "formen": [k for k in keys if k.startswith(("arkit.", "expr."))],
                        "formen_quelle": expr_source,
                        "viseme": [k for k in keys if k.startswith("visem.")]},
        "makro_hinweis": "Makros ueber das Gitter Geschlecht (w 0, m 1) x Alter (baby 0, kind 0,1875, jung 0,5, alt 1): Ecke macro.<g>.<a> mit dem Eckengewicht, dazu macro.<g>.<a>.<regler>.min/max mit Eckengewicht * (0,5-v)*2 bzw. (v-0,5)*2. Morph-Normalen nicht exportiert: der Lader rechnet die Normalen nach dem Mischen.",
        "ausstattung": equip_info,
        "linearisierung": linear,
        "rig_datei": {"datei": os.path.basename(rigp), "sha256": sha256(rigp), "posen": POSEN,
                      "ziele_mit_gelenken": len(joint_delta)},
        "referenz_pose": {"datei": os.path.basename(poserefp), "sha256": sha256(poserefp), "posen": POSE_REF,
                          "format": "float32 LE, je Pose je Blender-Ecke des Koerpers x, y, z in glTF-Achsen; Blender-Skinning mit Volumenerhalt (DQS), Mitte aller Regler",
                          "ecken": len(basemesh.data.vertices)},
        "referenz": {"datei": os.path.basename(refp), "sha256": sha256(refp),
                      "format": "float32 LE, je Person je Netz (Reihenfolge 'netze') je Blender-Ecke x, y, z in glTF-Achsen",
                      "netze": ref_layout, "personen": ref_info},
        "fehlt": missing,
        "exportoptionen_unbekannt": unknown,
        "quellen": [{"was": "MPFB2-Grundnetz, Ziele, Rig game_engine", "lizenz": "CC0",
                      "von": "MakeHuman Community"}] +
                   [{"was": e["datei"], "lizenz": e.get("lizenz", "siehe Paket"), "von": e["quelle"]} for e in equip_info],
        "dauer_s": round(time.time() - T0, 1),
        "protokoll": LOG,
    }
    return glb, proto

# ---------------------------------------------------------------- Zweiter Lauf
def second_run(glb):
    tmp = os.path.join(ARGS["out"], "_zweiter_lauf")
    os.makedirs(tmp, exist_ok=True)
    cmd = [bpy.app.binary_path, "-b", "-P", ARGS["script"], "--", "--out", tmp, "--name", ARGS["name"]]
    log("Zweiter Lauf: " + " ".join(cmd))
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=1800)
    other = os.path.join(tmp, ARGS["name"] + ".glb")
    ok = r.returncode == 0 and os.path.isfile(other)
    h2 = sha256(other) if ok else None
    res = {"zweiter_lauf_sha256": h2, "gleich": bool(ok and h2 == sha256(glb)),
           "rueckgabe": r.returncode}
    if not ok:
        res["fehler"] = (r.stdout[-2000:] + "\n" + r.stderr[-2000:])
    if not res["gleich"]:
        res["ordner"] = tmp            # zum Vergleichen liegen lassen
        return res
    for f in os.listdir(tmp):
        try: os.remove(os.path.join(tmp, f))
        except Exception: pass
    try: os.rmdir(tmp)
    except Exception: pass
    return res

def main():
    glb, proto = build()
    if ARGS["twice"]:
        proto["pruefung"] = second_run(glb)
        log("Pruefsummen gleich: %s" % proto["pruefung"]["gleich"])
        proto["protokoll"] = LOG
    with open(os.path.join(ARGS["out"], ARGS["name"] + ".protokoll.json"), "w", encoding="utf-8") as f:
        json.dump(proto, f, ensure_ascii=False, indent=2, sort_keys=False)
    log("fertig")

main()
