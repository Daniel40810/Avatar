package com.dan.avatar;

import java.util.ArrayList;
import java.util.List;

/**
 * Was eine Person trägt (Phase 10): Teile aus dem Kleiderschrank ({@link Wardrobe}) mit Stoff und Farbe,
 * dazu ob eine Kapuze auf dem Kopf ist. Presets für die Abnahme und {@link #forMonth} nach Monat, Rolle
 * und Epoche (wie {@code Outfit.forMonth} aus {@code com.dan.figure}).
 *
 * <pre>
 *   Outfit o = Outfit.preset("Strickkapuze");
 *   Outfit w = Outfit.forMonth(1, Outfit.Role.DRAUSSEN, Outfit.Epoch.HEUTE, seed);
 * </pre>
 */
public final class Outfit {

    /** Ein Teil: Netz (Teil des Namens im Grundkörper), Stoff, Farbe (linear RGB oder null = Stoff). */
    public static final class Piece {
        public final String mesh;
        public final Fabric fabric;
        public final double[] color;
        public Piece(String mesh, Fabric fabric, double[] color) { this.mesh = mesh; this.fabric = fabric; this.color = color; }
        public double[] colorOrDefault() { return color != null ? color : (fabric != null ? fabric.color : new double[]{0.5, 0.5, 0.5}); }
        @Override public String toString() { return mesh + (fabric == null ? "" : " (" + fabric + ")"); }
    }

    public enum Role { ALLTAG, DRAUSSEN, ARBEIT }
    public enum Epoch { HEUTE, MITTELALTER_1362, WIKINGERZEIT }

    public final String name;
    public final List<Piece> pieces = new ArrayList<>();

    public Outfit(String name) { this.name = name; }

    public Outfit add(String mesh, Fabric f, double[] color) { pieces.add(new Piece(mesh, f, color)); return this; }
    public Outfit add(String mesh, Fabric f) { return add(mesh, f, null); }

    /** Ist eine Kapuze aufgesetzt? (Schutz vor Regen, Haar darunter) */
    public boolean hoodUp() {
        for (Piece p : pieces) if (p.mesh.equals(HOOD_UP)) return true;
        return false;
    }

    /** Hat das Outfit eine Kapuze (auf oder ab)? */
    public boolean hasHood() {
        for (Piece p : pieces) if (p.mesh.equals(HOOD_UP) || p.mesh.equals(HOOD_DOWN)) return true;
        return false;
    }

    /**
     * Dasselbe Outfit mit aufgesetzter ({@code up}) oder abgenommener Kapuze (Netz {@link #HOOD_UP} oder
     * {@link #HOOD_DOWN}, gleicher Stoff, gleiche Farbe). Ohne Kapuze: dieses Outfit.
     */
    public Outfit withHood(boolean up) {
        if (!hasHood() || hoodUp() == up) return this;
        Outfit o = new Outfit(name);
        for (Piece p : pieces) {
            boolean hood = p.mesh.equals(HOOD_UP) || p.mesh.equals(HOOD_DOWN);
            o.pieces.add(hood ? new Piece(up ? HOOD_UP : HOOD_DOWN, p.fabric, p.color) : p);
        }
        return o;
    }

    /** Teil für dieses Netz oder null. */
    public Piece piece(String meshName) {
        if (meshName == null) return null;
        for (Piece p : pieces) if (meshName.equals(p.mesh)) return p;
        return null;
    }

    public Outfit copy(String newName) {
        Outfit o = new Outfit(newName);
        o.pieces.addAll(pieces);
        return o;
    }

    // ------------------------------------------------------------ Netze (Fassung 1.7 der Blender-Kette)
    public static final String CASUAL = "male_casualsuit01", SWEATER = "sweater_fisherman", HOOD_UP = "Monks_Hood",
            HOOD_DOWN = "Monks_Hood_Down", ROBE = "Monks_Robe", WOOL_PANTS = "pants_wool", CARGO = "cargo_pants",
            SHORTS = "jean_shorts", TSHIRT = "t_shirt_basic_tucked", BOOTS = "boots_ankle", RUBBER_BOOTS = "hero_boots_2",
            SHOES = "mj_shoes", VIKING_TUNIC = "tunicviking", VIKING_PANTS = "pantsviking", VIKING_BOOTS = "bootsviking";

    static double[] rgb(double r, double g, double b) { return new double[]{r, g, b}; }

    /** Namen der Presets. */
    public static final String[] PRESETS = {"Strickkapuze", "Regenjacke", "Winter", "Sommer", "1362", "Wikingerzeit", "Alltag", "ohne"};

