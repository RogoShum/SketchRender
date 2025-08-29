// uniforms used in culling shaders
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;
uniform sampler2D Sampler6;
uniform sampler2D Sampler7;

uniform mat4 sketch_cullingViewMat;
uniform mat4 sketch_cullingProjMat;
uniform vec3 sketch_cullingCameraPos;
uniform vec3 sketch_cullingCameraDir;
uniform vec3 sketch_frustumPos;
uniform vec4[6] sketch_cullingFrustum;
uniform vec2[8] sketch_depthSize;
uniform int sketch_renderDistance;
uniform float sketch_cullingFineness;

// constants used in culling shaders
const float near = 0.05;
float far = 16.0;

// structures used in culling shaders
struct ClipResult {
    float minDepth;
    vec2 screenMin;
    vec2 screenMax;
};

// utility functions used in culling shaders
int getSampler(float xLength, float yLength) {
    for (int i = 0; i < sketch_depthSize.length(); ++i) {
        float xStep = sketch_cullingFineness / sketch_depthSize[i].x;
        float yStep = sketch_cullingFineness / sketch_depthSize[i].y;
        if (xStep > xLength && yStep > yLength) {
            return i;
        }
    }
    return sketch_depthSize.length() - 1;
}

float calculateDistance(vec3 P, vec3 Q) {
    return pow(Q.x - P.x, 2) + pow(Q.y - P.y, 2) + pow(Q.z - P.z, 2);
}

float LinearizeDepth(float z) {
    return (near * far) / (far + near - z * (far - near));
}

vec3 moveTowardsCamera(vec3 pos, float distance) {
    vec3 direction = normalize(pos - sketch_cullingCameraPos);
    vec3 newPos = pos - direction * distance;
    return newPos;
}

vec3 blockToChunk(vec3 blockPos) {
    vec3 chunkPos;
    chunkPos.x = floor(blockPos.x / 16.0);
    chunkPos.y = floor(blockPos.y / 16.0);
    chunkPos.z = floor(blockPos.z / 16.0);
    return chunkPos;
}

float getUVDepth(int idx, ivec2 uv) {
    if (idx == 0)
    return texelFetch(Sampler0, uv, 0).r;
    else if (idx == 1)
    return texelFetch(Sampler1, uv, 0).r;
    else if (idx == 2)
    return texelFetch(Sampler2, uv, 0).r;
    else if (idx == 3)
    return texelFetch(Sampler3, uv, 0).r;
    else if (idx == 4)
    return texelFetch(Sampler4, uv, 0).r;
    else if (idx == 5)
    return texelFetch(Sampler5, uv, 0).r;
    else if (idx == 6)
    return texelFetch(Sampler6, uv, 0).r;

    return texelFetch(Sampler7, uv, 0).r;
}

// Clipping and visibility functions used in culling shaders

ClipResult getClippedMinDepth(vec3 center, float extentWidth, float extentHeight) {
    ClipResult result;
    result.minDepth = 1.0;
    result.screenMin = vec2(1.0);
    result.screenMax = vec2(0.0);

    mat4 mvp = sketch_cullingProjMat * sketch_cullingViewMat;

    vec4 clipPositions[8];
    for(int i = 0; i < 8; i++) {
        vec3 vertex = center + vec3(
        (i & 1) == 0 ? -extentWidth : extentWidth,
        (i & 2) == 0 ? -extentHeight : extentHeight,
        (i & 4) == 0 ? -extentWidth : extentWidth
        );
        clipPositions[i] = mvp * vec4(vertex, 1.0);
    }

    for(int i = 0; i < 8; i++) {
        vec4 clipPos = clipPositions[i];
        vec2 ndcXY;

        if (clipPos.w > 0.0) {
            float maxW = max(abs(clipPos.x), abs(clipPos.y));
            clipPos.w = max(clipPos.w, maxW);
            clipPos /= clipPos.w;
            ndcXY = clipPos.xy;
            result.minDepth = min(result.minDepth, clipPos.z);
        } else {
            clipPos /= -clipPos.w;
            ndcXY = vec2(
            clipPos.x >= 0.0 ? 1.0 : -1.0,
            clipPos.y >= 0.0 ? 1.0 : -1.0
            );
            result.minDepth = min(result.minDepth, clipPos.z);
        }

        vec2 screenPos = ndcXY * 0.5 + 0.5;
        result.screenMin = min(result.screenMin, screenPos);
        result.screenMax = max(result.screenMax, screenPos);
    }

    return result;
}