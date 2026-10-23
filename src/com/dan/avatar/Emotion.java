package com.dan.avatar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Der Gefühlsraum (F16 A): Valenz (−1 unangenehm … +1 angenehm) und Erregung (−1 ruhig, müde …
 * +1 aufgeregt) mit benannten Gefühlen. Jedes Gefühl ist eine Mischung der ARKit-Gesichtsformen
 * (nach FACS, Ekman und Friesen), dazu Haltung, Blick, Lidschlag und Atem. Ein Punkt im Raum mischt
 * die benannten Gefühle mit einem Gauß-Kern; in der Mitte liegt das neutrale Gesicht.
 *
 * <p>Die Formen stehen ohne Seite ({@code mouthSmile}) und werden für beide Seiten gesetzt; die
 * Asymmetrie kommt später je Person dazu ({@link Behavior}).
 */
public final class Emotion {

    /** Ein benanntes Gefühl. */
    public static final class Def {
        public final String name;
        public final double valence, arousal;
        /** Gesichtsformen 0…1 (ARKit-Name, ohne Left/Right wenn beidseitig). */
        public final Map<String, Double> forms = new LinkedHashMap<>();
        /** Kopf nach unten (Grad, + = nicken), Blick nach unten (Grad), Kopf zur Seite geneigt (Grad). */
        public double headDown, gazeDown, headTilt;
        /** Abstand der Lidschläge (×), Atemtempo (×), Atemtiefe (×), Blicksprünge (×), Blick abwenden (×). */
        public double blinkGap = 1, breathRate = 1, breathDepth = 1, saccadeRate = 1, aversion = 1;
        /** Schultern hoch (Grad). */
        public double shoulders;

        Def(String name, double v, double a) { this.name = name; valence = v; arousal = a; }

        Def f(String form, double w) { forms.put(form, w); return this; }
    }

    /** Alle benannten Gefühle; das erste ist neutral (Mitte). */
    public static final List<Def> NAMED;
    private static final Map<String, Def> BY_NAME = new LinkedHashMap<>();

