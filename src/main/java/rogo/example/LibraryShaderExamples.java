package rogo.example;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import rogo.sketch.api.ShaderResource;
import rogo.sketch.render.shader.ComputeShaderProgram;
import rogo.sketch.render.shader.GraphicsShaderProgram;
import rogo.sketch.util.Identifier;

/**
 * Examples showing how to use the library version of shaders 
 * (without Minecraft dependencies)
 */
public class LibraryShaderExamples {

    /**
     * Example: Create a simple graphics shader from GLSL source code
     */
    public static void simpleGraphicsShaderExample() throws Exception {
        String vertexSource = """
            #version 330 core
            layout (location = 0) in vec3 position;
            layout (location = 1) in vec3 normal;
            layout (location = 2) in vec2 texCoord;
            
            uniform mat4 mvpMatrix;
            uniform mat4 modelMatrix;
            uniform mat4 normalMatrix;
            
            out vec3 fragPos;
            out vec3 fragNormal;
            out vec2 fragTexCoord;
            
            void main() {
                fragPos = vec3(modelMatrix * vec4(position, 1.0));
                fragNormal = mat3(normalMatrix) * normal;
                fragTexCoord = texCoord;
                
                gl_Position = mvpMatrix * vec4(position, 1.0);
            }
            """;

        String fragmentSource = """
            #version 330 core
            in vec3 fragPos;
            in vec3 fragNormal;
            in vec2 fragTexCoord;
            
            uniform vec4 baseColor;
            uniform vec3 lightPosition;
            uniform vec3 lightColor;
            uniform float ambientStrength;
            
            out vec4 fragColor;
            
            void main() {
                // Ambient lighting
                vec3 ambient = ambientStrength * lightColor;
                
                // Diffuse lighting
                vec3 norm = normalize(fragNormal);
                vec3 lightDir = normalize(lightPosition - fragPos);
                float diff = max(dot(norm, lightDir), 0.0);
                vec3 diffuse = diff * lightColor;
                
                vec3 result = (ambient + diffuse) * baseColor.rgb;
                fragColor = vec4(result, baseColor.a);
            }
            """;

        // Create shader from source code directly
        GraphicsShaderProgram shader = GraphicsShaderProgram.create(
                Identifier.of("basic_lighting_shader"),
            vertexSource, 
            fragmentSource
        );

        System.out.println("Created graphics shader: " + shader.getIdentifier());
        System.out.println("Vertex format: " + shader.getVertexFormat());

        // Set uniforms
        ShaderResource<Matrix4f> mvpMatrix = (ShaderResource<Matrix4f>) 
            shader.getUniformHookGroup().getUniform("mvpMatrix");
        ShaderResource<Vector4f> baseColor = (ShaderResource<Vector4f>) 
            shader.getUniformHookGroup().getUniform("baseColor");
        ShaderResource<Vector3f> lightPosition = (ShaderResource<Vector3f>) 
            shader.getUniformHookGroup().getUniform("lightPosition");

        if (mvpMatrix != null) {
            mvpMatrix.set(new Matrix4f().identity());
        }
        if (baseColor != null) {
            baseColor.set(new Vector4f(0.8f, 0.3f, 0.2f, 1.0f));
        }
        if (lightPosition != null) {
            lightPosition.set(new Vector3f(5.0f, 5.0f, 5.0f));
        }

        shader.close();
    }

