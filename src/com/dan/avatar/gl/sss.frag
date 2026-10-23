#version 430 core
// Streuung im Bildraum, ein Durchgang des trennbaren Kerns (Jimenez u. a. 2015).
// Eingang: diffuses Hautlicht, a = 0 keine Haut, sonst 0,5 + 0,5 · Melanin (wählt zwischen dem Kern
// für helle und dunkle Haut). Schrittweite in Pixeln aus der Breite (mm), der Brennweite und der Tiefe.
// Folgt der Oberfläche: Proben mit großem Tiefensprung zählen wie die Mitte.
in vec2 vUv;
uniform sampler2D uColor;
uniform sampler2D uDepth;
uniform vec2 uDir;          // (1,0) oder (0,1)
uniform float uFocal;       // Pixel je Meter in 1 m Abstand
uniform vec2 uNearFar;
uniform float uWidth;       // Breite des Profils (1 = Jimenez)
uniform int uN;
uniform vec4 uKernel[25];
uniform vec4 uKernelDark[25];
uniform int uFinal;         // 1: zweiter Durchgang, ergibt nur die Haut (wird zum Bild addiert)
out vec4 oColor;

float lin(float d) {
    float z = d * 2.0 - 1.0;
    return 2.0 * uNearFar.x * uNearFar.y / (uNearFar.y + uNearFar.x - z * (uNearFar.y - uNearFar.x));
}

void main() {
    ivec2 px = ivec2(gl_FragCoord.xy);
    vec4 c = texelFetch(uColor, px, 0);
    if (c.a <= 0.0) { oColor = uFinal == 1 ? vec4(0.0) : vec4(c.rgb, 0.0); return; }
    float dark = clamp((c.a - 0.5) * 2.0, 0.0, 1.0);
    float d = lin(texelFetch(uDepth, px, 0).r);
    float pxPerMm = uWidth * 1e-3 * uFocal / d;
    vec3 acc = c.rgb * mix(uKernel[0].rgb, uKernelDark[0].rgb, dark);
    vec2 size = vec2(textureSize(uColor, 0));
    for (int i = 1; i < uN; i++) {
        vec4 k = mix(uKernel[i], uKernelDark[i], dark);
        vec2 uv = (gl_FragCoord.xy + uDir * k.w * pxPerMm) / size;
        vec4 s = texture(uColor, uv);
        vec3 col = s.a > 0.0 ? s.rgb : c.rgb;
        float ds = lin(texture(uDepth, uv).r);
        float t = clamp(abs(ds - d) * 300.0 / uWidth, 0.0, 1.0);   // 3 mm Sprung: ganz die Mitte
        col = mix(col, c.rgb, t);
        acc += k.rgb * col;
    }
    oColor = vec4(acc, c.a);
}
