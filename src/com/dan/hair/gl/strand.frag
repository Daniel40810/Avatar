// (hairlight.glsl steht davor)
// Einzelne Haare: gemischt, ohne Tiefe zu schreiben. Ziel 1 (diffuses Hautlicht vor der Streuung) deckt
// nur ein dichtes Feld ab (Bart); einzelne Fliegenhaare nicht, sonst verwischt die Streuung ihre Abdeckung
// zu einem Schleier ohne Haarstruktur.
in vec3 vWorld;
in vec3 vTan;
in float vAlpha;
in float vRand;
in float vGrey;
in float vT;
uniform float uGreyMix;
uniform float uDiffCover;   // Abdeckung des Hautlichts (dichter Bart ja, einzelne Fliegenhaare nein)
layout(location = 0) out vec4 oColor;
layout(location = 1) out vec4 oDiff;
uniform vec2 uDither;          // Rasterblende der Detailstufen (Phase 12): sichtbar im Fenster [x, y)
bool ditherOut() {
    int x = int(mod(gl_FragCoord.x, 4.0)), y = int(mod(gl_FragCoord.y, 4.0));
    int m[16] = int[](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);
    float b = (float(m[y * 4 + x]) + 0.5) / 16.0;
    return b < uDither.x || b >= uDither.y;
}
void main() {
    if (ditherOut()) discard;
    vec3 alb = uHairAlb * (0.75 + 0.5 * vRand);
    if (vGrey > 0.5) alb = mix(alb, vec3(0.72, 0.71, 0.69), uGreyMix);
    vec3 c = hairShade(vWorld, normalize(vTan), alb, uRough, uShift, 1.0);
    oColor = vec4(c, vAlpha);
    oDiff = vec4(0.0, 0.0, 0.0, vAlpha * uDiffCover);
}
