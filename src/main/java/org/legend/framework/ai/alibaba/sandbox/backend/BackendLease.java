package org.legend.framework.ai.alibaba.sandbox.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 沙箱租约记录，封装沙箱后端实例和关闭器。
 *
 * <p>该记录实现了 {@link AutoCloseable} 接口，支持 try-with-resources 语法，
 * 确保沙箱资源在使用完毕后正确释放。
 *
 * <p>使用示例：
 * <pre>{@code
 * try (BackendLease lease = provider.acquire(skill)) {
 *     SandboxBackend backend = lease.backend();
 *     // 使用 backend 执行命令、读写文件
 * } // 自动调用 close() 释放资源
 * }</pre>
 *
 * @param backend 沙箱后端实例，用于执行命令和文件操作
 * @param closer  关闭器，用于释放沙箱资源（如关闭进程、回收容器到池中）
 * @param kind    沙箱类型标识（"local" 或 "docker"），用于审计日志
 */
public record BackendLease (
        /** 沙箱后端实例，用于执行命令和文件操作 */
        SandboxBackend backend,

        /** 关闭器，用于释放沙箱资源 */
        AutoCloseable closer,

        /** 沙箱类型标识（"local" 或 "docker"） */
        String kind
    ) implements AutoCloseable{
	private static final Logger logger = LoggerFactory.getLogger(BackendLease.class);
    /**
     * 关闭沙箱租约，释放沙箱资源。
     *
     * <p>该方法调用 closer 的 close 方法释放沙箱资源。
     * 对于本地进程沙箱，这会终止子进程；对于 Docker 容器沙箱，
     * 这会将容器回收到预热池中。
     *
     * <p>如果关闭过程中发生异常，该方法会静默忽略，
     * 确保 try-with-resources 块不会因关闭失败而抛出异常。
     */
    @Override
    public void close() {
        try {
            closer.close();
        } catch (Exception e) {
        	// 静默忽略关闭异常，确保资源释放不会中断主流程
        	logger.warn("Failed to close backend lease (kind={})", kind, e);
            
        }
    }


}
