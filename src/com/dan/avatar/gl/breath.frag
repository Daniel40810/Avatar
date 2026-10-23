#version 430 core
// Feiner Nebel: streut nach vorn (Henyey-Greenstein, g = 0,6), im Gegenlicht hell. Vormultipliziert.
in vec3 vWorld;
in float vAlpha;
in float vSeed;
uniform vec3 uEye;
uniform vec3 uKeyPos; uniform vec3 uKeyCol; uniform vec3 uKeyDir; uniform vec2 uKeyCone;
uniform vec3 uRimPos; uniform vec3 uRimCol;
uniform vec3 uFillCol;
out vec4 oColor;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}
float hg(float c, float g) { float g2 = g * g; return (1.0 - g2) / (12.566 * pow(1.0 + g2 - 2.0 * g * c, 1.5)); }
void main() {
    vec2 q = gl_PointCoord * 2.0 - 1.0;
    float r2 = dot(q, q);
    if (r2 >= 1.0) discard;
    float n = vnoise(q * 2.5 + vSeed * 37.0) * 0.6 + vnoise(q * 5.0 - vSeed * 11.0) * 0.4;
    float a = vAlpha * pow(1.0 - r2, 1.5) * (0.35 + 0.9 * n) * 0.10;
    if (a < 0.002) discard;
    vec3 v = normalize(uEye - vWorld);
    vec3 lk = uKeyPos - vWorld; float dk = length(lk); lk /= dk;
    float cone = smoothstep(uKeyCone.y, uKeyCone.x, dot(-lk, normalize(uKeyDir)));
    vec3 lr = uRimPos - vWorld; float dr = length(lr); lr /= dr;
    vec3 L = uKeyCol * cone / (dk * dk) * hg(dot(-lk, -v), 0.6) * 12.566
           + uRimCol / (dr * dr) * hg(dot(-lr, -v), 0.6) * 12.566
           + uFillCol * 1.5;
    vec3 c = L * 0.9;
    oColor = vec4(c * a, a);
}
