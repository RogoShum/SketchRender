layout(local_size_x = 16, local_size_y = 16) in;

layout(r32f) uniform writeonly image2D output_texture_0;
uniform sampler2D hiz_source_snapshot;
#if HAS_SOURCE_ATLAS
uniform sampler2D hiz_source_atlas;
#endif

const float HIZ_NEAR = 0.05;
const float HIZ_FAR = 1024.0;

int levelExtent(int value, int packedLevel) {
    int extent = max(1, value >> (packedLevel + 1));
    return (extent & 1) == 1 ? extent + 1 : extent;
}

int levelYOffset(ivec2 originalScreen, int packedLevel) {
    int offset = 0;
    for (int index = 0; index < packedLevel; ++index) {
        offset += levelExtent(originalScreen.y, index);
    }
    return offset;
}

float linearizeDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return (HIZ_NEAR * HIZ_FAR) / (HIZ_FAR + HIZ_NEAR - z * (HIZ_FAR - HIZ_NEAR));
}

float readSourceDepth(ivec2 pos) {
#if HAS_SOURCE_ATLAS
    return texelFetch(hiz_source_atlas, pos, 0).r;
#else
    return linearizeDepth(texelFetch(hiz_source_snapshot, pos, 0).r);
#endif
}

void main() {
    ivec2 originalScreen = textureSize(hiz_source_snapshot, 0);
    int sourceLevel = DEST_LEVEL - 1;
    ivec2 sourceSize = DEST_LEVEL == 0
        ? originalScreen
        : ivec2(levelExtent(originalScreen.x, sourceLevel), levelExtent(originalScreen.y, sourceLevel));
    int sourceYOffset = DEST_LEVEL == 0 ? 0 : levelYOffset(originalScreen, sourceLevel);
    ivec2 destSize = ivec2(levelExtent(originalScreen.x, DEST_LEVEL), levelExtent(originalScreen.y, DEST_LEVEL));
    int destYOffset = levelYOffset(originalScreen, DEST_LEVEL);
    ivec2 outPos = ivec2(gl_GlobalInvocationID.xy);
    if (outPos.x >= destSize.x || outPos.y >= destSize.y) {
        return;
    }

    ivec2 sourceMax = sourceSize - ivec2(1);
    ivec2 base = outPos * 2;
    ivec2 p0 = min(base + ivec2(0, 0), sourceMax);
    ivec2 p1 = min(base + ivec2(1, 0), sourceMax);
    ivec2 p2 = min(base + ivec2(0, 1), sourceMax);
    ivec2 p3 = min(base + ivec2(1, 1), sourceMax);
    p0.y += sourceYOffset;
    p1.y += sourceYOffset;
    p2.y += sourceYOffset;
    p3.y += sourceYOffset;

    float d0 = readSourceDepth(p0);
    float d1 = readSourceDepth(p1);
    float d2 = readSourceDepth(p2);
    float d3 = readSourceDepth(p3);
    float hizDepth = max(max(d0, d1), max(d2, d3));
    imageStore(output_texture_0, ivec2(outPos.x, outPos.y + destYOffset), vec4(hizDepth, 0.0, 0.0, 1.0));
}
