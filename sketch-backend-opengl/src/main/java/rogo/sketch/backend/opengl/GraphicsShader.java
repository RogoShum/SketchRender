package rogo.sketch.backend.opengl;

import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL20;
import rogo.sketch.core.data.layout.FieldSpec;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.data.type.ValueType;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.core.shader.ShaderType;
import rogo.sketch.core.shader.preprocessor.ShaderPreprocessor;
import rogo.sketch.core.shader.vertex.ActiveShaderVertexLayout;
import rogo.sketch.core.shader.vertex.ShaderVertexLayout;
import rogo.sketch.core.util.KeyId;

import java.io.IOException;
import java.io.InputStream;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

public final class GraphicsShader extends Shader {
    private StructLayout vertexFormat;
    private final ActiveShaderVertexLayout activeVertexLayout;

    public GraphicsShader(GraphicsAPI api, KeyId keyId, Map<ShaderType, String> shaderSources) throws IOException {
        this(api, keyId, shaderSources, ActiveShaderVertexLayout.empty());
    }

    public GraphicsShader(
            GraphicsAPI api,
            KeyId keyId,
            Map<ShaderType, String> shaderSources,
            ActiveShaderVertexLayout activeVertexLayout) throws IOException {
        super(api, keyId, shaderSources, attributeBindings(activeVertexLayout));
        this.activeVertexLayout = normalizeActiveVertexLayout(activeVertexLayout);
    }

    public GraphicsShader(
            GraphicsAPI api,
            KeyId keyId,
            Map<ShaderType, String> shaderSources,
            ShaderPreprocessor preprocessor,
            Function<KeyId, Optional<InputStream>> resourceProvider,
            Map<String, String> macros,
            ShaderVertexLayout shaderVertexLayout) throws IOException {
        super(
                api,
                keyId,
                shaderSources,
                preprocessor,
                resourceProvider,
                macros,
                shaderVertexLayout,
                attributeBindings(ActiveShaderVertexLayout.fromDeclaredLayout(shaderVertexLayout)));
        this.activeVertexLayout = ActiveShaderVertexLayout.fromDeclaredLayout(shaderVertexLayout);
    }

    private static ActiveShaderVertexLayout normalizeActiveVertexLayout(ActiveShaderVertexLayout activeVertexLayout) {
        return activeVertexLayout != null ? activeVertexLayout : ActiveShaderVertexLayout.empty();
    }

    private static Map<String, Integer> attributeBindings(ActiveShaderVertexLayout activeVertexLayout) {
        ActiveShaderVertexLayout normalized = normalizeActiveVertexLayout(activeVertexLayout);
        return normalized.isEmpty() ? Map.of() : normalized.activeAttributeLocations();
    }

    @Override
    protected void postLinkInitialization() {
        super.postLinkInitialization();
        collectVertexAttributes();
    }

    @Override
    protected void validateShaderTypes(Map<ShaderType, String> shaderSources) {
        if (!shaderSources.containsKey(ShaderType.VERTEX)) {
            throw new IllegalArgumentException("Graphics shader program requires a vertex shader");
        }
        if (!shaderSources.containsKey(ShaderType.FRAGMENT)) {
            throw new IllegalArgumentException("Graphics shader program requires a fragment shader");
        }
        for (ShaderType type : shaderSources.keySet()) {
            if (!type.isGraphicsShader()) {
                throw new IllegalArgumentException("Graphics shader program cannot contain compute shaders");
            }
        }
        boolean hasTessControl = shaderSources.containsKey(ShaderType.TESS_CONTROL);
        boolean hasTessEval = shaderSources.containsKey(ShaderType.TESS_EVALUATION);
        if (hasTessControl != hasTessEval) {
            throw new IllegalArgumentException("Tessellation shaders must be used together");
        }
    }

    private void collectVertexAttributes() {
        if (activeVertexLayout != null && !activeVertexLayout.isEmpty()) {
            this.vertexFormat = activeVertexLayout.buildStructLayout("ActiveShaderVertexFormat_" + keyId);
            return;
        }

        bind();
        int attributeCount = GL20.glGetProgrami(program, GL20.GL_ACTIVE_ATTRIBUTES);
        List<FieldSpec> elements = new ArrayList<>();

        for (int i = 0; i < attributeCount; i++) {
            IntBuffer size = BufferUtils.createIntBuffer(1);
            IntBuffer type = BufferUtils.createIntBuffer(1);
            String attributeName = GL20.glGetActiveAttrib(program, i, size, type);
            if (attributeName.startsWith("gl_")) {
                continue;
            }

            int location = GL20.glGetAttribLocation(program, attributeName);
            if (location >= 0) {
                ValueType dataType = inferAttributeType(type.get(0));
                if (dataType != null) {
                    boolean normalized = shouldNormalizeAttribute(attributeName, dataType);
                    elements.add(new FieldSpec(attributeName, dataType, location, normalized, false, false));
                }
            }
        }

        elements.sort((a, b) -> Integer.compare(a.getIndex(), b.getIndex()));
        this.vertexFormat = new StructLayout("ShaderVertexFormat_" + keyId, elements);
    }

    private ValueType inferAttributeType(int glType) {
        return switch (glType) {
            case GL20.GL_FLOAT -> ValueType.FLOAT;
            case GL20.GL_FLOAT_VEC2 -> ValueType.VEC2F;
            case GL20.GL_FLOAT_VEC3 -> ValueType.VEC3F;
            case GL20.GL_FLOAT_VEC4 -> ValueType.VEC4F;
            case GL20.GL_INT -> ValueType.INT;
            case GL20.GL_INT_VEC2 -> ValueType.VEC2I;
            case GL20.GL_INT_VEC3 -> ValueType.VEC3I;
            case GL20.GL_INT_VEC4 -> ValueType.VEC4I;
            case GL20.GL_UNSIGNED_INT -> ValueType.UINT;
            case GL20.GL_FLOAT_MAT2 -> ValueType.MAT2;
            case GL20.GL_FLOAT_MAT3 -> ValueType.MAT3;
            case GL20.GL_FLOAT_MAT4 -> ValueType.MAT4;
            default -> null;
        };
    }

    private boolean shouldNormalizeAttribute(String name, ValueType type) {
        String lowerName = name.toLowerCase();
        if (lowerName.contains("color") && (type.isByteType() || type.isShortType())) {
            return true;
        }
        return lowerName.contains("normal") && (type.isByteType() || type.isShortType());
    }

    public StructLayout getVertexFormat() {
        return vertexFormat;
    }

    public ActiveShaderVertexLayout getActiveVertexLayout() {
        return activeVertexLayout;
    }

    public boolean isCompatibleWith(StructLayout bufferFormat) {
        if (activeVertexLayout != null && !activeVertexLayout.isEmpty()) {
            return activeVertexLayout.compatibilityWith(bufferFormat).compatible();
        }
        return vertexFormat != null && vertexFormat.isCompatibleWith(bufferFormat);
    }

    public boolean exactlyMatches(StructLayout bufferFormat) {
        return vertexFormat != null && vertexFormat.matches(bufferFormat);
    }
}

