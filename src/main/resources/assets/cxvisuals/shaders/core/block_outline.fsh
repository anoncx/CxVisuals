#version 330

layout(std140) uniform Outline {
    mat4 Unproject;
    vec4 Low;
    vec4 High;
    vec4 Knobs;
    vec4 Lens;
    vec4 First;
    vec4 Second;
};

uniform sampler2D DepthSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    vec4 point = Unproject * vec4(texCoord * 2.0 - 1.0, depth * Lens.x + Lens.y, 1.0);
    vec3 at = point.xyz / point.w;
    float range = length(at);
    float slack = 0.0015 + range * 0.001;
    vec3 below = at - Low.xyz;
    vec3 above = High.xyz - at;
    if (any(lessThan(below, vec3(-slack))) || any(lessThan(above, vec3(-slack)))) {
        discard;
    }
    vec3 gap = min(abs(below), abs(above));
    if (min(gap.x, min(gap.y, gap.z)) > slack) {
        discard;
    }

    vec2 rim;
    float facing;
    if (gap.x <= gap.y && gap.x <= gap.z) {
        rim = gap.yz;
        facing = at.x;
    } else if (gap.y <= gap.z) {
        rim = gap.xz;
        facing = at.y;
    } else {
        rim = gap.xy;
        facing = at.z;
    }
    float edge = min(rim.x, rim.y);
    float along = max(rim.x, rim.y);
    float soft = range * Low.w / max(abs(facing) / range, 0.2);
    float width = max(Knobs.x, soft * 0.75);
    float line = 1.0 - smoothstep(width - soft, width + soft, edge);
    float glow = exp(-edge / max(Knobs.x * 5.0, 1.0e-3)) * Knobs.y * 0.35;
    float fill = Knobs.z;

    float time = High.w;
    int style = int(Knobs.w);
    if (style == 1) {
        float keep = 1.0 - smoothstep(0.24, 0.3, along);
        line *= keep;
        glow *= keep;
    } else if (style == 2) {
        float tall = High.y - Low.y;
        float sweep = fract(time * 0.45) * (tall + 0.6) - 0.3;
        fill += exp(-abs(below.y - sweep) * 12.0) * 0.4;
    }

    vec3 tone = mix(First.rgb, Second.rgb, 0.5 + 0.5 * sin(dot(below, vec3(1.2)) - time * 1.5));
    fragColor = vec4(tone * (line + glow + fill), clamp(line * 0.9 + fill * 0.5, 0.0, 1.0));
}
