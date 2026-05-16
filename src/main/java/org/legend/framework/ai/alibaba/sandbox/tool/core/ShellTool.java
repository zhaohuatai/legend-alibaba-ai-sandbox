package org.legend.framework.ai.alibaba.sandbox.tool.core;

import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.skills.enums.OsKind;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvContext;
import org.legend.framework.ai.alibaba.sandbox.tool.BashToPowerShellTranslator;
import org.legend.framework.ai.alibaba.sandbox.tool.ToolInputs;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.time.Duration;
import java.util.Map;
import java.util.function.Function;

/**
 * Bash 命令执行工具，用于在沙箱环境中执行 shell 命令。
 *
 * <p>该工具实现了 Claude Code 标准工具集中的 Bash 工具，支持以下特性：
 * <ul>
 *   <li><b>沙箱隔离</b>：命令在沙箱环境中执行，与宿主机隔离</li>
 *   <li><b>跨平台支持</b>：在 Windows 上自动将 Bash 命令翻译为 PowerShell 语法</li>
 *   <li><b>超时控制</b>：默认超时 120 秒，最大 600 秒</li>
 *   <li><b>输出分离</b>：标准输出、标准错误和退出码分别返回</li>
 *   <li><b>工作目录持久化</b>：同一会话内的多次调用共享工作目录</li>
 * </ul>
 *
 * <p>沙箱预装工具：
 * <ul>
 *   <li>python3 - Python 3 解释器</li>
 *   <li>ripgrep (rg) - 快速正则搜索工具</li>
 *   <li>jq - JSON 处理工具</li>
 *   <li>curl - HTTP 请求工具</li>
 *   <li>node - Node.js 运行时</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 执行简单命令
 * ToolInputs.BashInput input = new ToolInputs.BashInput("ls -la", null, "List files");
 * String result = bashTool.apply(input);
 *
 * // 执行 Python 脚本
 * ToolInputs.BashInput input = new ToolInputs.BashInput("python3 script.py", 30000L, "Run script");
 * String result = bashTool.apply(input);
 * }</pre>
 *
 * @see ToolInputs.BashInput Bash 工具的输入参数
 * @see SandboxBackend 沙箱后端接口，提供命令执行的底层实现
 * @see EnvContext 环境上下文，用于判断操作系统类型
 * @see BashToPowerShellTranslator Bash 到 PowerShell 的翻译器
 */
public class ShellTool implements Function<ToolInputs.BashInput, String> {

    /**
     * 沙箱后端实例，用于执行 shell 命令。
     */
    private final SandboxBackend backend;

    /**
     * 环境上下文记录，包含操作系统类型、Shell 类型等信息。
     */
    private final EnvContext env;

    /**
     * 创建 BashTool 实例。
     *
     * @param backend 沙箱后端实例，提供命令执行的底层实现
     * @param env 环境上下文记录，用于判断操作系统类型
     */
    public ShellTool(SandboxBackend backend, EnvContext env) {
        this.backend = backend; this.env = env;
    }

    /**
     * 在沙箱环境中执行 shell 命令。
     *
     * <p>该方法执行以下操作：
     * <ol>
     *   <li>解析超时参数（默认 120 秒，最大 600 秒）</li>
     *   <li>根据操作系统类型对命令进行翻译（Windows 上翻译为 PowerShell）</li>
     *   <li>调用沙箱后端执行命令</li>
     *   <li>组装输出结果（标准输出 + 标准错误 + 退出码）</li>
     * </ol>
     *
     * <p>输出格式：
     * <pre>
     * [标准输出内容]
     * [stderr]
     * [标准错误内容]
     * [exit 退出码]
     * </pre>
     *
     * @param in Bash 输入参数，包含命令字符串、超时时间和描述
     * @return 命令执行结果文本
     */
    @Override
    public String apply(ToolInputs.BashInput in) {
        /** 解析超时时间：默认 300 秒（5 分钟），最大 600 秒（10 分钟） */
        long ms = in.timeout() == null ? 300_000L : Math.min(in.timeout(), 600_000L);
        /** 根据操作系统类型翻译命令 */
        String wrapped = wrapForOs(in.command());
        /** 调用沙箱后端执行命令 */
        var r = backend.exec(wrapped, Duration.ofMillis(ms), Map.of());

        /** 组装输出结果 */
        StringBuilder sb = new StringBuilder();
        /** 添加标准输出 */
        if (!r.stdout().isEmpty()) sb.append(r.stdout());
        /** 添加标准错误（如果有） */
        if (!r.stderr().isEmpty()) {
            sb.append("\n[stderr]\n").append(r.stderr());
        }
        /** 添加退出码（如果执行失败） */
        if (!r.ok()) sb.append("\n[exit ").append(r.exitCode()).append("]");
        return sb.toString();
    }

    /**
     * 根据操作系统类型翻译命令。
     *
     * <p>如果当前操作系统是 Windows，则使用 {@link BashToPowerShellTranslator} 将 Bash 命令
     * 翻译为 PowerShell 语法；否则直接返回原命令。
     *
     * @param cmd 原始命令字符串
     * @return 翻译后的命令字符串
     */
    private String wrapForOs(String cmd) {
        if (env.os() == OsKind.WINDOWS) {
            var translation = BashToPowerShellTranslator.translate(cmd);
            return translation.command();
        }
        return cmd;
    }

    /**
     * 创建 Bash 工具的 Spring AI ToolCallback 实例。
     *
     * <p>该方法创建一个 {@link ToolCallback} 实例，用于集成到 Spring AI 的工具调用框架中。
     * 工具描述详细说明了工具的功能、沙箱预装工具、超时设置和工作目录持久化特性。
     *
     * @param backend 沙箱后端实例
     * @param env 环境上下文记录
     * @return Spring AI ToolCallback 实例
     */
    @SuppressWarnings("null")
	public static ToolCallback create(SandboxBackend backend, EnvContext env) {
        String os = env != null && env.os() != null ? env.os().name() : "LINUX";
        String shellType = env != null && env.shellKind() != null ? env.shellKind().name() : "BASH";
        return FunctionToolCallback.builder("shell", new ShellTool(backend, env))
            .description("""
                You are executing commands in a sandbox with the following shell type: {SHELL_TYPE} which is either BASH or POWERSHELL.

                **MANDATORY RULES:**

                1. You MUST check the current shell type before every command block.
                2. If SHELL_TYPE = BASH:
                - Use &&, ||, |, >, <, >>, 2>&1
                - Use $(cmd) for command substitution
                - Use $VAR for variables
                3. If SHELL_TYPE = POWERSHELL:
                - DO NOT use &&, ||, >, < (except -gt, -lt)
                - Use ; to chain commands
                - Use $($cmd) or cmd inside quotes for substitution
                - Use $env:VAR for environment variables
                - Use Out-File or > for redirection

                **BEFORE GENERATING ANY SHELL COMMAND, repeat this check:**
                "Current shell is {SHELL_TYPE}. I will use {SHELL_TYPE} syntax only."
                """.formatted(shellType, shellType))
            .inputType(ToolInputs.BashInput.class)
            .build();
    }
}