    static {
        List<Def> l = new ArrayList<>();
        l.add(new Def("neutral", 0, 0));
        Def d;
        d = new Def("ruhig", 0.25, -0.45).f("mouthSmile", 0.10).f("eyeSquint", 0.06).f("browInnerUp", 0.03);
        d.blinkGap = 1.1; d.breathRate = 0.9; d.breathDepth = 1.1; d.saccadeRate = 0.8; l.add(d);
        d = new Def("nachdenklich", -0.15, -0.3).f("browDown", 0.22).f("browInnerUp", 0.20).f("mouthPress", 0.30)
                .f("mouthRollLower", 0.12).f("eyeSquint", 0.22).f("mouthLeft", 0.08);
        d.blinkGap = 1.35; d.saccadeRate = 0.7; d.aversion = 3.0; d.headTilt = 3; l.add(d);
        d = new Def("freudig", 0.8, 0.45).f("mouthSmile", 0.78).f("cheekSquint", 0.55).f("eyeSquint", 0.32)
                .f("mouthDimple", 0.25).f("jawOpen", 0.10).f("mouthUpperUp", 0.12).f("browInnerUp", 0.06);
        d.headDown = -2.5; d.breathRate = 1.1; d.saccadeRate = 1.15; l.add(d);
        d = new Def("überrascht", 0.15, 0.9).f("browInnerUp", 0.85).f("browOuterUp", 0.80).f("eyeWide", 0.75)
                .f("jawOpen", 0.32).f("mouthFunnel", 0.12).f("mouthLowerDown", 0.10);
        d.headDown = -3.5; d.blinkGap = 2.2; d.breathRate = 1.25; d.breathDepth = 1.4; d.saccadeRate = 0.6; l.add(d);
        d = new Def("entschlossen", 0.35, 0.5).f("browDown", 0.38).f("mouthPress", 0.45).f("eyeSquint", 0.28)
                .f("jawForward", 0.10).f("mouthShrugLower", 0.20).f("noseSneer", 0.05);
        d.headDown = -2.0; d.blinkGap = 1.25; d.saccadeRate = 0.75; d.aversion = 0.4; l.add(d);
        d = new Def("traurig", -0.7, -0.4).f("browInnerUp", 0.70).f("mouthFrown", 0.50).f("mouthShrugLower", 0.30)
                .f("mouthLowerDown", 0.08).f("eyeBlink", 0.14).f("eyeSquint", 0.10).f("mouthPress", 0.12);
        d.headDown = 6; d.gazeDown = 8; d.blinkGap = 0.9; d.breathRate = 0.85; d.breathDepth = 1.2; d.saccadeRate = 0.7; d.aversion = 1.8; d.shoulders = -1.2; l.add(d);
        d = new Def("müde", -0.25, -0.85).f("eyeBlink", 0.34).f("browInnerUp", 0.14).f("jawOpen", 0.04)
                .f("mouthShrugUpper", 0.06).f("eyeSquint", 0.05);
        d.headDown = 4; d.gazeDown = 4; d.blinkGap = 0.6; d.breathRate = 0.8; d.breathDepth = 1.3; d.saccadeRate = 0.55; d.shoulders = -1.0; l.add(d);
        d = new Def("frierend", -0.45, 0.35).f("mouthStretch", 0.30).f("mouthPress", 0.22).f("browDown", 0.15)
                .f("browInnerUp", 0.22).f("eyeSquint", 0.30).f("noseSneer", 0.10).f("mouthRollUpper", 0.10);
        d.headDown = 3; d.blinkGap = 0.85; d.breathRate = 1.2; d.saccadeRate = 1.1; d.shoulders = 4.5; l.add(d);
        d = new Def("ärgerlich", -0.75, 0.55).f("browDown", 0.75).f("noseSneer", 0.32).f("eyeSquint", 0.38)
                .f("eyeWide", 0.18).f("mouthPress", 0.40).f("mouthRollUpper", 0.10).f("jawForward", 0.08);
        d.headDown = 2; d.blinkGap = 1.2; d.breathRate = 1.3; d.breathDepth = 1.3; d.saccadeRate = 0.8; d.aversion = 0.3; l.add(d);
        d = new Def("ängstlich", -0.42, 0.88).f("browInnerUp", 0.72).f("browOuterUp", 0.30).f("eyeWide", 0.60)
                .f("mouthStretch", 0.40).f("jawOpen", 0.10).f("browDown", 0.10).f("mouthFrown", 0.10);
        d.headDown = 1; d.blinkGap = 0.8; d.breathRate = 1.45; d.breathDepth = 0.8; d.saccadeRate = 1.6; d.aversion = 1.5; d.shoulders = 2.5; l.add(d);
        for (Def x : l) BY_NAME.put(x.name, x);
        NAMED = Collections.unmodifiableList(l);
    }

    private Emotion() {}

    /** Gefühl nach Namen (null, wenn unbekannt). */
    public static Def named(String name) { return BY_NAME.get(name); }

    /** Namen aller benannten Gefühle. */
    public static String[] names() {
        String[] s = new String[NAMED.size()];
        for (int i = 0; i < s.length; i++) s[i] = NAMED.get(i).name;
        return s;
    }

