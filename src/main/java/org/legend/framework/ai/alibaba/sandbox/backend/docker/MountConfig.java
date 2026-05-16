package org.legend.framework.ai.alibaba.sandbox.backend.docker;

import java.nio.file.Path;
/**
 * 目录挂载配置。
 *
 * <p>用于定义宿主机目录到容器内目录的映射关系。
 *
 * <p>使用示例：
 * <pre>{@code
 * MountConfig mount = new MountConfig(
 *     Paths.get("E:/skills/data-analysis"),  // 宿主机目录
 *     "/skill",                              // 容器内路径
 *     true                                   // 只读挂载
 * );
 * }</pre>
 *
 * @param hostPath      宿主机目录路径（绝对路径）
 * @param containerPath 容器内挂载路径（绝对路径，如 /skill、/data/input）
 * @param readOnly      是否只读挂载（true=只读，false=读写）
 */
public record MountConfig(
        /** 宿主机目录路径 */
        Path hostPath,

        /** 容器内挂载路径 */
        String containerPath,

        /** 是否只读挂载 */
        boolean readOnly ) {
    /**
     * 创建读写挂载配置。
     *
     * @param hostPath      宿主机目录路径
     * @param containerPath 容器内挂载路径
     */
    public MountConfig(Path hostPath, String containerPath) {
        this(hostPath, containerPath, false);
    }


}
