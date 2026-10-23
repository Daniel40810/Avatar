package com.dan.avatarstudio;

import com.dan.avatar.Body;
import com.dan.avatar.Pose;
import com.dan.avatar.Skeleton;
import com.dan.avatar.Person;
import com.dan.avatar.gl.AvatarGl;
import com.dan.avatar.gl.ShadowMap;
import com.dan.avatar.gl.SkinScatter;
import com.jogamp.common.nio.Buffers;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL2ES2;
import com.jogamp.opengl.GL2ES3;
import com.jogamp.opengl.GL4;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLEventListener;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Die Bühne des Avatar Studios: Hintergrund mit unscharfen Lichtern, nasser Bühnenboden mit
 * Standring und Maßstab (1,75 m), Führungs-, Kanten- und Aufhelllicht, HDR-Ziel mit Tonkurve,
 * HUD. Seit Phase 2 stehen hier Personen ({@link Figure}); seit Phase 4 mit Schattenkarte des
 * Führungslichts und Streuung der Haut im Bildraum. Ablauf je Bild:
 * Personen vorbereiten → Schattenkarte → Hintergrund und Bühne (Ziel 0) → Personen (Ziel 0 Glanz und
 * Sonstiges, Ziel 1 diffuses Hautlicht) → Streuung (zwei Durchgänge, addiert in Ziel 0) → Tonkurve.
 */
public final class StudioScene implements GLEventListener {

    public final Camera cam = new Camera();
    /** Licht der Szene, gemeinsam für Bühne und Personen. */
    public final AvatarGl.Light light = new AvatarGl.Light();
    /** Personen auf der Bühne. */
    public final List<Figure> figures = new CopyOnWriteArrayList<>();

    /** Eine Person auf der Bühne: wer sie ist, ihr Körper, ihre Pose und wo sie steht. */
    public static final class Figure implements com.dan.avatar.Actor.Host {
        public final Body body;
        public final Skeleton skeleton;
        public volatile Person person;
        public volatile float x, z;
        /** Pose nach Namen (null oder "ruhe" = Ruhehaltung). */
        public volatile String pose;
        /** Dual-Quaternion (Volumenerhalt) oder linear. */
        public volatile boolean dualQuaternion = true;
        private volatile int version, poseVersion;
        private int drawn = -1, posed = -1;
        private AvatarGl gl;
        /** Haare (Phase 6): Karten, Fliegenhaare, Bart, Bewegung. */
        HairRig hair;
        /** Fußhöhe der gemischten Ruhelage (zum Aufstellen). */
        public volatile float footY;
        /** Zuletzt gerechnete Weltmatrizen der Gelenke (für Prüfung und Blick). */
        public volatile double[][] jointWorld;
        /** Leben, Blick, Gefühl (Phase 7); entsteht mit dem Körper. */
        public volatile com.dan.avatar.Behavior behavior;
        /** Verhalten in jedem Bild anwenden (sonst Standbild wie bis Phase 6). */
        public volatile boolean behaviorOn = true;
        /** Gesichtsformen des Verhaltens im letzten Bild. */
        public volatile java.util.Map<String, Double> lastForms = java.util.Map.of();
        private java.util.Map<String, Double> baseW;
        private double[][] rest;
        private boolean wasLive;
        private final Skeleton.Additive add = new Skeleton.Additive();
        /** Nässe je Zone, Tropfen auf der Haut, Atemhauch (Phase 8). */
        public final com.dan.avatar.Wetness wetness = new com.dan.avatar.Wetness();
        public volatile com.dan.avatar.SkinDrops drops;
        public volatile com.dan.avatar.BreathFog breath;
        private com.dan.avatar.gl.DropMapGl dropGl;
        private com.dan.avatar.gl.BreathGl breathGl;
        private volatile boolean clearDrops;
        private double dropIdle;
        /** Gezeichnet (Figuren der Menge sind nur sichtbar, wenn eine nahe Person sie braucht, Phase 12). */
        public volatile boolean visible = true;
        /** Detailstufe 0 (Porträt) oder 1 (halbnah: ohne Streuung, ohne einzelne Haare). */
        public volatile int lod;
        /** Die Bewegung kommt von außen (Menge) und wird hier nicht weitergerechnet. */
        public volatile boolean motionExternal;
        /** Person der Menge, die diese Figur gerade zeigt (oder null). */
        volatile com.dan.avatar.Crowd.Member member;
        /** Bewegung (Phase 11): Gehen, Drehen, Sitzen, Gesten; null = steht in ihrer Pose am Ort (x, z). */
        public volatile com.dan.avatar.Motion motion;
        private boolean motionReady;
        /** Bank aus der Pose „sitzen“: Abstand hinter den Füßen und Höhe der Sitzfläche (m). */
        public volatile double seatBack = 0.45, seatH = 0.45;
        /** Kleidung (Phase 10): was die Person trägt; beim nächsten Bild angezogen. */
        public volatile com.dan.avatar.Outfit outfit = com.dan.avatar.Outfit.preset("Alltag");
        private com.dan.avatar.Outfit dressed;
        /** Kleidungsnetze, die gerade nicht getragen werden. */
        final java.util.Set<String> clothHidden = new java.util.HashSet<>();
        /** Nachbewegung loser Teile. */
        public final com.dan.avatar.ClothSway clothSway = new com.dan.avatar.ClothSway();
        private double swayClock;
        /** Alles abtrocknen (Haut, Haar, Stoff, Tropfen). */
        public void dry() { wetness.dry(); clearDrops = true; }
        /** Höhe des Bodens am Ort (aus der Umwelt der Szene, Phase 13). */
        public volatile float groundY;
        /** Unterkante der Ruhelage in der Welt: Boden minus Fußhöhe der gemischten Ruhelage. */
        public float baseY() { return groundY - footY; }
        /** Rolle in der Regie (oder null). */
        public volatile com.dan.avatar.Actor actor;

        // ---- Actor.Host (Phase 13): was die Regie von der Person braucht
        @Override public com.dan.avatar.Behavior behavior() { return behavior; }
        @Override public com.dan.avatar.Motion motion() {
            com.dan.avatar.Motion m = motion;
            if (m == null) {
                m = new com.dan.avatar.Motion(com.dan.avatar.Motion.line(x, z, 0), 0);
                motionReady = false;
                motion = m;
            }
            return m;
        }
        @Override public com.dan.avatar.Outfit outfit() { return outfit; }
        @Override public void outfit(com.dan.avatar.Outfit o) { outfit = o; }
        @Override public double[] head() {
            double[][] w = jointWorld;
            if (w == null) return null;
            double[] hp = Skeleton.position(w, skeleton.joint("head"));
            return new double[]{hp[0] + x, hp[1] + baseY(), hp[2] + z};
        }
        @Override public void seat() {
            com.dan.avatar.Motion m = motion();
            m.placeBench(m.x, m.z, m.yaw, seatBack, seatH);
        }
        // ---- Sprechen (Phase 16)
        /** Die Spur, die gerade gesprochen wird (oder null). */
        public volatile com.dan.voice.Speech speech;
        /** Zeit in der Spur (s), wenn kein Ton läuft. */
        public volatile double speechT;
        /** Zeit anhalten (Statusbild, Prüfung): die Spur steht bei {@link #speechT}. */
        public volatile boolean speechHold;
        /** Ton zur Spur (seine Wiedergabe gibt die Zeit vor) oder null. */
        public volatile javax.sound.sampled.Clip clip;
        private com.dan.voice.Speech stepped;
        private int breathI, endI;

        /** Regie ({@code sage}): Text stumm sprechen, Dauer bis zum letzten Laut (s). */
        @Override public double say(String text) {
            com.dan.voice.Speech s = com.dan.voice.Speech.fromText(text, 1.0);
            speak(s, null);
            return s.duration;
        }

        /** Eine Spur sprechen, mit Ton (startet ihn von vorn) oder ohne. */
        public void speak(com.dan.voice.Speech s, javax.sound.sampled.Clip c) {
            javax.sound.sampled.Clip old = clip;
            if (old != null && old != c) { old.stop(); old.close(); }
            speechHold = false;
            speechT = 0;
            clip = c;
            speech = s;
            if (c != null) { c.setFramePosition(0); c.start(); }
        }

        /** Verstummen (Ton aus, Mund zurück). */
        public void hush() {
            javax.sound.sampled.Clip c = clip;
            clip = null;
            if (c != null) { c.stop(); c.close(); }
            speech = null;
            rhythm = null;
        }

        /** Zeit in der Spur (s): aus dem Ton, sonst mitgezählt. */
        public double speechTime() {
            javax.sound.sampled.Clip c = clip;
            return c != null && !speechHold ? c.getMicrosecondPosition() / 1e6 : speechT;
        }

        // ---- Tanz und Trommel (Phase 18)
        /** Tanz aus Bewegungsaufnahmen im Takt der Musik (oder null). */
        public volatile com.dan.avatar.Dance dance;
        /** Handtrommel (oder null): wird im Takt geschlagen. */
        public volatile com.dan.avatar.Drum drum;
        /** Zeit für Tanz und Trommel ohne Musik (s). */
        private double danceClock;

        /** Schläge stetig gezählt: aus der Musik (ab ihrer ersten Eins) oder ohne Musik mit 113 Schlägen je Minute. */
        public double beatNow() {
            com.dan.beat.Rhythm r = rhythm;
            return r != null ? r.beatAt(speechTime()) - r.downbeat : danceClock / beatSeconds();
        }

        /** Dauer eines Schlags (s). */
        public double beatSeconds() { com.dan.beat.Rhythm r = rhythm; return r != null ? 60 / r.bpm : 60 / 113.0; }

        // ---- Musik (Phase 17)
        /** Takt und Stimmung der laufenden Musik (oder null); die Zeit kommt wie beim Sprechen vom Ton. */
        public volatile com.dan.beat.Rhythm rhythm;
        /** Wie stark die Person mitgeht (0 … 1). */
        public volatile double grooveAmount = 1;
        /** Schlag-Puls des letzten Bilds (0 … 1, für das Licht im Takt). */
        public volatile double musicPulse;

        /** Musik spielen: Takt, gesungene Spur (oder null) und Ton (oder null, dann zählt die Szene die Zeit). */
        public void play(com.dan.beat.Rhythm r, com.dan.voice.Speech song, javax.sound.sampled.Clip c) {
            javax.sound.sampled.Clip old = clip;
            if (old != null && old != c) { old.stop(); old.close(); }
            speechHold = false;
            speechT = 0;
            clip = c;
            rhythm = r;
            speech = song;
            if (c != null) { c.setFramePosition(0); c.start(); }
        }

        /** Je Bild: Viseme ins Verhalten, beim Satzanfang einatmen, am Phrasenende bald blinzeln; Musik in Kopf und Schultern. */
        void speakStep(com.dan.avatar.Behavior b, double dt) {
            com.dan.voice.Speech s = speech;
            com.dan.beat.Rhythm r = rhythm;
            if (s != stepped) { stepped = s; breathI = 0; endI = 0; }
            if (s == null && r == null) {
                if (!b.speech.isEmpty()) b.speech = java.util.Map.of();
                b.talking = false;
                b.musicBeat = Double.NaN;
                musicPulse = 0;
                return;
            }
            if (clip == null && !speechHold) speechT += dt;
            double t = speechTime();
            if (s != null) {
                if (!speechHold) {
                    while (breathI < s.breaths.size() && s.breaths.get(breathI) <= t) { b.inhale(); breathI++; }
                    while (endI < s.phraseEnds.size() && s.phraseEnds.get(endI) <= t) { b.blinkSoon(0.15); endI++; }
                }
                b.speech = s.forms(t);
                b.talking = s.talking(t);
            } else {
                if (!b.speech.isEmpty()) b.speech = java.util.Map.of();
                b.talking = false;
            }
            if (r != null) {
                b.musicBeat = r.beatAt(t);
                b.musicDown = r.downbeat;
                b.groove = grooveAmount * (0.35 + 0.65 * r.energy(t)) * (dance != null ? 0.25 : drum != null ? 0.6 : 1);
                b.musicCalm = r.calm(t);
                musicPulse = r.pulse(t);
            } else { b.musicBeat = Double.NaN; musicPulse = 0; }
            javax.sound.sampled.Clip c = clip;
            boolean toneOn = c != null && c.isRunning();
            if (!speechHold && s != null && t > s.duration + 0.4 && r == null && !toneOn) {
                if (c != null) { c.stop(); c.close(); clip = null; }
                speech = null;
            }
            if (!speechHold && r != null && t > r.seconds + 0.2 && !toneOn) {
                if (c != null) { c.stop(); c.close(); clip = null; }
                speech = null;
                rhythm = null;
            }
        }

        /** Momentaufnahme (Ausgang der Schnittstelle). */
        public com.dan.avatar.Snapshot snapshot(com.dan.avatar.World world) {
            String n = actor != null ? actor.name : (person == null ? "?" : person.name);
            return com.dan.avatar.Snapshot.of(n, groundY, motion, behavior, wetness, outfit, x, z, head(), actor == null ? null : actor.gazeTarget());
        }

        public Figure(Body body, Skeleton skeleton, Person person, String pose, float x, float z) {
            this.body = body; this.skeleton = skeleton; this.person = person; this.pose = pose; this.x = x; this.z = z;
        }

        /** Neue Person (oder geänderte Regler): wird beim nächsten Bild gemischt. */
        public void set(Person p) { person = p; version++; }

        public void setPose(String p) { pose = p; poseVersion++; }

        public void setDualQuaternion(boolean dq) { dualQuaternion = dq; poseVersion++; }

        AvatarGl gl() { return gl; }

        private boolean allTriangles, reinit;

        /** Ohne Kleidung zeigen: Kleidung ausblenden und die verdeckten Körperdreiecke mitzeichnen. */
        public void setUnclothed(boolean on) {
            if (on) { if (!"ohne".equals(outfit.name)) beforeUnclothed = outfit; outfit = com.dan.avatar.Outfit.preset("ohne"); }
            else if ("ohne".equals(outfit.name)) outfit = beforeUnclothed != null ? beforeUnclothed : com.dan.avatar.Outfit.preset("Alltag");
            if (allTriangles != on) { allTriangles = on; reinit = true; }
        }
        private com.dan.avatar.Outfit beforeUnclothed;

        /** Netze, die nicht gezeichnet werden (Teil des Namens, z. B. "bob02"). */
        public final java.util.Set<String> hidden = new java.util.HashSet<>();

        /** Lage der Augen (Figur-Koordinaten), sobald gezeichnet. */
        public volatile com.dan.avatar.EyeRig.Frame[] eyes;
        /** Pupille: Durchmesser in mm (passt sich dem Licht an), Ziel aus dem letzten Bild. */
        public volatile double pupilMm = 4, pupilTargetMm = 4;
        /** Pupille sofort auf das Ziel setzen (für Standbilder). */
        public volatile boolean pupilInstant;

        /** Grundhäute und Zonen (Format, Ladezeit), sobald gezeichnet. */
        public String skinNote() { return gl == null ? "" : gl.skinNote(); }

