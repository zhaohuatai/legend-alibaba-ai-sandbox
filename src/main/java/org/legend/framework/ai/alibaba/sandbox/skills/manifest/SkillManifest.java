package org.legend.framework.ai.alibaba.sandbox.skills.manifest;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.legend.framework.ai.alibaba.sandbox.skills.enums.RiskLevel;

/**
 * Skill 元数据记录类，封装单个 Skill 的完整描述信息和安全策略。
 *
 * <p>该类是 Claude Code 运行时契约的元数据层核心组件，从 SKILL.md 文件的 YAML frontmatter 中解析而来。
 * 它定义了 Skill 的名称、描述、版本、风险等级、允许使用的工具列表、网络访问权限等关键信息。
 *
 * <p>核心设计理念：
 * <ul>
 *   <li><b>不可变性</b> - 作为 record 类型，实例创建后不可修改，确保线程安全</li>
 *   <li><b>完整描述</b> - 封装 Skill 的所有元数据，包括安全策略和执行环境信息</li>
 *   <li><b>风险驱动</b> - 通过 {@link #riskLevel} 决定沙箱隔离级别和执行环境</li>
 * </ul>
 *
 * <p>Skill Manifest 结构（SKILL.md 文件）：
 * <pre>
 * ---
 * name: my-skill
 * description: A skill that does something useful
 * version: 1.0.0
 * risk-level: low
 * network: false
 * allowed-tools:
 *   - Read
 *   - Bash(command:python3 *)
 * ---
 * Skill body content here...
 * </pre>
 *
 * <p>风险等级（RiskLevel）：
 * <ul>
 *   <li><b>LOW</b> - 低风险 Skill，仅使用只读工具（如 Read、Grep、Glob）</li>
 *   <li><b>MEDIUM</b> - 中等风险 Skill，使用读写工具（如 Write、Edit）</li>
 *   <li><b>HIGH</b> - 高风险 Skill，使用命令执行工具（如 Bash）或网络访问</li>
 * </ul>
 *
 * <p>风险等级决定了 Skill 执行时使用的沙箱后端：
 * <ul>
 *   <li>LOW - 本地进程沙箱（{@link org.legend.framework.ai.alibaba.sandbox.backend.local.LocalProcessSandbox}）</li>
 *   <li>MEDIUM - 本地进程沙箱或 Docker 容器（根据配置）</li>
 *   <li>HIGH - Docker 容器沙箱（{@link org.legend.framework.ai.alibaba.skill.guard.sandbox.docker.backup.DockerSession}）</li>
 * </ul>
 *
 * <p>使用场景：
 * <ul>
 *   <li>{@link org.legend.framework.ai.alibaba.skill.guard.SkillContext} - 存储当前 Skill 的元数据</li>
 *   <li>{@link org.legend.framework.ai.alibaba.sandbox.tool.GuardedToolCallback} - 使用 {@link #allowedTools()} 进行权限检查</li>
 *   <li>{@link org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackendProvider} - 使用 {@link #riskLevel()} 选择沙箱后端</li>
 * </ul>
 *
 * @param name Skill 名称，唯一标识一个 Skill
 * @param description Skill 描述，用于向用户和 LLM 说明 Skill 的功能
 * @param version Skill 版本号，遵循语义化版本规范
 * @param riskLevel 风险等级，决定沙箱隔离级别
 * @param networkAllowed 是否允许网络访问，true 表示允许 HTTP/HTTPS 请求
 * @param allowedTools 允许使用的工具策略列表，定义 Skill 可以调用哪些工具及其参数约束
 * @param body Skill 的正文内容，通常是从 SKILL.md 的 YAML frontmatter 之后到文件末尾的文本
 * @param skillDir Skill 目录的绝对路径，包含 SKILL.md 和其他相关文件
 * @param raw 原始 YAML 元数据的键值对映射，保留所有未解析的字段
 * @see SkillManifestParser Skill 元数据解析器，从 SKILL.md 文件解析本类
 * @see ToolPolicy 工具策略，定义 Skill 可以使用的工具及其约束
 * @see RiskLevel 风险等级枚举，定义 Skill 的风险级别
 */
