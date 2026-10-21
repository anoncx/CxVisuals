layout(std140) uniform EntityFx {
    mat4 Unproject;
    mat4 Anchor;
    vec4 Screen;
    vec4 Lens;
    vec4 Origin;
    vec4 Knobs;
    vec4 First;
    vec4 Second;
    vec4 Region;
    vec4 Centres[32];
};

const float NO_SEED = 4096.0;

vec3 viewRay(vec2 pixel) {
    vec2 ndc = pixel * Screen.zw * 2.0 - 1.0;
    vec4 point = Unproject * vec4(ndc, 0.5 * Lens.x + Lens.y, 1.0);
    return point.xyz / point.w;
}

float viewDistance(vec2 pixel, float depth) {
    vec2 ndc = pixel * Screen.zw * 2.0 - 1.0;
    vec4 point = Unproject * vec4(ndc, depth * Lens.x + Lens.y, 1.0);
    return -point.z / point.w;
}
