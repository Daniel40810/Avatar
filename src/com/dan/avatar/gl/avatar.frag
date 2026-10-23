#version 430 core
// Avatar (Phase 4). Zwei Wege:
//  uMode 1 Haut: Grundhäute (sechs Ebenen, nach Alter und Geschlecht gemischt, durch ihren Mittelwert
//    geteilt) mal Grundton aus Melanin und Rötung; Detailkarten für Poren, Fältchen, Sommersprossen,
//    Altersflecken, Marmorierung und Bartstoppeln; Detailnormalen über den Oberflächengradienten
//    (Mikkelsen 2010, ohne Tangenten); zwei Glanzlagen (Haut und Fettfilm, GGX); Schatten aus der
//    Schattenkarte (weiches PCF); Durchleuchtung dünner Stellen aus Dicke und Schattenkarte (Jimenez 2010).
//    Mit uSplit = 1 geht das diffuse Licht in Ziel 1 (für die Streuung im Bildraum, SkinScatter), der
//    Glanz in Ziel 0. Auf „Niedrig“ (uLow = 1) vorab integrierte Streuung (Penner 2011) ohne eigenen Durchgang.
//  uMode 2 Augen (Phase 5, F9 A): analytisch je Auge (EyeRig): Hornhaut als Kugelkappe, Brechung
//    (n = 1,376) auf die Irisebene, Iris aus Parametern und seed (Fasern, Krause, Krypten, Limbusring),
//    Pupille nach Licht, Lederhaut mit Adern, Schatten der Lider, Glanz der Hornhaut aus den Lichtern.
//  uMode 3 Zähne und Zunge: Abdunklung nach Tiefe im Mund und Öffnung (kein „leuchtendes Gebiss“).
//  uMode 0 alles andere (Haar, Kleidung): einfaches Licht wie bisher, mit Schatten.
in vec3 vWorld;
in vec3 vNrm;
in vec2 vUv;
in vec4 vMaskA;   // Dicke (m), Bart, Rötung, Sommersprossen
in vec4 vMaskB;   // Fett, Falten, Lippen, Ohr
in vec4 vMaskC;   // Lidrand (nass), Tiefe im Mund, frei, frei
uniform vec3 uEye;
uniform vec3 uKeyPos;   uniform vec3 uKeyCol;   uniform vec3 uKeyDir; uniform vec2 uKeyCone;
uniform vec3 uRimPos;   uniform vec3 uRimCol;
uniform vec3 uFillCol;
uniform vec4 uBaseColor;
uniform int uHasTex;
uniform sampler2D uTex;
uniform int uAlphaMode;      // 0 OPAQUE, 1 MASK, 2 BLEND (als Maske gezeichnet)
uniform float uAlphaCutoff;
uniform float uWrap;
uniform float uSpec;
uniform float uShine;
uniform int uMode;           // 0 sonstiges, 1 Haut
uniform int uSplit;          // 1: diffuses Hautlicht nach Ziel 1
// Schatten
uniform int uShadowOn;
uniform sampler2D uShadow;
uniform mat4 uLightVP;
uniform vec2 uLightNF;       // nah, fern der Schattenkamera
uniform float uShadowSoft;   // Radius in Texeln
uniform vec2 uShadowTexel;
// Haut
uniform sampler2DArray uSkinTex;
uniform float uTexW[6];
uniform vec3 uTexInvMean[6];
uniform vec3 uTone;
uniform sampler2D uDetA;
uniform sampler2D uDetB;
uniform float uTile;
uniform float uSlope;
uniform vec2 uSeedOfs;
uniform vec4 uSkinP;         // Poren, Alter (Falten), Sommersprossen, Bart
uniform vec4 uSkinQ;         // Fett, Altersflecken, Rötung, Melanin
uniform vec2 uTrans;         // Dickenmaßstab, Stärke der Durchleuchtung
uniform int uLow;
uniform sampler2D uLut;
// Augen
uniform vec3 uEyeC[2];
uniform vec3 uEyeX[2];
uniform vec3 uEyeY[2];
uniform vec3 uEyeZ[2];
uniform float uEyeR[2];
uniform vec4 uIris;          // Farbe 0…1 (blau … dunkelbraun), Pupille (Anteil am Irisradius), seed u, seed v
uniform float uMouthOpen;    // 0 geschlossen … 1 weit offen
uniform float uGain;         // Helligkeit der Lichter (auch für die gespiegelte Umgebung)
uniform int uDebug;          // 1…8: Zone als Farbe (Prüfen der Masken)
// Umwelt (Phase 8)
uniform vec4 uWet;           // Nässe: Gesicht, Haar/Kopfhaut, Oberkörper, Beine
uniform vec3 uRainFrom;      // Richtung, aus der der Regen kommt
uniform float uCold;         // Kälte 0…1
uniform vec2 uZoneY;         // Höhe von Hals und Hüfte (Welt)
uniform int uCloth;          // 1: Stoff
uniform vec4 uFab0;          // Stoff (Phase 10): rau, Flaum, Muster, Maßstab
uniform vec4 uFab1;          // saugt, Perlen, Wasserglanz, gesetzt (−1: wie bis Phase 9)
uniform vec3 uFabCol;        // Farbe des Stoffs (linear)
uniform int uDropOn;
uniform vec4 uDropRect;      // UV-Ausschnitt der Tropfenkarte
uniform float uDropTexel;
uniform sampler2D uDropH;    // Höhe der Perlen (mm)
uniform sampler2D uDropT;    // Spuren (Nässe 0…1)
// Umgebung (Phase 9): Lichter der Szene für Spiegelungen in Augen und Wasser
uniform int uEnvN;
uniform vec3 uEnvDir[16];
uniform vec3 uEnvCol[16];
uniform float uEnvCos[16];   // cos des Winkelhalbmessers
layout(location = 0) out vec4 oColor;
layout(location = 1) out vec4 oDiff;

