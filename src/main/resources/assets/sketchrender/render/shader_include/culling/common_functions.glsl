// Common utility functions used in culling shaders

int getSampler(float xLength, float yLength) {
    for (int i = 0; i < sketch_depthSize.length(); ++i) {
        float xStep = 2.0 / sketch_depthSize[i].x;
        float yStep = 2.0 / sketch_depthSize[i].y;
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
