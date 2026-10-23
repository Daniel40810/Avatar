package com.dan.avatar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Leben, Blick und Gefühl einer Person (Phase 7, F15–F17 A). Läuft ohne Befehl: atmet, blinzelt,
 * lässt den Blick springen, verlagert das Gewicht, zeigt das Gefühl mit Trägheit. Reproduzierbar
 * mit {@code seed}: gleiche Zeitschritte, gleiches Verhalten.
 *
 * <p>Ablauf je Bild:
 * <pre>
 *   b.step(dt);                                  // Zustand weiter (Zeit · Zeitlupe)
 *   b.additive(add);                             // Zugabe zur Pose: Kopf, Atem, Becken
 *   double[][] w = skeleton.pose(pose, rest, true, add);
 *   Map&lt;String, Double&gt; f = b.forms(w, rest, skeleton);   // Gesichtsformen: Gefühl, Lider, Augen
 * </pre>
 * Die Augen werden aus der wirklichen Lage des Kopfes gerechnet: Was der Kopf dreht, gleichen die
 * Augen aus (vestibulo-okulär), beide Augen treffen denselben Punkt (Vergenz).
 *
 * <p>Zahlen vom Menschen (Werkbuch Abschnitt 2, [G] wo geschätzt): Lidschlag rund 15–20 je Minute,
 * Schließen schneller als Öffnen, nach großen Blicksprüngen oft ein Lidschlag; Sakkaden nach der
 * Hauptfolge D = 21 ms + 2,2 ms je Grad (Carpenter), Profil mit kleinstem Ruck; der Kopf folgt dem
 * Auge ab etwa 10–15°; Atmen 12–20 Züge je Minute, Einatmen kürzer als Ausatmen, ab und zu ein Seufzer;
 * alle 5–14 s eine Gewichtsverlagerung im Stand.
 */
public final class Behavior {

    /** Blickziel. */
    public enum Look { CAMERA, POINT, FREE, HORIZON }

    // ------------------------------------------------------------ Einstellungen (von außen)

    /** Leben an: Atmen, Lidschlag, Blicksprünge, Mikrobewegungen. Aus: Standbild mit Gefühl und Blick. */
    public volatile boolean alive = true;
    /** Zeitlupe (1 = Echtzeit). */
    public volatile double timeScale = 1;
    /** Steht die Person (dann Gewichtsverlagerung)? */
    public volatile boolean standing = true;
    /** Kameraposition in Figur-Koordinaten (für {@link Look#CAMERA}). */
    public volatile double[] camera = {0, 1.6, 2.5};
    /** Kälte 0…1 (Phase 8, aus {@link Climate#coldness}): Schultern hoch, Frösteln, Zähneklappern. */
    public volatile double cold;
    /** Regen im Gesicht 0…1 (Phase 8): Augen zusammenkneifen, öfter blinzeln. */
    public volatile double rainOnFace;
    /**
     * Mund beim Sprechen (Phase 16): Viseme nach Formnamen ({@code visem.viseme_PP} → Gewicht) von der Szene, z. B. aus
     * {@code com.dan.voice}. Die Mundformen des Gefühls treten dabei zur Hälfte zurück.
     */
    public volatile Map<String, Double> speech = Map.of();
    /** Spricht die Person gerade (Phase 16)? Dann blinzelt sie öfter [G: rund 1,5-mal so oft]. */
    public volatile boolean talking;
    /**
     * Musik (Phase 17): die Schläge stetig gezählt (z. B. {@code com.dan.beat.Rhythm#beatAt}; NaN = keine Musik), die
     * Nummer eines Schlags, der eine Eins ist, wie stark die Person mitgeht (0 … 1) und wie ruhig die Stelle ist (0 … 1;
     * bei Ruhe schließen sich die Augen).
     */
    public volatile double musicBeat = Double.NaN, musicDown, groove, musicCalm;
    private volatile Look look = Look.CAMERA;
    private volatile double[] point = {0, 1.5, 1.5};

    // ------------------------------------------------------------ Zustand

