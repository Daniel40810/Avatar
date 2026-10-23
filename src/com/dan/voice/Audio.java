package com.dan.voice;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Eine Tonspur (WAV, PCM 8/16/24 Bit, mono oder stereo) und was man ihr ansieht (Phase 16): je 10 ms Lautstärke,
 * Nulldurchgänge, Anteil hoher Frequenzen, Stimmhaftigkeit und die ersten beiden Formanten (LPC). Daraus
 * <ul>
 * <li>ohne Text: je 10 ms ein Mundbild (Stille, Reibelaut, Vokal nach den Formanten, Nasal), geglättet;</li>
 * <li>mit Text: die Laute aus {@link G2P} werden an den Ton angelegt (dynamische Programmierung über die Lautklassen,
 * jeder Laut mindestens 20 ms), die Zeiten kommen aus dem Ton, die Mundbilder aus dem Text.</li>
 * </ul>
 * Vokale nach Formanten [G: Mittelwerte deutscher Vokale, Sprecherin und Sprecher über den Median von F2 angeglichen]:
 * a 820/1350, e 420/2050, i 290/2250, o 430/850, u 310/800, ü 280/1650 Hz.
 */
public final class Audio {

    public final float[] samples;
    public final int rate;
    /** Merkmale je 10 ms. */
    public final Frame[] frames;
    /** Name der Datei (oder leer). */
    public final String name;

    /** Merkmale eines Ausschnitts (25 ms, alle 10 ms): Pegel (dB unter dem lautesten), Nulldurchgänge je Probe, Anteil über 3 kHz, stimmhaft, F1, F2 (Hz). */
    public record Frame(double db, double zcr, double high, boolean voiced, double f1, double f2) { }

    public Audio(float[] samples, int rate, String name) {
        this.samples = samples;
        this.rate = rate;
        this.name = name;
        this.frames = analyse(samples, rate);
    }

    public double seconds() { return samples.length / (double) rate; }

    // ------------------------------------------------------------ WAV

    /** WAV-Datei lesen (PCM; mehrere Kanäle werden gemittelt). */
    public static Audio read(File f) throws IOException { return read(Files.readAllBytes(f.toPath()), f.getName()); }

    public static Audio read(InputStream in, String name) throws IOException { return read(in.readAllBytes(), name); }

    public static Audio read(byte[] b, String name) throws IOException {
        ByteBuffer bb = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        if (b.length < 44 || bb.getInt(0) != 0x46464952 || bb.getInt(8) != 0x45564157) {
            // häufig: eine Aufnahme mit der Endung .wav, die in Wahrheit gepackt ist
            String kind = b.length > 12 && b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p' ? "M4A/AAC (z. B. aus der Windows-Sprachaufzeichnung)"
                    : b.length > 3 && (b[0] == 'I' && b[1] == 'D' && b[2] == '3' || (b[0] & 0xFF) == 0xFF && (b[1] & 0xE0) == 0xE0) ? "MP3"
                    : b.length > 4 && b[0] == 'O' && b[1] == 'g' && b[2] == 'g' ? "Ogg" : null;
            throw new IOException(kind == null ? "keine WAV-Datei: " + name
                    : name + " ist " + kind + ", keine WAV; bitte als WAV (PCM) speichern, z. B. ffmpeg -i ein.m4a -ac 1 aus.wav");
        }
        int pos = 12, ch = 1, sr = 16000, bits = 16, fmt = 1;
        int dataOff = -1, dataLen = 0;
        while (pos + 8 <= b.length) {
            int id = bb.getInt(pos), len = bb.getInt(pos + 4);
            if (id == 0x20746d66) { fmt = bb.getShort(pos + 8) & 0xFFFF; ch = bb.getShort(pos + 10); sr = bb.getInt(pos + 12); bits = bb.getShort(pos + 22); }
            else if (id == 0x61746164) { dataOff = pos + 8; dataLen = Math.min(len, b.length - dataOff); break; }
            pos += 8 + len + (len & 1);
        }
        if (dataOff < 0) throw new IOException("WAV ohne Daten: " + name);
        if (fmt != 1 && fmt != 0xFFFE) throw new IOException("nur PCM-WAV (Format " + fmt + "): " + name);
        int bps = bits / 8, n = dataLen / (bps * ch);
        float[] s = new float[n];
        for (int i = 0; i < n; i++) {
            double v = 0;
            for (int c = 0; c < ch; c++) {
                int o = dataOff + (i * ch + c) * bps;
                v += switch (bits) {
                    case 8 -> ((b[o] & 0xFF) - 128) / 128.0;
                    case 16 -> bb.getShort(o) / 32768.0;
                    case 24 -> ((b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | (b[o + 2] << 16)) / 8388608.0;
                    default -> throw new IOException(bits + " Bit nicht unterstützt: " + name);
                };
            }
            s[i] = (float) (v / ch);
        }
        return new Audio(s, sr, name);
    }

    /** Als WAV (16 Bit mono) schreiben. */
    public byte[] wav() {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        int n = samples.length;
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.putInt(0x46464952).putInt(36 + n * 2).putInt(0x45564157).putInt(0x20746d66).putInt(16).putShort((short) 1).putShort((short) 1)
                .putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16).putInt(0x61746164).putInt(n * 2);
        o.writeBytes(h.array());
        ByteBuffer d = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (float v : samples) d.putShort((short) Math.round(Math.max(-1, Math.min(1, v)) * 32767));
        o.writeBytes(d.array());
        return o.toByteArray();
    }

