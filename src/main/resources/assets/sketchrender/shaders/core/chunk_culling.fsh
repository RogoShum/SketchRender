#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;

uniform vec2 ScreenSize;
uniform vec2 CullingSize;
uniform mat4 CullingViewMat;
uniform mat4 CullingProjMat;
uniform vec3 CullingCameraPos;
uniform vec3 CullingCameraDir;
uniform vec3 FrustumPos;
uniform float RenderDistance;
uniform int LevelHeightOffset;
uniform int LevelMinSection;

flat in int spacePartitionSize;
flat in vec4[6] frustum;
flat in vec2[6] DepthScreenSize;

out vec4 fragColor;

float near = 0.05;
float far  = 16.0;

struct ClipResult {
    float minDepth;
    vec2 screenMin;
    vec2 screenMax;
};

int getSampler(float xLength, float yLength) {
    for (int i = 0; i < DepthScreenSize.length(); ++i) {
        float xStep = 4.0 / DepthScreenSize[i].x;
        float yStep = 4.0 / DepthScreenSize[i].y;
        if (xStep > xLength && yStep > yLength) {
            return i;
        }
    }

    return DepthScreenSize.length() - 1;
}

float LinearizeDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return (near * far) / (far + near - z * (far - near));
}

float calculateDistance(vec3 P, vec3 Q) {
    return pow(Q.x - P.x, 2) + pow(Q.y - P.y, 2) + pow(Q.z - P.z, 2);
}

vec4 computeNearIntersection(vec4 inside, vec4 outside, float clipValue, int axis) {
    // axis: 0 for X plane, 1 for Y plane
    float wa = inside.w;
    float wb = outside.w;
    float pa = axis == 0 ? inside.x : inside.y;
    float pb = axis == 0 ? outside.x : outside.y;

    float t = (wa * clipValue - pa) / ((pb - pa) - (wb - wa) * clipValue);
    return mix(inside, outside, t);
}

ClipResult getClippedMinDepth(vec3 center, float extent) {
    ClipResult result;
    result.minDepth = 1.0;
    result.screenMin = vec2(1.0);
    result.screenMax = vec2(0.0);

    mat4 mvp = CullingProjMat * CullingViewMat;
    bool hasValidPoint = false;

    vec4 clipPositions[8];
    for(int i = 0; i < 8; i++) {
        vec3 vertex = center + vec3(
        (i & 1) == 0 ? -extent : extent,
        (i & 2) == 0 ? -extent : extent,
        (i & 4) == 0 ? -extent : extent
        );
        clipPositions[i] = mvp * vec4(vertex, 1.0);
    }

    for(int i = 0; i < 8; i++) {
        vec4 clipPos = clipPositions[i];
        vec2 ndcXY;

        if(clipPos.w > 0.0) {
            ndcXY = clipPos.xy / clipPos.w;
        } else {
            ndcXY = clipPos.xy / -clipPos.w;
        }

        vec2 screenPos = (ndcXY + 1.0) * 0.5;

        result.screenMin = min(result.screenMin, screenPos);
        result.screenMax = max(result.screenMax, screenPos);

        if (clipPos.w > 0.0) {
            result.minDepth = min(result.minDepth, clipPos.z / clipPos.w);
            hasValidPoint = true;
        } else {
            result.minDepth = -2.0;
            hasValidPoint = true;
        }
    }

    return result;
}

