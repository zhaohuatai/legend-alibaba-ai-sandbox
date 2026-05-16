package org.legend.framework.ai.alibaba.sandbox.backend.docker;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

/**
 * Docker 容器配置记录。
 * <p>容器安全配置：
 * <ul>
 *   <li>内存限制：默认 512MB（可配置）</li>
 *   <li>Swap 限制：禁用 Swap（MemorySwap = Memory）</li>
 *   <li>CPU 配额：默认 0.5 CPU（可配置）</li>
 *   <li>PIDs 限制：256 个进程（防止 fork bomb）</li>
 *   <li>只读根文件系统：防止修改系统文件</li>
 *   <li>丢弃所有 Capabilities：最小权限原则</li>
 *   <li>no-new-privileges：防止提权</li>
 *   <li>网络模式：bridge（可断开实现网络隔离）</li>
 *   <li>临时文件系统：/tmp tmpfs 64MB</li>
 * </ul>
 *
 * <p>该记录封装了容器的所有配置参数，支持通过 {@link #defaults()} 获取默认配置，
 * 并通过 withXxx 方法链式修改配置。
 *
 * @param image              Docker 镜像名称（默认 "skill-sandbox:latest"）
 * @param warmSize           预热池大小（默认 3，仅用于池化方案）
 * @param memBytes           每个容器的内存限制（字节，默认 512MB）
 * @param cpuQuota           每个容器的 CPU 配额（微秒/周期，默认 50,000）
 * @param hostWorkspaceRoot  宿主机工作区根目录（默认 "/var/skills/workspaces"）
 * @param idleTimeout        容器空闲超时（默认 15 分钟，仅用于池化方案）
 * @param mounts             目录挂载列表（默认空列表）
 */
public record DockerConfig(
        /** Docker 镜像名称 */
		String image,

        /** 预热池大小（仅用于池化方案） */
        int warmSize,

        /** 每个容器的内存限制（字节） */
        long memBytes,

        /** 每个容器的 CPU 配额（微秒/周期） */
        long cpuQuota,

        /** 宿主机工作区根目录 */
        Path hostWorkspaceRoot,

        /** 容器空闲超时（仅用于池化方案） */
        Duration idleTimeout,

        /** 目录挂载列表 */
        List<MountConfig> mounts
    ) {

    /**
     * 获取默认配置。
     *
     * <p>默认配置值：
     * <ul>
     *   <li>image: "skill-sandbox:latest"</li>
     *   <li>warmSize: 3</li>
     *   <li>memBytes: 512MB</li>
     *   <li>cpuQuota: 50,000（0.5 CPU）</li>
     *   <li>hostWorkspaceRoot: "/var/skills/workspaces"</li>
     *   <li>idleTimeout: 15 分钟</li>
     *   <li>mounts: 空列表</li>
     * </ul>
     *
     * @return 默认配置对象
     */
    public static DockerConfig defaults() {
        return new DockerConfig(
            "skill-sandbox:latest",
            3,
            512L * 1024 * 1024,
            50_000L,
            Paths.get("/var/skills/workspaces"),
            Duration.ofMinutes(15),
            Collections.emptyList()
        );
    }

    /**
     * 设置 Docker 镜像名称。
     *
     * @param image Docker 镜像名称
     * @return 新的 Config 对象（record 是不可变的）
     */
    public DockerConfig withImage(String image) {
        return new DockerConfig(image, warmSize, memBytes, cpuQuota, hostWorkspaceRoot, idleTimeout, mounts);
    }

    /**
     * 设置预热池大小。
     *
     * @param warmSize 预热池大小
     * @return 新的 Config 对象
     */
    public DockerConfig withWarmSize(int warmSize) {
        return new DockerConfig(image, warmSize, memBytes, cpuQuota, hostWorkspaceRoot, idleTimeout, mounts);
    }

    /**
     * 设置每个容器的内存限制。
     *
     * @param memBytes 内存限制（字节）
     * @return 新的 Config 对象
     */
    public DockerConfig withMemBytes(long memBytes) {
        return new DockerConfig(image, warmSize, memBytes, cpuQuota, hostWorkspaceRoot, idleTimeout, mounts);
    }

    /**
     * 设置每个容器的 CPU 配额。
     *
     * @param cpuQuota CPU 配额（微秒/周期）
     * @return 新的 Config 对象
     */
    public DockerConfig withCpuQuota(long cpuQuota) {
        return new DockerConfig(image, warmSize, memBytes, cpuQuota, hostWorkspaceRoot, idleTimeout, mounts);
    }

    /**
     * 设置宿主机工作区根目录。
     *
     * @param hostWorkspaceRoot 宿主机工作区根目录
     * @return 新的 Config 对象
     */
    public DockerConfig withHostWorkspaceRoot(Path hostWorkspaceRoot) {
        return new DockerConfig(image, warmSize, memBytes, cpuQuota, hostWorkspaceRoot, idleTimeout, mounts);
    }

    /**
     * 设置容器空闲超时。
     *
     * @param idleTimeout 容器空闲超时
     * @return 新的 Config 对象
     */
    public DockerConfig withIdleTimeout(Duration idleTimeout) {
        return new DockerConfig(image, warmSize, memBytes, cpuQuota, hostWorkspaceRoot, idleTimeout, mounts);
    }

    /**
     * 添加目录挂载（读写模式）。
     *
     * <p>使用示例：
     * <pre>{@code
     * DockerConfig cfg = DockerConfig.defaults()
     *     .withMount(Paths.get("E:/skills/data-analysis"), "/skill")
     *     .withMount(Paths.get("E:/data/input"), "/data/input")
     *     .withMount(Paths.get("E:/output"), "/output");
     * }</pre>
     *
     * @param hostPath      宿主机目录路径
     * @param containerPath 容器内挂载路径
     * @return 新的 Config 对象
     */
    public DockerConfig withMount(Path hostPath, String containerPath) {
        return withMount(hostPath, containerPath, false);
    }

    /**
     * 添加目录挂载（可指定读写模式）。
     *
     * <p>使用示例：
     * <pre>{@code
     * DockerConfig cfg = DockerConfig.defaults()
     *     .withMount(Paths.get("E:/skills/data-analysis"), "/skill", true)  // 只读
     *     .withMount(Paths.get("E:/data/input"), "/data/input", true)       // 只读
     *     .withMount(Paths.get("E:/output"), "/output", false);             // 读写
     * }</pre>
     *
     * @param hostPath      宿主机目录路径
     * @param containerPath 容器内挂载路径
     * @param readOnly      是否只读挂载
     * @return 新的 Config 对象
     */
    public DockerConfig withMount(Path hostPath, String containerPath, boolean readOnly) {
        List<MountConfig> newMounts = new ArrayList<>(mounts);
        newMounts.add(new MountConfig(hostPath, containerPath, readOnly));
        return new DockerConfig(image, warmSize, memBytes, cpuQuota, hostWorkspaceRoot, idleTimeout, newMounts);
    }

    /**
     * 批量设置目录挂载列表。
     *
     * @param mounts 挂载列表
     * @return 新的 Config 对象
     */
    public DockerConfig withMounts(List<MountConfig> mounts) {
        return new DockerConfig(image, warmSize, memBytes, cpuQuota, hostWorkspaceRoot, idleTimeout, mounts);
    }
}