    /** Proben als 16 Bit PCM (für Video und Wiedergabe). */
    public short[] pcm16() {
        short[] s = new short[samples.length];
        for (int i = 0; i < s.length; i++) s[i] = (short) Math.round(Math.max(-1, Math.min(1, samples[i])) * 32767);
        return s;
    }

    // ------------------------------------------------------------ Merkmale

    static Frame[] analyse(float[] x, int sr) {
        int hop = sr / 100, win = (int) (sr * 0.025);
        int n = Math.max(0, (x.length - win) / hop + 1);
        double[] rms = new double[n];
        double max = 1e-9;
        for (int f = 0; f < n; f++) {
            double s = 0;
            for (int i = 0; i < win; i++) { double v = x[f * hop + i]; s += v * v; }
            rms[f] = Math.sqrt(s / win);
            max = Math.max(max, rms[f]);
        }
        // Formanten bei 11 025 Hz (Mittel über ganze Teiler), LPC 12
        int dec = Math.max(1, (int) Math.round(sr / 11025.0));
        double fs = sr / (double) dec;
        Frame[] out = new Frame[n];
        double[] seg = new double[win], low = new double[win / dec];
        for (int f = 0; f < n; f++) {
            int o = f * hop;
            double zc = 0, hi = 0, all = 0, prev = x[o];
            for (int i = 0; i < win; i++) {
                double v = x[o + i];
                seg[i] = v;
                if ((v >= 0) != (prev >= 0)) zc++;
                if (i > 0) { double d = v - prev; hi += d * d; }
                all += v * v;
                prev = v;
            }
            double db = 20 * Math.log10(Math.max(1e-9, rms[f] / max));
            double zcr = zc / win, high = all < 1e-12 ? 0 : hi / (4 * all);   // Anteil der Energie der Differenz: hohe Frequenzen
            boolean voiced = db > -38 && periodic(seg, sr) > 0.38 && zcr < 0.25;
            double f1 = 0, f2 = 0;
            if (voiced) {
                for (int i = 0; i < low.length; i++) { double s = 0; for (int k = 0; k < dec; k++) s += seg[i * dec + k]; low[i] = s / dec; }
                double[] fm = formants(low, fs);
                f1 = fm[0]; f2 = fm[1];
            }
            out[f] = new Frame(db, zcr, high, voiced, f1, f2);
        }
        return out;
    }

