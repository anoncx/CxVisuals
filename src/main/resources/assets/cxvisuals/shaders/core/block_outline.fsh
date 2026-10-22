#version 330

layout(std140) uniform Outline {
    mat4 Unproject;
    vec4 Low;
    vec4 High;
    vec4 Knobs;
    vec4 Lens;
    vec4 First;
    vec4 Second;
    vec4 Pivot;
    vec4 Pop;
};

uniform sampler2D DepthSampler;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    float depth = texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r;
    vec4 point = Unproject * vec4(texCoord * 2.0 - 1.0, depth * Lens.x + Lens.y, 1.0);
    vec3 at = point.xyz / point.w;
    float range = length(at);
    vec3 view = at;
    float unit = 1.0;
    if (Pop.z > 0.0) {
        unit = Pop.z;
        vec3 offset = at - Pivot.xyz - vec3(0.0, Pop.w, 0.0);
        at = Pivot.xyz + vec3(Pop.x * offset.x - Pop.y * offset.z, offset.y, Pop.y * offset.x + Pop.x * offset.z) / unit;
        view = vec3(Pop.x * view.x - Pop.y * view.z, view.y, Pop.y * view.x + Pop.x * view.z);
    }
    float slack = 0.0015 + range * 0.001;
    float reach = slack / unit;
    vec3 below = at - Low.xyz;
    vec3 above = High.xyz - at;
    if (any(lessThan(below, vec3(-reach))) || any(lessThan(above, vec3(-reach)))) {
        discard;
    }
    below *= unit;
    above *= unit;
    vec3 gap = min(abs(below), abs(above));
    if (min(gap.x, min(gap.y, gap.z)) > slack) {
        discard;
    }

    vec2 rim;
    float facing;
    if (gap.x <= gap.y && gap.x <= gap.z) {
        rim = gap.yz;
        facing = view.x;
    } else if (gap.y <= gap.z) {
        rim = gap.xz;
        facing = view.y;
    } else {
        rim = gap.xy;
        facing = view.z;
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
        float tall = (High.y - Low.y) * unit;
        float sweep = fract(time * 0.45) * (tall + 0.6) - 0.3;
        fill += exp(-abs(below.y - sweep) * 12.0) * 0.4;
    }

    vec3 tone = mix(First.rgb, Second.rgb, 0.5 + 0.5 * sin(dot(below, vec3(1.2)) - time * 1.5));
    fragColor = vec4(tone * (line + glow + fill), clamp(line * 0.9 + fill * 0.5, 0.0, 1.0));
}
