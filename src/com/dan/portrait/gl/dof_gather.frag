#version 430 core
// Schritt 2 (halbe Auflösung): Sammeln in der Scheibe des eigenen Kreises; ein Nachbar zählt, wenn
// sein eigener Kreis bis hierher reicht (keine Säume von scharfem Hintergrund über unscharfem Vordergrund).
in vec2 vUv;
uniform sampler2D uHalf;
uniform vec2 uTexel;          // halbe Auflösung
out vec4 oC;
const int N = 40;
float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
void main() {
    vec4 c0 = texture(uHalf, vUv);
    float r0 = abs(c0.a) * 0.5;                 // Halbmesser in halber Auflösung
    if (r0 < 0.6) { oC = c0; return; }
    float rot = hash(gl_FragCoord.xy) * 6.2831853;
    vec3 sum = c0.rgb;
    float wsum = 1.0;
    for (int i = 0; i < N; i++) {
        float t = (float(i) + 0.5) / float(N);
        float rad = sqrt(t) * r0;
        float a = float(i) * 2.39996323 + rot;   // goldener Winkel
        vec2 off = vec2(cos(a), sin(a)) * rad;
        vec4 s = texture(uHalf, vUv + off * uTexel);
        float rs = abs(s.a) * 0.5;
        float w = clamp(rs - rad + 1.0, 0.0, 1.0);
        if (s.a > c0.a + 1.0) w *= clamp(r0 - rad + 1.0, 0.0, 1.0);
        sum += s.rgb * w;
        wsum += w;
    }
    oC = vec4(sum / wsum, c0.a);
}
