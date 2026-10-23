#version 430 core
// Form der Blende: rund oder n Lamellen mit gerundeten Ecken; heller Rand (sphärische Abweichung),
// feine Ringe; weicher Rand von gut einem Bildpunkt. Ausgabe additiv.
in vec2 vQ;
in vec3 vCol;
in float vD;
in float vMirror;
uniform int uBlades;
uniform float uRound, uRot;
out vec4 oColor;
void main() {
    float r = length(vQ);
    float rr = r;
    if (uBlades >= 3) {
        float a = 6.2831853 / float(uBlades);
        float th = mod(atan(vQ.y, vQ.x) - uRot, a) - 0.5 * a;
        float poly = r * cos(th) / cos(0.5 * a);
        rr = mix(poly, r, uRound);
    }
    float e = max(1.6 / max(vD * 0.5, 1.0), 0.22);   // weicher Rand: gut ein Bildpunkt, bei großen Kreisen ein Fünftel (weiche Optik)
    float m = 1.0 - smoothstep(1.0 - e, 1.0, rr);
    if (m <= 0.0) discard;
    // weiche Optik (Apodisation): zur Mitte etwas heller, feine Ringe kaum sichtbar
    float shape = (1.25 - 0.45 * rr * rr) * (1.0 + 0.02 * sin(rr * 36.0));
    if (vMirror > 0.5) shape *= 0.55 + 0.45 * (1.0 - abs(vQ.y));
    oColor = vec4(vCol * m * shape, 0.0);
}
