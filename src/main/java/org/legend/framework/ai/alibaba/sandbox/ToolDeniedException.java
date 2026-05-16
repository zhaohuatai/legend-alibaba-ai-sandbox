package org.legend.framework.ai.alibaba.sandbox;

import org.legend.framework.ai.alibaba.sandbox.tool.GuardedToolCallback;

/**
 * 工具拒绝异常，当工具调用违反安全策略时抛出。
 *
 * <p>该异常是 Skill 安全门控框架的核心异常类，在以下情况下抛出：
 * <ul>
 *   <li>工具调用违反全局拒绝策略（由 {@link GlobalPolicy} 检查）</li>
 *   <li>工具调用不在 Skill 的允许工具列表中（由 {@link GuardedToolCallback} 检查）</li>
 *   <li>工具参数不匹配 Skill 的策略约束（由 {@link org.legend.framework.ai.alibaba.sandbox.skills.manifest.ToolPolicy} 检查）</li>
 * </ul>
 *
 * <p>异常处理流程：
 * <ol>
 *   <li>{@link GuardedToolCallback#checkSecurity(String)} 执行安全检查</li>
 *   <li>如果检查失败，调用 {@link org.legend.framework.ai.alibaba.sandbox.agent.audit.AuditLog#recordDenied} 记录审计日志</li>
 *   <li>抛出本异常，中断工具调用流程</li>
 *   <li>上层调用者捕获异常并返回错误信息给 LLM</li>
 * </ol>
 *
 * <p>使用示例：
 * <pre>{@code
 * try {
 *     String result = tool.call(toolInput);
 * } catch (ToolDeniedException e) {
 *     // 处理工具被拒绝的情况
 *     System.err.println("Tool denied: " + e.getMessage());
 * }
 * }</pre>
 *
 * @see GuardedToolCallback 门控工具回调包装器
 * @see GlobalPolicy 全局策略检查器
 * @see org.legend.framework.ai.alibaba.sandbox.skills.manifest.ToolPolicy 工具策略
 */
public class ToolDeniedException extends RuntimeException {

    /**
     * 创建工具拒绝异常实例。
     *
     * <p>该构造函数接受拒绝原因消息，并将其传递给父类 {@link RuntimeException}。
     * 拒绝原因消息通常包含以下信息：
     * <ul>
     *   <li>拒绝类型（全局策略拒绝或 Skill 权限拒绝）</li>
     *   <li>具体的拒绝原因（如 "blocked by global policy" 或 "not-in-allowed-tools"）</li>
     * </ul>
     *
     * @param msg 拒绝原因消息，不能为 null
     */
    public ToolDeniedException(String msg) {
        // 调用父类构造函数，设置异常消息
        super(msg);
    }
}