public record SkillManifest(
    /**
     * Skill 名称。
     *
     * <p>该字段唯一标识一个 Skill，通常使用小写字母和连字符（如 "pdf-extractor"、"code-reviewer"）。
     * 名称用于：
     * <ul>
     *   <li>在审计日志中标识 Skill（通过 {@link org.legend.framework.ai.alibaba.skill.guard.AuditLog}）</li>
     *   <li>在错误消息中显示 Skill 名称</li>
     *   <li>在 Skill 注册表中查找 Skill</li>
     * </ul>
     */
    String name,

    /**
     * Skill 描述。
     *
     * <p>该字段用于向用户和 LLM 说明 Skill 的功能和用途。
     * 描述通常是一句话，简洁明了地说明 Skill 的主要功能。
     *
     * <p>该描述在以下场景中使用：
     * <ul>
     *   <li>Agent 选择 Skill 时的参考信息</li>
     *   <li>用户界面中显示 Skill 列表</li>
     *   <li>调试和日志中说明 Skill 功能</li>
     * </ul>
     */
    String description,

    /**
     * Skill 版本号。
     *
     * <p>该字段遵循语义化版本规范（SemVer），格式为 "主版本号.次版本号.修订号"（如 "1.0.0"、"2.1.3"）。
     * 版本号用于：
     * <ul>
     *   <li>跟踪 Skill 的更新和变更</li>
     *   <li>在日志中记录 Skill 版本</li>
     *   <li>版本兼容性检查</li>
     * </ul>
     */
    String version,

    /**
     * 风险等级。
     *
     * <p>该字段决定 Skill 执行时使用的沙箱隔离级别：
     * <ul>
     *   <li>{@link RiskLevel#LOW} - 低风险，使用本地进程沙箱</li>
     *   <li>{@link RiskLevel#MEDIUM} - 中等风险，使用本地进程沙箱或 Docker 容器</li>
     *   <li>{@link RiskLevel#HIGH} - 高风险，使用 Docker 容器沙箱</li>
     * </ul>
     *
     * <p>风险等级由 {@link SandboxBackendProvider} 使用，用于选择合适的沙箱后端。
     *
     * @see RiskLevel 风险等级枚举
     * @see org.legend.framework.ai.alibaba.skill.guard.sandbox.SandboxBackendProvider 沙箱后端提供者
     */
    RiskLevel riskLevel,

    /**
     * 是否允许网络访问。
     *
     * <p>该字段决定 Skill 是否可以发起 HTTP/HTTPS 请求：
     * <ul>
     *   <li>{@code true} - 允许网络访问，Skill 可以调用外部 API</li>
     *   <li>{@code false} - 网络隔离，Skill 无法访问外部网络</li>
     * </ul>
     *
     * <p>网络访问权限通常与风险等级相关：
     * <ul>
     *   <li>LOW - 通常不允许网络访问</li>
     *   <li>MEDIUM - 可能允许网络访问</li>
     *   <li>HIGH - 可能允许网络访问</li>
     * </ul>
     */
    boolean networkAllowed,

    /**
     * 允许使用的工具策略列表。
     *
     * <p>该字段定义 Skill 可以调用哪些工具及其参数约束。
     * 列表中的每个 {@link ToolPolicy} 实例表示一个允许的工具及其约束条件。
     *
     * <p>权限检查流程：
     * <ol>
     *   <li>在 {@link org.legend.framework.ai.alibaba.skill.guard.GuardedToolCallback#checkSecurity(String)} 中获取本列表</li>
     *   <li>遍历列表，调用 {@link ToolPolicy#matches(String, Map)} 检查工具调用是否匹配</li>
     *   <li>如果匹配任何策略，允许工具调用</li>
     *   <li>如果不匹配任何策略，拒绝工具调用</li>
     * </ol>
     *
     * <p>示例：
     * <ul>
     *   <li>{@code [ToolPolicy("Read", [])]} - 仅允许 Read 工具，无约束</li>
     *   <li>{@code [ToolPolicy("Bash", [ArgConstraint("command", Pattern.compile("^python3 .*.*$"))])]} - 仅允许 Bash 工具执行 python3 命令</li>
     * </ul>
     *
     * @see ToolPolicy 工具策略
     * @see org.legend.framework.ai.alibaba.skill.guard.GuardedToolCallback 门控工具回调
     */
    List<ToolPolicy> allowedTools,

    /**
     * Skill 的正文内容。
     *
     * <p>该字段包含 SKILL.md 文件中 YAML frontmatter 之后到文件末尾的文本。
     * 正文内容通常包含：
     * <ul>
     *   <li>Skill 的使用说明</li>
     *   <li>示例代码</li>
     *   <li>注意事项</li>
     * </ul>
     *
     * <p>正文内容在以下场景中使用：
     * <ul>
     *   <li>LLM 理解 Skill 的使用方式</li>
     *   <li>用户查看 Skill 文档</li>
     *   <li>调试时查看 Skill 完整内容</li>
     * </ul>
     */
    String body,

    /**
     * Skill 目录的绝对路径。
     *
     * <p>该字段指向包含 SKILL.md 文件的目录，通常还包含其他相关文件：
     * <ul>
     *   <li>SKILL.md - Skill 的定义文件</li>
     *   <li>scripts/ - Skill 使用的脚本文件</li>
     *   <li>templates/ - Skill 使用的模板文件</li>
     *   <li>assets/ - Skill 使用的资源文件</li>
     * </ul>
     *
     * <p>该路径在以下场景中使用：
     * <ul>
     *   <li>加载 Skill 相关的资源文件</li>
     *   <li>执行 Skill 目录下的脚本</li>
     *   <li>调试时查看 Skill 文件结构</li>
     * </ul>
     */
    Path skillDir,

    /**
     * 原始 YAML 元数据的键值对映射。
     *
     * <p>该字段保留所有未解析的 YAML frontmatter 字段，用于：
     * <ul>
     *   <li>扩展性：支持未来新增的元数据字段</li>
     *   <li>调试：查看原始 YAML 数据</li>
     *   <li>兼容性：保留未知字段，避免解析失败</li>
     * </ul>
     *
     * <p>该映射包含所有 YAML frontmatter 中的键值对，包括已解析的字段（如 name、description 等）
     * 和未解析的字段（如自定义字段）。
     */
    Map<String, Object> raw
) {

}
