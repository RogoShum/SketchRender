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
    protected boolean replace;

    public ShaderModifier(Program.Type programType, String positionRegex, String injectionContent) {
        this.type = programType;
        this.injectionPosition = positionRegex != null ? positionRegex : "";
        this.injectionContent = injectionContent != null ? injectionContent : "";
    }

    public ShaderModifier replace() {
        this.replace = true;
        return this;
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
                    uniform sampler2D sketch_culling_texture;
                    uniform int sketch_culling_terrain;
                    uniform int sketch_level_min_pos;
                    uniform int sketch_level_pos_range;
                    uniform int sketch_level_section_range;
                    uniform int sketch_render_distance;
                    uniform int sketch_space_partition_size;
                    uniform int sketch_culling_size;
                    uniform vec3 sketch_camera_pos;
                    uniform int sketch_check_culling;
                    
                    flat out vec3 sketch_section_pos;
                    flat out ivec3 _chunk_offset_map;
                    flat out int _pre_chunk_offset_y;
                    
                    int _map_culling_chunkY(float _pos_y) {
                        float offset = _pos_y - sketch_level_min_pos;
                        float mappingRatio = offset / sketch_level_pos_range;
                    
                        return int(floor(mappingRatio * sketch_level_section_range));
                    }
                    
                    int _get_chunk_index(ivec3 chunk_offset) {
                        return (chunk_offset.x + sketch_render_distance) * sketch_space_partition_size * sketch_level_section_range + (chunk_offset.z + sketch_render_distance) * sketch_level_section_range + chunk_offset.y;
                    }
                    
                    ivec3 _vec_to_section_pos(vec3 vec) {
                        return ivec3(int(vec.x) >> 4, int(vec.y) >> 4, int(vec.z) >> 4);
                    }
                    
                    vec2 _get_culling_uv_from_index(ivec3 chunk_offset) {
                        int screenIndex = _get_chunk_index(chunk_offset);
                    
                        int fragX = screenIndex % sketch_culling_size;
                        int fragY = screenIndex / sketch_culling_size;
                    
                        return vec2(fragX, fragY) / vec2(sketch_culling_size);
                    }
                    
                    bool _is_chunk_culled(ivec3 chunk_offset) {
                        return texture(sketch_culling_texture, _get_culling_uv_from_index(chunk_offset)).y < 0.5;
                    }
                    """));
            list.add(ShaderModifier.afterVertInit(Program.Type.VERTEX, """
                    if (sketch_culling_terrain > 0) {
                        sketch_section_pos = u_RegionOffset + _get_draw_translation(_draw_id) + sketch_camera_pos;
                        ivec3 sketch_camera_section = _vec_to_section_pos(sketch_camera_pos);
                        _chunk_offset_map = _vec_to_section_pos(vec3(sketch_section_pos.x + 8.0, sketch_section_pos.y, sketch_section_pos.z + 8.0));
                        _chunk_offset_map.x = _chunk_offset_map.x - sketch_camera_section.x;
                        _chunk_offset_map.z = _chunk_offset_map.z - sketch_camera_section.z;
                        _pre_chunk_offset_y = _chunk_offset_map.y - sketch_camera_section.y;
                        _chunk_offset_map.y = _map_culling_chunkY(sketch_section_pos.y + 8.0);

                        bool sketch_culled = _is_chunk_culled(_chunk_offset_map);
                        if (sketch_check_culling > 0) {
                            sketch_culled = !sketch_culled;
                        }

                        if (sketch_culled) {
                            gl_Position = vec4(0.0, 0.0, -100.0, 1.0);
                            return;
                        }
                    }
                    """));
            return list;
        }

        /*
        if ("sodium:terrain".equals(currentProgram) && currentType == Program.Type.FRAGMENT) {
            list.add(ShaderModifier.beforeMain(Program.Type.FRAGMENT, """
                    flat in int _pre_chunk_offset_y;
                    flat in ivec3 _chunk_offset_map;
                    """));
            list.add(new ShaderModifier(Program.Type.VERTEX, "fragColor = _linearFog(diffuseColor, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);", """
                    fragColor = _linearFog(diffuseColor, v_FragDistance, u_FogColor, u_FogStart, u_FogEnd);
                    int maxOffset = max(abs(_chunk_offset_map.x), max(abs(_chunk_offset_map.z), abs(_pre_chunk_offset_y)));
                    if (maxOffset == 0) {
                        fragColor.r = 1.0;
                    } else if (maxOffset == 1) {
                    	fragColor.g = 1.0;
                    } else if (maxOffset == 2) {
                    	fragColor.b = 1.0;
                    }
                    """).replace());
            return list;
        }
         */

        return MODIFIERS.getOrDefault(currentProgram, new java.util.ArrayList<>()).stream()
                .filter(mod -> mod.type == currentType)
                .toList();
    }

    public String applyModifications(String shaderCode) {
        if (replace) {
            shaderCode = shaderCode.replace(injectionPosition, injectionContent);
        } else {
            Pattern pattern = Pattern.compile(injectionPosition);
            Matcher matcher = pattern.matcher(shaderCode);

            if (matcher.find()) {
                int position = matcher.end();
                shaderCode = new StringBuilder(shaderCode)
                        .insert(position, "\n" + injectionContent)
                        .toString();
            }
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
