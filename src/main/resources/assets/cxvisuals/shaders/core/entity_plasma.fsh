#version 330
#extension GL_ARB_separate_shader_objects : require

#moj_import <cxvisuals:entity_block.glsl>
#moj_import <cxvisuals:entity_fx.glsl>

float beam(float gap, float width) {
    float near = width / (abs(gap) + width);
    return near * near;
}

void main() {
    Surface s;
    if (!surface(s)) {
        discard;
    }
    float time = Lens.w;
    float edge = s.edge / s.scale;
    float reach = s.reach / s.scale;
    float thin = clamp(0.016, 1.6 / s.scale, 3.5 / s.scale);
    vec3 p = s.anchor;
    float u = s.edge / s.reach;
    float band = (1.0 - smoothstep(0.62, 1.0, u)) * smoothstep(-1.6, -0.2, u);
    float pulse = 0.9 + 0.1 * sin(time * 1.4);

    float live = band <= 0.0 ? 0.0 : smoothstep(0.4, 0.62, noise(p * 1.4 + vec3(time * 0.13, -time * 0.09, time * 0.05)));
    float arc = 0.0;
    if (live > 0.0) {
        vec3 q = p * 3.6 + vec3(time * 0.11, -time * 0.24, time * 0.08);
        float wander = (ridged(q) - 0.5) * 0.9 + (noise(q * 2.4) - 0.5) * 0.12;
        arc = beam(edge - reach * (0.2 + wander), thin) * live;
    }

    float rare = band <= 0.0 ? 0.0 : smoothstep(0.55, 0.72, noise(p * 1.0 + vec3(-time * 0.08, time * 0.06, time * 0.1) + 9.3));
    float jump = 0.0;
    if (rare > 0.0) {
        vec3 r = p * 2.3 + vec3(-time * 0.07, -time * 0.17, time * 0.12) + 31.7;
        float stray = (ridged(r) - 0.5) * 1.4 + (noise(r * 2.9) - 0.5) * 0.14;
        jump = beam(edge - reach * (0.44 + stray), thin * 0.85) * rare;
    }

    float power = (arc + jump * 0.8) * band * pulse;
    float halo = exp(-max(u, 0.0) * 3.2) * (0.14 + 0.3 * live) * band * pulse;
    vec3 glow = First.rgb * power * 1.5 + vec3(1.0) * pow(min(power, 1.0), 3.0) * 1.6 + Second.rgb * halo;

    if (s.inside) {
        float blotch = smoothstep(0.52, 0.72, noise(p * 1.9 + vec3(0.0, time * 0.1, time * 0.07)));
        float crawl = 0.0;
        if (blotch > 0.0) {
            float vein = abs(ridged(p * 3.4 + vec3(time * 0.09, -time * 0.14, 0.0)) - 0.55);
            crawl = thin * 1.4 / (vein * 0.35 + thin * 1.4);
            crawl *= crawl * blotch;
        }
        float rim = exp(s.edge / max(s.reach * 0.18, 1.0));
        glow = glow * 0.6 + First.rgb * crawl * 0.45 + Second.rgb * rim * 0.35 * pulse;
        emit(glow, Second.rgb, (0.05 + rim * 0.10) * Knobs.y);
        return;
    }
    emit(glow, First.rgb, 0.0);
}
