#version 430 core
// Haarkarten: Lage und Normale kommen verformt vom Körper (Skinning im Compute). Wind und Kopf
// biegen die Karte: Versatz der Spitze mal Biegsamkeit² (0 an der Kopfhaut, 1 weit weg).
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec2 aUv;
layout(location = 8) in vec4 aHair;      // x Biegsamkeit 0…1, yzw Haarrichtung (Wurzel → Spitze)
uniform mat4 uViewProj;
uniform vec3 uOffset;
uniform vec3 uTip;                       // Versatz der Spitzen (m)
uniform vec3 uFlutter;                   // Flattern (m)
uniform float uTime;
uniform mat3 uRot;                       // Drehung des Kopfes gegen die Ruhe (Haarrichtung folgt)
uniform vec4 uHood;                      // Kapuze (Phase 10): Mitte (Welt) und Innenradius; w ≤ 0 aus
// Die Kapuze drückt das Haar an den Kopf: oberhalb der Ohren in die Kugel, darunter fällt es frei
vec3 hoodPress(vec3 p) {
    if (uHood.w <= 0.0) return p;
    vec3 d = p - uHood.xyz;
    float r = length(d);
    if (r <= uHood.w) return p;
    float k = smoothstep(-0.11, -0.03, d.y);
    if (d.z > 0.0 && abs(d.x) < 0.075 && d.y > -0.14) k = 1.0;       // Pony vor dem Gesicht unter die Kapuze
    return mix(p, uHood.xyz + d * (uHood.w / r), k);
}
out vec3 vWorld;
out vec3 vNrm;
out vec2 vUv;
out float vFlex;
out vec3 vTan;
void main() {
    float f = aHair.x * aHair.x;
    float ph = dot(aPos, vec3(31.0, 17.0, 23.0));
    vec3 w = uTip * f + uFlutter * f * sin(uTime * 6.0 + ph);
    vWorld = hoodPress(aPos + uOffset + w);
    vNrm = aNrm;
    vUv = aUv;
    vFlex = aHair.x;
    vTan = uRot * aHair.yzw;
    gl_Position = uViewProj * vec4(vWorld, 1.0);
}
