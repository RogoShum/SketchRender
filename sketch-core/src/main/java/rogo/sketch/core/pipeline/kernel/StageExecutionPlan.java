package rogo.sketch.core.pipeline.kernel;

import rogo.sketch.core.driver.state.snapshot.SnapshotScope;
import rogo.sketch.core.packet.PipelineStateKey;
import rogo.sketch.core.packet.RenderPacket;
import rogo.sketch.core.packet.RenderPacketKind;
import rogo.sketch.core.packet.ResourceBindingPlan;
import rogo.sketch.core.pipeline.PipelineType;
import rogo.sketch.core.resource.GraphicsResourceManager;
import rogo.sketch.core.resource.ResourceTypes;
import rogo.sketch.core.shader.variant.ShaderTemplate;
import rogo.sketch.core.shader.variant.ShaderVariantKey;
import rogo.sketch.core.util.KeyId;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public record StageExecutionPlan(
        KeyId stageId,
        Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> packets,
        SnapshotScope stageSnapshotScope,
        StageResourceFootprint stageResourceFootprint
) {
    public StageExecutionPlan {
        packets = packets != null ? normalizePackets(packets) : Map.of();
        stageSnapshotScope = stageSnapshotScope != null ? stageSnapshotScope : SnapshotScope.empty();
        stageResourceFootprint = stageResourceFootprint != null
                ? stageResourceFootprint
                : StageResourceFootprint.fromPackets(packets, stageSnapshotScope);
    }

    public static StageExecutionPlan empty(KeyId stageId) {
        return new StageExecutionPlan(stageId, Map.of(), SnapshotScope.empty(), StageResourceFootprint.empty());
    }

    public static StageExecutionPlan fromPackets(
            KeyId stageId,
            Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> packets) {
        SnapshotScope snapshotScope = deriveSnapshotScope(packets);
        return new StageExecutionPlan(
                stageId,
                packets,
                snapshotScope,
                StageResourceFootprint.fromPackets(packets, snapshotScope));
    }

    public boolean isEmpty() {
        return packets.isEmpty();
    }

    private static Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> normalizePackets(
            Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> packets) {
        Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> normalized = new LinkedHashMap<>();
        for (Map.Entry<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> pipelineEntry : packets.entrySet()) {
            if (pipelineEntry.getKey() == null || pipelineEntry.getValue() == null || pipelineEntry.getValue().isEmpty()) {
                continue;
            }
            Map<PipelineStateKey, List<RenderPacket>> states = new LinkedHashMap<>();
            for (Map.Entry<PipelineStateKey, List<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                if (stateEntry.getKey() == null || stateEntry.getValue() == null || stateEntry.getValue().isEmpty()) {
                    continue;
                }
                states.put(stateEntry.getKey(), List.copyOf(stateEntry.getValue()));
            }
            if (!states.isEmpty()) {
                normalized.put(pipelineEntry.getKey(), Collections.unmodifiableMap(states));
            }
        }
        return Collections.unmodifiableMap(normalized);
    }

    private static SnapshotScope deriveSnapshotScope(
            Map<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> packets) {
        if (packets == null || packets.isEmpty()) {
            return SnapshotScope.empty();
        }

        SnapshotScope.Builder builder = SnapshotScope.builder();
        Map<PipelineStateKey, Map<KeyId, Map<KeyId, Integer>>> resolvedBindingsCache = new LinkedHashMap<>();
        Set<BindingPlanScopeCacheKey> appliedBindingScopes = new LinkedHashSet<>();
        boolean hasPackets = false;
        boolean touchesFramebuffer = false;
        boolean touchesVertexArrays = false;

        for (Map.Entry<PipelineType, Map<PipelineStateKey, List<RenderPacket>>> pipelineEntry : packets.entrySet()) {
            PipelineType pipelineType = pipelineEntry.getKey();
            for (Map.Entry<PipelineStateKey, List<RenderPacket>> stateEntry : pipelineEntry.getValue().entrySet()) {
                PipelineStateKey stateKey = stateEntry.getKey();
                if (stateKey != null) {
                    if (stateKey.renderState() != null) {
                        builder.addStatesFromRenderStatePatch(stateKey.renderState());
                    }
                    if (stateKey.shaderId() != null) {
                        builder.addState(SnapshotScope.StateType.PROGRAM);
                    }
                    if (stateKey.bindingPlan() != null && !stateKey.bindingPlan().isEmpty()) {
                        addBindingPlanScope(builder, stateKey, stateKey.bindingPlan(), resolvedBindingsCache, appliedBindingScopes);
                    }
                }

                for (RenderPacket packet : stateEntry.getValue()) {
                    if (packet == null) {
                        continue;
                    }
                    hasPackets = true;
                    if (packet.bindingPlan() != null
                            && !packet.bindingPlan().isEmpty()
                            && !packet.bindingPlan().equals(stateKey != null ? stateKey.bindingPlan() : null)) {
                        addBindingPlanScope(builder, packet.stateKey(), packet.bindingPlan(), resolvedBindingsCache, appliedBindingScopes);
                    }
                    if (packet.packetKind() == RenderPacketKind.DRAW) {
                        touchesFramebuffer = true;
                        touchesVertexArrays = true;
                        continue;
                    }
                    if (packet.packetKind() == RenderPacketKind.CLEAR && packet instanceof rogo.sketch.core.packet.ClearPacket clearPacket) {
                        touchesFramebuffer = true;
                        if (clearPacket.colorMask() != null && clearPacket.colorMask().length >= 4) {
                            builder.addState(SnapshotScope.StateType.PIPELINE_RASTER);
                        }
                        if (clearPacket.clearDepth()) {
                            builder.addState(SnapshotScope.StateType.PIPELINE_RASTER);
                        }
                        continue;
                    }
                    if (packet.packetKind() == RenderPacketKind.DISPATCH || PipelineType.COMPUTE.equals(pipelineType)) {
                        builder.addState(SnapshotScope.StateType.PROGRAM);
                        continue;
                    }
                    if (packet.packetKind() == RenderPacketKind.GENERATE_MIPMAP) {
                        builder.addState(SnapshotScope.StateType.PROGRAM);
                    }
                }
            }
        }

        if (touchesFramebuffer || hasPackets) {
            builder.addState(SnapshotScope.StateType.FBO);
        }
        if (touchesVertexArrays) {
            builder.addState(SnapshotScope.StateType.VAO);
        }
        return builder.build();
    }

    private static void addBindingPlanScope(
            SnapshotScope.Builder builder,
            PipelineStateKey stateKey,
            ResourceBindingPlan bindingPlan,
            Map<PipelineStateKey, Map<KeyId, Map<KeyId, Integer>>> resolvedBindingsCache,
            Set<BindingPlanScopeCacheKey> appliedBindingScopes) {
        builder.addState(SnapshotScope.StateType.PROGRAM);
        builder.addState(SnapshotScope.StateType.PASS_BINDINGS);
        if (bindingPlan == null || bindingPlan.isEmpty() || stateKey == null || stateKey.shaderId() == null) {
            return;
        }

        BindingPlanScopeCacheKey cacheKey = new BindingPlanScopeCacheKey(
                stateKey.shaderId(),
                stateKey.shaderVariantKey() != null ? stateKey.shaderVariantKey() : ShaderVariantKey.EMPTY,
                bindingPlan.layoutKey());
        if (appliedBindingScopes != null && !appliedBindingScopes.add(cacheKey)) {
            return;
        }

        Map<KeyId, Map<KeyId, Integer>> resolvedBindings = resolveShaderBindings(stateKey, resolvedBindingsCache);
        if (resolvedBindings.isEmpty()) {
            return;
        }

        for (ResourceBindingPlan.BindingEntry entry : bindingPlan.entries()) {
            if (entry == null || entry.bindingName() == null || entry.resourceType() == null) {
                continue;
            }
            Integer bindingIndex = resolveBindingIndex(resolvedBindings, entry);
            if (bindingIndex == null || bindingIndex < 0) {
                continue;
            }
            KeyId normalizedType = ResourceTypes.normalize(entry.resourceType());
            if (ResourceTypes.TEXTURE.equals(normalizedType)) {
                builder.addTextureUnit(bindingIndex);
            } else if (ResourceTypes.IMAGE.equals(normalizedType)) {
                builder.addImageBinding(bindingIndex);
            } else if (ResourceTypes.STORAGE_BUFFER.equals(normalizedType)) {
                builder.addSSBOBinding(bindingIndex);
            } else if (ResourceTypes.UNIFORM_BUFFER.equals(normalizedType)) {
                builder.addUBOBinding(bindingIndex);
            }
        }
    }

    private static Map<KeyId, Map<KeyId, Integer>> resolveShaderBindings(
            PipelineStateKey stateKey,
            Map<PipelineStateKey, Map<KeyId, Map<KeyId, Integer>>> resolvedBindingsCache) {
        if (stateKey == null) {
            return Map.of();
        }
        if (resolvedBindingsCache != null) {
            Map<KeyId, Map<KeyId, Integer>> cached = resolvedBindingsCache.get(stateKey);
            if (cached != null) {
                return cached;
            }
        }
        Object resource = GraphicsResourceManager.getInstance()
                .getResource(ResourceTypes.SHADER_TEMPLATE, stateKey.shaderId());
        if (!(resource instanceof ShaderTemplate shaderTemplate)) {
            return Map.of();
        }
        try {
            Map<KeyId, Map<KeyId, Integer>> resolved = shaderTemplate.resolveResourceBindings(stateKey.shaderVariantKey());
            if (resolvedBindingsCache != null) {
                resolvedBindingsCache.put(stateKey, resolved);
            }
            return resolved;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private static Integer resolveBindingIndex(
            Map<KeyId, Map<KeyId, Integer>> resolvedBindings,
            ResourceBindingPlan.BindingEntry entry) {
        for (KeyId searchType : ResourceTypes.getSearchOrder(entry.resourceType())) {
            Map<KeyId, Integer> typeBindings = resolvedBindings.get(ResourceTypes.normalize(searchType));
            if (typeBindings == null || typeBindings.isEmpty()) {
                continue;
            }
            Integer binding = typeBindings.get(entry.bindingName());
            if (binding != null) {
                return binding;
            }
        }
        return null;
    }

    private record BindingPlanScopeCacheKey(
            KeyId shaderId,
            ShaderVariantKey shaderVariantKey,
            KeyId resourceLayoutKey
    ) {
    }
}