    private final long seed;
    private final Person.Rng rng;
    private double t;
    /** Augen zu bei ruhiger Musik (geglättet). */
    private double calmLid;
    // Gefühl
    private double tv, ta, ts = 1;                 // Ziel: Valenz, Erregung, Stärke
    private double sv, sa;                          // geglättet (für Atem und Kenngrößen)
    private Emotion.Mix target = Emotion.mix(0, 0, 1);
    private final Map<String, Double> targetSided = new LinkedHashMap<>();
    private final Map<String, Double> cur = new LinkedHashMap<>();
    private double pHeadDown, pGazeDown, pHeadTilt, pShoulders, pBlinkGap = 1, pBreathRate = 1, pBreathDepth = 1, pSacc = 1, pAvert = 1;
    private final double asym;                      // Seitenunterschied der Mimik (+ links stärker)
    // Mikroausdruck
    private Emotion.Def micro;
    private double microT0 = -1, microDur, microAmp, nextMicro;
    // Lidschlag
    private boolean blinking;
    private double blinkT0, blinkClose, blinkHold, blinkOpen, blinkAmp, nextBlink, lastBlink = -10;
    // Atem
    private double breathPhase, breathRate = 14, breathDepth = 1, nextSigh;
    private boolean sigh;
    // Blick (Grad, Figur: Gier + nach links (+x), Nicken + nach oben)
    private final double[] G = new double[2], Tlast = new double[2], offset = new double[2];
    private boolean tInit, averted;
    private double nextShift;
    private boolean sacc;
    private final double[] sFrom = new double[2], sTo = new double[2];
    private double sT0, sDur, sEnd = -1, pendingAt = -1;
    // Kopf
    private final double[] H = new double[2], Hv = new double[2], Ht = new double[2];
    // Gewicht
    private double wFrom, wTo, wT0, wDur = 1, nextWeight;
    // Mikrobewegung
    private final Noise nYaw, nPitch, nRoll, nYaw2, nPitch2, nSwayX, nSwayZ;
    // Augen
    private final double[][] cRest = new double[2][];
    private final double[] yaw0 = new double[2], pitch0 = new double[2];
    /** Winkel bei Gewicht 1 (Grad): [Auge][Up, Down, In, Out] → Gier, Nicken. */
    private final double[][] fYaw = new double[2][4], fPitch = new double[2][4];
    private boolean calibrated;
    /** Reichweite der Augen im Kopf (Grad), aus dem Einmessen; den Rest übernimmt der Kopf. */
    private double rangeYaw = 25, rangePitch = 18;
    private final double[] eyeMid = {0, 1.62, 0.09};
    /** Zuletzt: Augenwinkel im Kopf (Grad) je Auge {Gier, Nicken}. */
    public final double[][] eyeAngles = new double[2][2];

    // ------------------------------------------------------------ Messwerte

    /** Zeitpunkte der Lidschläge (s). */
    public final List<Double> blinks = new ArrayList<>();
    /** Sakkaden: {Zeit, Weite (Grad), Dauer (s)}. */
    public final List<double[]> saccades = new ArrayList<>();
    /** Mikroausdrücke: {Zeit, Nummer des Gefühls, Dauer}. */
    public final List<double[]> micros = new ArrayList<>();

    public Behavior(long seed) {
        this.seed = seed;
        rng = new Person.Rng(seed * 0x51ED27L + 0x7E57L);
        asym = (rng.next() < 0.5 ? -1 : 1) * (0.05 + 0.06 * rng.next());
        nYaw = new Noise(seed * 11 + 1, 0.35); nPitch = new Noise(seed * 11 + 2, 0.3); nRoll = new Noise(seed * 11 + 3, 0.25);
        nYaw2 = new Noise(seed * 11 + 4, 1.1); nPitch2 = new Noise(seed * 11 + 5, 1.3);
        nSwayX = new Noise(seed * 11 + 6, 0.18); nSwayZ = new Noise(seed * 11 + 7, 0.14);
        nextBlink = 0.6 + 2.0 * rng.next();
        nextSigh = 40 + 60 * rng.next();
        nextMicro = 15 + 30 * rng.next();
        nextWeight = 2 + 4 * rng.next();
        breathPhase = rng.next();
        wFrom = wTo = rng.next() < 0.5 ? -1 : 1;
        setTarget();
    }

    // ------------------------------------------------------------ Regie

    /** Blick zur Kamera, frei, zum Horizont (Punkt: {@link #lookAt}). */
    public void look(Look mode) { look = mode; nextShift = t; }

    /** Blick auf einen Punkt (Figur-Koordinaten). */
    public void lookAt(double[] p) { point = p.clone(); look = Look.POINT; }

    public Look look() { return look; }

    public double[] point() { return point.clone(); }

    /** Gefühl nach Namen mit Stärke 0…1 (unbekannter Name: neutral). */
    public void feel(String name, double strength) {
        Emotion.Def d = Emotion.named(name);
        if (d == null) d = Emotion.NAMED.get(0);
        feelAt(d.valence, d.arousal, strength);
    }

    /** Gefühl als Punkt im Raum (Valenz, Erregung je −1…+1) mit Stärke 0…1. */
    public synchronized void feelAt(double valence, double arousal, double strength) {
        tv = clamp(valence, -1, 1); ta = clamp(arousal, -1, 1); ts = clamp(strength, 0, 1.5);
        setTarget();
    }

    public double valence() { return tv; }
    public double arousal() { return ta; }
    public double strength() { return ts; }

    /** Name des stärksten Gefühls am Zielpunkt. */
    public String feelingName() {
        double[] w = Emotion.blend(tv, ta);
        int b = 0;
        for (int i = 1; i < w.length; i++) if (w[i] > w[b]) b = i;
        return Emotion.NAMED.get(b).name;
    }

    /** Ist ein Befehl fertig? {@code "blick"}: Blick und Kopf am Ziel; {@code "gefuehl"}: Mimik angekommen. */
    public synchronized boolean done(String cmd) {
        switch (cmd) {
            case "blick": {
                if (sacc || pendingAt >= 0) return false;
                double[] T = targetAngles();
                double e = Math.hypot(T[0] + offset[0] - G[0], T[1] + offset[1] - G[1]);
                return e < 2 && Math.hypot(Ht[0] - H[0], Ht[1] - H[1]) < 3;
            }
            case "gefuehl": {
                for (Map.Entry<String, Double> e : targetSided.entrySet())
                    if (Math.abs(cur.getOrDefault(e.getKey(), 0.0) - e.getValue()) > 0.03) return false;
                for (Map.Entry<String, Double> e : cur.entrySet())
                    if (!targetSided.containsKey(e.getKey()) && e.getValue() > 0.03) return false;
                return true;
            }
            default: return true;
        }
    }

