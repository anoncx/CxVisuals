#version 330

uniform sampler2D Sampler0;

in vec4 vertexColor;
in vec2 glyphUv;
flat in vec2 erode;

out vec4 fragColor;

float hash(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), f.x), mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), f.x), f.y);
}

void main() {
    float coverage = texture(Sampler0, glyphUv).a;
    float alpha = coverage * vertexColor.a;
    if (erode.x > 0.0) {
        vec2 p = glyphUv * 512.0 * 0.14 + erode.y * 1.618;
        p += (vec2(vnoise(p * 1.9 + 3.1), vnoise(p * 1.9 + 7.7)) - 0.5) * 2.2;
        float n = vnoise(p) * 0.62 + vnoise(p * 2.3 + 11.0) * 0.38;
        n = clamp((n - 0.2) / 0.6, 0.0, 1.0);
        float front = erode.x * 1.25;
        alpha *= smoothstep(front - 0.25, front, n);
    }
    if (alpha < 0.004) {
        discard;
    }
    fragColor = vec4(vertexColor.rgb, alpha);
}
