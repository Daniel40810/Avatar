#version 430 core
// Bühne: Boden, Standring, Maßstab. Eine Ecke trägt Lage, Normale und Farbe (a = Eigenleuchten).
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec4 aCol;
uniform mat4 uViewProj;
out vec3 vWorld;
out vec3 vNrm;
out vec4 vCol;
void main() {
    vWorld = aPos;
    vNrm = aNrm;
    vCol = aCol;
    gl_Position = uViewProj * vec4(aPos, 1.0);
}
