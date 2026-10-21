#version 330

#moj_import <cxvisuals:entity_block.glsl>
#moj_import <cxvisuals:entity_fx.glsl>

uniform sampler2D SceneSampler;

const float LINES = 150.0;

void main() {
    Surface s;
    if (!surface(s)) {
        discard;
    }
    float time = Lens.w;
    vec3 p = s.anchor;
    vec3 colour = palette(0.5 + 0.5 * sin(p.y * 1.3 + time * 0.4));
    float flicker = mix(1.0, 0.6, step(0.94, hash11(floor(time * 14.0))));
    float roll = smoothstep(0.9, 1.0, sin(p.y * 2.2 - time * 1.1));
    float tear = step(0.985, hash11(floor(p.y * 18.0) + floor(time * 9.0) * 7.0));

    if (s.inside) {
        vec3 scene = texelFetch(SceneSampler, ivec2(gl_FragCoord.xy), 0).rgb;
        float luma = dot(scene, vec3(0.299, 0.587, 0.114));
        float period = s.scale * 6.2831853 / LINES;
        float fine = smoothstep(2.5, 5.0, period);
        float scan = 0.5 + 0.5 * sin(p.y * LINES - time * 5.0);
        float rim = exp(s.edge / max(s.reach * 0.2, 1.0));
        float grain = hash12(gl_FragCoord.xy + fract(time) * 91.0);
        vec3 body = colour * (0.2 + 1.3 * luma) * (0.66 + 0.34 * mix(1.0, scan, fine)) * (0.93 + 0.14 * grain) * flicker;
        vec3 glow = colour * (rim * 0.9 + roll * 0.5 + tear * 0.45) * flicker;
        emit(glow, body, 0.9 * Knobs.y);
        return;
    }

    float u = s.edge / s.reach;
    float halo = exp(-u * 4.6) * 0.5 * (1.0 - smoothstep(0.7, 1.0, u));
    float contour = exp(-s.edge / 1.4) * 0.9;
    float streak = tear * exp(-u * 2.0) * abs(s.normal.x);
    emit(colour * (halo + contour + streak * 0.3) * flicker, colour, 0.0);
}