const float PI = 3.14159265;

// ------------------------------------------------------------ Schatten
const vec2 POISSON[12] = vec2[](
    vec2(-0.326, -0.406), vec2(-0.840, -0.074), vec2(-0.696, 0.457), vec2(-0.203, 0.621),
    vec2(0.962, -0.195), vec2(0.473, -0.480), vec2(0.519, 0.767), vec2(0.185, -0.893),
    vec2(0.507, 0.064), vec2(0.896, 0.412), vec2(-0.322, -0.933), vec2(-0.792, -0.598));

float linDepth(float d) {
    float z = d * 2.0 - 1.0;
    return 2.0 * uLightNF.x * uLightNF.y / (uLightNF.y + uLightNF.x - z * (uLightNF.y - uLightNF.x));
}

// x = Sichtbarkeit 0…1, y = Weg des Lichts durch den Körper bis hierher (m)
vec2 shadow(vec3 p, float nl) {
    if (uShadowOn == 0) return vec2(1.0, 1.0);
    vec4 lp = uLightVP * vec4(p, 1.0);
    vec3 s = lp.xyz / lp.w * 0.5 + 0.5;
    if (s.x <= 0.0 || s.x >= 1.0 || s.y <= 0.0 || s.y >= 1.0 || s.z >= 1.0) return vec2(1.0, 1.0);
    float me = linDepth(s.z);
    float bias = 0.004 + 0.010 * (1.0 - clamp(nl, 0.0, 1.0));
    float vis = 0.0;
    float a = fract(sin(dot(gl_FragCoord.xy, vec2(12.9898, 78.233))) * 43758.5453) * 6.2832;
    mat2 rot = mat2(cos(a), sin(a), -sin(a), cos(a));
    for (int i = 0; i < 12; i++) {
        vec2 o = rot * POISSON[i] * uShadowSoft * uShadowTexel;
        float d = linDepth(texture(uShadow, s.xy + o).r);
        vis += me - bias <= d ? 1.0 : 0.0;
    }
    float first = linDepth(texture(uShadow, s.xy).r);
    return vec2(vis / 12.0, max(0.0, me - first));
}

// Weg des Lichts durch den Körper bis p (m), Mittel aus vier Abgriffen (weiche Ränder)
float lightPath(vec3 p) {
    vec4 lp = uLightVP * vec4(p, 1.0);
    vec3 s = lp.xyz / lp.w * 0.5 + 0.5;
    if (s.x <= 0.0 || s.x >= 1.0 || s.y <= 0.0 || s.y >= 1.0 || s.z >= 1.0) return 1.0;
    float me = linDepth(s.z), sum = 0.0;
    for (int i = 0; i < 4; i++)
        sum += max(0.0, me - linDepth(texture(uShadow, s.xy + POISSON[i * 3] * 1.5 * uShadowTexel).r));
    return sum * 0.25;
}

// ------------------------------------------------------------ Glanz
float D_GGX(float nh, float a) {
    float a2 = a * a;
    float d = nh * nh * (a2 - 1.0) + 1.0;
    return a2 / (PI * d * d);
}
float V_Smith(float nv, float nl, float a) {
    return 0.5 / max(nl * (nv * (1.0 - a) + a) + nv * (nl * (1.0 - a) + a), 1e-5);
}
vec3 skinSpec(vec3 n, vec3 l, vec3 v, float r1, float r2, float mixOil) {
    vec3 h = normalize(l + v);
    float nl = max(dot(n, l), 0.0), nv = max(dot(n, v), 1e-4), nh = max(dot(n, h), 0.0), vh = max(dot(v, h), 0.0);
    float a1 = r1 * r1, a2 = r2 * r2;
    float D = mix(D_GGX(nh, a1), D_GGX(nh, a2), mixOil);
    float V = mix(V_Smith(nv, nl, a1), V_Smith(nv, nl, a2), mixOil);
    float F = 0.028 + 0.972 * pow(1.0 - vh, 5.0);
    return vec3(D * V * F * nl);
}

// ------------------------------------------------------------ Durchleuchtung (Jimenez 2010)
vec3 T(float s) {   // s in mm
    float dd = -s * s;
    return vec3(0.233, 0.455, 0.649) * exp(dd / 0.0064) + vec3(0.1, 0.336, 0.344) * exp(dd / 0.0484)
         + vec3(0.118, 0.198, 0.0) * exp(dd / 0.187) + vec3(0.113, 0.007, 0.007) * exp(dd / 0.567)
         + vec3(0.358, 0.004, 0.0) * exp(dd / 1.99) + vec3(0.078, 0.0, 0.0) * exp(dd / 7.41);
}

vec3 toLinear(vec3 c) {
    return mix(c / 12.92, pow((c + 0.055) / 1.055, vec3(2.4)), step(vec3(0.04045), c));
}

// ------------------------------------------------------------ Detailnormale (Oberflächengradient)
vec3 perturb(vec3 N, vec3 P, vec2 uv, vec2 dHduv) {
    vec3 dPdx = dFdx(P), dPdy = dFdy(P);
    vec2 dUVdx = dFdx(uv), dUVdy = dFdy(uv);
    float dBs = dot(dHduv, dUVdx), dBt = dot(dHduv, dUVdy);
    vec3 R1 = cross(dPdy, N), R2 = cross(N, dPdx);
    float det = dot(dPdx, R1);
    if (abs(det) < 1e-14) return N;
    vec3 grad = sign(det) * (dBs * R1 + dBt * R2);
    return normalize(abs(det) * N - grad);
}

