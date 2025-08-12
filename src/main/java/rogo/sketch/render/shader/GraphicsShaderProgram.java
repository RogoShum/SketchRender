package rogo.sketch.render.shader;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;
import rogo.sketch.render.data.format.DataElement;
import rogo.sketch.render.data.format.DataFormat;
import rogo.sketch.render.shader.uniform.DataType;
import rogo.sketch.util.Identifier;

import java.io.IOException;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Graphics shader program for rasterization pipeline
 * Supports vertex, fragment, geometry, and tessellation shaders
 */
public class GraphicsShaderProgram extends Shader {
    private DataFormat vertexFormat;

    /**
     * Create a graphics shader program from GLSL sources
     */
    public GraphicsShaderProgram(Identifier identifier, Map<ShaderType, String> shaderSources) throws IOException {
        super(identifier, shaderSources);
    }

    @Override
    protected void postLinkInitialization() {
        super.postLinkInitialization();
        collectVertexAttributes();
    }

    @Override
    protected void validateShaderTypes(Map<ShaderType, String> shaderSources) {
        // Must have vertex and fragment shaders
        if (!shaderSources.containsKey(ShaderType.VERTEX)) {
            throw new IllegalArgumentException("Graphics shader program requires a vertex shader");
        }
        if (!shaderSources.containsKey(ShaderType.FRAGMENT)) {
            throw new IllegalArgumentException("Graphics shader program requires a fragment shader");
        }

        // Validate that only graphics shader types are used
        for (ShaderType type : shaderSources.keySet()) {
            if (!type.isGraphicsShader()) {
                throw new IllegalArgumentException("Graphics shader program cannot contain compute shaders");
            }
        }

        // Check tessellation shader dependencies
        boolean hasTessControl = shaderSources.containsKey(ShaderType.TESS_CONTROL);
        boolean hasTessEval = shaderSources.containsKey(ShaderType.TESS_EVALUATION);
        if (hasTessControl != hasTessEval) {
            throw new IllegalArgumentException("Tessellation shaders must be used together (both control and evaluation)");
        }
    }

