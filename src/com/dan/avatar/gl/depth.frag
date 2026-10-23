#version 430 core
// Schattenkarte: nur Tiefe; Haarkarten, Brauen und Wimpern mit Alphatest.
in vec2 vUv;
uniform sampler2D uTex;
uniform int uAlphaTest;
uniform float uAlphaCutoff;
void main() {
    if (uAlphaTest == 1 && texture(uTex, vUv).a < uAlphaCutoff) discard;
}
