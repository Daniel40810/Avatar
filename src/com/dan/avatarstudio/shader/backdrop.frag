#version 430 core
// Hintergrund ohne Szene: Nacht, unscharfe Lichter in Bernstein und Türkis (Platzhalter bis portrait, Phase 9).
in vec2 vUv;
uniform vec2 uSize;
uniform float uTime;
uniform float uYaw;     // Kamera-Drehung, verschiebt die Lichter leicht (Parallaxe)
uniform float uGain;    // Helligkeit der Lichter der Szene
uniform int uDiscs;     // 0: nur der Verlauf (die Lichter zeichnet portrait seit Phase 9)
out vec4 oColor;

uint hash(uint x) { x ^= x >> 16; x *= 0x7feb352du; x ^= x >> 15; x *= 0x846ca68bu; x ^= x >> 16; return x; }
float rnd(uint i, uint k) { return float(hash(i * 747796405u + k * 2891336453u + 4711u)) / 4294967295.0; }

void main() {
    vec2 px = vUv * uSize;
    float asp = uSize.x / uSize.y;
    vec2 q = vec2(vUv.x * asp, vUv.y);
    vec3 c = mix(vec3(0.0016, 0.0030, 0.0042), vec3(0.0060, 0.0100, 0.0140), vUv.y);
    for (uint i = 0u; i < (uDiscs == 1 ? 42u : 0u); i++) {
        float x = rnd(i, 1u) * (asp + 0.6) - 0.3 - uYaw * 0.15;
        x = mod(x + 0.3, asp + 0.6) - 0.3;
        float y = 0.25 + rnd(i, 2u) * 0.7;
        float rad = 0.02 + rnd(i, 3u) * 0.06;
        bool warm = rnd(i, 4u) < 0.6;
        vec3 col = warm ? vec3(1.0, 0.55, 0.18) : vec3(0.12, 0.62, 0.66);
        float a = (0.010 + rnd(i, 5u) * 0.030) * (0.85 + 0.15 * sin(uTime * (0.2 + rnd(i, 6u)) + rnd(i, 7u) * 6.28));
        float d = length(q - vec2(x, y)) / rad;
        float disk = smoothstep(1.0, 0.86, d) * (0.75 + 0.25 * smoothstep(0.6, 1.0, d));
        c += col * a * disk;
    }
    oColor = vec4(c * uGain, 1.0);
}