    /** Zeit des Verhaltens (s, mit Zeitlupe). */
    public double time() { return t; }

    private void setTarget() {
        target = Emotion.mix(tv, ta, ts);
        targetSided.clear();
        for (Map.Entry<String, Double> e : target.forms.entrySet()) {
            String f = e.getKey();
            double v = e.getValue();
            if (Emotion.isSided(f)) {
                targetSided.put("arkit." + f + "Left", v * (1 + asym));
                targetSided.put("arkit." + f + "Right", v * (1 - asym));
            } else targetSided.put("arkit." + f, v);
        }
    }

    // ------------------------------------------------------------ Augen einmessen

    /**
     * Augen einmessen (einmal je Person): Mitten in Ruhe und wie weit die ARKit-Blickformen das Auge
     * bei Gewicht 1 drehen. {@code weights}: Gewichte der Person ohne Gesichtsformen.
     */
    public synchronized void calibrate(EyeRig rig, Body body, Map<String, Double> weights) {
        if (rig == null || !rig.valid()) return;
        EyeRig.Frame[] f0 = rig.frames(body, weights, null);
        String[] dir = {"Up", "Down", "In", "Out"};
        for (int e = 0; e < 2; e++) {
            cRest[e] = f0[e].c.clone();
            yaw0[e] = Math.toDegrees(Math.atan2(f0[e].z[0], f0[e].z[2]));
            pitch0[e] = Math.toDegrees(Math.asin(clamp(f0[e].z[1], -1, 1)));
            String side = e == 0 ? "Left" : "Right";
            for (int k = 0; k < 4; k++) {
                Map<String, Double> w = new LinkedHashMap<>(weights);
                w.put("arkit.eyeLook" + dir[k] + side, 1.0);
                EyeRig.Frame[] f1 = rig.frames(body, w, null);
                fYaw[e][k] = Math.toDegrees(Math.atan2(f1[e].z[0], f1[e].z[2])) - yaw0[e];
                fPitch[e][k] = Math.toDegrees(Math.asin(clamp(f1[e].z[1], -1, 1))) - pitch0[e];
            }
        }
        for (int k = 0; k < 3; k++) eyeMid[k] = (cRest[0][k] + cRest[1][k]) / 2;
        double[] r = eyeRange();
        rangeYaw = Math.max(8, Math.min(r[0], r[1]));
        rangePitch = Math.max(6, Math.min(r[2], r[3]));
        calibrated = true;
    }

    /** Eingemessen? */
    public boolean calibrated() { return calibrated; }

    /** Größte Drehung der Augen durch die Formen (Grad): {links, rechts, oben, unten}, Mittel beider Augen. */
    public double[] eyeRange() {
        double[] r = new double[4];
        for (int e = 0; e < 2; e++) {
            double l = 0, ri = 0, u = 0, d = 0;
            for (int k = 0; k < 4; k++) {
                l = Math.max(l, fYaw[e][k]); ri = Math.max(ri, -fYaw[e][k]);
                u = Math.max(u, fPitch[e][k]); d = Math.max(d, -fPitch[e][k]);
            }
            r[0] += l / 2; r[1] += ri / 2; r[2] += u / 2; r[3] += d / 2;
        }
        return r;
    }

    // ------------------------------------------------------------ Schritt

    /** Ein Zeitschritt (s, Echtzeit; die Zeitlupe wird hier angewendet). */
    public synchronized void step(double dtReal) {
        double dt = Math.max(0, Math.min(0.1, dtReal)) * timeScale;
        if (dt <= 0) return;
        t += dt;
        boolean live = alive;
        double ct = live && !Double.isNaN(musicBeat) ? Math.max(0, Math.min(1, musicCalm)) : 0;
        calmLid += (ct - calmLid) * (1 - Math.exp(-dt / (ct > calmLid ? 1.2 : 0.35)));
        emotion(dt, live);
        if (live) { blink(dt); breathe(dt); weight(); }
        else { blinking = false; }
        gaze(dt, live);
        head(dt, live);
    }

    private void emotion(double dt, boolean live) {
        double k = 1 - Math.exp(-dt / 0.6);
        sv += (tv - sv) * k; sa += (ta - sa) * k;
        double kp = 1 - Math.exp(-dt / 0.5);
        Emotion.Mix m = target;
        pHeadDown += (m.headDown - pHeadDown) * kp; pGazeDown += (m.gazeDown - pGazeDown) * kp;
        pHeadTilt += (m.headTilt - pHeadTilt) * kp; pShoulders += (m.shoulders - pShoulders) * kp;
        pBlinkGap += (m.blinkGap - pBlinkGap) * kp; pBreathRate += (m.breathRate - pBreathRate) * kp;
        pBreathDepth += (m.breathDepth - pBreathDepth) * kp; pSacc += (m.saccadeRate - pSacc) * kp; pAvert += (m.aversion - pAvert) * kp;
        // je Form eigene Trägheit: Brauen zuerst, Augen, dann Mund; Abklingen langsamer als Einsetzen
        java.util.Set<String> keys = new java.util.LinkedHashSet<>(cur.keySet());
        keys.addAll(targetSided.keySet());
        for (String f : keys) {
            double c = cur.getOrDefault(f, 0.0), tg = targetSided.getOrDefault(f, 0.0);
            double[] tau = tau(f);
            double tt = tg > c ? tau[0] : tau[1];
            if (f.endsWith("Right")) tt *= 1 + 0.6 * Math.max(0, asym);   // die schwächere Seite kommt etwas später
            if (f.endsWith("Left")) tt *= 1 + 0.6 * Math.max(0, -asym);
            c += (tg - c) * (1 - Math.exp(-dt / tt));
            if (Math.abs(c) < 1e-5 && tg == 0) cur.remove(f); else cur.put(f, c);
        }
        // Mikroausdrücke: selten, kurz (1/25–1/3 s), ein anderes Gefühl schwach
        if (live && t >= nextMicro) {
            double[] w = Emotion.blend(tv, ta);
            int dom = 0;
            for (int i = 1; i < w.length; i++) if (w[i] > w[dom]) dom = i;
            int n = Emotion.NAMED.size(), pick;
            do pick = 1 + (int) (rng.next() * (n - 1)); while (pick == dom);
            micro = Emotion.NAMED.get(pick);
            microT0 = t; microDur = 0.14 + 0.18 * rng.next(); microAmp = 0.30 + 0.25 * rng.next();
            micros.add(new double[]{t, pick, microDur});
            trim(micros);
            nextMicro = t + 20 + 45 * rng.next();
        }
    }

