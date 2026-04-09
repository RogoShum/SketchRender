package rogo.sketch.core.shader.vertex;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.data.layout.FieldSpec;
import rogo.sketch.core.data.layout.StructLayout;
import rogo.sketch.core.data.type.ValueType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Runtime vertex layout for a resolved shader variant.
 * <p>
 * The declared {@link ShaderVertexLayout} describes the template-level semantic
 * superset. This type captures the active subset actually used by the processed
 * shader sources, with locations assigned in shader declaration order.
 */
public final class ActiveShaderVertexLayout {
    private static final ActiveShaderVertexLayout EMPTY = new ActiveShaderVertexLayout(Collections.emptyList());

    private final List<VertexAttributeSpec> attributes;
    private final Map<String, VertexAttributeSpec> nameToSpec;
    private final Map<String, Integer> activeAttributeLocations;

    private ActiveShaderVertexLayout(List<VertexAttributeSpec> attributes) {
        this.attributes = Collections.unmodifiableList(new ArrayList<>(attributes));
        this.nameToSpec = new LinkedHashMap<>();
        this.activeAttributeLocations = new LinkedHashMap<>();
        for (VertexAttributeSpec attribute : attributes) {
            nameToSpec.put(attribute.name(), attribute);
            activeAttributeLocations.put(attribute.name(), attribute.location());
        }
    }

    public static ActiveShaderVertexLayout empty() {
        return EMPTY;
    }

    public static ActiveShaderVertexLayout fromDeclaredLayout(@Nullable ShaderVertexLayout declaredLayout) {
        if (declaredLayout == null || declaredLayout.isEmpty()) {
            return empty();
        }
        List<VertexAttributeSpec> attributes = new ArrayList<>(declaredLayout.getAttributes().size());
        int location = 0;
        for (VertexAttributeSpec spec : declaredLayout.getAttributes()) {
            attributes.add(new VertexAttributeSpec(location++, spec.type(), spec.name(), spec.macro()));
        }
        return new ActiveShaderVertexLayout(attributes);
    }

    public static ActiveShaderVertexLayout resolve(@Nullable ShaderVertexLayout declaredLayout, @Nullable String processedVertexSource) {
        if (processedVertexSource == null || processedVertexSource.isBlank()) {
            return fromDeclaredLayout(declaredLayout);
        }

        List<VertexAttributeSpec> parsedInputs = VertexAttributeSpec.parseDeclaredInputs(processedVertexSource);
        if (parsedInputs.isEmpty()) {
            return fromDeclaredLayout(declaredLayout);
        }

        List<VertexAttributeSpec> resolved = new ArrayList<>(parsedInputs.size());
        int nextLocation = 0;
        for (VertexAttributeSpec parsed : parsedInputs) {
            VertexAttributeSpec declared = declaredLayout != null ? declaredLayout.getSpec(parsed.name()) : null;
            ValueType type = declared != null ? declared.type() : parsed.type();
            String macro = declared != null ? declared.macro() : parsed.macro();
            resolved.add(new VertexAttributeSpec(nextLocation++, type, parsed.name(), macro));
        }
        return new ActiveShaderVertexLayout(resolved);
    }

    public List<VertexAttributeSpec> getAttributes() {
        return attributes;
    }

    public boolean isEmpty() {
        return attributes.isEmpty();
    }

    public int size() {
        return attributes.size();
    }

    @Nullable
    public VertexAttributeSpec getSpec(String semanticName) {
        return nameToSpec.get(semanticName);
    }

    public int getLocation(String semanticName) {
        Integer location = activeAttributeLocations.get(semanticName);
        return location != null ? location : -1;
    }

    public Map<String, Integer> activeAttributeLocations() {
        return Collections.unmodifiableMap(activeAttributeLocations);
    }

    public ShaderVertexLayout toShaderVertexLayout() {
        if (attributes.isEmpty()) {
            return ShaderVertexLayout.empty();
        }
        return ShaderVertexLayout.fromParsedSpecs(attributes);
    }

    public StructLayout buildStructLayout(String formatName) {
        StructLayout.Builder builder = StructLayout.builder(formatName);
        for (VertexAttributeSpec attribute : attributes) {
            builder.add(attribute.location(), attribute.name(), attribute.type(), false, false, false);
        }
        return builder.build();
    }

    public CompatibilityReport compatibilityWith(@Nullable StructLayout bufferFormat) {
        if (attributes.isEmpty()) {
            return CompatibilityReport.ok();
        }
        if (bufferFormat == null) {
            return CompatibilityReport.error("Buffer format is null");
        }

        List<String> issues = new ArrayList<>();
        Map<String, FieldSpec> elementsByName = new HashMap<>();
        for (FieldSpec element : bufferFormat.getElements()) {
            elementsByName.put(element.getName(), element);
        }

        for (VertexAttributeSpec attribute : attributes) {
            FieldSpec element = elementsByName.get(attribute.name());
            if (element == null) {
                issues.add("Missing shader attribute semantic: " + attribute.name());
                continue;
            }
            if (!attribute.type().isCompatibleWith(element.getDataType())) {
                issues.add("Attribute semantic " + attribute.name()
                        + " expects " + attribute.type()
                        + " but buffer provides " + element.getDataType());
            }
        }

        return issues.isEmpty()
                ? CompatibilityReport.ok()
                : CompatibilityReport.error(String.join("\n", issues));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ActiveShaderVertexLayout that)) {
            return false;
        }
        return Objects.equals(attributes, that.attributes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(attributes);
    }

    @Override
    public String toString() {
        return "ActiveShaderVertexLayout" + attributes;
    }

    public record CompatibilityReport(boolean compatible, String details) {
        public static CompatibilityReport ok() {
            return new CompatibilityReport(true, "");
        }

        public static CompatibilityReport error(String details) {
            return new CompatibilityReport(false, details);
        }
    }
}

