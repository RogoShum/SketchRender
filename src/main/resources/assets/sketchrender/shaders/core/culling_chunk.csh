#version 430

// 输入 SSBO：数据提取源
layout(std430, binding = 0) buffer InputSSBO {
    int inputData[];
};

// 输出 SSBO：存储计算结果
layout(std430, binding = 1) buffer OutputSSBO {
    int outputData[];
};

// 纹理 Uniform：用于采样数据
uniform sampler2D inputTexture;

// 工作组大小
layout(local_size_x = 1) in;

// 获取纹理中的值
vec4 getTextureValue(int index) {
    // 假设索引对应纹理的 x 坐标，纹理高固定为 1 行
    float x = float(index) / textureSize(inputTexture, 0).x;
    return texture(inputTexture, vec2(x, 0.5));
}

// 主计算逻辑
void main() {
    uint id = gl_GlobalInvocationID.x; // 当前工作线程的索引

    // 防止越界访问
    if (id >= inputData.length()) return;

    // 从输入 SSBO 获取数据
    int inputValue = inputData[id];

    // 根据索引从纹理采样
    vec4 textureValue = getTextureValue(inputValue);

    // 假设我们计算一个简单的公式：纹理 r 通道加上输入值
    int result = int(textureValue.r * 255.0) + inputValue;

    // 将结果存储到输出 SSBO
    outputData[id] = result;
}
