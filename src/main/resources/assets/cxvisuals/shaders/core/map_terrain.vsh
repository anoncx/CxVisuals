#version 330
#extension GL_ARB_separate_shader_objects : require

layout(std140) uniform MapTerrain {
    mat4 ViewProj;
    vec4 Eye;
    vec4 FogColor;
    vec4 Params;
    vec4 Extra;
    mat4 Inverse;
    vec4 Cloud;
    vec4 CloudBase;
    vec4 CloudTop;
};
layout(std140) uniform MapTile {
    vec4 Tile;
    ivec4 Slots;
    ivec4 Near;
    ivec4 World;
};

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler3;

layout(location = 0) in vec3 Position;
layout(location = 1) in vec2 UV0;

layout(location = 0) out vec2 local;
layout(location = 1) out float fogDistance;
layout(location = 2) flat out vec3 raw;
layout(location = 3) flat out int block;
layout(location = 4) flat out int topBlock;
layout(location = 5) flat out int face;
layout(location = 6) flat out int side;
layout(location = 7) flat out int layer;
layout(location = 8) flat out float sideHeight;
layout(location = 9) flat out int water;
layout(location = 10) flat out int groundWet;
layout(location = 11) flat out float skirt;
layout(location = 12) out float relY;

const int EDGE = 128;
const int GRID = 16;
const int SKIRT = 14;
const int NONE = -1000;
const int MAX_GAP = 28;
const int CUT = 24;
const int WALL = 40;

struct Column {
    int block;
    int h;
    int water;
    int depth;
    int ground;
    int floor;
    int wet;
    bool known;
};

ivec2 slotOrigin(int slot) {
    return ivec2(slot % GRID, slot / GRID) * EDGE;
}

uint word(vec4 texel, bool high) {
    vec4 b = floor(texel * 255.0 + 0.5);
    return high ? uint(b.b) | (uint(b.a) << 8u) : uint(b.r) | (uint(b.g) << 8u);
}

Column fetchColumn(ivec2 col) {
    Column c = Column(0, NONE, 0, 0, 0, NONE, 0, false);
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
        return c;
    }
    ivec2 at = slotOrigin(slot) + col;
    vec4 id = texelFetch(Sampler1, at, 0);
    uint lo = word(id, false);
    uint hi = word(id, true);
    c.block = int(lo & 0x3FFFu);
    if (c.block == 0) {
        return c;
    }
    c.known = true;
    c.h = int(hi & 0x1FFu) - 64;
    c.water = int((hi >> 9u) & 1u);
    c.depth = int((hi >> 10u) & 0x3Fu);
    vec4 under = texelFetch(Sampler3, at, 0);
    uint ulo = word(under, false);
    uint uhi = word(under, true);
    if (c.depth > 0 && (uhi & 0x200u) != 0u) {
        c.ground = int(ulo & 0x3FFFu);
        c.floor = int(uhi & 0x1FFu) - 64;
        c.wet = int((uhi >> 10u) & 1u);
    }
    return c;
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

void hide() {
    gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
}

void main() {
    int stride = Slots.y;
    int perRow = EDGE / stride;
    ivec2 col = ivec2(gl_InstanceIndex % perRow, gl_InstanceIndex / perRow) * stride;
    Column here = fetchColumn(col);
    int kind = int(UV0.x + 0.5);
    layer = kind / 3;
    face = kind - layer * 3;
    water = here.water;
    groundWet = here.wet;
    skirt = 0.0;
    topBlock = here.block;
    if (here.block == 0 || (layer == 1 && (here.depth == 0 || here.ground == 0))) {
        hide();
        return;
    }
    if (layer == 1
        && (here.h - here.depth - here.floor > MAX_GAP
            || !fetchColumn(col + ivec2(stride, 0)).known
            || !fetchColumn(col + ivec2(-stride, 0)).known
            || !fetchColumn(col + ivec2(0, stride)).known
            || !fetchColumn(col + ivec2(0, -stride)).known)) {
        hide();
        return;
    }
    block = layer == 0 ? here.block : here.ground;
    float step = Tile.z;
    float width = step * float(stride);
    int h = layer == 0 ? here.h : here.floor;
    int underside = here.depth > 0 ? here.h - here.depth + 1 : NONE;
    float top = float(h + 1) - Extra.x;
    float x0 = Tile.x + float(col.x) * step;
    float z0 = Tile.y + float(col.y) * step;
    vec3 pos;
    sideHeight = 0.0;
    side = 0;
    if (face == 0) {
        pos = vec3(x0 + Position.x * width, top, z0 + Position.z * width);
        local = Position.xz * width;
    } else {
        bool zFace = face == 1;
        float sign = zFace ? Params.y : Params.z;
        side = zFace ? (sign > 0.0 ? 1 : 0) : (sign > 0.0 ? 3 : 2);
        ivec2 offset = zFace ? ivec2(0, sign > 0.0 ? stride : -stride)
                             : ivec2(sign > 0.0 ? stride : -stride, 0);
        Column next = fetchColumn(col + offset);
        int low;
        if (layer == 0) {
            low = next.known ? next.h : h - SKIRT;
            if (next.known && underside != NONE) {
                int cut = max(underside - 1, h - CUT);
                if (cut > low) {
                    low = cut;
                    skirt = 1.0;
                }
            }
            if (!next.known) {
                skirt = 1.0;
            }
            if (water == 1 && next.known && next.water == 1) {
                low = h;
            }
        } else {
            low = !next.known ? h - SKIRT
                : next.depth > 0 ? (next.ground != 0 ? next.floor : h - SKIRT) : next.h;
        }
        if (h - low > WALL) {
            low = h - WALL;
            skirt = 1.0;
        }
        if (low >= h) {
            hide();
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
    vec3 color = texelFetch(Sampler0, slotOrigin(Slots.x) + col, 0).rgb;
    if (water == 0) {
        Column westColumn = fetchColumn(col + ivec2(-stride, 0));
        Column northColumn = fetchColumn(col + ivec2(0, -stride));
        int west = westColumn.known ? westColumn.h : here.h;
        int north = northColumn.known ? northColumn.h : here.h;
        float light = relief(here.h, west, north);
        if (step < 1.5) {
            light *= grain(World.x + col.x, World.y + col.y);
        }
        color = unlight(color, light);
    }
    raw = clamp(color, 0.0, 1.0);
    relY = pos.y;
    fogDistance = length(pos - Eye.xyz);
    gl_Position = ViewProj * vec4(pos, 1.0);
}