    /** Trägheit (s) {Einsetzen, Abklingen} je Gesichtsform. */
    static double[] tau(String f) {
        if (f.contains("brow")) return new double[]{0.10, 0.45};
        if (f.contains("eye") || f.contains("cheekSquint")) return new double[]{0.16, 0.60};
        return new double[]{0.26, 0.85};
    }

    private void blink(double dt) {
        if (blinking && t > blinkT0 + blinkClose + blinkHold + blinkOpen) blinking = false;
        if (!blinking && t >= nextBlink) startBlink();
    }

    private void startBlink() {
        double tired = Math.max(0, -sa - 0.3);          // müde: langsamer
        blinking = true;
        blinkT0 = t;
        blinkClose = (0.075 + 0.025 * rng.next()) * (1 + 0.5 * tired);
        blinkHold = 0.02 + 0.03 * rng.next();
        blinkOpen = (0.14 + 0.08 * rng.next()) * (1 + 0.6 * tired);
        blinkAmp = rng.next() < 0.1 ? 0.6 + 0.25 * rng.next() : 1.0;
        blinks.add(t);
        trimD(blinks);
        lastBlink = t;
        double gap = (0.45 + 2.45 * pBlinkGap * Math.exp(0.55 * rng.gauss())) * (1 - 0.45 * Math.min(1, rainOnFace)) * (talking ? 0.65 : 1);
        nextBlink = t + blinkClose + blinkHold + blinkOpen + gap;
    }

    /**
     * Phase 16: bald einen Lidschlag (am Ende einer Phrase), spätestens in {@code within} s; nicht, wenn gerade einer
     * läuft oder der letzte kürzer als 0,6 s her ist.
     */
    public synchronized void blinkSoon(double within) {
        if (blinking || t - lastBlink < 0.6) return;
        nextBlink = Math.min(nextBlink, t + Math.max(0, within));
    }

    /** Phase 16: vor einem Satz einatmen: der Atemzug beginnt jetzt, etwas tiefer [G: 1,4-mal]. */
    public synchronized void inhale() {
        if (!alive) return;
        if (breathPhase < 0.38) return;               // atmet schon ein
        breathPhase = 0;
        breathDepth = Math.max(breathDepth, pBreathDepth * 1.4);
        sigh = false;
    }

    /** Lid zu (0…1) durch den Lidschlag. */
    private double blinkValue() {
        if (!blinking) return 0;
        double u = t - blinkT0;
        if (u < blinkClose) { double x = u / blinkClose; return blinkAmp * (0.5 - 0.5 * Math.cos(Math.PI * x)); }
        u -= blinkClose;
        if (u < blinkHold) return blinkAmp;
        u -= blinkHold;
        double x = Math.min(1, u / blinkOpen);
        return blinkAmp * (1 - x) * (1 - x);
    }

    private void breathe(double dt) {
        breathPhase += dt * breathRate / 60 * (sigh ? 0.6 : 1);
        if (breathPhase >= 1) {
            breathPhase -= Math.floor(breathPhase);
            double base = clamp((14 + 3 * sa) * pBreathRate * (1 + 0.25 * cold), 9, 26);
            breathRate = base * (1 + 0.08 * clamp(rng.gauss(), -2, 2));
            breathDepth = pBreathDepth * (1 + 0.12 * clamp(rng.gauss(), -2, 2));
            sigh = t >= nextSigh;
            if (sigh) { breathDepth *= 2.3; nextSigh = t + 50 + 90 * rng.next(); }
        }
    }

    /** Lungenfüllung 0…1 (mal Tiefe): Einatmen 38 %, Ausatmen 50 %, Pause 12 % des Zugs. */
    public double breath() {
        if (!alive) return 0;
        double p = breathPhase, b;
        if (p < 0.38) b = 0.5 - 0.5 * Math.cos(Math.PI * p / 0.38);
        else if (p < 0.88) b = 0.5 + 0.5 * Math.cos(Math.PI * (p - 0.38) / 0.5);
        else b = 0;
        return b * breathDepth;
    }

