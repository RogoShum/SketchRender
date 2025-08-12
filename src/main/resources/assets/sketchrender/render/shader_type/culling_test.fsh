#version 150

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;
uniform sampler2D Sampler6;
uniform sampler2D Sampler7;

uniform vec2 ScreenSize;
uniform mat4 CullingViewMat;
uniform mat4 CullingProjMat;
uniform vec3 CullingCameraPos;
uniform vec3 CullingCameraDir;
uniform vec4 TestPos;
uniform vec4 TestEntityPos;
uniform vec3 TestEntityAABB;
uniform vec3 FrustumPos;
uniform float RenderDistance;

flat in int spacePartitionSize;
flat in vec4[6] frustum;
flat in vec2[8] DepthScreenSize;

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
        float xStep = 2.10 / DepthScreenSize[i].x;
        float yStep = 2.10 / DepthScreenSize[i].y;
        if (xStep > xLength && yStep > yLength) {
            return i;
        }
    }

    return DepthScreenSize.length() - 1;
}

float LinearizeDepth(float z) {
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

ClipResult getClippedMinDepth(vec3 center, float extentWidth, float extentHeight) {
    ClipResult result;
    result.minDepth = 1.0;
    result.screenMin = vec2(1.0);
    result.screenMax = vec2(0.0);

    mat4 mvp = CullingProjMat * CullingViewMat;

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

bool isVisible(vec3 center, vec3 min, vec3 max) {
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
    else if (idx == 5)
    return texelFetch(Sampler5, uv, 0).r;
    else if (idx == 6)
    return texelFetch(Sampler6, uv, 0).r;

    return texelFetch(Sampler7, uv, 0).r;
}

bool isBehindCamera(vec3 center, vec3 cameraPos, vec3 cameraForward) {
    vec3 toObject = center - cameraPos;
    return dot(normalize(toObject), cameraForward) < 0.0;
}

void main() {
    fragColor = vec4(0.0, 0.0, 0.0, 0.0);

    bool hit = false;
    if (TestPos.w > 0) {
        far = RenderDistance * CHUNK_RANGE_SIZE;
        vec2 screenUV = (gl_FragCoord.xy - vec2(0.5)) / ScreenSize.xy;

        vec3 chunkBasePos = TestPos.xyz;
        vec3 chunkPos = chunkBasePos * CHUNK_SIZE + vec3(8.0);

        vec3 boxMin = chunkPos - vec3(9.0);
        vec3 boxMax = chunkPos + vec3(9.0);
        if(!isVisible(chunkPos, boxMin, boxMax)) {
            fragColor = vec4(0.0, 0.0, 0.0, 0.0);
            return;
        }

        // --------- Wireframe drawing logic start ---------
        vec3 center = chunkPos;
        vec3 corners[8] = vec3[](
        vec3(boxMin.x, boxMin.y, boxMin.z),
        vec3(boxMax.x, boxMin.y, boxMin.z),
        vec3(boxMin.x, boxMax.y, boxMin.z),
        vec3(boxMax.x, boxMax.y, boxMin.z),
        vec3(boxMin.x, boxMin.y, boxMax.z),
        vec3(boxMax.x, boxMin.y, boxMax.z),
        vec3(boxMin.x, boxMax.y, boxMax.z),
        vec3(boxMax.x, boxMax.y, boxMax.z)
        );

        // Project to screen space
        vec2 projected[8];
        mat4 mvp = CullingProjMat * CullingViewMat;

        for (int i = 0; i < 8; i++) {
            vec4 clip = mvp * vec4(corners[i], 1.0);
            vec2 ndc;

            if (clip.w > 0) {
                float maxW = max(abs(clip.x), abs(clip.y));
                clip.w = max(clip.w, maxW);
                clip /= clip.w;

                ndc = clip.xy;
            } else {
                clip /= -clip.w;
                ndc = vec2(
                clip.x >= 0.0 ? 1.0 : -1.0,
                clip.y >= 0.0 ? 1.0 : -1.0
                );
            }

            projected[i] = (ndc * 0.5 + 0.5) * ScreenSize;
        }


        // 12 edges (pairs of indices into corners)
        int edges[24] = int[](
        0,1, 1,3, 3,2, 2,0, // bottom
        4,5, 5,7, 7,6, 6,4, // top
        0,4, 1,5, 2,6, 3,7  // sides
        );

        vec2 fragCoord = gl_FragCoord.xy;
        float thickness = 2.0; // Outline width in pixels
        bool nearEdge = false;
        for (int i = 0; i < 12; i++) {
            vec2 a = projected[edges[i * 2 + 0]];
            vec2 b = projected[edges[i * 2 + 1]];
            vec2 ab = b - a;
            vec2 ap = fragCoord - a;
            float t = clamp(dot(ap, ab) / dot(ab, ab), 0.0, 1.0);
            vec2 closest = a + t * ab;
            float dist = length(fragCoord - closest);
            if (dist < thickness) {
                nearEdge = true;
                break;
            }
        }

        if (nearEdge) {
            fragColor = vec4(1.0); // white outline
            return;
        }
        // --------- Wireframe drawing logic end ---------

        ClipResult clip = getClippedMinDepth(chunkPos + CullingCameraDir, 10.0, 10.0);

        if(any(greaterThan(clip.screenMin, clip.screenMax))) {
            fragColor = vec4(0.0, 0.0, 0.0, 0.0);
            return;
        }

        float sectionDepth = LinearizeDepth(clip.minDepth);

        vec2 mins = clamp(clip.screenMin, 0.0, 1.0);
        vec2 maxs = clamp(clip.screenMax, 0.0, 1.0);

        int idx = getSampler(maxs.x - mins.x, maxs.y - mins.y);
        vec2 depthSize = DepthScreenSize[idx];

        ivec2 coordMin = max(ivec2(floor(mins * depthSize)), ivec2(0));
        ivec2 coordMax = min(ivec2(ceil(maxs * depthSize)), ivec2(depthSize) - 1);

        ivec2 aabbMinScreen = max(ivec2(floor(mins * ScreenSize)), ivec2(0));
        ivec2 aabbMaxScreen = min(ivec2(ceil(maxs * ScreenSize)), ivec2(ScreenSize) - 1);

        ivec2 screenCoords = ivec2(screenUV * depthSize);

        fragColor = vec4(0.0, 0.0, 0.0, 0.0);

        if(all(greaterThanEqual(gl_FragCoord.xy - vec2(0.5), vec2(aabbMinScreen))) &&
        all(lessThanEqual(gl_FragCoord.xy - vec2(0.5), vec2(aabbMaxScreen)))) {
            fragColor = vec4(1.0, 1.0, 0.0, 1.0);

            if(all(greaterThanEqual(screenCoords, coordMin)) &&
            all(lessThanEqual(screenCoords, coordMax))) {
                float pixelDepth = getUVDepth(idx, screenCoords);
                fragColor.rg = vec2(sectionDepth >= pixelDepth ? 1.0 : 0.0,
                sectionDepth < pixelDepth ? 1.0 : 0.0);
                if (sectionDepth < pixelDepth) {
                    hit = true;
                }
            }
        }
    }

    if (TestEntityPos.w > 0) {
        far = RenderDistance * CHUNK_RANGE_SIZE;
        vec2 screenUV = (gl_FragCoord.xy - vec2(0.5)) / ScreenSize.xy;

        vec3 entityPos = TestEntityPos.xyz;
        vec3 boxMin = entityPos - vec3(TestEntityAABB.x / 2, 0, TestEntityAABB.z / 2);
        vec3 boxMax = entityPos + vec3(TestEntityAABB.x / 2, TestEntityAABB.y, TestEntityAABB.z / 2);

        if(!isVisible(entityPos, boxMin, boxMax)) {
            if (!hit) {
                fragColor = vec4(0.0, 0.0, 0.0, 0.0);
            }

            return;
        }

        // --------- Wireframe drawing logic start ---------
        vec3 center = entityPos;

        vec3 corners[8] = vec3[](
        vec3(boxMin.x, boxMin.y, boxMin.z),
        vec3(boxMax.x, boxMin.y, boxMin.z),
        vec3(boxMin.x, boxMax.y, boxMin.z),
        vec3(boxMax.x, boxMax.y, boxMin.z),
        vec3(boxMin.x, boxMin.y, boxMax.z),
        vec3(boxMax.x, boxMin.y, boxMax.z),
        vec3(boxMin.x, boxMax.y, boxMax.z),
        vec3(boxMax.x, boxMax.y, boxMax.z)
        );

        // Project to screen space
        vec2 projected[8];
        mat4 mvp = CullingProjMat * CullingViewMat;

        for (int i = 0; i < 8; i++) {
            vec4 clip = mvp * vec4(corners[i], 1.0);
            vec2 ndc;

            if (clip.w > 0) {
                float maxW = max(abs(clip.x), abs(clip.y));
                clip.w = max(clip.w, maxW);
                clip /= clip.w;

                ndc = clip.xy;
            } else {
                clip /= -clip.w;
                ndc = vec2(
                clip.x >= 0.0 ? 1.0 : -1.0,
                clip.y >= 0.0 ? 1.0 : -1.0
                );
            }

            projected[i] = (ndc * 0.5 + 0.5) * ScreenSize;
        }


        // 12 edges (pairs of indices into corners)
        int edges[24] = int[](
        0,1, 1,3, 3,2, 2,0, // bottom
        4,5, 5,7, 7,6, 6,4, // top
        0,4, 1,5, 2,6, 3,7  // sides
        );

        vec2 fragCoord = gl_FragCoord.xy;
        float thickness = 2.0; // Outline width in pixels
        bool nearEdge = false;
        for (int i = 0; i < 12; i++) {
            vec2 a = projected[edges[i * 2 + 0]];
            vec2 b = projected[edges[i * 2 + 1]];
            vec2 ab = b - a;
            vec2 ap = fragCoord - a;
            float t = clamp(dot(ap, ab) / dot(ab, ab), 0.0, 1.0);
            vec2 closest = a + t * ab;
            float dist = length(fragCoord - closest);
            if (dist < thickness) {
                nearEdge = true;
                break;
            }
        }

        if (nearEdge) {
            fragColor = vec4(1.0); // white outline
            return;
        }
        // --------- Wireframe drawing logic end ---------

        ClipResult clip = getClippedMinDepth(entityPos + vec3(0, TestEntityAABB.y / 2, 0), TestEntityAABB.x / 2 + 0.15, TestEntityAABB.y / 2 + 0.15);

        if(any(greaterThan(clip.screenMin, clip.screenMax))) {
            if (!hit) {
                fragColor = vec4(0.0, 0.0, 0.0, 0.0);
            }
            return;
        }

        float sectionDepth = LinearizeDepth(clip.minDepth);

        vec2 mins = clamp(clip.screenMin, 0.0, 1.0);
        vec2 maxs = clamp(clip.screenMax, 0.0, 1.0);

        int idx = getSampler(maxs.x - mins.x, maxs.y - mins.y);
        vec2 depthSize = DepthScreenSize[idx];

        ivec2 coordMin = max(ivec2(floor(mins * depthSize)), ivec2(0));
        ivec2 coordMax = min(ivec2(ceil(maxs * depthSize)), ivec2(depthSize) - 1);

        ivec2 aabbMinScreen = max(ivec2(floor(mins * ScreenSize)), ivec2(0));
        ivec2 aabbMaxScreen = min(ivec2(ceil(maxs * ScreenSize)), ivec2(ScreenSize) - 1);

        ivec2 screenCoords = ivec2(screenUV * depthSize);

        if (!hit) {
            fragColor = vec4(0.0, 0.0, 0.0, 0.0);
        }

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
}