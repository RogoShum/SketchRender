package rogo.sketch.core.shader.vertex;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.data.DataType;
import rogo.sketch.core.data.format.DataFormat;
import rogo.sketch.core.util.KeyId;

import java.util.*;

/**
 * Represents the vertex layout for a shader program.
 * Contains all vertex attribute specifications and provides lookup by name/location.
 * 
 * Used by:
 * - ShaderPreprocessor: to inject layout(location=N) into GLSL
 * - VertexResource: to build DataFormat for VAO setup
 * - ShaderTemplate: to track attribute requirements
 */
public class ShaderVertexLayout {
    private final List<VertexAttributeSpec> attributes;
    private final Map<String, VertexAttributeSpec> nameToSpec;
    private final Map<Integer, VertexAttributeSpec> locationToSpec;
    
    private ShaderVertexLayout(List<VertexAttributeSpec> attributes) {
        this.attributes = Collections.unmodifiableList(new ArrayList<>(attributes));
        this.nameToSpec = new HashMap<>();
        this.locationToSpec = new HashMap<>();
        
        for (VertexAttributeSpec spec : attributes) {
            nameToSpec.put(spec.name(), spec);
            locationToSpec.put(spec.location(), spec);
        }
    }
    
    /**
     * Create a ShaderVertexLayout from a list of attribute specification strings.
     * The order of the strings determines the attribute locations.
     * 
     * @param specs List of specification strings (e.g., ["vec3 Position", "vec2 UV0"])
     * @return The ShaderVertexLayout
     */
    public static ShaderVertexLayout fromSpecs(List<String> specs) {
        List<VertexAttributeSpec> attributes = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) {
            attributes.add(VertexAttributeSpec.parse(specs.get(i), i));
        }
        return new ShaderVertexLayout(attributes);
    }

    /**
     * Create a ShaderVertexLayout from a list of attribute names, inferring types from vertex shader source.
     * The order of the names determines the attribute locations.
     * 
     * @param attributeNames List of attribute names (e.g., ["Position", "UV0", "Normal : ENABLE_NORMAL"])
     * @param vertexShaderSource The vertex shader GLSL source code
     * @return The ShaderVertexLayout with inferred types
     * @throws IllegalArgumentException if any attribute cannot be found or type cannot be inferred
     */
    public static ShaderVertexLayout fromNames(List<String> attributeNames, String vertexShaderSource) {
        List<VertexAttributeSpec> attributes = new ArrayList<>();
        for (int i = 0; i < attributeNames.size(); i++) {
            attributes.add(VertexAttributeSpec.parseFromSource(attributeNames.get(i), i, vertexShaderSource));
        }
        return new ShaderVertexLayout(attributes);
    }
    
    /**
     * Create a ShaderVertexLayout from already parsed VertexAttributeSpecs.
     * 
     * @param specs The list of VertexAttributeSpecs
     * @return The ShaderVertexLayout
     */
    public static ShaderVertexLayout fromParsedSpecs(List<VertexAttributeSpec> specs) {
        return new ShaderVertexLayout(specs);
    }
    
    /**
     * Create an empty layout.
     * @return An empty ShaderVertexLayout
     */
    public static ShaderVertexLayout empty() {
        return new ShaderVertexLayout(Collections.emptyList());
    }
    
    /**
     * Get the location for an attribute by name.
     * 
     * @param name The attribute name
     * @return The location, or -1 if not found
     */
    public int getLocation(String name) {
        VertexAttributeSpec spec = nameToSpec.get(name);
        return spec != null ? spec.location() : -1;
    }
    
    /**
     * Get the attribute specification by name.
     * 
     * @param name The attribute name
     * @return The spec, or null if not found
     */
    @Nullable
    public VertexAttributeSpec getSpec(String name) {
        return nameToSpec.get(name);
    }
    
    /**
     * Get the attribute specification by location.
     * 
     * @param location The attribute location
     * @return The spec, or null if not found
     */
    @Nullable
    public VertexAttributeSpec getSpecByLocation(int location) {
        return locationToSpec.get(location);
    }
    
    /**
     * Get all attributes.
     * @return Unmodifiable list of all attributes
     */
    public List<VertexAttributeSpec> getAttributes() {
        return attributes;
    }
    
    /**
     * Get all required (non-optional) attributes.
     * @return List of required attributes
     */
    public List<VertexAttributeSpec> getRequiredAttributes() {
        return attributes.stream()
                .filter(VertexAttributeSpec::isRequired)
                .toList();
    }
    
    /**
     * Get all optional attributes (controlled by macros).
     * @return List of optional attributes
     */
    public List<VertexAttributeSpec> getOptionalAttributes() {
        return attributes.stream()
                .filter(VertexAttributeSpec::isOptional)
                .toList();
    }
    
    /**
     * Get all macros that control optional attributes.
     * @return Set of macro names
     */
    public Set<String> getControlMacros() {
        Set<String> macros = new HashSet<>();
        for (VertexAttributeSpec spec : attributes) {
            if (spec.macro() != null) {
                macros.add(spec.macro());
            }
        }
        return macros;
    }
    
    /**
     * Check if this layout has an attribute with the given name.
     * @param name The attribute name
     * @return true if the attribute exists
     */
    public boolean hasAttribute(String name) {
        return nameToSpec.containsKey(name);
    }
    
    /**
     * Get the total stride for all attributes.
     * This is the size of one vertex in bytes.
     * 
     * @return The total stride in bytes
     */
    public int getTotalStride() {
        return attributes.stream()
                .mapToInt(VertexAttributeSpec::getStride)
                .sum();
    }
    
    /**
     * Get the stride for enabled attributes based on active macros.
     * 
     * @param activeMacros Set of currently active macros
     * @return The stride for enabled attributes
     */
    public int getActiveStride(Set<String> activeMacros) {
        return attributes.stream()
                .filter(spec -> spec.isRequired() || activeMacros.contains(spec.macro()))
                .mapToInt(VertexAttributeSpec::getStride)
                .sum();
    }
    
    /**
     * Build a DataFormat for this vertex layout.
     * All attributes are included regardless of macros (for full VAO binding).
     * 
     * @param formatName The name for the DataFormat
     * @return The DataFormat
     */
    public DataFormat buildDataFormat(String formatName) {
        return buildDataFormat(formatName, null);
    }
    
    /**
     * Build a DataFormat for this vertex layout with optional macro filtering.
     * 
     * @param formatName The name for the DataFormat
     * @param activeMacros Set of active macros (null = include all)
     * @return The DataFormat
     */
    public DataFormat buildDataFormat(String formatName, @Nullable Set<String> activeMacros) {
        DataFormat.Builder builder = DataFormat.builder(formatName);
        
        for (VertexAttributeSpec spec : attributes) {
            // If activeMacros is null, include all; otherwise filter
            if (activeMacros == null || spec.isRequired() || activeMacros.contains(spec.macro())) {
                builder.add(spec.location(), spec.name(), spec.type(), false, false, false);
            }
        }
        
        return builder.build();
    }
    
    /**
     * Get the number of attributes.
     * @return The attribute count
     */
    public int size() {
        return attributes.size();
    }
    
    /**
     * Check if this layout is empty.
     * @return true if no attributes
     */
    public boolean isEmpty() {
        return attributes.isEmpty();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ShaderVertexLayout that = (ShaderVertexLayout) o;
        return Objects.equals(attributes, that.attributes);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(attributes);
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("ShaderVertexLayout[\n");
        for (VertexAttributeSpec spec : attributes) {
            sb.append("  ").append(spec).append("\n");
        }
        sb.append("]");
        return sb.toString();
    }
}

