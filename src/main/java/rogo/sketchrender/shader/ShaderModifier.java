package rogo.sketchrender.shader;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.shaders.Program;

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

    public static List<ShaderModifier> getTargetModifier() {
        List<ShaderModifier> list = new ArrayList<>();
        if ("sodium:terrain".equals(currentProgram) && currentType == Program.Type.VERTEX) {
            list.add(ShaderModifier.beforeMain(Program.Type.VERTEX, "out vec3 testPos;"));
            list.add(new ShaderModifier(Program.Type.VERTEX, "\\b_vert_init\\(\\)\\s*\\;", """
                    testPos = u_RegionOffset + _get_draw_translation(_draw_id);
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
