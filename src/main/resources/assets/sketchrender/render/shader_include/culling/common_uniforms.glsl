// Common uniforms used in culling shaders
uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;
uniform sampler2D Sampler5;
uniform sampler2D Sampler6;
uniform sampler2D Sampler7;

uniform mat4 sketch_cullingViewMat;
uniform mat4 sketch_cullingProjMat;
uniform vec3 sketch_cullingCameraPos;
uniform vec3 sketch_cullingCameraDir;
uniform vec3 sketch_frustumPos;
uniform vec4[6] sketch_cullingFrustum;
uniform vec2[8] sketch_depthSize;
uniform int sketch_renderDistance;