    /** Die 52 Gesichtsformen nach ARKit (wie im Modell, ohne Präfix). */
    public static final String[] ARKIT = {"browDownLeft", "browDownRight", "browInnerUp", "browOuterUpLeft", "browOuterUpRight", "cheekPuff",
        "cheekSquintLeft", "cheekSquintRight", "eyeBlinkLeft", "eyeBlinkRight", "eyeLookDownLeft", "eyeLookDownRight", "eyeLookInLeft",
        "eyeLookInRight", "eyeLookOutLeft", "eyeLookOutRight", "eyeLookUpLeft", "eyeLookUpRight", "eyeSquintLeft", "eyeSquintRight",
        "eyeWideLeft", "eyeWideRight", "jawForward", "jawLeft", "jawOpen", "jawRight", "mouthClose", "mouthDimpleLeft", "mouthDimpleRight",
        "mouthFrownLeft", "mouthFrownRight", "mouthFunnel", "mouthLeft", "mouthLowerDownLeft", "mouthLowerDownRight", "mouthPressLeft",
        "mouthPressRight", "mouthPucker", "mouthRight", "mouthRollLower", "mouthRollUpper", "mouthShrugLower", "mouthShrugUpper",
        "mouthSmileLeft", "mouthSmileRight", "mouthStretchLeft", "mouthStretchRight", "mouthUpperUpLeft", "mouthUpperUpRight",
        "noseSneerLeft", "noseSneerRight", "tongueOut"};
    private static final java.util.Set<String> ARKIT_SET = new java.util.HashSet<>(java.util.Arrays.asList(ARKIT));

    /** Gibt es die Form je Seite (…Left, …Right)? */
    public static boolean isSided(String form) { return ARKIT_SET.contains(form + "Left"); }

    /** Gibt es die Form (mit oder ohne Seite)? */
    public static boolean exists(String form) { return ARKIT_SET.contains(form) || isSided(form); }

    /** Breite des Kerns im Gefühlsraum. */
    static final double SIGMA = 0.2;

    /** Anteile der benannten Gefühle an einem Punkt (Summe 1), Gauß-Kern. */
    public static double[] blend(double valence, double arousal) {
        double[] w = new double[NAMED.size()];
        double s = 0;
        for (int i = 0; i < w.length; i++) {
            Def d = NAMED.get(i);
            double dv = valence - d.valence, da = arousal - d.arousal;
            w[i] = Math.exp(-(dv * dv + da * da) / (2 * SIGMA * SIGMA));
            s += w[i];
        }
        for (int i = 0; i < w.length; i++) w[i] /= s;
        return w;
    }

    /** Mischung an einem Punkt: Formen (beidseitig, ohne Präfix) und Kenngrößen, mal Stärke. */
    public static Mix mix(double valence, double arousal, double strength) {
        return mix(blend(valence, arousal), strength);
    }

    static Mix mix(double[] w, double strength) {
        Mix m = new Mix();
        for (int i = 0; i < w.length; i++) {
            if (w[i] < 1e-4) continue;
            Def d = NAMED.get(i);
            for (Map.Entry<String, Double> e : d.forms.entrySet()) m.forms.merge(e.getKey(), w[i] * e.getValue() * strength, Double::sum);
            m.headDown += w[i] * d.headDown * strength;
            m.gazeDown += w[i] * d.gazeDown * strength;
            m.headTilt += w[i] * d.headTilt * strength;
            m.shoulders += w[i] * d.shoulders * strength;
            m.blinkGap += w[i] * Math.log(d.blinkGap) * strength;
            m.breathRate += w[i] * Math.log(d.breathRate) * strength;
            m.breathDepth += w[i] * Math.log(d.breathDepth) * strength;
            m.saccadeRate += w[i] * Math.log(d.saccadeRate) * strength;
            m.aversion += w[i] * Math.log(d.aversion) * strength;
        }
        m.blinkGap = Math.exp(m.blinkGap); m.breathRate = Math.exp(m.breathRate); m.breathDepth = Math.exp(m.breathDepth);
        m.saccadeRate = Math.exp(m.saccadeRate); m.aversion = Math.exp(m.aversion);
        return m;
    }

    /** Ergebnis einer Mischung. */
    public static final class Mix {
        public final Map<String, Double> forms = new LinkedHashMap<>();
        public double headDown, gazeDown, headTilt, shoulders;
        /** Faktoren (1 = neutral). */
        public double blinkGap, breathRate, breathDepth, saccadeRate, aversion;
    }
}
