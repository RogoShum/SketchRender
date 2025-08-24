// Clipping and visibility functions used in culling shaders

ClipResult getClippedMinDepth(vec3 center, float extent) {
    ClipResult result;
    result.minDepth = 1.0;
    result.screenMin = vec2(1.0);
    result.screenMax = vec2(0.0);

    mat4 mvp = sketch_cullingProjMat * sketch_cullingViewMat;

    vec4 clipPositions[8];
    for (int i = 0; i < 8; i++) {
        vec3 vertex = center + vec3(
            (i & 1) == 0 ? -extent : extent,
            (i & 2) == 0 ? -extent : extent,
            (i & 4) == 0 ? -extent : extent
        );
        clipPositions[i] = mvp * vec4(vertex, 1.0);
    }

    for (int i = 0; i < 8; i++) {
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

// Overloaded version for entity culling with separate width and height
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
