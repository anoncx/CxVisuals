#version 330

#moj_import <cxvisuals:sky.glsl>

const float RADIUS = 0.3;

float ringBand(float span) {
    float inside = smoothstep(1.28, 1.36, span) * (1.0 - smoothstep(2.18, 2.3, span));
    float grooves = 0.62 + 0.25 * sin(span * 34.0) + 0.13 * sin(span * 91.0 + 1.7);
    float gap = 1.0 - 0.85 * smoothstep(1.86, 1.89, span) * (1.0 - smoothstep(1.93, 1.96, span));
    return inside * grooves * gap;
}

void main() {
    vec3 world;
    vec3 dir;
    if (!skyRay(world, dir)) {
        discard;
    }
    float time = Lens.y;
    vec3 centre = normalize(vec3(-0.42, 0.75, -0.51));
    vec3 axis = normalize(vec3(0.38, 0.88, 0.28));
    vec3 sun = normalize(vec3(0.78, 0.36, -0.1) - centre * 0.45);

    float along = dot(dir, centre);
    float miss = sqrt(max(1.0 - along * along, 0.0));
    float cover = along > 0.0 ? clamp((RADIUS - miss) / Lens.x + 0.5, 0.0, 1.0) : 0.0;
    float near = along - sqrt(max(RADIUS * RADIUS - miss * miss, 0.0));

    vec3 colour = vec3(0.005, 0.007, 0.018) + Second.rgb * 0.01 * max(along, 0.0);
    colour += stars(dir, 1.0);
    if (along > 0.0) {
        float side = dot((dir * along - centre) / max(miss, 1.0e-4), sun);
        colour += First.rgb * exp(-max(miss - RADIUS, 0.0) * 14.0) * smoothstep(-0.3, 0.6, side) * 0.22;
    }

    float lean = dot(dir, axis);
    float reach = dot(centre, axis) / (abs(lean) > 1.0e-5 ? lean : 1.0e-5);
    vec3 onRing = dir * reach - centre;
    float ring = reach > 0.0 ? ringBand(length(onRing) / RADIUS) : 0.0;
    vec3 ringColour = vec3(0.0);
    if (ring > 0.0) {
        float toward = dot(onRing, sun);
        float beside = sqrt(max(dot(onRing, onRing) - toward * toward, 0.0));
        float shade = toward < 0.0 ? 1.0 - smoothstep(RADIUS * 0.94, RADIUS * 1.03, beside) : 0.0;
        ringColour = mix(vec3(0.85, 0.8, 0.72), First.rgb, 0.35) * (1.0 - shade * 0.92) * 0.9;
        ring *= 0.85;
    }

    vec3 body = vec3(0.0);
    if (cover > 0.0) {
        vec3 normal = normalize(dir * near - centre);
        float lit = dot(normal, sun);
        float day = smoothstep(-0.12, 0.35, lit);
        float lat = dot(normal, axis);
        float swirl = noise(vec3(normal.xz * 3.0 + time * 0.01, lat * 9.0));
        float bands = 0.5 + 0.5 * sin(lat * 17.0 + swirl * 2.4);
        vec3 surface = mix(Second.rgb * 0.55, First.rgb, bands);
        surface = mix(surface, vec3(1.0, 0.95, 0.88), 0.25 * smoothstep(0.6, 1.0, bands));
        float fall = dot(sun, axis);
        float climb = -lat * RADIUS / (abs(fall) > 1.0e-5 ? fall : 1.0e-5);
        if (climb > 0.0) {
            surface *= 1.0 - 0.7 * ringBand(length(normal * RADIUS + sun * climb) / RADIUS);
        }
        float limb = max(dot(normal, -dir), 0.0);
        body = surface * day * (0.25 + 0.75 * pow(limb, 0.35)) * 1.3;
        float rim = 1.0 - limb;
        body += mix(First.rgb, vec3(1.0), 0.3) * rim * rim * rim * day * 0.8;
        body += Second.rgb * 0.012;
    }

    if (cover > 0.0 && reach < near) {
        colour = mix(mix(colour, body, cover), ringColour, ring);
    } else {
        colour = mix(mix(colour, ringColour, ring), body, cover);
    }
    finish(colour, world);
}
