#version 430 core
// Avatar: Lage und Normale kommen fertig verformt aus skin.comp (Weltachsen der Person).
// Die Haut trägt zwei Zonen-Vektoren je Ecke (SkinMasks): Dicke, Bart, Rötung, Sommersprossen;
// Fett, Falten, Lippen, Ohr; seit Phase 5 Lidrand und Tiefe im Mund.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec2 aUv;
layout(location = 6) in vec4 aMaskA;
layout(location = 7) in vec4 aMaskB;
layout(location = 8) in vec4 aMaskC;
uniform mat4 uViewProj;
uniform vec3 uOffset;        // Standort der Person, Füße auf dem Boden
out vec3 vWorld;
out vec3 vNrm;
out vec2 vUv;
out vec4 vMaskA;
out vec4 vMaskB;
out vec4 vMaskC;
void main() {
    vWorld = aPos + uOffset;
    vNrm = aNrm;
    vUv = aUv;
    vMaskA = aMaskA;
    vMaskB = aMaskB;
    vMaskC = aMaskC;
    gl_Position = uViewProj * vec4(vWorld, 1.0);
}
