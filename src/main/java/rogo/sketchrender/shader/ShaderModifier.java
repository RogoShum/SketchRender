package rogo.sketchrender.shader;

import com.google.common.collect.Maps;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.shaders.Program;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ShaderModifier {
    public static String currentProgram;
    public static Program.Type currentType;
    private static final Map<String, List<ShaderModifier>> MODIFIERS = Maps.newHashMap();

    protected Program.Type type;
    protected String injectionPosition;
    protected String injectionContent;
    protected boolean replace;

    public ShaderModifier(Program.Type programType, String positionRegex, String injectionContent) {
        this.type = programType;
        this.injectionPosition = positionRegex != null ? findMarco(positionRegex) : "";
        this.injectionContent = injectionContent != null ? injectionContent : "";
    }

    public ShaderModifier replace() {
        this.replace = true;
        return this;
    }

    public static ShaderModifier beforeMain(Program.Type programType, String injectionContent) {
        return new ShaderModifier(programType, "BEFORE_MAIN", injectionContent + "\n");
    }

    public static ShaderModifier afterMain(Program.Type programType, String injectionContent) {
        return new ShaderModifier(programType, "AFTER_MAIN", injectionContent);
    }

    public static ShaderModifier afterVertInit(Program.Type programType, String injectionContent) {
        return new ShaderModifier(programType, "AFTER_VERT_INIT", injectionContent);
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

    public static void loadAll(ResourceManager resourceManager) {
        try {
            Map<ResourceLocation, Resource> map = resourceManager.listResources("shaders/modifier", (p_251575_) -> {
                String s = p_251575_.getPath();
                return s.endsWith(".json");
            });

            for (Resource resource : map.values()) {
                try (InputStream inputStream = resource.open();
                     InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
                    JsonElement jsonElement = JsonParser.parseReader(reader);
                    if (jsonElement instanceof JsonObject json) {
                        JsonElement shaderElement = json.get("shader");
                        JsonElement typeElement = json.get("type");
                        JsonElement regexElement = json.get("position_regex");
                        JsonElement contentElement = json.get("content");
                        if (shaderElement != null && typeElement != null && regexElement != null && contentElement != null) {
                            String shader = shaderElement.getAsString();
                            String type = typeElement.getAsString();
                            String regex = regexElement.getAsString();
                            String content = contentElement.getAsString();
                            if (type.equals("vertex") || type.equals("fragment")) {
                                Program.Type pt = type.equals("vertex") ? Program.Type.VERTEX : Program.Type.FRAGMENT;
                                ShaderModifier shaderModifier = new ShaderModifier(pt, regex, content);
                                if (json.has("replace")) {
                                    JsonElement re = json.get("replace");
                                    boolean replace = re.getAsBoolean();
                                    if (replace) {
                                        shaderModifier.replace();
                                    }
                                }
                                ShaderModifier.registerModifier(shader, shaderModifier);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String findMarco(String s) {
        return switch (s) {
            case "AFTER_MAIN" -> "\\bvoid main\\(\\)\\s*\\{";
            case "AFTER_VERT_INIT" -> "\\b_vert_init\\(\\)\\s*\\;";
            case "BEFORE_MAIN" -> "(?m)^.*(?=\\bvoid\\s+main\\s*\\(\\)\\s*\\{)";
            default -> s;
        };
    }
}
