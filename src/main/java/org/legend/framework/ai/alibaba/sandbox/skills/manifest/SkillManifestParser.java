package org.legend.framework.ai.alibaba.sandbox.skills.manifest;

import org.legend.framework.ai.alibaba.sandbox.skills.enums.RiskLevel;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvContext;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Skill Manifest 解析器，用于从 SKILL.md 文件解析 Skill 元数据。
 *
 * <p>该类是 Claude Code 运行时契约的元数据解析层核心组件，负责读取和解析 SKILL.md 文件，
 * 提取 YAML frontmatter 中的元数据，并构建 {@link SkillManifest} 实例。
 *
 * <p>核心设计理念：
 * <ul>
 *   <li><b>YAML Frontmatter 解析</b> - 从 SKILL.md 文件开头的 YAML 部分提取元数据</li>
 *   <li><b>必需字段验证</b> - 确保 name、description、risk-level 等必需字段存在</li>
 *   <li><b>环境推断</b> - 当 allowed-tools 为空时，根据环境中可用的可执行文件自动推断</li>
 * </ul>
 *
 * <p>SKILL.md 文件格式：
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
 * This content is passed to the LLM as the skill's instructions.
 * </pre>
 *
 * <p>解析步骤：
 * <ol>
 *   <li>读取 Skill 目录下的 SKILL.md 文件</li>
 *   <li>调用 {@link #splitFrontmatter(String)} 分离 YAML frontmatter 和正文</li>
 *   <li>使用 SnakeYAML 解析 YAML 部分为键值对映射</li>
 *   <li>提取必需字段（name、description、risk-level），如果缺失则抛出异常</li>
 *   <li>提取可选字段（version、network、allowed-tools）</li>
 *   <li>调用 {@link #parseAllowedTools(Object, EnvContext)} 解析工具策略列表</li>
 *   <li>构建并返回 {@link SkillManifest} 实例</li>
 * </ol>
 *
 * <p>使用示例：
 * <pre>{@code
 * Path skillDir = Paths.get("/path/to/skill-directory");
 * SkillManifest manifest = SkillManifestParser.parse(skillDir);
 * System.out.println("Skill name: " + manifest.name());
 * System.out.println("Risk level: " + manifest.riskLevel());
 * }</pre>
 *
 * @see SkillManifest Skill 元数据记录类
 * @see ToolPolicy 工具策略记录类
 * @see RiskLevel 风险等级枚举
 */
public final class SkillManifestParser {
	
    /**
     * YAML frontmatter 分隔符。
     *
     * <p>SKILL.md 文件以 --- 开头和结尾的 YAML frontmatter 部分包含元数据，
     * 之后的部分是 Skill 的正文内容。
     *
     * <p>分隔符格式：
     * <pre>
     * ---
     * YAML frontmatter
     * ---
     * Body content
     * </pre>
     */
    private static final String DELIM = "---";

    /**
     * 私有构造函数，防止实例化。
     *
     * <p>该类仅提供静态工具方法，不需要创建实例。
     */
    private SkillManifestParser() {}

    /**
     * 从 Skill 目录解析 SKILL.md 文件并返回 SkillManifest 实例。
     *
     * <p>该方法执行以下操作：
     * <ol>
     *   <li>读取 Skill 目录下的 SKILL.md 文件</li>
     *   <li>调用 {@link #splitFrontmatter(String)} 分离 YAML frontmatter 和正文</li>
     *   <li>使用 SnakeYAML 解析 YAML 部分为键值对映射</li>
     *   <li>提取必需字段（name、description、risk-level），如果缺失则抛出异常</li>
     *   <li>提取可选字段（version、network、allowed-tools）</li>
     *   <li>调用 {@link #parseAllowedTools(Object, EnvContext)} 解析工具策略列表</li>
     *   <li>构建并返回 {@link SkillManifest} 实例</li>
     * </ol>
     *
     * <p>字段说明：
     * <ul>
     *   <li><b>必需字段</b>：name、description、risk-level</li>
     *   <li><b>可选字段</b>：version（默认 "0.0.0"）、network（默认 false）、allowed-tools（默认空列表）</li>
     * </ul>
     *
     * <p>allowed-tools 解析：
     * <ul>
     *   <li>如果 allowed-tools 为空且传入了 EnvContext，则根据环境中可用的可执行文件自动推断</li>
     *   <li>推断规则见 {@link #inferAllowedToolsFromEnv(EnvContext)}</li>
     * </ul>
     *
     * @param skillDir Skill 目录的绝对路径，必须包含 SKILL.md 文件
     * @param env 环境上下文，用于推断 allowed-tools（可为 null）
     * @return 解析后的 SkillManifest 实例
     * @throws IOException 如果读取 SKILL.md 文件失败
     * @throws IllegalArgumentException 如果 SKILL.md 缺少必需字段（name、description 或 risk-level）
     */
    public static SkillManifest parse(Path skillDir, EnvContext env) throws IOException {
        // 构建 SKILL.md 文件路径
        Path md = skillDir.resolve("SKILL.md");
        // 读取文件内容
        String text = Files.readString(md);
        // 分离 YAML frontmatter 和正文内容
        Frontmatter fm = splitFrontmatter(text);

        // 使用 SnakeYAML 解析 YAML 部分为键值对映射
        Map<String, Object> meta = fm.yaml().isEmpty()
            ? Map.of()
            : new Yaml().load(fm.yaml());

        // 提取必需字段，如果缺失则抛出异常
        String name = required(meta, "name");
        String desc = required(meta, "description");
        // 提取可选字段，使用默认值
        String ver = (String) meta.getOrDefault("version", "0.0.0");

        // 解析风险等级（必须配置）
        Object riskObj = meta.get("risk-level");
        if (riskObj == null) {
            throw new IllegalArgumentException(
                "SKILL.md missing required field: risk-level. " +
                "Must be one of: low, medium, high. " +
                "Example: risk-level: low");
        }
        // 解析风险等级枚举
        RiskLevel risk = RiskLevel.parse(riskObj);
        // 解析网络访问权限（默认 false）
        boolean net = Boolean.TRUE.equals(meta.get("network"));
        // 解析工具策略列表（空列表时根据 EnvContext 自动推断）
        Object toolsObj;
        if (meta.containsKey("allowed_tools")) {
            toolsObj = meta.get("allowed_tools");
        } else if (meta.containsKey("allowed-tools")) {
            toolsObj = meta.get("allowed-tools");
        } else if (meta.containsKey("allowedTools")) {
            toolsObj = meta.get("allowedTools");
        }  else {
            toolsObj = null;
        }
        List<ToolPolicy> policies = parseAllowedTools(toolsObj, env);

        // 构建并返回 SkillManifest 实例
        return new SkillManifest(name, desc, ver, risk, net, policies, fm.body(), skillDir, meta);
    }

    /**
     * 从 Skill 目录解析 SKILL.md 文件并返回 SkillManifest 实例。
     *
     * <p>该方法不传入 EnvContext，allowed-tools 为空时不会自动推断。
     *
     * <p>该方法是一个便捷方法，内部调用 {@link #parse(Path, EnvContext)} 并传入 null 作为 env 参数。
     *
     * @param skillDir Skill 目录的绝对路径，必须包含 SKILL.md 文件
     * @return 解析后的 SkillManifest 实例
     * @throws IOException 如果读取 SKILL.md 文件失败
     * @throws IllegalArgumentException 如果 SKILL.md 缺少必需字段（name、description 或 risk-level）
     */
    public static SkillManifest parse(Path skillDir) throws IOException {
        return parse(skillDir, null);
    }

    /**
     * YAML frontmatter 和正文内容的记录类。
     *
     * <p>该类是私有内部 record 类型，用于封装 {@link #splitFrontmatter(String)} 方法的解析结果：
     * <ul>
     *   <li>{@code yaml} - YAML frontmatter 字符串（不包含 --- 分隔符）</li>
     *   <li>{@code body} - 正文内容字符串</li>
     * </ul>
     *
     * @param yaml YAML frontmatter 字符串（不包含 --- 分隔符）
     * @param body 正文内容字符串
     */
    private record Frontmatter(String yaml, String body) {}

    /**
     * 分离 YAML frontmatter 和正文内容。
     *
     * <p>该方法查找文件开头的 --- 分隔符，提取两个 --- 之间的 YAML 部分，
     * 以及第二个 --- 之后的正文内容。
     *
     * <p>分离规则：
     * <ul>
     *   <li>如果文件不以 --- 开头，返回空的 YAML 部分和完整的文件内容作为正文</li>
     *   <li>如果文件以 --- 开头但没有找到第二个 ---，返回空的 YAML 部分和完整的文件内容作为正文</li>
     *   <li>如果找到两个 ---，提取中间的 YAML 部分和第二个 --- 之后的正文</li>
     * </ul>
     *
     * <p>示例：
     * <pre>
     * 输入：
     * ---
     * name: my-skill
     * description: A skill
     * ---
     * Body content
     *
     * 输出：
     * yaml = "name: my-skill\ndescription: A skill"
     * body = "Body content"
     * </pre>
     *
     * @param text SKILL.md 文件的完整内容
     * @return 包含 YAML 部分和正文内容的 {@link Frontmatter} 记录
     */
    private static Frontmatter splitFrontmatter(String text) {
        // 如果文件不以 --- 开头，返回空的 YAML 和完整的正文
        if (!text.startsWith(DELIM)) return new Frontmatter("", text);
        // 查找第二个 --- 分隔符的位置
        int second = text.indexOf("\n" + DELIM, DELIM.length());
        // 如果没有找到第二个 ---，返回空的 YAML 和完整的正文
        if (second < 0) return new Frontmatter("", text);
        // 提取 YAML 部分（不包含 --- 分隔符）
        String yaml = text.substring(DELIM.length(), second).trim();
        // 提取正文部分（第二个 --- 之后，去除前导空白）
        String body = text.substring(second + ("\n" + DELIM).length()).stripLeading();
        return new Frontmatter(yaml, body);
    }

    /**
     * 解析 allowed-tools 字段为 ToolPolicy 实例列表。
     *
     * <p>该方法支持两种输入格式：
     * <ul>
     *   <li>YAML 列表：{@code ["read", "shell(command:python3 *)"]}</li>
     *   <li>逗号分隔的字符串：{@code "read, shell(command:python3 *)"}</li>
     * </ul>
     *
     * <p>如果 allowed-tools 为空且传入了 EnvContext，则根据环境中可用的可执行文件自动推断：
     * <ul>
     *   <li>始终允许 read, write, edit, grep, glob</li>
     *   <li>如果环境中有 python3/python，允许 shell(command:python3 *) 和 shell(command:python *)</li>
     *   <li>如果环境中有 node，允许 shell(command:node *)</li>
     *   <li>如果环境中有 bash/sh/pwsh/powershell，允许 shell（无限制）</li>
     * </ul>
     *
     * <p>解析流程：
     * <ol>
     *   <li>将输入转换为字符串列表</li>
     *   <li>过滤空白字符串</li>
     *   <li>调用 {@link ToolPolicy#parse(String)} 解析每个策略字符串</li>
     *   <li>如果结果为空且 env 不为 null，调用 {@link #inferAllowedToolsFromEnv(EnvContext)}</li>
     * </ol>
     *
     * @param node allowed-tools 字段的 YAML 节点（列表或字符串）
     * @param env 环境上下文，用于推断 allowed-tools（可为 null）
     * @return ToolPolicy 实例列表
     */
    private static List<ToolPolicy> parseAllowedTools(Object node, EnvContext env) {
        // 将输入转换为字符串列表
        List<String> raw;
        if (node == null) {
            raw = List.of();
        } else {
            raw = (node instanceof List<?> l)
                // 如果是列表，将每个元素转换为字符串
                ? l.stream().map(Object::toString).toList()
                // 如果是字符串，按逗号分割
                : Arrays.stream(node.toString().split(",")).map(String::trim).toList();
        }
        // 过滤空白字符串并解析每个策略
        List<ToolPolicy> policies = raw.stream().filter(s -> !s.isBlank()).map(ToolPolicy::parse).toList();

        // 如果 allowed-tools 为空且传入了 EnvContext，根据环境自动推断
        if (policies.isEmpty() && env != null) {
            return inferAllowedToolsFromEnv(env);
        }
        return policies;
    }

    /**
     * 根据环境上下文推断允许的工具列表。
     *
     * <p>该方法在 allowed-tools 为空时使用，根据环境中可用的可执行文件自动推断允许的工具。
     *
     * <p>推断规则：
     * <ol>
     *   <li>始终允许文件操作工具：Read、Write、Edit、Grep、Glob</li>
     *   <li>如果环境中有 python3，允许 Bash(command:python3 *)</li>
     *   <li>如果环境中有 python，允许 Bash(command:python *)</li>
     *   <li>如果环境中有 node，允许 Bash(command:node *)</li>
     *   <li>如果环境中有 bash/sh/pwsh/powershell，允许 Bash（无限制）</li>
     * </ol>
     *
     * <p>该方法的目的是为没有显式配置 allowed-tools 的 Skill 提供合理的默认权限。
     *
     * @param env 环境上下文记录，包含可用可执行文件列表
     * @return 推断的 ToolPolicy 列表
     * @see EnvContext#availableExecutables() 可用可执行文件列表
     */
    private static List<ToolPolicy> inferAllowedToolsFromEnv(EnvContext env) {
        var policies = new ArrayList<ToolPolicy>();
        // 始终允许文件操作工具
        policies.add(ToolPolicy.parse("read"));
        policies.add(ToolPolicy.parse("write"));
        policies.add(ToolPolicy.parse("edit"));
        policies.add(ToolPolicy.parse("grep"));
        policies.add(ToolPolicy.parse("glob"));

        // 根据可用可执行文件推断允许的 Bash 命令
        Set<String> executables = new HashSet<>(env.availableExecutables().stream()
            .map(String::toLowerCase)
            .toList());

        // 如果环境中有 python3，允许执行 python3 命令
        if (executables.contains("python3")) {
            policies.add(ToolPolicy.parse("shell(command:python3 *)"));
        }
        // 如果环境中有 python，允许执行 python 命令
        if (executables.contains("python")) {
            policies.add(ToolPolicy.parse("shell(command:python *)"));
        }
        // 如果环境中有 node，允许执行 node 命令
        if (executables.contains("node")) {
            policies.add(ToolPolicy.parse("shell(command:node *)"));
        }
        // 如果环境中有 shell，允许执行任意 shell 命令
        if (executables.contains("bash") || executables.contains("sh") || executables.contains("pwsh") || executables.contains("powershell")) {
            policies.add(ToolPolicy.parse("shell"));
        }

        return policies;
    }

    /**
     * 从 Skill 目录加载 Skill 正文内容。
     *
     * <p>该方法读取 Skill 目录下的 SKILL.md 文件，
     * 分离 YAML frontmatter 并返回正文部分。
     *
     * <p>如果 SKILL.md 不存在或读取失败，返回空字符串。
     *
     * @param skillDir Skill 目录路径
     * @return Skill 正文内容，如果失败则返回空字符串
     */
    public static String loadSkillBody(Path skillDir) {
        Path skillFile = skillDir.resolve("SKILL.md");
        if (!Files.exists(skillFile)) {
            return "";
        }
        try {
            String rawContent = Files.readString(skillFile);
            return splitFrontmatter(rawContent).body();
        } catch (IOException e) {
            return "";
        }
    }

    /**
     * 从映射中提取必需字段。
     *
     * <p>该方法从 YAML 元数据映射中提取指定 key 的值，并将其转换为字符串。
     * 如果字段不存在，抛出 {@link IllegalArgumentException} 异常。
     *
     * <p>该方法用于验证 SKILL.md 文件中的必需字段（name、description、risk-level）。
     *
     * @param m 键值对映射
     * @param key 字段名称
     * @return 字段值（转换为字符串）
     * @throws IllegalArgumentException 如果字段不存在
     */
    private static String required(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v == null) throw new IllegalArgumentException("SKILL.md missing required field: " + key);
        return v.toString();
    }
}
