package org.legend.framework.ai.alibaba.sandbox.skills.manifest;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * 工具策略记录类，定义 Skill 可以使用的工具及其参数约束。
 *
 * <p>该类是 Claude Code 运行时契约的权限控制层核心组件，用于解析和执行 Skill 的工具访问策略。
 * 每个 ToolPolicy 实例包含工具名称和一组参数约束，用于限制 Skill 可以如何调用该工具。
 *
 * <p>核心设计理念：
 * <ul>
 *   <li><b>策略解析</b> - 从字符串格式解析工具策略，支持带约束和不带约束的策略</li>
 *   <li><b>Glob 模式匹配</b> - 使用 glob 模式语法定义参数约束，自动转换为正则表达式</li>
 *   <li><b>权限控制</b> - 在工具调用时检查是否匹配策略，确保 Skill 只能使用授权的工具</li>
 * </ul>
 *
 * <p>策略语法：
 * <ul>
 *   <li>{@code Read} - 允许使用 Read 工具，无参数约束</li>
 *   <li>{@code Bash(command:python3 *)} - 允许使用 Bash 工具，但命令必须以 "python3 " 开头</li>
 *   <li>{@code Write(file_path:*.txt)} - 允许使用 Write 工具，但文件路径必须是 .txt 文件</li>
 *   <li>{@code Grep(pattern:TODO,file_path:*.java)} - 允许使用 Grep 工具，但模式必须包含 "TODO" 且文件为 .java</li>
 * </ul>
 *
 * <p>约束匹配规则：
 * <ul>
 *   <li>约束使用 glob 模式语法（* 表示任意字符，? 表示单个字符）</li>
 *   <li>glob 模式会被转换为正则表达式进行匹配</li>
 *   <li>所有约束都必须满足，工具调用才会被允许</li>
 *   <li>如果工具名称不匹配，直接返回 false</li>
 * </ul>
 *
 * <p>使用场景：
 * <ul>
 *   <li>{@link SkillManifest#allowedTools()} - Skill 的允许工具列表，包含多个 ToolPolicy 实例</li>
 *   <li>{@link org.legend.framework.ai.alibaba.sandbox.GlobalPolicy} - 全局拒绝策略，使用 ToolPolicy 定义拒绝规则</li>
 *   <li>{@link org.legend.framework.ai.alibaba.sandbox.tool.GuardedToolCallback#checkSecurity(String)} - 安全检查时调用 {@link #matches(String, Map)} 方法</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 解析策略
 * ToolPolicy policy = ToolPolicy.parse("Bash(command:python3 *)");
 *
 * // 检查工具调用是否允许
 * Map<String, Object> args = Map.of("command", "python3 script.py");
 * boolean allowed = policy.matches("shell", args);  // true
 *
 * Map<String, Object> args2 = Map.of("command", "rm -rf /");
 * boolean allowed2 = policy.matches("shell", args2);  // false
 * }</pre>
 *
 * @param tool 工具名称（如 "read", "write", "shell"）
 * @param constraints 参数约束列表，定义工具调用时必须满足的条件
 * @see SkillManifest Skill 元数据，包含允许工具列表
 * @see org.legend.framework.ai.alibaba.sandbox.GlobalPolicy 全局策略检查器
 */
public record ToolPolicy(
    /**
     * 工具名称。
     *
     * <p>该字段表示策略适用的工具名称，如 "read", "write", "shell", "grep", "glob", "edit"。
     * 在 {@link #matches(String, Map)} 方法中，该字段用于检查工具调用是否匹配此策略。
     * 匹配时忽略大小写。
     */
    String tool,

    /**
     * 参数约束列表。
     *
     * <p>该字段包含零个或多个 {@link ArgConstraint} 实例，定义工具调用时必须满足的参数条件。
     * 如果列表为空，表示该工具可以被无条件使用（仅检查工具名称）。
     * 如果列表非空，所有约束都必须满足，工具调用才会被允许。
     */
    java.util.List<ArgConstraint> constraints
) {

    /**
     * 参数约束记录类，定义单个参数的约束规则。
     *
     * <p>该类是 ToolPolicy 的内部 record 类型，用于封装单个参数的约束信息：
     * <ul>
     *   <li>{@code key} - 参数名称（如 "command"、"file_path"、"pattern"）</li>
     *   <li>{@code regex} - 正则表达式模式，参数值必须匹配此模式</li>
     * </ul>
     *
     * <p>该记录类在 {@link ToolPolicy#parse(String)} 方法中创建，
     * 在 {@link ToolPolicy#matches(String, Map)} 方法中使用。
     *
     * @param key 参数名称（如 "command"、"file_path"、"pattern"）
     * @param regex 正则表达式模式，参数值必须匹配此模式
     */
    public record ArgConstraint(
        /**
         * 参数名称。
         *
         * <p>该字段表示约束适用的参数名称，如：
         * <ul>
         *   <li>{@code "command"} - Bash 工具的命令参数</li>
         *   <li>{@code "file_path"} - Read/Write/Edit 工具的文件路径参数</li>
         *   <li>{@code "pattern"} - Grep 工具的搜索模式参数</li>
         * </ul>
         */
        String key,

        /**
         * 正则表达式模式。
         *
         * <p>该字段包含编译后的正则表达式模式，参数值必须完全匹配此模式。
         * 正则表达式由 glob 模式转换而来，使用 {@link ToolPolicy#globToRegex(String)} 方法。
         *
         * <p>匹配时使用 {@link Pattern#DOTALL} 模式，使 {@code .} 可以匹配换行符。
         */
        Pattern regex
    ) {}

    /**
     * 解析工具策略字符串为 ToolPolicy 实例。
     *
     * <p>该方法解析以下格式的策略字符串：
     * <ul>
     *   <li>{@code ToolName} - 无约束的工具策略（如 "Read"）</li>
     *   <li>{@code ToolName(key1:pattern1,key2:pattern2)} - 带约束的工具策略（如 "Bash(command:python3 *)"）</li>
     *   <li>{@code ToolName(pattern)} - 默认约束（key 为 "command"，如 "Bash(python3 *)"）</li>
     * </ul>
     *
     * <p>解析步骤：
     * <ol>
     *   <li>去除首尾空白字符</li>
     *   <li>查找左括号位置，判断是否有约束</li>
     *   <li>提取工具名称（括号前的部分）</li>
     *   <li>解析约束部分（括号内的内容），按逗号分割</li>
     *   <li>对每个约束，按冒号分割 key 和 pattern</li>
     *   <li>将 glob 模式转换为正则表达式（调用 {@link #globToRegex(String)}）</li>
     *   <li>构建并返回 ToolPolicy 实例</li>
     * </ol>
     *
     * <p>解析示例：
     * <ul>
     *   <li>{@code "read"} → ToolPolicy("read", [])</li>
     *   <li>{@code "shell(command:python3 *)"} → ToolPolicy("shell", [ArgConstraint("command", Pattern.compile("^python3 .*.*$"))])</li>
     *   <li>{@code "write(*.txt)"} → ToolPolicy("write", [ArgConstraint("command", Pattern.compile("^.*\\.txt.*$"))])</li>
     * </ul>
     *
     * @param spec 工具策略字符串（如 "shell(command:python3 *)"）
     * @return 解析后的 ToolPolicy 实例
     * @throws StringIndexOutOfBoundsException 如果括号不匹配
     */
    public static ToolPolicy parse(String spec) {
        // 去除首尾空白
        spec = spec.trim();
        // 查找左括号位置，判断是否有约束
        int lp = spec.indexOf('(');
        // 如果没有左括号，返回无约束的策略
        if (lp < 0) return new ToolPolicy(spec, java.util.List.of());
        // 提取工具名称
        String tool = spec.substring(0, lp).trim();
        // 提取括号内的约束部分
        String inner = spec.substring(lp + 1, spec.lastIndexOf(')')).trim();
        // 如果约束部分为空，返回无约束的策略
        if (inner.isEmpty()) return new ToolPolicy(tool, java.util.List.of());

        // 解析约束列表
        var list = new java.util.ArrayList<ArgConstraint>();
        // 按逗号分割约束部分
        for (String seg : inner.split(",")) {
            seg = seg.trim();
            int colon = seg.indexOf(':');
            String key, pat;
            // 如果有冒号，按冒号分割 key 和 pattern；否则使用默认 key "command"
            if (colon < 0) { key = "command"; pat = seg; }
            else { key = seg.substring(0, colon).trim(); pat = seg.substring(colon + 1).trim(); }
            // 将 glob 模式转换为正则表达式并添加约束
            list.add(new ArgConstraint(key, globToRegex(pat)));
        }
        return new ToolPolicy(tool, list);
    }

    /**
     * 检查工具调用是否匹配此策略。
     *
     * <p>该方法执行以下检查：
     * <ol>
     *   <li>检查工具名称是否匹配（忽略大小写）</li>
     *   <li>如果没有约束，直接返回 true</li>
     *   <li>对每个约束，检查对应的参数值是否匹配正则表达式</li>
     *   <li>所有约束都满足则返回 true，否则返回 false</li>
     * </ol>
     *
     * <p>匹配示例：
     * <ul>
     *   <li>策略：{@code "Bash(command:python3 *)"}，调用：{@code Bash("python3 script.py")} → true</li>
     *   <li>策略：{@code "Bash(command:python3 *)"}，调用：{@code Bash("rm -rf /")} → false</li>
     *   <li>策略：{@code "Read"}，调用：{@code Read("/path/to/file.txt")} → true</li>
     * </ul>
     *
     * @param toolName 要检查的工具名称（如 "Bash"、"Read"）
     * @param args 工具调用的参数映射（如 {"command": "python3 script.py"}）
     * @return 如果工具调用匹配此策略返回 true，否则返回 false
     */
    public boolean matches(String toolName, Map<String, Object> args) {
        // 检查工具名称是否匹配（忽略大小写）
        if (!tool.equalsIgnoreCase(toolName)) return false;
        // 如果没有约束，直接返回 true
        if (constraints.isEmpty()) return true;
        // 检查每个约束是否满足
        for (ArgConstraint c : constraints) {
            // 获取参数值
            Object v = pickArgValue(toolName, args, c.key());
            // 如果参数值不存在，返回 false
            if (v == null) return false;
            // 如果参数值不匹配正则表达式，返回 false
            if (!c.regex().matcher(v.toString()).matches()) return false;
        }
        return true;
    }

    /**
     * 从参数映射中提取指定 key 的值。
     *
     * <p>该方法处理 Bash 工具的特殊情况：Bash 工具的命令参数 key 为 "command"，
     * 但实际参数映射中的 key 也是 "command"，因此直接返回。
     *
     * <p>该方法为未来的工具类型扩展预留了特殊处理逻辑的空间。
     *
     * @param tool 工具名称
     * @param args 参数映射
     * @param key 要提取的参数 key
     * @return 参数值，如果不存在返回 null
     */
    private static Object pickArgValue(String tool, Map<String, Object> args, String key) {
        // shell 工具的特殊处理：命令参数 key 为 "command"
        if ("shell".equalsIgnoreCase(tool) && "command".equals(key)) {
            return args.get("command");
        }
        return args.get(key);
    }

    /**
     * 将 glob 模式转换为正则表达式。
     *
     * <p>转换规则：
     * <ul>
     *   <li>{@code *} → {@code .*}（匹配任意数量的任意字符）</li>
     *   <li>{@code ?} → {@code .}（匹配单个字符）</li>
     *   <li>{@code .() +|^${}[]\} → {@code \.()\ +|^${}[]\}（转义正则表达式特殊字符）</li>
     * </ul>
     *
     * <p>转换后的正则表达式会自动添加 {@code ^} 和 {@code .*$} 前缀和后缀，
     * 确保整个字符串都匹配模式。
     *
     * <p>转换示例：
     * <ul>
     *   <li>{@code "python3 *"} → {@code "^python3 .*.*$"}</li>
     *   <li>{@code "*.txt"} → {@code "^.*\\.txt.*$"}</li>
     *   <li>{@code "TODO"} → {@code "^TODO.*$"}</li>
     * </ul>
     *
     * @param glob glob 模式字符串（如 "python3 *"、"*.txt"）
     * @return 编译后的正则表达式 Pattern（使用 {@link Pattern#DOTALL} 模式）
     */
    private static Pattern globToRegex(String glob) {
        // 构建正则表达式字符串，以 ^ 开头
        StringBuilder sb = new StringBuilder("^");
        // 遍历 glob 模式的每个字符
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            switch (c) {
                // * 转换为 .*
                case '*' -> sb.append(".*");
                // ? 转换为 .
                case '?' -> sb.append('.');
                // 转义正则表达式特殊字符
                case '.', '(', ')', '+', '|', '^', '$', '{', '}', '[', ']', '\\' ->
                    sb.append('\\').append(c);
                // 其他字符直接添加
                default -> sb.append(c);
            }
        }
        // 添加 .* 结尾，确保整个字符串匹配
        sb.append(".*$");
        // 编译正则表达式，使用 DOTALL 模式使 . 匹配换行符
        return Pattern.compile(sb.toString(), Pattern.DOTALL);
    }
}
