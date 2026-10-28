uniform sampler2D MaskSampler;
uniform sampler2D MaskDepthSampler;
uniform sampler2D SceneDepthSampler;
uniform sampler2D FieldSampler;

layout(location = 0) out vec4 fragColor;

struct Surface {
    float edge;
    float reach;
    float scale;
    vec2 normal;
    vec3 anchor;
    vec2 rigid;
    bool inside;
};

float shown = 1.0;

const float FIELD_STEP = 0.25;
const int FIELD_ENTRIES = 29;
const float FIELD_TABLE[29] = float[29](0.000, 0.496, 1.034, 1.454, 2.209, 3.293, 4.638, 6.123, 7.931, 10.337, 13.321, 16.902, 21.390, 26.777, 32.884, 39.836, 47.491, 55.426, 63.405, 71.390, 79.336, 87.144, 94.702, 101.940, 108.823, 115.356, 121.560, 127.462, 133.094);
const float DEEP = 1.0e4;
const float BAND_SHARE = 0.01;
const float BAND_FROM = 4.0;
const float BAND_TO = 5.5;
const int RINGS = 19;
const ivec2 RING[19] = ivec2[19](ivec2(1, 0), ivec2(1, 1), ivec2(2, 0), ivec2(2, 1), ivec2(2, 2), ivec2(3, 0), ivec2(3, 1), ivec2(3, 2), ivec2(4, 0), ivec2(4, 1), ivec2(3, 3), ivec2(4, 2), ivec2(4, 3), ivec2(5, 0), ivec2(5, 1), ivec2(5, 2), ivec2(4, 4), ivec2(5, 3), ivec2(6, 0));

float fieldSpan(float share) {
    float at = -log(2.0 * max(share, 1.0e-9)) / FIELD_STEP;
    if (at >= float(FIELD_ENTRIES - 1)) {
        return -1.0;
    }
    int slot = int(max(at, 0.0));
    return mix(FIELD_TABLE[slot], FIELD_TABLE[slot + 1], max(at - float(slot), 0.0));
}

float covered(ivec2 at, ivec2 low, ivec2 high) {
    return texelFetch(MaskSampler, clamp(at, low, high), 0).a > 0.0 ? 1.0 : 0.0;
}

vec3 nearby(ivec2 here) {
    ivec2 low = ivec2(Region.xy);
    ivec2 high = ivec2(Region.xy + Region.zw) - 1;
    for (int i = 0; i < RINGS; i++) {
        ivec2 ring = RING[i];
        bool lopsided = ring.y != 0 && ring.x != ring.y;
        vec2 found = vec2(0.0);
        float hits = 0.0;
        ivec2 turn = ring;
        for (int quarter = 0; quarter < 4; quarter++) {
            float hit = covered(here + turn, low, high);
            found += vec2(turn) * hit;
            hits += hit;
            if (lopsided) {
                hit = covered(here + turn.yx, low, high);
                found += vec2(turn.yx) * hit;
                hits += hit;
            }
            turn = ivec2(-turn.y, turn.x);
        }
        if (hits > 0.0) {
            return vec3(found / hits, length(max(vec2(ring) - 0.5, 0.0)));
        }
    }
    return vec3(0.0, 0.0, -1.0);
}

bool bodyHidden(ivec2 probe) {
    ivec2 low = ivec2(Region.xy);
    ivec2 high = ivec2(Region.xy + Region.zw) - 1;
    ivec2 body = clamp(probe, low, high);
    bool found = texelFetch(MaskSampler, body, 0).a > 0.0;
    for (int i = 0; i < 4 && !found; i++) {
        ivec2 step = i == 0 ? ivec2(2, 0) : i == 1 ? ivec2(-2, 0) : i == 2 ? ivec2(0, 2) : ivec2(0, -2);
        ivec2 at = clamp(body + step, low, high);
        if (texelFetch(MaskSampler, at, 0).a > 0.0) {
            body = at;
            found = true;
        }
    }
    if (!found) {
        return false;
    }
    vec2 centre = vec2(body) + 0.5;
    float own = viewDistance(centre, texelFetch(MaskDepthSampler, body, 0).r);
    float scene = viewDistance(centre, texelFetch(SceneDepthSampler, body, 0).r);
    return scene < own * 0.98 - 0.05;
}

