package org.legend.framework.ai.alibaba.sandbox.agent.audit;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import org.legend.framework.ai.alibaba.sandbox.GlobalPolicy;
import org.legend.framework.ai.alibaba.sandbox.tool.GuardedToolCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 审计日志记录器，集成 OpenTelemetry 进行工具调用的监控和追踪。
 *
 * <p>该类是 Skill 安全门控框架的可观测性核心组件，负责记录工具调用的审计日志，
 * 并提供以下功能：
 * <ul>
 *   <li><b>指标收集</b> - 记录工具调用的允许/拒绝次数（通过 {@link LongCounter}）</li>
 *   <li><b>延迟统计</b> - 记录工具调用的执行延迟分布（通过 {@link LongHistogram}）</li>
 *   <li><b>分布式追踪</b> - 创建 OpenTelemetry Span 用于工具调用的追踪（通过 {@link Span}）</li>
 *   <li><b>日志记录</b> - 使用 SLF4J 记录工具调用的详细信息</li>
 * </ul>
 *
 * <p>OpenTelemetry 集成：
 * <ul>
 *   <li><b>Tracer</b> - 创建名为 "skill.tool.call" 的 Span，记录工具调用的详细信息</li>
 *   <li><b>Meter</b> - 创建三个指标：
 *     <ul>
 *       <li>{@code skill.tool.allowed} - 工具调用允许次数计数器</li>
 *       <li>{@code skill.tool.denied} - 工具调用拒绝次数计数器</li>
 *       <li>{@code skill.tool.latency_ms} - 工具调用延迟直方图</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>日志格式：
 * <ul>
 *   <li>允许日志：{@code ALLOW skill=xxx tool=xxx args=xxx}</li>
 *   <li>拒绝日志：{@code DENY skill=xxx tool=xxx reason=xxx args=xxx}</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 记录工具调用允许
 * Span span = AuditLog.recordAllowed("my-skill", "Read", Map.of("file_path", "/path/to/file.txt"));
 * try (var scope = span.makeCurrent()) {
 *     // 执行工具调用
 *     String result = tool.call(args);
 *     // 完成审计日志
 *     AuditLog.finishSpan(span, "my-skill", "Read", elapsedMs, true);
 * }
 *
 * // 记录工具调用拒绝
 * AuditLog.recordDenied("my-skill", "Bash", Map.of("command", "rm -rf /"), "global:Bash(command:rm -rf /)");
 * }</pre>
 *
 * @see GuardedToolCallback 门控工具回调，使用本类记录审计日志
 * @see GlobalPolicy 全局策略检查器，使用本类记录拒绝事件
 */
public final class AuditLog {

    /**
     * SLF4J 日志记录器，用于记录工具调用的审计日志。
     *
     * <p>该日志器使用名称 "skill.audit"，可以在日志配置中单独配置该日志器的级别和输出目标。
     */
    private static final Logger LOG = LoggerFactory.getLogger("skill.audit");

    /**
     * OpenTelemetry Tracer，用于创建工具调用的追踪 Span。
     *
     * <p>该 Tracer 使用名称 "org.legend.framework.ai.alibaba.skill" 和版本 "1.0.0"，
     * 用于创建名为 "skill.tool.call" 的 Span。
     */
    private static final Tracer TRACER =
        GlobalOpenTelemetry.getTracer("org.legend.framework.ai.alibaba.skill", "1.0.0");

    /**
     * 工具调用允许次数计数器。
     *
     * <p>该计数器使用名称 "skill.tool.allowed"，在每次工具调用被允许时递增。
     * 计数器包含以下属性：
     * <ul>
     *   <li>{@code skill} - Skill 名称</li>
     *   <li>{@code tool} - 工具名称</li>
     * </ul>
     */
    private static final LongCounter ALLOWED =
        GlobalOpenTelemetry.getMeter("org.legend.framework.ai.alibaba.skill")
            // 创建计数器构建器
            .counterBuilder("skill.tool.allowed")
            // 设置计数器描述
            .setDescription("Tool calls allowed by gate")
            // 构建计数器
            .build();

    /**
     * 工具调用拒绝次数计数器。
     *
     * <p>该计数器使用名称 "skill.tool.denied"，在每次工具调用被拒绝时递增。
     * 计数器包含以下属性：
     * <ul>
     *   <li>{@code skill} - Skill 名称</li>
     *   <li>{@code tool} - 工具名称</li>
     *   <li>{@code reason} - 拒绝原因</li>
     * </ul>
     */
    private static final LongCounter DENIED =
        GlobalOpenTelemetry.getMeter("org.legend.framework.ai.alibaba.skill")
            // 创建计数器构建器
            .counterBuilder("skill.tool.denied")
            // 设置计数器描述
            .setDescription("Tool calls denied by gate")
            // 构建计数器
            .build();

    /**
     * 工具调用延迟直方图。
     *
     * <p>该直方图使用名称 "skill.tool.latency_ms"，记录工具调用的执行延迟分布。
     * 直方图包含以下属性：
     * <ul>
     *   <li>{@code skill} - Skill 名称</li>
     *   <li>{@code tool} - 工具名称</li>
     *   <li>{@code ok} - 工具调用是否成功（"true" 或 "false"）</li>
     * </ul>
     */
    private static final LongHistogram LATENCY =
        GlobalOpenTelemetry.getMeter("org.legend.framework.ai.alibaba.skill")
            // 创建直方图构建器
            .histogramBuilder("skill.tool.latency_ms")
            // 设置单位为毫秒
            .setUnit("ms")
            // 构建长整型直方图
            .ofLongs().build();

