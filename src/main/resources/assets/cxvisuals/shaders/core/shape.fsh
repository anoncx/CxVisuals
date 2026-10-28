#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    mat4 TextureMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
};

layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 local;
layout(location = 2) flat in vec2 halfSize;
layout(location = 3) flat in float radius;
layout(location = 4) flat in float softness;
layout(location = 5) flat in float border;
layout(location = 6) flat in float fade;

layout(location = 0) out vec4 fragColor;

float roundBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - r;
}

vec3 rainbow(float hue) {
    return clamp(abs(fract(hue + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

void main() {
    float r = min(radius, min(halfSize.x, halfSize.y));
    float d = roundBox(local, halfSize, r);
    float aa = max(fwidth(d), 0.0001);
    float alpha;
    if (softness > 0.0) {
        alpha = 1.0 - smoothstep(-softness, softness, d);
        alpha *= alpha;
    } else {
        alpha = 1.0 - smoothstep(-aa * 0.5, aa * 0.5, d);
        if (fade > 0.0) {
            float line = smoothstep(-aa * 0.5, aa * 0.5, d + border);
            float glow = 1.0 - smoothstep(0.0, border * 3.0, -d);
            float lit = 1.0 - smoothstep(0.0, fade, local.y + halfSize.y);
            alpha *= max(line, glow * glow * 0.22) * lit;
        } else if (border > 0.0) {
            alpha *= smoothstep(-aa * 0.5, aa * 0.5, d + border);
        }
    }
    vec3 tone = vertexColor.rgb;
    if (border < -0.5) {
        vec2 at = clamp(local / (halfSize * 2.0) + 0.5, 0.0, 1.0);
        if (border < -2.0) {
            float inset = halfSize.y / (halfSize.x * 2.0);
            tone = rainbow(clamp((at.x - inset) / (1.0 - inset * 2.0), 0.0, 0.9999));
        } else {
            tone = mix(vec3(1.0), vertexColor.rgb, at.x) * (1.0 - at.y);
        }
    }
    vec4 color = vec4(tone, vertexColor.a * alpha) * ColorModulator;
    if (color.a < 0.002) {
        discard;
    }
    fragColor = color;
}
