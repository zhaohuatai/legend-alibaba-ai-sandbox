package org.legend.framework.ai.alibaba.sandbox.skills.env;

/**
 * 系统提示注入器，将环境上下文信息格式化为 LLM 可理解的 system prompt 片段。
 *
 * <p>该类是 Claude Code 运行时契约的 OS 感知层输出组件，负责将 {@link EnvContext}
 * 中的环境信息转换为结构化的 XML 格式提示文本，并附加跨平台命令语法指导。
 *
 * <p>生成的提示文本包含：
 * <ul>
 *   <li>操作系统类型（os）</li>
 *   <li>Shell 类型（shell）</li>
 *   <li>当前工作目录（cwd）</li>
 *   <li>可用可执行文件列表（available_executables）</li>
 *   <li>跨平台命令语法指导（Windows 使用 PowerShell 语法）</li>
 * </ul>
 *
 * <p>输出示例：
 * <pre>{@code
 * <env>
 * os: WINDOWS
 * shell: POWERSHELL
 * cwd: E:\worksapce\sts5.1\legend-smartmind
 * available_executables: [python3, git, curl, pwsh]
 * </env>
 * When invoking the Bash tool on Windows, use PowerShell syntax
 * (e.g. `Get-ChildItem` not `ls`). Prefer absolute paths.
 * }</pre>
 *
 * <p>使用示例：
 * <pre>{@code
 * EnvContext env = EnvProbe.probe(Paths.get(System.getProperty("user.dir")));
 * String systemPrompt = SystemPromptInjector.render(env);
 *
 * // 将 systemPrompt 拼接到 ChatClient 的 defaultSystem 中
 * ChatClient client = ChatClient.builder(chatModel)
 *     .defaultSystem(systemPrompt + "\n\nYou are a helpful assistant...")
 *     .build();
 * }</pre>
 *
 * @see EnvContext 环境上下文记录类
 * @see EnvProbe 环境探测工具类
 */
public class SystemPromptInjector {

    /**
     * 私有构造函数，防止实例化。
     * 该类仅提供静态工具方法。
     */
    private SystemPromptInjector() {}

    /**
     * 将环境上下文渲染为 system prompt 片段。
     *
     * <p>该方法生成两部分内容：
     * <ol>
     *   <li><env> XML 块：包含 os、shell、cwd、available_executables 四个字段</li>
     *   <li>跨平台命令语法指导：提醒模型在 Windows 上使用 PowerShell 语法</li>
     * </ol>
     *
     * <p>该提示文本应作为 system prompt 的一部分传递给 LLM，使模型能够：
     * <ul>
     *   <li>根据 os 字段判断当前操作系统</li>
     *   <li>根据 shell 字段选择合适的命令语法</li>
     *   <li>根据 cwd 字段使用正确的相对路径</li>
     *   <li>根据 available_executables 字段判断哪些工具可以直接调用</li>
     * </ul>
     *
     * @param env 环境上下文记录，包含探测到的环境信息
     * @return 格式化后的 system prompt 片段字符串
     */
    public static String render(EnvContext env) {
        String crossPlatformHint = 
        	switch (env.os()) {
            case WINDOWS -> "When invoking the Bash tool on Windows, use PowerShell syntax (e.g. `Get-ChildItem` not `ls`). Prefer absolute paths.";
            case LINUX -> "You are running in a Linux container. Use bash/shell commands (e.g. `ls`, `cat`, `grep`). Prefer absolute paths.";
            case MACOS -> "You are running on macOS. Use bash/zsh commands. Prefer absolute paths.";
            case UNKNOWN -> "Use standard POSIX shell commands (e.g. `ls`, `cat`, `grep`). Prefer absolute paths.";
        };

        return """
            <env>
            os: %s
            shell: %s
            cwd: %s
            available_executables: %s
            </env>
            %s
            """.formatted(
                env.os(),
                env.shellKind(),
                env.cwd(),
                env.availableExecutables(),
                crossPlatformHint
            );
    }
}
