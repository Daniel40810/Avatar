# -*- coding: utf-8 -*-
"""Vorschau der Kleidungs-Kandidaten (Phase 10): Frau und Mann mit je einem Outfit, Workbench, von vorn
und von der Seite. Nur zum Ansehen:  blender -b -P tools/blender/kleidung_vorschau.py -- --out DIR"""
import bpy, sys, os, importlib, math
argv = sys.argv[sys.argv.index("--") + 1:] if "--" in sys.argv else []
OUT = argv[argv.index("--out") + 1] if "--out" in argv else os.getcwd()
os.makedirs(OUT, exist_ok=True)
MP = "bl_ext.blender_org.mpfb"
def svc(m, c): return getattr(importlib.import_module(MP + "." + m), c)
HS = svc("services.humanservice", "HumanService"); TS = svc("services.targetservice", "TargetService")
AS = svc("services.assetservice", "AssetService"); HOP = svc("entities.objectproperties", "HumanObjectProperties")
OUTFITS = {
    "vorlage":  (0.0, ["toigo_fisherman_sweater", "culturalibre_hero-heroine_hood_1", "toigo_wool_pants", "toigo_ankle_boots_female"]),
    "vorlage2": (0.0, ["toigo_fisherman_sweater", "culturalibre_hero-heroine_hood_2", "toigo_wool_pants", "toigo_ankle_boots_female"]),
    "regen":    (1.0, ["male_worksuit01", "scailman_semitransparent_water_boots"]),
    "sport":    (0.0, ["female_sportsuit01", "scailman_semitransparent_water_boots"]),
    "sommer":   (0.0, ["toigo_basic_tucked_t-shirt", "cortu_jeans_shorts", "toigo_flats"]),
    "winter":   (1.0, ["toigo_fisherman_sweater", "toigo_wool_pants", "culturalibre_male_boots", "jujube_newsboy_cap"]),
    "1362":     (1.0, ["donitz_monk_robe_hood", "rehmanpolanski_viking_boots"]),
    "1362b":    (1.0, ["donitz_monk_robe_hood_down", "rehmanpolanski_viking_boots"]),
    "wikinger": (1.0, ["rehmanpolanski_viking_tunic", "rehmanpolanski_viking_pants", "rehmanpolanski_viking_boots"]),
    "cargo":    (1.0, ["elvs_crude_t-shirt_male", "cortu_cargo_pants", "toigo_ankle_boots_male"]),
}
def human(g):
    for o in list(bpy.data.objects): bpy.data.objects.remove(o, do_unlink=True)
    d = TS.get_default_macro_info_dict(); d["gender"] = g
    d["race"] = {"asian": 1/3, "caucasian": 1/3, "african": 1/3}
    b = HS.create_human(mask_helpers=True, detailed_helpers=True, extra_vertex_groups=True, feet_on_ground=True, scale=0.1, macro_detail_dict=d)
    return b
sc = bpy.context.scene
sc.render.engine = "BLENDER_WORKBENCH"
sc.display.shading.light = "STUDIO"; sc.display.shading.color_type = "MATERIAL"
sc.render.resolution_x = 520; sc.render.resolution_y = 900
info = []
for name, (g, items) in OUTFITS.items():
    b = human(g)
    for it in items:
        p = AS.find_asset_absolute_path(it + "/" + it + ".mhclo", "clothes")
        if not p: info.append("%s: fehlt %s" % (name, it)); continue
        try:
            o = HS.add_mhclo_asset(p, b, asset_type="Clothes", subdiv_levels=0, set_up_rigging=False, interpolate_weights=False, import_subrig=False, import_weights=False)
            info.append("%s: %s %d Ecken" % (name, it, len(o.data.vertices)))
        except Exception as e:
            info.append("%s: %s Fehler %s" % (name, it, e))
    cam = bpy.data.objects.new("cam", bpy.data.cameras.new("cam")); sc.collection.objects.link(cam); sc.camera = cam
    cam.data.lens = 50
    for view, ang in (("vorn", 0), ("seite", 60)):
        a = math.radians(ang)
        cam.location = (math.sin(a) * 3.6, -math.cos(a) * 3.6, 0.9)
        cam.rotation_euler = (math.radians(90), 0, a)
        sc.render.filepath = os.path.join(OUT, "%s_%s.png" % (name, view))
        bpy.ops.render.render(write_still=True)
open(os.path.join(OUT, "info.txt"), "w", encoding="utf-8").write("\n".join(info))
