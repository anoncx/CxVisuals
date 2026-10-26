#version 330

layout(std140) uniform Swing {
    mat4 ViewProj;
    vec4 First;
    vec4 Second;
    vec4 Look;
};

in float across;
in float arc;
in float along;
in float alpha;
flat in float seed;

out vec4 fragColor;

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
    float lead = smoothstep(0.0, 0.07, along);
    float ahead = across < 0.0 ? 1.0 : 0.0;
    float behind = 1.0 - ahead;
    float depth = max(1.0 - across, 0.0);
    vec3 tint = mix(First.rgb, Second.rgb, along);
    float light = 0.0;
    float white = 0.0;

    if (style == GLOW) {
        float tail = pow(fresh, 1.8);
        float core = exp(-sq(across / (0.06 * spread)));
        float outer = ahead * exp(-sq(across / (halo * 0.42)));
        float body = behind * pow(depth, 2.2) * 0.34;
        light = (core + (outer * 0.45 + body) * lead) * tail;
        white = core * 0.55;
    } else if (style == COMET) {
        float head = exp(-along * 9.0);
        float taper = pow(fresh, 2.2);
        float core = exp(-sq(across / (0.04 * spread * (1.0 + 2.2 * head)))) * pow(fresh, 1.6);
        float outer = ahead * exp(-sq(across / (halo * (0.35 + 0.35 * head)))) * (0.35 + 0.5 * head) * pow(fresh, 1.4) * lead;
        float body = behind * (1.0 - smoothstep(0.0, max(taper * 0.6, 0.02), across)) * taper * 0.45 * lead;
        light = core + outer + body;
        white = core * 0.85;
        tint = mix(First.rgb, Second.rgb, pow(along, 0.8));
    } else if (style == EMBER) {
        float n = fbm(vec2(arc * 2.4 + seed * 31.0, across * 3.2 - time * 2.2));
        float flame = behind * smoothstep(0.0, 1.0, pow(depth, 2.5) * 1.6 + n * 0.6 - 0.45 - along * 0.5) * lead;
        float core = exp(-sq(across / (0.05 * spread))) * fresh;
        float outer = ahead * exp(-sq(across / (halo * 0.5))) * (0.35 + 0.65 * n) * pow(fresh, 1.5) * lead;
        vec2 cell = vec2(arc * 22.0, (across + halo) * 10.0 - time * 1.5);
        vec2 local = fract(cell) - 0.5;
        float spark = step(0.985, hash21(floor(cell) + seed * 37.0)) * smoothstep(0.35, 0.0, length(local)) * fresh * 1.4 * lead;
        light = (core + outer + flame * 0.7) * pow(fresh, 0.6) + spark;
        white = core * 0.6 + spark * 0.5;
        tint = mix(First.rgb, Second.rgb, clamp(along * 0.8 + (1.0 - n) * 0.25, 0.0, 1.0));
    } else if (style == PRISM) {
        float tail = pow(fresh, 1.8);
        float core = exp(-sq(across / (0.06 * spread)));
        float outer = ahead * exp(-sq(across / (halo * 0.42)));
        float body = behind * pow(depth, 2.2) * 0.34;
        light = (core + (outer * 0.45 + body) * lead) * tail;
        white = core * 0.5;
        tint = mix(hue(fract(along * 0.75 + arc * 0.12 + time * 0.15 + seed)), First.rgb, 0.2);
    } else {
        float reach = max(pow(fresh, 1.3) * 0.38, 0.001);
        float body = behind * (1.0 - smoothstep(reach * 0.55, reach, across)) * (1.0 - 0.6 * across / reach) * lead;
        float edge = exp(-sq(across / (0.018 * spread))) * 1.5;
        float outer = ahead * exp(-sq(across / (halo * 0.22))) * 0.35 * lead;
        light = (body * 0.7 + edge + outer) * pow(fresh, 0.5);
        white = edge * 0.6;
        tint = mix(First.rgb, Second.rgb, clamp(across / max(reach, 0.05), 0.0, 1.0));
    }

    float a = clamp(light, 0.0, 1.6) * alpha * First.a;
    vec3 rgb = mix(tint, vec3(1.0), clamp(white, 0.0, 1.0));
    float glow = Look.y;
    fragColor = vec4(rgb * a * (0.6 + 0.4 * glow), a * (1.0 - clamp(glow, 0.0, 1.0) * 0.8));
}
