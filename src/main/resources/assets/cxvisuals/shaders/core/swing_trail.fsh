#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform Swing {
    mat4 ViewProj;
    vec4 First;
    vec4 Second;
    vec4 Look;
};

layout(location = 0) in float across;
layout(location = 1) in float arc;
layout(location = 2) in float along;
layout(location = 3) in float alpha;
layout(location = 4) in float ends;
layout(location = 5) in float tipEnds;
layout(location = 6) flat in float seed;

layout(location = 0) out vec4 fragColor;

const int GLOW = 0;
const int COMET = 1;
const int EMBER = 2;
const int PRISM = 3;

float sq(float v) {
    return v * v;
}

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), f.x), mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float weight = 0.5;
    for (int i = 0; i < 3; i++) {
        value += weight * vnoise(p);
        p = p * 2.03 + 17.1;
        weight *= 0.5;
    }
    return value;
}

vec3 hue(float h) {
    vec3 k = abs(fract(h + vec3(0.0, 2.0 / 3.0, 1.0 / 3.0)) * 6.0 - 3.0);
    return clamp(k - 1.0, 0.0, 1.0);
}

void main() {
    int style = int(Look.x + 0.5);
    float halo = Look.z;
    float spread = Look.w;
    float time = Second.a;
    float fresh = 1.0 - along;
    float lead = smoothstep(0.0, 0.16, along);
    float ahead = across < 0.0 ? 1.0 : 0.0;
    float behind = 1.0 - ahead;
    float depth = max(1.0 - across, 0.0);
    vec3 tint = mix(First.rgb, Second.rgb, along);
    float light = 0.0;
    float white = 0.0;

    if (style == GLOW) {
        float tail = pow(fresh, 1.8);
        float core = exp(-sq(across / (0.06 * spread)));
        float outer = ahead * exp(-sq(across / (halo * 0.42))) * 0.45;
        float body = behind * pow(depth, 14.0) * 0.45;
        light = (core * tipEnds + (outer + body) * lead * ends) * tail;
        white = core * 0.55;
    } else if (style == COMET) {
        float head = exp(-along * 9.0);
        float taper = pow(fresh, 2.2);
        float core = exp(-sq(across / (0.04 * spread * (1.0 + 2.2 * head)))) * pow(fresh, 1.6);
        float outer = ahead * exp(-sq(across / (halo * (0.35 + 0.35 * head)))) * (0.35 + 0.5 * head) * pow(fresh, 1.4) * lead;
        float body = behind * (1.0 - smoothstep(0.0, max(taper * 0.2, 0.02), across)) * (0.35 + 0.5 * head) * pow(fresh, 1.4) * lead;
        light = core * tipEnds + (outer + body) * ends;
        white = core * 0.85;
        tint = mix(First.rgb, Second.rgb, pow(along, 0.8));
    } else if (style == EMBER) {
        float n = fbm(vec2(arc * 2.4 + seed * 31.0, across * 3.2 - time * 2.2));
        float flame = behind * pow(depth, 10.0) * (0.35 + 0.65 * n) * pow(fresh, 1.5) * lead;
        float core = exp(-sq(across / (0.05 * spread))) * fresh;
        float outer = ahead * exp(-sq(across / (halo * 0.5))) * (0.35 + 0.65 * n) * pow(fresh, 1.5) * lead;
        vec2 cell = vec2(arc * 22.0, (across + halo) * 10.0 - time * 1.5);
        vec2 local = fract(cell) - 0.5;
        float spark = step(0.985, hash21(floor(cell) + seed * 37.0)) * smoothstep(0.35, 0.0, length(local)) * fresh * 1.4 * lead;
        light = core * pow(fresh, 0.6) * tipEnds + (outer + flame + spark) * ends;
        white = core * 0.6 + spark * 0.5;
        tint = mix(First.rgb, Second.rgb, clamp(along * 0.8 + (1.0 - n) * 0.25, 0.0, 1.0));
    } else if (style == PRISM) {
        float tail = pow(fresh, 1.8);
        float core = exp(-sq(across / (0.06 * spread)));
        float outer = ahead * exp(-sq(across / (halo * 0.42))) * 0.45;
        float body = behind * pow(depth, 14.0) * 0.45;
        light = (core * tipEnds + (outer + body) * lead * ends) * tail;
        white = core * 0.5;
        tint = mix(hue(fract(along * 0.75 + arc * 0.12 + time * 0.15 + seed)), First.rgb, 0.2);
    } else {
        float reach = max(pow(fresh, 1.3) * 0.16, 0.001);
        float body = behind * (1.0 - smoothstep(reach * 0.55, reach, across)) * (1.0 - 0.6 * across / reach) * lead;
        float edge = exp(-sq(across / (0.018 * spread))) * 1.5;
        float outer = ahead * exp(-sq(across / (halo * 0.22))) * 0.7 * lead;
        light = (edge * tipEnds + (body * 0.7 + outer) * ends) * pow(fresh, 0.5);
        white = edge * 0.6;
        tint = mix(First.rgb, Second.rgb, clamp(across / max(reach, 0.05), 0.0, 1.0));
    }

    float near = smoothstep(0.1, 0.65, 1.0 / gl_FragCoord.w);
    float window = smoothstep(-halo, -halo * 0.55, across) * (1.0 - smoothstep(0.7, 1.0, across));
    float a = clamp(light, 0.0, 1.6) * alpha * near * window * First.a;
    vec3 rgb = mix(tint, vec3(1.0), clamp(white, 0.0, 1.0));
    float glow = Look.y;
    fragColor = vec4(rgb * a * (0.6 + 0.4 * glow), a * (1.0 - clamp(glow, 0.0, 1.0) * 0.8));
}
