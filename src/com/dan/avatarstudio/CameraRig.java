package com.dan.avatarstudio;

import com.dan.portrait.Shot;

import java.util.Locale;

/**
 * Kamera für Menschen (Phase 13, Werkbuch Abschnitt 6): Einstellungen Totale, Halbtotale, Amerikanisch,
 * Halbnah, Nah, Groß auf eine Person, auf Augenhöhe, mit einem Winkel zur Blickrichtung der Person; auf
 * Wunsch folgt sie der gehenden Person (Ziel weich nachgeführt, Richtung träge, damit sie nicht jeden
 * Schritt mitschaukelt). „frei“ lässt die Kamera der Maus; „mitgehen“ verschiebt nur das Ziel mit der
 * Person und lässt Abstand und Richtung, wie sie sind (Studio, gegen „geht aus dem Bild“).
 */
public final class CameraRig {

    public enum Mode { FREI, MITGEHEN, TOTALE, EINSTELLUNG }

    public static final String[] NAMES = {"frei", "mitgehen", "Totale", "Halbtotale", "Amerikanisch", "Halbnah", "Nah", "Groß"};

    public volatile Mode mode = Mode.FREI;
    public volatile Shot shot = Shot.HALBNAH;
    /** Person, auf die die Kamera schaut (oder null: die erste). */
    public volatile StudioScene.Figure target;
    /** Winkel zur Blickrichtung der Person (Grad, 0 = genau von vorn, positiv = von ihrer linken Seite). */
    public volatile double angleDeg = 25;
    /** Der Person folgen (sonst steht die Kamera nach dem Schnitt). */
    public volatile boolean follow = true;
    /** Beim nächsten Bild ohne Nachführen setzen (Schnitt). */
    public volatile boolean snap = true;
    /** Zeitkonstanten (s): Ort und Richtung. */
    public double tauPos = 0.30, tauYaw = 1.6;

    private double lastSchwenk;
    private boolean placed;

    /** Bildwinkel je Einstellung (Grad) [G]: weiter für die Totale, enger, je näher. */
    static double fov(Shot s) {
        return switch (s) { case GROSS -> 18; case NAH -> 22; case HALBNAH -> 26; case AMERIKANISCH -> 30; case HALBTOTALE -> 34; };
    }

    /**
     * Einstellung nach Namen setzen („Totale“, „Halbnah“, „frei“, „mitgehen“ …); {@code false}, wenn unbekannt.
     */
    public boolean set(String name, StudioScene.Figure who, Double angle, Boolean follows) {
        String n = name.toLowerCase(Locale.ROOT).replace("ß", "ss");
        Mode m;
        Shot s = shot;
        switch (n) {
            case "frei" -> m = Mode.FREI;
            case "mitgehen", "folgen" -> m = Mode.MITGEHEN;
            case "totale" -> m = Mode.TOTALE;
            case "halbtotale" -> { m = Mode.EINSTELLUNG; s = Shot.HALBTOTALE; }
            case "amerikanisch" -> { m = Mode.EINSTELLUNG; s = Shot.AMERIKANISCH; }
            case "halbnah" -> { m = Mode.EINSTELLUNG; s = Shot.HALBNAH; }
            case "nah" -> { m = Mode.EINSTELLUNG; s = Shot.NAH; }
            case "gross", "groß" -> { m = Mode.EINSTELLUNG; s = Shot.GROSS; }
            default -> { return false; }
        }
        mode = m; shot = s;
        if (who != null) target = who;
        if (angle != null) angleDeg = angle;
        if (follows != null) follow = follows;
        snap = true;
        return true;
    }

    /** Je Bild: die Kamera der Szene nachführen. */
    public void update(StudioScene scene, double dt) {
        Mode m = mode;
        Camera cam = scene.cam;
        if (m == Mode.FREI) { placed = false; return; }
        boolean sn = snap || !placed || dt <= 0 || dt > 0.5;
        snap = false;
        placed = true;
        double kp = sn ? 1 : 1 - Math.exp(-dt / tauPos), ky = sn ? 1 : 1 - Math.exp(-dt / tauYaw);
        if (m == Mode.TOTALE) {
            JettySet js = scene.set;
            double[] t = js != null ? js.totale() : new double[]{0, 0.95, 0, 18, 6, 7.5, 40};
            double sw = scene.kino.laeuft() ? scene.kino.schwenk() : 0;
            if (sn) { cam.tx = t[0]; cam.ty = t[1]; cam.tz = t[2]; cam.yawDeg = t[3]; cam.pitchDeg = t[4]; cam.dist = t[5]; cam.fovyDeg = t[6]; }
            cam.yawDeg += sw - lastSchwenk;
            lastSchwenk = sw;
            far(cam);
            return;
        }
        lastSchwenk = scene.kino.laeuft() ? scene.kino.schwenk() : 0;
        StudioScene.Figure f = target;
        if (f == null || !scene.figures.contains(f)) f = scene.figures.isEmpty() ? null : scene.figures.get(0);
        if (f == null || f.jointWorld == null) return;
        double[] head = f.head();
        if (head == null) return;
        if (m == Mode.MITGEHEN) {
            cam.tx += (head[0] - cam.tx) * kp;
            cam.tz += (head[2] - cam.tz) * kp;
            return;
        }
        Shot s = shot;
        double fv = fov(s);
        // Im Kino verdecken die Balken oben und unten ein Viertel: dieselbe Einstellung braucht mehr Abstand
        // (Bild 16 : 9, sichtbar 2,39 : 1, also 74 % der Höhe)
        double k = scene.kino.laeuft() ? com.dan.kino.Kino.BREITWAND * 9.0 / 16 : 1;
        com.dan.avatar.Motion mo = f.motion;
        double yawF = mo == null ? 0 : Math.toDegrees(mo.yaw);
        double wantYaw = yawF + angleDeg;
        // Ziel: die Augen (Gelenk „head“ sitzt am Schädelgrund), beim Gehen ein wenig voraus, damit die
        // Person trotz Nachführen in der Mitte bleibt
        double lead = mo == null ? 0 : mo.speed * tauPos;
        double ax = head[0] + (mo == null ? 0 : Math.sin(mo.yaw) * lead), az = head[2] + (mo == null ? 0 : Math.cos(mo.yaw) * lead);
        if (sn || follow) {
            double ty = head[1] + 0.08 + s.offsetY * k;
            if (sn) { cam.tx = ax; cam.ty = ty; cam.tz = az; cam.yawDeg = wantYaw; }
            else {
                cam.tx += (ax - cam.tx) * kp; cam.ty += (ty - cam.ty) * kp; cam.tz += (az - cam.tz) * kp;
                double d = ((wantYaw - cam.yawDeg) % 360 + 540) % 360 - 180;
                cam.yawDeg += d * ky;
            }
        }
        cam.fovyDeg = fv;
        cam.dist = s.distance(fv) * k;
        cam.pitchDeg = s == Shot.HALBTOTALE ? 5 : 2;
        far(cam);
    }

    private static void far(Camera cam) {
        cam.near = Math.max(0.03, Math.min(0.15, cam.dist * 0.02));
        cam.far = Math.max(200, cam.dist * 20);
    }

    @Override public String toString() {
        return switch (mode) {
            case FREI -> "frei";
            case MITGEHEN -> "geht mit";
            case TOTALE -> "Totale";
            case EINSTELLUNG -> shot.label + (follow ? " · folgt" : "") + String.format(Locale.GERMANY, " · %.0f°", angleDeg);
        };
    }
}