        /** Aktive Frisur (Netzname) und Zahl der einzelnen Haare, sobald gezeichnet. */
        public String hairNote() {
            HairRig r = hair;
            if (r == null) return "";
            String m = r.activeMesh();
            return (m == null ? "Glatze" : m) + " · " + r.flyCount + " Fliegenhaare · " + r.beardCount + " Barthaare";
        }
    }
    public final Hud hud = new Hud();
    public volatile Quality quality = Quality.MEDIUM;
    public volatile boolean hudOn = true;
    public volatile double exposure = 1.0;
    /** Gedrückte Tasten für das Fliegen (WASD, Q/E, Umschalt). */
    public final java.util.Set<Integer> keys = java.util.concurrent.ConcurrentHashMap.newKeySet();
    /** Wind (m/s, Welt) für die Haare. */
    public volatile double[] wind = {0, 0, 0};
    /** Einzelne Haare zeichnen (Fliegenhaare, Bart). */
    public volatile boolean strands = true;
    /** Nur zum Vergleich im Statusbild: einzelne Haare als harte 1-Pixel-Linien (ohne Deckung, ohne Glättung). */
    public volatile boolean strandsHard;
    /** Feste Zeit für Wind und Böen (Standbilder) oder negativ = Uhr. */
    public volatile double hairTime = -1;
    /** GPU-Zeit der Haare (ms). */
    public volatile double msHair;
    /** Fester Zeitschritt (s) für Videos und Standbilder, sonst negativ = Uhr. */
    public volatile double fixedDt = -1;
    // ------------------------------------------------------------ Bild (Phase 9, com.dan.portrait)
    /** Objektiv: Brennweite, Blende, Fokus, Blendenform. */
    public final com.dan.portrait.Lens lens = new com.dan.portrait.Lens();
    /**
     * Bild-Werkzeuge an (Hintergrundlichter, Schärfentiefe, Farbtabelle, Korn, Bildwinkel aus dem Objektiv).
     * Aus: das Bild wie bis Phase 8 (Standbilder früherer Phasen bleiben vergleichbar).
     */
    public volatile boolean portraitOn = false;
    /** Bildwinkel der Kamera aus dem Objektiv (sonst die Brennweite aus dem Bildwinkel). */
    public volatile boolean lensFov = false;
    /** Fokus: 0 näheres Auge, 1 linkes Auge, 2 rechtes Auge, 3 von Hand ({@link com.dan.portrait.Lens#focusM}). */
    public volatile int focusMode = 0;
    /** Farbgebung, Stärke, Korn, Randabdunklung, Breitwand-Balken. */
    public volatile String grade = "Regennacht";
    public volatile float gradeMix = 1, grain = 0.4f, vignette = 0.32f;
    public volatile boolean bars;
    /** Hintergrund aus Lichtern (Stil, Anzahl, Helligkeit, Spiegelung auf nassem Boden). */
    public volatile String bokehStyle = "Regennacht";
    public volatile int bokehCount = 70;
    public volatile double bokehBright = 1;
    /** Licht im Takt (Phase 17): 0 aus, 1 voll (Belichtung +7 %, Lichter im Hintergrund +60 % auf dem Schlag). */
    public volatile double musicLight = 1;

    /** Anteil der Lichter im Hintergrund je Qualität (Phase 19). */
    static double qualityLights(Quality q) { return switch (q) { case LOW -> 0.55; case MEDIUM -> 1.0; case HIGH -> 1.3; case ULTRA -> 1.7; }; }

    /** Schwelle für die volle Figur (L0) je Qualität: auf „Niedrig“ erst bei größerem Bild, auf „Ultra“ früher. */
    static double qualityLod(Quality q) { return switch (q) { case LOW -> 1.5; case MEDIUM -> 1.0; case HIGH -> 0.8; case ULTRA -> 0.6; }; }

    /** Stärkster Schlag-Puls der Personen in diesem Bild. */
    double pulseNow() { double m = 0; for (Figure f : figures) m = Math.max(m, f.musicPulse); return m; }
    public volatile boolean bokehMirror = true;
    /** Lichter des Hintergrunds spiegeln sich in Augen, Tropfen und nasser Haut. */
    public volatile boolean envReflect = true;
    private com.dan.portrait.gl.PortraitGl portrait;
    private com.dan.portrait.BokehLights bokeh;
    private String gradeShown = "", bokehKey = "";
    /** Foto: Datei (.png oder .jpg) und Größe; nach dem Bild steht der Pfad in {@link #photoResult}. */
    public volatile java.io.File photoRequest;
    public volatile int photoW = 3840, photoH = 2160;
    public volatile String photoResult;
    /** GPU-Zeit von Hintergrundlichtern und Schärfentiefe (ms, Mittel). */
    public volatile double msPortrait;

    /** Umwelt am Ort der Personen (Phase 8): Regen, Wind, Temperatur, Feuchte, Kapuze. */
    public volatile com.dan.avatar.Climate climate = new com.dan.avatar.Climate();
    /** Zeitraffer der Umwelt (Nässe, Trocknen, Tropfen); 1 = Echtzeit. */
    public volatile double envSpeed = 1;
    /** Regen zeichnen (Streifen, Vorhang, Krönchen aus com.dan.rain). */
    public volatile boolean rainVisible = true;
    /** Prozessorzeit der Umwelt je Bild (ms) und GPU-Zeit des Regens. */
    public volatile double msEnv;
    private final com.dan.rain.Rain rain = com.dan.rain.Rain.calm();
    private com.dan.rain.gl.RainGl rainGl;
    private int depthCopyTex, depthCopyFbo;
    /** Prozessorzeit des Verhaltens je Bild (ms: Schritt, Pose, Formen, Augen). */
    public volatile double msBehavior;

    /** Bühnenradius in Metern. */
    public static final float STAGE_R = 3.0f;
    /** Höhe des Maßstabs in Metern. */
    public static final float RULER_H = 1.75f;

    private int progStage, progBack, progPost, progHud;
    private int vaoStage, vboStage, stageCount, vaoEmpty;
    private int fbo, colorTex, diffTex, depthTex, hudTex;
    /** Schattenkarte des Führungslichts (Größe nach Stufe). */
    public final ShadowMap shadowMap = new ShadowMap();
    /** Streuung der Haut (Proben nach Stufe). */
    public final SkinScatter scatter = new SkinScatter();
    /** Schatten an (Bühne und Personen). */
    public volatile boolean shadows = true;
    /** Durchleuchtung: Maßstab und Stärke (siehe {@link AvatarGl.Shading}). */
    public volatile float transScale = 0.2f, transStrength = 3.0f;
    /** GPU-Zeiten der Durchgänge (ms): Schatten, Hintergrund und Bühne, Personen, Streuung, Tonkurve. */
    public volatile double msShadow, msBack, msScene, msScatter, msPost;
    private final int[] stamps = new int[14];
    private int w = 1, h = 1;
    private final int[] query = new int[2];
    private int frame;
    private long t0 = System.nanoTime(), last = t0;
    private double fpsSmooth = 0, msSmooth = 0;
    public volatile double gpuMs = 0;
    public volatile String glInfo = "";
    private volatile String error;

    // ------------------------------------------------------------ GL

    @Override
    public void init(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        glInfo = gl.glGetString(GL.GL_RENDERER) + " · OpenGL " + gl.glGetString(GL.GL_VERSION).split(" ")[0];
        try {
            progStage = program(gl, "stage.vert", "stage.frag");
            progBack = program(gl, "fullscreen.vert", "backdrop.frag");
            progPost = program(gl, "fullscreen.vert", "post.frag");
            progHud = program(gl, "hud.vert", "hud.frag");
        } catch (Exception e) {
            error = e.getMessage();
            System.err.println("StudioScene: " + error);
        }
        buildStage(gl);
        try {
            kinoGl = new com.dan.kino.gl.KinoGl(kino, new com.dan.kino.KinoMaler());
            kinoGl.init(gl);
        } catch (Throwable k) { kinoGl = null; System.err.println("Kino: " + k); }
        portrait = new com.dan.portrait.gl.PortraitGl();
        portrait.init(gl);
        if (portrait.error() != null) error = portrait.error();
        try {
            rainGl = new com.dan.rain.gl.RainGl();
            rainGl.init(gl);
        } catch (Throwable t) { rainGl = null; error = "Regen: " + t.getMessage(); }
        int[] a = new int[1];
        gl.glGenVertexArrays(1, a, 0);
        vaoEmpty = a[0];
        gl.glGenQueries(2, query, 0);
        gl.glGenQueries(14, stamps, 0);
        gl.glGenTextures(1, a, 0);
        hudTex = a[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, hudTex);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.setSwapInterval(1);
    }

    @Override
    public void reshape(GLAutoDrawable d, int x, int y, int width, int height) {
        GL4 gl = d.getGL().getGL4();
        w = Math.max(1, width);
        h = Math.max(1, height);
        if (depthCopyTex != 0) { gl.glDeleteTextures(1, new int[]{depthCopyTex}, 0); gl.glDeleteFramebuffers(1, new int[]{depthCopyFbo}, 0); depthCopyTex = depthCopyFbo = 0; }
        targets(gl);
    }

    @Override
    public void display(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        long now = System.nanoTime();
        double dt = (now - last) / 1e9;
        last = now;
        if (fixedDt > 0) dt = fixedDt;
        lastDt = Math.max(1e-4, Math.min(0.1, dt));
        double t = (now - t0) / 1e9;
        if (dt > 0) {
            fpsSmooth = fpsSmooth == 0 ? 1 / dt : fpsSmooth * 0.95 + 0.05 / dt;
            msSmooth = msSmooth == 0 ? dt * 1000 : msSmooth * 0.95 + 0.05 * dt * 1000;
        }
        if (!keys.isEmpty()) {
            java.util.Set<Integer> k = keys;
            double f = (k.contains(java.awt.event.KeyEvent.VK_W) ? 1 : 0) - (k.contains(java.awt.event.KeyEvent.VK_S) ? 1 : 0);
            double r = (k.contains(java.awt.event.KeyEvent.VK_D) ? 1 : 0) - (k.contains(java.awt.event.KeyEvent.VK_A) ? 1 : 0);
            double u = (k.contains(java.awt.event.KeyEvent.VK_E) ? 1 : 0) - (k.contains(java.awt.event.KeyEvent.VK_Q) ? 1 : 0);
            cam.fly(f, r, u, Math.min(dt, 0.1), k.contains(java.awt.event.KeyEvent.VK_SHIFT));
        }

        if (photoRequest != null) photo(gl, t);
        filmStep(Math.min(dt, 0.1));
        renderFrame(gl, dt, t, 0);
    }