    /** Atemstrom beim Ausatmen 0…1 (für den Atemhauch), sonst 0. */
    public double breathFlow() {
        if (!alive) return 0;
        double p = breathPhase;
        if (p < 0.38 || p >= 0.88) return 0;
        return Math.sin(Math.PI * (p - 0.38) / 0.5) * Math.min(1.5, breathDepth);
    }

    /** Atemzüge je Minute (aktuell). */
    public double breathRate() { return breathRate; }

    private void weight() {
        if (t >= nextWeight) {
            wFrom = weightNow();
            double r = rng.next();
            wTo = r < 0.15 ? 0.25 * (rng.next() - 0.5) : (wFrom > 0 ? -1 : 1) * (0.7 + 0.3 * rng.next());
            wT0 = t;
            wDur = 1.1 + 0.7 * rng.next();
            nextWeight = t + wDur + 4 + 9 * rng.next() * (1.2 - 0.4 * Math.max(0, sa));
        }
    }

    /** Gewicht −1 (rechtes Bein) … +1 (linkes Bein). */
    public double weightNow() {
        double x = clamp((t - wT0) / wDur, 0, 1);
        x = x * x * (3 - 2 * x);
        return wFrom + (wTo - wFrom) * x;
    }

    // ------------------------------------------------------------ Blick

    private double[] targetAngles() {
        double[] p;
        switch (look) {
            case CAMERA -> p = camera;
            case POINT -> p = point;
            case HORIZON -> p = new double[]{eyeMid[0], eyeMid[1] - 0.6, eyeMid[2] + 40};
            default -> p = new double[]{eyeMid[0], eyeMid[1] - 0.15, eyeMid[2] + 2.0};
        }
        return angles(p, eyeMid);
    }

    private void gaze(double dt, boolean live) {
        double[] T = targetAngles();
        if (!tInit) { G[0] = T[0]; G[1] = T[1]; Tlast[0] = T[0]; Tlast[1] = T[1]; tInit = true; }
        double jump = Math.hypot(T[0] - Tlast[0], T[1] - Tlast[1]);
        if (!live) {
            offset[0] = offset[1] = 0;
            G[0] = T[0]; G[1] = T[1] - pGazeDown;
            sacc = false; pendingAt = -1;
            Tlast[0] = T[0]; Tlast[1] = T[1];
            return;
        }
        // Fixation und neue Ablage vom Ziel
        if (t >= nextShift) newOffset();
        double[] want = {T[0] + offset[0], T[1] + offset[1] - pGazeDown * (look == Look.CAMERA ? 0.5 : 1)};
        if (sacc) {
            double x = (t - sT0) / sDur;
            if (x >= 1) { sacc = false; sEnd = t; G[0] = sTo[0]; G[1] = sTo[1]; }
            else { double s = x * x * x * (10 - 15 * x + 6 * x * x); G[0] = sFrom[0] + (sTo[0] - sFrom[0]) * s; G[1] = sFrom[1] + (sTo[1] - sFrom[1]) * s; }
        } else if (jump < 3) {
            // Folgebewegung: bewegtes Ziel bis 40°/s mitführen
            double mx = 40 * dt;
            G[0] += clamp(T[0] - Tlast[0], -mx, mx);
            G[1] += clamp(T[1] - Tlast[1], -mx, mx);
        }
        Tlast[0] = T[0]; Tlast[1] = T[1];
        double err = Math.hypot(want[0] - G[0], want[1] - G[1]);
        if (!sacc && err <= 0.8) pendingAt = -1;
        if (!sacc && err > 0.8) {
            if (pendingAt < 0) pendingAt = t + (jump >= 3 ? 0.17 + 0.07 * rng.next() : 0) + Math.max(0, sEnd + 0.12 - t);
            if (t >= pendingAt) startSaccade(want);
        }
    }

    private void newOffset() {
        double rate = Math.max(0.3, pSacc);
        switch (look) {
            case CAMERA, POINT -> {
                if (averted) {
                    averted = false;
                    offset[0] = 0.8 * rng.gauss(); offset[1] = -1.0 + 0.8 * rng.gauss();
                    nextShift = t + 1.2 * Math.exp(0.4 * rng.gauss()) / rate;
                } else if (rng.next() < 0.10 * pAvert) {
                    averted = true;
                    double ang = rng.next() * 2 * Math.PI, mag = 9 + 11 * rng.next();
                    double ox = Math.cos(ang), oy = Math.sin(ang) * 0.6;
                    if (pAvert > 1.5) oy = Math.abs(oy) + 0.3;              // nachdenklich: nach oben zur Seite
                    if (pGazeDown > 3) oy = -Math.abs(oy) - 0.3;            // traurig: nach unten
                    double l = Math.hypot(ox, oy);
                    offset[0] = ox / l * mag; offset[1] = oy / l * mag;
                    nextShift = t + (0.7 + 1.5 * rng.next()) * (pAvert > 1.5 ? 1.6 : 1);
                } else {
                    // zwischen Augen und Mund des Gegenübers: klein, eher senkrecht
                    offset[0] = 1.2 * rng.gauss(); offset[1] = -1.2 + 1.3 * rng.gauss();
                    nextShift = t + 0.85 * Math.exp(0.5 * rng.gauss()) / rate;
                }
            }
            case HORIZON -> {
                offset[0] = 14 * rng.gauss(); offset[1] = 1.2 * rng.gauss();
                nextShift = t + 1.4 * Math.exp(0.5 * rng.gauss()) / rate;
            }
            default -> {
                if (rng.next() < 0.3) { offset[0] = 3 * rng.gauss(); offset[1] = 2 * rng.gauss(); }
                else { offset[0] = clamp(16 * rng.gauss(), -35, 35); offset[1] = clamp(-2 + 7 * rng.gauss(), -18, 12); }
                nextShift = t + 1.1 * Math.exp(0.55 * rng.gauss()) / rate;
            }
        }
    }