    /** Höchste normierte Autokorrelation zwischen 2,5 und 12,5 ms (80–400 Hz Grundton). */
    static double periodic(double[] s, int sr) {
        int lo = (int) (sr * 0.0025), hi = Math.min(s.length - 2, (int) (sr * 0.0125));
        double e0 = 0;
        for (double v : s) e0 += v * v;
        if (e0 < 1e-12) return 0;
        double best = 0;
        for (int lag = lo; lag <= hi; lag++) {
            double c = 0, e1 = 0, e2 = 0;
            for (int i = 0; i + lag < s.length; i++) { c += s[i] * s[i + lag]; e1 += s[i] * s[i]; e2 += s[i + lag] * s[i + lag]; }
            double r = c / Math.sqrt(Math.max(1e-12, e1 * e2));
            if (r > best) best = r;
        }
        return best;
    }

    /** F1 und F2 (Hz) aus LPC 12 (Autokorrelation, Levinson-Durbin, Nullstellen nach Durand-Kerner). */
    static double[] formants(double[] s, double fs) {
        int p = 12, n = s.length;
        double[] w = new double[n];
        for (int i = 0; i < n; i++) {
            double v = s[i] - (i > 0 ? 0.94 * s[i - 1] : 0);                  // Vorbetonung
            w[i] = v * (0.54 - 0.46 * Math.cos(2 * Math.PI * i / (n - 1)));   // Hamming
        }
        double[] r = new double[p + 1];
        for (int k = 0; k <= p; k++) for (int i = 0; i + k < n; i++) r[k] += w[i] * w[i + k];
        if (r[0] < 1e-12) return new double[]{0, 0};
        r[0] *= 1.0001;
        double[] a = new double[p + 1], tmp = new double[p + 1];
        a[0] = 1;
        double e = r[0];
        for (int i = 1; i <= p; i++) {
            double acc = r[i];
            for (int j = 1; j < i; j++) acc += a[j] * r[i - j];
            double k = -acc / e;
            System.arraycopy(a, 0, tmp, 0, p + 1);
            for (int j = 1; j < i; j++) a[j] = tmp[j] + k * tmp[i - j];
            a[i] = k;
            e *= 1 - k * k;
            if (e <= 0) break;
        }
        // Nullstellen von z^p + a1 z^(p-1) + … + ap
        double[] re = new double[p], im = new double[p];
        for (int i = 0; i < p; i++) { double ang = 2 * Math.PI * i / p + 0.4; re[i] = 0.9 * Math.cos(ang); im[i] = 0.9 * Math.sin(ang); }
        for (int it = 0; it < 120; it++) {
            double moved = 0;
            for (int i = 0; i < p; i++) {
                // Wert des Polynoms
                double pr = 1, pi = 0;
                for (int k = 1; k <= p; k++) { double nr = pr * re[i] - pi * im[i] + a[k], ni = pr * im[i] + pi * re[i]; pr = nr; pi = ni; }
                double dr = 1, di = 0;
                for (int j = 0; j < p; j++) {
                    if (j == i) continue;
                    double xr = re[i] - re[j], xi = im[i] - im[j];
                    double nr = dr * xr - di * xi, ni = dr * xi + di * xr; dr = nr; di = ni;
                }
                double den = dr * dr + di * di;
                if (den < 1e-30) continue;
                double qr = (pr * dr + pi * di) / den, qi = (pi * dr - pr * di) / den;
                re[i] -= qr; im[i] -= qi;
                moved = Math.max(moved, Math.abs(qr) + Math.abs(qi));
            }
            if (moved < 1e-9) break;
        }
        List<Double> fq = new ArrayList<>();
        for (int i = 0; i < p; i++) {
            if (im[i] <= 0) continue;
            double rad = Math.hypot(re[i], im[i]);
            double f = Math.atan2(im[i], re[i]) * fs / (2 * Math.PI), bw = -Math.log(Math.max(1e-9, rad)) * fs / Math.PI;
            // unter 1200 Hz auch breite Pole: bei o und u liegen F1 und F2 nah beisammen, LPC verschmiert F2 dann
            if (f > 180 && f < fs / 2 - 100 && (bw < 450 || f < 1200 && bw < 1300)) fq.add(f);
        }
        java.util.Collections.sort(fq);
        return new double[]{fq.size() > 0 ? fq.get(0) : 0, fq.size() > 1 ? fq.get(1) : 0};
    }