vec3 moveTowardsCamera(vec3 pos, float distance) {
    vec3 direction = normalize(pos - CullingCameraPos);
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

bool cubeInFrustum(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    for (int i = 0; i < 6; ++i) {
        vec4 plane = frustum[i];
        if (!(dot(plane, vec4(minX, minY, minZ, 1.0)) > 0.0) &&
        !(dot(plane, vec4(maxX, minY, minZ, 1.0)) > 0.0) &&
        !(dot(plane, vec4(minX, maxY, minZ, 1.0)) > 0.0) &&
        !(dot(plane, vec4(maxX, maxY, minZ, 1.0)) > 0.0) &&
        !(dot(plane, vec4(minX, minY, maxZ, 1.0)) > 0.0) &&
        !(dot(plane, vec4(maxX, minY, maxZ, 1.0)) > 0.0) &&
        !(dot(plane, vec4(minX, maxY, maxZ, 1.0)) > 0.0) &&
        !(dot(plane, vec4(maxX, maxY, maxZ, 1.0)) > 0.0)) {
            return false;
        }
    }
    return true;
}

bool calculateCube(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
    float f = minX - FrustumPos.x;
    float f1 = minY - FrustumPos.y;
    float f2 = minZ - FrustumPos.z;
    float f3 = maxX - FrustumPos.x;
    float f4 = maxY - FrustumPos.y;
    float f5 = maxZ - FrustumPos.z;
    return cubeInFrustum(f, f1, f2, f3, f4, f5);
}

bool isVisible(vec3 center) {
    vec3 min = center - vec3(8.0);
    vec3 max = center + vec3(8.0);
    vec3 corners[8] = vec3[](
    vec3(min.x, min.y, min.z),
    vec3(max.x, min.y, min.z),
    vec3(min.x, max.y, min.z),
    vec3(max.x, max.y, min.z),
    vec3(min.x, min.y, max.z),
    vec3(max.x, min.y, max.z),
    vec3(min.x, max.y, max.z),
    vec3(max.x, max.y, max.z)
    );

    for(int i = 0; i < 6; i++) {
        bool inside = false;
        for(int j = 0; j < 8 && !inside; j++) {
            inside = dot(frustum[i], vec4(corners[j] - FrustumPos, 1.0)) > 0.0;
        }
        if(!inside) return false;
    }
    return true;
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

    return texelFetch(Sampler5, uv, 0).r;
}

bool isBehindCamera(vec3 center, vec3 cameraPos, vec3 cameraForward) {
    vec3 toObject = center - cameraPos;
    return dot(normalize(toObject), cameraForward) < 0.0;
}

void main() {
    int screenIndex = int(gl_FragCoord.x) + int(gl_FragCoord.y) * int(CullingSize.x);
    int renderDistance = int(RenderDistance);
    int spacePartitionSize = renderDistance*2+1;

    int chunkX = screenIndex / (spacePartitionSize * LevelHeightOffset) - renderDistance;
    int chunkZ = (screenIndex / LevelHeightOffset) % spacePartitionSize - renderDistance;
    int chunkY = screenIndex % LevelHeightOffset + LevelMinSection;
    vec3 chunkBasePos = vec3(chunkX, chunkY, chunkZ);
    vec3 chunkPos = vec3(chunkBasePos+blockToChunk(CullingCameraPos))*16;
    chunkPos = vec3(chunkPos.x + 8.0, chunkY*16 + 8.0, chunkPos.z + 8.0);

    /*
        if(isBehindCamera(chunkPos + (CullingCameraDir * 16), CullingCameraPos, CullingCameraDir)) {
        fragColor = vec4(0.0, 0.0, 1.0, 1.0);
        return;
    }

    if(length(chunkPos - CullingCameraPos) <= 50) {
        fragColor = vec4(1.0, 0.0, 0.0, 1.0);
        return;
    }
    */

    if (!isVisible(chunkPos)) {
        fragColor = vec4(0.0, 0.0, 1.0, 1.0);
        return;
    }

    far = RenderDistance * 64.0;

    ClipResult clip = getClippedMinDepth(chunkPos + (CullingCameraDir * -2), 9.0);

    float sectionDepth = (clip.minDepth + 1.0) * 0.5;
    sectionDepth = LinearizeDepth(sectionDepth);

    vec2 mins = clamp(clip.screenMin, 0.0, 1.0);
    vec2 maxs = clamp(clip.screenMax, 0.0, 1.0);

    int idx = getSampler(maxs.x - mins.x, maxs.y - mins.y);

    int depthX = int(DepthScreenSize[idx].x);
    int depthY = int(DepthScreenSize[idx].y);

    // 计算屏幕空间AABB的宽度和高度
    float screenWidth = (maxs.x - mins.x) * ScreenSize.x;
    float screenHeight = (maxs.y - mins.y) * ScreenSize.y;

    // 如果AABB小于一个像素，则标记为被剔除
    if (screenWidth < 3.0 && screenHeight < 3.0) {
        fragColor = vec4(0.0, 1.0, 0.0, 1.0); // 标记为剔除
        return;
    }

    int coordMinX = max(int(floor(mins.x * depthX)), 0);
    int coordMaxX = min(int(ceil(maxs.x * depthX)), depthX - 1);
    int coordMinY = max(int(floor(mins.y * depthY)), 0);
    int coordMaxY = min(int(ceil(maxs.y * depthY)), depthY - 1);

    for (int x = coordMinX; x <= coordMaxX; x++) {
        for (int y = coordMinY; y <= coordMaxY; y++) {
            float pixelDepth = getUVDepth(idx, ivec2(x, y));
            if (sectionDepth < pixelDepth) {
                fragColor = vec4(1.0, 0.0, 0.0, 1.0);
                return;
            }
        }
    }

    fragColor = vec4(0.0, 1.0, 0.0, 1.0);
}
