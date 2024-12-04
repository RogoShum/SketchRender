#version 150

uniform sampler2D Sampler0;
uniform float RenderDistance;

flat in ivec2 ParentSize;
flat in float xMult;
flat in float yMult;

out vec4 fragColor;

float near = 0.1;
float far  = 16.0;

float LinearizeDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return (near * far) / (far + near - z * (far - near));
}

void main() {
    int px = int(gl_FragCoord.x * xMult);
    int py = int(gl_FragCoord.y * yMult);
    ivec2 depthUV = ivec2(px, py);
    float depth = 0.0;

    depth = max(depth, texelFetch(Sampler0, depthUV, 0).r);
    depth = max(depth, texelFetchOffset(Sampler0, depthUV, 0, ivec2(0, 1)).r);
    depth = max(depth, texelFetchOffset(Sampler0, depthUV, 0, ivec2(0, -1)).r);
    depth = max(depth, texelFetchOffset(Sampler0, depthUV, 0, ivec2(1, 0)).r);
    depth = max(depth, texelFetchOffset(Sampler0, depthUV, 0, ivec2(-1, 0)).r);
    depth = max(depth, texelFetchOffset(Sampler0, depthUV, 0, ivec2(1, 1)).r);
    depth = max(depth, texelFetchOffset(Sampler0, depthUV, 0, ivec2(1, -1)).r);
    depth = max(depth, texelFetchOffset(Sampler0, depthUV, 0, ivec2(-1, -1)).r);
    depth = max(depth, texelFetchOffset(Sampler0, depthUV, 0, ivec2(-1, 1)).r);

    if(RenderDistance > 1) {
        fragColor = vec4(vec3(depth), 1.0);
    } else {
        fragColor = vec4(vec3(LinearizeDepth(depth) / (far * 0.5)), 1.0);
    }
}
