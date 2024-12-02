package rogo.sketchrender.shader;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.shaders.Program;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VanillaShaderModifier {
    public static String currentProgram;
    public static Program.Type currentType;
    private static final Map<String, List<VanillaShaderModifier>> MODIFIERS = Maps.newHashMap();
    private static final String BEFORE_MAIN = "\\bvoid main\\(\\)\\s*\\{";
    private static final String AFTER_MAIN = "\\bvoid main\\(\\)\\s*\\{[\\s\\S]*?\\}";

    protected Program.Type type;
    protected String injectionPosition;
    protected String injectionContent;

    public VanillaShaderModifier(Program.Type programType, String positionRegex, String injectionContent) {
        this.type = programType;
        this.injectionPosition = positionRegex != null ? positionRegex : "";
        this.injectionContent = injectionContent != null ? injectionContent : "";
    }

    public static VanillaShaderModifier beforeMain(Program.Type programType, String injectionContent) {
        return new VanillaShaderModifier(programType, BEFORE_MAIN, injectionContent);
    }

    public static VanillaShaderModifier afterMain(Program.Type programType, String injectionContent) {
        return new VanillaShaderModifier(programType, AFTER_MAIN, injectionContent);
    }

    public static List<VanillaShaderModifier> getTargetModifier() {
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

    public static void registerModifier(String programName, VanillaShaderModifier modifier) {
        MODIFIERS.computeIfAbsent(programName, k -> new java.util.ArrayList<>()).add(modifier);
    }

    public static void clear() {
        MODIFIERS.clear();
    }
}
