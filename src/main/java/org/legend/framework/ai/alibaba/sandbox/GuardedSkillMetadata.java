package org.legend.framework.ai.alibaba.sandbox;

import com.alibaba.cloud.ai.graph.skills.SkillMetadata;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.legend.framework.ai.alibaba.sandbox.skills.enums.RiskLevel;
import org.legend.framework.ai.alibaba.sandbox.skills.manifest.ToolPolicy;

/**
 * 带安全门控的 Skill 元数据，扩展 Spring AI 的 SkillMetadata。
 * 
 * <p>该类复制了 SkillManifest 的所有安全相关字段，不再依赖 SkillManifest 实例：
 * <ul>
 *   <li>version - 版本号</li>
 *   <li>riskLevel - 风险等级，决定沙箱隔离级别</li>
 *   <li>networkAllowed - 是否允许网络访问</li>
 *   <li>toolPolicies - 工具策略列表，定义允许的工具及约束</li>
 *   <li>body - Skill 正文内容</li>
 *   <li>skillDir - Skill 目录路径</li>
 *   <li>raw - 原始 YAML 元数据映射</li>
 * </ul>
 * 
 * <p>继承关系：
 * <pre>
 * SkillMetadata (Spring AI)
 *   └── GuardedSkillMetadata (我们的扩展)
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
 *   <li>LOW - 本地进程沙箱</li>
 *   <li>MEDIUM - 本地进程沙箱或 Docker 容器（根据配置）</li>
 *   <li>HIGH - Docker 容器沙箱</li>
 * </ul>
 * 
 * <p>使用场景：
 * <ul>
 *   <li>GuardedSkillRegistry - 存储解析后的 Skill 元数据</li>
 *   <li>GuardedToolCallback - 使用 toolPolicies 进行权限检查</li>
 *   <li>SandboxBackendProvider - 使用 riskLevel 选择沙箱后端</li>
 * </ul>
 */
public class GuardedSkillMetadata extends SkillMetadata {

    /**
     * 版本号。
     * 
     * <p>该字段遵循语义化版本规范（SemVer），格式为 "主版本号.次版本号.修订号"（如 "1.0.0"、"2.1.3"）。
     * 版本号用于：
     * <ul>
     *   <li>跟踪 Skill 的更新和变更</li>
     *   <li>在日志中记录 Skill 版本</li>
     *   <li>版本兼容性检查</li>
     * </ul>
     */
    protected String version;

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
     * <p>风险等级由 SandboxBackendProvider 使用，用于选择合适的沙箱后端。
     */
    protected RiskLevel riskLevel;

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
    protected boolean networkAllowed;

    /**
     * 工具策略列表。
     * 
     * <p>该字段定义 Skill 可以调用哪些工具及其参数约束。
     * 列表中的每个 {@link ToolPolicy} 实例表示一个允许的工具及其约束条件。
     * 
     * <p>权限检查流程：
     * <ol>
     *   <li>在 GuardedToolCallback.checkSecurity() 中获取本列表</li>
     *   <li>遍历列表，调用 ToolPolicy.matches() 检查工具调用是否匹配</li>
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
     * <p>注意：该字段命名为 toolPolicies 而非 allowedTools，
     * 以避免与 Spring AI 框架未来版本中 SkillMetadata.allowedTools（List&lt;String&gt;）产生命名冲突。
     */
    protected List<ToolPolicy> toolPolicies;
   
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
    protected String body;

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
    protected Path skillDir;

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
    protected Map<String, Object> raw;

    public GuardedSkillMetadata() {
        super();
    }

    /**
     * 创建 GuardedSkillMetadata 实例。
     */
    public static GuardedSkillMetadata create(
            String name,
            String description,
            Path skillDir,
            String version,
            RiskLevel riskLevel,
            boolean networkAllowed,
            List<ToolPolicy> toolPolicies,
            String body,
            Map<String, Object> raw) {
        GuardedSkillMetadata metadata = new GuardedSkillMetadata();
        metadata.setName(name);
        metadata.setDescription(description);
        metadata.setSkillPath(skillDir.toString());
        metadata.setSource("file");
        metadata.version = version;
        metadata.riskLevel = riskLevel;
        metadata.networkAllowed = networkAllowed;
        metadata.toolPolicies = toolPolicies;
        metadata.body = body;
        metadata.skillDir = skillDir;
        metadata.raw = raw;
        return metadata;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public RiskLevel getRiskLevel() {
        return riskLevel;
    }

    public void setRiskLevel(RiskLevel riskLevel) {
        this.riskLevel = riskLevel;
    }

    public boolean isNetworkAllowed() {
        return networkAllowed;
    }

    public void setNetworkAllowed(boolean networkAllowed) {
        this.networkAllowed = networkAllowed;
    }

    public List<ToolPolicy> getToolPolicies() {
        return toolPolicies;
    }

    public void setToolPolicies(List<ToolPolicy> toolPolicies) {
        this.toolPolicies = toolPolicies;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Path getSkillDir() {
        return skillDir;
    }

    public void setSkillDir(Path skillDir) {
        this.skillDir = skillDir;
    }

    public Map<String, Object> getRaw() {
        return raw;
    }

    public void setRaw(Map<String, Object> raw) {
        this.raw = raw;
    }

    @Override
    public String toString() {
        return "GuardedSkillMetadata{" +
                "name='" + getName() + '\'' +
                ", description='" + getDescription() + '\'' +
                ", skillPath='" + getSkillPath() + '\'' +
                ", version='" + version + '\'' +
                ", riskLevel=" + riskLevel +
                ", networkAllowed=" + networkAllowed +
                ", toolPolicies=" + toolPolicies +
                '}';
    }
}
