package com.dan.avatar;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;

/**
 * Die sechs Grundhäute (F6 A, CC0 aus den MakeHuman-Systempaketen, vorbereitet von
 * {@code tools/blender/haut_build.py}) als sRGB-Bytes, dazu je Bild die mittlere Hautfarbe (linear)
 * über die Fläche, die der Körper wirklich benutzt. Der Shader teilt durch diesen Mittelwert und
 * behält so nur das Muster; den Ton gibt {@link Skin#tone}.
 */
public final class SkinTextures {

    public final int size;
    /** RGB-Bytes (sRGB) je Ebene, Reihenfolge wie {@link Skin#TEXTURES}. */
    public final byte[][] rgb;
    /** Mittlere Farbe je Ebene, linear. */
    public final float[][] mean;
    public final double ms;

    private static volatile SkinTextures cached;

    /** Einmal geladen (aus den Ressourcen im Package), gemittelt über die UV-Fläche des Körpers. */
    public static SkinTextures load(Body body) throws IOException {
        SkinTextures s = cached;
        if (s == null) synchronized (SkinTextures.class) {
            if (cached == null) cached = new SkinTextures(body);
            s = cached;
        }
        return s;
    }

    private SkinTextures(Body body) throws IOException {
        long t0 = System.nanoTime();
        int n = Skin.TEXTURES.length;
        BufferedImage[] im = new BufferedImage[n];
        IOException[] err = new IOException[1];
        IntStream.range(0, n).parallel().forEach(i -> {
            String name = "res/haut/haut_" + Skin.TEXTURES[i] + ".jpg";
            try (InputStream in = SkinTextures.class.getResourceAsStream(name)) {
                if (in == null) throw new IOException("Hauttextur fehlt: " + name);
                im[i] = ImageIO.read(in);
            } catch (IOException e) {
                err[0] = e;
            }
        });
        if (err[0] != null) throw err[0];
        size = im[0].getWidth();
        boolean[] cover = coverage(body, 256);
        rgb = new byte[n][];
        mean = new float[n][];
        float[] lin = new float[256];
        for (int i = 0; i < 256; i++) lin[i] = (float) srgbToLinear(i / 255.0);
        IntStream.range(0, n).parallel().forEach(k -> {
            BufferedImage b = im[k];
            int w = b.getWidth(), h = b.getHeight();
            int[] px = b.getRGB(0, 0, w, h, null, 0, w);
            byte[] out = new byte[w * h * 3];
            double[] s = new double[3];
            long cnt = 0;
            for (int y = 0; y < h; y++)
                for (int x = 0; x < w; x++) {
                    int p = px[y * w + x];
                    int o = (y * w + x) * 3;
                    out[o] = (byte) (p >> 16); out[o + 1] = (byte) (p >> 8); out[o + 2] = (byte) p;
                    // UV v läuft in glTF von oben nach unten wie die Bildzeilen
                    int cx = x * 256 / w, cy = y * 256 / h;
                    if (cover[cy * 256 + cx]) {
                        s[0] += lin[(p >> 16) & 255]; s[1] += lin[(p >> 8) & 255]; s[2] += lin[p & 255];
                        cnt++;
                    }
                }
            rgb[k] = out;
            mean[k] = new float[]{(float) (s[0] / cnt), (float) (s[1] / cnt), (float) (s[2] / cnt)};
        });
        ms = (System.nanoTime() - t0) / 1e6;
    }

    /** Welche Zellen eines n×n-Rasters die UV-Dreiecke des Körpers bedecken. */
    static boolean[] coverage(Body body, int n) {
        Body.Part b = body.body();
        float[] uv = b.prim.uv0;
        int[] ix = b.prim.indices;
        boolean[] c = new boolean[n * n];
        if (uv == null) { java.util.Arrays.fill(c, true); return c; }
        for (int t = 0; t + 2 < ix.length; t += 3) {
            double[] u = new double[3], v = new double[3];
            for (int k = 0; k < 3; k++) { u[k] = uv[ix[t + k] * 2] * n; v[k] = uv[ix[t + k] * 2 + 1] * n; }
            double d = (u[1] - u[0]) * (v[2] - v[0]) - (u[2] - u[0]) * (v[1] - v[0]);
            if (Math.abs(d) < 1e-12) continue;
            int x0 = (int) Math.max(0, Math.floor(Math.min(u[0], Math.min(u[1], u[2])))), x1 = (int) Math.min(n - 1, Math.ceil(Math.max(u[0], Math.max(u[1], u[2]))));
            int y0 = (int) Math.max(0, Math.floor(Math.min(v[0], Math.min(v[1], v[2])))), y1 = (int) Math.min(n - 1, Math.ceil(Math.max(v[0], Math.max(v[1], v[2]))));
            for (int y = y0; y <= y1; y++)
                for (int x = x0; x <= x1; x++) {
                    double px = x + 0.5, py = y + 0.5;
                    double w1 = ((px - u[0]) * (v[2] - v[0]) - (u[2] - u[0]) * (py - v[0])) / d;
                    double w2 = ((u[1] - u[0]) * (py - v[0]) - (px - u[0]) * (v[1] - v[0])) / d;
                    if (w1 >= 0 && w2 >= 0 && w1 + w2 <= 1) c[y * n + x] = true;
                }
        }
        return c;
    }

    static double srgbToLinear(double c) {
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }
}
