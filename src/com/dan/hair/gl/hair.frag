// (hairlight.glsl steht davor)
// Haarkarten in zwei Durchgängen ohne Sortieren: 0 = deckend (Alpha ≥ 0,5, schreibt Tiefe),
// 1 = Saum (Alpha < 0,5, gemischt, ohne Tiefe schreiben). Ziel 1 (diffuses Hautlicht für die
// Streuung) wird unter dem Haar abgedeckt.
in vec3 vWorld;
in vec3 vNrm;
in vec2 vUv;
in float vFlex;
in vec3 vTan;
uniform sampler2D uTex;
uniform int uPass;
uniform int uAxis;          // UV-Achse entlang der Haare (0 u, 1 v)
uniform float uAxisSign;
uniform float uTexMean;     // mittlere Helligkeit der Karte
uniform float uGreyFrac;
uniform int uDebug;         // 1 Richtung, 2 Textur, 3 Selbstschatten (-Dhair.debug)
layout(location = 0) out vec4 oColor;
layout(location = 1) out vec4 oDiff;

uniform vec2 uDither;          // Rasterblende der Detailstufen (Phase 12): sichtbar im Fenster [x, y)
bool ditherOut() {
    int x = int(mod(gl_FragCoord.x, 4.0)), y = int(mod(gl_FragCoord.y, 4.0));
    int m[16] = int[](0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5);
    float b = (float(m[y * 4 + x]) + 0.5) / 16.0;
    return b < uDither.x || b >= uDither.y;
}
void main() {
    if (ditherOut()) discard;
    vec4 tx = texture(uTex, vUv);
    float a = tx.a;
    if (uPass == 0 && a < 0.5) discard;
    if (uPass == 1 && (a >= 0.5 || a < 0.12)) discard;   // ganz dünner Rand der Karte: weg (sonst Schleier)
    // Tangente aus den Bildableitungen: dP/du, dP/dv
    vec3 dpx = dFdx(vWorld), dpy = dFdy(vWorld);
    vec2 dux = dFdx(vUv), duy = dFdy(vUv);
    float det = dux.x * duy.y - duy.x * dux.y;
    vec3 dPdu = (dpx * duy.y - dpy * dux.y) / (abs(det) > 1e-12 ? det : 1e-12);
    vec3 dPdv = (dpy * dux.x - dpx * duy.x) / (abs(det) > 1e-12 ? det : 1e-12);
    vec3 T;
    if (dot(vTan, vTan) > 0.25) T = normalize(vTan);          // Richtung je Ecke (glatt über die Karte)
    else {
        T = normalize((uAxis == 0 ? dPdu : dPdv) * uAxisSign + 1e-6);
        if (any(isnan(T))) T = vec3(0.0, -1.0, 0.0);
    }
    // Strähnenvielfalt aus der Karte: Helligkeit, Grau je Strähne
    float lum = dot(tx.rgb, vec3(0.3, 0.55, 0.15)) / max(uTexMean, 0.05);
    // Vielfalt je feiner Zelle (≈ ein Haar breit in der Karte), nicht in breiten Bändern
    // Zellen quer zur Haarrichtung schmal, längs lang: Strähnen statt Kacheln
    vec2 cells = uAxis == 0 ? vec2(6.0, 600.0) : vec2(600.0, 6.0);
    float str = fract(sin(dot(floor(vUv * cells), vec2(12.9898, 78.233))) * 43758.5453);
    vec3 alb = uHairAlb * clamp(0.5 + 0.5 * lum, 0.35, 1.6);
    // graue Haare: Salz und Pfeffer; helle Fasern der Karte werden eher grau
    float gsel = clamp(str * 0.6 + 0.4 * clamp(lum - 0.5, 0.0, 1.0), 0.0, 1.0);
    alb = mix(alb, vec3(0.72, 0.71, 0.69), 0.85 * smoothstep(1.0 - uGreyFrac - 0.08, 1.0 - uGreyFrac + 0.08, gsel));
    float rough = uRough * (0.92 + 0.16 * str);
    // innen dunkler (wenig Licht dringt zwischen die Karten nahe der Kopfhaut)
    float ao = mix(0.45, 1.0, smoothstep(0.0, 0.35, vFlex));
    vec3 c = hairShade(vWorld, T, alb, rough, uShift * (0.9 + 0.2 * str), ao) * ao;
    if (uDebug == 1) c = T * 0.5 + 0.5;
    else if (uDebug == 2) c = tx.rgb;
    else if (uDebug == 3) c = vec3(hairShadow(vWorld + normalize(uKeyPos - vWorld) * 0.008));
    if (uPass == 0) {
        oColor = vec4(c, 1.0);
        oDiff = vec4(0.0);
    } else {
        float aa = smoothstep(0.12, 0.5, a);
        oColor = vec4(c, aa);
        oDiff = vec4(0.0, 0.0, 0.0, aa);
    }
}
