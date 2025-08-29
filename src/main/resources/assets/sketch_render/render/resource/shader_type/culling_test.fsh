#version 150

uniform vec2 windowSize;
uniform vec4 sketch_testPos;
uniform vec4 sketch_testEntityPos;
uniform vec3 sketch_testEntityAABB;
#import "hiz_culling.glsl"

out vec4 fragColor;

#define CHUNK_SIZE 16.0
#define CHUNK_RANGE_SIZE 64.0

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
            inside = dot(sketch_cullingFrustum[i], vec4(corners[j] - sketch_frustumPos, 1.0)) > 0.0;
        }
        if(!inside) return false;
    }
    return true;
}

void main() {
    fragColor = vec4(0.0, 0.0, 0.0, 0.0);

    bool hit = false;
    if (sketch_testPos.w > 0) {
        far = sketch_renderDistance * CHUNK_RANGE_SIZE;
        vec2 screenUV = (gl_FragCoord.xy - vec2(0.5)) / windowSize.xy;

        vec3 chunkBasePos = sketch_testPos.xyz;
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
        mat4 mvp = sketch_cullingProjMat * sketch_cullingViewMat;

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

            projected[i] = (ndc * 0.5 + 0.5) * windowSize;
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
            fragColor = vec4(1.0, 1.0, 1.0, 0.35); // white outline
            return;
        }
        // --------- Wireframe drawing logic end ---------

        ClipResult clip = getClippedMinDepth(chunkPos + sketch_cullingCameraDir, 10.0, 10.0);

        if(any(greaterThan(clip.screenMin, clip.screenMax))) {
            fragColor = vec4(0.0, 0.0, 0.0, 0.0);
            return;
        }

        float sectionDepth = LinearizeDepth(clip.minDepth);

        vec2 mins = clamp(clip.screenMin, 0.0, 1.0);
        vec2 maxs = clamp(clip.screenMax, 0.0, 1.0);

        int idx = getSampler(maxs.x - mins.x, maxs.y - mins.y);
        vec2 depthSize = sketch_depthSize[idx];

        ivec2 coordMin = max(ivec2(floor(mins * depthSize)), ivec2(0));
        ivec2 coordMax = min(ivec2(ceil(maxs * depthSize)), ivec2(depthSize) - 1);

        ivec2 aabbMinScreen = max(ivec2(floor(mins * windowSize)), ivec2(0));
        ivec2 aabbMaxScreen = min(ivec2(ceil(maxs * windowSize)), ivec2(windowSize) - 1);

        ivec2 screenCoords = ivec2(screenUV * depthSize);

        fragColor = vec4(0.0, 0.0, 0.0, 0.0);

        if(all(greaterThanEqual(gl_FragCoord.xy - vec2(0.5), vec2(aabbMinScreen))) &&
        all(lessThanEqual(gl_FragCoord.xy - vec2(0.5), vec2(aabbMaxScreen)))) {
            fragColor = vec4(1.0, 1.0, 0.0, 0.35);

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

    if (sketch_testEntityPos.w > 0) {
        far = sketch_renderDistance * CHUNK_RANGE_SIZE;
        vec2 screenUV = (gl_FragCoord.xy - vec2(0.5)) / windowSize.xy;

        vec3 entityPos = sketch_testEntityPos.xyz;
        vec3 boxMin = entityPos - vec3(sketch_testEntityAABB.x / 2, 0, sketch_testEntityAABB.z / 2);
        vec3 boxMax = entityPos + vec3(sketch_testEntityAABB.x / 2, sketch_testEntityAABB.y, sketch_testEntityAABB.z / 2);

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
        mat4 mvp = sketch_cullingProjMat * sketch_cullingViewMat;

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

            projected[i] = (ndc * 0.5 + 0.5) * windowSize;
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
            fragColor = vec4(1.0, 1.0, 1.0, 0.35); // white outline
            return;
        }
        // --------- Wireframe drawing logic end ---------

        ClipResult clip = getClippedMinDepth(entityPos + vec3(0, sketch_testEntityAABB.y / 2, 0), sketch_testEntityAABB.x / 2 + 0.15, sketch_testEntityAABB.y / 2 + 0.15);

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
        vec2 depthSize = sketch_depthSize[idx];

        ivec2 coordMin = max(ivec2(floor(mins * depthSize)), ivec2(0));
        ivec2 coordMax = min(ivec2(ceil(maxs * depthSize)), ivec2(depthSize) - 1);

        ivec2 aabbMinScreen = max(ivec2(floor(mins * windowSize)), ivec2(0));
        ivec2 aabbMaxScreen = min(ivec2(ceil(maxs * windowSize)), ivec2(windowSize) - 1);

        ivec2 screenCoords = ivec2(screenUV * depthSize);

        if (!hit) {
            fragColor = vec4(0.0, 0.0, 0.0, 0.0);
        }

        if(all(greaterThanEqual(gl_FragCoord.xy - vec2(0.5), vec2(aabbMinScreen))) &&
        all(lessThanEqual(gl_FragCoord.xy - vec2(0.5), vec2(aabbMaxScreen)))) {
            fragColor = vec4(1.0, 1.0, 0.0, 0.35);

            if(all(greaterThanEqual(screenCoords, coordMin)) &&
            all(lessThanEqual(screenCoords, coordMax))) {
                float pixelDepth = getUVDepth(idx, screenCoords);
                fragColor.rg = vec2(sectionDepth >= pixelDepth ? 1.0 : 0.0,
                sectionDepth < pixelDepth ? 1.0 : 0.0);
            }
        }
    }
}