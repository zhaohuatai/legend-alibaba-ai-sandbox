package org.legend.framework.ai.alibaba.sandbox.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;

import org.legend.framework.ai.alibaba.sandbox.GlobalPolicy;
import org.legend.framework.ai.alibaba.sandbox.GuardedSkillMetadata;
import org.legend.framework.ai.alibaba.sandbox.ToolDeniedException;
import org.legend.framework.ai.alibaba.sandbox.agent.audit.AuditLog;
import org.legend.framework.ai.alibaba.sandbox.skills.manifest.PolicyDecision;
import org.legend.framework.ai.alibaba.sandbox.skills.manifest.ToolPolicy;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;
import java.util.Map;

/**
 * 门控工具回调包装器，在工具调用前执行安全检查。
 *
 * <p>该类是 Skill 运行时安全门控层核心组件，实现了 Spring AI 的 {@link ToolCallback} 接口，
 * 在委托给实际工具实现之前，执行以下安全检查：
 * <ol>
 *   <li><b>全局策略检查</b>：检查工具调用是否违反全局拒绝策略（由 {@link GlobalPolicy} 定义）</li>
 *   <li><b>Skill 权限检查</b>：检查工具调用是否在 Skill 的允许工具列表中（由 {@link GuardedSkillMetadata#getToolPolicies()} 定义）</li>
 *   <li><b>审计日志记录</b>：记录工具调用的允许/拒绝事件和性能指标（通过 {@link AuditLog} 记录）</li>
 * </ol>
 *
 * <p>支持所有 Spring AI ToolCallback 实现类型：
 * <ul>
 *   <li>{@code FunctionToolCallback} - 基于函数式接口的工具回调</li>
 *   <li>{@code MethodToolCallback} - 基于反射方法调用的工具回调</li>
 *   <li>{@code AsyncToolCallbackAdapter} - 异步工具适配器（已实现同步 call）</li>
 *   <li>{@code CancellableAsyncToolCallback} - 可取消的异步工具回调</li>
 * </ul>
 *
 * <p>安全检查流程：
 * <pre>
 * 工具调用请求
 *     ↓
 * 解析 JSON 参数 (parseArgs)
 *     ↓
 * 全局策略检查 (GlobalPolicy.check)
 *     ↓ 拒绝 → 记录审计日志 → 抛出 ToolDeniedException
 *     ↓ 允许
 * Skill 权限检查 (GuardedSkillMetadata.getToolPolicies)
 *     ↓ 拒绝 → 记录审计日志 → 抛出 ToolDeniedException
 *     ↓ 允许
 * 记录允许审计日志 (AuditLog.recordAllowed)
 *     ↓
 * 执行实际工具调用 (delegate.call)
 *     ↓
 * 完成审计日志 (AuditLog.finishSpan)
 * </pre>
 *
 * <p>使用示例：
 * <pre>{@code
 * GuardedSkillMetadata skillMetadata = ...;
 * ToolCallback rawTool = ReadTool.create(backend);
 * GuardedToolCallback guarded = new GuardedToolCallback(rawTool, skillMetadata);
 * String result = guarded.call("{\"file_path\": \"/path/to/file.txt\"}");
 * }</pre>
 *
 * <p>线程安全说明：
 * <ul>
 *   <li>该类本身是线程安全的，所有字段都是 final 的</li>
 *   <li>每个 GuardedToolCallback 实例绑定到特定的 GuardedSkillMetadata</li>
 *   <li>多个线程可以同时使用同一个 GuardedToolCallback 实例</li>
 * </ul>
 *
 * @see ToolCallback Spring AI 工具回调接口
 * @see GuardedSkillMetadata 带安全门控的 Skill 元数据
 * @see GlobalPolicy 全局策略检查器
 * @see AuditLog 审计日志记录器
 * @see ToolDeniedException 工具拒绝异常
 */
public class GuardedToolCallback implements ToolCallback {

    /**
     * 委托的实际工具回调实例。
     */
    private final ToolCallback delegate;

    /**
     * 带安全门控的 Skill 元数据，用于获取当前 Skill 的允许工具列表。
     */
    private final GuardedSkillMetadata skillMetadata;

    /**
     * JSON 对象映射器，用于解析工具调用的 JSON 参数。
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 创建门控工具回调实例。
     *
     * @param delegate 委托的实际工具回调实例，不能为 null
     * @param skillMetadata 带安全门控的 Skill 元数据，不能为 null
     */
    public GuardedToolCallback(ToolCallback delegate, GuardedSkillMetadata skillMetadata) {
        this.delegate = delegate;
        this.skillMetadata = skillMetadata;
    }

    /**
     * 获取工具定义。
     *
     * <p>该方法委托给实际工具回调的 {@link ToolCallback#getToolDefinition()} 方法，
     * 返回工具的名称、描述和参数 schema 等信息。
     *
     * @return 工具定义实例
     */
    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    /**
     * 获取工具元数据。
     *
     * <p>该方法委托给实际工具回调的 {@link ToolCallback#getToolMetadata()} 方法，
     * 返回工具的元数据信息，如是否支持并发、是否可取消等。
     *
     * @return 工具元数据实例
     */
    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    /**
     * 执行工具调用（无 ToolContext 版本）。
     *
     * <p>该方法首先调用 {@link #checkSecurity(String)} 执行安全检查，
     * 如果检查通过，则委托给实际工具回调执行工具调用。
     *
     * @param toolInput 工具调用的 JSON 参数字符串
     * @return 工具调用的结果字符串
     * @throws ToolDeniedException 如果安全检查失败
     */
    @Override
    public String call(String toolInput) {
        // 执行安全检查
        checkSecurity(toolInput);
        // 委托给实际工具回调执行工具调用
        return delegate.call(toolInput);
    }

