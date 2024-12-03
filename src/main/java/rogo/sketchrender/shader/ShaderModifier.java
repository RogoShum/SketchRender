package rogo.sketchrender.shader;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.RenderType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderModifier {
    public static String currentProgram;
    public static Program.Type currentType;
    private static final Map<String, List<ShaderModifier>> MODIFIERS = Maps.newHashMap();
    private static final String AFTER_MAIN = "\\bvoid main\\(\\)\\s*\\{";
    private static final String AFTER_VERT_INIT = "\\b_vert_init\\(\\)\\s*\\;";
    private static final String BEFORE_MAIN = "(?m)^.*(?=\\bvoid\\s+main\\s*\\(\\)\\s*\\{)";

    protected Program.Type type;
    protected String injectionPosition;
    protected String injectionContent;

    public ShaderModifier(Program.Type programType, String positionRegex, String injectionContent) {
        this.type = programType;
        this.injectionPosition = positionRegex != null ? positionRegex : "";
        this.injectionContent = injectionContent != null ? injectionContent : "";
    }

    public static ShaderModifier beforeMain(Program.Type programType, String injectionContent) {
        return new ShaderModifier(programType, BEFORE_MAIN, injectionContent + "\n");
    }

    public static ShaderModifier afterMain(Program.Type programType, String injectionContent) {
        return new ShaderModifier(programType, AFTER_MAIN, injectionContent);
    }

    public static ShaderModifier afterVertInit(Program.Type programType, String injectionContent) {
        return new ShaderModifier(programType, AFTER_VERT_INIT, injectionContent);
    }

    public static List<ShaderModifier> getTargetModifier() {
        List<ShaderModifier> list = new ArrayList<>();
        if ("sodium:terrain".equals(currentProgram) && currentType == Program.Type.VERTEX) {
            list.add(ShaderModifier.beforeMain(Program.Type.VERTEX, """
                    uniform sampler2D _culling_texture;
                    uniform int _culling_terrain;
                    uniform int _level_min_pos;
                    uniform int _level_pos_range;
                    uniform int _level_section_range;
                    uniform int _render_distance;
                    uniform int _space_partition_size;
                    uniform float _culling_size;
                    uniform float _culling_y;
                    
                    out vec3 _test_pos;
                    flat out ivec3 _chunk_offset_map;
                    flat out int _chunk_index;
                    
                    int _map_culling_chunkY(float _pos_y) {
                        float offset = _pos_y - _level_min_pos;
                        float mappingRatio = offset / _level_pos_range;
                    
                        return int(floor(mappingRatio * _level_section_range));
                    }
                    
                    int _get_chunk_index(ivec3 chunk_offset) {
                        return (chunk_offset.x + _render_distance) * _space_partition_size * _level_section_range + (chunk_offset.z + _render_distance) * _level_section_range + chunk_offset.y;
                    }
                    
                    vec2 _get_culling_uv_from_index(ivec3 chunk_offset) {
                        int screenIndex = _get_chunk_index(chunk_offset);
                    
                        int fragX = screenIndex % _culling_size;
                        int fragY = screenIndex / _culling_size;
                    
                        return vec2(fragX, fragY) / vec2(_culling_size);
                    }
                    
                    bool _is_chunk_culled(ivec3 chunk_offset) {
                        return texture(_culling_texture, _get_culling_uv_from_index(chunk_offset)).y < 0.5;
                    }
                    """));
            list.add(ShaderModifier.afterVertInit(Program.Type.VERTEX, """
                        if (_culling_terrain > 0) {
                            _test_pos = u_RegionOffset + _get_draw_translation(_draw_id);
                            _test_pos.y = _culling_y;
                            _chunk_offset_map = ivec3(int(_test_pos.x), int(_test_pos.y), int(_test_pos.z));
                            _chunk_index = _get_chunk_index(_chunk_offset_map);
                            
                            if (_is_chunk_culled(_chunk_offset_map)) {
                                gl_Position = vec4(0.0, 0.0, -100.0, 1.0);
                                return;
                            }
                        }
                    """));
            return list;
        }

        if ("sodium:terrain".equals(currentProgram) && currentType == Program.Type.FRAGMENT) {
            list.add(ShaderModifier.beforeMain(Program.Type.FRAGMENT, """
                    in vec3 _test_pos;
                    flat in ivec3 _chunk_offset_map;
                    flat in int _chunk_index;
                    """));
            list.add(ShaderModifier.afterMain(Program.Type.VERTEX, """
                        if (_chunk_index < 0) {
                            discard;
                        }
                    """));
            return list;
        }

        return MODIFIERS.getOrDefault(currentProgram, new java.util.ArrayList<>()).stream()
                .filter(mod -> mod.type == currentType)
                .toList();
    }

    public String applyModifications(String shaderCode) {
        Pattern pattern = Pattern.compile(injectionPosition);
        Matcher matcher = pattern.matcher(shaderCode);

        if (matcher.find()) {
            int position = matcher.end();
            shaderCode = new StringBuilder(shaderCode)
                    .insert(position, "\n" + injectionContent)
                    .toString();
        }
        return shaderCode;
    }

    public static void registerModifier(String programName, ShaderModifier modifier) {
        MODIFIERS.computeIfAbsent(programName, k -> new java.util.ArrayList<>()).add(modifier);
    }

    public static void clear() {
        MODIFIERS.clear();
    }
}
