#version 430 core
// Ferne Detailstufen: Farbe je Teil, Führungslicht (Spot), Kante, Aufhellung; Übergänge zwischen den Stufen
// als Rasterblende (Bayer 4×4): sichtbar, wo der Schwellwert im Fenster [uDither.x, uDither.y) liegt.
in vec3 vWorld;
in vec3 vNrm;
in vec3 vCol;
uniform vec3 uEye;
uniform vec3 uKeyPos, uKeyDir, uKeyCol, uRimPos, uRimCol, uFillCol;
uniform vec2 uKeyCone;
uniform vec2 uDither;
uniform vec3 uTint;          // Anzeige der Stufe (0 = aus)
layout(location = 0) out vec4 oColor;
layout(location = 1) out vec4 oDiff;
float bayer(vec2 p) {
    int x = int(mod(p.x, 4.0)), y = int(mod(p.y, 4.0));
    int m[16] = int[](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);
    return (float(m[y * 4 + x]) + 0.5) / 16.0;
}
void main() {
    float b = bayer(gl_FragCoord.xy);
    if (b < uDither.x || b >= uDither.y) discard;
    vec3 n = normalize(vNrm);
    vec3 v = normalize(uEye - vWorld);
    if (dot(n, v) < 0.0) n = -n;
    vec3 lk = uKeyPos - vWorld; float dk = length(lk); lk /= dk;
    float cone = smoothstep(uKeyCone.y, uKeyCone.x, dot(-lk, normalize(uKeyDir)));
    vec3 key = uKeyCol * cone / (dk * dk);
    vec3 lr = uRimPos - vWorld; float dr = length(lr); lr /= dr;
    vec3 rim = uRimCol / (dr * dr);
    float wrap = 0.3;
    vec3 c = vCol * (key * max((dot(n, lk) + wrap) / (1.0 + wrap), 0.0) + rim * max((dot(n, lr) + wrap) / (1.0 + wrap), 0.0) + uFillCol * (0.6 + 0.4 * n.y));
    if (uTint.x + uTint.y + uTint.z > 0.0) c = mix(c, uTint * (0.4 + 0.6 * max(dot(n, lk), 0.2)), 0.6);
    oColor = vec4(c, 1.0);
    oDiff = vec4(0.0);
}
