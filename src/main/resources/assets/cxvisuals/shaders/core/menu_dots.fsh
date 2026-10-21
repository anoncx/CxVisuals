#version 330

layout(std140) uniform Dots {
    vec4 Paper;
    vec4 Ink;
    vec4 Accent;
    vec4 Frame;
    vec4 Cursor;
};

out vec4 fragColor;

const float PITCH = 22.0;
const float BASE = 1.1;
const float SWELL = 2.4;
const float REACH = 140.0;
const float STEP = 0.8;
const float QUIET = 0.08;
const float LOUD = 0.24;
const float NEAR = 0.55;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float noise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash(cell);
    float b = hash(cell + vec2(1.0, 0.0));
    float c = hash(cell + vec2(0.0, 1.0));
    float d = hash(cell + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

void main() {
    float scale = max(Frame.w, 0.001);
    float time = Frame.z;
    vec2 pixel = vec2(gl_FragCoord.x, Cursor.z > 0.5 ? gl_FragCoord.y : Frame.y - gl_FragCoord.y);
    vec2 unit = pixel / scale;
    vec2 cell = floor(unit / PITCH);
    vec2 centre = (cell + 0.5) * PITCH;

    float drift = 0.65 * noise(cell * 0.11 + time * 0.05) + 0.35 * noise(cell * 0.31 - time * 0.09);
    float near = 1.0 - smoothstep(0.0, REACH, distance(centre, Cursor.xy / scale));
    float radius = mix(BASE, SWELL, near);
    float strength = mix(mix(QUIET, LOUD, drift), NEAR, near);

    vec2 grid = max(floor(Frame.xy / scale / PITCH), vec2(1.0));
    float tick = floor(time / STEP);
    vec2 walker = vec2(mod(tick, grid.x), mod(floor(tick / grid.x), grid.y));
    float walking = step(distance(cell, walker), 0.5);

    float edge = 0.75 / scale;
    float mask = 1.0 - smoothstep(radius - edge, radius + edge, distance(unit, centre));
    vec3 dot = mix(Ink.rgb, Accent.rgb, walking);
    vec3 colour = mix(Paper.rgb, dot, mask * mix(strength, 1.0, walking));
    fragColor = vec4(colour, 1.0);
}
