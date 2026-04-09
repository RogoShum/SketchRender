package rogo.sketch.backend.opengl;

import rogo.sketch.core.data.layout.FieldSpec;
import rogo.sketch.core.data.type.ValueType;
import rogo.sketch.backend.opengl.driver.GraphicsAPI;
import rogo.sketch.backend.opengl.internal.IGLVertexArrayStrategy;
import rogo.sketch.backend.opengl.internal.OpenGLRuntimeSupport;
import rogo.sketch.core.shader.vertex.ActiveShaderVertexLayout;
import rogo.sketch.core.shader.vertex.VertexAttributeSpec;

final class OpenGLVertexInputAssembler {
    private OpenGLVertexInputAssembler() {
    }

    static OpenGLVertexInputLayout assemble(
            OpenGLGeometryBinding geometryBinding,
            ActiveShaderVertexLayout shaderLayout) {
        ActiveShaderVertexLayout resolvedLayout = shaderLayout != null ? shaderLayout : ActiveShaderVertexLayout.empty();
        IGLVertexArrayStrategy vertexArrayStrategy = OpenGLRuntimeSupport.vertexArrayStrategy();
        int vao = vertexArrayStrategy.createVertexArray();

        if (geometryBinding.hasIndices()) {
            vertexArrayStrategy.elementBuffer(vao, geometryBinding.getIndexBuffer().getHandle());
        }

        for (VertexAttributeSpec attribute : resolvedLayout.getAttributes()) {
            ResolvedVertexAttribute resolved = resolveSemanticAttribute(
                    geometryBinding,
                    attribute.name(),
                    attribute.location());
            if (resolved == null) {
                continue;
            }

            OpenGLVertexComponent component = resolved.component();
            FieldSpec field = resolved.field();
            int attribLocation = attribute.location();
            int bindingPoint = component.getBindingPoint();
            int stride = component.getFormat().getStride();
            long baseOffset = (long) component.getVertexOffset() * stride;

            vertexArrayStrategy.enableVertexAttribArray(vao, attribLocation);
            applyFormat(vertexArrayStrategy, vao, attribLocation, field);
            vertexArrayStrategy.vertexAttribBinding(vao, attribLocation, bindingPoint);
            vertexArrayStrategy.vertexBuffer(
                    vao,
                    bindingPoint,
                    component.getVboHandle(),
                    baseOffset,
                    stride);
            vertexArrayStrategy.vertexBindingDivisor(
                    vao,
                    bindingPoint,
                    component.isInstanced() ? 1 : 0);
        }

        return new OpenGLVertexInputLayout(vao, resolvedLayout);
    }

    private static void applyFormat(
            IGLVertexArrayStrategy vertexArrayStrategy,
            int vao,
            int attribLocation,
            FieldSpec field) {
        ValueType dataType = field.getDataType();
        if (dataType.isDoubleType()) {
            vertexArrayStrategy.vertexAttribLFormat(
                    vao,
                    attribLocation,
                    field.getComponentCount(),
                    GraphicsAPI.getGLType(dataType),
                    field.getOffset());
            return;
        }
        if (dataType.isIntegerType() && !field.isNormalized()) {
            vertexArrayStrategy.vertexAttribIFormat(
                    vao,
                    attribLocation,
                    field.getComponentCount(),
                    GraphicsAPI.getGLType(dataType),
                    field.getOffset());
            return;
        }
        vertexArrayStrategy.vertexAttribFormat(
                vao,
                attribLocation,
                field.getComponentCount(),
                GraphicsAPI.getGLType(dataType),
                field.isNormalized(),
                field.getOffset());
    }

    private static ResolvedVertexAttribute resolveSemanticAttribute(
            OpenGLGeometryBinding geometryBinding,
            String semanticName,
            int locationFallback) {
        if (semanticName == null || semanticName.isBlank()) {
            return null;
        }
        ResolvedVertexAttribute caseInsensitiveFallback = null;
        ResolvedVertexAttribute slotFallback = null;
        for (OpenGLVertexComponent component : geometryBinding.getAllComponents().values()) {
            if (component == null || component.getFormat() == null) {
                continue;
            }
            for (FieldSpec field : component.getFormat().getElements()) {
                if (semanticName.equals(field.getName())) {
                    return new ResolvedVertexAttribute(component, field);
                }
                if (caseInsensitiveFallback == null && semanticName.equalsIgnoreCase(field.getName())) {
                    caseInsensitiveFallback = new ResolvedVertexAttribute(component, field);
                }
                if (slotFallback == null && field.getSlot() == locationFallback) {
                    slotFallback = new ResolvedVertexAttribute(component, field);
                }
            }
        }
        return caseInsensitiveFallback != null ? caseInsensitiveFallback : slotFallback;
    }

    record ResolvedVertexAttribute(OpenGLVertexComponent component, FieldSpec field) {
    }
}
