#version 150

uniform sampler2D depth;
uniform vec2 windowSize;
uniform mat4 sketch_cullingProjMat;

in vec2 texCoord0;
out vec4 fragColor;

vec3 getPos(vec2 uv, float depthValue) {
    vec4 ndc = vec4(uv.x * 2.0 - 1.0, uv.y * 2.0 - 1.0, depthValue * 2.0 - 1.0, 1.0);

    mat4 invProj = inverse(sketch_cullingProjMat);
    vec4 viewPos = invProj * ndc;

    return viewPos.xyz / viewPos.w;
}

vec3 computeNormal( const sampler2D depth, in vec2 uv ) {
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
        return vec3(0.0, 0.0, 1.0); // 默认指向观察者
    }

    // 3. 重建三个点的观察空间坐标
    vec3 pCenter = getPos(texCoord0, dCenter);
    vec3 pRight  = getPos(texCoord0 + vec2(texelSize.x, 0.0), dRight);
    vec3 pUp     = getPos(texCoord0 + vec2(0.0, texelSize.y), dUp);

    // 4. 计算切线向量 (注意：是在 View Space 内)
    vec3 tangentX = pRight - pCenter;
    vec3 tangentY = pUp - pCenter;

    // 5. 叉乘计算法线
    // OpenGL 坐标系通常右手定则：X朝右，Y朝上，Z朝屏幕外(观察者)
    // 这里的顺序取决于你的坐标系习惯，如果法线反了，交换 cross 的参数
    return normalize(cross(tangentX, tangentY));
}

// https://www.shadertoy.com/view/fsVczR
// computes the normal at pixel "p" based on the deph buffer "depth"
vec3 computeNormalImproved( const sampler2D depth, in vec2 uv )
{
    vec2 texelSize = (1.0 / windowSize);
    ivec2 p = ivec2(windowSize * uv);
    float c0 = texelFetch(depth,p           ,0).r;
    float l2 = texelFetch(depth,p-ivec2(2,0),0).r;
    float l1 = texelFetch(depth,p-ivec2(1,0),0).r;
    float r1 = texelFetch(depth,p+ivec2(1,0),0).r;
    float r2 = texelFetch(depth,p+ivec2(2,0),0).r;
    float b2 = texelFetch(depth,p-ivec2(0,2),0).r;
    float b1 = texelFetch(depth,p-ivec2(0,1),0).r;
    float t1 = texelFetch(depth,p+ivec2(0,1),0).r;
    float t2 = texelFetch(depth,p+ivec2(0,2),0).r;

    float dl = abs(l1*l2/(2.0*l2-l1)-c0);
    float dr = abs(r1*r2/(2.0*r2-r1)-c0);
    float db = abs(b1*b2/(2.0*b2-b1)-c0);
    float dt = abs(t1*t2/(2.0*t2-t1)-c0);

    vec3 ce = getPos(uv,c0);

    vec3 dpdx = (dl<dr) ?  ce-getPos((p-ivec2(1,0)) * texelSize,l1) :
    -ce+getPos((p+ivec2(1,0)) * texelSize,r1) ;
    vec3 dpdy = (db<dt) ?  ce-getPos((p-ivec2(0,1)) * texelSize,b1) :
    -ce+getPos((p+ivec2(0,1)) * texelSize,t1) ;

    return normalize(cross(dpdx,dpdy));
}

