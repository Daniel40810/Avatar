#version 430 core
// Schritt 3 (volle Auflösung): scharf und unscharf nach dem Kreis überblenden.
in vec2 vUv;
uniform sampler2D uColor, uDepth, uBlur;
uniform float uNear, uFar, uCocK, uFocus, uMaxCoc;
out vec4 oC;
float linZ(float d) { float z = d * 2.0 - 1.0; return 2.0 * uNear * uFar / (uFar + uNear - z * (uFar - uNear)); }
void main() {
    vec4 sharp = texture(uColor, vUv);
    float d = texture(uDepth, vUv).r;
    float k = 0.0;
    if (d < 0.99999) { float z = linZ(d); k = clamp(uCocK * (z - uFocus) / z, -uMaxCoc, uMaxCoc); }
    vec4 b = texture(uBlur, vUv);
    float t = smoothstep(0.9, 3.0, max(abs(k), abs(b.a) * 0.7));
    oC = vec4(mix(sharp.rgb, b.rgb, t), 1.0);
}
