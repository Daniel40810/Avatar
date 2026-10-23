#version 430 core
// Einzelne Haare als Linien. Je Haar uSeg Abschnitte; die Wurzel liegt baryzentrisch in einem
// Dreieck des verformten Netzes (Lage aus dessen Zeichenpuffer), die Richtung im Rahmen des Dreiecks.
// Form: gerade, mit Schwerkraft gebogen, Locken als Schraube, Wind und Kopf biegen die Spitze.
layout(std430, binding = 0) readonly buffer PosB { float pos[]; };
layout(std430, binding = 1) readonly buffer NrmB { float nrm[]; };
layout(std430, binding = 2) readonly buffer IdxB { uint idx[]; };
layout(std430, binding = 3) readonly buffer StrB { float str[]; };
layout(std430, binding = 4) readonly buffer FlexB { float flexv[]; };   // 4 je Ecke, x = Biegsamkeit
uniform int uRootFlex;      // 1: Wurzel biegt sich mit der Karte (wie hair.vert)
uniform mat4 uViewProj;
uniform vec3 uOffset;
uniform int uSeg;
uniform float uGravity;     // Durchhang (Anteil der Länge)
uniform vec3 uTip;
uniform vec3 uFlutter;
uniform float uTime;
uniform float uFocal;       // Pixel je Meter in 1 m Abstand
uniform float uRadius;      // Radius eines Haares (m)
uniform vec3 uEye;
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
out vec3 vTan;
out float vAlpha;
out float vRand;
out float vGrey;
out float vT;

vec3 P(uint i) { return vec3(pos[i * 3u], pos[i * 3u + 1u], pos[i * 3u + 2u]); }
vec3 N(uint i) { return vec3(nrm[i * 3u], nrm[i * 3u + 1u], nrm[i * 3u + 2u]); }

vec3 shape(float t, vec3 root, vec3 d, vec3 u, vec3 w, float len, float curl, float turns, float rnd, float flex) {
    vec3 p = root + d * (len * t);
    p += vec3(0.0, -1.0, 0.0) * (uGravity * flex * len * t * t);
    float ang = 6.2831853 * (turns * t + rnd);
    p += curl * t * (cos(ang) * u + sin(ang) * w);
    float f = flex * t * t;
    p += uTip * f + uFlutter * f * sin(uTime * 7.0 + rnd * 40.0);
    return p;
}

void main() {
    int per = uSeg * 2;
    int sid = gl_VertexID / per;
    int k = gl_VertexID - sid * per;
    int seg = k / 2 + (k & 1);
    float t = float(seg) / float(uSeg);
    int b = sid * 12;
    uint tri = uint(str[b]);
    float b1 = str[b + 1], b2 = str[b + 2];
    uint i0 = idx[tri * 3u], i1 = idx[tri * 3u + 1u], i2 = idx[tri * 3u + 2u];
    vec3 p0 = P(i0), p1 = P(i1), p2 = P(i2);
    vec3 e1 = p1 - p0, e2 = p2 - p0;
    vec3 n = normalize(cross(e1, e2));
    if (dot(n, N(i0) + N(i1) + N(i2)) < 0.0) n = -n;
    vec3 root = p0 + b1 * e1 + b2 * e2 + uOffset;
    if (uRootFlex == 1) {
        vec3 bw = vec3(1.0 - b1 - b2, b1, b2);
        uint ii[3] = uint[3](i0, i1, i2);
        vec3 pp[3] = vec3[3](p0, p1, p2);
        for (int q = 0; q < 3; q++) {
            float f = flexv[ii[q] * 4u]; f *= f;
            float ph = dot(pp[q], vec3(31.0, 17.0, 23.0));
            root += bw[q] * (uTip * f + uFlutter * f * sin(uTime * 6.0 + ph));
        }
    }
    vec3 d = normalize(str[b + 3] * e1 + str[b + 4] * e2 + str[b + 5] * n);
    vec3 u = normalize(cross(d, abs(d.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    vec3 w = cross(d, u);
    float len = str[b + 6], curl = str[b + 7], turns = str[b + 8], rnd = str[b + 9], flex = str[b + 10];
    vec3 p = shape(t, root, d, u, w, len, curl, turns, rnd, flex);
    vec3 p2_ = shape(min(t + 0.02, 1.0), root, d, u, w, len, curl, turns, rnd, flex);
    vec3 p1_ = shape(max(t - 0.02, 0.0), root, d, u, w, len, curl, turns, rnd, flex);
    p = hoodPress(p); p2_ = hoodPress(p2_); p1_ = hoodPress(p1_);
    vTan = normalize(p2_ - p1_ + 1e-7);
    vWorld = p;
    vRand = rnd;
    vGrey = str[b + 11];
    vT = t;
    // Deckung: Breite des Haares in Pixeln (dünner als ein Pixel → durchsichtiger), zur Spitze dünner
    float depth = max(distance(uEye, p), 0.05);
    float px = 2.0 * uRadius * uFocal / depth;
    vAlpha = clamp(px, 0.08, 1.0) * (1.0 - 0.65 * t) * smoothstep(0.0, 0.06, t);
    gl_Position = uViewProj * vec4(p, 1.0);
}