    /**
     * OpenTelemetry 属性键：Skill 名称。
     *
     * <p>该属性键用于在指标和 Span 中记录 Skill 名称。
     */
    private static final AttributeKey<String> SKILL = AttributeKey.stringKey("skill");

    /**
     * OpenTelemetry 属性键：工具名称。
     *
     * <p>该属性键用于在指标和 Span 中记录工具名称。
     */
    private static final AttributeKey<String> TOOL  = AttributeKey.stringKey("tool");

    /**
     * OpenTelemetry 属性键：拒绝原因。
     *
     * <p>该属性键用于在拒绝指标中记录拒绝原因。
     */
    private static final AttributeKey<String> REASON = AttributeKey.stringKey("reason");

    /**
     * 私有构造函数，防止实例化。
     *
     * <p>该类仅提供静态工具方法，不需要创建实例。
     */
    private AuditLog() {}

    /**
     * 记录工具调用允许事件。
     *
     * <p>该方法在工具调用通过安全检查后调用，执行以下操作：
     * <ol>
     *   <li>创建 OpenTelemetry 属性（包含 Skill 名称和工具名称）</li>
     *   <li>递增允许次数计数器</li>
     *   <li>记录允许日志（包含 Skill 名称、工具名称和参数摘要）</li>
     *   <li>创建 OpenTelemetry Span（包含属性 "args.summary"）</li>
     *   <li>启动 Span 并返回</li>
     * </ol>
     *
     * <p>返回的 Span 应该在工具调用完成后通过 {@link #finishSpan} 方法结束。
     *
     * @param skill Skill 名称
     * @param tool 工具名称
     * @param args 工具调用的参数映射
     * @return 启动的 OpenTelemetry Span
     */
    public static Span recordAllowed(String skill, String tool, Map<String, Object> args) {
        // 创建 OpenTelemetry 属性
        Attributes attrs = Attributes.of(SKILL, skill, TOOL, tool);
        // 递增允许次数计数器
        ALLOWED.add(1, attrs);
        // 记录允许日志
        LOG.info("ALLOW skill={} tool={} args={}", skill, tool, summarize(args));
        // 创建 OpenTelemetry Span
        return TRACER.spanBuilder("skill.tool.call")
            // 设置所有属性
            .setAllAttributes(attrs)
            // 设置参数摘要属性
            .setAttribute("args.summary", summarize(args))
            // 启动 Span
            .startSpan();
    }

    /**
     * 记录工具调用拒绝事件。
     *
     * <p>该方法在工具调用被安全检查拒绝后调用，执行以下操作：
     * <ol>
     *   <li>创建 OpenTelemetry 属性（包含 Skill 名称、工具名称和拒绝原因）</li>
     *   <li>递增拒绝次数计数器</li>
     *   <li>记录拒绝日志（包含 Skill 名称、工具名称、拒绝原因和参数摘要）</li>
     * </ol>
     *
     * <p>该方法不会创建 Span，因为拒绝的工具调用不需要追踪。
     *
     * @param skill Skill 名称
     * @param tool 工具名称
     * @param args 工具调用的参数映射
     * @param reason 拒绝原因（如 "global:Bash(command:rm -rf /)" 或 "not-in-allowed-tools"）
     */
    public static void recordDenied(String skill, String tool, Map<String, Object> args, String reason) {
        // 创建 OpenTelemetry 属性
        Attributes attrs = Attributes.of(SKILL, skill, TOOL, tool, REASON, reason);
        // 递增拒绝次数计数器
        DENIED.add(1, attrs);
        // 记录拒绝日志
        LOG.warn("DENY skill={} tool={} reason={} args={}", skill, tool, reason, summarize(args));
    }

    /**
     * 完成 OpenTelemetry Span 并记录延迟指标。
     *
     * <p>该方法在工具调用完成后调用，执行以下操作：
     * <ol>
     *   <li>记录延迟指标（包含 Skill 名称、工具名称和执行状态）</li>
     *   <li>设置 Span 的 "elapsed_ms" 属性（工具调用耗时）</li>
     *   <li>设置 Span 的 "ok" 属性（工具调用是否成功）</li>
     *   <li>结束 Span</li>
     * </ol>
     *
     * <p>该方法应该在 try-finally 块的 finally 部分调用，确保 Span 总是被正确结束。
     *
     * @param span 要完成的 OpenTelemetry Span
     * @param skill Skill 名称
     * @param tool 工具名称
     * @param elapsedMs 工具调用耗时（毫秒）
     * @param ok 工具调用是否成功（true 表示成功，false 表示失败）
     */
    public static void finishSpan(Span span, String skill, String tool, long elapsedMs, boolean ok) {
        // 记录延迟指标
        LATENCY.record(elapsedMs, Attributes.of(
            SKILL, skill, TOOL, tool,
            AttributeKey.stringKey("ok"), String.valueOf(ok)));
        // 设置 Span 的耗时属性
        span.setAttribute("elapsed_ms", elapsedMs);
        // 设置 Span 的成功状态属性
        span.setAttribute("ok", ok);
        // 结束 Span
        span.end();
    }

    /**
     * 生成参数映射的摘要字符串。
     *
     * <p>该方法将参数映射转换为字符串，并限制最大长度为 500 字符。
     * 如果字符串超过 500 字符，则截断为 497 字符并添加 "..."。
     *
     * <p>该方法用于日志记录和 Span 属性，避免记录过长的参数信息。
     *
     * @param args 参数映射
     * @return 参数摘要字符串（最大 500 字符）
     */
    private static String summarize(Map<String, Object> args) {
        // 将参数映射转换为字符串
        String s = String.valueOf(args);
        // 如果字符串长度不超过 500 字符，直接返回
        return s.length() <= 500 ? s : s.substring(0, 497) + "...";
    }
}