bool surface(out Surface s) {
    vec2 pixel = gl_FragCoord.xy;
    ivec2 here = ivec2(pixel);
    vec2 size = vec2(textureSize(FieldSampler, 0)) * 2.0;
    vec2 low = (Region.xy + 1.0) / size;
    vec2 high = max(low, (Region.xy + Region.zw - 1.0) / size);
    vec4 field = texture(FieldSampler, clamp(pixel / size, low, high));
    vec2 slope = vec2(dFdx(field.r), dFdy(field.r));
    vec2 outward = dot(slope, slope) > 1.0e-14 ? -normalize(slope) : vec2(0.0);
    s.inside = texelFetch(MaskSampler, here, 0).a > 0.0;
    float range = 0.0;
    ivec2 probe = here;
    if (s.inside) {
        range = max(viewDistance(pixel, texelFetch(MaskDepthSampler, here, 0).r), 0.001);
        float span = fieldSpan(field.b);
        s.edge = span < 0.0 ? -DEEP : -span;
        s.normal = span < 0.0 ? vec2(0.0) : outward;
    } else {
        float span = fieldSpan(field.r);
        vec3 hit = vec3(0.0, 0.0, -1.0);
        if (field.a > BAND_SHARE) {
            hit = nearby(here);
        }
        if (span < 0.0 && hit.z < 0.0) {
            return false;
        }
        vec2 toward = hit.z >= 0.0 ? hit.xy : (span >= 0.0 ? -outward * span : vec2(0.0));
        probe = here + ivec2(round(toward));
        vec2 away = dot(hit.xy, hit.xy) > 0.0 ? -normalize(hit.xy) : outward;
        if (hit.z < 0.0) {
            s.edge = span;
            s.normal = outward;
        } else if (span < 0.0) {
            s.edge = hit.z;
            s.normal = away;
        } else {
            float blend = smoothstep(BAND_FROM, BAND_TO, hit.z);
            vec2 lean = mix(away, outward, blend);
            s.edge = mix(hit.z, span, blend);
            s.normal = dot(lean, lean) > 1.0e-8 ? normalize(lean) : away;
        }
        if (field.r > 1.0e-5) {
            range = max(field.g / field.r, 0.001);
        } else {
            ivec2 from = here + ivec2(round(hit.xy));
            range = max(viewDistance(vec2(from) + 0.5, texelFetch(MaskDepthSampler, from, 0).r), 0.001);
        }
    }
    if (Knobs.z > 0.5) {
        float scene = viewDistance(pixel, texelFetch(SceneDepthSampler, here, 0).r);
        if (s.inside) {
            if (scene < range * 0.98 - 0.05) {
                return false;
            }
        } else {
            if (bodyHidden(probe)) {
                return false;
            }
            float slack = Origin.w;
            shown = clamp((scene - range * 0.98 + slack) / (slack * 0.5 + 0.02), 0.0, 1.0);
            if (shown <= 0.0) {
                return false;
            }
        }
    }
    vec3 ray = viewRay(pixel);
    ray /= max(-ray.z, 1.0e-4);
    s.anchor = vec3((pixel - Screen.xy * 0.5) * range / Lens.z, 0.0);
    int count = int(Knobs.w);
    float total = 0.0;
    float strongest = 0.0;
    float steady = 0.0;
    s.rigid = s.anchor.xy;
    vec2 weighted = vec2(0.0);
    for (int i = 0; i < count; i++) {
        vec4 centre = Centres[i];
        if (abs(centre.z - range) > centre.w) {
            continue;
        }
        vec2 gap = (pixel - centre.xy) * centre.z / Lens.z;
        float pull = 1.0 / (dot(gap, gap) + 0.02);
        pull *= pull;
        total += pull;
        if (pull > strongest) {
            strongest = pull;
            s.rigid = gap;
        }
        steady += centre.z * pull;
        weighted += gap * pull;
    }
    if (total > 0.0) {
        range = steady / total;
        s.anchor = vec3(weighted / total, 0.0);
    }
    shown *= 1.0 - smoothstep(Origin.x, Origin.y, range);
    if (shown <= 0.0) {
        return false;
    }
    s.scale = Lens.z / max(range, 0.05);
    s.reach = clamp(Origin.w * s.scale, 3.0, 64.0);
    return s.inside || s.edge <= s.reach;
}

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
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

float noise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    vec3 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float a = mix(hash13(cell), hash13(cell + vec3(1.0, 0.0, 0.0)), u.x);
    float b = mix(hash13(cell + vec3(0.0, 1.0, 0.0)), hash13(cell + vec3(1.0, 1.0, 0.0)), u.x);
    float c = mix(hash13(cell + vec3(0.0, 0.0, 1.0)), hash13(cell + vec3(1.0, 0.0, 1.0)), u.x);
    float d = mix(hash13(cell + vec3(0.0, 1.0, 1.0)), hash13(cell + vec3(1.0, 1.0, 1.0)), u.x);
    return mix(mix(a, b, u.y), mix(c, d, u.y), u.z);
}

float fbm(vec3 p) {
    float sum = 0.0;
    float weight = 0.5;
    for (int octave = 0; octave < 4; octave++) {
        sum += noise(p) * weight;
        p = p * 2.03 + vec3(17.1, 5.7, 9.2);
        weight *= 0.5;
    }
    return sum / 0.9375;
}

float ridged(vec3 p) {
    float sum = 0.0;
    float weight = 0.5;
    for (int octave = 0; octave < 3; octave++) {
        sum += (1.0 - abs(noise(p) * 2.0 - 1.0)) * weight;
        p = p * 2.11 + vec3(3.3, 11.9, 7.4);
        weight *= 0.5;
    }
    return sum / 0.875;
}

vec3 palette(float t) {
    return mix(First.rgb, Second.rgb, clamp(t, 0.0, 1.0));
}

void emit(vec3 glow, vec3 tint, float cover) {
    vec3 light = (1.0 - exp(-max(glow, 0.0) * Knobs.x)) * shown;
    float alpha = clamp(cover, 0.0, 1.0) * shown;
    vec3 colour = light * (1.0 - alpha * 0.35) + tint * alpha;
    float grain = (hash12(gl_FragCoord.xy + fract(Lens.w * 7.31) * 113.0) - 0.5) / 255.0;
    if (any(isnan(colour)) || any(isinf(colour))) {
        discard;
    }
    float lit = step(0.002, max(max(colour.r, colour.g), max(colour.b, alpha)));
    fragColor = vec4(max(colour + grain * lit, 0.0), alpha);
}