    // ------------------------------------------------------------ Vokale nach Formanten

    static final Viseme[] VOW = {Viseme.AA, Viseme.E, Viseme.I, Viseme.O, Viseme.U, Viseme.U};
    static final double[][] PROTO = {{820, 1350}, {420, 2050}, {290, 2250}, {430, 850}, {310, 800}, {280, 1650}};

    /** Faktor für die Formanten dieses Sprechers (Median von F2 gegen 1650 Hz, begrenzt auf 0,9–1,15). */
    double scale() {
        List<Double> f2 = new ArrayList<>();
        for (Frame f : frames) if (f.voiced && f.f2 > 0) f2.add(f.f2);
        if (f2.isEmpty()) return 1;
        java.util.Collections.sort(f2);
        return Math.max(0.9, Math.min(1.15, f2.get(f2.size() / 2) / 1650));
    }

    /** Abstand des Ausschnitts zu einem Vokal-Mundbild (in Formanten, 0 = genau). */
    static double vowelDist(Frame f, Viseme v, double k) {
        if (f.f1 <= 0 || f.f2 <= 0) return 1.2;
        double best = 9;
        for (int i = 0; i < VOW.length; i++)
            if (VOW[i] == v) best = Math.min(best, Math.hypot((f.f1 - PROTO[i][0]) / 110, (f.f2 / k - PROTO[i][1]) / 380));
        return Math.min(2.5, best);
    }

    /** Abstand zum Schwa und vokalischen r (Mitte des Vokalraums, 500/1450 Hz). */
    static double schwaDist(Frame f, double k) {
        if (f.f1 <= 0 || f.f2 <= 0) return 1.2;
        return Math.min(2.5, Math.hypot((f.f1 - 470) / 150, (f.f2 / k - 1450) / 450));
    }

    /** Das nächste Vokal-Mundbild nach den Formanten. */
    static Viseme nearestVowel(Frame f, double k) {
        Viseme b = Viseme.E;
        double bd = 1e9;
        for (Viseme v : new Viseme[]{Viseme.AA, Viseme.E, Viseme.I, Viseme.O, Viseme.U}) {
            double d = vowelDist(f, v, k);
            if (d < bd) { bd = d; b = v; }
        }
        return b;
    }

    // ------------------------------------------------------------ Spur

    /** Lautstärke je 10 ms, 0 … 1 (−40 dB … 0 dB). */
    public float[] envelope() {
        float[] e = new float[frames.length];
        for (int i = 0; i < e.length; i++) e[i] = (float) Math.max(0, Math.min(1, (frames[i].db + 40) / 40));
        return e;
    }

