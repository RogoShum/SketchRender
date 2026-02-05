#version 430

// Camera matrices
uniform mat4 sketch_cullingViewMat;
uniform mat4 sketch_cullingProjMat;

// Vertex attributes
in vec3 Position;
in vec2 UV;
in vec3 Normal;

in int TransformID;

// World matrices computed by the transform compute shader
layout(std430, binding = 1) readonly buffer TransformOutputBuffer {
    mat4 worldMatrices[];
};

// Output
out vec3 normal;
out vec2 uv;

void main() {
    // Get the world matrix from the SSBO
    mat4 worldMatrix = mat4(1.0);
    if (TransformID >= 0) {
        worldMatrix = worldMatrices[TransformID];
    }
    
    // Apply world matrix to position
    vec4 worldPos = worldMatrix * vec4(Position, 1.0);
    
    // Project to clip space
    gl_Position = sketch_cullingProjMat * sketch_cullingViewMat * worldPos;
    
    // Transform normal by the rotation part of the world matrix
    mat3 normalMatrix = mat3(worldMatrix);
    normal = normalize(normalMatrix * Normal);
    
    uv = UV;
}

