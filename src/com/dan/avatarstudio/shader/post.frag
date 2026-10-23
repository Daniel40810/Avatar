#version 430 core
// Belichtung, Tonkurve (ACES nach Narkowicz), Farbgebung über die 3D-Farbtabelle aus portrait (Phase 9),
// Randabdunklung, Filmkorn, Breitwand-Balken, sRGB.
in vec2 vUv;
uniform sampler2D uHdr;
uniform sampler3D uLut;
uniform float uExposure;
uniform float uLutMix;      // 0 ohne Farbgebung … 1 voll
uniform float uGrain;       // Stärke des Korns
uniform float uTime;
uniform float uBars;        // Höhe je Balken (Anteil der Bildhöhe), 0 = keine
uniform float uVignette;
uniform vec2 uSize;
out vec4 oColor;
vec3 aces(vec3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }
float hash(vec2 p) { return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453); }
void main() {
    if (vUv.y < uBars || vUv.y > 1.0 - uBars) { oColor = vec4(0.0, 0.0, 0.0, 1.0); return; }
    vec3 c = texture(uHdr, vUv).rgb * uExposure;
    c = aces(c);
    if (uLutMix > 0.0) {
        const float N = 32.0;
        vec3 g = texture(uLut, c * ((N - 1.0) / N) + 0.5 / N).rgb;
        c = mix(c, g, uLutMix);
    }
    vec2 d = (vUv - 0.5) * vec2(uSize.x / uSize.y, 1.0);
    c *= 1.0 - uVignette * smoothstep(0.35, 1.15, length(d));
    // Korn: luminanzabhängig (am stärksten in den Mitten), 24-mal je Sekunde neu, Körner von 1,3 Bildpunkten
    if (uGrain > 0.0) {
        vec2 cell = floor(gl_FragCoord.xy / 1.3);
        float n = hash(cell + floor(uTime * 24.0) * vec2(17.0, 31.0)) + hash(cell * 1.7 + 3.1) - 1.0;
        float l = dot(c, vec3(0.2126, 0.7152, 0.0722));
        c += n * uGrain * 0.045 * (0.35 + 1.3 * l * (1.0 - l));
    }
    c = pow(max(c, 0.0), vec3(1.0 / 2.2));
    oColor = vec4(c, 1.0);
}
