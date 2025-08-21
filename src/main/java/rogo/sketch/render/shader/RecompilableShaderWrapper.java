package rogo.sketch.render.shader;

import rogo.sketch.render.shader.config.ShaderConfiguration;
import rogo.sketch.render.shader.config.ShaderConfigurationManager;
import rogo.sketch.render.shader.preprocessor.PreprocessorResult;
import rogo.sketch.render.shader.preprocessor.ShaderPreprocessor;
import rogo.sketch.render.shader.preprocessor.ShaderPreprocessorException;
import rogo.sketch.render.shader.preprocessor.ShaderResourceProvider;
import rogo.sketch.util.Identifier;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 可重编译Shader包装器
 * 将可重编译功能作为特性附加到现有的Shader实现上
 */
public class RecompilableShaderWrapper implements RecompilableShaderFeature {

    private final Identifier identifier;
    private final Supplier<Shader> shaderFactory;
    private final ShaderPreprocessor preprocessor;
    private final ShaderResourceProvider resourceProvider;
    private final Map<ShaderType, String> originalSources;

    private Shader currentShader;
    private PreprocessorResult lastPreprocessingResult;
    private final Map<Identifier, Long> dependencyLastModified = new ConcurrentHashMap<>();
    private final Set<Consumer<Shader>> recompilationListeners = ConcurrentHashMap.newKeySet();

    /**
     * 创建可重编译Shader包装器
     *
     * @param identifier       shader标识符
     * @param originalSources  原始shader源码
     * @param shaderFactory    shader创建工厂（用于重编译时创建新的shader实例）
     * @param preprocessor     预处理器
     * @param resourceProvider 资源提供者
     */
    public RecompilableShaderWrapper(Identifier identifier,
                                     Map<ShaderType, String> originalSources,
                                     Supplier<Shader> shaderFactory,
                                     ShaderPreprocessor preprocessor,
                                     ShaderResourceProvider resourceProvider) throws IOException {
        this.identifier = identifier;
        this.originalSources = new HashMap<>(originalSources);
        this.shaderFactory = shaderFactory;
        this.preprocessor = preprocessor;
        this.resourceProvider = resourceProvider;

        // 注册配置变更监听器
        ShaderConfigurationManager.getInstance().addConfigurationListener(identifier, this::onConfigurationChanged);

        // 初始编译
        performInitialCompilation();
    }

    /**
     * 获取当前的shader实例
     */
    public Shader getShader() {
        return currentShader;
    }

    private void performInitialCompilation() throws IOException {
        Map<ShaderType, String> processedSources = preprocessSources();
        this.currentShader = createShaderWithSources(processedSources);
        updateDependencyTimestamps();
    }

    private Map<ShaderType, String> preprocessSources() throws IOException {
        try {
            ShaderConfiguration config = ShaderConfigurationManager.getInstance().getConfiguration(identifier);
            Map<String, String> macros = new HashMap<>(config.getMacros());

            Map<ShaderType, String> processedSources = new HashMap<>();

            for (Map.Entry<ShaderType, String> entry : originalSources.entrySet()) {
                PreprocessorResult result = preprocessor.process(
                        entry.getValue(),
                        identifier,
                        macros
                );
                processedSources.put(entry.getKey(), result.processedSource());

                // 记录依赖
                if (lastPreprocessingResult == null || entry.getKey() == getMainShaderType()) {
                    lastPreprocessingResult = result;
                }
            }

            return processedSources;

        } catch (ShaderPreprocessorException e) {
            throw new IOException("Shader preprocessing failed for " + identifier, e);
        }
    }

    private ShaderType getMainShaderType() {
        // 返回主要的shader类型，用于记录依赖
        if (originalSources.containsKey(ShaderType.COMPUTE)) {
            return ShaderType.COMPUTE;
        } else if (originalSources.containsKey(ShaderType.FRAGMENT)) {
            return ShaderType.FRAGMENT;
        } else {
            return originalSources.keySet().iterator().next();
        }
    }

    private Shader createShaderWithSources(Map<ShaderType, String> processedSources) throws IOException {
        // 这里我们需要根据shader类型创建相应的实例
        // 保持原有的ComputeShader和GraphicsShader实现
        if (processedSources.containsKey(ShaderType.COMPUTE) && processedSources.size() == 1) {
            return new ComputeShader(identifier, processedSources.get(ShaderType.COMPUTE));
        } else {
            return new GraphicsShader(identifier, processedSources);
        }
    }

