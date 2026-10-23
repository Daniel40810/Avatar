#version 430 core
// Perle: Kappe h · (1 − d²)^0,6 (Höhe in mm). Spur: Nässe 0…1, zur Mitte voll.
in vec2 vC;
in float vH;
uniform int uMode;
out vec4 oH;
void main() {
    if (uMode == 0) {
        float q = dot(vC, vC);
        if (q >= 1.0) discard;
        oH = vec4(vH * pow(1.0 - q, 0.6), 0.0, 0.0, 1.0);
    } else {
        float q = vC.y * vC.y;
        oH = vec4(1.0 - q, 0.0, 0.0, 1.0);
    }
}
