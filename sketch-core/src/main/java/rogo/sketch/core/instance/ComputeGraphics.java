package rogo.sketch.core.instance;

import org.jetbrains.annotations.Nullable;
import rogo.sketch.core.api.graphics.AsyncTickable;
import rogo.sketch.core.api.graphics.DescriptorStability;
import rogo.sketch.core.api.graphics.ComputeDispatchCommand;
import rogo.sketch.core.api.graphics.DispatchableGraphics;
import rogo.sketch.core.pipeline.CompiledRenderSetting;
import rogo.sketch.core.pipeline.PartialRenderSetting;
import rogo.sketch.core.pipeline.RenderContext;
import rogo.sketch.core.pipeline.RenderSetting;
import rogo.sketch.core.pipeline.RenderSettingCompiler;
import rogo.sketch.core.pipeline.parmeter.RenderParameter;
import rogo.sketch.core.shader.ComputeShader;
import rogo.sketch.core.util.KeyId;

import java.util.function.BiConsumer;

public abstract class ComputeGraphics implements DispatchableGraphics, AsyncTickable, Comparable<ComputeGraphics> {
    private final KeyId id;
    @Nullable
    private final Runnable tick;
    private final ComputeDispatchCommand dispatchOperation;
    @Nullable
    private final BiConsumer<RenderContext, ComputeShader> dispatchAdapter;
    private int priority;

    public ComputeGraphics(KeyId keyId, @Nullable Runnable tick,
                           BiConsumer<RenderContext, ComputeShader> dispatchCommand) {
        this(keyId, tick, dispatchCommand, 100);
    }

    public ComputeGraphics(KeyId keyId, @Nullable Runnable tick,
                           BiConsumer<RenderContext, ComputeShader> dispatchCommand, int priority) {
        this(
                keyId,
                tick,
                dispatchCommand != null
                        ? dispatchContext -> dispatchCommand.accept(
                        dispatchContext.renderContext(),
                        dispatchContext.programHandle() != null
                                ? dispatchContext.programHandle().computeShaderAdapter()
                                : null)
                        : null,
                dispatchCommand,
                priority);
    }

    public ComputeGraphics(KeyId keyId, @Nullable Runnable tick,
                           ComputeDispatchCommand dispatchOperation) {
        this(keyId, tick, dispatchOperation, null, 100);
    }

    public ComputeGraphics(KeyId keyId, @Nullable Runnable tick,
                           ComputeDispatchCommand dispatchOperation, int priority) {
        this(keyId, tick, dispatchOperation, null, priority);
    }

    private ComputeGraphics(KeyId keyId, @Nullable Runnable tick,
                            ComputeDispatchCommand dispatchOperation,
                            @Nullable BiConsumer<RenderContext, ComputeShader> dispatchAdapter,
                            int priority) {
        this.id = keyId;
        this.tick = tick;
        this.dispatchOperation = dispatchOperation;
        this.dispatchAdapter = dispatchAdapter;
        this.priority = priority;
    }

    public ComputeGraphics setPriority(int priority) {
        this.priority = priority;
        return this;
    }

    public int getPriority() {
        return priority;
    }

    @Override
    public KeyId getIdentifier() {
        return id;
    }

    @Override
    public void asyncTick() {
        if (tick != null) {
            tick.run();
        }
    }

    @Override
    public void swapData() {
    }

    @Override
    public ComputeDispatchCommand getDispatchOperation() {
        return dispatchOperation;
    }

    @Override
    @Deprecated(forRemoval = false)
    public BiConsumer<RenderContext, ComputeShader> getDispatchCommand() {
        return dispatchAdapter;
    }

    @Override
    public int compareTo(ComputeGraphics o) {
        return Integer.compare(this.priority, o.priority);
    }

    @Override
    public DescriptorStability descriptorStability() {
        return DescriptorStability.STABLE;
    }

    protected final long partialDescriptorVersion(PartialRenderSetting partialRenderSetting) {
        return java.util.Objects.hash(
                descriptorStability(),
                partialRenderSetting != null ? partialRenderSetting : PartialRenderSetting.EMPTY);
    }

    protected final CompiledRenderSetting compilePartialDescriptor(
            RenderParameter renderParameter,
            PartialRenderSetting partialRenderSetting) {
        return RenderSettingCompiler.compile(RenderSetting.fromPartial(
                renderParameter,
                partialRenderSetting != null ? partialRenderSetting : PartialRenderSetting.EMPTY));
    }
}