    /**
     * Collect vertex attributes from the shader program and build a vertex format
     */
    private void collectVertexAttributes() {
        bind(); // Ensure the program is bound

        int attributeCount = GL20.glGetProgrami(program, GL20.GL_ACTIVE_ATTRIBUTES);
        List<DataElement> elements = new ArrayList<>();

        for (int i = 0; i < attributeCount; i++) {
            IntBuffer size = BufferUtils.createIntBuffer(1);
            IntBuffer type = BufferUtils.createIntBuffer(1);
            String attributeName = GL20.glGetActiveAttrib(program, i, size, type);

            // Skip built-in attributes
            if (attributeName.startsWith("gl_")) {
                continue;
            }

            int location = GL20.glGetAttribLocation(program, attributeName);
            if (location >= 0) {
                DataType dataType = inferAttributeType(type.get(0));
                if (dataType != null) {
                    boolean normalized = shouldNormalizeAttribute(attributeName, dataType);
                    DataElement element = new DataElement(attributeName, dataType, location, normalized);
                    elements.add(element);
                }
            }
        }

        // Sort elements by location for consistent layout
        elements.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));

        // Build the vertex format
        this.vertexFormat = new DataFormat("ShaderVertexFormat_" + identifier.toString(), elements);

        System.out.println("Collected vertex format for shader " + identifier + ": " + vertexFormat);
    }

    /**
     * Infer data type from OpenGL attribute type
     */
    private DataType inferAttributeType(int glType) {
        return switch (glType) {
            case GL20.GL_FLOAT -> DataType.FLOAT;
            case GL20.GL_FLOAT_VEC2 -> DataType.VEC2;
            case GL20.GL_FLOAT_VEC3 -> DataType.VEC3;
            case GL20.GL_FLOAT_VEC4 -> DataType.VEC4;
            case GL20.GL_INT -> DataType.INT;
            case GL20.GL_INT_VEC2 -> DataType.VEC2I;
            case GL20.GL_INT_VEC3 -> DataType.VEC3I;
            case GL20.GL_INT_VEC4 -> DataType.VEC4I;
            case GL20.GL_UNSIGNED_INT -> DataType.UINT;
            case GL20.GL_FLOAT_MAT2 -> DataType.MAT2;
            case GL20.GL_FLOAT_MAT3 -> DataType.MAT3;
            case GL20.GL_FLOAT_MAT4 -> DataType.MAT4;
            default -> {
                System.err.println("Unknown vertex attribute type: " + glType);
                yield null;
            }
        };
    }

    /**
     * Determine if an attribute should be normalized based on naming conventions
     */
    private boolean shouldNormalizeAttribute(String name, DataType type) {
        String lowerName = name.toLowerCase();

        // Common normalized attributes
        if (lowerName.contains("color") && (type.isByteType() || type.isShortType())) {
            return true;
        }
        if (lowerName.contains("normal") && (type.isByteType() || type.isShortType())) {
            return true;
        }

        return false;
    }

    /**
     * Get the vertex format expected by this shader
     */
    public DataFormat getVertexFormat() {
        return vertexFormat;
    }

    // ShaderProvider interface implementation
    @Override
    public Identifier getIdentifier() {
        return Identifier.of(identifier.toString());
    }

    @Override
    public UniformHookGroup getUniformHookGroup() {
        return uniformHookGroup;
    }

    @Override
    public int getHandle() {
        return program;
    }

    /**
     * Check if a vertex buffer format is compatible with this shader
     */
    public boolean isCompatibleWith(DataFormat bufferFormat) {
        return vertexFormat != null && vertexFormat.isCompatibleWith(bufferFormat);
    }

    /**
     * Check if a vertex buffer format exactly matches this shader's requirements
     */
    public boolean exactlyMatches(DataFormat bufferFormat) {
        return vertexFormat != null && vertexFormat.matches(bufferFormat);
    }

    /**
     * Get detailed compatibility report between shader and buffer format
     */
    public CompatibilityReport getCompatibilityReport(DataFormat bufferFormat) {
        if (vertexFormat == null) {
            return new CompatibilityReport(false, "Shader vertex format not initialized");
        }

        List<String> issues = new ArrayList<>();
        boolean compatible = true;

        // Check element count
        if (vertexFormat.getElementCount() != bufferFormat.getElementCount()) {
            issues.add(String.format("Element count mismatch: shader expects %d, buffer has %d",
                    vertexFormat.getElementCount(), bufferFormat.getElementCount()));
            compatible = false;
        }

        // Check individual elements
        int minElements = Math.min(vertexFormat.getElementCount(), bufferFormat.getElementCount());
        for (int i = 0; i < minElements; i++) {
            DataElement shaderElement = vertexFormat.getElement(i);
            DataElement bufferElement = bufferFormat.getElement(i);

            if (!shaderElement.isCompatibleWith(bufferElement)) {
                issues.add(String.format("Element %d incompatible: shader expects %s at location %d, buffer provides %s at location %d",
                        i, shaderElement.getDataType(), shaderElement.getIndex(),
                        bufferElement.getDataType(), bufferElement.getIndex()));
                compatible = false;
            }
        }

        return new CompatibilityReport(compatible, String.join("\n", issues));
    }

    /**
     * Builder for graphics shader programs
     */
    public static class Builder {
        private final Map<ShaderType, String> shaderSources = new HashMap<>();
        private final Identifier identifier;

        public Builder(Identifier identifier) {
            this.identifier = identifier;
        }

        public Builder vertex(String source) {
            shaderSources.put(ShaderType.VERTEX, source);
            return this;
        }

        public Builder fragment(String source) {
            shaderSources.put(ShaderType.FRAGMENT, source);
            return this;
        }

        public Builder geometry(String source) {
            shaderSources.put(ShaderType.GEOMETRY, source);
            return this;
        }

        public Builder tessControl(String source) {
            shaderSources.put(ShaderType.TESS_CONTROL, source);
            return this;
        }

        public Builder tessEvaluation(String source) {
            shaderSources.put(ShaderType.TESS_EVALUATION, source);
            return this;
        }

        public GraphicsShaderProgram build() throws IOException {
            return new GraphicsShaderProgram(identifier, shaderSources);
        }
    }

    /**
     * Create a graphics shader program builder
     */
    public static Builder builder(Identifier identifier) {
        return new Builder(identifier);
    }

    /**
     * Create a simple graphics shader program with vertex and fragment shaders
     */
    public static GraphicsShaderProgram create(Identifier identifier, String vertexShader, String fragmentShader) throws IOException {
        return builder(identifier)
                .vertex(vertexShader)
                .fragment(fragmentShader)
                .build();
    }

    /**
     * Compatibility report between shader and vertex buffer
     */
    public static class CompatibilityReport {
        private final boolean compatible;
        private final String details;

        public CompatibilityReport(boolean compatible, String details) {
            this.compatible = compatible;
            this.details = details;
        }

        public boolean isCompatible() {
            return compatible;
        }

        public String getDetails() {
            return details;
        }

        @Override
        public String toString() {
            return "CompatibilityReport{compatible=" + compatible + ", details='" + details + "'}";
        }
    }
}