    /** Spur aus dem Ton allein (ohne Text): je 10 ms ein Mundbild, über 50 ms geglättet. */
    public Speech speech() {
        double k = scale();
        int n = frames.length;
        Viseme[] v = new Viseme[n];
        double top = -200;
        for (Frame f : frames) top = Math.max(top, f.db);
        for (int i = 0; i < n; i++) {
            Frame f = frames[i];
            double q = top - f.db;
            if (q > 34) v[i] = Viseme.SIL;
            else if (!f.voiced && (f.zcr > 0.18 || f.high > 0.35)) v[i] = f.high > 0.55 ? Viseme.SS : Viseme.FF;
            else if (f.voiced && f.f1 > 0 && f.f1 < 300 && (f.f2 < 1250 || q > 6)) v[i] = Viseme.NN;   // Nasal: tiefer F1
            else if (f.voiced && q < 20) v[i] = nearestVowel(f, k);
            else if (f.voiced) v[i] = Viseme.NN;
            else v[i] = Viseme.DD;
        }
        // Mehrheitsfilter über 5 Ausschnitte
        Viseme[] m = new Viseme[n];
        for (int i = 0; i < n; i++) {
            int[] c = new int[Viseme.values().length];
            for (int j = Math.max(0, i - 2); j <= Math.min(n - 1, i + 2); j++) c[v[j].ordinal()]++;
            int b = v[i].ordinal();
            for (int j = 0; j < c.length; j++) if (c[j] > c[b]) b = j;
            m[i] = Viseme.values()[b];
        }
        List<Phon> ph = new ArrayList<>();
        List<double[]> t = new ArrayList<>();
        int s = 0;
        for (int i = 1; i <= n; i++) {
            if (i < n && m[i] == m[s]) continue;
            Viseme vv = m[s];
            Phon.Kind kind = vv == Viseme.SIL ? Phon.Kind.PAUSE : vv.open >= 0.35 ? Phon.Kind.VOWEL : vv == Viseme.SS || vv == Viseme.FF ? Phon.Kind.FRICATIVE
                    : vv == Viseme.NN ? Phon.Kind.NASAL : Phon.Kind.PLOSIVE;
            ph.add(new Phon(vv.key, kind, vv, (i - s) * 10, kind == Phon.Kind.VOWEL, -1));
            t.add(new double[]{s * 0.01, i * 0.01});
            s = i;
        }
        double[] t0 = new double[t.size()], t1 = new double[t.size()];
        for (int i = 0; i < t.size(); i++) { t0[i] = t.get(i)[0]; t1[i] = t.get(i)[1]; }
        return Speech.timed("(" + name + ")", ph, t0, t1, envelope(), "Ton");
    }

