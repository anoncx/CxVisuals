#version 330

layout(std140) uniform Stage {
    mat4 ViewProj;
    mat4 Unproject;
    vec4 Eye;
    vec4 Lens;
    vec4 Accent;
    vec4 Frame;
};

in vec2 texCoord;

out vec4 fragColor;

const float FAR = 60.0;
const float GRID = 0.25;
const float BODY = 0.27;
const float HEAD = 0.22;

float figure(vec3 p) {
    vec3 q = p - vec3(Frame.z, Eye.w, Frame.w);
    vec3 spine = q - vec3(0.0, clamp(q.y, 0.27, 1.05), 0.0);
    float body = length(spine) - BODY;
    float head = length(q - vec3(0.0, 1.42, 0.0)) - HEAD;
    float k = 0.06;
    float h = clamp(0.5 + 0.5 * (head - body) / k, 0.0, 1.0);
    return mix(head, body, h) - k * h * (1.0 - h);
}

vec3 normalAt(vec3 p) {
    vec2 e = vec2(0.002, 0.0);
    return normalize(vec3(figure(p + e.xyy) - figure(p - e.xyy), figure(p + e.yxy) - figure(p - e.yxy), figure(p + e.yyx) - figure(p - e.yyx)));
}

float march(vec3 origin, vec3 direction) {
    vec3 offset = origin - vec3(Frame.z, Eye.w + 0.85, Frame.w);
    float b = dot(offset, direction);
    float c = dot(offset, offset) - 1.21;
    float disc = b * b - c;
    if (disc < 0.0) {
        return -1.0;
    }
    float t = max(-b - sqrt(disc), 0.0);
    float end = -b + sqrt(disc);
    for (int i = 0; i < 48 && t < end; i++) {
        float d = figure(origin + direction * t);
        if (d < 0.0015) {
            return t;
        }
        t += d;
    }
    return -1.0;
}

float depthOf(vec3 relative) {
    vec4 clip = ViewProj * vec4(relative, 1.0);
    return clamp((clip.z / clip.w - Lens.y) / Lens.x, 0.0, 1.0);
}

float corners(vec2 pixel) {
    vec2 middle = Lens.zw * 0.5;
    vec2 q = abs(pixel - middle) - middle + Frame.x;
    float d = length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - Frame.x;
    return 1.0 - smoothstep(-0.75, 0.75, d);
}

void main() {
    vec4 far = Unproject * vec4(texCoord * 2.0 - 1.0, Lens.y + Lens.x * 0.5, 1.0);
    vec3 direction = normalize(far.xyz / far.w);
    vec3 origin = Eye.xyz;
    float glow = Accent.a;

    vec3 sky = mix(vec3(0.018, 0.018, 0.022), vec3(0.055, 0.056, 0.064), smoothstep(-0.25, 0.35, direction.y));
    vec3 colour = sky;
    float depth = 0.0;

    float floorT = direction.y < -1.0e-4 ? -origin.y / direction.y : -1.0;
    float bodyT = Frame.y > 0.5 ? march(origin, direction) : -1.0;

    if (bodyT > 0.0 && (floorT < 0.0 || bodyT < floorT)) {
        vec3 p = origin + direction * bodyT;
        vec3 n = normalAt(p);
        vec3 key = normalize(vec3(-0.5, 0.8, 0.35));
        float diffuse = max(dot(n, key), 0.0);
        float rim = pow(1.0 - max(dot(n, -direction), 0.0), 3.0);
        float bounce = max(-n.y, 0.0) * exp(-max(p.y, 0.0) * 1.4);
        vec3 clay = vec3(0.82, 0.82, 0.8);
        colour = clay * (0.16 + 0.7 * diffuse) + vec3(rim * 0.35) + Accent.rgb * bounce * glow * 0.9;
        depth = depthOf(p - origin);
    } else if (floorT > 0.0 && floorT < FAR) {
        vec3 p = origin + direction * floorT;
        vec2 cell = (fract(p.xz / GRID + 0.5) - 0.5) * GRID;
        float px = max(fwidth(p.x), fwidth(p.z));
        float dotSize = 0.022;
        float bead = 1.0 - smoothstep(dotSize - px, dotSize + px, length(cell));
        float spread = exp(-length(p.xz) * 0.38);
        float lod = 1.0 - smoothstep(0.02, 0.09, px);
        vec3 ground = vec3(0.03, 0.03, 0.034) + vec3(0.22) * bead * spread * lod;
        float shadowSize = 0.34 + Eye.w * 0.22;
        float shadow = 1.0 - (0.7 / (1.0 + Eye.w * 2.5)) * (1.0 - smoothstep(0.0, shadowSize, length(p.xz - Frame.zw)));
        float pool = exp(-dot(p.xz, p.xz) * 0.55) * glow * 0.12;
        colour = ground * shadow + Accent.rgb * pool;
        float horizon = smoothstep(FAR * 0.25, FAR * 0.6, floorT);
        colour = mix(colour, sky, horizon);
        depth = depthOf(p - origin);
    }

    vec2 pixel = texCoord * Lens.zw;
    vec2 centred = texCoord - 0.5;
    colour *= 1.0 - dot(centred, centred) * 0.6;
    float cover = corners(pixel);
    gl_FragDepth = depth;
    fragColor = vec4(colour * cover, cover);
}
