package rogo.sketch.core.shader.vertex;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.data.DataType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Specification for a single vertex attribute.
 * Parsed from shader_program.json attributes array.
 * 
 * Format: "type name" or "type name : MACRO_NAME"
 * Examples:
 *   "vec3 Position"
 *   "vec3 Normal : ENABLE_NORMAL"
 *   "int EntityId"
 */
public record VertexAttributeSpec(
    int location,
    DataType type,
    String name,
    @Nullable String macro  // null means required (always enabled)
) {
    
    // Pattern: "type name" or "type name : MACRO"
    private static final Pattern SPEC_PATTERN = Pattern.compile(
        "^\\s*(\\w+)\\s+(\\w+)(?:\\s*:\\s*(\\w+))?\\s*$"
    );
    
    // GLSL type to DataType mapping
    private static final Map<String, DataType> GLSL_TYPE_MAP = new HashMap<>();
    
    static {
        // Float types
        GLSL_TYPE_MAP.put("float", DataType.FLOAT);
        GLSL_TYPE_MAP.put("vec2", DataType.VEC2F);
        GLSL_TYPE_MAP.put("vec3", DataType.VEC3F);
        GLSL_TYPE_MAP.put("vec4", DataType.VEC4F);
        
        // Integer types
        GLSL_TYPE_MAP.put("int", DataType.INT);
        GLSL_TYPE_MAP.put("ivec2", DataType.VEC2I);
        GLSL_TYPE_MAP.put("ivec3", DataType.VEC3I);
        GLSL_TYPE_MAP.put("ivec4", DataType.VEC4I);
        
        // Unsigned integer types
        GLSL_TYPE_MAP.put("uint", DataType.UINT);
        GLSL_TYPE_MAP.put("uvec2", DataType.VEC2UI);
        GLSL_TYPE_MAP.put("uvec3", DataType.VEC3UI);
        GLSL_TYPE_MAP.put("uvec4", DataType.VEC4UI);
        
        // Double types
        GLSL_TYPE_MAP.put("double", DataType.DOUBLE);
        GLSL_TYPE_MAP.put("dvec2", DataType.VEC2D);
        GLSL_TYPE_MAP.put("dvec3", DataType.VEC3D);
        GLSL_TYPE_MAP.put("dvec4", DataType.VEC4D);
        
        // Matrix types
        GLSL_TYPE_MAP.put("mat2", DataType.MAT2);
        GLSL_TYPE_MAP.put("mat3", DataType.MAT3);
        GLSL_TYPE_MAP.put("mat4", DataType.MAT4);
        GLSL_TYPE_MAP.put("mat2x3", DataType.MAT2X3);
        GLSL_TYPE_MAP.put("mat2x4", DataType.MAT2X4);
        GLSL_TYPE_MAP.put("mat3x2", DataType.MAT3X2);
        GLSL_TYPE_MAP.put("mat3x4", DataType.MAT3X4);
        GLSL_TYPE_MAP.put("mat4x2", DataType.MAT4X2);
        GLSL_TYPE_MAP.put("mat4x3", DataType.MAT4X3);
    }
    
    /**
     * Parse an attribute specification string.
     * 
     * @param spec The specification string (e.g., "vec3 Position : ENABLE_NORMAL")
     * @param location The attribute location (determined by order in array)
     * @return The parsed VertexAttributeSpec
     * @throws IllegalArgumentException if the spec is invalid
     */
    public static VertexAttributeSpec parse(String spec, int location) {
        Matcher matcher = SPEC_PATTERN.matcher(spec);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid attribute spec: " + spec);
        }
        
        String typeName = matcher.group(1);
        String attrName = matcher.group(2);
        String macroName = matcher.group(3); // may be null
        
        DataType dataType = GLSL_TYPE_MAP.get(typeName);
        if (dataType == null) {
            throw new IllegalArgumentException("Unknown GLSL type: " + typeName + " in spec: " + spec);
        }
        
        return new VertexAttributeSpec(location, dataType, attrName, macroName);
    }

    /**
     * Parse an attribute specification string that only contains the attribute name.
     * The type will be inferred from the vertex shader source code.
     * 
     * @param attrName The attribute name (e.g., "Position" or "Position : ENABLE_NORMAL")
     * @param location The attribute location (determined by order in array)
     * @param vertexShaderSource The vertex shader GLSL source code
     * @return The parsed VertexAttributeSpec with inferred type
     * @throws IllegalArgumentException if the attribute is not found in the shader or type cannot be inferred
     */
    public static VertexAttributeSpec parseFromSource(String attrName, int location, String vertexShaderSource) {
        // Extract macro name if present (format: "name : MACRO")
        String actualAttrName = attrName;
        String macroName = null;
        if (attrName.contains(" : ")) {
            String[] parts = attrName.split(" : ", 2);
            actualAttrName = parts[0].trim();
            macroName = parts[1].trim();
        }
        
        // Find the attribute declaration in the shader source
        DataType inferredType = inferTypeFromSource(actualAttrName, vertexShaderSource);
        if (inferredType == null) {
            throw new IllegalArgumentException("Could not infer type for attribute '" + actualAttrName + 
                    "' from vertex shader source. Make sure the attribute is declared as 'in' in the vertex shader.");
        }
        
        return new VertexAttributeSpec(location, inferredType, actualAttrName, macroName);
    }

    /**
     * Infer the DataType of an attribute from the vertex shader source code.
     * Looks for declarations like: "in vec3 Position;" or "layout(location=0) in vec3 Position;"
     * 
     * @param attrName The attribute name to find
     * @param vertexShaderSource The vertex shader GLSL source code
     * @return The inferred DataType, or null if not found
     */
    private static DataType inferTypeFromSource(String attrName, String vertexShaderSource) {
        // Pattern to match: "in TYPE name;" or "layout(...) in TYPE name;"
        // Handles various formats:
        //   - in vec3 Position;
        //   - layout(location=0) in vec3 Position;
        //   - layout(location = 0) in vec3 Position;
        //   - in highp vec3 Position;
        //   - in vec3 Position, Normal; (multiple attributes)
        Pattern attributePattern = Pattern.compile(
            "(?:layout\\s*\\([^)]*\\)\\s*)?(?:\\w+\\s+)?in\\s+(\\w+)\\s+" + 
            Pattern.quote(attrName) + 
            "\\s*[;,]",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
        );
        
        Matcher matcher = attributePattern.matcher(vertexShaderSource);
        if (matcher.find()) {
            String typeName = matcher.group(1);
            return GLSL_TYPE_MAP.get(typeName);
        }
        
        return null;
    }
    
    /**
     * Get the GLSL type string for this attribute.
     * @return The GLSL type name (e.g., "vec3", "int")
     */
    public String getGlslTypeName() {
        for (Map.Entry<String, DataType> entry : GLSL_TYPE_MAP.entrySet()) {
            if (entry.getValue() == type) {
                return entry.getKey();
            }
        }
        return type.name().toLowerCase();
    }
    
    /**
     * Check if this attribute is optional (controlled by a macro).
     * @return true if optional
     */
    public boolean isOptional() {
        return macro != null;
    }
    
    /**
     * Check if this attribute is required (always enabled).
     * @return true if required
     */
    public boolean isRequired() {
        return macro == null;
    }
    
    /**
     * Get the stride (size in bytes) of this attribute.
     * @return The stride in bytes
     */
    public int getStride() {
        return type.getStride();
    }
    
    /**
     * Get the number of components in this attribute.
     * @return The component count
     */
    public int getComponentCount() {
        return type.getComponentCount();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VertexAttributeSpec that = (VertexAttributeSpec) o;
        return location == that.location && 
               type == that.type && 
               Objects.equals(name, that.name) && 
               Objects.equals(macro, that.macro);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(location, type, name, macro);
    }
    
    @Override
    public String toString() {
        String result = getGlslTypeName() + " " + name;
        if (macro != null) {
            result += " : " + macro;
        }
        return result + " (location=" + location + ")";
    }
}

