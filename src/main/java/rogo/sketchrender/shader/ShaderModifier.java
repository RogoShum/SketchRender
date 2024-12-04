package rogo.sketchrender.shader;

import com.google.common.collect.Maps;
import com.mojang.blaze3d.shaders.Program;

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
