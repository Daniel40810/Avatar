#version 430 core
// HUD als Bild über allem, in Bildpunkten von oben links.
uniform vec4 uRect;    // x, y, w, h in Bildpunkten
uniform vec2 uSize;
out vec2 vUv;
void main() {
    vec2 p = vec2((gl_VertexID == 1 || gl_VertexID == 2 || gl_VertexID == 4) ? 1.0 : 0.0,
                  (gl_VertexID == 2 || gl_VertexID == 4 || gl_VertexID == 5) ? 1.0 : 0.0);
    vUv = vec2(p.x, p.y);
    vec2 px = uRect.xy + p * uRect.zw;
    gl_Position = vec4(px.x / uSize.x * 2.0 - 1.0, 1.0 - px.y / uSize.y * 2.0, 0.0, 1.0);
}
