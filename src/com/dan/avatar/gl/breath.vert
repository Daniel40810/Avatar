#version 430 core
// Atemhauch (Phase 8): eine weiche Scheibe je Wolke, Daten {x, y, z, r, Deckung, seed}.
layout(std430, binding = 0) readonly buffer D { float d[]; };
uniform mat4 uViewProj;
uniform vec3 uOffset;
uniform vec3 uEye;
uniform float uFocal;      // Pixel je Meter in 1 m Abstand
out vec3 vWorld;
out float vAlpha;
out float vSeed;
void main() {
    int b = gl_VertexID * 6;
    vec3 p = vec3(d[b], d[b + 1], d[b + 2]) + uOffset;
    vWorld = p;
    vAlpha = d[b + 4];
    vSeed = d[b + 5];
    float dist = max(distance(uEye, p), 0.05);
    gl_PointSize = clamp(2.0 * d[b + 3] * uFocal / dist, 1.0, 256.0);
    gl_Position = uViewProj * vec4(p, 1.0);
}
