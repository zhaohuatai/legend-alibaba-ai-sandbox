package org.legend.framework.ai.alibaba.sandbox.skills.registry;

import com.alibaba.cloud.ai.graph.skills.SkillMetadata;
import com.alibaba.cloud.ai.graph.skills.registry.AbstractSkillRegistry;
import com.alibaba.cloud.ai.graph.skills.registry.filesystem.SkillScanner;

import org.legend.framework.ai.alibaba.sandbox.GuardedSkillMetadata;
import org.legend.framework.ai.alibaba.sandbox.SandboxConstants;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvContext;
import org.legend.framework.ai.alibaba.sandbox.skills.manifest.SkillManifest;
import org.legend.framework.ai.alibaba.sandbox.skills.manifest.SkillManifestParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 带安全门控的 Skill 注册表，继承 Spring AI 的 AbstractSkillRegistry。
 * 
 * <p>设计原则：
 * <ul>
 *   <li>只支持文件系统路径（用户自定义 Skill）</li>
 *   <li>不支持 classpath/JAR 内置 Skill（违背 Skill 可扩展理念）</li>
 *   <li>统一使用 SkillManifestParser 解析（提取安全字段）</li>
 * </ul>
 * 
 * <p>继承关系：
 * <pre>
 * SkillRegistry (接口)
 *   └── AbstractSkillRegistry (抽象基类，已实现 get/listAll/contains/search/disable 等)
 *         ├── FileSystemSkillRegistry (文件系统实现)
 *         ├── ClasspathSkillRegistry (classpath 实现)
 *         └── GuardedSkillRegistry (我们的安全门控实现)
 * </pre>
 * 
 * <p>职责：
 * <ol>
 *   <li>解析 SKILL.md 文件，提取 GuardedSkillMetadata</li>
 *   <li>管理 Skill 的注册、查询、搜索</li>
 *   <li>兼容 Spring AI 的 SkillsAgentHook 和 SkillsInterceptor</li>
 *   <li>复用 AbstractSkillRegistry 的通用方法（搜索、禁用、路径匹配等）</li>
 * </ol>
 * 
 * <p>使用示例：
 * <pre>{@code
 * GuardedSkillRegistry registry = GuardedSkillRegistry.builder()
 *     .skillsDirectory("C:\\Users\\xxx\\.claude\\skills", "user")
 *     .skillsDirectory("./skills", "project")
 *     .env(envContext)
 *     .build();
 * }</pre>
 */
public class GuardedSkillRegistry extends AbstractSkillRegistry {

    private static final Logger logger = LoggerFactory.getLogger(GuardedSkillRegistry.class);
    /**
     * 环境上下文，用于推断 allowed-tools。
     */
    private final EnvContext env;

    /**
     * Skill 目录列表（文件系统路径）。
     */
    private final List<String> skillsDirectories;

    /**
     * 系统提示模板。
     */
    private final SystemPromptTemplate systemPromptTemplate;

    /**
     * Skill 扫描器，复用框架的扫描逻辑。
     */
    private final SkillScanner scanner = new SkillScanner();

    /**
     * 带安全门控的 Skill 集合。
     * 父类的 skills 字段是 Map<String, SkillMetadata>，
     * 我们维护一个类型更精确的副本，方便直接访问 GuardedSkillMetadata。
     */
    private final Map<String, GuardedSkillMetadata> guardedSkills = new HashMap<>();

