#version 430 core
// Schärfentiefe, Schritt 1 (halbe Auflösung): Farbe aus 2 × 2 Bildpunkten, Zerstreuungskreis (Bildpunkte
// der vollen Auflösung, + hinter, − vor dem Fokus). Hintergrund ohne Tiefe ist schon unscharf (0).
in vec2 vUv;
uniform sampler2D uColor, uDepth;
uniform vec2 uTexel;          // volle Auflösung
uniform float uNear, uFar, uCocK, uFocus, uMaxCoc;
out vec4 oC;
float linZ(float d) { float z = d * 2.0 - 1.0; return 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear)); }
float coc(vec2 uv) {
    float d = texture(uDepth, uv).r;
    if (d >= 0.99999) return 0.0;
    float z = linZ(d);
    return clamp(uCocK * (z - uFocus) / z, -uMaxCoc, uMaxCoc);
}
void main() {
    vec2 o = uTexel * 0.5;
    vec3 c = texture(uColor, vUv + vec2(-o.x, -o.y)).rgb + texture(uColor, vUv + vec2(o.x, -o.y)).rgb
           + texture(uColor, vUv + vec2(-o.x, o.y)).rgb + texture(uColor, vUv + vec2(o.x, o.y)).rgb;
    float k0 = coc(vUv + vec2(-o.x, -o.y)), k1 = coc(vUv + vec2(o.x, -o.y)), k2 = coc(vUv + vec2(-o.x, o.y)), k3 = coc(vUv + vec2(o.x, o.y));
    float k = k0;
    if (abs(k1) > abs(k)) k = k1;
    if (abs(k2) > abs(k)) k = k2;
    if (abs(k3) > abs(k)) k = k3;
    oC = vec4(c * 0.25, k);
}