vec3 computeNormalRobustSec(const sampler2D depthMap, in vec2 uv) {
    vec2 texelSize = 1.0 / windowSize;

    // 1. 获取当前像素位置
    float depthCenter = texture(depth, texCoord0).r;
    vec3 posCenter = getPos(texCoord0, depthCenter);

    // 2. 获取左右像素位置
    // 增加 offset (如 1.5 或 2.0) 可以显著减少阶梯噪点
    float offset = 1.0;

    vec2 uvRight = texCoord0 + vec2(texelSize.x * offset, 0.0);
    vec2 uvLeft  = texCoord0 - vec2(texelSize.x * offset, 0.0);
    vec2 uvUp    = texCoord0 + vec2(0.0, texelSize.y * offset);
    vec2 uvDown  = texCoord0 - vec2(0.0, texelSize.y * offset);

    vec3 posRight = getPos(uvRight, texture(depth, uvRight).r);
    vec3 posLeft  = getPos(uvLeft,  texture(depth, uvLeft).r);
    vec3 posUp    = getPos(uvUp,    texture(depth, uvUp).r);
    vec3 posDown  = getPos(uvDown,  texture(depth, uvDown).r);

    // 3. 计算切线 (dx) 和 副切线 (dy)
    // 使用 min/max 或 abs 逻辑来避免跨越边缘（几何感知）

    vec3 dx = posRight - posLeft;
    vec3 dy = posUp - posDown;

    // 几何感知逻辑：如果左边和右边的深度差很大，可能跨越了边缘。
    // 这种情况下，使用离中心点更近的那个点来计算导数。
    if (abs(posRight.z - posCenter.z) < abs(posLeft.z - posCenter.z)) {
        dx = posRight - posCenter;
    } else if (abs(posLeft.z - posCenter.z) < abs(posRight.z - posCenter.z)){
        dx = posCenter - posLeft;
    }

    if (abs(posUp.z - posCenter.z) < abs(posDown.z - posCenter.z)) {
        dy = posUp - posCenter;
    } else if (abs(posDown.z - posCenter.z) < abs(posUp.z - posCenter.z)){
        dy = posCenter - posDown;
    }

    // 4. 计算法线
   return normalize(cross(dx, dy));
}

vec3 computeNormalRobust(const sampler2D depthMap, in vec2 uv) {
    vec2 texelSize = 1.0 / windowSize;

    // 获取中心点的深度
    float c0 = texture(depthMap, uv).r;

    // 采样十字形邻居 (Left, Right, Bottom, Top)
    // 使用 texture 而不是 texelFetch 可以获得硬件插值，或者手动偏移
    float l = texture(depthMap, uv - vec2(texelSize.x, 0.0)).r;
    float r = texture(depthMap, uv + vec2(texelSize.x, 0.0)).r;
    float b = texture(depthMap, uv - vec2(0.0, texelSize.y)).r;
    float t = texture(depthMap, uv + vec2(0.0, texelSize.y)).r;

    // 重构视图空间位置
    vec3 P_c = getPos(uv, c0);
    vec3 P_l = getPos(uv - vec2(texelSize.x, 0.0), l);
    vec3 P_r = getPos(uv + vec2(texelSize.x, 0.0), r);
    vec3 P_b = getPos(uv - vec2(0.0, texelSize.y), b);
    vec3 P_t = getPos(uv + vec2(0.0, texelSize.y), t);

    // 核心优化：边缘检测
    // 如果左右深度差太大，说明跨越了物体边缘，只取与中心点接近的一侧
    // 否则取 (Right - Left) 作为切线
    vec3 ddx, ddy;

    // 阈值，根据场景缩放调整，通常 0.1 到 1.0 之间
    float edgeThreshold = 0.1;

    if (abs(c0 - l) < abs(c0 - r)) {
        ddx = P_c - P_l; // 左侧更近，用左侧差分
    } else {
        ddx = P_r - P_c; // 右侧更近，用右侧差分
    }
    // 如果都不算边缘（平滑表面），使用跨度更大的中心差分（更平滑）
    if (abs(l - r) < edgeThreshold) {
        ddx = P_r - P_l;
    }

    if (abs(c0 - b) < abs(c0 - t)) {
        ddy = P_c - P_b;
    } else {
        ddy = P_t - P_c;
    }
    if (abs(b - t) < edgeThreshold) {
        ddy = P_t - P_b;
    }

    // 计算法线
    // 注意：根据你的坐标系手性，可能需要反转 cross 的顺序
    return normalize(cross(ddx, ddy));
}

void main() {
    vec3 normal = computeNormalRobustSec(depth, texCoord0);
    fragColor = vec4(normal, 1.0);
}