    private void startSaccade(double[] want) {
        double a = Math.hypot(want[0] - G[0], want[1] - G[1]);
        sFrom[0] = G[0]; sFrom[1] = G[1];
        sTo[0] = want[0]; sTo[1] = want[1];
        sT0 = t;
        sDur = 0.021 + 0.0022 * a;
        sacc = true;
        pendingAt = -1;
        saccades.add(new double[]{t, a, sDur});
        trim(saccades);
        // große Blicksprünge gehen oft mit einem Lidschlag einher [G]
        double p = clamp((a - 12) / 30, 0, 0.85);
        if (!blinking && t - lastBlink > 0.4 && rng.next() < p) startBlink();
    }

    private void head(double dt, boolean live) {
        double[] g = sacc ? sTo : G;
        // Anteil des Kopfes; mindestens so viel, dass die Augen in 80 % ihrer Reichweite bleiben
        double hy = g[0] * share(Math.abs(g[0])), hp = 0.5 * g[1] * share(Math.abs(g[1]));
        double ey = 0.8 * rangeYaw, ep = 0.8 * rangePitch;
        if (Math.abs(g[0]) - Math.abs(hy) > ey) hy = Math.signum(g[0]) * (Math.abs(g[0]) - ey);
        if (Math.abs(g[1]) - Math.abs(hp) > ep) hp = Math.signum(g[1]) * (Math.abs(g[1]) - ep);
        double[] ht = {hy, hp - pHeadDown};
        Ht[0] = ht[0]; Ht[1] = ht[1];
        if (!live) { H[0] = ht[0]; H[1] = ht[1]; Hv[0] = Hv[1] = 0; return; }
        double w = 2 * Math.PI * 1.1;
        int sub = Math.max(1, (int) Math.ceil(dt / 0.005));
        double h = dt / sub;
        for (int s = 0; s < sub; s++)
            for (int k = 0; k < 2; k++) {
                double acc = w * w * (ht[k] - H[k]) - 2 * w * Hv[k];
                Hv[k] += acc * h;
                H[k] += Hv[k] * h;
            }
    }

    /** Anteil des Kopfes an einer Blickwendung der Weite {@code a} (Grad) [G]. */
    static double share(double a) {
        double x = clamp((a - 6) / 29, 0, 1);
        return 0.12 + 0.55 * x * x * (3 - 2 * x);
    }

    // ------------------------------------------------------------ Ausgabe

    /** Zugabe zur Pose: Kopf und Hals (Blick, Mikrobewegung, Gefühl), Atem, Becken. */
    public synchronized void additive(Skeleton.Additive add) {
        add.clear();
        boolean live = alive;
        double nY = 0, nP = 0, nR = 0;
        if (live) {
            nY = 0.7 * nYaw.at(t) + 0.25 * nYaw2.at(t);
            nP = 0.5 * nPitch.at(t) + 0.2 * nPitch2.at(t);
            nR = 0.35 * nRoll.at(t);
        }
        double yaw = H[0] + nY, pitch = H[1] + nP, roll = pHeadTilt + nR;
        add.rotate("neck_01", 0, 1, 0, Math.toRadians(0.35 * yaw));
        add.rotate("neck_01", 1, 0, 0, Math.toRadians(-0.35 * pitch));
        add.rotate("head", 0, 1, 0, Math.toRadians(0.65 * yaw));
        add.rotate("head", 1, 0, 0, Math.toRadians(-0.65 * pitch));
        add.rotate("head", 0, 0, 1, Math.toRadians(roll));
        double b = breath();
        add.rotate("spine_02", 1, 0, 0, Math.toRadians(-0.45 * b));
        add.rotate("spine_03", 1, 0, 0, Math.toRadians(-0.9 * b));
        add.rotate("head", 1, 0, 0, Math.toRadians(0.95 * b));
        double c = cold;
        double sh = 1.3 * b + pShoulders + 3.5 * c;
        // Frösteln: kurze Schübe eines feinen Zitterns um 10 Hz (Muskelzittern) [G]
        if (live && c > 0.35) {
            double burst = Math.max(0, nRoll.at(t * 3.1 + 17)) * (c - 0.35) / 0.65;
            double tr = burst * 0.9 * Math.sin(2 * Math.PI * 9.7 * t + 2 * nYaw2.at(t * 2));
            add.rotate("spine_03", 0, 0, 1, Math.toRadians(0.4 * tr));
            add.rotate("clavicle_l", 0, 0, 1, Math.toRadians(0.8 * tr));
            add.rotate("clavicle_r", 0, 0, 1, Math.toRadians(0.6 * tr));
            add.rotate("head", 1, 0, 0, Math.toRadians(0.25 * tr));
        }
        add.rotate("clavicle_l", 0, 0, 1, Math.toRadians(sh));
        add.rotate("clavicle_r", 0, 0, 1, Math.toRadians(-sh));
        // Musik (Phase 17): Nicken kurz nach dem Schlag, Wiegen über zwei Schläge (Kopf, Brust, Schultern im Wechsel)
        double mb = musicBeat, gv = Math.max(0, Math.min(1, groove));
        if (live && !Double.isNaN(mb) && gv > 0) {
            double ph = mb - Math.floor(mb);
            double nod = gv * 4.0 * (0.5 + 0.5 * Math.cos(2 * Math.PI * (ph - 0.12))) - gv * 2.0;
            add.rotate("neck_01", 1, 0, 0, Math.toRadians(0.3 * nod));
            add.rotate("head", 1, 0, 0, Math.toRadians(0.7 * nod));
            double sw = Math.sin(Math.PI * (mb - musicDown));
            add.rotate("spine_03", 0, 0, 1, Math.toRadians(1.8 * gv * sw));
            add.rotate("head", 0, 0, 1, Math.toRadians(-2.4 * gv * sw));
            add.rotate("clavicle_l", 0, 0, 1, Math.toRadians(2.2 * gv * Math.max(0, sw)));
            add.rotate("clavicle_r", 0, 0, 1, Math.toRadians(-2.2 * gv * Math.max(0, -sw)));
            if (standing) {
                add.pelvis[0] += 0.012 * gv * sw;
                add.rotate("pelvis", 0, 0, 1, Math.toRadians(1.5 * gv * sw));
            }
        }
        if (live && standing) {
            double s = weightNow();
            add.pelvis[0] = 0.02 * s + 0.003 * nSwayX.at(t);
            add.pelvis[1] = -0.004 - 0.006 * Math.abs(s);
            add.pelvis[2] = 0.003 * nSwayZ.at(t);
            add.rotate("pelvis", 0, 0, 1, Math.toRadians(2.2 * s));
            add.rotate("spine_01", 0, 0, 1, Math.toRadians(-1.6 * s));
            add.rotate("spine_03", 0, 0, 1, Math.toRadians(-0.6 * s));
        } else if (live) {
            add.pelvis[0] = 0.0015 * nSwayX.at(t);
        }
    }

