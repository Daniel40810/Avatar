#version 430 core
// Licht der leeren Bühne: Führungslicht in Bernstein von vorn links oben (Spot),
// Kantenlicht in Türkis von hinten rechts, schwaches kühles Aufhelllicht.
// Linear und HDR; der Boden ist nasser Schiefer (dunkel, glänzend).
in vec3 vWorld;
in vec3 vNrm;
in vec4 vCol;
uniform vec3 uEye;
uniform vec3 uKeyPos;   uniform vec3 uKeyCol;   uniform vec3 uKeyDir; uniform vec2 uKeyCone; // cos innen, cos außen
uniform vec3 uRimPos;   uniform vec3 uRimCol;
uniform vec3 uFillCol;
uniform float uStageRadius;
uniform int uShadowOn;
uniform sampler2D uShadow;
uniform mat4 uLightVP;
uniform vec2 uLightNF;
uniform float uShadowSoft;
uniform vec2 uShadowTexel;
out vec4 oColor;

const vec2 POISSON[12] = vec2[](
    vec2(-0.326, -0.406), vec2(-0.840, -0.074), vec2(-0.696, 0.457), vec2(-0.203, 0.621),
    vec2(0.962, -0.195), vec2(0.473, -0.480), vec2(0.519, 0.767), vec2(0.185, -0.893),
    vec2(0.507, 0.064), vec2(0.896, 0.412), vec2(-0.322, -0.933), vec2(-0.792, -0.598));

float linDepth(float d) {
    float z = d * 2.0 - 1.0;
    return 2.0 * uLightNF.x * uLightNF.y / (uLightNF.y + uLightNF.x - z * (uLightNF.y - uLightNF.x));
}

// Schatten der Personen auf der Bühne, weicher als auf der Haut (Abstand zum Boden)
float shadow(vec3 p) {
    if (uShadowOn == 0) return 1.0;
    vec4 lp = uLightVP * vec4(p, 1.0);
    vec3 s = lp.xyz / lp.w * 0.5 + 0.5;
    if (s.x <= 0.0 || s.x >= 1.0 || s.y <= 0.0 || s.y >= 1.0 || s.z >= 1.0) return 1.0;
    float me = linDepth(s.z);
    float vis = 0.0;
    float a = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) * 6.2832;
    mat2 rot = mat2(cos(a), sin(a), -sin(a), cos(a));
    for (int i = 0; i < 12; i++) {
        vec2 o = rot * POISSON[i] * uShadowSoft * 2.5 * uShadowTexel;
        vis += me - 0.01 <= linDepth(texture(uShadow, s.xy + o).r) ? 1.0 : 0.0;
    }
    return vis / 12.0;
}

float spec(vec3 n, vec3 l, vec3 v, float p) {
    vec3 h = normalize(l + v);
    return pow(max(dot(n, h), 0.0), p) * (p + 8.0) / 25.13;
}

void main() {
    vec3 n = normalize(vNrm);
    vec3 v = normalize(uEye - vWorld);
    vec3 alb = vCol.rgb;
    float r = length(vWorld.xz);

    // Führungslicht als Spot mit Abfall nach Entfernung
    vec3 lk = uKeyPos - vWorld; float dk = length(lk); lk /= dk;
    float cone = smoothstep(uKeyCone.y, uKeyCone.x, dot(-lk, normalize(uKeyDir)));
    vec3 key = uKeyCol * cone / (dk * dk) * shadow(vWorld);
    // Kantenlicht
    vec3 lr = uRimPos - vWorld; float dr = length(lr); lr /= dr;
    vec3 rim = uRimCol / (dr * dr);

    float wet = vCol.a < 0.25 ? 1.0 : 0.0;  // Boden und Maßstab glänzen (a < 0,25), matt (0,25 … 0,5, weiter Boden der Menge), Ring leuchtet
    float sp = mix(32.0, 180.0, wet);
    float ks = mix(0.08, 0.55, wet);

    vec3 c = alb * (key * max(dot(n, lk), 0.0) + rim * max(dot(n, lr), 0.0) + uFillCol * (0.5 + 0.5 * n.y));
    c += ks * (key * spec(n, lk, v, sp) + rim * spec(n, lr, v, sp));
    // Fresnel: flacher Blick, mehr Spiegel der dunklen Umgebung
    float f = pow(1.0 - max(dot(n, v), 0.0), 5.0);
    c += wet * f * vec3(0.010, 0.016, 0.020);

    if (vCol.a >= 0.5) c = alb * vCol.a;     // Eigenleuchten (Standring)

    // Rand der Bühne läuft ins Dunkel
    float edge = 1.0 - smoothstep(uStageRadius * 0.55, uStageRadius, r);
    c *= mix(0.0, 1.0, edge);
    oColor = vec4(c, 1.0);
}
