#version 430 core
// Haarkarten in die Schattenkarte (Alphatest).
in vec2 vUv;
uniform sampler2D uTex;
void main() {
    if (texture(uTex, vUv).a < 0.35) discard;
}