    /** Preset nach Namen (siehe {@link #PRESETS}). */
    public static Outfit preset(String name) {
        switch (name) {
            case "Strickkapuze": // wie die Vorlage: dunkle Wolle mit Kapuze, Wasserperlen auf dem Strick
                return new Outfit(name).add(SWEATER, Fabric.STRICK, rgb(0.014, 0.016, 0.018)).add(HOOD_UP, Fabric.STRICK, rgb(0.014, 0.016, 0.018))
                        .add(WOOL_PANTS, Fabric.WOLLE, rgb(0.03, 0.03, 0.035)).add(BOOTS, Fabric.LEDER);
            case "Regenjacke":   // Ölzeug (Friesennerz): Kittel und Kapuze gelb, Gummistiefel
                return new Outfit(name).add(SWEATER, Fabric.OELZEUG).add(HOOD_UP, Fabric.OELZEUG)
                        .add(CARGO, Fabric.BAUMWOLLE, rgb(0.06, 0.07, 0.09)).add(RUBBER_BOOTS, Fabric.GUMMI);
            case "Winter":
                return new Outfit(name).add(SWEATER, Fabric.STRICK, rgb(0.45, 0.40, 0.32)).add(WOOL_PANTS, Fabric.WOLLE)
                        .add(BOOTS, Fabric.LEDER);
            case "Sommer":
                return new Outfit(name).add(TSHIRT, Fabric.BAUMWOLLE, rgb(0.62, 0.62, 0.60)).add(SHORTS, Fabric.DENIM)
                        .add(SHOES, Fabric.LEINEN, rgb(0.55, 0.52, 0.45));
            case "1362":         // Kutte aus ungefärbter Wolle mit Kapuze, Wendeschuhe
                return new Outfit(name).add(ROBE, Fabric.WOLLE, rgb(0.20, 0.16, 0.11)).add(HOOD_UP, Fabric.WOLLE, rgb(0.20, 0.16, 0.11))
                        .add(VIKING_BOOTS, Fabric.LEDER);
            case "Wikingerzeit": // Tunika aus Leinen (mit Waid gefärbt), Hose aus Wolle, Lederschuhe
                return new Outfit(name).add(VIKING_TUNIC, Fabric.LEINEN, rgb(0.16, 0.22, 0.30)).add(VIKING_PANTS, Fabric.WOLLE, rgb(0.25, 0.20, 0.14))
                        .add(VIKING_BOOTS, Fabric.LEDER);
            case "Alltag":
                return new Outfit(name).add(CASUAL, null, null);      // wie bis Phase 9 (Textur der Kleidung)
            default:
                return new Outfit("ohne");
        }
    }

    /**
     * Outfit nach Monat (1–12), Rolle und Epoche, mit {@code seed} für kleine Abweichungen in der Farbe.
     * Heute: Dezember bis Februar Winter, Juni bis August Sommer, sonst draußen Ölzeug oder Strick,
     * drinnen Alltag. Die Epochen haben ihre Tracht, im Winter mit Kapuze.
     */
    public static Outfit forMonth(int month, Role role, Epoch epoch, long seed) {
        boolean winter = month == 12 || month <= 2, summer = month >= 6 && month <= 8;
        Outfit o;
        switch (epoch) {
            case MITTELALTER_1362 -> {
                o = preset("1362");
                if (!winter && role != Role.DRAUSSEN) { o = o.copy("1362"); o.pieces.removeIf(p -> p.mesh.equals(HOOD_UP)); o.add(HOOD_DOWN, Fabric.WOLLE, rgb(0.20, 0.16, 0.11)); }
            }
            case WIKINGERZEIT -> o = preset("Wikingerzeit");
            default -> {
                if (winter) o = preset("Winter");
                else if (summer) o = preset(role == Role.ARBEIT ? "Alltag" : "Sommer");
                else o = preset(role == Role.DRAUSSEN ? ((seed & 1) == 0 ? "Regenjacke" : "Strickkapuze") : "Alltag");
            }
        }
        // kleine Abweichung der Farben je Person (±12 % Helligkeit)
        Outfit r = new Outfit(o.name);
        double k = 0.88 + 0.24 * ((seed * 0x9E3779B97F4A7C15L >>> 11) * 0x1.0p-53);
        for (Piece p : o.pieces) {
            double[] c = p.color == null ? null : new double[]{p.color[0] * k, p.color[1] * k, p.color[2] * k};
            r.add(p.mesh, p.fabric, c);
        }
        return r;
    }

    @Override public String toString() { return name + " " + pieces; }
}
