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
uniform vec3 TestPos;
uniform vec3 FrustumPos;
uniform float RenderDistance;

flat in int spacePartitionSize;
flat in vec4[6] frustum;
flat in vec2[6] DepthScreenSize;

out vec4 fragColor;

float near = 0.05;
float far  = 16.0;

int getSampler(float xLength, float yLength) {
    for (int i = 0; i < DepthScreenSize.length(); ++i) {
        float xStep = 2.0 / DepthScreenSize[i].x;
        float yStep = 2.0 / DepthScreenSize[i].y;
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

vec3 worldToScreenSpace(vec3 pos) {
    vec4 cameraSpace = CullingProjMat * CullingViewMat * vec4(pos, 1);
    vec3 ndc;

    float w = cameraSpace.w;
    if (w < 0.0) {
        ndc.xy = cameraSpace.xy / -w;
        ndc.z = -2;
    } else {
        ndc.xy = cameraSpace.xy / w;
        ndc.z = cameraSpace.z / w;
    }

    return (ndc + vec3(1.0)) * 0.5;
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

bool isVisible(vec3 vec) {
    float minX, minY, minZ, maxX, maxY, maxZ;
    minX = vec.x - 8;
    minY = vec.y - 8;
    minZ = vec.z - 8;

    maxX = vec.x + 8;
    maxY = vec.y + 8;
    maxZ = vec.z + 8;
    return calculateCube(minX, minY, minZ, maxX, maxY, maxZ);
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

void main() {
    far = RenderDistance * 64.0;
    vec2 screenUV = gl_FragCoord.xy / ScreenSize.xy;

    vec3 chunkBasePos = TestPos;
    vec3 chunkPos = chunkBasePos*16;
    chunkPos = vec3(chunkPos.x, chunkPos.y, chunkPos.z)+vec3(8.0);

    if(!isVisible(chunkPos)) {
        fragColor = vec4(0.0, 0.0, 1.0, 1.0);
        return;
    }

    float sizeOffset = 12.0;
    vec3 aabb[8] = vec3[](
        chunkPos+vec3(-sizeOffset, -sizeOffset, -sizeOffset), chunkPos+vec3(sizeOffset, -sizeOffset, -sizeOffset),
        chunkPos+vec3(-sizeOffset, sizeOffset, -sizeOffset), chunkPos+vec3(sizeOffset, sizeOffset, -sizeOffset),
        chunkPos+vec3(-sizeOffset, -sizeOffset, sizeOffset), chunkPos+vec3(sizeOffset, -sizeOffset, sizeOffset),
        chunkPos+vec3(-sizeOffset, sizeOffset, sizeOffset), chunkPos+vec3(sizeOffset, sizeOffset, sizeOffset)
    );

    float maxX = 0.0;
    float maxY = 0.0;
    float minX = 1.0;
    float minY = 1.0;

    bool inside = false;
    bool intersect = false;
    float sectionDepth = 1.0;

    for (int i = 0; i < 8; ++i) {
        vec3 screenPos = worldToScreenSpace(aabb[i]);

        if (screenPos.x >= 0 && screenPos.x <= 1
        && screenPos.y >= 0 && screenPos.y <= 1
        && screenPos.z >= 0 && screenPos.z <= 1) {
            inside = true;
        } else {
            intersect = true;
        }

        if (screenPos.x > maxX)
        maxX = screenPos.x;
        if (screenPos.y > maxY)
        maxY = screenPos.y;
        if (screenPos.x < minX)
        minX = screenPos.x;
        if (screenPos.y < minY)
        minY = screenPos.y;

        sectionDepth = min(screenPos.z, sectionDepth);
    }

    sectionDepth = LinearizeDepth(sectionDepth);
    float chunkCenterDepth = worldToScreenSpace(moveTowardsCamera(chunkPos, 16.0)).z;

    int idx = getSampler(maxX-minX, maxY-minY);

    int depthX = int(DepthScreenSize[idx].x);
    int depthY = int(DepthScreenSize[idx].y);

    int coordMinX = max(int(floor(minX * depthX)), 0);
    int coordMaxX = min(int(ceil(maxX * depthX)), depthX - 1);
    int coordMinY = max(int(floor(minY * depthY)), 0);
    int coordMaxY = min(int(ceil(maxY * depthY)), depthY - 1);

    int aabbMinX = max(int(floor(minX * ScreenSize.x)), 0);
    int aabbMaxX = min(int(ceil(maxX * ScreenSize.x)), int(ScreenSize.x - 1));
    int aabbMinY = max(int(floor(minY * ScreenSize.y)), 0);
    int aabbMaxY = min(int(ceil(maxY * ScreenSize.y)), int(ScreenSize.y - 1));

    ivec2 screenCoords = ivec2(screenUV.x * depthX, screenUV.y * depthY);

    fragColor = vec4(0.0, 0.0, 0.0, 1.0);

    if(gl_FragCoord.x >= aabbMinX && gl_FragCoord.x <= aabbMaxX
    && gl_FragCoord.y >= aabbMinY && gl_FragCoord.y <= aabbMaxY) {
        fragColor = vec4(1.0, 1.0, 0.0, 1.0);
        if(screenCoords.x >= coordMinX && screenCoords.x <= coordMaxX
        && screenCoords.y >= coordMinY && screenCoords.y <= coordMaxY) {
            float pixelDepth = getUVDepth(idx, screenCoords);
            if(sectionDepth < pixelDepth) {
                fragColor.r = 0.0;
            } else {
                fragColor.g = 0.0;
            }
        }
    }
}