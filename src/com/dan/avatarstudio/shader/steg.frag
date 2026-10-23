#version 430 core
// Steg im Regen (Phase 13): nasse Planken, Pfähle, Laternen, Kai und schwarzes Wasser.
// Jede Laterne ist ein Punktlicht; die Laterne, die die Person beleuchtet (Führungslicht), wirft
// den Schatten der Person auf die Planken. Wasser: kleine Wellen und Regenringe, Fresnel, Spiegel
// der Laternen als lange Streifen. Linear und HDR.
in vec3 vWorld;
in vec3 vNrm;
in vec4 vCol;              // a: 0 nasses Holz, 0.3 matt (Kai), 0.6 Wasser, 1 Glas der Laterne (leuchtet)
uniform vec3 uEye;
uniform float uTime;
uniform int uLampN;
uniform vec3 uLampPos[8];
uniform vec3 uLampCol[8];
uniform int uKeyLamp;      // Laterne mit Schatten (−1 = keine)
uniform vec3 uFillCol;     // Himmel, Mond, Stadt
uniform vec3 uRimPos;      // Kantenlicht (türkis, fern)
uniform vec3 uRimCol;
uniform float uRain;       // mm/h
uniform float uWaterY;
uniform vec3 uFogCol;
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
        vec2 o = rot * POISSON[i] * uShadowSoft * 3.0 * uShadowTexel;
        vis += me - 0.012 <= linDepth(texture(uShadow, s.xy + o).r) ? 1.0 : 0.0;
    }
    return vis / 12.0;
}

float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), u.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), u.x), u.y);
}

// Höhe der Wasserfläche: zwei Scharen Wellen plus Regenringe (je Zelle ein Einschlag, der sich ausbreitet)
float waterH(vec2 p) {
    float h = 0.0;
    h += 0.020 * noise(p * 0.9 + vec2(uTime * 0.21, uTime * 0.13));
    h += 0.010 * noise(p * 2.3 - vec2(uTime * 0.37, -uTime * 0.17));
    h += 0.004 * noise(p * 6.1 + vec2(uTime * 0.6, uTime * 0.4));
    float rr = clamp(uRain / 6.0, 0.0, 1.5);
    if (rr > 0.0) {
        for (int k = 0; k < 2; k++) {
            vec2 q = p * (k == 0 ? 2.2 : 3.7) + float(k) * 17.3;
            vec2 c = floor(q);
            float ph = hash(c + 3.1);
            float t = fract(uTime * (0.9 + 0.6 * ph) + ph);
            vec2 o = vec2(hash(c), hash(c + 7.7)) * 0.8 + 0.1;
            float d = length(fract(q) - o);
            float ring = sin((d - t * 0.55) * 60.0) * exp(-abs(d - t * 0.55) * 30.0) * (1.0 - t);
            h += 0.0016 * rr * ring * (k == 0 ? 1.0 : 0.6);
        }
    }
    return h;
}

vec3 waterNormal(vec2 p, float dist) {
    float e = 0.02 + dist * 0.002;
    float hx = waterH(p + vec2(e, 0)) - waterH(p - vec2(e, 0));
    float hz = waterH(p + vec2(0, e)) - waterH(p - vec2(0, e));
    float k = 1.0 / (1.0 + dist * 0.05);               // in der Ferne glatter
    return normalize(vec3(-hx / (2.0 * e) * k, 1.0, -hz / (2.0 * e) * k));
}

vec3 lamp(int i, vec3 p, vec3 n, vec3 v, float rough, float ks, vec3 alb) {
    vec3 l = uLampPos[i] - p;
    float d2 = max(dot(l, l), 0.04);
    l *= inversesqrt(d2);
    vec3 E = uLampCol[i] / d2;
    if (i == uKeyLamp) E *= shadow(p);
    float nl = max(dot(n, l), 0.0);
    vec3 h = normalize(l + v);
    float sp = pow(max(dot(n, h), 0.0), rough) * (rough + 8.0) / 25.13;
    return E * (alb * nl + ks * sp * nl);
}

void main() {
    vec3 n = normalize(vNrm);
    vec3 v = normalize(uEye - vWorld);
    float dist = length(uEye - vWorld);
    float m = vCol.a;
    vec3 c;
    if (m > 0.85) {
        c = vCol.rgb;                                    // Glas der Laterne leuchtet
    } else if (m > 0.5) {
        // Wasser: schwarz, Spiegel des Himmels und der Laternen
        vec3 wn = waterNormal(vWorld.xz, dist);
        float f = 0.02 + 0.98 * pow(1.0 - max(dot(wn, v), 0.0), 5.0);
        vec3 r = reflect(-v, wn);
        vec3 sky = mix(uFillCol * 1.6, uFogCol, smoothstep(0.0, 0.25, 1.0 - r.y));
        c = vCol.rgb * uFillCol * 0.3 + f * sky;
        for (int i = 0; i < uLampN; i++) {
            vec3 l = uLampPos[i] - vWorld;
            float d = length(l);
            l /= d;
            float g = pow(max(dot(r, l), 0.0), 900.0) * 40.0 + pow(max(dot(r, l), 0.0), 60.0) * 0.6;
            c += uLampCol[i] * g * f / (1.0 + d * d * 0.02);
        }
        // Kantenlicht fern (Stadt, Boje)
        vec3 lr = normalize(uRimPos - vWorld);
        c += uRimCol * pow(max(dot(r, lr), 0.0), 300.0) * f * 0.02;
    } else {
        bool wood = m < 0.25;
        vec3 alb = vCol.rgb;
        float rough = wood ? 120.0 : 24.0;
        float ks = wood ? 0.45 : 0.06;
        if (wood) {
            // Maserung und Fugen (Planken liegen quer, 14 cm)
            float g = noise(vec2(vWorld.x * 1.5, vWorld.z * 22.0));
            alb *= 0.75 + 0.5 * g;
            float seam = smoothstep(0.0, 0.012, abs(fract(vWorld.z / 0.14 + 0.5) - 0.5) * 0.14);
            alb *= mix(0.25, 1.0, seam);
            // Pfützen: glatter, dunkler
            float pud = smoothstep(0.55, 0.75, noise(vWorld.xz * 1.7 + 11.0));
            rough = mix(rough, 600.0, pud);
            ks = mix(ks, 0.9, pud);
            alb *= 1.0 - 0.35 * pud;
        }
        c = alb * uFillCol * (0.5 + 0.5 * n.y);
        for (int i = 0; i < uLampN; i++) c += lamp(i, vWorld, n, v, rough, ks, alb);
        vec3 lr = uRimPos - vWorld; float dr = dot(lr, lr); lr = normalize(lr);
        c += alb * uRimCol / dr * max(dot(n, lr), 0.0) * 0.5;
        float f = pow(1.0 - max(dot(n, v), 0.0), 5.0);
        if (wood) c += f * uFillCol * 1.5;
    }
    // Dunst über dem Wasser
    float fog = 1.0 - exp(-dist * 0.035);
    c = mix(c, uFogCol, fog);
    oColor = vec4(c, 1.0);
}