    /**
     * Example: Create a compute shader for particle simulation
     */
    public static void computeShaderExample() throws Exception {
        String computeSource = """
            #version 430
            layout(local_size_x = 64, local_size_y = 1, local_size_z = 1) in;
            
            layout(std430, binding = 0) buffer ParticleBuffer {
                vec4 positions[];
            };
            
            layout(std430, binding = 1) buffer VelocityBuffer {
                vec4 velocities[];
            };
            
            uniform float deltaTime;
            uniform vec3 gravity;
            uniform float damping;
            
            void main() {
                uint index = gl_GlobalInvocationID.x;
                
                if (index >= positions.length()) {
                    return;
                }
                
                // Apply gravity
                velocities[index].xyz += gravity * deltaTime;
                
                // Apply damping
                velocities[index].xyz *= damping;
                
                // Update position
                positions[index].xyz += velocities[index].xyz * deltaTime;
                
                // Simple ground collision
                if (positions[index].y < 0.0) {
                    positions[index].y = 0.0;
                    velocities[index].y = abs(velocities[index].y) * 0.8;
                }
            }
            """;

        ComputeShaderProgram computeShader = new ComputeShaderProgram(Identifier.of("particle_simulation"), computeSource);

        System.out.println("Created compute shader: " + computeShader.getIdentifier());

        // Set uniforms
        ShaderResource<Float> deltaTime = (ShaderResource<Float>) 
            computeShader.getUniformHookGroup().getUniform("deltaTime");
        ShaderResource<Vector3f> gravity = (ShaderResource<Vector3f>) 
            computeShader.getUniformHookGroup().getUniform("gravity");
        ShaderResource<Float> damping = (ShaderResource<Float>) 
            computeShader.getUniformHookGroup().getUniform("damping");

        if (deltaTime != null) {
            deltaTime.set(0.016f); // 60 FPS
        }
        if (gravity != null) {
            gravity.set(new Vector3f(0, -9.81f, 0));
        }
        if (damping != null) {
            damping.set(0.98f);
        }

        // Dispatch compute work
        computeShader.dispatch(1000 / 64 + 1); // Process 1000 particles with 64 per work group
        computeShader.shaderStorageBarrier();

        computeShader.close();
    }

    /**
     * Example: Advanced graphics shader with all shader stages
     */
    public static void advancedGraphicsShaderExample() throws Exception {
        String vertexSource = """
            #version 400 core
            layout (location = 0) in vec3 position;
            
            uniform mat4 mvpMatrix;
            
            void main() {
                gl_Position = mvpMatrix * vec4(position, 1.0);
            }
            """;

        String tessControlSource = """
            #version 400 core
            layout (vertices = 3) out;
            
            uniform float tessLevel;
            
            void main() {
                gl_TessLevelInner[0] = tessLevel;
                gl_TessLevelOuter[0] = tessLevel;
                gl_TessLevelOuter[1] = tessLevel;
                gl_TessLevelOuter[2] = tessLevel;
                
                gl_out[gl_InvocationID].gl_Position = gl_in[gl_InvocationID].gl_Position;
            }
            """;

        String tessEvalSource = """
            #version 400 core
            layout (triangles, equal_spacing, ccw) in;
            
            void main() {
                gl_Position = (gl_TessCoord.x * gl_in[0].gl_Position + 
                              gl_TessCoord.y * gl_in[1].gl_Position + 
                              gl_TessCoord.z * gl_in[2].gl_Position);
            }
            """;

        String geometrySource = """
            #version 400 core
            layout (triangles) in;
            layout (triangle_strip, max_vertices = 3) out;
            
            out vec3 worldPos;
            
            void main() {
                for (int i = 0; i < 3; i++) {
                    worldPos = gl_in[i].gl_Position.xyz;
                    gl_Position = gl_in[i].gl_Position;
                    EmitVertex();
                }
                EndPrimitive();
            }
            """;

        String fragmentSource = """
            #version 400 core
            in vec3 worldPos;
            
            uniform vec4 color;
            
            out vec4 fragColor;
            
            void main() {
                fragColor = color;
            }
            """;

        GraphicsShaderProgram shader = GraphicsShaderProgram.builder(Identifier.of("advanced_graphics_shader"))
            .vertex(vertexSource)
            .tessControl(tessControlSource)
            .tessEvaluation(tessEvalSource)
            .geometry(geometrySource)
            .fragment(fragmentSource)
            .build();

        System.out.println("Created advanced graphics shader: " + shader.getIdentifier());
        System.out.println("Vertex format: " + shader.getVertexFormat());

        shader.close();
    }

    /**
     * Example: Error handling and shader compilation
     */
    public static void errorHandlingExample() {
        try {
            String invalidVertexSource = """
                #version 330 core
                layout (location = 0) in vec3 position;
                
                // This will cause a compilation error
                uniform mat4 invalidSyntax = ;
                
                void main() {
                    gl_Position = vec4(position, 1.0);
                }
                """;

            String fragmentSource = """
                #version 330 core
                out vec4 fragColor;
                
                void main() {
                    fragColor = vec4(1.0, 0.0, 0.0, 1.0);
                }
                """;

            GraphicsShaderProgram.create(Identifier.valueOf("error_test"), invalidVertexSource, fragmentSource);
            
        } catch (Exception e) {
            System.out.println("Expected shader compilation error: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        try {
            simpleGraphicsShaderExample();
            computeShaderExample();
            advancedGraphicsShaderExample();
            errorHandlingExample();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}