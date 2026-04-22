package rogo.sketch.core.dashboard;

import rogo.sketch.core.debugger.DashboardControlAccessor;
import rogo.sketch.core.debugger.DashboardDataSource;
import rogo.sketch.core.debugger.DashboardDiagnosticLine;
import rogo.sketch.core.debugger.DashboardMetricCard;
import rogo.sketch.core.debugger.DashboardTreeNode;
import rogo.sketch.core.memory.MemoryDebugSnapshot;
import rogo.sketch.core.pipeline.kernel.FrameCaptureSnapshot;
import rogo.sketch.core.pipeline.module.diagnostic.DiagnosticEntry;
import rogo.sketch.core.pipeline.module.diagnostic.DiagnosticLevel;
import rogo.sketch.core.pipeline.module.macro.ModuleMacroDefinition;
import rogo.sketch.core.pipeline.module.metric.MetricDescriptor;
import rogo.sketch.core.pipeline.module.metric.MetricKind;
import rogo.sketch.core.pipeline.module.metric.MetricSnapshot;
import rogo.sketch.core.pipeline.module.runtime.ModuleRuntimeHost;
import rogo.sketch.core.pipeline.module.setting.ChangeImpact;
import rogo.sketch.core.pipeline.module.setting.DependencyRule;
import rogo.sketch.core.pipeline.module.setting.ModuleSettingRegistry;
import rogo.sketch.core.pipeline.module.setting.SettingNode;
import rogo.sketch.core.shader.config.MacroContext;
import rogo.sketch.core.shader.config.MacroEntryType;
import rogo.sketch.core.shader.config.MacroSnapshotEntry;
import rogo.sketch.core.ui.control.ControlSpec;
import rogo.sketch.core.util.KeyId;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class DashboardViewModelFactory {
    private static final boolean TRACE_SETTING_TREE = Boolean.getBoolean("sketch.dashboard.traceSettings");
    private static final Set<String> TRACED_SETTING_NODES = Collections.synchronizedSet(new LinkedHashSet<>());
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());
    private static final KeyId ENTITY_HIDDEN_METRIC = KeyId.of("sketch_render", "entity_hidden_count");
    private static final KeyId ENTITY_TOTAL_METRIC = KeyId.of("sketch_render", "entity_total_count");
    private static final KeyId BLOCK_ENTITY_HIDDEN_METRIC = KeyId.of("sketch_render", "block_entity_hidden_count");
    private static final KeyId BLOCK_ENTITY_TOTAL_METRIC = KeyId.of("sketch_render", "block_entity_total_count");
    private final DashboardMemorySectionBuilder memorySectionBuilder = new DashboardMemorySectionBuilder();

    public DashboardViewSnapshot build(DashboardDataSource dataSource, MetricSnapshot metricSnapshot, List<DiagnosticEntry> diagnostics) {
        ModuleRuntimeHost runtimeHost = dataSource.runtimeHost();
        ModuleSettingRegistry registry = runtimeHost.settingRegistry();
        DashboardControlAccessor accessor = dataSource.controlAccessor();
        Map<KeyId, SettingNode<?>> settingsById = indexSettings(registry);
        List<ModuleMacroDefinition> macroDefinitions = new ArrayList<>(runtimeHost.macroRegistry().allDefinitions());
        Set<KeyId> pureMacroSettings = collectPureMacroSettings(macroDefinitions, settingsById);

        List<DashboardTreeNode> settingRoots = new ArrayList<>(buildSettingRoots(registry, accessor, settingsById, pureMacroSettings));
        settingRoots.addAll(dataSource.extraSettingRoots());

        List<DashboardTreeNode> macroRoots = buildMacroRoots(macroDefinitions, registry, accessor, settingsById);
        List<DashboardSummaryMetric> summaryMetrics = new ArrayList<>(mapExtraMetrics(dataSource.extraMetricCards()));
        summaryMetrics.addAll(buildMetricCards(runtimeHost, metricSnapshot));
        MemoryDebugSnapshot memorySnapshot = dataSource.memorySnapshot();
        DashboardMemorySection memorySection = memorySectionBuilder.build(memorySnapshot);
        FrameCaptureSnapshot frameCaptureSnapshot = dataSource.latestFrameCaptureSnapshot();
        summaryMetrics.addAll(buildFrameCaptureMetrics(frameCaptureSnapshot));

        List<DashboardRatioMetric> ratioMetrics = buildRatioMetrics(metricSnapshot);
        List<DashboardDiagnosticLine> diagnosticLines = new ArrayList<>();
        int warningCount = 0;
        int errorCount = 0;
        long latestAlertSequence = 0L;
        for (DiagnosticEntry diagnostic : diagnostics) {
            int repeats = Math.max(1, diagnostic.repeatCount());
            if (diagnostic.level() == DiagnosticLevel.WARN) {
                warningCount += repeats;
                latestAlertSequence = Math.max(latestAlertSequence, diagnostic.alertSequence());
            } else if (diagnostic.level() == DiagnosticLevel.ERROR) {
                errorCount += repeats;
                latestAlertSequence = Math.max(latestAlertSequence, diagnostic.alertSequence());
            }
            diagnosticLines.add(new DashboardDiagnosticLine(
                    TIME_FORMATTER.format(diagnostic.timestamp()),
                    diagnostic.level(),
                    diagnostic.moduleId(),
                    diagnostic.message(),
                    diagnostic.stackPreview(),
                    repeats));
        }

        String preview = diagnosticLines.isEmpty() ? "" : formatDiagnosticPreview(diagnosticLines.get(diagnosticLines.size() - 1));
        return new DashboardViewSnapshot(
                settingRoots,
                macroRoots,
                summaryMetrics,
                memorySection,
                frameCaptureSnapshot,
                ratioMetrics,
                dataSource.frameTimeHistory(),
                buildMacroConstants(runtimeHost),
                diagnosticLines,
                preview,
                warningCount,
                errorCount,
                warningCount + errorCount,
                latestAlertSequence);
    }

    private String formatDiagnosticPreview(DashboardDiagnosticLine line) {
        if (line.repeatCount() <= 1) {
            return line.message();
        }
        return "[" + line.repeatCount() + "] " + line.message();
    }

    private Map<KeyId, SettingNode<?>> indexSettings(ModuleSettingRegistry registry) {
        Map<KeyId, SettingNode<?>> result = new LinkedHashMap<>();
        for (SettingNode<?> setting : registry.allSettings()) {
            result.put(setting.id(), setting);
        }
        return result;
    }

    private List<DashboardTreeNode> buildSettingRoots(ModuleSettingRegistry registry, DashboardControlAccessor accessor,
                                                      Map<KeyId, SettingNode<?>> settingsById, Set<KeyId> pureMacroSettings) {
        Map<KeyId, List<SettingNode<?>>> children = new LinkedHashMap<>();
        List<SettingNode<?>> roots = new ArrayList<>();
        for (SettingNode<?> setting : settingsById.values()) {
            if (setting.parentId() != null) {
                children.computeIfAbsent(setting.parentId(), ignored -> new ArrayList<>()).add(setting);
            } else {
                roots.add(setting);
            }
        }

        List<DashboardTreeNode> result = new ArrayList<>();
        for (SettingNode<?> root : roots) {
            DashboardTreeNode node = buildSettingNode(root, registry, accessor, children, settingsById, pureMacroSettings);
            if (node != null) {
                result.add(node);
            }
        }
        return result;
    }

    private DashboardTreeNode buildSettingNode(
            SettingNode<?> setting,
            ModuleSettingRegistry registry,
            DashboardControlAccessor accessor,
            Map<KeyId, List<SettingNode<?>>> childrenMap,
            Map<KeyId, SettingNode<?>> settingsById,
            Set<KeyId> pureMacroSettings) {
        List<DashboardTreeNode> childNodes = new ArrayList<>();
        for (SettingNode<?> child : childrenMap.getOrDefault(setting.id(), List.of())) {
            DashboardTreeNode childNode = buildSettingNode(child, registry, accessor, childrenMap, settingsById, pureMacroSettings);
            if (childNode != null) {
                childNodes.add(childNode);
            }
        }

        boolean visible = (setting.visibleInGui() && !pureMacroSettings.contains(setting.id())) || !childNodes.isEmpty();
        if (!visible) {
            return null;
        }

        if (setting.isGroup()) {
            return DashboardTreeNode.group("setting-group/" + setting.id(), setting.displayKey(), setting.summaryKey(), setting.detailKey(), childNodes);
        }

        traceSettingNode(setting, childNodes);

        String controlId = "setting/" + setting.id();
        Object value = accessor.readValue(controlId, registry, setting);
        if (value == null) {
            value = registry.getPreviewValue(setting.id());
        }
        BlockedState blockedState = resolveBlockedState(setting, registry, settingsById);
        String disabledDetail = resolveDisabledDetail(controlId, setting, registry, accessor);
        return DashboardTreeNode.control(
                "setting/" + setting.id(),
                setting.displayKey(),
                setting.summaryKey(),
                setting.detailKey(),
                registry.isPreviewActive(setting.id()),
                accessor.isEnabled(controlId, registry, setting),
                disabledDetail,
                controlId,
                blockedState.nodeId(),
                blockedState.displayPath(),
                setting.id(),
                setting.controlSpec(),
                value,
                List.of(),
                childNodes);
    }

    private List<DashboardTreeNode> buildMacroRoots(List<ModuleMacroDefinition> definitions, ModuleSettingRegistry registry,
                                                    DashboardControlAccessor accessor, Map<KeyId, SettingNode<?>> settingsById) {
        Map<String, List<DashboardTreeNode>> byModule = new LinkedHashMap<>();
        definitions.sort(Comparator.comparing(ModuleMacroDefinition::moduleId).thenComparing(ModuleMacroDefinition::id));
        for (ModuleMacroDefinition definition : definitions) {
            if (definition.settingId() == null) {
                continue;
            }
            SettingNode<?> setting = settingsById.get(definition.settingId());
            if (setting == null) {
                continue;
            }
            String controlId = "setting/" + definition.settingId();
            Object value = accessor.readValue(controlId, registry, setting);
            if (value == null) {
                value = registry.getPreviewValue(setting.id());
            }
            ControlSpec controlSpec = definition.controlSpec() != null ? definition.controlSpec() : setting.controlSpec();
            BlockedState blockedState = resolveBlockedState(setting, registry, settingsById);
            String disabledDetail = resolveDisabledDetail(controlId, setting, registry, accessor);
            byModule.computeIfAbsent(definition.moduleId(), ignored -> new ArrayList<>())
                    .add(DashboardTreeNode.control(
                            "macro/" + definition.moduleId() + "/" + definition.id(),
                            definition.displayKey() != null ? definition.displayKey() : setting.displayKey(),
                            definition.summaryKey() != null ? definition.summaryKey() : setting.summaryKey(),
                            definition.detailKey() != null ? definition.detailKey() : setting.detailKey(),
                            registry.isPreviewActive(setting.id()),
                            accessor.isEnabled(controlId, registry, setting),
                            disabledDetail,
                            controlId,
                            blockedState.nodeId(),
                            blockedState.displayPath(),
                            definition.settingId(),
                            controlSpec,
                            value,
                            definition.declaredMacroNames()));
        }

        List<DashboardTreeNode> roots = new ArrayList<>();
        for (Map.Entry<String, List<DashboardTreeNode>> entry : byModule.entrySet()) {
            roots.add(DashboardTreeNode.group("macro-module/" + entry.getKey(), "module." + entry.getKey(), null, null, entry.getValue()));
        }
        return roots;
    }

    private Set<KeyId> collectPureMacroSettings(List<ModuleMacroDefinition> definitions, Map<KeyId, SettingNode<?>> settingsById) {
        Set<KeyId> result = new HashSet<>();
        for (ModuleMacroDefinition definition : definitions) {
            KeyId settingId = definition.settingId();
            if (settingId == null) {
                continue;
            }
            SettingNode<?> setting = settingsById.get(settingId);
            if (setting != null && setting.changeImpact() == ChangeImpact.RECOMPILE_SHADERS) {
                result.add(settingId);
            }
        }
        return result;
    }

    private String resolveDisabledDetail(String controlId, SettingNode<?> setting, ModuleSettingRegistry registry,
                                         DashboardControlAccessor accessor) {
        String accessorDetail = accessor.disabledDetailKey(controlId, registry, setting);
        if (accessorDetail != null) {
            return accessorDetail;
        }
        return setting.detailKey();
    }

    private BlockedState resolveBlockedState(SettingNode<?> setting, ModuleSettingRegistry registry,
                                             Map<KeyId, SettingNode<?>> settingsById) {
        BlockedPath path = findBlockedPath(setting, registry, settingsById, new HashSet<>());
        if (path == null) {
            return BlockedState.NONE;
        }
        return new BlockedState(path.nodeId(), path.displayPath());
    }

    private BlockedPath findBlockedPath(SettingNode<?> setting, ModuleSettingRegistry registry,
                                        Map<KeyId, SettingNode<?>> settingsById, Set<KeyId> visited) {
        if (!visited.add(setting.id())) {
            return null;
        }

        if (setting.parentId() != null) {
            SettingNode<?> parent = settingsById.get(setting.parentId());
            if (parent != null) {
                if (!registry.isPreviewActive(parent.id())) {
                    return findBlockedPath(parent, registry, settingsById, visited);
                }
                if (!parent.isGroup()) {
                    Object parentValue = registry.getPreviewValue(parent.id());
                    if (parentValue instanceof Boolean bool && !bool) {
                        return new BlockedPath(parent.id().toString(), buildPath(parent, settingsById));
                    }
                }
            }
        }

        for (DependencyRule dependency : setting.dependencies()) {
            SettingNode<?> target = settingsById.get(dependency.targetSetting());
            if (target == null) {
                continue;
            }
            if (!registry.isPreviewActive(target.id())) {
                return findBlockedPath(target, registry, settingsById, visited);
            }
            if (dependency.dependencyType() == DependencyRule.DependencyType.REQUIRES_TRUE) {
                Object dependencyValue = registry.getPreviewValue(target.id());
                if (!(dependencyValue instanceof Boolean bool) || !bool) {
                    return new BlockedPath(target.id().toString(), buildPath(target, settingsById));
                }
            }
        }
        return null;
    }

    private List<String> buildPath(SettingNode<?> setting, Map<KeyId, SettingNode<?>> settingsById) {
        LinkedList<String> path = new LinkedList<>();
        SettingNode<?> current = setting;
        int guard = 0;
        while (current != null && guard++ < 32) {
            path.addFirst(current.displayKey());
            current = current.parentId() != null ? settingsById.get(current.parentId()) : null;
        }
        return path;
    }

    private List<DashboardSummaryMetric> mapExtraMetrics(List<DashboardMetricCard> extraMetricCards) {
        List<DashboardSummaryMetric> metrics = new ArrayList<>();
        for (DashboardMetricCard card : extraMetricCards) {
            metrics.add(new DashboardSummaryMetric(card.id(), card.labelKey(), card.valueText(), card.unitText(), card.accentColor(), ""));
        }
        return metrics;
    }

    private List<DashboardSummaryMetric> buildMetricCards(ModuleRuntimeHost runtimeHost, MetricSnapshot metricSnapshot) {
        Map<KeyId, MetricDescriptor> descriptors = new LinkedHashMap<>();
        for (MetricDescriptor descriptor : runtimeHost.metricRegistry().descriptors()) {
            descriptors.put(descriptor.id(), descriptor);
        }

        List<DashboardSummaryMetric> cards = new ArrayList<>();
        for (MetricDescriptor descriptor : descriptors.values()) {
            if (isCullingRatioSource(descriptor.id())) {
                continue;
            }
            Object value = metricSnapshot.value(descriptor.id());
            if (value == null) {
                continue;
            }
            cards.add(new DashboardSummaryMetric(
                    descriptor.id().toString(),
                    descriptor.displayKey(),
                    formatMetricValue(descriptor.kind(), value),
                    metricUnit(descriptor.kind()),
                    metricAccent(descriptor.kind()),
                    Objects.toString(descriptor.detailKey(), "")));
        }
        return cards;
    }

    private List<DashboardSummaryMetric> buildFrameCaptureMetrics(FrameCaptureSnapshot captureSnapshot) {
        if (captureSnapshot == null || (captureSnapshot.stages().isEmpty() && captureSnapshot.resourceBindings().isEmpty()
                && FrameCaptureSnapshot.RenderStateCapture.empty().equals(captureSnapshot.renderState()))) {
            return List.of();
        }

        int packetCount = 0;
        int drawPacketCount = 0;
        int stateCount = 0;
        for (FrameCaptureSnapshot.StageCapture stage : captureSnapshot.stages()) {
            packetCount += stage.packetCount();
            drawPacketCount += stage.drawPacketCount();
            stateCount += stage.states().size();
        }

        List<DashboardSummaryMetric> metrics = new ArrayList<>();
        metrics.add(new DashboardSummaryMetric(
                "frame-capture/stages",
                "debug.dashboard.capture.stages",
                String.valueOf(captureSnapshot.stages().size()),
                "",
                0xFF93C5FD,
                "debug.dashboard.capture.stages.detail"));
        metrics.add(new DashboardSummaryMetric(
                "frame-capture/packets",
                "debug.dashboard.capture.packets",
                String.valueOf(packetCount),
                "pkt",
                0xFF34D399,
                "draw=" + drawPacketCount + ", states=" + stateCount));
        metrics.add(new DashboardSummaryMetric(
                "frame-capture/resources",
                "debug.dashboard.capture.resources",
                String.valueOf(captureSnapshot.resourceBindings().size()),
                "sets",
                0xFFF59E0B,
                renderStateDetail(captureSnapshot.renderState())));
        return metrics;
    }

    private String renderStateDetail(FrameCaptureSnapshot.RenderStateCapture renderState) {
        if (renderState == null || FrameCaptureSnapshot.RenderStateCapture.empty().equals(renderState)) {
            return "No render state was bound when the capture was requested.";
        }
        return "domain=" + renderState.domain()
                + ", shader=" + renderState.shaderId()
                + ", target=" + renderState.renderTargetId()
                + ", layout=" + renderState.resourceLayoutKey()
                + ", stamp=" + renderState.resourceBindingStamp();
    }

    private List<DashboardRatioMetric> buildRatioMetrics(MetricSnapshot metricSnapshot) {
        return List.of(
                buildRatioMetric(
                        "entity-culling",
                        "debug.dashboard.ratio.entity",
                        metricSnapshot.value(ENTITY_HIDDEN_METRIC),
                        metricSnapshot.value(ENTITY_TOTAL_METRIC),
                        0xFF10B981),
                buildRatioMetric(
                        "block-culling",
                        "debug.dashboard.ratio.block_entity",
                        metricSnapshot.value(BLOCK_ENTITY_HIDDEN_METRIC),
                        metricSnapshot.value(BLOCK_ENTITY_TOTAL_METRIC),
                        0xFF3B82F6)
        );
    }

    private DashboardRatioMetric buildRatioMetric(String id, String labelKey, Object hiddenValue, Object totalValue, int accentColor) {
        int hidden = hiddenValue instanceof Number number ? number.intValue() : 0;
        int total = totalValue instanceof Number number ? number.intValue() : 0;
        int visible = Math.max(0, total - hidden);
        double ratio = total <= 0 ? 0.0D : hidden / (double) total;
        return new DashboardRatioMetric(id, labelKey, hidden, visible, total, ratio, accentColor, "");
    }

    private boolean isCullingRatioSource(KeyId id) {
        return id.equals(ENTITY_HIDDEN_METRIC)
                || id.equals(ENTITY_TOTAL_METRIC)
                || id.equals(BLOCK_ENTITY_HIDDEN_METRIC)
                || id.equals(BLOCK_ENTITY_TOTAL_METRIC);
    }

    private List<DashboardMacroConstantView> buildMacroConstants(ModuleRuntimeHost runtimeHost) {
        Set<String> editableMacros = new LinkedHashSet<>();
        for (ModuleMacroDefinition definition : runtimeHost.macroRegistry().allDefinitions()) {
            editableMacros.addAll(definition.declaredMacroNames());
        }

        LinkedHashMap<String, DashboardMacroConstantView> constants = new LinkedHashMap<>();
        for (MacroSnapshotEntry entry : MacroContext.getInstance().mergedEntrySnapshot()) {
            if (editableMacros.contains(entry.name())) {
                continue;
            }
            constants.put(entry.name() + "#" + Objects.toString(entry.sourceId(), ""),
                    new DashboardMacroConstantView(
                            entry.name(),
                            entry.value(),
                            entry.flag(),
                            sourceText(entry),
                            typeText(entry.type()),
                            Objects.toString(entry.detailKey(), "")));
        }
        for (MacroSnapshotEntry entry : MacroContext.getInstance().templateEntrySnapshot()) {
            if (editableMacros.contains(entry.name())) {
                continue;
            }
            String key = entry.name() + "#" + Objects.toString(entry.sourceId(), "");
            constants.putIfAbsent(key,
                    new DashboardMacroConstantView(
                            entry.name(),
                            entry.value(),
                            entry.flag(),
                            sourceText(entry),
                            typeText(entry.type()),
                            Objects.toString(entry.detailKey(), "")));
        }
        List<DashboardMacroConstantView> result = new ArrayList<>(constants.values());
        result.sort(Comparator.comparing(DashboardMacroConstantView::sourceText).thenComparing(DashboardMacroConstantView::name));
        return result;
    }

    private String sourceText(MacroSnapshotEntry entry) {
        return switch (entry.layer()) {
            case GLOBAL -> entry.sourceId() != null ? "global/" + entry.sourceId() : "global";
            case MODULE -> entry.sourceId() != null ? "module/" + entry.sourceId() : "module";
            case RESOURCE_PACK -> entry.sourceId() != null ? "pack/" + entry.sourceId() : "pack";
            case TEMPLATE -> entry.sourceId() != null ? "template/" + entry.sourceId() : "template";
            case CONFIG -> "config";
            case DYNAMIC -> "dynamic";
        };
    }

    private String typeText(MacroEntryType type) {
        return switch (type) {
            case FLAG -> "flag";
            case VALUE -> "value";
            case CHOICE -> "choice";
            case CONSTANT -> "constant";
        };
    }

    private String formatMetricValue(MetricKind kind, Object value) {
        if (value == null) {
            return "-";
        }
        return switch (kind) {
            case BOOLEAN -> Boolean.TRUE.equals(value) ? "On" : "Off";
            case FLOAT, DURATION -> value instanceof Number number ? String.format(Locale.ROOT, "%.2f", number.doubleValue()) : String.valueOf(value);
            case BYTES -> value instanceof Number number
                    ? DashboardMemorySectionBuilder.formatBytes(number.longValue())
                    : String.valueOf(value);
            case BYTES_PER_SECOND -> value instanceof Number number
                    ? DashboardMemorySectionBuilder.formatBytesPerSecond(number.doubleValue())
                    : String.valueOf(value);
            case PERCENT -> value instanceof Number number
                    ? DashboardMemorySectionBuilder.formatPercent(number.doubleValue())
                    : String.valueOf(value);
            default -> String.valueOf(value);
        };
    }

    private String metricUnit(MetricKind kind) {
        return kind == MetricKind.DURATION ? "ms" : "";
    }

    private int metricAccent(MetricKind kind) {
        return switch (kind) {
            case DURATION -> 0xFF0EA5E9;
            case COUNT -> 0xFF10B981;
            case BOOLEAN -> 0xFFF59E0B;
            case BYTES -> 0xFF3B82F6;
            case BYTES_PER_SECOND -> 0xFF14B8A6;
            case PERCENT -> 0xFFF59E0B;
            default -> 0xFFA78BFA;
        };
    }

    private void traceSettingNode(SettingNode<?> setting, List<DashboardTreeNode> childNodes) {
        if (!TRACE_SETTING_TREE || childNodes.isEmpty() || !TRACED_SETTING_NODES.add(setting.id().toString())) {
            return;
        }
        System.out.println("[Sketch][Dashboard] retained setting subtree id=" + setting.id()
                + " module=" + setting.moduleId()
                + " childCount=" + childNodes.size());
    }

    private record BlockedPath(String nodeId, List<String> displayPath) {
    }

    private record BlockedState(String nodeId, List<String> displayPath) {
        private static final BlockedState NONE = new BlockedState(null, List.of());
    }
}