    /** Ein Bild der Szene in das Ziel {@code outFbo} (0 = Fenster) in der Größe w × h. */
    private void renderFrame(GL4 gl, double dt, double t, int outFbo) {
        if (fbo == 0) targets(gl);
        boolean pOn = portraitOn && portrait != null && portrait.error() == null;
        if (pOn && lensFov) cam.fovyDeg = lens.fovyDeg();
        else if (pOn) lens.focalMm = 12 / Math.tan(Math.toRadians(cam.fovyDeg) / 2);
        // GPU-Zeit des letzten Bilds abholen (zwei Abfragen im Wechsel)
        int q = query[frame & 1];
        if (frame > 1) {
            int[] r = new int[1];
            gl.glGetQueryObjectiv(query[(frame + 1) & 1], GL2ES3.GL_QUERY_RESULT_AVAILABLE, r, 0);
            if (r[0] != 0) {
                long[] ns = new long[1];
                gl.glGetQueryObjecti64v(query[(frame + 1) & 1], GL2ES3.GL_QUERY_RESULT, ns, 0);
                gpuMs = gpuMs * 0.9 + 0.1 * ns[0] / 1e6;
            }
        }
        gl.glBeginQuery(GL2ES3.GL_TIME_ELAPSED, q);
        int sb = (frame & 1) * 7;
        readStamps(gl, ((frame + 1) & 1) * 7);
        gl.glQueryCounter(stamps[sb], GL4.GL_TIMESTAMP);

        // Menge (Phase 12): Wege, Detailstufen, nahe Personen auf die Figuren
        crowdStep(gl, dt);
        // Personen vorbereiten (Morphs, Pose, Augen) und Schattenkarte
        prepareFigures(gl, dt);
        crowdPose(gl);
        environment(gl, dt);
        // Steg: das Licht der Person kommt aus den Laternen (Phase 13)
        JettySet jl = set;
        if (jl != null && setLight && !shown().isEmpty()) {
            double[] hd = shown().get(0).head();
            if (hd != null) setKeyLamp = jl.light(light, hd, cam.eye());
        }
        pupils(dt);
        focusAndMirror(dt);
        float ht = (float) (hairTime >= 0 ? hairTime : t);
        hairs(dt, ht);
        Quality qq = quality;
        boolean shadowOn = shadows && !shown().isEmpty();
        if (shadowOn) {
            double[] c = new double[3];
            double rr = figureBounds(c);
            shadowMap.fit(light.keyPos, c, rr);
            shadowMap.soft = qq == Quality.LOW ? 1.5f : 2.5f;
            shadowMap.begin(gl, qq == Quality.LOW ? 1024 : (qq == Quality.MEDIUM ? 2048 : 4096));
            for (Figure f : shown()) if (f.gl != null) {
                f.gl.drawDepth(gl, shadowMap.viewProj, f.x, f.baseY(), f.z);
                if (f.hair != null) f.hair.depth(gl, shadowMap.viewProj, f.x, f.baseY(), f.z, ht, f.hidden);
            }
            // Zubehör (Bank, Trommel) wirft Schatten (Phase 19); die Dreiecke vom letzten Bild genügen
            if (propCount > 0 && vaoProps != 0) {
                if (progPropDepth == 0) progPropDepth = programSrc(gl,
                        "#version 430\nlayout(location=0) in vec3 aPos; uniform mat4 uViewProj;\nvoid main(){ gl_Position = uViewProj * vec4(aPos, 1.0); }\n",
                        "#version 430\nvoid main(){ }\n");
                if (progPropDepth != 0) {
                    gl.glUseProgram(progPropDepth);
                    gl.glUniformMatrix4fv(gl.glGetUniformLocation(progPropDepth, "uViewProj"), 1, false, shadowMap.viewProj, 0);
                    gl.glBindVertexArray(vaoProps);
                    gl.glDrawArrays(GL.GL_TRIANGLES, 0, propCount);
                    gl.glBindVertexArray(0);
                }
            }
            shadowMap.end(gl);
        } else shadowMap.valid = false;
        gl.glQueryCounter(stamps[sb + 1], GL4.GL_TIMESTAMP);

        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glViewport(0, 0, w, h);
        gl.glDrawBuffers(2, new int[]{GL.GL_COLOR_ATTACHMENT0, GL2ES3.GL_COLOR_ATTACHMENT1}, 0);
        gl.glClearBufferfv(GL2ES3.GL_COLOR, 1, new float[]{0, 0, 0, 0}, 0);
        gl.glDrawBuffers(1, new int[]{GL.GL_COLOR_ATTACHMENT0}, 0);
        gl.glDisable(GL.GL_DEPTH_TEST);
        gl.glDisable(GL.GL_BLEND);
        if (progBack != 0) {
            gl.glUseProgram(progBack);
            gl.glUniform2f(loc(gl, progBack, "uSize"), w, h);
            gl.glUniform1f(loc(gl, progBack, "uTime"), (float) t);
            gl.glUniform1f(loc(gl, progBack, "uYaw"), (float) Math.toRadians(cam.yawDeg));
            gl.glUniform1f(loc(gl, progBack, "uGain"), light.gain);
            gl.glUniform1i(loc(gl, progBack, "uDiscs"), pOn || set != null ? 0 : 1);
            gl.glBindVertexArray(vaoEmpty);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        // Hintergrund aus Lichtern als Zerstreuungskreise (portrait)
        if (pOn) {
            // Fächer der Kamera gegenüber (in Schritten von 15°, damit beim Umkreisen nicht jedes Bild neu gewürfelt wird)
            long fan = Math.round(cam.yawDeg / 15.0);
            // Licht im Takt (Phase 17): die Lichter in sechs Stufen heller (nur beim Wechsel der Stufe neu)
            int pulseLevel = (int) Math.round(6 * Math.min(1, musicLight * pulseNow()));
            // Qualität (Phase 19): Zahl der Lichter im Hintergrund nach Stufe
            int nLights = (int) Math.round(bokehCount * qualityLights(quality));
            String key = bokehStyle + "|" + nLights + "|" + bokehBright + "|" + bokehMirror + "|" + fan + "|" + light.gain + "|" + pulseLevel;
            if (!key.equals(bokehKey)) {
                bokeh = com.dan.portrait.BokehLights.city(bokehStyle, 4711, new double[]{cam.tx, 0, cam.tz}, nLights, bokehBright * light.gain * (1 + 0.6 * pulseLevel / 6.0), bokehMirror,
                        Math.toRadians(fan * 15.0) + Math.PI, Math.toRadians(55));
                portrait.lights(gl, bokeh);
                bokehKey = key;
            }
            if (!grade.equals(gradeShown)) { portrait.grade(gl, grade); gradeShown = grade; }
            portrait.background(gl, Camera.mul(cam.proj((double) w / h), cam.view()), lens, w, h);
        }

        gl.glClear(GL.GL_DEPTH_BUFFER_BIT);
        gl.glEnable(GL.GL_DEPTH_TEST);
        gl.glDepthFunc(GL.GL_LESS);
        boolean split = qq != Quality.LOW && !Boolean.getBoolean("avatar.ohneStreuung");
        if (progStage != 0) {
            float[] vp = Camera.mul(cam.proj((double) w / h), cam.view());
            double[] e = cam.eye();
            gl.glUseProgram(progStage);
            gl.glUniformMatrix4fv(loc(gl, progStage, "uViewProj"), 1, false, vp, 0);
            gl.glUniform3f(loc(gl, progStage, "uEye"), (float) e[0], (float) e[1], (float) e[2]);
            AvatarGl.Light l = light;
            gl.glUniform3fv(loc(gl, progStage, "uKeyPos"), 1, l.keyPos, 0);
            gl.glUniform3fv(loc(gl, progStage, "uKeyDir"), 1, l.keyDir, 0);
            gl.glUniform3fv(loc(gl, progStage, "uKeyCol"), 1, l.g(l.keyCol), 0);
            gl.glUniform2f(loc(gl, progStage, "uKeyCone"), (float) Math.cos(Math.toRadians(l.keyConeInner)), (float) Math.cos(Math.toRadians(l.keyConeOuter)));
            gl.glUniform3fv(loc(gl, progStage, "uRimPos"), 1, l.rimPos, 0);
            gl.glUniform3fv(loc(gl, progStage, "uRimCol"), 1, l.g(l.rimCol), 0);
            gl.glUniform3fv(loc(gl, progStage, "uFillCol"), 1, l.g(l.fillCol), 0);
            gl.glUniform1f(loc(gl, progStage, "uStageRadius"), STAGE_R);
            shadowMap.bind(gl, progStage, 1);
            JettySet js = set;
            if (js != null) {
                if (!js.ready()) { js.init(gl); if (js.error() != null) error = js.error(); }
                js.draw(gl, vp, e, t, climate.rain, shadowOn ? shadowMap : null, setKeyLamp);
                gl.glUseProgram(progStage);
            } else {
                gl.glBindVertexArray(vaoStage);
                gl.glDrawArrays(GL.GL_TRIANGLES, 0, stageCount);
            }
            drawProps(gl);
            if (crowd != null) drawGround(gl);
            gl.glQueryCounter(stamps[sb + 2], GL4.GL_TIMESTAMP);
            gl.glDrawBuffers(2, new int[]{GL.GL_COLOR_ATTACHMENT0, GL2ES3.GL_COLOR_ATTACHMENT1}, 0);
            AvatarGl.Shading sh = new AvatarGl.Shading();
            sh.shadow = shadowOn ? shadowMap : null;
            sh.split = split;
            sh.low = qq == Quality.LOW;
            sh.transScale = transScale;
            sh.transStrength = transStrength;
            for (Figure f : shown()) {
                if (f.gl == null) continue;
                sh.split = split && f.lod == 0;
                f.gl.hidden.clear();
                f.gl.hidden.addAll(f.hidden);
                f.gl.hidden.addAll(f.clothHidden);
                f.gl.draw(gl, vp, e, light, sh, f.x, f.baseY(), f.z);
            }
            drawCrowd(gl, vp, e);
            gl.glQueryCounter(stamps[sb + 3], GL4.GL_TIMESTAMP);
            // Haare: erst alle deckenden Karten, dann Säume und einzelne Haare (ohne Sortieren)
            float focalH = (float) (0.5 * h / Math.tan(Math.toRadians(cam.fovyDeg) / 2));
            com.dan.hair.gl.HairGl.Lighting li = HairRig.lighting(light, shadowOn ? shadowMap : null, e, focalH, ht);
            for (Figure f : shown()) if (f.hair != null) f.hair.drawOpaque(gl, li, vp, f.x, f.baseY(), f.z, f.hidden);
            for (Figure f : shown()) if (f.hair != null) { f.hair.strandsOn = strands && f.lod == 0 && quality != Quality.LOW; f.hair.hgl.hardLines = strandsHard; f.hair.hgl.strandDepth = portraitOn; f.hair.drawBlended(gl, li, vp, f.x, f.baseY(), f.z, f.hidden); }
            gl.glDrawBuffers(1, new int[]{GL.GL_COLOR_ATTACHMENT0}, 0);
            // Atemhauch (Phase 8)
            for (Figure f : shown())
                if (f.breathGl != null && f.breath != null && !f.breath.puffs().isEmpty())
                    f.breathGl.draw(gl, f.breath.data(), vp, e, light, focalH, f.x, f.baseY(), f.z);
            // Regen (com.dan.rain): Streifen um das Auge, Vorhang, Krönchen auf der Bühne
            if (rainVisible && rainGl != null && climate.rain > 0.01) drawRain(gl, vp, e);
        } else { gl.glQueryCounter(stamps[sb + 2], GL4.GL_TIMESTAMP); gl.glQueryCounter(stamps[sb + 3], GL4.GL_TIMESTAMP); }
        gl.glQueryCounter(stamps[sb + 4], GL4.GL_TIMESTAMP);

        // Streuung der Haut
        if (split && !figures.isEmpty()) {
            scatter.samples = qq == Quality.MEDIUM ? 11 : (qq == Quality.HIGH ? 17 : 25);
            float focal = (float) (0.5 * h / Math.tan(Math.toRadians(cam.fovyDeg) / 2));
            scatter.apply(gl, diffTex, depthTex, fbo, w, h, focal, (float) cam.near, (float) cam.far);
            if (scatter.error() != null) error = scatter.error();
        }
        gl.glQueryCounter(stamps[sb + 5], GL4.GL_TIMESTAMP);

        // Schärfentiefe (portrait)
        int src = colorTex;
        // Qualität (Phase 19): auf „Niedrig“ ohne Schärfentiefe (die Lichter bleiben Scheiben)
        portrait.dofOn = qq != Quality.LOW;
        if (pOn) src = portrait.depthOfField(gl, colorTex, depthTex, lens, cam.near, cam.far, w, h);

        // Nachbearbeitung ins Fenster (oder ins Foto)
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, outFbo);
        gl.glViewport(0, 0, w, h);
        gl.glDisable(GL.GL_DEPTH_TEST);
        if (progPost != 0) {
            gl.glUseProgram(progPost);
            gl.glActiveTexture(GL.GL_TEXTURE0);
            gl.glBindTexture(GL.GL_TEXTURE_2D, src);
            gl.glUniform1i(loc(gl, progPost, "uHdr"), 0);
            gl.glActiveTexture(GL.GL_TEXTURE1);
            gl.glBindTexture(GL2ES2.GL_TEXTURE_3D, portrait == null ? 0 : portrait.lutTexture());
            gl.glActiveTexture(GL.GL_TEXTURE0);
            gl.glUniform1i(loc(gl, progPost, "uLut"), 1);
            gl.glUniform1f(loc(gl, progPost, "uLutMix"), !pOn || portrait.lutTexture() == 0 ? 0 : gradeMix);
            gl.glUniform1f(loc(gl, progPost, "uGrain"), pOn ? grain * (h > 1600 ? 1.3f : 1f) : 0f);
            gl.glUniform1f(loc(gl, progPost, "uTime"), (float) t);
            gl.glUniform1f(loc(gl, progPost, "uBars"), pOn && bars ? (float) Math.max(0, (1 - (double) w / h / 2.39) / 2) : 0f);
            gl.glUniform1f(loc(gl, progPost, "uVignette"), pOn ? vignette : 0.35f);
            gl.glUniform2f(loc(gl, progPost, "uSize"), w, h);
            gl.glUniform1f(loc(gl, progPost, "uExposure"), (float) (exposure * (1 + 0.07 * musicLight * pulseNow())));
            gl.glBindVertexArray(vaoEmpty);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        gl.glQueryCounter(stamps[sb + 6], GL4.GL_TIMESTAMP);
        gl.glEndQuery(GL2ES3.GL_TIME_ELAPSED);

        // Kino (Phase 13): Schnitt merken, Balken, Titel, Untertitel, Überblendung, Korn
        if (outFbo == 0 && kinoGl != null && kino.laeuft()) {
            if (kino.schnittFaellig()) {
                kinoGl.merken(gl, outFbo, w, h);
                Runnable r = pendingCut;
                pendingCut = null;
                if (r != null) r.run();
                kino.geschnitten();
            }
            kinoGl.zeichnen(gl, outFbo, w, h, t);
        }
        if (outFbo == 0 && hudOn && progHud != 0 && !kino.laeuft()) drawHud(gl);
        frame++;
    }

    /**
     * Foto in hoher Auflösung (Phase 9): dieselbe Szene in {@link #photoW} × {@link #photoH} in ein eigenes
     * Ziel, ohne HUD, ohne Zeitschritt; gespeichert als PNG oder JPEG (nach der Endung).
     */
    private void photo(GL4 gl, double t) {
        java.io.File out = photoRequest;
        photoRequest = null;
        int ow = w, oh = h;
        w = photoW; h = photoH;
        targets(gl);
        if (depthCopyTex != 0) { gl.glDeleteTextures(1, new int[]{depthCopyTex}, 0); gl.glDeleteFramebuffers(1, new int[]{depthCopyFbo}, 0); depthCopyTex = depthCopyFbo = 0; }
        int[] a = new int[1];
        gl.glGenTextures(1, a, 0);
        int tex = a[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, w, h, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, null);
        gl.glGenFramebuffers(1, a, 0);
        int pf = a[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, pf);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, tex, 0);
        renderFrame(gl, 0, t, pf);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, pf);
        java.nio.ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        gl.glDeleteFramebuffers(1, new int[]{pf}, 0);
        gl.glDeleteTextures(1, new int[]{tex}, 0);
        final int pw = w, ph = h;
        w = ow; h = oh;
        targets(gl);
        if (depthCopyTex != 0) { gl.glDeleteTextures(1, new int[]{depthCopyTex}, 0); gl.glDeleteFramebuffers(1, new int[]{depthCopyFbo}, 0); depthCopyTex = depthCopyFbo = 0; }
        Thread th = new Thread(() -> {
            try {
                java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(pw, ph, java.awt.image.BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < ph; y++)
                    for (int x = 0; x < pw; x++) {
                        int i = ((ph - 1 - y) * pw + x) * 4;
                        img.setRGB(x, y, ((bb.get(i) & 255) << 16) | ((bb.get(i + 1) & 255) << 8) | (bb.get(i + 2) & 255));
                    }
                out.getAbsoluteFile().getParentFile().mkdirs();
                String n = out.getName().toLowerCase();
                if (n.endsWith(".jpg") || n.endsWith(".jpeg")) {
                    javax.imageio.ImageWriter wr = javax.imageio.ImageIO.getImageWritersByFormatName("jpeg").next();
                    javax.imageio.ImageWriteParam p = wr.getDefaultWriteParam();
                    p.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
                    p.setCompressionQuality(0.95f);
                    try (javax.imageio.stream.ImageOutputStream os = javax.imageio.ImageIO.createImageOutputStream(out)) {
                        wr.setOutput(os);
                        wr.write(null, new javax.imageio.IIOImage(img, null, null), p);
                    }
                    wr.dispose();
                } else javax.imageio.ImageIO.write(img, "png", out);
                photoResult = out.getAbsolutePath();
            } catch (Exception e) { photoResult = "! " + e.getMessage(); }
        }, "Foto speichern");
        th.start();
        lastPhotoThread = th;
    }

    /** Für Standbilder: auf das Speichern des letzten Fotos warten. */
    public void awaitPhoto() throws InterruptedException { Thread th = lastPhotoThread; if (th != null) th.join(); }
    private volatile Thread lastPhotoThread;

    /**
     * Fokus auf das gewählte Auge (Abstand entlang der Blickachse der Kamera, weich nachgeführt) und
     * Spiegelungen der Lichter in Augen und Wasser (die zwölf hellsten Lichter des Hintergrunds).
     */
    private void focusAndMirror(double dt) {
        double[] e = cam.eye();
        double[] fw = {cam.tx - e[0], cam.ty - e[1], cam.tz - e[2]};
        double fl = Math.sqrt(fw[0] * fw[0] + fw[1] * fw[1] + fw[2] * fw[2]);
        for (int k = 0; k < 3; k++) fw[k] /= fl;
        if (!shown().isEmpty()) {
            Figure f = shown().get(0);
            com.dan.avatar.EyeRig.Frame[] ey = f.eyes;
            if (ey != null && focusMode < 3) {
                double best = -1;
                for (int i = 0; i < 2; i++) {
                    double[] p = {ey[i].c[0] + f.x, ey[i].c[1] + f.baseY(), ey[i].c[2] + f.z};
                    double dz = (p[0] - e[0]) * fw[0] + (p[1] - e[1]) * fw[1] + (p[2] - e[2]) * fw[2];
                    boolean take = focusMode == 0 ? (best < 0 || dz < best) : (focusMode == 1 ? i == 0 : i == 1);
                    if (take) best = dz;
                }
                if (best > 0.05) {
                    double k = dt <= 0 || dt > 0.5 ? 1 : 1 - Math.exp(-dt / 0.12);
                    lens.focusM += (best - lens.focusM) * k;
                }
            }
            // Spiegelungen: Lichter vom Kopf aus
            JettySet je = set;
            if (f.gl != null && je != null && envReflect && f.jointWorld != null) {
                // Laternen und Boje spiegeln sich in Augen und Wasser, dazu die hellsten Lichter des Hintergrunds
                double[] hd = f.head();
                AvatarGl ag = f.gl;
                int n = 0;
                for (int i = 0; i < je.lampPos.length && n < 16; i++) {
                    float[] lp = je.lampPos[i];
                    double[] d = {lp[0] - hd[0], lp[1] - hd[1], lp[2] - hd[2]};
                    double dl = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
                    for (int k = 0; k < 3; k++) { ag.envDir[n * 3 + k] = (float) (d[k] / dl); ag.envCol[n * 3 + k] = je.lampCol[i][k] * 2.6f * 0.35f; }
                    ag.envCos[n] = (float) Math.cos(Math.atan(0.1 / dl));
                    n++;
                }
                if (portraitOn && bokeh != null) for (com.dan.portrait.BokehLights.Light l : bokeh.brightest(16 - n)) {
                    double[] d = {l.p[0] - hd[0], l.p[1] - hd[1], l.p[2] - hd[2]};
                    double dl = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
                    for (int k = 0; k < 3; k++) { ag.envDir[n * 3 + k] = (float) (d[k] / dl); ag.envCol[n * 3 + k] = (float) (l.col[k] * 0.35); }
                    ag.envCos[n] = (float) Math.cos(Math.atan(l.r / dl));
                    n++;
                }
                ag.envN = n;
            }
            else if (f.gl != null && (!portraitOn || !envReflect || bokeh == null)) f.gl.envN = 0;
            else if (f.gl != null && f.jointWorld != null) {
                double[] hp = com.dan.avatar.Skeleton.position(f.jointWorld, f.skeleton.joint("head"));
                double[] hd = {hp[0] + f.x, hp[1] + f.baseY(), hp[2] + f.z};
                java.util.List<com.dan.portrait.BokehLights.Light> top = bokeh.brightest(12);
                AvatarGl ag = f.gl;
                int n = 0;
                for (com.dan.portrait.BokehLights.Light l : top) {
                    double[] d = {l.p[0] - hd[0], l.p[1] - hd[1], l.p[2] - hd[2]};
                    double dl = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
                    for (int k = 0; k < 3; k++) { ag.envDir[n * 3 + k] = (float) (d[k] / dl); ag.envCol[n * 3 + k] = (float) (l.col[k] * 0.35); }
                    ag.envCos[n] = (float) Math.cos(Math.atan(l.r / dl));
                    n++;
                }
                ag.envN = n;
            }
        }
    }

    @Override
    public void dispose(GLAutoDrawable d) {
        GL4 gl = d.getGL().getGL4();
        freeTargets(gl);
        gl.glDeleteBuffers(1, new int[]{vboStage}, 0);
        gl.glDeleteVertexArrays(2, new int[]{vaoStage, vaoEmpty}, 0);
        gl.glDeleteTextures(1, new int[]{hudTex}, 0);
        gl.glDeleteQueries(2, query, 0);
        gl.glDeleteQueries(14, stamps, 0);
        shadowMap.dispose(gl);
        scatter.dispose(gl);
        for (int p : new int[]{progStage, progBack, progPost, progHud}) if (p != 0) gl.glDeleteProgram(p);
        for (Figure f : shown()) {
            if (f.hair != null) { f.hair.dispose(gl); f.hair = null; }
            if (f.gl != null) { f.gl.dispose(gl); f.gl = null; }
        }
    }

    // ------------------------------------------------------------ Personen

    /**
     * Morphs und Pose der Personen auf den neuesten Stand (Compute), vor Schatten und Bild. Mit Verhalten
     * (Phase 7) in jedem Bild: Schritt, Zugabe zur Pose, Gesichtsformen, Morphs, Skinning, Augen.
     */
    private void prepareFigures(GL4 gl, double dt) {
        double ms = 0;
        for (Figure f : shown()) {
            if (f.reinit && f.gl != null) {
                if (f.hair != null) { f.hair.dispose(gl); f.hair = null; }
                f.gl.dispose(gl); f.gl = null; f.drawn = -1; f.posed = -1;
            }
            f.reinit = false;
            int v = f.version, pv = f.poseVersion;
            boolean newBody = f.gl == null || f.drawn != v;
            if (f.gl == null) {
                f.gl = new AvatarGl();
                f.gl.allTriangles = f.allTriangles;
                f.gl.init(gl, f.body);
                if (f.gl.error() != null) error = f.gl.error();
                f.hair = new HairRig();
                f.hair.init(gl, f.gl, f.body);
                if (f.hair.error() != null) error = f.hair.error();
            }
            // nur die aktive Frisur und sichtbare Teile rechnen
            String activeHair = HairRig.STYLE_MESH.get(f.person.hair);
            for (int i = 0; i < f.body.parts.size(); i++) {
                String nm = f.body.parts.get(i).mesh.name;
                boolean idle = false;
                for (String hm : HairRig.STYLE_MESH.values()) if (hm != null && nm.contains(hm) && !hm.equals(activeHair)) idle = true;
                for (String hd : f.hidden) if (nm.contains(hd)) idle = true;
                if (f.clothHidden.contains(nm)) idle = true;
                if (idle) f.gl.idle.add(i); else f.gl.idle.remove(i);
            }
            dress(gl, f, dt);
            if (newBody) {
                java.util.Map<String, Double> w = f.person.weights();
                f.baseW = w;
                f.gl.setWeights(gl, w);
                f.gl.setPerson(f.person);
                f.footY = f.body.footY(w);
                f.rest = f.skeleton.restFor(w);
                ms += f.gl.lastMorphMs;
                f.drawn = v;
                if (f.behavior == null) f.behavior = new com.dan.avatar.Behavior(f.person.seed);
                java.util.Map<String, Double> plain = new java.util.LinkedHashMap<>(w);
                plain.keySet().removeIf(k -> k.startsWith("arkit.") || k.startsWith("visem."));
                f.behavior.calibrate(f.gl.eyeRig(), f.body, plain);
            }
            boolean live = f.behaviorOn && f.behavior != null;
            Pose p = f.pose == null ? null : f.skeleton.poses.get(f.pose);
            com.dan.avatar.Motion mo = f.motion;
            if (mo != null) {
                if (!f.motionReady) { if (!f.motionExternal) mo.calibrate(f.skeleton, f.rest, f.person); seat(f, mo); f.motionReady = true; }
                if (!f.motionExternal) { mo.step(dt); mo.trackFeet(); }
                f.x = (float) mo.x; f.z = (float) mo.z;
                int he = mo.takeHoodEvent();
                if (he != 0) f.outfit = f.outfit.withHood(he > 0);
                Pose st = f.skeleton.poses.get("stehen"), si = f.skeleton.poses.get("sitzen");
                if (st != null) p = mo.sit > 1e-3 && si != null ? Pose.blend(st, si, smooth01(mo.sit)) : st;
            }
            f.groundY = (float) world.ground(f.x, f.z);
            int hj = f.skeleton.joint("head");
            if (live) {
                long t0 = System.nanoTime();
                com.dan.avatar.Behavior b = f.behavior;
                double[] e = cam.eye();
                b.camera = toLocal(f, e[0], e[1] - f.baseY(), e[2]);
                b.standing = "stehen".equals(f.pose) && (mo == null || (mo.gait < 0.05 && mo.sit < 0.05));
                // Blick der Regie (Ort, Person) in Figur-Koordinaten; sonst beim Gehen voraus
                com.dan.avatar.Actor ac = f.actor;
                double[] gz = ac == null ? null : ac.gazeTarget();
                boolean directed = ac != null && ac.gazeDirected();
                if (gz != null) b.lookAt(toLocal(f, gz[0], gz[1] - f.baseY(), gz[2]));
                else if (mo != null && mo.gait > 0.05 && !directed) {
                    // beim Gehen geht der Blick voraus: 3 m vor die Füße auf dem Weg
                    double[] ahead = mo.path.at(mo.s + 3);
                    b.lookAt(toLocal(f, ahead[0], 1.2, ahead[1]));
                } else if (mo != null && !directed && b.look() == com.dan.avatar.Behavior.Look.POINT) b.look(com.dan.avatar.Behavior.Look.CAMERA);
                f.speakStep(b, dt);
                com.dan.avatar.Dance dn = f.dance;
                com.dan.avatar.Drum dr = f.drum;
                if (dn != null || dr != null) { f.danceClock += dt; if (dn != null) b.standing = false; }
                b.step(dt);
                b.additive(f.add);
                if (mo != null && dn == null) mo.additive(f.add);
                double[][] world;
                if (dn != null) {
                    dn.beatS = f.beatSeconds();
                    world = dn.world(f.skeleton, f.rest, f.beatNow(), f.add);
                } else {
                    world = f.skeleton.pose(p, f.rest, true, f.add);
                    if (mo != null) { mo.apply(f.skeleton, world, f.rest); mo.measure(f.skeleton, world, dt); }
                }
                if (dr != null) dr.apply(f.skeleton, world, f.beatNow(), 0, f.beatSeconds());
                java.util.Map<String, Double> forms = b.forms(world, f.rest, f.skeleton);
                java.util.Map<String, Double> all = new java.util.LinkedHashMap<>(f.baseW);
                java.util.Map<String, Double> face = new java.util.LinkedHashMap<>(f.person.forms);
                for (java.util.Map.Entry<String, Double> en : forms.entrySet()) {
                    all.merge(en.getKey(), en.getValue(), (a, c) -> Math.min(1, a + c));
                    face.merge(en.getKey(), en.getValue(), (a, c) -> Math.min(1, a + c));
                }
                f.gl.setWeights(gl, all);
                f.gl.setMouthOpen(AvatarGl.mouthOpening(face));
                double[][] sk = Skeleton.skin(world, f.rest);
                float[] dq = Skeleton.toDq(sk);
                f.gl.setSkin(gl, dq, Skeleton.toMat(sk), f.dualQuaternion);
                if (f.gl.eyeRig() != null && f.gl.eyeRig().valid()) {
                    f.eyes = f.gl.eyeRig().frames(f.body, all, dq);
                    f.gl.setEyes(f.eyes);
                }
                f.jointWorld = world;
                f.lastForms = forms;
                if (f.hair != null && hj >= 0) f.hair.headRotation(toF(Skeleton.skinRotation(world, f.rest, hj)));
                ms += f.gl.lastMorphMs + f.gl.lastSkinMs;
                f.posed = pv;
                f.wasLive = true;
                msBehavior = msBehavior * 0.9 + 0.1 * ((System.nanoTime() - t0) / 1e6 - f.gl.lastMorphMs - f.gl.lastSkinMs);
            } else if (newBody || f.posed != pv || f.wasLive || f.gl.needsRefresh() || f.gl.hasFlex() || mo != null) {
                if (f.wasLive || f.gl.needsRefresh()) { f.gl.setWeights(gl, f.baseW); f.gl.setPerson(f.person); f.lastForms = java.util.Map.of(); }
                f.wasLive = false;
                double[][] world;
                if (mo != null) {
                    f.add.clear();
                    mo.additive(f.add);
                    world = f.skeleton.pose(p, f.rest, true, f.add);
                    mo.apply(f.skeleton, world, f.rest);
                    mo.measure(f.skeleton, world, dt);
                } else world = f.skeleton.pose(p, f.rest);
                double[][] sk = Skeleton.skin(world, f.rest);
                float[] dq = Skeleton.toDq(sk);
                f.gl.setSkin(gl, dq, Skeleton.toMat(sk), f.dualQuaternion);
                if (f.gl.eyeRig() != null && f.gl.eyeRig().valid()) {
                    f.eyes = f.gl.eyeRig().frames(f.body, f.baseW, dq);
                    f.gl.setEyes(f.eyes);
                }
                f.jointWorld = world;
                if (f.hair != null && hj >= 0) f.hair.headRotation(toF(Skeleton.skinRotation(world, f.rest, hj)));
                ms += f.gl.lastSkinMs;
                f.posed = pv;
            }
            if (f.hair != null) f.hair.update(gl, f.gl, f.body, f.person, quality, f.hidden);
        }
        if (ms > 0) lastMixMs = ms;
    }

    // ------------------------------------------------------------ Szene, Regie, Kino (Phase 13)
    /** Demo-Szene „Steg“ (oder null: die Bühne). */
    public volatile JettySet set;
    /** Im Steg das Licht der Person aus den Laternen setzen. */
    public volatile boolean setLight = true;
    private volatile int setKeyLamp = -1;
    /** Orte der Bühne (Regie im Studio). */
    static final java.util.Map<String, double[]> STAGE_PLACES = new java.util.LinkedHashMap<>();
    static {
        STAGE_PLACES.put("Mitte", new double[]{0, 0, 0});
        STAGE_PLACES.put("Vorn", new double[]{0, 0, 1.8});
        STAGE_PLACES.put("Hinten", new double[]{0, 0, -1.8});
        STAGE_PLACES.put("Links", new double[]{1.8, 0, 0});
        STAGE_PLACES.put("Rechts", new double[]{-1.8, 0, 0});
    }
    /** Umwelt der Szene für die Personen: Boden, Orte, Kamera (Bühne oder Steg). */
    public final com.dan.avatar.World world = new com.dan.avatar.World() {
        @Override public double ground(double x, double z) { JettySet j = set; return j != null ? j.ground(x, z) : 0; }
        @Override public double[] place(String n) {
            JettySet j = set;
            if (j != null) return j.place(n);
            double[] p = STAGE_PLACES.get(n);
            return p == null ? null : p.clone();
        }
        @Override public java.util.List<String> places() { JettySet j = set; return j != null ? j.places() : new java.util.ArrayList<>(STAGE_PLACES.keySet()); }
        @Override public double[] camera() { return cam.eye(); }
    };
    /** Kinomodus aus com.dan.kino (Balken, Titel, Untertitel, Schnitte). */
    public final com.dan.kino.Kino kino = new com.dan.kino.Kino();
    private com.dan.kino.gl.KinoGl kinoGl;
    /** Kamera für Menschen (Einstellungen, folgen). */
    public final CameraRig rig = new CameraRig();
    /** Laufendes Regiebuch (oder null). */
    public volatile com.dan.avatar.Regie regie;
    private volatile Runnable pendingCut;
    private volatile boolean filmEnded;
    /** Zeit des Films (s), Zeile und Untertitel zuletzt (für Bedienfeld und Statusbild). */
    public volatile double filmClock;
    public volatile String filmLine = "", filmText = "";

    /**
     * Schnitt: im Kino merkt sich die Szene das letzte Bild, führt {@code r} vor dem nächsten aus und blendet
     * über; ohne Kino sofort.
     */
    public void cut(Runnable r) {
        if (kinoGl != null && kino.laeuft() && kino.schnitt()) {
            Runnable before = pendingCut;
            pendingCut = before == null ? r : () -> { before.run(); r.run(); };
        } else r.run();
    }

    /** Regiebuch im Kino beginnen (Vorspann mit Titel, dann die Zeilen). */
    public void startFilm(com.dan.avatar.Regie r, com.dan.avatar.Regie.Stage stage) {
        filmEnded = false;
        filmClock = 0;
        filmLine = ""; filmText = "";
        r.onText = s -> { filmText = s; kino.untertitel(s); };
        r.onLine = l -> filmLine = l.toString();
        kino.beginnen("Avatar · Regiebuch", r.title, r.subtitle);
        regie = r;
        r.start(stage);
    }

    /** Film abbrechen (Esc): Kino aus, Regie aus. */
    public void stopFilm() { kino.abbrechen(); regie = null; filmEnded = true; }

    /** Ist der Vorspann vorbei ({@code warte vorspann})? */
    public boolean afterTitle() { return !kino.laeuft() || kino.lage() != com.dan.kino.Kino.Lage.VORSPANN; }

    /** Regie und Kino um dt weiter, dann die Kamera. */
    public void filmStep(double dt) {
        com.dan.avatar.Regie r = regie;
        if (r != null) {
            filmClock += dt;
            r.step(dt);
            if (r.finished() && !filmEnded) {
                filmEnded = true;
                java.util.List<String> cr = new java.util.ArrayList<>();
                for (String c : r.credits) cr.add(c.replace(" | ", ": "));
                kino.beenden(cr);
            }
        }
        kino.schritt(dt);
        if (r != null && filmEnded && !kino.laeuft()) regie = null;
        rig.update(this, dt);
    }

    // ------------------------------------------------------------ Menge (Phase 12)
    /** Eine Gruppe vieler Personen; null = keine. Die Figuren in {@link #pool} zeigen die nahen (L0, L1). */
    public volatile com.dan.avatar.Crowd crowd;
    /** Figuren für die nahen Personen der Menge (höchstens zwei, F20); Teil von {@link #figures}. */
    public final List<Figure> pool = new java.util.ArrayList<>();
    /** Grenzen der Stufen in Bildpunkten der Personengröße: L0 ab, L1 ab, L2 ab (darunter L3) [G]. */
    public volatile double lod0 = 380, lod1 = 140, lod2 = 40;
    /** Stufen farbig zeigen (L0 weiß, L1 türkis, L2 bernstein, L3 rot). */
    public volatile boolean lodTint;
    /** Zahl der Personen je Stufe im letzten Bild, Prozessorzeit der Menge (ms). */
    public final int[] lodCount = new int[4];
    public volatile double msCrowd;
    private com.dan.avatar.gl.CrowdGl crowdGl;
    private final java.util.Map<com.dan.avatar.Crowd.Member, Lod> lods = new java.util.HashMap<>();
    private volatile Thread baker;
    private final java.util.concurrent.ConcurrentLinkedQueue<Object[]> baked = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private int crowdFrame;
    private com.dan.avatar.Crowd crowdReady;

    /** Stufen einer Person der Menge. */
    static final class Lod {
        com.dan.avatar.gl.CrowdGl.Mesh g2, g3;
        com.dan.avatar.LodMesh m2, m3;
        float footY;
        int slot;
        double px;
        /** Anteile: a = Figur (L0/L1), c = L2 gegenüber L3. */
        float a, c = 1;
        boolean visible;
    }

    private List<Figure> shown() {
        List<Figure> l = new java.util.ArrayList<>(figures.size());
        for (Figure f : figures) if (f.visible) l.add(f);
        return l;
    }

    /** Teile und Farben einer Person der Menge für die fernen Stufen bauen (auf einem eigenen Faden). */
    private void startBaker(com.dan.avatar.Crowd cr, com.dan.gltf.Gltf model) {
        Thread t = new Thread(() -> {
            Body b = new Body(model);
            java.util.Map<Integer, boolean[]> coverCache = new java.util.HashMap<>();
            for (com.dan.avatar.Crowd.Member m : cr.members) {
                if (crowd != cr) return;
                try {
                    com.dan.avatar.LodMesh[] ms = bake(b, m, coverCache);
                    baked.add(new Object[]{m, ms[0], ms[1], b.footY});
                } catch (RuntimeException ex) { error = "Menge: " + ex; }
            }
        }, "Menge backen");
        t.setDaemon(true);
        baker = t;
        t.start();
    }

    private com.dan.avatar.LodMesh[] bake(Body b, com.dan.avatar.Crowd.Member m, java.util.Map<Integer, boolean[]> coverCache) {
        b.mix(m.person.weights());
        Body.Part bodyP = b.body();
        com.dan.avatar.Outfit o = m.outfit;
        // Deckung der Teile ohne Gruppe (einmal je Teil)
        int[] extra = new int[bodyP.prim.vertexCount];
        int mask = 0;
        java.util.List<Body.Part> parts = new java.util.ArrayList<>();
        java.util.List<int[]> tris = new java.util.ArrayList<>();
        java.util.List<float[]> cols = new java.util.ArrayList<>();
        boolean robe = false;
        for (com.dan.avatar.Outfit.Piece q : o.pieces) robe |= q.mesh.equals(com.dan.avatar.Outfit.ROBE);
        String hairMesh = HairRig.STYLE_MESH.get(m.person.hair);
        for (int i = 0; i < b.parts.size(); i++) {
            Body.Part p = b.parts.get(i);
            String nm = p.mesh.name;
            com.dan.avatar.Wardrobe.Item it = wardrobe.item(nm);
            if (it != null) {
                com.dan.avatar.Outfit.Piece pc = null;
                for (com.dan.avatar.Outfit.Piece q : o.pieces) if (wardrobe.find(q.mesh) == it) pc = q;
                if (pc == null) continue;
                mask |= 1 << it.bit;
                if (!it.groups && !nm.startsWith("Monks_Hood")) {
                    final Body.Part bp0 = b.body(), cp0 = p;
                    boolean[] cv = coverCache.computeIfAbsent(it.bit, k -> com.dan.avatar.Wardrobe.cover(bp0, cp0));
                    for (int v = 0; v < cv.length; v++) if (cv[v]) extra[v] |= 1 << it.bit;
                }
                parts.add(p);
                tris.add(nm.startsWith("Monks_Hood") && !robe ? above(p, 1.15f) : p.prim.indices);
                double[] c = pc.fabric == null ? new double[]{0.10, 0.14, 0.26} : pc.colorOrDefault();
                cols.add(new float[]{(float) c[0], (float) c[1], (float) c[2]});
            } else if (hairMesh != null && nm.contains(hairMesh)) {
                parts.add(p);
                tris.add(p.prim.indices);
                cols.add(com.dan.avatar.LodMesh.hairColor(m.person.hairColor, m.person.hairRed));
            }
        }
        int[] keepExtra = bodyP.extraCover;
        bodyP.extraCover = extra;
        int[] bodyTris = bodyP.visibleIndices(mask);
        bodyP.extraCover = keepExtra;
        parts.add(0, bodyP);
        tris.add(0, bodyTris);
        cols.add(0, com.dan.avatar.Skin.tone(m.person.melanin, m.person.redness));
        return new com.dan.avatar.LodMesh[]{com.dan.avatar.LodMesh.build(b, parts, tris, cols, 0.035), com.dan.avatar.LodMesh.build(b, parts, tris, cols, 0.09)};
    }

    /** Wege der Menge, Größe im Bild je Person, Stufen mit Übergängen; die nahen Personen bekommen die Figuren. */
    private void crowdStep(GL4 gl, double dt) {
        com.dan.avatar.Crowd cr = crowd;
        if (cr == null) {
            if (crowdReady != null) { for (Figure f : pool) f.visible = true; crowdReady = null; }
            return;
        }
        long t0 = System.nanoTime();
        if (crowdReady != cr) {
            if (pool.isEmpty() || pool.get(0).body == null) return;
            if (crowdGl == null) {
                crowdGl = new com.dan.avatar.gl.CrowdGl();
                crowdGl.init(gl, pool.get(0).skeleton.size(), 256);
                if (crowdGl.error() != null) error = crowdGl.error();
            }
            for (Lod l : lods.values()) { crowdGl.free(gl, l.g2); crowdGl.free(gl, l.g3); }
            lods.clear();
            cr.calibrate(pool.get(0).skeleton);
            int slot = 0;
            for (com.dan.avatar.Crowd.Member m : cr.members) { Lod l = new Lod(); l.slot = slot++; lods.put(m, l); }
            for (Figure f : pool) { f.visible = false; f.member = null; }
            startBaker(cr, pool.get(0).body.model);
            crowdReady = cr;
        }
        for (Object[] b; (b = baked.poll()) != null; ) {
            Lod l = lods.get(b[0]);
            if (l == null) continue;
            l.m2 = (com.dan.avatar.LodMesh) b[1]; l.m3 = (com.dan.avatar.LodMesh) b[2]; l.footY = (float) b[3];
            l.g2 = crowdGl.upload(gl, l.m2); l.g3 = crowdGl.upload(gl, l.m3);
        }
        cr.step(dt);
        // Größe im Bild
        double[] e = cam.eye();
        double focal = 0.5 * h / Math.tan(Math.toRadians(cam.fovyDeg) / 2);
        double[] fw = {cam.tx - e[0], cam.ty - e[1], cam.tz - e[2]};
        double fl = Math.sqrt(fw[0] * fw[0] + fw[1] * fw[1] + fw[2] * fw[2]);
        java.util.Arrays.fill(lodCount, 0);
        java.util.List<com.dan.avatar.Crowd.Member> near = new java.util.ArrayList<>();
        for (com.dan.avatar.Crowd.Member m : cr.members) {
            Lod l = lods.get(m);
            com.dan.avatar.Motion mo = m.motion;
            double dx = mo.x - e[0], dy = m.height * 0.55 - e[1], dz = mo.z - e[2];
            double d = Math.max(0.05, Math.sqrt(dx * dx + dy * dy + dz * dz));
            double ahead = (dx * fw[0] + dy * fw[1] + dz * fw[2]) / fl;
            l.visible = ahead > -1.0;
            l.px = m.height * focal / d;
            l.c = (float) smooth(lod2 * 0.9, lod2 * 1.1, l.px);
            double a = smooth(lod1 * 0.9, lod1 * 1.1, l.px);
            l.a = (float) a;
            if (a > 0 && l.visible) near.add(m);
        }
        // die größten bekommen die Figuren
        near.sort((p, q) -> Double.compare(lods.get(q).px, lods.get(p).px));
        java.util.Set<com.dan.avatar.Crowd.Member> keep = new java.util.HashSet<>(near.subList(0, Math.min(pool.size(), near.size())));
        for (Figure f : pool) if (f.member != null && !keep.contains(f.member)) { f.member = null; f.visible = false; }
        for (com.dan.avatar.Crowd.Member m : keep) {
            Figure f = null;
            for (Figure g : pool) if (g.member == m) f = g;
            if (f == null) for (Figure g : pool) if (g.member == null) { f = g; break; }
            if (f == null) continue;
            if (f.member != m) {
                f.member = m;
                f.set(m.person);
                f.outfit = m.outfit;
                f.motion = m.motion;
                f.motionExternal = true;
                f.motionReady = false;
                f.behaviorOn = true;
            }
            f.visible = crowdVisible;
        }
        for (com.dan.avatar.Crowd.Member m : cr.members) {
            Lod l = lods.get(m);
            Figure f = null;
            for (Figure g : pool) if (g.member == m) f = g;
            if (f == null) { l.a = 0; continue; }
            f.x = (float) m.motion.x; f.z = (float) m.motion.z;
            f.lod = l.px >= lod0 * qualityLod(quality) ? 0 : 1;
            float a = f.drawn == f.version ? l.a : 0;            // erst zeigen, wenn die neue Person gemischt ist
            l.a = a;
            if (f.gl() != null) { f.gl().dither[0] = 0; f.gl().dither[1] = a; }
            if (f.hair != null) { f.hair.hgl.dither[0] = 0; f.hair.hgl.dither[1] = a; }
        }
        for (com.dan.avatar.Crowd.Member m : cr.members) {
            Lod l = lods.get(m);
            if (!l.visible) continue;
            int lv = l.a >= 0.5 ? (l.px >= lod0 * qualityLod(quality) ? 0 : 1) : (l.c >= 0.5 ? 2 : 3);
            lodCount[lv]++;
        }
        msCrowd = msCrowd * 0.9 + 0.1 * (System.nanoTime() - t0) / 1e6;
    }

    /** Gelenke der fernen Personen (L2 jedes zweite, L3 jedes vierte Bild) auf den Prozessor gerechnet. */
    private void crowdPose(GL4 gl) {
        com.dan.avatar.Crowd cr = crowd;
        if (cr == null || crowdReady != cr || crowdGl == null || pool.isEmpty()) return;
        long t0 = System.nanoTime();
        com.dan.avatar.Skeleton sk = pool.get(0).skeleton;
        Pose st = sk.poses.get("stehen");
        com.dan.avatar.Skeleton.Additive add = new com.dan.avatar.Skeleton.Additive();
        crowdFrame++;
        for (com.dan.avatar.Crowd.Member m : cr.members) {
            Lod l = lods.get(m);
            if (!l.visible || l.a >= 1 || l.g2 == null) continue;
            int every = l.c > 0 ? (l.px > 80 ? 1 : 2) : 4;
            if ((crowdFrame + l.slot) % every != 0) continue;
            add.clear();
            m.motion.additive(add);
            double[][] w = sk.pose(st, m.rest, true, add);
            m.motion.apply(sk, w, m.rest);
            crowdGl.bones(gl, l.slot, com.dan.avatar.Skeleton.toDq(com.dan.avatar.Skeleton.skin(w, m.rest)));
        }
        msCrowd += (System.nanoTime() - t0) / 1e6 * 0.1;
    }

    private int vaoGround, vboGround;

    /** Weiter Boden für die Menge (nasser Schiefer wie die Bühne, ohne Rand). */
    private void drawGround(GL4 gl) {
        if (vaoGround == 0) {
            float R = 600, y = -0.004f;
            float[] q = {-R, y, -R, R, y, -R, R, y, R, -R, y, -R, R, y, R, -R, y, R};
            float[] d = new float[6 * 10];
            for (int i = 0; i < 6; i++) {
                d[i * 10] = q[i * 3]; d[i * 10 + 1] = q[i * 3 + 1]; d[i * 10 + 2] = q[i * 3 + 2];
                d[i * 10 + 3] = 0; d[i * 10 + 4] = 1; d[i * 10 + 5] = 0;
                d[i * 10 + 6] = 0.035f; d[i * 10 + 7] = 0.04f; d[i * 10 + 8] = 0.045f; d[i * 10 + 9] = 0.3f;
            }
            int[] a = new int[1];
            gl.glGenVertexArrays(1, a, 0); vaoGround = a[0];
            gl.glGenBuffers(1, a, 0); vboGround = a[0];
            gl.glBindVertexArray(vaoGround);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboGround);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) d.length * 4, java.nio.FloatBuffer.wrap(d), GL.GL_STATIC_DRAW);
            gl.glEnableVertexAttribArray(0); gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 40, 0);
            gl.glEnableVertexAttribArray(1); gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 40, 12);
            gl.glEnableVertexAttribArray(2); gl.glVertexAttribPointer(2, 4, GL.GL_FLOAT, false, 40, 24);
            gl.glBindVertexArray(0);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
        }
        gl.glUniform1f(loc(gl, progStage, "uStageRadius"), 1e6f);
        gl.glBindVertexArray(vaoGround);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 6);
        gl.glBindVertexArray(0);
        gl.glUniform1f(loc(gl, progStage, "uStageRadius"), STAGE_R);
    }

    private static final float[][] TINT = {{1, 1, 1}, {0.3f, 0.85f, 0.85f}, {0.95f, 0.7f, 0.3f}, {0.9f, 0.25f, 0.2f}};

    /** Menge zeichnen (aus: nur rechnen, zum Messen ihrer Kosten). */
    public volatile boolean crowdVisible = true;

    /** Wie viele Personen fertig gebacken sind (L2 und L3 auf der Grafikkarte). */
    public int crowdBaked() { int n = 0; for (Lod l : lods.values()) if (l.g2 != null) n++; return n; }

    /** Dreiecke je Stufe im Mittel der gebackenen Personen {L2, L3}. */
    public double[] crowdTriangles() {
        double a = 0, b = 0; int n = 0;
        for (Lod l : lods.values()) if (l.m2 != null) { a += l.m2.triangles; b += l.m3.triangles; n++; }
        return new double[]{a / Math.max(1, n), b / Math.max(1, n)};
    }

    private void drawCrowd(GL4 gl, float[] vp, double[] e) {
        com.dan.avatar.Crowd cr = crowd;
        if (cr == null || crowdReady != cr || crowdGl == null || !crowdVisible) return;
        crowdGl.begin(gl, vp, e, light);
        for (com.dan.avatar.Crowd.Member m : cr.members) {
            Lod l = lods.get(m);
            if (!l.visible || l.g2 == null || l.a >= 1) continue;
            float a = l.a, split = a + (1 - a) * l.c;
            float x = (float) m.motion.x, y = -l.footY, z = (float) m.motion.z;
            crowdGl.draw(gl, l.g2, l.slot, x, y, z, a, split, lodTint ? TINT[2] : null);
            crowdGl.draw(gl, l.g3, l.slot, x, y, z, split, 1, lodTint ? TINT[3] : null);
        }
    }

    static double smooth(double a, double b, double v) { double t = Math.max(0, Math.min(1, (v - a) / (b - a))); return t * t * (3 - 2 * t); }

    // ------------------------------------------------------------ Kleidung (Phase 10)
    /** Kleiderschrank des Grundkörpers (Netze und Bits). */
    public final com.dan.avatar.Wardrobe wardrobe = com.dan.avatar.Wardrobe.load();
    private final java.util.Map<com.dan.gltf.Gltf.Primitive, float[]> flexCache = new java.util.IdentityHashMap<>();
    private final java.util.Set<Long> coverDone = new java.util.HashSet<>();
    /** Kugel, in die die Kapuze das Haar drückt: Höhe über dem Kopfgelenk und Radius (m) [G]. */
    public volatile double hoodLift = 0.06, hoodRadius = 0.088;
    /** Nachbewegung loser Teile an (sonst steht die Kleidung wie in Blender angepasst). */
    public volatile boolean clothSwayOn = true;
    /** Wind für die Kleidung (m/s, Figur-Koordinaten); null = {@link #wind}. */
    public volatile double[] clothWind;

    /** Anziehen, wenn sich das Outfit geändert hat; Feder der losen Teile weiterrechnen. */
    private void dress(GL4 gl, Figure f, double dt) {
        com.dan.avatar.Outfit o = f.outfit;
        if (o != f.dressed) {
            f.clothHidden.clear();
            int mask = 0;
            for (int i = 0; i < f.body.parts.size(); i++) {
                Body.Part bp = f.body.parts.get(i);
                String nm = bp.mesh.name;
                com.dan.avatar.Wardrobe.Item it = wardrobe.item(nm);
                if (it == null) continue;
                com.dan.avatar.Outfit.Piece pc = null;
                for (com.dan.avatar.Outfit.Piece p : o.pieces) if (wardrobe.find(p.mesh) == it) pc = p;
                if (pc == null) {
                    f.clothHidden.add(nm);
                    f.gl.setFabric(i, null, null);
                    f.gl.setFlex(gl, i, null);
                } else {
                    mask |= 1 << it.bit;
                    f.gl.setFabric(i, pc.fabric, pc.color);
                    // die Kapuzen der Kutte tragen den Gürtelstrick mit: ohne Kutte weglassen (unter 1,15 m Grundform)
                    if (nm.startsWith("Monks_Hood")) {
                        boolean robe = false;
                        for (com.dan.avatar.Outfit.Piece q : o.pieces) robe |= q.mesh.equals(com.dan.avatar.Outfit.ROBE);
                        f.gl.setPartIndices(gl, i, robe ? bp.prim.indices : above(bp, 1.15f));
                    }
                    // Deckung selbst rechnen, wenn MPFB keine Gruppe mitbringt (nicht am Kopf: dort bleibt Luft)
                    Body.Part bodyP = f.body.body();
                    if (!it.groups && bodyP != null && !nm.startsWith("Monks_Hood") && coverDone.add(System.identityHashCode(f.body) * 64L + it.bit)) {
                        boolean[] cv = com.dan.avatar.Wardrobe.cover(bodyP, bp);
                        if (bodyP.extraCover == null) bodyP.extraCover = new int[bodyP.prim.vertexCount];
                        for (int v = 0; v < cv.length; v++) if (cv[v]) bodyP.extraCover[v] |= 1 << it.bit;
                    }
                    float[] fx = flexCache.computeIfAbsent(bp.prim, k -> flexOf(f.body, bp));
                    f.gl.setFlex(gl, i, maxOf(fx) > 0.05f ? fx : null);
                }
            }
            f.gl.setWorn(gl, mask);
            f.dressed = o;
            f.gl.idle.clear();                     // neu bestimmen: im nächsten Bild
        }
        // Feder: Anker am Hals, Wind aus der Umwelt
        if (f.jointWorld != null) {
            int nj = f.skeleton.joint("neck_01");
            double[] a = com.dan.avatar.Skeleton.position(f.jointWorld, nj >= 0 ? nj : 0);
            double[] wd = clothWind != null ? clothWind : (climate != null && climate.windSpeed() > 0 ? climate.wind : wind);
            f.clothSway.step(dt, a, wd);
            f.swayClock += Math.max(0, Math.min(0.1, dt));
            for (int k = 0; k < 3; k++) { f.gl.sway[k] = clothSwayOn ? (float) f.clothSway.sway[k] : 0; f.gl.flutter[k] = clothSwayOn ? (float) f.clothSway.flutter[k] : 0; }
            f.gl.swayTime = (float) f.swayClock;
            // Kapuze drückt das Haar an (Kugel um den Kopf)
            if (f.hair != null) {
                float[] hd = f.hair.hgl.hood;
                if (o.hoodUp()) {
                    double[] hp = com.dan.avatar.Skeleton.position(f.jointWorld, f.skeleton.joint("head"));
                    hd[0] = (float) (hp[0] + f.x); hd[1] = (float) (hp[1] + f.baseY() + hoodLift); hd[2] = (float) (hp[2] + f.z); hd[3] = (float) hoodRadius;
                } else hd[3] = 0;
            }
        }
    }

    /** Dreiecke eines Teils, von denen mindestens eine Ecke über {@code y} (Grundform) liegt. */
    static int[] above(Body.Part p, float y) {
        int[] ix = p.prim.indices, out = new int[ix.length];
        float[] pos = p.prim.position;
        int n = 0;
        for (int k = 0; k + 2 < ix.length; k += 3)
            if (pos[ix[k] * 3 + 1] > y || pos[ix[k + 1] * 3 + 1] > y || pos[ix[k + 2] * 3 + 1] > y) { out[n++] = ix[k]; out[n++] = ix[k + 1]; out[n++] = ix[k + 2]; }
        return java.util.Arrays.copyOf(out, n);
    }

    private static float maxOf(float[] a) { float m = 0; for (float x : a) m = Math.max(m, x); return m; }

    /**
     * Lose je Ecke eines Kleidungsstücks: Abstand zur nächsten Körperecke (Grundform), über
     * {@link com.dan.avatar.ClothSway#flex}. Ein Gitter mit 3 cm Zellen sucht die Nachbarn.
     */
    static float[] flexOf(Body b, Body.Part cloth) {
        Body.Part body = b.body();
        float[] bp = body.prim.position, cp = cloth.prim.position;
        double cs = 0.03;
        java.util.HashMap<Long, java.util.List<Integer>> grid = new java.util.HashMap<>();
        for (int i = 0; i < bp.length / 3; i++) {
            long key = key((int) Math.floor(bp[i * 3] / cs), (int) Math.floor(bp[i * 3 + 1] / cs), (int) Math.floor(bp[i * 3 + 2] / cs));
            grid.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(i);
        }
        int n = cp.length / 3;
        float[] fx = new float[n];
        for (int v = 0; v < n; v++) {
            int gx = (int) Math.floor(cp[v * 3] / cs), gy = (int) Math.floor(cp[v * 3 + 1] / cs), gz = (int) Math.floor(cp[v * 3 + 2] / cs);
            double best = 0.12 * 0.12;
            for (int dx = -3; dx <= 3; dx++) for (int dy = -3; dy <= 3; dy++) for (int dz = -3; dz <= 3; dz++) {
                java.util.List<Integer> l = grid.get(key(gx + dx, gy + dy, gz + dz));
                if (l == null) continue;
                for (int i : l) {
                    double ddx = bp[i * 3] - cp[v * 3], ddy = bp[i * 3 + 1] - cp[v * 3 + 1], ddz = bp[i * 3 + 2] - cp[v * 3 + 2];
                    double d2 = ddx * ddx + ddy * ddy + ddz * ddz;
                    if (d2 < best) best = d2;
                }
            }
            fx[v] = (float) com.dan.avatar.ClothSway.flex(Math.sqrt(best));
        }
        return fx;
    }

    private static long key(int x, int y, int z) { return ((long) (x & 0x1FFFFF) << 42) | ((long) (y & 0x1FFFFF) << 21) | (z & 0x1FFFFF); }

    static double smooth01(double v) { double t = Math.max(0, Math.min(1, v)); return t * t * (3 - 2 * t); }

    /** Szenenpunkt → Figur-Koordinaten (ohne Richtung, Blick +z), für Blickziele. */
    static double[] toLocal(Figure f, double wx, double wy, double wz) {
        double yaw = f.motion == null ? 0 : f.motion.yaw;
        double dx = wx - f.x, dz = wz - f.z, c = Math.cos(yaw), s = Math.sin(yaw);
        return new double[]{c * dx - s * dz, wy, s * dx + c * dz};
    }

    /** Sitzhöhe und Abstand der Bank aus der Pose „sitzen“ (Becken über der Sitzfläche). */
    private void seat(Figure f, com.dan.avatar.Motion mo) {
        Pose si = f.skeleton.poses.get("sitzen");
        if (si == null) return;
        double[][] w = f.skeleton.pose(si, f.rest, true);
        double[] fl = com.dan.avatar.Skeleton.position(w, f.skeleton.joint("foot_l")), fr = com.dan.avatar.Skeleton.position(w, f.skeleton.joint("foot_r"));
        double[] tl = com.dan.avatar.Skeleton.position(w, f.skeleton.joint("thigh_l")), tr = com.dan.avatar.Skeleton.position(w, f.skeleton.joint("thigh_r"));
        f.seatBack = (fl[2] + fr[2]) / 2 - (tl[2] + tr[2]) / 2 + 0.05;
        f.seatH = (tl[1] + tr[1]) / 2 - 0.085;
        if (mo.benchAt != null) {
            double[] p = mo.path.at(mo.benchAt[0]);
            mo.placeBench(p[0], p[1], Math.atan2(p[2], p[3]) + mo.benchAt[1], f.seatBack, f.seatH);
        }
    }

    // ------------------------------------------------------------ Bänke (Phase 11)
    private int vaoProps, vboProps, propCount, progPropDepth;
    private String propKey = "";

    private void drawProps(GL4 gl) {
        StringBuilder key = new StringBuilder();
        for (Figure f : shown()) if (f.motion != null && f.motion.bench != null) for (double v : f.motion.bench) key.append(Math.round(v * 1000)).append(',');
        for (Figure f : shown()) {
            com.dan.avatar.Drum dr = f.drum;
            if (dr == null) continue;
            key.append("T").append(f.x).append(',').append(f.baseY()).append(',').append(f.z).append(',').append(Math.round(dr.hit * 100));
            for (double v : dr.center) key.append(',').append(Math.round(v * 2000));
            for (double v : dr.normal) key.append(',').append(Math.round(v * 2000));
        }
        String k = key.toString();
        if (!k.equals(propKey)) {
            propKey = k;
            java.util.List<Float> d = new java.util.ArrayList<>();
            for (Figure f : shown()) if (f.motion != null && f.motion.bench != null) {
                double[] b = f.motion.bench;
                double h = b[3], wdt = 1.1, dep = 0.38, th = 0.05;
                float[] wood = {0.16f, 0.10f, 0.06f, 0.2f}, iron = {0.03f, 0.03f, 0.035f, 0.2f};
                box(d, b, 0, h - th / 2, 0, wdt / 2, th / 2, dep / 2, wood);
                for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2)
                    box(d, b, sx * (wdt / 2 - 0.06), (h - th) / 2, sz * (dep / 2 - 0.05), 0.025, (h - th) / 2, 0.025, iron);
            }
            for (Figure f : shown()) {
                com.dan.avatar.Drum dr = f.drum;
                if (dr != null) drumTris(d, dr, f.x, f.baseY(), f.z);
            }
            float[] arr = new float[d.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = d.get(i);
            if (vaoProps == 0) {
                int[] a = new int[1];
                gl.glGenVertexArrays(1, a, 0); vaoProps = a[0];
                gl.glGenBuffers(1, a, 0); vboProps = a[0];
                gl.glBindVertexArray(vaoProps);
                gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboProps);
                gl.glEnableVertexAttribArray(0); gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 40, 0);
                gl.glEnableVertexAttribArray(1); gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 40, 12);
                gl.glEnableVertexAttribArray(2); gl.glVertexAttribPointer(2, 4, GL.GL_FLOAT, false, 40, 24);
                gl.glBindVertexArray(0);
            }
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboProps);
            gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) Math.max(1, arr.length) * 4, java.nio.FloatBuffer.wrap(arr.length == 0 ? new float[1] : arr), GL.GL_DYNAMIC_DRAW);
            gl.glBindBuffer(GL.GL_ARRAY_BUFFER, 0);
            propCount = arr.length / 10;
        }
        if (propCount == 0) return;
        gl.glBindVertexArray(vaoProps);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, propCount);
        gl.glBindVertexArray(0);
    }

    /**
     * Rahmentrommel (Phase 18) als Dreiecke: Zarge aus Holz (außen hell, innen dunkel, Kanten), das Fell als Scheibe, beim
     * Schlag in der Mitte ein wenig eingedrückt. Lage aus der Trommel (Figur-Koordinaten) plus Ort der Figur.
     */
    private static void drumTris(java.util.List<Float> d, com.dan.avatar.Drum dr, double ox, double oy, double oz) {
        double[] c = dr.center, n = dr.normal, u = dr.right;
        double[] v = {n[1] * u[2] - n[2] * u[1], n[2] * u[0] - n[0] * u[2], n[0] * u[1] - n[1] * u[0]};
        int seg = 36;
        double R = dr.radius, D = dr.depth, dent = 0.010 * dr.hit;
        float[] wood = {0.42f, 0.27f, 0.14f, 0.3f}, woodIn = {0.22f, 0.14f, 0.08f, 0.3f}, skin = {0.82f, 0.72f, 0.55f, 0.45f}, rim = {0.30f, 0.19f, 0.10f, 0.3f};
        java.util.function.BiFunction<Double, Double, double[]> at = (ang, h) -> new double[]{
            ox + c[0] + R * (Math.cos(ang) * u[0] + Math.sin(ang) * v[0]) + h * n[0],
            oy + c[1] + R * (Math.cos(ang) * u[1] + Math.sin(ang) * v[1]) + h * n[1],
            oz + c[2] + R * (Math.cos(ang) * u[2] + Math.sin(ang) * v[2]) + h * n[2]};
        double[] mid = {ox + c[0] - dent * n[0], oy + c[1] - dent * n[1], oz + c[2] - dent * n[2]};
        for (int i = 0; i < seg; i++) {
            double a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
            double[] p00 = at.apply(a0, 0.0), p01 = at.apply(a1, 0.0), p10 = at.apply(a0, -D), p11 = at.apply(a1, -D);
            double[] r0 = {Math.cos(a0) * u[0] + Math.sin(a0) * v[0], Math.cos(a0) * u[1] + Math.sin(a0) * v[1], Math.cos(a0) * u[2] + Math.sin(a0) * v[2]};
            double[] r1 = {Math.cos(a1) * u[0] + Math.sin(a1) * v[0], Math.cos(a1) * u[1] + Math.sin(a1) * v[1], Math.cos(a1) * u[2] + Math.sin(a1) * v[2]};
            // Zarge außen und innen
            tri(d, p00, r0, p10, r0, p11, r1, wood); tri(d, p00, r0, p11, r1, p01, r1, wood);
            double[] i0 = {-r0[0], -r0[1], -r0[2]}, i1 = {-r1[0], -r1[1], -r1[2]};
            double[] q00 = shrink(p00, r0, 0.008), q01 = shrink(p01, r1, 0.008), q10 = shrink(p10, r0, 0.008), q11 = shrink(p11, r1, 0.008);
            tri(d, q00, i0, q11, i1, q10, i0, woodIn); tri(d, q00, i0, q01, i1, q11, i1, woodIn);
            // hintere Kante
            double[] bn = {-n[0], -n[1], -n[2]};
            tri(d, p10, bn, q10, bn, q11, bn, rim); tri(d, p10, bn, q11, bn, p11, bn, rim);
            // Fell
            tri(d, mid, n, p00, n, p01, n, skin);
        }
    }

    private static double[] shrink(double[] p, double[] r, double s) { return new double[]{p[0] - r[0] * s, p[1] - r[1] * s, p[2] - r[2] * s}; }

    private static void tri(java.util.List<Float> d, double[] a, double[] na, double[] b, double[] nb, double[] c, double[] nc, float[] col) {
        double[][] p = {a, b, c}, n = {na, nb, nc};
        for (int i = 0; i < 3; i++) {
            d.add((float) p[i][0]); d.add((float) p[i][1]); d.add((float) p[i][2]);
            d.add((float) n[i][0]); d.add((float) n[i][1]); d.add((float) n[i][2]);
            d.add(col[0]); d.add(col[1]); d.add(col[2]); d.add(col[3]);
        }
    }

    /** Quader (Mitte cx, cy, cz und halbe Maße örtlich zur Bank b = {x, z, Richtung, Höhe}) als 12 Dreiecke. */
    private static void box(java.util.List<Float> d, double[] b, double cx, double cy, double cz, double hx, double hy, double hz, float[] col) {
        double c = Math.cos(b[2]), s = Math.sin(b[2]);
        double[][] n = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (double[] nn : n) {
            double[] u = Math.abs(nn[1]) > 0.5 ? new double[]{1, 0, 0} : new double[]{0, 1, 0};
            double[] v = {nn[1] * u[2] - nn[2] * u[1], nn[2] * u[0] - nn[0] * u[2], nn[0] * u[1] - nn[1] * u[0]};
            double[][] q = new double[4][];
            int[][] sg = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (int i = 0; i < 4; i++) {
                double lx = cx + (nn[0] + sg[i][0] * u[0] + sg[i][1] * v[0]) * hx;
                double ly = cy + (nn[1] + sg[i][0] * u[1] + sg[i][1] * v[1]) * hy;
                double lz = cz + (nn[2] + sg[i][0] * u[2] + sg[i][1] * v[2]) * hz;
                q[i] = new double[]{b[0] + c * lx + s * lz, ly, b[1] - s * lx + c * lz};
            }
            double nx = c * nn[0] + s * nn[2], nz = -s * nn[0] + c * nn[2];
            for (int i : new int[]{0, 1, 2, 0, 2, 3}) {
                d.add((float) q[i][0]); d.add((float) q[i][1]); d.add((float) q[i][2]);
                d.add((float) nx); d.add((float) nn[1]); d.add((float) nz);
                d.add(col[0]); d.add(col[1]); d.add(col[2]); d.add(col[3]);
            }
        }
    }

    private static float[] toF(double[] d) {
        float[] r = new float[d.length];
        for (int i = 0; i < d.length; i++) r[i] = (float) d[i];
        return r;
    }

    /**
     * Umwelt je Person (Phase 8): Nässe je Zone, Tropfen auf der Haut, Atemhauch, Kälte; Werte an
     * Verhalten, Haut, Haare und Stoff. Zeitraffer über {@link #envSpeed}.
     */
    private void environment(GL4 gl, double dt) {
        long t0 = System.nanoTime();
        com.dan.avatar.Climate c = climate;
        double edt = Math.min(0.1, dt) * envSpeed;
        for (Figure f : shown()) {
            if (f.gl == null || f.jointWorld == null || f.rest == null) continue;
            if (f.drops == null) {
                f.drops = new com.dan.avatar.SkinDrops(new com.dan.avatar.DropField(f.body), f.person.seed);
                f.breath = new com.dan.avatar.BreathFog(f.person.seed);
                f.dropGl = new com.dan.avatar.gl.DropMapGl();
                f.dropGl.init(gl);
                if (f.dropGl.error() != null) error = f.dropGl.error();
                f.breathGl = new com.dan.avatar.gl.BreathGl();
                f.breathGl.init(gl);
                if (f.breathGl.error() != null) error = f.breathGl.error();
                double[] r = f.drops.field.rect;
                f.gl.dropRect = new float[]{(float) r[0], (float) r[1], (float) r[2], (float) r[3]};
                f.gl.drops = f.dropGl;
            }
            if (f.clearDrops) { f.drops.clear(); f.breath.clear(); f.dropGl.clear(gl); f.clearDrops = false; }
            com.dan.avatar.Wetness w = f.wetness;
            String hs = f.person.hair;
            w.hair = !"glatze".equals(hs);
            w.capacity[com.dan.avatar.Wetness.HAIR] = switch (hs) { case "kurz" -> 0.4; case "lang", "locken" -> 1.6; case "bob" -> 1.0; default -> 1.2; };
            int sub = Math.max(1, (int) Math.ceil(edt / 0.25));
            com.dan.avatar.Climate cf = c;
            if (f.outfit.hoodUp() && c.hood < 1) { cf = c.copy(); cf.hood = 1; }   // Kapuze des Outfits schützt
            for (int i = 0; i < sub; i++) w.step(edt / sub, cf);
            double faceWet = w.wet(com.dan.avatar.Wetness.FACE);
            float cold = (float) c.coldness(faceWet);
            com.dan.avatar.Behavior b = f.behavior;
            if (b != null) {
                b.cold = cold;
                b.rainOnFace = Math.min(1, w.inflow(com.dan.avatar.Wetness.FACE) / 5.0);
            }
            // Kopf: Skin-Matrix S (Ruhe → jetzt)
            int hj = f.skeleton.joint("head");
            double[] S = com.dan.avatar.Skeleton.skinMatrix(f.jointWorld, f.rest, hj);
            double[] from = w.rainFrom(c);
            double[] fromRest = {S[0] * from[0] + S[1] * from[1] + S[2] * from[2], S[4] * from[0] + S[5] * from[1] + S[6] * from[2], S[8] * from[0] + S[9] * from[1] + S[10] * from[2]};
            double evap = 0.35 / 3600 * (1 + 0.5 * c.windSpeed()) * Math.max(0.02, 1 - c.humidity) * Math.pow(2, (c.tempC - 20) / 12) * 2.0;
            f.drops.film = faceWet;
            int dsub = Math.min(20, Math.max(1, (int) Math.ceil(edt / 0.05)));
            for (int i = 0; i < dsub; i++) f.drops.step(edt / dsub, cf.rain * (1 - 0.78 * Math.max(0, Math.min(1, cf.hood))), fromRest, evap, w.hair);
            double tau = 20 + 200 * Math.pow(c.humidity, 4);
            float[] bd = f.drops.beadData(), td = f.drops.trailData();
            // trocken und ohne Perlen: die Karten nur so lange erneuern, bis die Spuren getrocknet sind
            if (bd.length > 0 || td.length > 0 || c.rain > 0) f.dropIdle = 0; else f.dropIdle += edt;
            if (f.dropIdle < 5 * tau) f.dropGl.update(gl, bd, td, (float) Math.exp(-edt / tau));
            else if (f.dropIdle < 5 * tau + 1) { f.dropGl.clear(gl); f.dropIdle = 1e9; }
            AvatarGl ag = f.gl;
            ag.wet[0] = (float) faceWet;
            ag.wet[1] = (float) w.wet(com.dan.avatar.Wetness.HAIR);
            ag.wet[2] = (float) ((w.wet(com.dan.avatar.Wetness.SHOULDERS) + w.wet(com.dan.avatar.Wetness.CHEST) + w.wet(com.dan.avatar.Wetness.BACK)) / 3);
            ag.wet[3] = (float) w.wet(com.dan.avatar.Wetness.LEGS);
            ag.rainFrom = new float[]{(float) from[0], (float) from[1], (float) from[2]};
            ag.cold = cold;
            int nj = f.skeleton.joint("neck_01"), pj = f.skeleton.joint("pelvis");
            if (nj >= 0) ag.neckY = (float) f.jointWorld[nj][13];
            if (pj >= 0) ag.hipY = (float) f.jointWorld[pj][13];
            if (f.hair != null) { f.hair.wetness = ag.wet[1]; f.hair.browWet = faceWet; }
            // Atemhauch: aus der Nase, bei offenem Mund aus dem Mund
            com.dan.avatar.SkinMasks sm = com.dan.avatar.SkinMasks.of(f.body);
            double mo = AvatarGl.mouthOpening(f.lastForms);
            double[] at, dir;
            if (mo > 0.12) { at = pt(S, sm.mouth[0], sm.mouth[1], sm.mouth[2] + 0.004); dir = vec(S, 0, -0.2, 0.98); }
            else { at = pt(S, sm.noseTip[0], sm.noseTip[1] - 0.014, sm.noseTip[2] - 0.006); dir = vec(S, 0, -0.62, 0.78); }
            double bdt = b == null ? dt : Math.min(0.1, dt) * b.timeScale;
            f.breath.step(bdt, b == null ? 0 : b.breathFlow(), at, dir, c.breathFog(), c.wind);
        }
        msEnv = msEnv * 0.9 + 0.1 * (System.nanoTime() - t0) / 1e6;
    }

    private static double[] pt(double[] S, double x, double y, double z) {
        return new double[]{S[0] * x + S[4] * y + S[8] * z + S[12], S[1] * x + S[5] * y + S[9] * z + S[13], S[2] * x + S[6] * y + S[10] * z + S[14]};
    }

    private static double[] vec(double[] S, double x, double y, double z) {
        double[] v = {S[0] * x + S[4] * y + S[8] * z, S[1] * x + S[5] * y + S[9] * z, S[2] * x + S[6] * y + S[10] * z};
        double l = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        return new double[]{v[0] / l, v[1] / l, v[2] / l};
    }

    /** Regen aus com.dan.rain ins HDR-Ziel: Tiefe kopieren, Licht der Bühne als Mond und Sonne. */
    private void drawRain(GL4 gl, float[] vp, double[] e) {
        com.dan.avatar.Climate c = climate;
        rain.rate = c.rain;
        double ws = c.windSpeed();
        rain.windSpeed = ws;
        if (ws > 0.05) rain.windFromDeg = windFrom(c.wind);
        rain.gust = 0.25;
        rain.cloudBase = 600;
        rain.eyeX = e[0]; rain.eyeY = e[1]; rain.eyeZ = e[2];
        rain.waterLevel = set != null ? JettySet.WATER_Y : -5;
        rain.quality = quality == Quality.HIGH || quality == Quality.ULTRA ? com.dan.rain.Rain.Quality.MEDIUM : com.dan.rain.Rain.Quality.LOW;
        rain.step(Math.max(1e-3, lastDt));
        rainGl.update(gl, rain.snapshot());
        // Tiefe kopieren (der Regen liest sie, während er ins selbe Ziel zeichnet)
        if (depthCopyTex == 0) {
            int[] a = new int[1];
            gl.glGenTextures(1, a, 0); depthCopyTex = a[0];
            gl.glBindTexture(GL.GL_TEXTURE_2D, depthCopyTex);
            gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_DEPTH_COMPONENT24, w, h, 0, com.jogamp.opengl.GL2ES2.GL_DEPTH_COMPONENT, GL.GL_UNSIGNED_INT, null);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
            gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
            gl.glGenFramebuffers(1, a, 0); depthCopyFbo = a[0];
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, depthCopyFbo);
            gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, depthCopyTex, 0);
        }
        gl.glBindFramebuffer(GL2ES3.GL_READ_FRAMEBUFFER, fbo);
        gl.glBindFramebuffer(GL2ES3.GL_DRAW_FRAMEBUFFER, depthCopyFbo);
        gl.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL.GL_DEPTH_BUFFER_BIT, GL.GL_NEAREST);
        // Der Hintergrund hat keine Tiefe (Fernebene). Für den Regen steht er 12 m hinter der Person,
        // sonst verschleiert der Vorhang (bis 8 km) die Lichter ganz.
        if (progFar == 0) progFar = programSrc(gl, FAR_VS, FAR_FS);
        if (progFar != 0) {
            double zf = backDepth(12 + Math.sqrt((cam.tx - e[0]) * (cam.tx - e[0]) + (cam.tz - e[2]) * (cam.tz - e[2])));
            gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, depthCopyFbo);
            gl.glDrawBuffers(1, new int[]{GL.GL_NONE}, 0);
            gl.glEnable(GL.GL_DEPTH_TEST);
            gl.glDepthFunc(GL.GL_LESS);
            gl.glDepthMask(true);
            gl.glUseProgram(progFar);
            gl.glUniform1f(gl.glGetUniformLocation(progFar, "uZ"), (float) (zf * 2 - 1));
            gl.glBindVertexArray(vaoEmpty);
            gl.glDrawArrays(GL.GL_TRIANGLES, 0, 3);
        }
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glDrawBuffers(1, new int[]{GL.GL_COLOR_ATTACHMENT0}, 0);
        com.dan.rain.gl.RainGl.View v = new com.dan.rain.gl.RainGl.View();
        v.viewProj = vp;
        v.invViewProj = Camera.invert(vp);
        v.eye = e.clone();
        double[] fw = {cam.tx - e[0], cam.ty - e[1], cam.tz - e[2]};
        double fl = Math.sqrt(fw[0] * fw[0] + fw[1] * fw[1] + fw[2] * fw[2]);
        v.forward = new double[]{fw[0] / fl, fw[1] / fl, fw[2] / fl};
        v.reversedZ = false;
        // der Hintergrund hat keine Tiefe (Fernebene): als 15 m werten, sonst verschleiert der Vorhang alles
        v.near = cam.near; v.far = cam.far; v.fovyDeg = cam.fovyDeg;
        v.width = w; v.height = h;
        v.depthTexture = depthCopyTex;
        v.shutter = 1 / 30.0;
        com.dan.rain.gl.RainGl.Light rl = new com.dan.rain.gl.RainGl.Light();
        double[] hc = {0, 1.55, 0};
        if (!figures.isEmpty() && figures.get(0).jointWorld != null) {
            Figure f = figures.get(0);
            double[] hp = com.dan.avatar.Skeleton.position(f.jointWorld, f.skeleton.joint("head"));
            hc = new double[]{hp[0] + f.x, hp[1] + f.baseY(), hp[2] + f.z};
        }
        AvatarGl.Light L = light;
        rl.moonDir = dirTo(L.rimPos, hc);
        rl.moonColor = scaled(L.g(L.rimCol), 1 / dist2(L.rimPos, hc) * 0.6);
        rl.sunDir = dirTo(L.keyPos, hc);
        rl.sunColor = scaled(L.g(L.keyCol), 1 / dist2(L.keyPos, hc) * 0.2);
        rl.skyTop = scaled(L.g(L.fillCol), 0.6);
        rl.skyHorizon = scaled(L.g(L.fillCol), 0.8);
        rl.fogDistance = 400;
        rainGl.draw(gl, v, rl, 1.0);
    }

    private double lastDt = 1 / 60.0;
    private int progFar;
    private static final String FAR_VS = "#version 430 core\nuniform float uZ;\nvoid main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2); gl_Position = vec4(p * 2.0 - 1.0, uZ, 1.0); }\n";
    private static final String FAR_FS = "#version 430 core\nvoid main() {}\n";

    /** Tiefenwert (0…1) einer Entfernung d vor der Kamera (Perspektive der Studio-Kamera). */
    private double backDepth(double d) {
        double n = cam.near, f = cam.far;
        double zn = (f + n) / (f - n) - 2 * f * n / ((f - n) * d);
        return Math.min(0.99999, zn * 0.5 + 0.5);
    }

    private static int programSrc(GL4 gl, String vs, String fs) {
        int v = gl.glCreateShader(GL2ES2.GL_VERTEX_SHADER), f = gl.glCreateShader(GL2ES2.GL_FRAGMENT_SHADER);
        gl.glShaderSource(v, 1, new String[]{vs}, null, 0); gl.glCompileShader(v);
        gl.glShaderSource(f, 1, new String[]{fs}, null, 0); gl.glCompileShader(f);
        int p = gl.glCreateProgram();
        gl.glAttachShader(p, v); gl.glAttachShader(p, f); gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL2ES2.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(v); gl.glDeleteShader(f);
        return ok[0] == 0 ? 0 : p;
    }

    private static double[] dirTo(float[] p, double[] c) {
        double[] d = {p[0] - c[0], p[1] - c[1], p[2] - c[2]};
        double l = Math.sqrt(d[0] * d[0] + d[1] * d[1] + d[2] * d[2]);
        return new double[]{d[0] / l, d[1] / l, d[2] / l};
    }

    private static double dist2(float[] p, double[] c) {
        return Math.max(0.05, (p[0] - c[0]) * (p[0] - c[0]) + (p[1] - c[1]) * (p[1] - c[1]) + (p[2] - c[2]) * (p[2] - c[2]));
    }

    private static double[] scaled(float[] c, double k) { return new double[]{c[0] * k, c[1] * k, c[2] * k}; }

    /** Windrichtung in Grad für com.dan.rain, so dass seine Trift mit dem Wind (Figur) übereinstimmt. */
    private double windFrom(double[] wind) {
        double best = 0, bestErr = 9;
        double wl = Math.hypot(wind[0], wind[2]);
        com.dan.rain.Rain probe = new com.dan.rain.Rain();
        probe.windSpeed = 1;
        for (int d = 0; d < 360; d += 2) {
            probe.windFromDeg = d;
            double[] t = probe.windTo();
            double tl = Math.hypot(t[0], t.length > 2 ? t[t.length - 1] : t[1]);
            if (tl < 1e-9) continue;
            double tx = t[0] / tl, tz = (t.length > 2 ? t[t.length - 1] : t[1]) / tl;
            double err = Math.hypot(tx - wind[0] / wl, tz - wind[2] / wl);
            if (err < bestErr) { bestErr = err; best = d; }
        }
        return best;
    }

    /**
     * Pupille nach dem Licht am Auge (Moon und Spencer 1944): D = 4,9 − 3 · tanh(0,4 · log10 L) mm, L die
     * Leuchtdichte (hier aus der Beleuchtungsstärke der Lichter am Auge, 1 Einheit ≈ 25 cd/m²).
     * Enger wird sie schnell (0,25 s), weit langsam (1,5 s).
     */
    private void pupils(double dt) {
        for (Figure f : shown()) {
            com.dan.avatar.EyeRig.Frame[] e = f.eyes;
            if (e == null || f.gl == null) continue;
            double[] p = {e[0].c[0] + f.x, e[0].c[1] + f.baseY(), e[0].c[2] + f.z};
            AvatarGl.Light l = light;
            double ill = lum(l.fillCol) * 3;
            double[] lk = {l.keyPos[0] - p[0], l.keyPos[1] - p[1], l.keyPos[2] - p[2]};
            double dk2 = lk[0] * lk[0] + lk[1] * lk[1] + lk[2] * lk[2], dk = Math.sqrt(dk2);
            float[] kd = l.keyDir;
            double kl = Math.sqrt(kd[0] * kd[0] + kd[1] * kd[1] + kd[2] * kd[2]);
            double cosA = -(lk[0] * kd[0] + lk[1] * kd[1] + lk[2] * kd[2]) / (dk * kl);
            double ci = Math.cos(Math.toRadians(l.keyConeInner)), co = Math.cos(Math.toRadians(l.keyConeOuter));
            double tt = Math.max(0, Math.min(1, (cosA - co) / (ci - co)));
            double cone = tt * tt * (3 - 2 * tt);
            // das Führungslicht zählt nur, wenn es von vorn kommt (Blick +z der Person)
            double front = Math.max(0.15, lk[2] / dk);
            ill += lum(l.keyCol) * cone / dk2 * front;
            double[] lr = {l.rimPos[0] - p[0], l.rimPos[1] - p[1], l.rimPos[2] - p[2]};
            double dr2 = lr[0] * lr[0] + lr[1] * lr[1] + lr[2] * lr[2];
            ill += lum(l.rimCol) / dr2 * Math.max(0.15, lr[2] / Math.sqrt(dr2));
            double L = Math.max(1e-4, ill * 25 * l.gain);
            double target = com.dan.avatar.EyeRig.pupilMm(L);
            f.pupilTargetMm = target;
            if (f.pupilInstant || dt <= 0 || dt > 1) f.pupilMm = target;
            else {
                double tau = target < f.pupilMm ? 0.25 : 1.5;
                f.pupilMm += (target - f.pupilMm) * (1 - Math.exp(-dt / tau));
            }
            // Iris 11,8 mm Durchmesser
            f.gl.setPupil(f.pupilMm / 11.8);
        }
    }

    /** Haare bewegen: Kopf aus den Gelenken, Wind der Szene. */
    private void hairs(double dt, double t) {
        for (Figure f : shown()) {
            double[][] jw = f.jointWorld;
            if (f.hair == null || jw == null) continue;
            double[] hp = com.dan.avatar.Skeleton.position(jw, f.skeleton.joint("head"));
            double[] head = {hp[0] + f.x, hp[1] + f.baseY(), hp[2] + f.z};
            f.hair.step(dt, head, wind, t);
        }
    }

    static String lookName(com.dan.avatar.Behavior.Look l) {
        return switch (l) { case CAMERA -> "Kamera"; case POINT -> "Punkt"; case FREE -> "frei"; case HORIZON -> "Horizont"; };
    }

    private static double lum(float[] c) { return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]; }

    /** Kugel um alle Personen (Mitte nach {@code c}), aus ihren Gelenken. */
    private double figureBounds(double[] c) {
        double[] lo = {1e9, 1e9, 1e9}, hi = {-1e9, -1e9, -1e9};
        for (Figure f : shown()) {
            double[][] jw = f.jointWorld;
            if (jw == null) continue;
            for (double[] m : jw) {
                double[] p = {m[12] + f.x, m[13] + f.baseY(), m[14] + f.z};
                for (int k = 0; k < 3; k++) { lo[k] = Math.min(lo[k], p[k]); hi[k] = Math.max(hi[k], p[k]); }
            }
        }
        if (lo[0] > hi[0]) { c[0] = 0; c[1] = 0.9; c[2] = 0; return 1.2; }
        double r = 0;
        for (int k = 0; k < 3; k++) c[k] = (lo[k] + hi[k]) / 2;
        for (int k = 0; k < 3; k++) r += (hi[k] - lo[k]) * (hi[k] - lo[k]) / 4;
        return Math.sqrt(r) + 0.25;
    }

    private void readStamps(GL4 gl, int base) {
        if (frame < 2) return;
        int[] av = new int[1];
        gl.glGetQueryObjectiv(stamps[base + 6], GL2ES3.GL_QUERY_RESULT_AVAILABLE, av, 0);
        if (av[0] == 0) return;
        long[] t = new long[7];
        for (int i = 0; i < 7; i++) {
            long[] v = new long[1];
            gl.glGetQueryObjecti64v(stamps[base + i], GL2ES3.GL_QUERY_RESULT, v, 0);
            t[i] = v[0];
        }
        double k = 0.9;
        msShadow = msShadow * k + (1 - k) * (t[1] - t[0]) / 1e6;
        msBack = msBack * k + (1 - k) * (t[2] - t[1]) / 1e6;
        msScene = msScene * k + (1 - k) * (t[3] - t[2]) / 1e6;
        msHair = msHair * k + (1 - k) * (t[4] - t[3]) / 1e6;
        msScatter = msScatter * k + (1 - k) * (t[5] - t[4]) / 1e6;
        msPost = msPost * k + (1 - k) * (t[6] - t[5]) / 1e6;
    }

    public volatile double lastMixMs;

    // ------------------------------------------------------------ HUD

    private void drawHud(GL4 gl) {
        hud.fps = fpsSmooth;
        hud.frameMs = msSmooth;
        hud.gpuMs = gpuMs;
        hud.gl = glInfo;
        hud.quality = quality;
        hud.size = w + " × " + h;
        hud.error = error;
        if (!shown().isEmpty()) {
            Figure f = shown().get(0);
            hud.person = figures.size() == 1 ? f.person.toString() : figures.size() + " Personen";
            hud.mix = String.format(java.util.Locale.GERMANY, "%.2f ms Compute · Pose %s · %s", lastMixMs,
                    f.pose == null ? "Ruhe" : f.pose, f.dualQuaternion ? "Dual-Quaternion" : "linear");
            com.dan.avatar.Behavior b = f.behavior;
            if (b != null && f.behaviorOn) {
                double tb = b.time(), n = 0;
                for (int i = b.blinks.size() - 1; i >= 0 && b.blinks.get(i) > tb - 60; i--) n++;
                double win = Math.min(60, Math.max(1, tb));
                hud.life = String.format(java.util.Locale.GERMANY, "%s %.2f · Blick %s · %.0f Lidschläge/min · Atem %.0f/min · %.2f ms",
                        b.feelingName(), b.strength(), lookName(b.look()), n * 60 / win, b.breathRate(), msBehavior);
            } else hud.life = b == null ? "" : "aus";
            com.dan.avatar.Wetness wt = f.wetness;
            hud.env = String.format(java.util.Locale.GERMANY, "%s · Gesicht %.0f %% · Haar %.0f %% · Schultern %.0f %% · %d Perlen%s · %.2f ms",
                    climate, wt.wet(com.dan.avatar.Wetness.FACE) * 100, wt.wet(com.dan.avatar.Wetness.HAIR) * 100, wt.wet(com.dan.avatar.Wetness.SHOULDERS) * 100,
                    f.drops == null ? 0 : f.drops.beads().size(), envSpeed != 1 ? String.format(java.util.Locale.GERMANY, " · Zeitraffer %.0f×", envSpeed) : "", msEnv);
            StringBuilder cl = new StringBuilder(f.outfit.name);
            if (!f.outfit.pieces.isEmpty()) cl.append(" · ").append(f.outfit.pieces.size()).append(" Teile");
            if (f.outfit.hoodUp()) cl.append(" · Kapuze auf");
            cl.append(String.format(java.util.Locale.GERMANY, " · Lose %.1f cm", f.clothSway.amount() * 100));
            hud.cloth = cl.toString();
            com.dan.avatar.Motion mo = f.motion;
            hud.motion = mo == null ? "" : mo + (figures.size() > 1 ? " · " + figures.size() + " Personen" : "");
            if (crowd != null) hud.motion = String.format(java.util.Locale.GERMANY, "Gruppe %d · L0 %d · L1 %d · L2 %d · L3 %d · Menge %.2f ms (Prozessor)",
                    crowd.members.size(), lodCount[0], lodCount[1], lodCount[2], lodCount[3], msCrowd);
            hud.image = !portraitOn ? "aus" : String.format(java.util.Locale.GERMANY, "%s · %s · %s %.0f %% · Lichter %s",
                    lens, focusMode == 0 ? "näheres Auge" : focusMode == 1 ? "linkes Auge" : focusMode == 2 ? "rechtes Auge" : "von Hand", grade, gradeMix * 100, bokehStyle);
            hud.skin = String.format(java.util.Locale.GERMANY, "Schatten %.2f · Personen %.2f · Haare %.2f · Streuung %.2f ms (%s)",
                    msShadow, msScene, msHair, msScatter, quality == Quality.LOW ? "vorab integriert" : scatter.samples + " Proben");
        }
        java.awt.image.BufferedImage img = hud.render();
        int iw = img.getWidth(), ih = img.getHeight();
        int[] px = ((java.awt.image.DataBufferInt) img.getRaster().getDataBuffer()).getData();
        // ARGB (vormultipliziert) -> RGBA-Bytes
        ByteBuffer bb = Buffers.newDirectByteBuffer(iw * ih * 4);
        for (int p : px) {
            bb.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
        }
        bb.flip();
        gl.glActiveTexture(GL.GL_TEXTURE0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, hudTex);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 1);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA8, iw, ih, 0, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        gl.glEnable(GL.GL_BLEND);
        gl.glBlendFunc(GL.GL_ONE, GL.GL_ONE_MINUS_SRC_ALPHA);
        gl.glUseProgram(progHud);
        gl.glUniform1i(loc(gl, progHud, "uTex"), 0);
        gl.glUniform2f(loc(gl, progHud, "uSize"), w, h);
        gl.glUniform4f(loc(gl, progHud, "uRect"), 14, 14, iw, ih);
        gl.glBindVertexArray(vaoEmpty);
        gl.glDrawArrays(GL.GL_TRIANGLES, 0, 6);
        gl.glDisable(GL.GL_BLEND);
    }

    // ------------------------------------------------------------ Bühne

    private void buildStage(GL4 gl) {
        List<float[]> v = new ArrayList<>();
        // Boden: Scheibe in Ringen (feiner in der Mitte, damit der Spot rund wird)
        float[] slate = {0.055f, 0.060f, 0.066f, 0f};
        int seg = 128, rings = 24;
        for (int r = 0; r < rings; r++) {
            float r0 = STAGE_R * (float) Math.pow((double) r / rings, 1.6);
            float r1 = STAGE_R * (float) Math.pow((double) (r + 1) / rings, 1.6);
            for (int s = 0; s < seg; s++) {
                double a0 = 2 * Math.PI * s / seg, a1 = 2 * Math.PI * (s + 1) / seg;
                float[] p00 = polar(r0, a0, 0), p01 = polar(r0, a1, 0), p10 = polar(r1, a0, 0), p11 = polar(r1, a1, 0);
                tri(v, p00, p11, p10, 0, 1, 0, slate);
                if (r > 0) tri(v, p00, p01, p11, 0, 1, 0, slate);
            }
        }
        // Standring (leuchtet schwach türkis), 2 mm über dem Boden
        float[] teal = {0.06f, 0.30f, 0.32f, 1f};
        for (int s = 0; s < seg; s++) {
            double a0 = 2 * Math.PI * s / seg, a1 = 2 * Math.PI * (s + 1) / seg;
            float[] i0 = polar(0.44f, a0, 0.002f), i1 = polar(0.44f, a1, 0.002f);
            float[] o0 = polar(0.455f, a0, 0.002f), o1 = polar(0.455f, a1, 0.002f);
            tri(v, i0, o1, o0, 0, 1, 0, teal);
            tri(v, i0, i1, o1, 0, 1, 0, teal);
        }
        // Maßstab: schlanker Stab mit Marken alle 25 cm, rechts hinter dem Ring
        float[] steel = {0.55f, 0.56f, 0.58f, 0f};
        float[] mark = {0.85f, 0.55f, 0.22f, 0.35f};
        float x = 1.25f, z = -1.05f, hw = 0.006f;
        box(v, x - hw, 0, z - hw, x + hw, RULER_H, z + hw, steel);
        for (int i = 1; i <= 7; i++) {
            float y = i * 0.25f;
            float mw = i % 2 == 0 ? 0.03f : 0.018f;
            box(v, x - hw - mw, y - 0.003f, z - hw - 0.001f, x + hw + 0.001f, y + 0.003f, z + hw + 0.001f, mark);
        }
        box(v, x - 0.05f, RULER_H - 0.003f, z - 0.012f, x + 0.05f, RULER_H + 0.003f, z + 0.012f, mark);

        stageCount = v.size();
        FloatBuffer fb = Buffers.newDirectFloatBuffer(stageCount * 10);
        for (float[] e : v) fb.put(e);
        fb.flip();
        int[] a = new int[1];
        gl.glGenVertexArrays(1, a, 0); vaoStage = a[0];
        gl.glGenBuffers(1, a, 0); vboStage = a[0];
        gl.glBindVertexArray(vaoStage);
        gl.glBindBuffer(GL.GL_ARRAY_BUFFER, vboStage);
        gl.glBufferData(GL.GL_ARRAY_BUFFER, (long) fb.limit() * 4, fb, GL.GL_STATIC_DRAW);
        gl.glEnableVertexAttribArray(0);
        gl.glVertexAttribPointer(0, 3, GL.GL_FLOAT, false, 40, 0);
        gl.glEnableVertexAttribArray(1);
        gl.glVertexAttribPointer(1, 3, GL.GL_FLOAT, false, 40, 12);
        gl.glEnableVertexAttribArray(2);
        gl.glVertexAttribPointer(2, 4, GL.GL_FLOAT, false, 40, 24);
        gl.glBindVertexArray(0);
    }

    private static float[] polar(float r, double a, float y) {
        return new float[]{(float) (r * Math.sin(a)), y, (float) (r * Math.cos(a))};
    }

    private static void tri(List<float[]> v, float[] a, float[] b, float[] c, float nx, float ny, float nz, float[] col) {
        for (float[] p : new float[][]{a, b, c})
            v.add(new float[]{p[0], p[1], p[2], nx, ny, nz, col[0], col[1], col[2], col[3]});
    }

    private static void box(List<float[]> v, float x0, float y0, float z0, float x1, float y1, float z1, float[] col) {
        float[][] c = {{x0, y0, z0}, {x1, y0, z0}, {x1, y1, z0}, {x0, y1, z0}, {x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
        int[][] f = {{4, 5, 6, 7}, {1, 0, 3, 2}, {5, 1, 2, 6}, {0, 4, 7, 3}, {7, 6, 2, 3}, {0, 1, 5, 4}};
        float[][] n = {{0, 0, 1}, {0, 0, -1}, {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}};
        for (int i = 0; i < 6; i++) {
            float[] a = c[f[i][0]], b = c[f[i][1]], cc = c[f[i][2]], d = c[f[i][3]];
            tri(v, a, b, cc, n[i][0], n[i][1], n[i][2], col);
            tri(v, a, cc, d, n[i][0], n[i][1], n[i][2], col);
        }
    }

    // ------------------------------------------------------------ Ziele

    private void targets(GL4 gl) {
        freeTargets(gl);
        int[] a = new int[1];
        colorTex = colorTarget(gl);
        diffTex = colorTarget(gl);
        gl.glGenTextures(1, a, 0); depthTex = a[0];
        gl.glBindTexture(GL.GL_TEXTURE_2D, depthTex);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_DEPTH_COMPONENT24, w, h, 0, com.jogamp.opengl.GL2ES2.GL_DEPTH_COMPONENT, GL.GL_UNSIGNED_INT, null);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_NEAREST);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glGenFramebuffers(1, a, 0); fbo = a[0];
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, fbo);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_COLOR_ATTACHMENT0, GL.GL_TEXTURE_2D, colorTex, 0);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL2ES3.GL_COLOR_ATTACHMENT1, GL.GL_TEXTURE_2D, diffTex, 0);
        gl.glFramebufferTexture2D(GL.GL_FRAMEBUFFER, GL.GL_DEPTH_ATTACHMENT, GL.GL_TEXTURE_2D, depthTex, 0);
        int st = gl.glCheckFramebufferStatus(GL.GL_FRAMEBUFFER);
        if (st != GL.GL_FRAMEBUFFER_COMPLETE) error = "Bildziel unvollständig: 0x" + Integer.toHexString(st);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
    }

    private int colorTarget(GL4 gl) {
        int[] a = new int[1];
        gl.glGenTextures(1, a, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, a[0]);
        gl.glTexImage2D(GL.GL_TEXTURE_2D, 0, GL.GL_RGBA16F, w, h, 0, GL.GL_RGBA, GL.GL_FLOAT, null);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        return a[0];
    }

    private void freeTargets(GL4 gl) {
        if (fbo != 0) gl.glDeleteFramebuffers(1, new int[]{fbo}, 0);
        for (int t : new int[]{colorTex, diffTex, depthTex}) if (t != 0) gl.glDeleteTextures(1, new int[]{t}, 0);
        fbo = colorTex = diffTex = depthTex = 0;
    }

    // ------------------------------------------------------------ Shader

    static String read(String name) throws IOException {
        try (InputStream in = StudioScene.class.getResourceAsStream("shader/" + name)) {
            if (in == null) throw new IOException("Shader fehlt: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    static int program(GL4 gl, String vs, String fs) throws IOException {
        int v = shader(gl, GL2ES3.GL_VERTEX_SHADER, vs);
        int f = shader(gl, GL2ES3.GL_FRAGMENT_SHADER, fs);
        int p = gl.glCreateProgram();
        gl.glAttachShader(p, v);
        gl.glAttachShader(p, f);
        gl.glLinkProgram(p);
        int[] ok = new int[1];
        gl.glGetProgramiv(p, GL2ES3.GL_LINK_STATUS, ok, 0);
        gl.glDeleteShader(v);
        gl.glDeleteShader(f);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetProgramiv(p, GL2ES3.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetProgramInfoLog(p, b.length, null, 0, b, 0);
            throw new IOException(vs + "+" + fs + ": " + new String(b, StandardCharsets.UTF_8).trim());
        }
        return p;
    }

    private static int shader(GL4 gl, int type, String name) throws IOException {
        int s = gl.glCreateShader(type);
        gl.glShaderSource(s, 1, new String[]{read(name)}, null);
        gl.glCompileShader(s);
        int[] ok = new int[1];
        gl.glGetShaderiv(s, GL2ES3.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            int[] len = new int[1];
            gl.glGetShaderiv(s, GL2ES3.GL_INFO_LOG_LENGTH, len, 0);
            byte[] b = new byte[Math.max(1, len[0])];
            gl.glGetShaderInfoLog(s, b.length, null, 0, b, 0);
            throw new IOException(name + ": " + new String(b, StandardCharsets.UTF_8).trim());
        }
        return s;
    }

    private static int loc(GL4 gl, int p, String n) {
        return gl.glGetUniformLocation(p, n);
    }

    /** Liest das fertige Bild aus dem Fenster (unten links beginnend) als ARGB. */
    public static java.awt.image.BufferedImage grab(GL4 gl, int w, int h) {
        ByteBuffer bb = Buffers.newDirectByteBuffer(w * h * 4);
        gl.glBindFramebuffer(GL.GL_FRAMEBUFFER, 0);
        gl.glPixelStorei(GL.GL_PACK_ALIGNMENT, 1);
        gl.glReadPixels(0, 0, w, h, GL.GL_RGBA, GL.GL_UNSIGNED_BYTE, bb);
        java.awt.image.BufferedImage img = new java.awt.image.BufferedImage(w, h, java.awt.image.BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++)
            for (int x = 0; x < w; x++) {
                int i = ((h - 1 - y) * w + x) * 4;
                int r = bb.get(i) & 255, g = bb.get(i + 1) & 255, b = bb.get(i + 2) & 255;
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        return img;
    }
}