    /**
     * 执行工具调用（带 ToolContext 版本）。
     *
     * <p>该方法首先调用 {@link #checkSecurity(String)} 执行安全检查，
     * 如果检查通过，则委托给实际工具回调执行工具调用。
     *
     * @param toolInput 工具调用的 JSON 参数字符串
     * @param toolContext Spring AI 的工具上下文，包含聊天模型、消息历史等信息
     * @return 工具调用的结果字符串
     * @throws ToolDeniedException 如果安全检查失败
     */
    @Override
    public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
        // 执行安全检查
        checkSecurity(toolInput);
        // 委托给实际工具回调执行工具调用
        return delegate.call(toolInput, toolContext);
    }

    /**
     * 执行安全检查，包括全局策略检查和 Skill 权限检查。
     *
     * <p>该方法执行以下检查步骤：
     * <ol>
     *   <li>解析 JSON 参数为 Map 对象</li>
     *   <li>获取当前 Skill 元数据</li>
     *   <li>执行全局策略检查（调用 {@link GlobalPolicy#check(String, Map)}）</li>
     *   <li>如果全局策略拒绝，记录审计日志并抛出异常</li>
     *   <li>执行 Skill 权限检查（遍历 {@link GuardedSkillMetadata#getToolPolicies()}）</li>
     *   <li>如果 Skill 权限拒绝，记录审计日志并抛出异常</li>
     *   <li>记录允许审计日志，创建 OpenTelemetry Span</li>
     *   <li>完成审计日志，记录工具调用耗时</li>
     * </ol>
     *
     * <p>检查失败时，该方法会：
     * <ul>
     *   <li>调用 {@link AuditLog#recordDenied} 记录拒绝事件</li>
     *   <li>抛出 {@link ToolDeniedException} 中断工具调用流程</li>
     * </ul>
     *
     * <p>检查通过时，该方法会：
     * <ul>
     *   <li>调用 {@link AuditLog#recordAllowed} 创建 OpenTelemetry Span</li>
     *   <li>在 finally 块中调用 {@link AuditLog#finishSpan} 完成审计日志</li>
     * </ul>
     *
     * @param toolInput 工具调用的 JSON 参数字符串（如 "{\"file_path\": \"/path/to/file.txt\"}"）
     * @throws ToolDeniedException 如果工具调用被全局策略或 Skill 权限拒绝
     */
    protected void checkSecurity(String toolInput) {
        String tool = delegate.getToolDefinition().name();
        Map<String, Object> args = parseArgs(toolInput);
        String skillName = skillMetadata == null ? "<none>" : skillMetadata.getName();

        PolicyDecision blocked = GlobalPolicy.check(tool, args);
        if (blocked.denied()) {
            AuditLog.recordDenied(skillName, tool, args, "global:" + blocked.reason());
            throw new ToolDeniedException("blocked by global policy: " + blocked.reason());
        }

        if (skillMetadata != null) {
            List<ToolPolicy> policies = skillMetadata.getToolPolicies();
            if (policies != null && !policies.isEmpty()) {
                boolean allowed = policies.stream().anyMatch(p -> p.matches(tool, args));
                if (!allowed) {
                    AuditLog.recordDenied(skillName, tool, args, "not-in-allowed-tools");
                    throw new ToolDeniedException("Skill '%s' not authorized for tool '%s'".formatted(skillName, tool));
                }
            }
        }

        Span span = AuditLog.recordAllowed(skillName, tool, args);
        long t0 = System.currentTimeMillis();
        boolean ok = false;
        try (var scope = span.makeCurrent()) {
            ok = true;
        } finally {
            AuditLog.finishSpan(span, skillName, tool, System.currentTimeMillis() - t0, ok);
        }
    }

    /**
     * 解析工具调用的 JSON 参数字符串为 Map 对象。
     *
     * <p>该方法使用 Jackson ObjectMapper 将 JSON 字符串解析为 Map 对象。
     * 如果解析失败（如 JSON 格式错误），则返回包含原始字符串的 Map，
     * 键为 "__raw"，值为原始 JSON 字符串。
     *
     * <p>解析示例：
     * <ul>
     *   <li>输入：{@code "{\"file_path\": \"/path/to/file.txt\"}"}</li>
     *   <li>输出：{@code Map.of("file_path", "/path/to/file.txt")}</li>
     *   <li>解析失败：{@code Map.of("__raw", "invalid json")}</li>
     * </ul>
     *
     * @param json 工具调用的 JSON 参数字符串
     * @return 解析后的参数 Map，解析失败时包含原始字符串
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArgs(String json) {
        try {
            // 尝试解析 JSON 字符串为 Map 对象
            return mapper.readValue(json, Map.class);
        } catch (Exception e) {
            // 解析失败，返回包含原始字符串的 Map
            return Map.of("__raw", json);
        }
    }
}
