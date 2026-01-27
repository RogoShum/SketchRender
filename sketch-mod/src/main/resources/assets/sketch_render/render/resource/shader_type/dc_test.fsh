#version 150

uniform sampler2D depth;
uniform vec2 windowSize; // 屏幕分辨率，例如 vec2(1920, 1080)
uniform mat4 sketch_cullingProjMat; // 你的投影矩阵

in vec2 texCoord0;
out vec4 fragColor;

// 【核心函数】从深度重建观察空间坐标 (View Space Position)
vec3 getViewPos(vec2 uv, float depthValue) {
    // 1. 转换到 NDC (Normalized Device Coordinates)
    // x, y 范围 [-1, 1], z 范围 [-1, 1] (OpenGL标准)
    vec4 ndc = vec4(uv.x * 2.0 - 1.0, uv.y * 2.0 - 1.0, depthValue * 2.0 - 1.0, 1.0);

    // 2. 逆投影 (Projection -> View)
    // 建议：在CPU计算 invProj = inverse(sketch_cullingProjMat) 并传入，性能更好
    mat4 invProj = inverse(sketch_cullingProjMat);
    vec4 viewPos = invProj * ndc;

    // 3. 透视除法
    return viewPos.xyz / viewPos.w;
}

void main() {
    // 设置采样偏移 (Offset)
    // 建议设为 1.0 或 2.0。如果噪点多，设为 2.0
    float offset = 2.0;
    vec2 texelSize = (1.0 / windowSize) * offset;

    // 1. 获取中心点、右边点、上边点的深度
    float dCenter = texture(depth, texCoord0).r;
    float dRight  = texture(depth, texCoord0 + vec2(texelSize.x, 0.0)).r;
    float dUp     = texture(depth, texCoord0 + vec2(0.0, texelSize.y)).r;

    // 2. 简单过滤天空盒 (深度为1.0通常是远平面)
    if(dCenter >= 1.0) {
        fragColor = vec4(0.0, 0.0, 1.0, 0.0); // 默认指向观察者
        return;
    }

    // 3. 重建三个点的观察空间坐标
    vec3 pCenter = getViewPos(texCoord0, dCenter);
    vec3 pRight  = getViewPos(texCoord0 + vec2(texelSize.x, 0.0), dRight);
    vec3 pUp     = getViewPos(texCoord0 + vec2(0.0, texelSize.y), dUp);

    // 4. 计算切线向量 (注意：是在 View Space 内)
    vec3 tangentX = pRight - pCenter;
    vec3 tangentY = pUp - pCenter;

    // 5. 叉乘计算法线
    // OpenGL 坐标系通常右手定则：X朝右，Y朝上，Z朝屏幕外(观察者)
    // 这里的顺序取决于你的坐标系习惯，如果法线反了，交换 cross 的参数
    vec3 normal = normalize(cross(tangentY, tangentX));

    // 6. 输出结果
    // 结果范围 [-1, 1]。如果存入 SNORM 格式，直接输出 normal
    // 如果要可视化或者存入普通 RGB，需要 normal * 0.5 + 0.5
    fragColor = vec4(normal, 1.0);
}