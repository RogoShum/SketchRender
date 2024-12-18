#version 150

uniform sampler2D Sampler0;
uniform float RenderDistance;
uniform int DepthIndex;

flat in ivec2 ParentSize;
flat in float xMult;
flat in float yMult;
flat in int xStep;
flat in int yStep;

out vec4 fragColor;

float near = 0.05;
float far  = 16.0;

float LinearizeDepth(float depth) {
    float z = depth * 2.0 - 1.0;
    return (near * far) / (far + near - z * (far - near));
}

void main() {
    far = RenderDistance * 64.0;
    int px = int(gl_FragCoord.x * xMult);
    int py = int(gl_FragCoord.y * yMult);
    ivec2 depthUV = ivec2(px, py);
    float depth = 0.0;

    for (int x = 0; x < xStep; x++) {
        for (int y = 0; y < yStep; y++) {
            depth = max(depth, texelFetch(Sampler0, depthUV + ivec2(x, y), 0).r);
        }
    }

    if(DepthIndex > 0) {
        fragColor = vec4(vec3(depth), 1.0);
    } else {
        fragColor = vec4(vec3(LinearizeDepth(depth)), 1.0);
    }
}