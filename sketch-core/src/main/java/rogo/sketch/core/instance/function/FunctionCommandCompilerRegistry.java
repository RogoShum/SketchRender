package rogo.sketch.core.instance.function;

import rogo.sketch.core.api.graphics.FunctionalGraphics;
import rogo.sketch.core.instance.StandardFunctionGraphics;
import rogo.sketch.core.packet.ClearPacket;
import rogo.sketch.core.packet.GenerateMipmapPacket;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.shader.uniform.UniformValueSnapshot;
import rogo.sketch.core.util.KeyId;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class FunctionCommandCompilerRegistry {
    private static final FunctionCommandCompilerRegistry STANDARD = createStandard();

    private final Map<Class<?>, FunctionCommandCompiler<?>> compilers = new ConcurrentHashMap<>();

    public static FunctionCommandCompilerRegistry standard() {
        return STANDARD;
    }

    public <T extends StandardFunctionGraphics.Command> FunctionCommandCompilerRegistry register(
            Class<T> commandType,
            FunctionCommandCompiler<T> compiler) {
        if (commandType == null || compiler == null) {
            return this;
        }
        compilers.put(commandType, compiler);
        return this;
    }

    public RenderPacket compile(
            KeyId stageId,
            PipelineType pipelineType,
            CompiledRenderSetting compiledRenderSetting,
            FunctionalGraphics graphics,
            StandardFunctionGraphics.Command command) {
        if (compiledRenderSetting == null || command == null) {
            return null;
        }
        FunctionCommandCompiler<StandardFunctionGraphics.Command> compiler =
                lookup(command.getClass());
        if (compiler == null) {
            return null;
        }
        return compiler.compile(stageId, pipelineType, compiledRenderSetting, graphics, command);
    }

    @SuppressWarnings("unchecked")
    private FunctionCommandCompiler<StandardFunctionGraphics.Command> lookup(Class<?> commandType) {
        if (commandType == null) {
            return null;
        }
        FunctionCommandCompiler<?> exact = compilers.get(commandType);
        if (exact != null) {
            return (FunctionCommandCompiler<StandardFunctionGraphics.Command>) exact;
        }
        for (Map.Entry<Class<?>, FunctionCommandCompiler<?>> entry : compilers.entrySet()) {
            if (entry.getKey().isAssignableFrom(commandType)) {
                return (FunctionCommandCompiler<StandardFunctionGraphics.Command>) entry.getValue();
            }
        }
        return null;
    }

    private static FunctionCommandCompilerRegistry createStandard() {
        return new FunctionCommandCompilerRegistry()
                .register(StandardFunctionGraphics.ClearCommand.class, (stageId, pipelineType, compiledRenderSetting, graphics, command) ->
                        new ClearPacket(
                                stageId,
                                pipelineType,
                                compiledRenderSetting.pipelineStateKey(),
                                compiledRenderSetting.resourceBindingPlan(),
                                UniformValueSnapshot.empty(),
                                List.of(graphics),
                                command.renderTargetId(),
                                command.colorAttachments(),
                                command.clearColor(),
                                command.clearDepth(),
                                command.clearColorValue(),
                                command.clearDepthValue(),
                                command.colorMask(),
                                command.restorePreviousRenderTarget()))
                .register(StandardFunctionGraphics.GenMipmapCommand.class, (stageId, pipelineType, compiledRenderSetting, graphics, command) ->
                        new GenerateMipmapPacket(
                                stageId,
                                pipelineType,
                                compiledRenderSetting.pipelineStateKey(),
                                compiledRenderSetting.resourceBindingPlan(),
                                UniformValueSnapshot.empty(),
                                List.of(graphics),
                                command.textureId()));
    }

    @FunctionalInterface
    public interface FunctionCommandCompiler<T extends StandardFunctionGraphics.Command> {
        RenderPacket compile(
                KeyId stageId,
                PipelineType pipelineType,
                CompiledRenderSetting compiledRenderSetting,
                FunctionalGraphics graphics,
                T command);
    }
}

