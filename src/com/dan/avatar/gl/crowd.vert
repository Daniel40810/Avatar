#version 430 core
// Ferne Detailstufen (Phase 12): Netz der Person aus LodMesh, Skinning mit Dual-Quaternionen im Scheitelshader
// (wie skin.comp), Gelenke aus dem Puffer der Menge ab uBone; Ort der Person als Versatz.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec3 aCol;
layout(location = 3) in ivec4 aJn;
layout(location = 4) in vec4 aWt;
layout(std430, binding = 0) readonly buffer DQ { float dq[]; };
uniform mat4 uViewProj;
uniform vec3 uOffset;
uniform int uBone;
out vec3 vWorld;
out vec3 vNrm;
out vec3 vCol;
vec3 qrot(vec4 q, vec3 v) { vec3 c = cross(q.xyz, v) + q.w * v; return v + 2.0 * cross(q.xyz, c); }
void main() {
    vec4 r = vec4(0.0), d = vec4(0.0);
    for (int k = 0; k < 4; k++) {
        float w = aWt[k];
        if (w == 0.0) continue;
        int j = (uBone + aJn[k]) * 8;
        vec4 q = vec4(dq[j], dq[j + 1], dq[j + 2], dq[j + 3]);
        if (dot(r, q) < 0.0) w = -w;
        r += w * q;
        d += w * vec4(dq[j + 4], dq[j + 5], dq[j + 6], dq[j + 7]);
    }
    float l = length(r);
    if (l < 1e-12) { r = vec4(0, 0, 0, 1); l = 1.0; }
    r /= l; d /= l;
    vec3 t = 2.0 * (r.w * d.xyz - d.w * r.xyz + cross(r.xyz, d.xyz));
    vWorld = qrot(r, aPos) + t + uOffset;
    vNrm = qrot(r, aNrm);
    vCol = aCol;
    gl_Position = uViewProj * vec4(vWorld, 1.0);
}
