package rogo.sketch.render.shader;

import rogo.sketch.render.shader.preprocessor.PreprocessorResult;
import rogo.sketch.util.Identifier;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * 可重编译Shader特性接口
 * 提供shader的动态重编译能力，不影响原有的shader实现
 */
public interface RecompilableShaderFeature {

    /**
     * 检查是否需要重编译
     *
     * @return 如果需要重编译则返回true
     */
    boolean needsRecompilation();

    /**
     * 执行重编译
     *
     * @throws IOException 如果重编译失败
     */
    void recompile() throws IOException;

    /**
     * 强制重编译，忽略依赖检查
     *
     * @throws IOException 如果重编译失败
     */
    void forceRecompile() throws IOException;

    /**
     * 获取shader的依赖文件
     *
     * @return 依赖的shader文件标识符集合
     */
    Set<Identifier> getDependencies();

    /**
     * 获取最后的预处理结果
     *
     * @return 预处理结果，如果未进行预处理则返回null
     */
    PreprocessorResult getLastPreprocessingResult();

    /**
     * 添加重编译监听器
     *
     * @param listener 重编译完成时的回调
     */
    void addRecompilationListener(Consumer<Shader> listener);

    /**
     * 移除重编译监听器
     *
     * @param listener 要移除的监听器
     */
    void removeRecompilationListener(Consumer<Shader> listener);

    /**
     * 获取原始的shader源码（预处理前）
     *
     * @return 原始源码映射
     */
    Map<ShaderType, String> getOriginalSources();

    /**
     * 检查依赖是否已变更
     *
     * @return 如果有依赖变更则返回true
     */
    boolean hasDependencyChanges();

    /**
     * 更新依赖的最后修改时间记录
     */
    void updateDependencyTimestamps();
}