// ------------------------------------------------------------ Auge
const float CORNEA_C = 0.405, CORNEA_R = 0.635, IRIS_Z = 0.80, IRIS_R = 0.36, LIMBUS_Z = 0.925;

float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}
// Wertrauschen, in x periodisch mit Periode per (Winkel)
float pnoise(vec2 p, float per) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float x0 = mod(i.x, per), x1 = mod(i.x + 1.0, per);
    float a = hash12(vec2(x0, i.y)), b = hash12(vec2(x1, i.y));
    float c = hash12(vec2(x0, i.y + 1.0)), d = hash12(vec2(x1, i.y + 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

vec3 irisColor(vec2 ip, float pupil, float m, vec2 seed) {
    float r = length(ip);
    if (r < pupil) return vec3(0.003);
    float ang = atan(ip.y, ip.x) / 6.2831853 + 0.5;
    float t = clamp((r - pupil) / (1.0 - pupil), 0.0, 1.0);
    vec3 blue = vec3(0.11, 0.22, 0.36), green = vec3(0.14, 0.20, 0.08), hazel = vec3(0.25, 0.15, 0.05),
         brown = vec3(0.10, 0.045, 0.016), dark = vec3(0.035, 0.016, 0.007);
    vec3 base = m < 0.25 ? mix(blue, green, m / 0.25) : m < 0.5 ? mix(green, hazel, (m - 0.25) / 0.25)
              : m < 0.75 ? mix(hazel, brown, (m - 0.5) / 0.25) : mix(brown, dark, (m - 0.75) / 0.25);
    vec2 so = seed * 37.0;
    float fib = 0.6 * pnoise(vec2(ang * 96.0, t * 5.0 + so.y), 96.0) + 0.4 * pnoise(vec2(ang * 240.0, t * 13.0 + so.x), 240.0);
    vec3 c = base * (0.55 + 0.9 * fib);
    // Krause: wärmerer, hellerer Ring um die Pupille, gewellt
    float rc = 0.30 + 0.06 * (pnoise(vec2(ang * 14.0, so.x), 14.0) - 0.5);
    float col = exp(-pow((t - rc) / 0.07, 2.0));
    c = mix(c, base * vec3(1.7, 1.45, 0.95) + vec3(0.05, 0.035, 0.0), col * 0.55);
    c = mix(c * 1.15 + vec3(0.02, 0.015, 0.0) * (1.0 - m), c, smoothstep(0.0, rc, t));
    // Krypten: dunkle Mulden zwischen Krause und Rand
    float cr = smoothstep(0.66, 0.82, pnoise(vec2(ang * 30.0, t * 4.0 + so.y * 3.0), 30.0))
             * smoothstep(rc, rc + 0.12, t) * (1.0 - smoothstep(0.75, 0.92, t));
    c *= 1.0 - 0.6 * cr;
    // Pupillensaum, Limbusring
    c = mix(vec3(0.04, 0.025, 0.012), c, smoothstep(0.0, 0.05, t));
    c *= mix(1.0, 0.22, smoothstep(0.80, 1.0, t));
    return c;
}

vec3 scleraColor(vec3 q, vec2 seed) {
    vec3 c = vec3(0.68, 0.63, 0.59);
    float corner = smoothstep(0.45, 0.95, abs(q.x));
    c = mix(c, vec3(0.78, 0.55, 0.50), 0.45 * corner);
    // Adern: dünne, gebrochene Linien, nach außen dichter
    float a = atan(q.y, q.x) / 6.2831853 + 0.5;
    float th = acos(clamp(q.z, -1.0, 1.0));
    float vein = pnoise(vec2(a * 40.0 + seed.x * 20.0, th * 9.0), 40.0);
    vein = smoothstep(0.47, 0.5, vein) * (1.0 - smoothstep(0.5, 0.53, vein));
    c = mix(c, vec3(0.62, 0.20, 0.18), vein * 0.5 * smoothstep(0.45, 1.0, th) * (0.4 + 0.6 * corner));
    return c;
}

vec3 envRefl(vec3 rw) {
    // dunkle Umgebung mit Bernstein und Türkis wie die Bühne (bis portrait in Phase 9 echte Lichter liefert)
    float up = clamp(rw.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 c = mix(vec3(0.006, 0.008, 0.010), vec3(0.03, 0.045, 0.055), up);
    if (uEnvN == 0) {
        c += vec3(0.08, 0.05, 0.02) * pow(max(dot(rw, normalize(vec3(-0.6, 0.2, 0.75))), 0.0), 24.0);
        c += vec3(0.02, 0.07, 0.08) * pow(max(dot(rw, normalize(vec3(0.7, 0.3, -0.6))), 0.0), 24.0);
    }
    for (int i = 0; i < uEnvN; i++) {
        float cr = uEnvCos[i];
        float x = dot(rw, uEnvDir[i]);
        float edge = (1.0 - cr) * 0.6 + 2e-5;
        c += uEnvCol[i] * smoothstep(cr - edge, cr + edge, x);
    }
    return c;
}

void shadeEye(vec3 n, vec3 v, vec3 lk, vec3 key, vec3 lr, vec3 rim, float dk, float dr) {
    int e = distance(vWorld, uEyeC[0]) < distance(vWorld, uEyeC[1]) ? 0 : 1;
    mat3 M = mat3(uEyeX[e], uEyeY[e], uEyeZ[e]);
    mat3 Mt = transpose(M);
    float R = uEyeR[e];
    vec3 q = Mt * (vWorld - uEyeC[e]) / R;
    vec3 o = Mt * (uEye - uEyeC[e]) / R;
    vec3 d = normalize(q - o);
    vec3 cc = vec3(0.0, 0.0, CORNEA_C);
    vec3 oc = o - cc;
    float bq = dot(oc, d), cq = dot(oc, oc) - CORNEA_R * CORNEA_R, disc = bq * bq - cq;
    bool corneaHit = false;
    vec3 h = q;
    if (disc > 0.0) {
        float t = -bq - sqrt(disc);
        h = o + t * d;
        corneaHit = t > 0.0 && h.z > LIMBUS_Z;
    }
    vec3 nl = corneaHit ? normalize(h - cc) : normalize(q);
    vec3 nw = normalize(M * nl);
    vec2 sv = shadow(vWorld, dot(nw, lk));
    // Schatten der Lider und Wimpern: oben stärker, in den Winkeln
    // (sichtbar ist etwa |y| < 0,35, |x| < 0,7 der Kugel)
    float ao = 1.0 - 0.55 * smoothstep(-0.02, 0.34, q.y) - 0.22 * smoothstep(-0.08, -0.32, q.y) - 0.35 * smoothstep(0.32, 0.68, abs(q.x));
    ao = clamp(ao, 0.15, 1.0);
    vec3 alb;
    vec3 ndiff = nw;
    float wrap = 0.35;
    if (corneaHit) {
        vec3 d2 = refract(d, nl, 1.0 / 1.376);
        float t2 = (IRIS_Z - h.z) / min(d2.z, -1e-3);
        vec3 ip = h + t2 * d2;
        vec2 iuv = ip.xy / IRIS_R;
        if (length(iuv) < 1.0) {
            alb = irisColor(iuv, uIris.y, uIris.x, uIris.zw);
            ndiff = normalize(M * vec3(0.0, 0.0, 1.0) + 0.6 * nw);
            wrap = 0.2;
        } else {
            alb = scleraColor(normalize(vec3(ip.xy, sqrt(max(0.0, 1.0 - dot(ip.xy, ip.xy))))), uIris.zw) * 0.85;
        }
    } else {
        alb = scleraColor(q, uIris.zw);
    }
    float dkey = max((dot(ndiff, lk) + wrap) / (1.0 + wrap), 0.0) * sv.x;
    float drim = max((dot(ndiff, lr) + wrap) / (1.0 + wrap), 0.0);
    vec3 diffuse = alb * (key * dkey + rim * drim + uFillCol * 1.2) * ao;
    // Glanz: Hornhaut sehr glatt, Lederhaut nass
    // Rauheit der Hornhaut etwas größer als die Fläche, damit die Lichter als Reflexe sichtbar bleiben
    float rough = corneaHit ? 0.06 : 0.2;
    vec3 hk = normalize(lk + v), hr = normalize(lr + v);
    float nv = max(dot(nw, v), 1e-4);
    float a = rough * rough;
    // Lichter als kleine Kugeln (~4 cm, Karis 2013): die Verteilung wird etwas breiter, der Reflex bleibt sichtbar
    float ak = min(1.0, a + 0.02 / dk), ar = min(1.0, a + 0.02 / dr);
    float Fv = 0.025 + 0.975 * pow(1.0 - nv, 5.0);
    vec3 spec = key * sv.x * D_GGX(max(dot(nw, hk), 0.0), ak) * V_Smith(nv, max(dot(nw, lk), 0.0), a) * (0.025 + 0.975 * pow(1.0 - max(dot(v, hk), 0.0), 5.0)) * max(dot(nw, lk), 0.0)
              + rim * D_GGX(max(dot(nw, hr), 0.0), ar) * V_Smith(nv, max(dot(nw, lr), 0.0), a) * (0.025 + 0.975 * pow(1.0 - max(dot(v, hr), 0.0), 5.0)) * max(dot(nw, lr), 0.0);
    spec += envRefl(reflect(-v, nw)) * Fv * 6.0 * uGain;
    oColor = vec4(diffuse + spec * mix(0.6, 1.0, ao), 1.0);
    if (uDebug == 15) oColor = vec4(vec3(ao), 1.0);
    if (uDebug == 16) oColor = vec4(spec * 4.0, 1.0);
    if (uDebug == 17) oColor = vec4(q * 0.5 + 0.5, 1.0);
    if (uDebug == 18) oColor = vec4(vec3(sv.x), 1.0);
    if (uDebug == 20) oColor = vec4(vec3(pow(max(dot(nw, hk), 0.0), 60.0)), 1.0);
    if (uDebug == 21) oColor = vec4(nw * 0.5 + 0.5, 1.0);
    if (uDebug == 22) oColor = vec4(hk * 0.5 + 0.5, 1.0);
    if (uDebug == 19) oColor = vec4(corneaHit ? 1.0 : 0.0, max(dot(nw, hk), 0.0) > 0.995 ? 1.0 : 0.0, 0.0, 1.0);
    oDiff = vec4(0.0);
}

// Wasser: GGX mit Fresnel für n = 1,33 (F0 = 0,02)
vec3 waterSpec(vec3 n, vec3 l, vec3 v, float r) {
    vec3 h = normalize(l + v);
    float nl = max(dot(n, l), 0.0), nv = max(dot(n, v), 1e-4), nh = max(dot(n, h), 0.0), vh = max(dot(v, h), 0.0);
    float a = r * r;
    float F = 0.02 + 0.98 * pow(1.0 - vh, 5.0);
    return vec3(D_GGX(nh, a) * V_Smith(nv, nl, a) * F * nl);
}

// ---------------- Stoffe (Phase 10): Bindung als Höhe im UV-Raum, Wasserperlen als Halbkugeln
float weaveH(vec2 uv, float kind, float sc) {
    vec2 q = uv * sc;
    if (kind < 0.5) return 0.0;
    if (kind < 1.5) {                                   // Strick: Maschen als V, Reihen versetzt
        vec2 c = vec2(q.x, q.y * 1.35);
        float x = fract(c.x) - 0.5;
        float y = fract(c.y + abs(x) * 0.9);
        float loop = smoothstep(0.0, 0.35, y) * smoothstep(1.0, 0.6, y);
        return loop * (1.0 - 1.6 * x * x * 4.0 * 0.25) + 0.15 * hash12(floor(c) + 3.7);
    }
    if (kind < 2.5) return 0.5 + 0.5 * sin(6.2831853 * (q.x + q.y)) * 0.8 + 0.2 * hash12(floor(q * 4.0)); // Köper
    if (kind < 3.5) {                                   // Leinwand: Kette und Schuss, ungleichmäßig
        float a = sin(6.2831853 * q.x), b = sin(6.2831853 * q.y);
        return 0.5 + 0.5 * (a * b) + 0.25 * hash12(floor(q * vec2(1.0, 7.0)));
    }
    vec2 i = floor(q * 3.0), f = fract(q * 3.0);        // Narben (Leder): Zellen
    float d = 1.0;
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        vec2 o = vec2(x, y);
        vec2 r = o + vec2(hash12(i + o), hash12(i + o + 17.3)) - f;
        d = min(d, dot(r, r));
    }
    return sqrt(d);
}

// Perlen auf dem Stoff: Zellen im UV-Raum, besetzt nach Nässe; Höhe (Halbkugel 0…1) und Maske
vec2 beads(vec2 uv, float amount) {
    if (amount <= 0.001) return vec2(0.0);
    vec2 q = uv * 260.0;
    vec2 i = floor(q), f = fract(q);
    float h = 0.0, m = 0.0;
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        vec2 o = vec2(x, y);
        float r0 = hash12(i + o + 41.0);
        if (r0 > amount) continue;
        vec2 c = o + 0.25 + 0.5 * vec2(hash12(i + o + 7.0), hash12(i + o + 13.0));
        float rad = 0.18 + 0.30 * hash12(i + o + 29.0);
        float d = length(f - c) / rad;
        if (d < 1.0) { float b = sqrt(1.0 - d * d); if (b > h) { h = b; m = 1.0; } }
    }
    return vec2(h, m);
}

// Normale aus einer Höhe (m) über den Oberflächengradienten (ohne Tangenten, Mikkelsen 2010)
vec3 bumpN(vec3 n, float h) {
    vec3 dpdx = dFdx(vWorld), dpdy = dFdy(vWorld);
    float hx = dFdx(h), hy = dFdy(h);
    vec3 r1 = cross(dpdy, n), r2 = cross(n, dpdx);
    float det = dot(dpdx, r1);
    if (abs(det) < 1e-14) return n;
    vec3 g = sign(det) * (hx * r1 + hy * r2);
    return normalize(abs(det) * n - g);
}

float blinn(vec3 n, vec3 l, vec3 v, float p) {
    vec3 h = normalize(l + v);
    return pow(max(dot(n, h), 0.0), p) * (p + 8.0) / 25.13;
}

uniform vec2 uDither;          // Rasterblende der Detailstufen (Phase 12): sichtbar im Fenster [x, y)
bool ditherOut() {
    int x = int(mod(gl_FragCoord.x, 4.0)), y = int(mod(gl_FragCoord.y, 4.0));
    int m[16] = int[](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);
    float b = (float(m[y * 4 + x]) + 0.5) / 16.0;
    return b < uDither.x || b >= uDither.y;
}
void main() {
    if (ditherOut()) discard;
    vec3 n = normalize(vNrm);
    vec3 v = normalize(uEye - vWorld);
    vec3 lk = uKeyPos - vWorld; float dk = length(lk); lk /= dk;
    float cone = smoothstep(uKeyCone.y, uKeyCone.x, dot(-lk, normalize(uKeyDir)));
    vec3 key = uKeyCol * cone / (dk * dk);
    vec3 lr = uRimPos - vWorld; float dr = length(lr); lr /= dr;
    vec3 rim = uRimCol / (dr * dr);

    if (uMode == 2) {
        shadeEye(n, v, lk, key, lr, rim, dk, dr);
        return;
    }

    if (uMode == 3) {
        // Zähne, Zahnfleisch, Zunge: Tiefe im Mund dunkelt ab, offener Mund lässt mehr Licht hinein
        vec4 base = uBaseColor;
        if (uHasTex == 1) base *= texture(uTex, vUv);
        if (uAlphaMode == 1 && base.a < uAlphaCutoff) discard;
        if (uAlphaMode == 2 && base.a < 0.5) discard;
        if (dot(n, v) < 0.0) n = -n;
        float depth = vMaskC.y;
        float ao = mix(1.0, 0.05, clamp(depth * (1.7 - 1.3 * uMouthOpen), 0.0, 1.0));
        ao *= mix(0.7, 1.0, uMouthOpen);
        vec3 alb = min(base.rgb, vec3(0.72)) * vec3(0.95, 0.91, 0.84);
        float nlk = dot(n, lk);
        float vis = shadow(vWorld, nlk).x;
        float dkey = max((nlk + 0.3) / 1.3, 0.0) * vis;
        float drim = max((dot(n, lr) + 0.3) / 1.3, 0.0);
        vec3 c = alb * (key * dkey + rim * drim + uFillCol * 0.6) * ao;
        c += 0.25 * ao * (key * blinn(n, lk, v, 60.0) * vis + rim * blinn(n, lr, v, 60.0));
        oColor = vec4(c, 1.0);
        oDiff = vec4(0.0);
        return;
    }

    if (uMode == 0) {
        vec4 base = uBaseColor;
        if (uHasTex == 1) base *= texture(uTex, vUv);
        bool fabricOn = uCloth == 1 && uFab1.w >= 0.0;      // Stoffe (Phase 10) sind dicht: Alpha der Textur gilt nicht
        if (!fabricOn && uAlphaMode == 1 && base.a < uAlphaCutoff) discard;
        if (!fabricOn && uAlphaMode == 2 && base.a < 0.5) discard;
        if (dot(n, v) < 0.0) n = -n;
        float nlk = dot(n, lk);
        float vis = shadow(vWorld, nlk).x;
        float dkey = max((nlk + uWrap) / (1.0 + uWrap), 0.0) * vis;
        float drim = max((dot(n, lr) + uWrap) / (1.0 + uWrap), 0.0);
        // nasser Stoff: dunkler (Wasser in den Fasern), Schultern und die Seite zum Regen zuerst
        float wz = vWorld.y > uZoneY.y ? uWet.z : uWet.w;
        float expo = clamp(0.25 + 0.9 * dot(n, uRainFrom), 0.1, 1.0);
        float wc = uCloth == 1 ? clamp(wz * (0.45 + 0.9 * expo) * 1.15 - 0.1, 0.0, 1.0) : 0.0;
        if (uCloth == 1 && uFab1.w >= 0.0) {
            // ---- Stoff mit Bindung, Flaum, Nässe und Perlen (Phase 10)
            vec3 mean = uHasTex == 1 ? textureLod(uTex, vec2(0.5), 12.0).rgb * uBaseColor.rgb : vec3(0.5);
            float lt = dot(base.rgb, vec3(0.2126, 0.7152, 0.0722)) / max(dot(mean, vec3(0.2126, 0.7152, 0.0722)), 0.02);
            // dichte Stoffe (ohne Bindung) einfarbig, sonst die Struktur der Textur
            vec3 alb = uFabCol * (uFab0.z < 0.5 ? 1.0 : clamp(mix(1.0, lt, 0.6), 0.3, 1.7));
            float rough = uFab0.x, sheen = uFab0.y;
            float absorb = uFab1.x, beading = uFab1.y, wgloss = uFab1.z;
            // Muster blendet aus, wenn es kleiner als ein Bildpunkt wird
            vec2 fw = fwidth(vUv * uFab0.w);
            float amp = (uFab0.z > 3.5 ? 0.0004 : (uFab0.z < 1.5 ? 0.0005 : 0.0007)) * (1.0 - smoothstep(0.25, 0.7, max(fw.x, fw.y)));
            vec2 bd = beads(vUv, clamp(wc * beading * 0.3, 0.0, 0.35));
            vec2 fb = fwidth(vUv * 260.0);
            float bAmp = 0.0008 * (1.0 - smoothstep(0.4, 1.0, max(fb.x, fb.y)));
            float h = amp * weaveH(vUv, uFab0.z, uFab0.w) + bAmp * bd.x;
            vec3 nb = bumpN(n, h);
            // nasser Stoff: saugt und dunkelt, oder bleibt außen nass (Film) und perlt
            alb *= 1.0 - 0.55 * wc * absorb;
            float film = wc * (1.0 - absorb) * wgloss;
            float nlk2 = dot(nb, lk);
            float vis2 = shadow(vWorld, nlk2).x;
            float wrap = 0.35;
            float dk2 = max((nlk2 + wrap) / (1.0 + wrap), 0.0) * vis2;
            float dr2 = max((dot(nb, lr) + wrap) / (1.0 + wrap), 0.0);
            float nv = max(dot(nb, v), 0.0);
            vec3 c = alb * (key * dk2 + rim * dr2 + uFillCol * (0.6 + 0.4 * nb.y));
            // Flaum: leuchtet im Streiflicht am Rand
            float fz = pow(1.0 - nv, 3.0);
            c += sheen * fz * mix(alb * 3.0, vec3(0.08), 0.3) * (key * vis2 * smoothstep(-0.4, 0.6, nlk2) + rim * 0.9 + uFillCol * 3.0);
            // Glanz der Faser
            float shine = mix(260.0, 6.0, rough), sAmt = mix(0.22, 0.02, rough);
            c += sAmt * (key * blinn(nb, lk, v, shine) * vis2 + rim * blinn(nb, lr, v, shine));
            // Wasser: Film und Perlen spiegeln Lichter und Umgebung
            float wet = clamp(film * 0.5 + bd.y * 0.6, 0.0, 1.0);
            if (wet > 0.0) {
                float fe = 0.02 + 0.98 * pow(1.0 - nv, 5.0);
                c += wet * (key * blinn(nb, lk, v, 400.0) * vis2 * 1.2 + rim * blinn(nb, lr, v, 400.0) * 1.2);
                c += wet * fe * envRefl(reflect(-v, nb)) * uGain * 4.0;
            }
            oColor = vec4(c, 1.0);
            oDiff = vec4(0.0);
            return;
        }
        base.rgb *= 1.0 - 0.45 * wc;
        vec3 c = base.rgb * (key * dkey + rim * drim + uFillCol * (0.6 + 0.4 * n.y));
        c += uSpec * (key * blinn(n, lk, v, uShine) * vis + rim * blinn(n, lr, v, uShine));
        c += 0.05 * wc * (key * blinn(n, lk, v, 30.0) * vis + rim * blinn(n, lr, v, 30.0));
        float f = pow(1.0 - max(dot(n, v), 0.0), 4.0);
        c += f * rim * 0.06;
        oColor = vec4(c, 1.0);
        oDiff = vec4(0.0);
        return;
    }

    // ---------------- Haut
    if (uDebug == 12) { oColor = vec4(uSkinP.w, uSkinP.z, uSkinQ.w, 1.0); oDiff = vec4(0.0); return; }
    if (uDebug >= 9 && uDebug <= 10) {
        vec2 q = shadow(vWorld, dot(n, lk));
        oColor = vec4(uDebug == 9 ? vec3(q.x) : vec3(clamp(q.y / 0.05, 0.0, 1.0), 0.0, clamp(1.0 - q.y / 0.01, 0.0, 1.0)), 1.0);
        oDiff = vec4(0.0);
        return;
    }
    if (uDebug > 0 && uDebug <= 8) {
        float z = uDebug <= 4 ? vMaskA[uDebug - 1] : vMaskB[uDebug - 5];
        if (uDebug == 1) z = clamp(1.0 - z / 0.02, 0.0, 1.0);
        oColor = vec4(vec3(z, z * 0.3, 0.05) + 0.02, 1.0);
        oDiff = vec4(0.0);
        return;
    }
    float pores = uSkinP.x, age = uSkinP.y, freck = uSkinP.z, beard = uSkinP.w;
    float oil = uSkinQ.x, spots = uSkinQ.y, redness = uSkinQ.z, melanin = uSkinQ.w;
    vec2 uvD = vUv * uTile + uSeedOfs;
    vec4 A = texture(uDetA, uvD);
    vec4 B = texture(uDetB, uvD);
    float lip = vMaskB.z;
    // Albedo: Muster der Grundhäute mal Grundton
    vec3 st = vec3(0.0);
    for (int i = 0; i < 6; i++)
        if (uTexW[i] > 0.0) st += uTexW[i] * toLinear(texture(uSkinTex, vec3(vUv, float(i))).rgb) * uTexInvMean[i];
    vec3 alb = uTone * st;
    // Marmorierung und Rötung
    float mot = texture(uDetB, uvD * 0.2 + vec2(0.37, 0.11)).a;
    float red = vMaskA.z * (0.3 + 1.4 * redness) * (0.55 + 0.9 * mot);
    red += uCold * (1.1 * vMaskA.z + 0.9 * vMaskB.w) * (0.7 + 0.6 * mot);       // Kälte: Nase, Wangen, Ohren
    alb *= mix(vec3(1.0), vec3(1.14, 0.82, 0.84), clamp(red * 0.42, 0.0, 0.6) * (1.0 - 0.6 * melanin));
    // Sommersprossen (grobe Kachel)
    float fF = texture(uDetB, uvD / 7.0 + vec2(0.53, 0.29)).g;
    float thr = 1.0 - 0.62 * freck * vMaskA.w;
    float fr = smoothstep(thr, thr + 0.07, fF) * step(0.005, freck);
    alb *= mix(vec3(1.0), vec3(0.62, 0.47, 0.36), 0.8 * fr * (1.0 - 0.7 * melanin));
    // Altersflecken (sehr grobe Kachel)
    float sF = texture(uDetB, uvD / 23.0 + vec2(0.19, 0.71)).g;
    float sthr = 1.0 - 0.35 * spots * max(vMaskA.w, 0.3);
    alb *= mix(vec3(1.0), vec3(0.72, 0.6, 0.5), 0.6 * smoothstep(sthr, sthr + 0.05, sF) * step(0.005, spots));
    // Kälte: Lippen bläulich
    alb = mix(alb, alb * vec3(0.78, 0.74, 0.98), 0.45 * uCold * lip);
    // Bartstoppeln (Haarbälge in halber Dichte) und Schatten
    float bz = vMaskA.y * beard;
    float dots = smoothstep(0.3, 0.75, texture(uDetB, uvD / 2.5 + vec2(0.71, 0.43)).b);
    float bs = bz * 1.2 * (0.75 + 0.25 * dots);
    alb *= mix(vec3(1.0), vec3(0.15, 0.15, 0.19), clamp(bs, 0.0, 0.85));
    // Porenhöhlen etwas dunkler
    float cav = B.r;
    alb *= 1.0 - 0.10 * cav * pores;
    // Mundinneres: dunkel, je weiter innen und je geschlossener der Mund
    float mouthIn = vMaskC.y;
    alb *= mix(1.0, 0.12, clamp(mouthIn * (1.3 - 0.7 * uMouthOpen), 0.0, 1.0));
    alb = clamp(alb, 0.0, 0.95);

    if (uDebug == 13) { oColor = vec4(alb, 1.0); oDiff = vec4(0.0); return; }
    if (uDebug == 14) { oColor = vec4(bz, bs, dots, 1.0); oDiff = vec4(0.0); return; }
    // Detailnormale: Poren und Fältchen (Höhe in m je UV-Einheit)
    float poreAmp = 4.5e-5 * (0.3 + 1.4 * pores) * (1.0 - 0.7 * lip) * (1.0 - 0.6 * bz);
    float wrAmp = 1.6e-5 * (0.4 + 2.2 * age) * (1.0 + 2.5 * vMaskB.y * age);
    vec2 g = ((A.rg * 2.0 - 1.0) * poreAmp + (A.ba * 2.0 - 1.0) * wrAmp) * uSlope * uTile;
    // Nässe (Phase 8): Wasserfilm je Zone, Perlen und Spuren aus der Tropfenkarte
    float headM = smoothstep(uZoneY.x - 0.03, uZoneY.x + 0.02, vWorld.y);
    float film = mix(uWet.z * 0.8, uWet.x, headM);
    float drop = 0.0, trail = 0.0, ring = 0.0;
    vec2 gDrop = vec2(0.0);
    if (uDropOn == 1 && headM > 0.01) {
        vec2 ext = uDropRect.zw - uDropRect.xy;
        vec2 lq = (vUv - uDropRect.xy) / ext;
        if (all(greaterThan(lq, vec2(0.0))) && all(lessThan(lq, vec2(1.0)))) {
            float t = uDropTexel;
            float h = texture(uDropH, lq).r;
            float hx = texture(uDropH, lq + vec2(t, 0.0)).r - texture(uDropH, lq - vec2(t, 0.0)).r;
            float hy = texture(uDropH, lq + vec2(0.0, t)).r - texture(uDropH, lq - vec2(0.0, t)).r;
            gDrop = vec2(hx, hy) / (2.0 * t) / ext * 0.001;   // m je UV-Einheit
            drop = smoothstep(0.01, 0.06, h);
            ring = drop * (1.0 - smoothstep(0.06, 0.25, h));   // Rand der Perle: Licht wird weggebrochen
            trail = texture(uDropT, lq).r;
        }
    }
    float wetL = clamp(film * 0.85 + 0.75 * trail + drop, 0.0, 1.0);
    alb *= (1.0 - 0.10 * wetL) * (1.0 - 0.45 * ring) * (1.0 - 0.12 * drop);  // nasse Haut wird etwas dunkler, Perlen haben einen dunklen Rand
    g = mix(g, g * 0.3, max(drop, 0.6 * trail));
    vec3 nd = perturb(n, vWorld, vUv, g);
    vec3 ndW = drop > 0.0 ? perturb(n, vWorld, vUv, g + gDrop) : nd;   // Oberfläche des Wassers

    // Rauheit
    float r1 = clamp(0.46 + 0.10 * age + 0.18 * bz - 0.14 * lip, 0.25, 0.75);
    float r2 = 0.20;
    // Lidrand: Tränenfilm, sehr glatt und nass
    float wet = vMaskC.x;
    float mixOil = clamp(0.08 + 0.5 * oil * (0.35 + 0.65 * vMaskB.x), 0.0, 0.6) + 0.18 * lip;
    mixOil = mix(mixOil, 1.0, wet);
    r2 = mix(r2, 0.08, wet);
    float specOcc = 1.0 - 0.45 * cav * pores;

    // Licht
    float nlk = dot(nd, lk), nlr = dot(nd, lr);
    vec2 sh = shadow(vWorld, dot(n, lk));
    vec3 E;
    if (uLow == 1) {
        float curv = clamp(length(fwidth(n)) / max(length(fwidth(vWorld)), 1e-6) * 0.004, 0.0, 1.0);
        vec3 dk = texture(uLut, vec2(nlk * 0.5 + 0.5, curv)).rgb;
        vec3 drr = texture(uLut, vec2(nlr * 0.5 + 0.5, curv)).rgb;
        E = key * dk * mix(sh.x, 1.0, 0.15 * (1.0 - sh.x) * float(nlk > 0.0)) + rim * drr;
    } else {
        E = key * max(nlk, 0.0) * sh.x + rim * max(nlr, 0.0);
    }
    E += uFillCol * (0.6 + 0.4 * nd.y);
    // Durchleuchtung durch das Führungslicht: Weg aus der Schattenkarte (von einem 4 mm nach innen
    // versetzten Punkt) und Eckendicke müssen beide dünn sein; so leuchten Schatten mit Luftspalt
    // (unter Nase und Lippe) nicht. Nur wo das Licht nicht direkt trifft. Ohne Karte: Eckendicke.
    float thick = vMaskA.x;
    float pathK = thick, shadowed = 1.0;
    if (uShadowOn == 1) {
        pathK = min(max(lightPath(vWorld - n * 0.004), 0.35 * thick), 0.25);
        shadowed = 1.0 - sh.x;
    }
    vec3 trans = T(pathK * 1000.0 * uTrans.x) * key * clamp(0.3 + dot(-n, lk), 0.0, 1.0) * shadowed * (1.0 - mouthIn);
    vec3 diffuse = alb * E + alb * trans * uTrans.y;
    specOcc *= 1.0 - 0.9 * mouthIn;
    vec3 spec = specOcc * (key * sh.x * skinSpec(nd, lk, v, r1, r2, mixOil) + rim * skinSpec(nd, lr, v, r1, r2, mixOil));
    // Wasser darüber: Film glatt (0,10), Perlen sehr glatt (0,05); in der Perle sammelt sich Licht
    if (wetL > 0.001) {
        float rw = mix(0.12, 0.05, drop);
        spec = mix(spec, spec * 0.6, wetL) + wetL * (key * sh.x * waterSpec(ndW, lk, v, rw) + rim * waterSpec(ndW, lr, v, rw)) * 1.4;
        // Spiegelung der Umgebung im Wasser (dunkle Bühne mit Bernstein und Türkis)
        float fe = 0.02 + 0.98 * pow(1.0 - max(dot(ndW, v), 0.0), 5.0);
        spec += wetL * fe * envRefl(reflect(-v, ndW)) * uGain * (2.0 + 6.0 * drop);
        // in der Perle sammelt sich Licht (Linse) auf der dem Licht abgewandten Seite
        float lens = clamp(dot(n, lk) - dot(ndW, lk), 0.0, 1.0);
        diffuse += drop * alb * key * sh.x * (0.15 + 1.2 * lens * lens) * max(dot(n, lk), 0.0);
    }

    if (uSplit == 1) {
        oColor = vec4(spec, 1.0);
        oDiff = vec4(diffuse, 0.5 + 0.5 * melanin);
    } else {
        oColor = vec4(diffuse + spec, 1.0);
        oDiff = vec4(0.0);
    }
}