    /**
     * 私有构造函数，通过 Builder 创建。
     */
    private GuardedSkillRegistry(Builder builder) {
        this.env = builder.env;
        this.skillsDirectories = builder.skillsDirectories;

        if (builder.systemPromptTemplate != null) {
            this.systemPromptTemplate = builder.systemPromptTemplate;
        } else {
            this.systemPromptTemplate = SystemPromptTemplate.builder()
                .template(SandboxConstants.DEFAULT_SYSTEM_PROMPT_TEMPLATE)
                .build();
        }

        if (builder.autoLoad) {
            loadSkillsToRegistry();
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * 从配置的目录加载 Skill 到注册表。
     * 实现 AbstractSkillRegistry 的抽象方法。
     */
    @Override
    protected void loadSkillsToRegistry() {
        this.guardedSkills.clear();

        for (String dir : skillsDirectories) {
            Path skillsPath = Path.of(dir);
            if (!Files.exists(skillsPath)) {
                logger.info("Skills directory not found: {}", dir);
                continue;
            }

            try {
                List<SkillMetadata> scannedSkills = scanner.scan(dir, "user");
                for (SkillMetadata skill : scannedSkills) {
                    try {
                        Path skillDir = Path.of(skill.getSkillPath());
                        SkillManifest manifest = SkillManifestParser.parse(skillDir, env);
                        GuardedSkillMetadata metadata = GuardedSkillMetadata.create(
                            manifest.name(),
                            manifest.description(),
                            skillDir,
                            manifest.version(),
                            manifest.riskLevel(),
                            manifest.networkAllowed(),
                            manifest.allowedTools(),
                            manifest.body(),
                            manifest.raw()
                        );
                        this.guardedSkills.put(metadata.getName(), metadata);
                    } catch (Exception e) {
                        logger.error("Failed to enhance skill metadata: {}", skill.getName(), e);
                    }
                }
            } catch (Exception e) {
                logger.error("Failed to scan skills directory: {}", dir, e);
            }
        }

        this.skills = new HashMap<>(this.guardedSkills);

        logger.info("Loaded {} guarded skills from {} directories",
            this.guardedSkills.size(), skillsDirectories.size());
    }

    /**
     * 获取带安全门控的 Skill 元数据。
     * 这是我们的自定义方法，返回 GuardedSkillMetadata 而非 SkillMetadata。
     */
    public GuardedSkillMetadata getGuarded(String name) {
        return this.guardedSkills.get(name);
    }

    @Override
    public String readSkillContent(String name) throws IOException {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Skill name cannot be null or empty");
        }

        GuardedSkillMetadata skill = getGuarded(name);
        if (skill == null) {
            throw new IllegalStateException("Skill not found: " + name);
        }

        String body = skill.getBody();
        if (body != null && !body.isEmpty()) {
            return body;
        }

        return SkillManifestParser.loadSkillBody(skill.getSkillDir());
    }

    @Override
    public String getSkillLoadInstructions() {
        StringBuilder instructions = new StringBuilder();
        instructions.append("**Skill Locations:**\n");
        for (String dir : skillsDirectories) {
            instructions.append(String.format("- `%s`\n", dir));
        }
        instructions.append("\n**Skill Path Format:**\n");
        instructions.append("Each skill has a unique path shown in the skill list above. ");
        instructions.append("Use the exact path shown when calling `read_skill`.\n");
        return instructions.toString();
    }

    @Override
    public String getRegistryType() {
        return "GuardedFileSystem";
    }

    @Override
    public SystemPromptTemplate getSystemPromptTemplate() {
        String skillsList = buildSkillsList();
        String os = env != null ? env.os().name() : "Unknown";
        String shell = env != null ? env.shellKind().name() : "Unknown";
        String executables = env != null && env.availableExecutables() != null 
            ? String.join(", ", env.availableExecutables()) 
            : "None";
        
        String renderedTemplate = SandboxConstants.FRAME_SYSTEM_PROMPT_WITH_ENV_TEMPLATE
            .replace("{skills_list}", skillsList)
            .replace("{os}", os)
            .replace("{shell}", shell)
            .replace("{executables}", executables);
        return SystemPromptTemplate.builder()
            .template(renderedTemplate)
            .build();
    }

    /**
     * 构建可用 skill 列表字符串，包含名称和描述。
     */
    private String buildSkillsList() {
        if (guardedSkills.isEmpty()) {
            return "No skills available.";
        }

        StringBuilder sb = new StringBuilder();
        for (GuardedSkillMetadata skill : guardedSkills.values()) {
            sb.append(String.format("- **%s**: %s\n", skill.getName(), skill.getDescription()));
        }
        return sb.toString();
    }

    /**
     * Builder for creating GuardedSkillRegistry instances.
     */
    public static class Builder {
        private final List<String> skillsDirectories = new ArrayList<>();
        private EnvContext env;
        private boolean autoLoad = true;
        private SystemPromptTemplate systemPromptTemplate;

        /**
         * 添加 Skill 目录（文件系统路径）。
         */
        public Builder skillsDirectory(String directory) {
            return skillsDirectory(directory, "user");
        }

        /**
         * 添加 Skill 目录（带来源标识）。
         */
        public Builder skillsDirectory(String directory, String source) {
            this.skillsDirectories.add(directory);
            return this;
        }

        public Builder env(EnvContext env) {
            this.env = env;
            return this;
        }

        public Builder autoLoad(boolean autoLoad) {
            this.autoLoad = autoLoad;
            return this;
        }

        public Builder systemPromptTemplate(SystemPromptTemplate template) {
            this.systemPromptTemplate = template;
            return this;
        }

        public GuardedSkillRegistry build() {
            return new GuardedSkillRegistry(this);
        }
    }
}
