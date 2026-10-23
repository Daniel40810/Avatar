package com.dan.avatar;

/**
 * Skinning auf dem Prozessor, Zeile für Zeile wie {@code gl/skin.comp}: Referenz für die
 * Grafikkarte, für Treffer (Klick, Kontakt) und für die Prüfung gegen Blender.
 */
public final class Skinning {

    private Skinning() {}

    /** Verformt die gemischten Ruhelagen aller Teile; Ergebnis in {@code outP[part]}, {@code outN[part]}. */
    public static void apply(Body body, float[] dq, float[] mat, boolean dualQuaternion, float[][] outP, float[][] outN) {
        for (int k = 0; k < body.parts.size(); k++) {
            Body.Part p = body.parts.get(k);
            int n = p.prim.vertexCount;
            if (outP[k] == null || outP[k].length != n * 3) { outP[k] = new float[n * 3]; outN[k] = new float[n * 3]; }
            int[] js = p.prim.joints;
            float[] ws = p.prim.weights;
            for (int v = 0; v < n; v++) {
                int o = v * 3;
                if (js == null) {
                    System.arraycopy(p.position, o, outP[k], o, 3);
                    System.arraycopy(p.normal, o, outN[k], o, 3);
                } else if (dualQuaternion) {
                    Dq.skin(dq, js, ws, v, p.position[o], p.position[o + 1], p.position[o + 2],
                            p.normal[o], p.normal[o + 1], p.normal[o + 2], outP[k], outN[k], o);
                } else {
                    Dq.linear(mat, js, ws, v, p.position[o], p.position[o + 1], p.position[o + 2],
                              p.normal[o], p.normal[o + 1], p.normal[o + 2], outP[k], outN[k], o);
                }
            }
        }
    }
}
