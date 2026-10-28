#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};

uniform sampler2D InSampler;
uniform sampler2D DepthSampler;

layout(std140) uniform MotionBlur {
    mat4 Reproject;
    vec4 Shape;
    vec4 Lens;
    vec4 Hand;
};

layout(location = 0) in vec2 texCoord;

layout(location = 0) out vec4 fragColor;

const int MAX_SAMPLES = 16;

float held(float depth) {
    return step(Hand.x, depth) * step(depth, Hand.y);
}

void main() {
    vec3 here = texture(InSampler, texCoord).rgb;
    fragColor = vec4(here, 1.0);

    vec2 ndc = texCoord * 2.0 - 1.0;
    float depth = texture(DepthSampler, texCoord).r;
    vec4 before = Reproject * vec4(ndc, depth * Lens.x + Lens.y, 1.0);
    if (before.w <= 0.0) {
        return;
    }

    vec2 aspect = vec2(Lens.z, 1.0);
    vec2 motion = (before.xy / before.w - ndc) * 0.5;
    if (Shape.z > 0.0) {
        motion *= smoothstep(Shape.y, Shape.z, length(ndc * aspect));
    }

    float mine = Hand.w * held(depth);
    float allowed = mix(1.0, Hand.z, mine);
    motion *= allowed;

    float reach = length(motion * aspect);
    if (reach * ScreenSize.y < 0.75) {
        return;
    }
    motion *= min(1.0, Shape.w * allowed / reach);
    bool guard = Hand.w > 0.5 && mine < 0.5 && Hand.z < 1.0;

    int count = min(int(Shape.x), MAX_SAMPLES);
    float jitter = fract(52.9829189 * fract(dot(gl_FragCoord.xy, vec2(0.06711056, 0.00583715))));
    vec3 sum = vec3(0.0);
    float total = 0.0;
    for (int i = 0; i < count; i++) {
        vec2 at = texCoord + motion * ((float(i) + jitter) / float(count) - 0.5);
        float weight = guard ? mix(1.0, Hand.z, held(texture(DepthSampler, at).r)) : 1.0;
        sum += texture(InSampler, at).rgb * weight;
        total += weight;
    }
    if (total > 0.0) {
        fragColor = vec4(sum / total, 1.0);
    }
}
