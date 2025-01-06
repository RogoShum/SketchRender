#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;

uniform vec2 ScreenSize;
uniform mat4 CullingViewMat;
uniform mat4 CullingProjMat;
uniform vec3 CullingCameraPos;
uniform vec3 CullingCameraDir;
uniform vec3 TestPos;
uniform vec3 FrustumPos;
uniform float RenderDistance;

flat in int spacePartitionSize;
flat in vec4[6] frustum;
flat in vec2[6] DepthScreenSize;

out vec4 fragColor;

float near = 0.05;
float far  = 16.0;
#define CHUNK_SIZE 16.0
#define CHUNK_RANGE_SIZE 64.0

struct ClipResult {
    float minDepth;
    vec2 screenMin;
    vec2 screenMax;
};

int getSampler(float xLength, float yLength) {
    for (int i = 0; i < DepthScreenSize.length(); ++i) {
        float xStep = 3.0 / DepthScreenSize[i].x;
        float yStep = 3.0 / DepthScreenSize[i].y;
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

void updateMinDepth(vec4 intersection, inout ClipResult result, inout bool hasValidPoint) {
    vec2 intersectionNDC = intersection.xy / intersection.w;
    result.minDepth = min(result.minDepth, intersection.z / intersection.w);
    hasValidPoint = true;
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
    far = RenderDistance * CHUNK_RANGE_SIZE;
    vec2 screenUV = (gl_FragCoord.xy - vec2(0.5)) / ScreenSize.xy;

    vec3 chunkBasePos = TestPos;
    vec3 chunkPos = chunkBasePos * CHUNK_SIZE + vec3(8.0);

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

    if(!isVisible(chunkPos)) {
        fragColor = vec4(0.0, 0.0, 1.0, 1.0);
        return;
    }

    ClipResult clip = getClippedMinDepth(chunkPos + (CullingCameraDir * -2), 9.0);

    if(any(greaterThan(clip.screenMin, clip.screenMax))) {
        fragColor = vec4(0.0, 0.0, 0.5, 1.0);
        return;
    }

    float sectionDepth = (clip.minDepth + 1.0) * 0.5;
    sectionDepth = LinearizeDepth(sectionDepth);

    vec2 mins = clamp(clip.screenMin, 0.0, 1.0);
    vec2 maxs = clamp(clip.screenMax, 0.0, 1.0);

    int idx = getSampler(maxs.x - mins.x, maxs.y - mins.y);
    vec2 depthSize = DepthScreenSize[idx];

    ivec2 coordMin = max(ivec2(floor(mins * depthSize)), ivec2(0));
    ivec2 coordMax = min(ivec2(ceil(maxs * depthSize)), ivec2(depthSize) - 1);

    ivec2 aabbMinScreen = max(ivec2(floor(mins * ScreenSize)), ivec2(0));
    ivec2 aabbMaxScreen = min(ivec2(ceil(maxs * ScreenSize)), ivec2(ScreenSize) - 1);

    ivec2 screenCoords = ivec2(screenUV * depthSize);

    fragColor = vec4(0.0, 0.0, 0.0, 1.0);

    if(all(greaterThanEqual(gl_FragCoord.xy - vec2(0.5), vec2(aabbMinScreen))) &&
    all(lessThanEqual(gl_FragCoord.xy - vec2(0.5), vec2(aabbMaxScreen)))) {
        fragColor = vec4(1.0, 1.0, 0.0, 1.0);

        if(all(greaterThanEqual(screenCoords, coordMin)) &&
        all(lessThanEqual(screenCoords, coordMax))) {
            float pixelDepth = getUVDepth(idx, screenCoords);
            fragColor.rg = vec2(sectionDepth >= pixelDepth ? 1.0 : 0.0,
            sectionDepth < pixelDepth ? 1.0 : 0.0);
        }
    }
}