    @Override
    public boolean needsRecompilation() {
        return hasDependencyChanges() || hasConfigurationChanges();
    }

    private boolean hasConfigurationChanges() {
        // 检查配置是否发生变化
        // 这里可以通过比较当前配置与上次编译时的配置来判断
        return false; // 简化实现，实际应该检查配置变更
    }

    @Override
    public void recompile() throws IOException {
        if (!needsRecompilation()) {
            return; // 无需重编译
        }
        forceRecompile();
    }

    @Override
    public void forceRecompile() throws IOException {
        System.out.println("Recompiling shader: " + identifier);

        // 保存旧的shader
        Shader oldShader = currentShader;

        try {
            // 重新预处理和编译
            Map<ShaderType, String> processedSources = preprocessSources();
            Shader newShader = createShaderWithSources(processedSources);

            // 更新当前shader
            this.currentShader = newShader;
            updateDependencyTimestamps();

            // 通知监听器
            notifyRecompilationListeners(newShader);

            System.out.println("Shader recompiled successfully: " + identifier);

        } catch (Exception e) {
            System.err.println("Shader recompilation failed for " + identifier + ": " + e.getMessage());
            // 保持旧的shader不变
            throw new IOException("Recompilation failed", e);
        } finally {
            // 清理旧的shader资源
            if (oldShader != null && oldShader != currentShader) {
                try {
                    oldShader.dispose();
                } catch (Exception e) {
                    System.err.println("Failed to dispose old shader: " + e.getMessage());
                }
            }
        }
    }

    @Override
    public Set<Identifier> getDependencies() {
        return lastPreprocessingResult != null ?
                lastPreprocessingResult.importedFiles() :
                Collections.emptySet();
    }

    @Override
    public PreprocessorResult getLastPreprocessingResult() {
        return lastPreprocessingResult;
    }

    @Override
    public void addRecompilationListener(Consumer<Shader> listener) {
        recompilationListeners.add(listener);
    }

    @Override
    public void removeRecompilationListener(Consumer<Shader> listener) {
        recompilationListeners.remove(listener);
    }

    @Override
    public Map<ShaderType, String> getOriginalSources() {
        return new HashMap<>(originalSources);
    }

    @Override
    public boolean hasDependencyChanges() {
        if (lastPreprocessingResult == null) {
            return false;
        }

        for (Identifier dependency : lastPreprocessingResult.importedFiles()) {
            try {
                // 这里应该检查文件的最后修改时间
                // 简化实现，实际应该通过resourceProvider检查文件时间戳
                Long lastKnownTime = dependencyLastModified.get(dependency);
                if (lastKnownTime == null) {
                    return true; // 新的依赖
                }
                // 实际实现应该检查文件的真实修改时间
            } catch (Exception e) {
                System.err.println("Failed to check dependency " + dependency + ": " + e.getMessage());
            }
        }

        return false;
    }

    @Override
    public void updateDependencyTimestamps() {
        if (lastPreprocessingResult != null) {
            long currentTime = System.currentTimeMillis();
            for (Identifier dependency : lastPreprocessingResult.importedFiles()) {
                dependencyLastModified.put(dependency, currentTime);
            }
        }
    }

    private void onConfigurationChanged(ShaderConfiguration newConfiguration) {
        try {
            forceRecompile();
        } catch (IOException e) {
            System.err.println("Failed to recompile shader after configuration change: " + e.getMessage());
        }
    }

    private void notifyRecompilationListeners(Shader newShader) {
        for (Consumer<Shader> listener : recompilationListeners) {
            try {
                listener.accept(newShader);
            } catch (Exception e) {
                System.err.println("Recompilation listener failed: " + e.getMessage());
            }
        }
    }

    /**
     * 清理资源
     */
    public void dispose() {
        if (currentShader != null) {
            currentShader.dispose();
        }
        recompilationListeners.clear();
        dependencyLastModified.clear();
        ShaderConfigurationManager.getInstance().removeConfigurationListener(identifier, this::onConfigurationChanged);
    }
}
