package org.legend.framework.ai.alibaba.sandbox.skills.env;

import java.nio.file.Path;
import java.util.*;

import org.legend.framework.ai.alibaba.sandbox.skills.enums.OsKind;
import org.legend.framework.ai.alibaba.sandbox.skills.enums.ShellKind;

/**
 * 环境上下文记录，封装当前运行环境的操作系统、Shell 类型、工作目录等信息。
 *
 * <p>该记录类是 Claude Code 运行时契约的核心组件之一，用于在 Agent 启动时探测
 * 并传递环境信息到 LLM 的 system prompt 中，使模型能够根据实际操作系统
 * 生成正确的命令（例如 Windows 上使用 PowerShell 语法而非 Bash 语法）。
 *
 * <p>使用示例：
 * <pre>{@code
 * EnvContext env = EnvProbe.probe(Paths.get(System.getProperty("user.dir")));
 * // env.os() -> WINDOWS
 * // env.shellKind() -> POWERSHELL
 * // env.shellExecutable() -> "pwsh"
 * // env.cwd() -> E:\worksapce\sts5.1\legend-smartmind
 * // env.availableExecutables() -> [python3, git, curl, ...]
 * }</pre>
 *
 * @param os                  操作系统类型（LINUX / MACOS / WINDOWS / UNKNOWN）
 * @param shellKind           Shell 类型（BASH / ZSH / POWERSHELL / CMD）
 * @param shellExecutable     Shell 可执行文件名称（如 "bash"、"pwsh"、"powershell"）
 * @param cwd                 当前工作目录的绝对路径
 * @param availableExecutables 系统中可用的可执行文件列表（如 python3、git、curl 等）
 */
public record EnvContext(
    /** 操作系统类型枚举值，用于判断当前运行平台 */
    OsKind os,

    /** Shell 类型枚举值，决定命令执行时使用的解释器 */
    ShellKind shellKind,

    /** Shell 可执行文件的名称，如 "bash"、"zsh"、"pwsh" 或 "powershell" */
    String shellExecutable,

    /** 当前工作目录的绝对路径，所有相对路径都基于此目录解析 */
    Path cwd,

    /** 系统中可用的可执行文件名称列表，用于判断哪些工具可以直接调用 */
    List<String> availableExecutables
) {
 

 
}
