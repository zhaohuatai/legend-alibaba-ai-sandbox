package org.legend.framework.ai.alibaba.sandbox.skills.manifest;

/**
 * 策略决策记录，表示工具调用安全检查的结果。
 *
 * <p>该类是 Java record 类型，用于封装策略检查的决策结果，包含两个字段：
 * <ul>
 *   <li>{@code denied} - 是否被拒绝（true 表示拒绝，false 表示允许）</li>
 *   <li>{@code reason} - 拒绝原因（如果 denied 为 false，则为空字符串）</li>
 * </ul>
 *
 * <p>核心设计理念：
 * <ul>
 *   <li><b>不可变性</b> - 作为 record 类型，实例创建后不可修改，确保线程安全</li>
 *   <li><b>语义清晰</b> - 通过静态工厂方法 {@link #allow()} 和 {@link #deny(String)} 创建实例</li>
 *   <li><b>轻量级</b> - 使用 record 而非 class，减少样板代码</li>
 * </ul>
 *
 * <p>使用场景：
 * <ul>
 *   <li>{@link org.legend.framework.ai.alibaba.sandbox.GlobalPolicy#check(String, java.util.Map)} 返回策略决策</li>
 *   <li>{@link org.legend.framework.ai.alibaba.sandbox.tool.GuardedToolCallback#checkSecurity(String)} 根据决策结果决定是否允许工具调用</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 创建允许决策
 * PolicyDecision allowed = PolicyDecision.allow();
 * if (!allowed.denied()) {
 *     // 执行工具调用
 * }
 *
 * // 创建拒绝决策
 * PolicyDecision denied = PolicyDecision.deny("blocked by global policy");
 * if (denied.denied()) {
 *     throw new ToolDeniedException(denied.reason());
 * }
 * }</pre>
 *
 * @param denied 是否被拒绝（true 表示拒绝，false 表示允许）
 * @param reason 拒绝原因（如果 denied 为 false，则为空字符串）
 */
public record PolicyDecision(
    /**
     * 是否被拒绝标志。
     *
     * <p>该字段表示工具调用是否被策略拒绝：
     * <ul>
     *   <li>{@code true} - 工具调用被拒绝，应该中断执行</li>
     *   <li>{@code false} - 工具调用被允许，可以继续执行</li>
     * </ul>
     *
     * <p>在 {@link org.legend.framework.ai.alibaba.skill.guard.GuardedToolCallback#checkSecurity(String)} 中，
     * 该字段用于判断是否抛出 {@link org.legend.framework.ai.alibaba.skill.guard.ToolDeniedException}。
     */
    boolean denied,

    /**
     * 拒绝原因消息。
     *
     * <p>该字段包含拒绝工具调用的具体原因，仅在 {@link #denied} 为 true 时有意义。
     * 如果 {@link #denied} 为 false，该字段为空字符串。
     *
     * <p>常见拒绝原因：
     * <ul>
     *   <li>{@code "Bash(command:rm -rf /)"} - 全局策略拒绝的危险命令</li>
     *   <li>{@code "not-in-allowed-tools"} - Skill 权限拒绝，工具不在允许列表中</li>
     * </ul>
     *
     * <p>该原因消息通常用于：
     * <ul>
     *   <li>记录审计日志（通过 {@link org.legend.framework.ai.alibaba.skill.guard.AuditLog#recordDenied}）</li>
     *   <li>构造异常消息（通过 {@link org.legend.framework.ai.alibaba.skill.guard.ToolDeniedException}）</li>
     * </ul>
     */
    String reason
) {
    /**
     * 创建允许决策。
     *
     * <p>该方法创建一个表示允许的策略决策实例，其中：
     * <ul>
     *   <li>{@code denied} 字段为 {@code false}</li>
     *   <li>{@code reason} 字段为空字符串</li>
     * </ul>
     *
     * <p>该方法通常在以下情况使用：
     * <ul>
     *   <li>全局策略检查未匹配任何拒绝策略</li>
     *   <li>Skill 权限检查匹配到允许策略</li>
     * </ul>
     *
     * @return 允许决策实例
     */
    public static PolicyDecision allow() {
        return new PolicyDecision(false, "");
    }

    /**
     * 创建拒绝决策。
     *
     * <p>该方法创建一个表示拒绝的策略决策实例，其中：
     * <ul>
     *   <li>{@code denied} 字段为 {@code true}</li>
     *   <li>{@code reason} 字段为传入的拒绝原因</li>
     * </ul>
     *
     * <p>该方法通常在以下情况使用：
     * <ul>
     *   <li>全局策略检查匹配到拒绝策略</li>
     *   <li>Skill 权限检查未匹配任何允许策略</li>
     * </ul>
     *
     * @param r 拒绝原因（如 "Bash(command:rm -rf /)" 或 "not-in-allowed-tools"）
     * @return 拒绝决策实例
     */
    public static PolicyDecision deny(String r) {
        return new PolicyDecision(true, r);
    }
}
