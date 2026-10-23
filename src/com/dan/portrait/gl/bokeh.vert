#version 430 core
// Lichter des Hintergrunds als Zerstreuungskreise: Größe aus dem Objektiv (cocK · |z − s| / z) oder
// aus der Größe des Lichts, Helligkeit mit der Fläche verteilt (Energie bleibt). Spiegelungen auf
// nassem Boden senkrecht gestreckt und weicher.
layout(std430, binding = 0) readonly buffer L { float d[]; };   // je Licht: x y z r  R G B spiegel
uniform mat4 uViewProj;
uniform vec2 uSize;
uniform float uCocK, uFocus, uFocalPx, uMaxPx;
out vec2 vQ;
out vec3 vCol;
out float vD;
out float vMirror;
void main() {
    int i = gl_VertexID / 6, k = gl_VertexID - i * 6;
    vec2 c = vec2((k == 1 || k == 2 || k == 4) ? 1.0 : -1.0, (k == 2 || k == 4 || k == 5) ? 1.0 : -1.0);
    int b = i * 8;
    vec4 cp = uViewProj * vec4(d[b], d[b + 1], d[b + 2], 1.0);
    if (cp.w <= 0.05) { gl_Position = vec4(2.0, 2.0, 2.0, 1.0); vQ = c; vCol = vec3(0.0); vD = 1.0; vMirror = 0.0; return; }
    float z = cp.w;
    float coc = uCocK * abs(z - uFocus) / z;
    float sz = 2.0 * d[b + 3] * uFocalPx / z;
    float mir = d[b + 7];
    float D = clamp(max(max(coc, sz), 1.5) * (1.0 + 0.2 * mir), 1.5, uMaxPx);
    vec3 col = vec3(d[b + 4], d[b + 5], d[b + 6]);
    float e = clamp(sz * sz / (D * D), 0.0, 1.0);
    vCol = col * e;
    vQ = c;
    vD = D;
    vMirror = mir;
    vec2 half_ = vec2(D * 0.5, D * 0.5 * (1.0 + 0.8 * mir)) / uSize * 2.0;
    gl_Position = vec4(cp.xy / cp.w + c * half_, 0.0, 1.0);
}