    /**
     * Text an den Ton anlegen: die Laute (mit Pausen an Anfang und Ende) so über die Ausschnitte verteilen, dass die
     * Kosten am kleinsten sind (Segment-DP). Je Laut zählt, wie gut jeder Ausschnitt zur Lautart passt (laut und
     * stimmhaft beim Vokal mit passenden Formanten, rauschend beim Reibelaut, still oder knallend beim Verschluss,
     * stimmhaft mit tiefem F1 beim Nasal) und wie weit die Dauer von der erwarteten abweicht (die Regeldauern auf die
     * gesprochene Zeit gestreckt). Pausen im Satz dürfen ausfallen.
     */
    public Speech speech(String text) {
        List<Phon> raw = G2P.phonemes(text);
        List<Phon> ph = new ArrayList<>();
        ph.add(new Phon("_", Phon.Kind.PAUSE, Viseme.SIL, 100, false, -1));
        // zwischen zwei Wörtern ohne Satzzeichen darf eine Pause liegen (‿), muss aber nicht
        for (int i = 0; i < raw.size(); i++) {
            Phon p = raw.get(i);
            if (i > 0 && !p.pause() && !raw.get(i - 1).pause() && p.word() != raw.get(i - 1).word())
                ph.add(new Phon(OPT, Phon.Kind.PAUSE, Viseme.SIL, 250, false, -1));
            ph.add(p);
        }
        if (!ph.get(ph.size() - 1).pause()) ph.add(new Phon("_", Phon.Kind.PAUSE, Viseme.SIL, 100, false, -1));
        double k = scale();
        int N = ph.size(), M = frames.length;
        // Pegel: lautester Ausschnitt und Grundrauschen (15 % leisester); der Abstand wird auf 45 dB gestreckt, damit
        // eine Aufnahme mit Rauschen (Handy, Zimmer) wie eine saubere bewertet wird
        double top = -200;
        for (Frame f : frames) top = Math.max(top, f.db);
        double[] dbs = new double[M];
        for (int i = 0; i < M; i++) dbs[i] = frames[i].db;
        java.util.Arrays.sort(dbs);
        double floor = M == 0 ? top - 45 : dbs[(int) (0.15 * (M - 1))];
        double sq = 45 / Math.max(20, Math.min(45, top - floor));
        // Kosten je Laut und Ausschnitt, aufsummiert (Präfixsummen)
        // örtlich lautester Ausschnitt (±0,25 s): Vokale sind die Gipfel ihrer Silbe, auch wenn der Satz leiser wird
        double[] loc = new double[M];
        for (int f = 0; f < M; f++) {
            double m = -200;
            for (int g = Math.max(0, f - 25); g <= Math.min(M - 1, f + 25); g++) m = Math.max(m, frames[g].db);
            loc[f] = m;
        }
        // Rauschen der Reibelaute: verlustbehaftet gepackte Aufnahmen (AAC, MP3) schneiden die Höhen ab, dann rauscht
        // ein s kaum; die Nulldurchgänge werden so gestreckt, dass die rauschendsten 3 % bei 0,35 liegen (höchstens doppelt)
        double[] zs = new double[M];
        for (int i = 0; i < M; i++) zs[i] = frames[i].zcr;
        java.util.Arrays.sort(zs);
        double zk = M == 0 ? 1 : Math.max(1, Math.min(2, 0.35 / Math.max(1e-3, zs[(int) (0.97 * (M - 1))])));
        Frame[] fr = new Frame[M];
        for (int i = 0; i < M; i++) { Frame x = frames[i]; fr[i] = zk == 1 ? x : new Frame(x.db, Math.min(1, x.zcr * zk), x.high, x.voiced, x.f1, x.f2); }
        double[][] cs = new double[N][M + 1];
        for (int i = 0; i < N; i++)
            for (int f = 0; f < M; f++) cs[i][f + 1] = cs[i][f] + cost(ph.get(i), fr[f], k, top, sq, loc[f] - frames[f].db);
        // Sprechtempo: Ausschnitte über dem Rauschen gegen die Regelzeit der Laute
        int active = 0;
        for (Frame f : frames) if ((top - f.db) * sq < 30) active++;
        double total = 0;
        for (Phon p : ph) if (!p.pause()) total += p.ms();
        double rate = Math.max(0.6, Math.min(2.5, active * 10.0 / Math.max(1, total)));
        double INF = 1e18;
        double[][] D = new double[N + 1][M + 1];
        int[][] B = new int[N + 1][M + 1];
        for (double[] r : D) java.util.Arrays.fill(r, INF);
        D[0][0] = 0;
        for (int i = 0; i < N; i++) {
            Phon p = ph.get(i);
            boolean edge = i == 0 || i == N - 1, opt = p.sym().equals(OPT);
            double e = Math.max(2, p.ms() * (p.pause() ? 1 : rate) / 10);   // erwartete Ausschnitte
            int dmin = p.pause() ? 0 : Math.max(2, (int) Math.round(0.35 * e));
            int dmax = edge || p.pause() ? M : (int) Math.ceil(3.0 * e) + 3;
            for (int f = 0; f <= M; f++) {
                if (D[i][f] >= INF) continue;
                for (int d = dmin; d <= dmax && f + d <= M; d++) {
                    double dur;
                    if (edge) dur = 0;
                    else if (opt) dur = d == 0 ? 0 : 0.6;
                    else if (p.pause()) dur = d == 0 ? 0.8 : 0.3 * Math.pow(Math.log((d + 1.0) / (e + 1)), 2);
                    else dur = 1.6 * Math.pow(Math.log(d / e), 2);
                    double v = D[i][f] + cs[i][f + d] - cs[i][f] + dur;
                    if (v < D[i + 1][f + d]) { D[i + 1][f + d] = v; B[i + 1][f + d] = f; }
                    if (v > D[i][f] + 400) break;
                }
            }
        }
        double[] t0 = new double[N], t1 = new double[N];
        int f = M;
        for (int i = N; i > 0; i--) {
            int s = B[i][f];
            t0[i - 1] = s * 0.01;
            t1[i - 1] = f * 0.01;
            f = s;
        }
        // mögliche Pausen: kürzer als 0,25 s gehören zum Laut davor (Verschluss), längere werden echte Pausen
        List<Phon> op = new ArrayList<>();
        List<double[]> ot = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            Phon p = ph.get(i);
            if (p.sym().equals(OPT)) {
                if (t1[i] - t0[i] < 0.25) { if (!ot.isEmpty()) ot.get(ot.size() - 1)[1] = t1[i]; continue; }
                p = new Phon("_", Phon.Kind.PAUSE, Viseme.SIL, (t1[i] - t0[i]) * 1000, false, -1);
            }
            op.add(p);
            ot.add(new double[]{t0[i], t1[i]});
        }
        double[] a0 = new double[op.size()], a1 = new double[op.size()];
        for (int i = 0; i < op.size(); i++) { a0[i] = ot.get(i)[0]; a1[i] = ot.get(i)[1]; }
        return Speech.timed(text, op, a0, a1, envelope(), "Text am Ton");
    }

    /** Zeichen der möglichen Pause zwischen Wörtern (nur beim Anlegen). */
    static final String OPT = "‿";

    private static double clamp(double x) { return Math.max(0, Math.min(1, x)); }

    /**
     * Kosten: wie schlecht passt der Ausschnitt zum Laut? {@code top} ist der lauteste Ausschnitt (dB); q = wie viele dB
     * leiser der Ausschnitt ist.
     */
    static double cost(Phon p, Frame f, double k, double top, double sq, double ql) {
        double q = (top - f.db) * sq;
        if (q > 38 && f.voiced) f = new Frame(f.db, f.zcr, f.high, false, 0, 0);   // Rauschen ist nicht stimmhaft
        String s = p.sym();
        return switch (p.kind()) {
            case PAUSE -> 3.0 * clamp((38 - q) / 16);
            case VOWEL -> {
                double c = 2.5 * clamp((Math.min(q, 1.5 * ql) - 10) / 15) + (f.voiced ? 0 : 1.5) + 1.5 * clamp((f.zcr - 0.12) / 0.15);
                if (s.equals("@") || s.equals("6")) c += 0.45 + 0.35 * schwaDist(f, k);
                else if (p.v2() == null) c += 0.9 * Math.min(1.3, vowelDist(f, p.v(), k));   // Formanten nur als Hinweis
                else c += 0.5 * Math.min(vowelDist(f, p.v(), k), vowelDist(f, p.v2(), k));   // Doppellaut gleitet
                yield c;
            }
            case PLOSIVE -> 1.6 * clamp((24 - q) / 14) - (f.zcr > 0.2 ? 0.3 : 0) + 0.2 + 0.5 * clamp((q - 34) / 8);   // lange Stille ist Pause
            case FRICATIVE -> s.equals("v")
                    ? 0.5 + 0.8 * clamp((10 - q) / 8) + (f.voiced ? 0 : 0.3)
                    : 2.0 * clamp((0.24 - f.zcr) / 0.16) + (f.voiced ? 0.6 : 0) + 1.0 * clamp((q - 40) / 10);
            case NASAL, LIQUID -> {
                double c = (f.voiced ? 0 : 1.2) + 1.2 * clamp((q - 26) / 10) + 0.4 + 0.7 * clamp((5 - ql) / 5);   // am Gipfel der Silbe eher Vokal
                if (f.f1 > 0) c += 0.6 * clamp((f.f1 - 320) / 150);
                else c += 0.3;
                if (s.equals("R")) c = Math.min(c, 0.6 + 0.8 * clamp((q - 30) / 10)) + 1.0 * clamp((12 - q) / 6);   // Zäpfchen-r oft gerieben
                yield c;
            }
            case H -> 0.5 + (f.voiced ? 0.4 : 0) + 1.0 * clamp((q - 40) / 10);
        };
    }
}
