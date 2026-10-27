#version 330

layout(std140) uniform MapTerrain {
    mat4 ViewProj;
    vec4 Eye;
    vec4 FogColor;
    vec4 Params;
    vec4 Extra;
};
layout(std140) uniform MapTile {
    vec4 Tile;
    ivec4 Slots;
    ivec4 Near;
    ivec4 World;
};

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;

in vec3 Position;
in vec2 UV0;

out vec2 local;
out float fogDistance;
flat out vec3 raw;
flat out int block;
flat out int face;
flat out float sideHeight;
flat out int water;

const int EDGE = 128;
const int GRID = 16;
const int SKIRT = 6;

ivec2 slotOrigin(int slot) {
    return ivec2(slot % GRID, slot / GRID) * EDGE;
}

uint word(vec4 texel, bool high) {
    vec4 b = floor(texel * 255.0 + 0.5);
    return high ? uint(b.b) | (uint(b.a) << 8u) : uint(b.r) | (uint(b.g) << 8u);
}

ivec4 fetchColumn(ivec2 col) {
    int slot = Slots.x;
    if (col.x < 0) {
        slot = Near.z;
        col.x += EDGE;
    } else if (col.x >= EDGE) {
        slot = Near.w;
        col.x -= EDGE;
    }
    if (col.y < 0) {
        slot = slot == Slots.x ? Near.x : -1;
        col.y += EDGE;
    } else if (col.y >= EDGE) {
        slot = slot == Slots.x ? Near.y : -1;
        col.y -= EDGE;
    }
    if (slot < 0) {
        return ivec4(0, -1000, 0, 0);
    }
    vec4 id = texelFetch(Sampler1, slotOrigin(slot) + col, 0);
    uint lo = word(id, false);
    uint hi = word(id, true);
    int b = int(lo & 0x3FFFu);
    return ivec4(b, b == 0 ? -1000 : int(hi & 0x1FFu) - 64, int((hi >> 9u) & 1u), 1);
}

float relief(int h, int west, int north) {
    if (Params.x > 1.5) {
        return 1.0;
    }
    float d = float((h - west) + (h - north));
    float light = clamp(1.0 + d * 0.055, 0.66, 1.24);
    light *= 1.0 + clamp((float(h) - 63.0) / 160.0, -0.6, 1.0) * 0.09;
    if (Params.x > 0.5 && ((h >> 3) != (west >> 3) || (h >> 3) != (north >> 3))) {
        light *= 0.8;
    }
    return light;
}

float grain(int x, int z) {
    uint h = uint(x) * 374761393u + uint(z) * 668265263u;
    h = (h ^ (h >> 13u)) * 1274126177u;
    return 1.0 + (float(h >> 24u) / 255.0 - 0.5) * 0.09;
}

vec3 unlight(vec3 lit, float light) {
    if (light <= 1.0) {
        return lit / light;
    }
    float k = (light - 1.0) * 0.35;
    return (lit - k) / (light - k);
}

void main() {
    int stride = Slots.y;
    int perRow = EDGE / stride;
    ivec2 col = ivec2(gl_InstanceID % perRow, gl_InstanceID / perRow) * stride;
    ivec4 here = fetchColumn(col);
    block = here.x;
    water = here.z;
    face = int(UV0.x + 0.5);
    if (block == 0) {
        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
        return;
    }
    float step = Tile.z;
    float width = step * float(stride);
    int h = here.y;
    float top = float(h + 1) - Extra.x;
    float x0 = Tile.x + float(col.x) * step;
    float z0 = Tile.y + float(col.y) * step;
    vec3 pos;
    sideHeight = 0.0;
    if (face == 0) {
        pos = vec3(x0 + Position.x * width, top, z0 + Position.z * width);
        local = Position.xz * width;
    } else {
        bool zFace = face == 1;
        float sign = zFace ? Params.y : Params.z;
        ivec2 offset = zFace ? ivec2(0, sign > 0.0 ? stride : -stride)
                             : ivec2(sign > 0.0 ? stride : -stride, 0);
        ivec4 next = fetchColumn(col + offset);
        int low = next.w == 0 || next.x == 0 ? h - SKIRT : next.y;
        if (water == 1 && next.x != 0 && next.z == 1) {
            low = h;
        }
        if (low >= h) {
            gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
            return;
        }
        float bottom = float(low + 1) - Extra.x;
        float y = mix(bottom, top, Position.y);
        sideHeight = top - bottom;
        float along = zFace ? Position.x : Position.z;
        if (zFace) {
            pos = vec3(x0 + along * width, y, sign > 0.0 ? z0 + width : z0);
        } else {
            pos = vec3(sign > 0.0 ? x0 + width : x0, y, z0 + along * width);
        }
        local = vec2(along * width, top - y);
    }
    ivec4 westColumn = fetchColumn(col + ivec2(-stride, 0));
    ivec4 northColumn = fetchColumn(col + ivec2(0, -stride));
    int west = westColumn.x == 0 ? h : westColumn.y;
    int north = northColumn.x == 0 ? h : northColumn.y;
    vec3 color = texelFetch(Sampler0, slotOrigin(Slots.x) + col, 0).rgb;
    if (water == 0) {
        float light = relief(h, west, north);
        if (step < 1.5) {
            light *= grain(World.x + col.x, World.y + col.y);
        }
        color = unlight(color, light);
    }
    raw = clamp(color, 0.0, 1.0);
    fogDistance = length(pos - Eye.xyz);
    gl_Position = ViewProj * vec4(pos, 1.0);
}
