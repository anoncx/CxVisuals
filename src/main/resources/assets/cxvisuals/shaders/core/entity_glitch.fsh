#version 330
#extension GL_ARB_separate_shader_objects : require

#moj_import <cxvisuals:entity_block.glsl>
#moj_import <cxvisuals:entity_fx.glsl>

uniform sampler2D SceneSampler;

ivec2 texel(vec2 pixel) {
    return clamp(ivec2(pixel), ivec2(0), ivec2(Screen.xy) - 1);
}

float body(vec2 pixel) {
    return step(0.001, texelFetch(MaskSampler, texel(pixel), 0).a);
}

vec3 scene(vec2 pixel) {
    return texelFetch(SceneSampler, texel(pixel), 0).rgb;
}

void main() {
    Surface s;
    if (!surface(s)) {
        discard;
    }
    float time = Lens.w;
    vec2 pixel = gl_FragCoord.xy;
    float tick = floor(time * 14.0);
    float burst = smoothstep(0.42, 0.5, hash11(floor(time * 2.6) + 17.0));
    float tall = 5.0 + 11.0 * hash11(tick * 1.3);
    float row = floor(pixel.y / tall);
    float pick = step(0.58, hash12(vec2(row, tick + 7.0))) * burst;
    float push = (hash12(vec2(row, tick)) - 0.5) * 2.0 * pick * s.reach * 0.6;
    vec2 split = vec2(1.5 + s.reach * 0.07 * (0.3 + 0.7 * burst), 0.0);

    vec2 from = pixel - vec2(push, 0.0);
    float left = body(from - split);
    float right = body(from + split);
    float cover = max(max(left, right), max(body(from), float(s.inside)));

    vec2 block = floor(pixel / (3.0 + 5.0 * hash11(tick + 3.0)));
    float near = 1.0 - smoothstep(0.0, 0.7, abs(s.edge) / s.reach);
    float dust = step(0.94, hash12(block + tick)) * near * burst;
    vec3 spark = mix(First.rgb, Second.rgb, hash12(block)) * dust * 1.3;
    float lines = 0.86 + 0.14 * step(0.5, fract(pixel.y * 0.5));

    if (cover <= 0.0) {
        emit(spark, First.rgb, 0.0);
        return;
    }
    vec3 torn = vec3(scene(from - split).r, scene(from).g, scene(from + split).b) * lines;
    vec3 wash = mix(First.rgb, Second.rgb, step(0.5, hash12(vec2(row, tick + 3.0))));
    torn = mix(torn, torn * (0.55 + wash * 1.1), 0.5 * pick);
    vec3 fringe = First.rgb * left * (1.0 - right) + Second.rgb * right * (1.0 - left);
    emit(spark + fringe * 0.55, torn, cover * min(1.0, Knobs.y));
}
