package com.dan.beat;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Takt und Stimmung einer Tonspur (Phase 17): je 10 ms Einsätze (spektraler Fluss in 24 Bändern), Lautheit,
 * Helligkeit (Schwerpunkt des Spektrums) und Ruhe; daraus Tempo (Autokorrelation der Einsätze mit einer Vorliebe für
 * 110 Schläge je Minute), die Schläge (dynamische Programmierung nach Ellis 2007: Schläge auf starke Einsätze, Abstand
 * nah am Tempo) und die Eins im Takt (4/4: die Lage mit den meisten tiefen Einsätzen).
 *
 * <p>Für die Szene: {@link #beatAt} zählt die Schläge stetig (zwischen zwei Schlägen gebrochen), {@link #pulse} ist 1
 * auf dem Schlag und klingt in 120 ms ab, {@link #energy}, {@link #brightness} und {@link #calm} liegen zwischen 0 und 1.
 */
public final class Rhythm {

    /** Ausschnitte je Sekunde (ungefähr; genau {@link #fps} = Abtastrate / Schritt). */
    public static final double FPS = 100;
    /** Ausschnitte je Sekunde dieser Spur. */
    public final double fps;
    /** Schläge je Takt. */
    public final int meter = 4;
    public final double seconds;
    /** Tempo (Schläge je Minute). */
    public final double bpm;
    /** Zeiten der Schläge (s). */
    public final double[] beats;
    /** Welcher Schlag (0 … 3) der ersten vier eine Eins ist. */
    public final int downbeat;
    /** Je Ausschnitt: Einsatz (normiert, ≥ 0), Lautheit, Helligkeit, Ruhe (0 … 1). */
    public final float[] onset, energy, brightness, calm;
    /** Tiefe Einsätze (unter 200 Hz, Bassdrum). */
    public final float[] low;

    private Rhythm(double fps, double seconds, double bpm, double[] beats, int downbeat, float[] onset, float[] low, float[] energy, float[] brightness, float[] calm) {
        this.fps = fps; this.seconds = seconds; this.bpm = bpm; this.beats = beats; this.downbeat = downbeat;
        this.onset = onset; this.low = low; this.energy = energy; this.brightness = brightness; this.calm = calm;
    }

    // ------------------------------------------------------------ Analyse

    public static Rhythm analyse(Track tr) { return analyse(tr, 110); }

    /** Analyse mit Vorliebe für ein Tempo (Schläge je Minute). */
    public static Rhythm analyse(Track tr, double preferBpm) {
        int sr = tr.rate, hop = Math.max(1, (int) Math.round(sr / FPS));
        final double FPS = sr / (double) hop;
        int n = 1;
        while (n < 2.3 * hop) n <<= 1;
        float[] x = tr.samples;
        int M = Math.max(1, (x.length - n) / hop + 1);
        int B = 24;
        double fLo = 50, fHi = Math.min(8000, sr / 2.2);
        int[] edge = new int[B + 1];
        for (int b = 0; b <= B; b++) edge[b] = (int) Math.round(fLo * Math.pow(fHi / fLo, b / (double) B) * n / sr);
        for (int b = 1; b <= B; b++) if (edge[b] <= edge[b - 1]) edge[b] = edge[b - 1] + 1;
        int lowBands = 0;
        while (lowBands < B && fLo * Math.pow(fHi / fLo, (lowBands + 1) / (double) B) <= 200) lowBands++;
        double[] win = new double[n];
        for (int i = 0; i < n; i++) win[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / n);
        double[] re = new double[n], im = new double[n];
        double[][] band = new double[M][B];
        double[] rms = new double[M], cent = new double[M];
        double bandMax = 1e-12;
        for (int f = 0; f < M; f++) {
            int o = f * hop;
            double e = 0;
            for (int i = 0; i < n; i++) { double v = o + i < x.length ? x[o + i] : 0; e += v * v; re[i] = v * win[i]; im[i] = 0; }
            rms[f] = Math.sqrt(e / n);
            fft(re, im);
            double num = 0, den = 0;
            for (int k = 1; k < n / 2; k++) {
                double p = re[k] * re[k] + im[k] * im[k];
                num += p * k * sr / (double) n;
                den += p;
            }
            cent[f] = den > 1e-12 ? num / den : 0;
            for (int b = 0; b < B; b++) {
                double s = 0;
                for (int k = edge[b]; k < Math.min(n / 2, edge[b + 1]); k++) s += re[k] * re[k] + im[k] * im[k];
                band[f][b] = s;
                bandMax = Math.max(bandMax, s);
            }
        }
        // Einsätze: Zuwachs der logarithmierten Bandenergie
        double[] od = new double[M], ol = new double[M];
        double[] prev = new double[B];
        for (int f = 0; f < M; f++) {
            double s = 0, sl = 0;
            for (int b = 0; b < B; b++) {
                double l = Math.log(1 + 1000 * band[f][b] / bandMax);
                if (f > 0) { double d = Math.max(0, l - prev[b]); s += d; if (b < lowBands) sl += d; }
                prev[b] = l;
            }
            od[f] = s; ol[f] = sl;
        }
        double[] on = normalize(od, 50), lo = normalize(ol, 50);
        // Tempo: Autokorrelation mit Vorliebe (log-normal um preferBpm, eine Oktave breit)
        int lagMin = (int) Math.floor(60 * FPS / 200), lagMax = (int) Math.ceil(60 * FPS / 50);
        double best = -1, bestLag = 60 * FPS / preferBpm;
        double[] ac = new double[lagMax + 2];
        for (int lag = lagMin; lag <= lagMax + 1 && lag < M; lag++) {
            double c = 0;
            for (int f = lag; f < M; f++) c += on[f] * on[f - lag];
            ac[lag] = c / Math.max(1, M - lag);
        }
        for (int lag = lagMin + 1; lag <= lagMax && lag + 1 < ac.length; lag++) {
            double bpm = 60 * FPS / lag, w = Math.exp(-0.5 * Math.pow(Math.log(bpm / preferBpm) / Math.log(2) / 0.9, 2));
            double v = ac[lag] * w;
            if (v > best && ac[lag] >= ac[lag - 1] && ac[lag] >= ac[lag + 1]) {
                best = v;
                double den = ac[lag - 1] - 2 * ac[lag] + ac[lag + 1];
                bestLag = lag + (Math.abs(den) > 1e-12 ? 0.5 * (ac[lag - 1] - ac[lag + 1]) / den : 0);
            }
        }
        double P = bestLag;
        // Schläge: dynamische Programmierung
        double alpha = 100;
        double[] score = new double[M];
        int[] back = new int[M];
        int pLo = (int) Math.round(P / 2), pHi = (int) Math.round(2 * P);
        for (int t = 0; t < M; t++) {
            double bs = 0;
            int bi = -1;
            for (int tau = t - pHi; tau <= t - pLo; tau++) {
                if (tau < 0) continue;
                double v = score[tau] - alpha * Math.pow(Math.log((t - tau) / P), 2);
                if (bi < 0 || v > bs) { bs = v; bi = tau; }
            }
            score[t] = on[t] + (bi >= 0 ? Math.max(0, bs) : 0);
            back[t] = bs > 0 ? bi : -1;
        }
        int end = Math.max(0, M - (int) Math.round(P));
        int bt = M - 1;
        for (int t = end; t < M; t++) if (score[t] > score[bt]) bt = t;
        List<Integer> bl = new ArrayList<>();
        while (bt >= 0) { bl.add(bt); bt = back[bt]; }
        java.util.Collections.reverse(bl);
        // Lücken am Anfang (Vorspiel ohne Einsätze) im Tempo auffüllen
        List<Double> bts = new ArrayList<>();
        if (!bl.isEmpty()) {
            double first = bl.get(0);
            List<Double> pre = new ArrayList<>();
            for (double t = first - P; t >= 0; t -= P) pre.add(t);
            java.util.Collections.reverse(pre);
            bts.addAll(pre);
        }
        for (int f : bl) bts.add((double) f);
        double[] beats = new double[bts.size()];
        for (int i = 0; i < beats.length; i++) beats[i] = (bts.get(i) + n / 2.0 / hop) / FPS;   // Mitte des Fensters
        // Eins: Lage im Takt mit den meisten tiefen Einsätzen (und Lautheitssprüngen)
        double[] acc = new double[4];
        for (int i = 0; i < beats.length; i++) {
            int f = (int) Math.round(beats[i] * FPS - n / 2.0 / hop);
            double v = 0;
            for (int g = Math.max(0, f - 3); g <= Math.min(M - 1, f + 3); g++) v = Math.max(v, lo[g] + 0.3 * on[g]);
            acc[i % 4] += v;
        }
        int db = 0;
        for (int k = 1; k < 4; k++) if (acc[k] > acc[db]) db = k;
        // Lautheit, Helligkeit, Ruhe
        double[] dB = new double[M];
        for (int f = 0; f < M; f++) dB[f] = 20 * Math.log10(Math.max(1e-6, rms[f]));
        double[] sorted = dB.clone();
        Arrays.sort(sorted);
        double p10 = sorted[(int) (0.10 * (M - 1))], p95 = sorted[(int) (0.95 * (M - 1))];
        float[] energy = new float[M], bright = new float[M], calm = new float[M];
        double[] e = new double[M], c = new double[M];
        for (int f = 0; f < M; f++) {
            e[f] = clamp((dB[f] - p10) / Math.max(6, p95 - p10));
            c[f] = cent[f] <= 0 ? 0 : clamp(Math.log(cent[f] / 300) / Math.log(4000 / 300.0));
        }
        double[] eS = smooth(e, 0.3), cS = smooth(c, 0.5), e4 = smooth(e, 2.0), o4 = smooth(clip(on, 3), 2.0);
        double oMax = 1e-9;
        for (double v : o4) oMax = Math.max(oMax, v);
        for (int f = 0; f < M; f++) {
            energy[f] = (float) eS[f];
            bright[f] = (float) cS[f];
            double busy = 0.6 * e4[f] + 0.4 * o4[f] / oMax;
            calm[f] = (float) clamp((0.42 - busy) / 0.25);
        }
        float[] onF = new float[M], loF = new float[M];
        for (int f = 0; f < M; f++) { onF[f] = (float) on[f]; loF[f] = (float) lo[f]; }
        // Tempo aus den gefundenen Schlägen (Median der Abstände) statt aus dem Raster der Autokorrelation
        double bpm = 60 * FPS / P;
        if (beats.length > 8) {
            double[] d = new double[beats.length - 1];
            for (int i = 1; i < beats.length; i++) d[i - 1] = beats[i] - beats[i - 1];
            Arrays.sort(d);
            double med = d[d.length / 2];
            double sum = 0;
            int cnt = 0;
            for (double v : d) if (Math.abs(v - med) < 0.08 * med) { sum += v; cnt++; }
            if (cnt > 4) bpm = 60 / (sum / cnt);
        }
        return new Rhythm(FPS, tr.seconds(), bpm, beats, db, onF, loF, energy, bright, calm);
    }

    // ------------------------------------------------------------ Abfragen

    /** Schläge stetig gezählt: 0 auf dem ersten Schlag, 1,5 zwischen zweitem und drittem; davor und danach im Tempo. */
    public double beatAt(double t) {
        int n = beats.length;
        double per = 60 / bpm;
        if (n == 0) return t / per;
        if (t <= beats[0]) return (t - beats[0]) / per;
        if (t >= beats[n - 1]) return n - 1 + (t - beats[n - 1]) / per;
        int lo = 0, hi = n - 1;
        while (hi - lo > 1) { int m = (lo + hi) >>> 1; if (beats[m] <= t) lo = m; else hi = m; }
        return lo + (t - beats[lo]) / (beats[hi] - beats[lo]);
    }

    /** Lage zwischen zwei Schlägen (0 … 1). */
    public double phase(double t) { double b = beatAt(t); return b - Math.floor(b); }

    /** Ist Schlag Nummer i eine Eins? */
    public boolean isDownbeat(int i) { return Math.floorMod(i - downbeat, meter) == 0; }

    /** Lage im Takt (0 … 4: Schläge seit der letzten Eins, gebrochen). */
    public double barBeat(double t) { double b = beatAt(t) - downbeat; return b - meter * Math.floor(b / meter); }

    /** 1 auf dem Schlag, klingt in 120 ms ab; die Eins voll, die übrigen 70 %, mit der Lautheit. */
    public double pulse(double t) {
        double b = beatAt(t);
        int i = (int) Math.floor(b);
        double since = (b - i) * 60 / bpm;
        return Math.exp(-since / 0.12) * (isDownbeat(i) ? 1 : 0.7) * (0.4 + 0.6 * energy(t));
    }

    public double energy(double t) { return at(energy, t); }

    public double brightness(double t) { return at(brightness, t); }

    public double calm(double t) { return at(calm, t); }

    public double onset(double t) { return at(onset, t); }

    private double at(float[] a, double t) {
        if (a.length == 0) return 0;
        double x = t * fps;
        int i = (int) Math.floor(x);
        if (i < 0) return a[0];
        if (i >= a.length - 1) return a[a.length - 1];
        double f = x - i;
        return a[i] * (1 - f) + a[i + 1] * f;
    }

    /** Kurzbeschreibung. */
    public String describe() {
        double calmS = 0;
        for (float v : calm) if (v > 0.5) calmS += 1 / fps;
        return String.format(Locale.GERMANY, "%.1f Schläge/min · %d Schläge · Eins auf Schlag %d · %.0f s ruhig von %.0f s", bpm, beats.length, downbeat + 1, calmS, seconds);
    }

    // ------------------------------------------------------------ Hilfen

    static double clamp(double v) { return Math.max(0, Math.min(1, v)); }

    /** Abzüglich gleitendem Mittel (±w Ausschnitte), halbiert gleichgerichtet, auf Standardabweichung 1. */
    static double[] normalize(double[] a, int w) {
        int M = a.length;
        double[] pre = new double[M + 1];
        for (int i = 0; i < M; i++) pre[i + 1] = pre[i] + a[i];
        double[] o = new double[M];
        double s2 = 0;
        for (int i = 0; i < M; i++) {
            int l = Math.max(0, i - w), r = Math.min(M, i + w + 1);
            o[i] = Math.max(0, a[i] - (pre[r] - pre[l]) / (r - l));
            s2 += o[i] * o[i];
        }
        double sd = Math.sqrt(s2 / Math.max(1, M));
        if (sd > 1e-12) for (int i = 0; i < M; i++) o[i] /= sd;
        return o;
    }

    static double[] clip(double[] a, double m) { double[] o = new double[a.length]; for (int i = 0; i < a.length; i++) o[i] = Math.min(m, a[i]); return o; }

    /** Gleitendes Mittel über ±s Sekunden. */
    static double[] smooth(double[] a, double s) {
        int w = (int) Math.round(s * FPS), M = a.length;
        double[] pre = new double[M + 1];
        for (int i = 0; i < M; i++) pre[i + 1] = pre[i] + a[i];
        double[] o = new double[M];
        for (int i = 0; i < M; i++) { int l = Math.max(0, i - w), r = Math.min(M, i + w + 1); o[i] = (pre[r] - pre[l]) / (r - l); }
        return o;
    }

    /** FFT (Länge Zweierpotenz), an Ort und Stelle. */
    static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j ^= bit;
            j ^= bit;
            if (i < j) { double t = re[i]; re[i] = re[j]; re[j] = t; t = im[i]; im[i] = im[j]; im[j] = t; }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len, wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1, ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = a + len / 2;
                    double xr = re[b] * cr - im[b] * ci, xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr; im[b] = im[a] - xi;
                    re[a] += xr; im[a] += xi;
                    double nr = cr * wr - ci * wi; ci = cr * wi + ci * wr; cr = nr;
                }
            }
        }
    }
}
