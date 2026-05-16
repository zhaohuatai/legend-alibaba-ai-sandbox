package org.legend.framework.ai.alibaba.sandbox;

import java.util.List;
import java.util.Map;

import org.legend.framework.ai.alibaba.sandbox.skills.manifest.PolicyDecision;
import org.legend.framework.ai.alibaba.sandbox.skills.manifest.ToolPolicy;
import org.legend.framework.ai.alibaba.sandbox.tool.GuardedToolCallback;

/**
 * 全局策略检查器，定义系统级别的工具调用拒绝策略。
 *
 * <p>该类提供全局级别的工具调用限制，独立于 Skill 的允许工具列表。
 * 全局策略用于禁止某些危险操作，无论 Skill 的权限如何。
 *
 * <p>核心设计理念：
 * <ul>
 *   <li><b>系统级安全</b> - 全局策略优先于 Skill 权限检查，确保危险操作被绝对禁止</li>
 *   <li><b>静态配置</b> - 策略列表在应用启动时初始化，运行时不可变</li>
 *   <li><b>模式匹配</b> - 使用 {@link ToolPolicy} 进行工具名称和参数的模式匹配</li>
 * </ul>
 *
 * <p>检查流程：
 * <ol>
 *   <li>在 {@link GuardedToolCallback#checkSecurity(String)} 中首先调用本类的 {@link #check(String, Map)} 方法</li>
 *   <li>遍历全局拒绝策略列表，检查工具调用是否匹配任何拒绝策略</li>
 *   <li>如果匹配，返回拒绝决策，中断工具调用流程</li>
 *   <li>如果不匹配任何策略，返回允许决策，继续执行 Skill 权限检查</li>
 * </ol>
 *
 * <p>策略初始化：
 * <pre>{@code
 * // 初始化全局拒绝策略列表
 * GlobalPolicy.init(List.of(
 *     "Bash(command:rm -rf /)",      // 禁止删除根目录
 *     "Bash(command:sudo *)",        // 禁止使用 sudo
 *     "Bash(command:su *)"           // 禁止切换用户
 * ));
 * }</pre>
 *
 * <p>策略语法说明：
 * <ul>
 *   <li>{@code ToolName} - 拒绝该工具的所有调用</li>
 *   <li>{@code ToolName(param:value)} - 拒绝该工具在参数匹配时的调用</li>
 *   <li>{@code ToolName(param:regex)} - 使用正则表达式匹配参数值</li>
 * </ul>
 *
 * @see ToolPolicy 工具策略，用于解析和匹配策略规则
 * @see PolicyDecision 策略决策，表示允许或拒绝的结果
 * @see GuardedToolCallback 门控工具回调，使用本类进行全局策略检查
 */
public class GlobalPolicy {

    /**
     * 全局拒绝策略列表，使用 volatile 确保多线程可见性。
     *
     * <p>该列表在应用启动时通过 {@link #init(List)} 方法初始化，
     * 之后在运行时不可变。使用 volatile 确保在多线程环境中
     * 所有线程都能看到最新的策略列表。
     *
     * <p>默认值为空列表，表示没有全局拒绝策略。
     */
    private static volatile List<ToolPolicy> denyList = List.of();

    /**
     * 私有构造函数，防止实例化。
     *
     * <p>该类仅提供静态工具方法，不需要创建实例。
     */
    private GlobalPolicy() {}

    /**
     * 初始化全局拒绝策略列表。
     *
     * <p>该方法将原始策略字符串列表解析为 {@link ToolPolicy} 对象列表。
     * 解析过程中会过滤掉空白字符串，确保策略列表的有效性。
     *
     * <p>该方法通常在应用启动时调用一次，之后策略列表不可变。
     * 如果需要更新策略，可以再次调用本方法。
     *
     * <p>解析流程：
     * <ol>
     *   <li>检查输入列表是否为 null，如果是则使用空列表</li>
     *   <li>过滤掉空白字符串（使用 {@link String#isBlank()}）</li>
     *   <li>将每个策略字符串解析为 {@link ToolPolicy} 对象</li>
     *   <li>更新静态的 {@link #denyList} 变量</li>
     * </ol>
     *
     * @param denyRaw 原始策略字符串列表，可以为 null
     */
    public static void init(List<String> denyRaw) {
        // 解析原始策略字符串为 ToolPolicy 对象列表
        denyList = denyRaw == null ? List.of()
            : denyRaw.stream()
                // 过滤掉空白字符串
                .filter(s -> !s.isBlank())
                // 解析为 ToolPolicy 对象
                .map(ToolPolicy::parse)
                // 收集为不可变列表
                .toList();
    }

    /**
     * 检查工具调用是否违反全局拒绝策略。
     *
     * <p>该方法遍历全局拒绝策略列表，检查工具调用是否匹配任何拒绝策略。
     * 匹配规则由 {@link ToolPolicy#matches(String, Map)} 方法定义。
     *
     * <p>检查流程：
     * <ol>
     *   <li>遍历 {@link #denyList} 中的每个策略</li>
     *   <li>调用 {@link ToolPolicy#matches(String, Map)} 检查是否匹配</li>
     *   <li>如果匹配，返回拒绝决策，包含拒绝原因</li>
     *   <li>如果遍历完所有策略都没有匹配，返回允许决策</li>
     * </ol>
     *
     * <p>使用示例：
     * <pre>{@code
     * PolicyDecision decision = GlobalPolicy.check("Bash", Map.of("command", "rm -rf /"));
     * if (decision.denied()) {
     *     throw new ToolDeniedException("blocked by global policy: " + decision.reason());
     * }
     * }</pre>
     *
     * @param tool 工具名称（如 "Read"、"Write"、"Bash"）
     * @param args 工具调用的参数映射（如 {"command": "ls -la"}）
     * @return 策略决策实例，表示允许或拒绝
     */
    public static PolicyDecision check(String tool, Map<String, Object> args) {
        // 遍历全局拒绝策略列表
        for (ToolPolicy p : denyList) {
            // 检查工具调用是否匹配当前策略
            if (p.matches(tool, args)) {
                // 匹配，返回拒绝决策
                return PolicyDecision.deny(p.tool() + describe(p));
            }
        }
        // 没有匹配任何策略，返回允许决策
        return PolicyDecision.allow();
    }

    /**
     * 生成策略的描述字符串，用于拒绝原因消息。
     *
     * <p>该方法将策略的工具名称和约束条件格式化为可读的字符串。
     * 如果策略没有约束条件，仅返回工具名称。
     * 如果有约束条件，返回格式为 "ToolName(key1:regex1,key2:regex2)"。
     *
     * <p>示例：
     * <ul>
     *   <li>无约束：{@code "Bash"}</li>
     *   <li>有约束：{@code "Bash(command:rm -rf /)"}</li>
     * </ul>
     *
     * @param p 要描述的策略对象
     * @return 策略的描述字符串
     */
    private static String describe(ToolPolicy p) {
        // 如果策略没有约束条件，返回空字符串
        return p.constraints().isEmpty() ? "" :
            // 格式化约束条件为 "key:regex" 格式
            "(" + p.constraints().stream()
                // 将每个约束条件格式化为 "key:regex" 字符串
                .map(c -> c.key() + ":" + c.regex().pattern())
                // 用逗号连接所有约束条件
                .reduce((a, b) -> a + "," + b).orElse("") + ")";
    }
}
