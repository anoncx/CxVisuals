layout(std140) uniform Sky {
    mat4 Rays;
    mat4 Turn;
    vec4 Lens;
    vec4 Haze;
    vec4 First;
    vec4 Second;
};

uniform sampler2D DepthSampler;

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

bool skyRay(out vec3 world, out vec3 dir) {
    world = vec3(0.0, 1.0, 0.0);
    dir = world;
    if (texelFetch(DepthSampler, ivec2(gl_FragCoord.xy), 0).r > 0.0) {
        return false;
    }
    vec4 point = Rays * vec4(texCoord * 2.0 - 1.0, 0.5, 1.0);
    world = normalize(point.xyz / point.w);
    dir = mat3(Turn) * world;
    return true;
}

float hash12(vec2 p) {
    vec3 q = fract(vec3(p.xyx) * 0.1031);
    q += dot(q, q.yzx + 33.33);
    return fract((q.x + q.y) * q.z);
}

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

vec3 hash33(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

float noise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = mix(hash12(cell), hash12(cell + vec2(1.0, 0.0)), u.x);
    float b = mix(hash12(cell + vec2(0.0, 1.0)), hash12(cell + vec2(1.0, 1.0)), u.x);
    return mix(a, b, u.y);
}

float noise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * (3.0 - 2.0 * f);
    float a = mix(hash13(cell), hash13(cell + vec3(1.0, 0.0, 0.0)), u.x);
    float b = mix(hash13(cell + vec3(0.0, 1.0, 0.0)), hash13(cell + vec3(1.0, 1.0, 0.0)), u.x);
    float c = mix(hash13(cell + vec3(0.0, 0.0, 1.0)), hash13(cell + vec3(1.0, 0.0, 1.0)), u.x);
    float d = mix(hash13(cell + vec3(0.0, 1.0, 1.0)), hash13(cell + vec3(1.0, 1.0, 1.0)), u.x);
    return mix(mix(a, b, u.y), mix(c, d, u.y), u.z);
}

float fbm(vec3 p) {
    float sum = noise(p) * 0.5;
    p = p * 2.03 + vec3(17.1, 5.7, 9.2);
    sum += noise(p) * 0.25;
    p = p * 2.03 + vec3(17.1, 5.7, 9.2);
    sum += noise(p) * 0.125;
    return sum / 0.875;
}

vec3 starLayer(vec3 dir, float scale, float share, float least) {
    vec3 p = dir * scale;
    vec3 cell = floor(p);
    vec3 seed = hash33(cell);
    float size = clamp(Lens.x * scale * 1.2, least, 0.25);
    float core = 1.0 - smoothstep(0.0, size, length(p - cell - 0.25 - seed * 0.5));
    float sparkle = 0.8 + 0.2 * sin(Lens.y * (1.5 + seed.x * 3.0) + seed.y * 40.0);
    float lit = step(seed.z, share) * core * core * sparkle * (0.25 + 0.75 * seed.x * seed.x);
    return mix(vec3(0.72, 0.84, 1.0), vec3(1.0, 0.86, 0.7), seed.y) * lit;
}

vec3 stars(vec3 dir, float crowd) {
    return starLayer(dir, 140.0, 0.07 * crowd, 0.1) * 1.6 + starLayer(dir, 60.0, 0.09, 0.16) * 3.2;
}

void finish(vec3 colour, vec3 world) {
    colour = 1.0 - exp(-max(colour, 0.0) * Lens.z);
    float rise = world.y < 0.0 ? world.y * -0.4 : world.y;
    colour = mix(colour, Haze.rgb, (1.0 - smoothstep(0.0, 0.14, rise)) * Haze.a);
    colour += (hash12(gl_FragCoord.xy) - 0.5) / 255.0;
    fragColor = vec4(max(colour, 0.0) * Lens.w, Lens.w);
}