    /**
     * Gesichtsformen ("arkit.…" → 0…1) für dieses Bild: Gefühl, Mikroausdruck, Lidschlag, Lider und
     * Augen aus der Lage des Kopfes ({@code posed}: Weltmatrizen der Gelenke, {@code rest}: Ruhelage).
     */
    public synchronized Map<String, Double> forms(double[][] posed, double[][] rest, Skeleton sk) {
        Map<String, Double> out = new LinkedHashMap<>(cur);
        // Mikroausdruck: schneller Anstieg (40 ms), Halten, Abklingen (80 ms)
        if (micro != null && microT0 >= 0) {
            double u = t - microT0, env;
            if (u > microDur + 0.08) { micro = null; env = 0; }
            else env = u < 0.04 ? u / 0.04 : (u < microDur ? 1 : 1 - (u - microDur) / 0.08);
            if (micro != null && env > 0)
                for (Map.Entry<String, Double> e : micro.forms.entrySet()) {
                    double v = e.getValue() * microAmp * env;
                    if (Emotion.isSided(e.getKey())) {
                        out.merge("arkit." + e.getKey() + "Left", v, Double::sum);
                        out.merge("arkit." + e.getKey() + "Right", v, Double::sum);
                    } else out.merge("arkit." + e.getKey(), v, Double::sum);
                }
        }
        // Kälte und Regen im Gesicht: Augen schmaler, Lippen gepresst, bei großer Kälte Zähneklappern
        double cd = cold, rf = Math.min(1, rainOnFace);
        if (cd > 0 || rf > 0) {
            for (String sd : new String[]{"Left", "Right"}) {
                out.merge("arkit.eyeSquint" + sd, 0.12 * cd + 0.32 * rf, Double::sum);
                out.merge("arkit.mouthPress" + sd, 0.15 * cd, Double::sum);
                out.merge("arkit.browDown" + sd, 0.14 * rf, Double::sum);
                out.merge("arkit.cheekSquint" + sd, 0.12 * rf, Double::sum);
            }
            if (alive && cd > 0.75) out.merge("arkit.jawOpen", 0.022 * (cd - 0.75) / 0.25 * Math.max(0, Math.sin(2 * Math.PI * 7.5 * t)) * Math.max(0, nPitch.at(t * 2.3)), Double::sum);
        }
        // Sprechen (Phase 16): die Mundformen des Gefühls treten zur Hälfte zurück, die Viseme kommen dazu
        Map<String, Double> sp = speech;
        if (sp != null && !sp.isEmpty()) {
            double sum = 0;
            for (double v : sp.values()) sum += v;
            double k = 1 - 0.5 * Math.min(1, sum);
            out.replaceAll((f, v) -> f.contains("mouth") || f.contains("jaw") ? v * k : v);
            for (Map.Entry<String, Double> e : sp.entrySet()) out.merge(e.getKey(), e.getValue(), Double::sum);
        }
        double bl = blinkValue();
        // Augen: Blickpunkt aus G, Augen aus der Lage des Kopfes
        int hj = sk.joint("head");
        if (calibrated && hj >= 0) {
            double[] S = M4.mul(posed[hj], M4.invRigid(rest[hj]));
            double[][] c = {M4.point(S, cRest[0][0], cRest[0][1], cRest[0][2]), M4.point(S, cRest[1][0], cRest[1][1], cRest[1][2])};
            for (int k = 0; k < 3; k++) eyeMid[k] = (c[0][k] + c[1][k]) / 2;
            double dist;
            switch (look) {
                case CAMERA -> dist = Math.max(0.25, dist(camera, eyeMid));
                case POINT -> dist = Math.max(0.25, dist(point, eyeMid));
                case HORIZON -> dist = 40;
                default -> dist = 2.0;
            }
            double cy = Math.cos(Math.toRadians(G[1]));
            double[] gp = {eyeMid[0] + dist * cy * Math.sin(Math.toRadians(G[0])), eyeMid[1] + dist * Math.sin(Math.toRadians(G[1])),
                           eyeMid[2] + dist * cy * Math.cos(Math.toRadians(G[0]))};
            for (int e = 0; e < 2; e++) {
                double dx = gp[0] - c[e][0], dy = gp[1] - c[e][1], dz = gp[2] - c[e][2];
                // in den Ruherahmen des Kopfes: Rᵀ · d
                double rx = S[0] * dx + S[1] * dy + S[2] * dz, ry = S[4] * dx + S[5] * dy + S[6] * dz, rz = S[8] * dx + S[9] * dy + S[10] * dz;
                double yaw = Math.toDegrees(Math.atan2(rx, rz)) - yaw0[e];
                double pitch = Math.toDegrees(Math.atan2(ry, Math.hypot(rx, rz))) - pitch0[e];
                eyeAngles[e][0] = yaw; eyeAngles[e][1] = pitch;
                String side = e == 0 ? "Left" : "Right";
                put(out, side, yaw, 2, 3, fYaw[e], "In", "Out");
                put(out, side, pitch, 0, 1, fPitch[e], "Up", "Down");
                // die Lider folgen dem Blick nach unten, oben weiten sie sich etwas
                double down = Math.max(0, -pitch) / 25, up = Math.max(0, pitch) / 25;
                out.merge("arkit.eyeBlink" + side, 0.28 * down, Double::sum);
                out.merge("arkit.eyeWide" + side, 0.12 * up, Double::sum);
            }
        }
        out.merge("arkit.eyeBlinkLeft", bl, Double::sum);
        out.merge("arkit.eyeBlinkRight", bl, Double::sum);
        // ruhige Musik (Phase 17): Augen zu
        if (calmLid > 1e-3) {
            out.merge("arkit.eyeBlinkLeft", 0.92 * calmLid, Double::sum);
            out.merge("arkit.eyeBlinkRight", 0.92 * calmLid, Double::sum);
        }
        // beim Lidschlag kein Weiten
        if (bl > 0) for (String s : new String[]{"Left", "Right"}) out.computeIfPresent("arkit.eyeWide" + s, (k, v) -> v * (1 - bl));
        out.replaceAll((k, v) -> clamp(v, 0, 1));
        return out;
    }

