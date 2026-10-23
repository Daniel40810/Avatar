#version 430 core
// Tropfenkarte im Texturraum (Phase 8): Perlen als Kappen (uMode 0) oder Spuren als Bänder (uMode 1).
// Daten je Element 5 floats: Perle {x, y, rx, ry, Höhe mm}, Spur {x0, y0, x1, y1, Breite}; Ausschnitt 0…1.
layout(std430, binding = 0) readonly buffer D { float d[]; };
uniform int uMode;
out vec2 vC;
out float vH;
void main() {
    int e = gl_VertexID / 6, k = gl_VertexID - e * 6;
    vec2 c = vec2((k == 1 || k == 2 || k == 4) ? 1.0 : -1.0, (k == 2 || k == 4 || k == 5) ? 1.0 : -1.0);
    int b = e * 5;
    vec2 p;
    if (uMode == 0) {
        p = vec2(d[b], d[b + 1]) + c * vec2(d[b + 2], d[b + 3]) * 1.15;
        vC = c * 1.15;
        vH = d[b + 4];
    } else {
        vec2 a = vec2(d[b], d[b + 1]), z = vec2(d[b + 2], d[b + 3]);
        float w = max(d[b + 4], 1e-4);
        vec2 t = z - a; float L = length(t);
        t = L > 1e-7 ? t / L : vec2(0.0, 1.0);
        vec2 nn = vec2(-t.y, t.x);
        p = (c.x < 0.0 ? a - t * w : z + t * w) + nn * c.y * w;
        vC = vec2(0.0, c.y);
        vH = 1.0;
    }
    gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
}
