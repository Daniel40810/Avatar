# -*- coding: utf-8 -*-
"""
haut_build.py  –  Hauttexturen des Avatars (Phase 4, F6 A)

Nimmt die sechs hellen Grundhäute aus den MakeHuman-Systempaketen (CC0) –
jung, mittel, alt × Frau, Mann –, speichert sie als JPEG (2048², Güte 92)
nach src/com/dan/avatar/res/haut/ und schreibt haut.json mit Quelle,
Lizenz, Größe und SHA-256 je Bild.

Die Farbe der Haut kommt nicht aus diesen Bildern: Java teilt jedes Bild durch
seine mittlere Hautfarbe und behält nur das Muster (Lippen, Lider, Brustwarzen,
Adern, Flecken). Den Ton rechnet der Shader aus Melanin und Rötung der Person.
Alles Feine (Poren, Fältchen, Sommersprossen, Bartschatten) kommt aus den
Detailkarten von com.dan.avatar.SkinDetail.

Ohne Fenster:
    blender -b -P tools/blender/haut_build.py -- [--out DIR] [--twice]
"""

import bpy, sys, os, json, hashlib, subprocess

VERSION = "1.0"
HAEUTE = [
    ("jung_w", "young_caucasian_female"), ("jung_m", "young_caucasian_male"),
    ("mittel_w", "middleage_caucasian_female"), ("mittel_m", "middleage_caucasian_male"),
    ("alt_w", "old_caucasian_female"), ("alt_m", "old_caucasian_male"),
]
GROESSE = 2048
GUETE = 92


def parse_args():
    argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
    here = os.path.dirname(os.path.abspath(__file__)) if "__file__" in globals() else os.getcwd()
    project = os.path.abspath(os.path.join(here, "..", ".."))
    a = {"out": os.path.join(project, "src", "com", "dan", "avatar", "res", "haut"), "twice": False,
         "script": os.path.join(here, "haut_build.py")}
    i = 0
    while i < len(argv):
        if argv[i] == "--out": a["out"] = argv[i + 1]; i += 2
        elif argv[i] == "--twice": a["twice"] = True; i += 1
        else: i += 1
    return a


def skin_root():
    # MPFB-Datenordner des Nutzers (dort liegen die installierten Häute)
    for m in ("bl_ext.blender_org.mpfb", "bl_ext.user_default.mpfb"):
        try:
            mod = __import__(m + ".services.locationservice", fromlist=["LocationService"])
            d = mod.LocationService.get_user_data("skins")
            if os.path.isdir(d):
                return d
        except Exception:
            pass
    d = os.path.join(os.environ.get("APPDATA", ""), "Blender Foundation", "Blender", "5.2", "extensions",
                     ".user", "blender_org", "mpfb", "data", "skins")
    return d


def sha(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        h.update(f.read())
    return h.hexdigest()


def build(out):
    os.makedirs(out, exist_ok=True)
    root = skin_root()
    scene = bpy.context.scene
    st = scene.render.image_settings
    st.file_format = "JPEG"
    st.color_mode = "RGB"
    st.quality = GUETE
    # ohne Tonkurve speichern (Blender nimmt sonst AgX und verändert die Farben)
    vs = scene.view_settings
    vs.view_transform = "Standard"
    vs.look = "None"
    vs.exposure = 0.0
    vs.gamma = 1.0
    scene.display_settings.display_device = "sRGB"
    try:
        st.color_management = "FOLLOW_SCENE"
    except Exception:
        pass
    prot = {"version": VERSION, "groesse": GROESSE, "guete": GUETE, "lizenz": "CC0 1.0 (MakeHuman-Systempakete)",
            "quelle": root, "haeute": []}
    for key, name in HAEUTE:
        d = os.path.join(root, name)
        png = sorted(f for f in os.listdir(d) if f.lower().endswith(".png"))[0]
        src = os.path.join(d, png)
        img = bpy.data.images.load(src, check_existing=False)
        if tuple(img.size) != (GROESSE, GROESSE):
            img.scale(GROESSE, GROESSE)
        dst = os.path.join(out, "haut_" + key + ".jpg")
        img.save_render(dst, scene=scene)
        bpy.data.images.remove(img)
        prot["haeute"].append({"schluessel": key, "mpfb": name, "datei": png, "quelle_sha256": sha(src),
                               "ziel": os.path.basename(dst), "bytes": os.path.getsize(dst), "sha256": sha(dst)})
        print("Haut", key, "<-", name, os.path.getsize(dst) // 1024, "KB")
    with open(os.path.join(out, "haut.json"), "w", encoding="utf-8") as f:
        json.dump(prot, f, ensure_ascii=False, indent=1, sort_keys=True)
    return prot


def main():
    a = parse_args()
    prot = build(a["out"])
    if a["twice"]:
        tmp = os.path.join(a["out"], "_zweiter_lauf")
        subprocess.run([bpy.app.binary_path, "-b", "-P", a["script"], "--", "--out", tmp], check=True)
        with open(os.path.join(tmp, "haut.json"), encoding="utf-8") as f:
            p2 = json.load(f)
        gleich = [x["sha256"] for x in prot["haeute"]] == [x["sha256"] for x in p2["haeute"]]
        print("Zweiter Lauf:", "gleich" if gleich else "VERSCHIEDEN")
        for x in p2["haeute"]:
            os.remove(os.path.join(tmp, x["ziel"]))
        os.remove(os.path.join(tmp, "haut.json"))
        os.rmdir(tmp)
        if not gleich:
            sys.exit(2)


if __name__ == "__main__":
    main()