    /** Augenform für einen Winkel: Form a oder b je nach Vorzeichen ihrer Wirkung. */
    private static void put(Map<String, Double> out, String side, double ang, int ka, int kb, double[] full, String na, String nb) {
        if (Math.abs(ang) < 1e-4) return;
        int k = Math.signum(full[ka]) == Math.signum(ang) ? ka : kb;
        double th = Math.abs(full[k]);
        if (th < 0.5 || Math.signum(full[k]) != Math.signum(ang)) return;
        double a = Math.toRadians(Math.min(Math.abs(ang), th)), f = Math.toRadians(th);
        // Ecken wandern auf der Sehne: Winkel → Gewicht exakt umkehren
        double w = Math.tan(a) / (Math.sin(f) + Math.tan(a) * (1 - Math.cos(f)));
        out.merge("arkit.eyeLook" + (k == ka ? na : nb) + side, clamp(w, 0, 1), Double::sum);
    }

    // ------------------------------------------------------------ Hilfen

    /** Gier und Nicken (Grad) der Richtung von {@code from} nach {@code p}. */
    static double[] angles(double[] p, double[] from) {
        double dx = p[0] - from[0], dy = p[1] - from[1], dz = p[2] - from[2];
        return new double[]{Math.toDegrees(Math.atan2(dx, dz)), Math.toDegrees(Math.atan2(dy, Math.hypot(dx, dz)))};
    }

    private static double dist(double[] a, double[] b) {
        return Math.sqrt((a[0] - b[0]) * (a[0] - b[0]) + (a[1] - b[1]) * (a[1] - b[1]) + (a[2] - b[2]) * (a[2] - b[2]));
    }

    private static void trim(List<double[]> l) { while (l.size() > 4000) l.remove(0); }

    private static void trimD(List<Double> l) { while (l.size() > 4000) l.remove(0); }

    static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    /** Blickwinkel des Gesamtblicks (Grad) {Gier, Nicken} und des Kopfes. */
    public synchronized double[] gazeHead() { return new double[]{G[0], G[1], H[0], H[1]}; }

    /** Lidschlag gerade (0…1). */
    public synchronized double lid() { return blinkValue(); }

    /** Augen zu bei ruhiger Musik (0 … 1, Phase 17). */
    public synchronized double calmLid() { return calmLid; }

    /** Läuft gerade eine Sakkade? */
    public synchronized boolean inSaccade() { return sacc; }

    /** Seed. */
    public long seed() { return seed; }
}
