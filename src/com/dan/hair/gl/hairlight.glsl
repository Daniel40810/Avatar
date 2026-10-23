// Gemeinsames Licht für Haarkarten und einzelne Haare (wird vor hair.frag und strand.frag gesetzt).
// Haar-BSDF nach Marschner u. a. (2003) in der Näherung von Karis (2016, „Physically Based Hair
// Shading in Unreal“): R (Glanz an der Oberfläche, zur Wurzel verschoben), TT (durch das Haar,
// leuchtet im Gegenlicht), TRT (zweiter, farbiger Glanz, zur Spitze verschoben); dazu eine
// Mehrfachstreuung nach Kajiya-Kay. Selbstschatten als „tiefe Deckung“ aus der Schattenkarte:
// Durchlässigkeit exp(−Weg im Haar / Dichtelänge).
uniform vec3 uEye;
uniform vec3 uKeyPos;   uniform vec3 uKeyCol;   uniform vec3 uKeyDir; uniform vec2 uKeyCone;
uniform vec3 uRimPos;   uniform vec3 uRimCol;
uniform vec3 uFillCol;
uniform int uShadowOn;
uniform sampler2D uShadow;
uniform mat4 uLightVP;
uniform vec2 uLightNF;
uniform vec2 uShadowTexel;
uniform vec3 uHairAlb;      // lineare Albedo der Faser
uniform float uRough;       // 0,2 … 0,45
uniform float uShift;       // Neigung der Schuppen (rad), ~0,05

const float PI = 3.14159265;

float linDepth(float d) {
    float z = d * 2.0 - 1.0;
    return 2.0 * uLightNF.x * uLightNF.y / (uLightNF.y + uLightNF.x - z * (uLightNF.y - uLightNF.x));
}

// Durchlässigkeit für das Führungslicht: Weg im Haar (und Kopf) bis hierher
float hairShadow(vec3 p) {
    if (uShadowOn == 0) return 1.0;
    vec4 lp = uLightVP * vec4(p, 1.0);
    vec3 s = lp.xyz / lp.w * 0.5 + 0.5;
    if (s.x <= 0.0 || s.x >= 1.0 || s.y <= 0.0 || s.y >= 1.0 || s.z >= 1.0) return 1.0;
    float me = linDepth(s.z), acc = 0.0;
    // Kern je Bildpunkt gedreht (fest im Bild, also ruhig): aus Texel-Blöcken wird feines Korn
    float ign = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    float a = ign * 6.2831853;
    mat2 rot = mat2(cos(a), sin(a), -sin(a), cos(a));
    for (int i = -1; i <= 1; i++)
        for (int j = -1; j <= 1; j++) {
            vec2 o = rot * (vec2(i, j) + 0.5 * vec2(j, -i) * ign) * 2.2 * uShadowTexel;
            float d = linDepth(texture(uShadow, s.xy + o).r);
            acc += exp(-max(0.0, me - d - 0.003) / 0.015);
        }
    return acc / 9.0;
}

float Hair_g(float B, float th) { return exp(-0.5 * th * th / (B * B)) / (sqrt(2.0 * PI) * B); }
float Hair_F(float c) { float f0 = 0.046; return f0 + (1.0 - f0) * pow(1.0 - c, 5.0); }

vec3 hairBSDF(vec3 L, vec3 V, vec3 T, vec3 alb, float rough, float shift, float backlit) {
    float sl = clamp(dot(T, L), -1.0, 1.0), sv = clamp(dot(T, V), -1.0, 1.0);
    float cosThD = cos(0.5 * abs(asin(sv) - asin(sl)));
    vec3 Lp = L - sl * T, Vp = V - sv * T;
    float cosPhi = dot(Lp, Vp) * inversesqrt(dot(Lp, Lp) * dot(Vp, Vp) + 1e-4);
    float cosHalfPhi = sqrt(clamp(0.5 + 0.5 * cosPhi, 0.0, 1.0));
    float n_ = 1.19 / cosThD + 0.36 * cosThD;
    float r2 = rough * rough;
    float B0 = r2 + 0.02, B1 = r2 * 0.8 + 0.04, B2 = r2 * 2.0 + 0.02;
    vec3 S = vec3(0.0);
    // R
    float a0 = -2.0 * shift, sa = sin(a0), ca = cos(a0);
    float shiftR = 2.0 * sa * (ca * cosHalfPhi * sqrt(max(0.0, 1.0 - sv * sv)) + sa * sv);
    float Mp = Hair_g(B0 * sqrt(2.0) * max(cosHalfPhi, 0.1), sl + sv - shiftR);
    S += vec3(Mp * 0.25 * cosHalfPhi * Hair_F(sqrt(clamp(0.5 + 0.5 * dot(L, V), 0.0, 1.0))));
    // TT
    Mp = Hair_g(B1, sl + sv + shift);
    float a = 1.0 / n_;
    float h = cosHalfPhi * (1.0 + a * (0.6 - 0.8 * cosPhi));
    float f = Hair_F(cosThD * sqrt(clamp(1.0 - h * h, 0.0, 1.0)));
    vec3 Tp = pow(max(alb, vec3(1e-3)), vec3(0.5 * sqrt(clamp(1.0 - h * h * a * a, 0.0, 1.0)) / cosThD));
    S += 0.6 * Mp * exp(-3.65 * cosPhi - 3.98) * (1.0 - f) * (1.0 - f) * Tp * backlit;
    // TRT
    Mp = Hair_g(B2, sl + sv - 4.0 * shift);
    f = Hair_F(cosThD * 0.5);
    Tp = pow(max(alb, vec3(1e-3)), vec3(0.8 / cosThD));
    S += Mp * exp(17.0 * cosPhi - 16.78) * (1.0 - f) * (1.0 - f) * f * Tp;
    // Mehrfachstreuung (Kajiya-Kay, umgeschlagen)
    float kd = 1.0 - abs(sl);
    S += alb * (0.18 + 0.5 * kd) * (0.5 + 0.5 * dot(normalize(Lp + 1e-4), normalize(Vp + 1e-4))) / PI;
    return S;
}

vec3 hairShade(vec3 P, vec3 T, vec3 alb, float rough, float shift, float ao) {
    vec3 V = normalize(uEye - P);
    vec3 lk = uKeyPos - P; float dk = length(lk); lk /= dk;
    float cone = smoothstep(uKeyCone.y, uKeyCone.x, dot(-lk, normalize(uKeyDir)));
    vec3 key = uKeyCol * cone / (dk * dk);
    vec3 lr = uRimPos - P; float dr = length(lr); lr /= dr;
    vec3 rim = uRimCol / (dr * dr);
    float sh = hairShadow(P + lk * 0.008);      // zum Licht versetzt: keine Streifen durch Selbstschatten
    vec3 c = key * sh * hairBSDF(lk, V, T, alb, rough, shift, 1.0) * PI
           + rim * hairBSDF(lr, V, T, alb, rough, shift, 1.0) * PI * mix(0.4, 1.0, ao);
    c += uFillCol * alb * 1.3 * ao;
    return c;
}
