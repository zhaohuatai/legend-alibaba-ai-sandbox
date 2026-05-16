package org.legend.framework.ai.alibaba.sandbox.skills.enums;
/**
 * Shell 类型枚举。
 *
 * <p>用于决定命令执行时使用的解释器：
 * <ul>
 *   <li>BASH - Bourne Again Shell，Linux/macOS 默认 Shell</li>
 *   <li>ZSH - Z Shell，macOS Catalina 及以后版本默认 Shell</li>
 *   <li>POWERSHELL - PowerShell 7+（跨平台，命令名 pwsh）</li>
 *   <li>CMD - Windows 命令提示符（cmd.exe）</li>
 * </ul>
 */
public enum ShellKind {
    /** Bourne Again Shell，Linux 系统默认 Shell */
    BASH,

    /** Z Shell，macOS 默认 Shell，兼容 Bash 语法 */
    ZSH,

    /** PowerShell 7+ 跨平台 Shell，命令名为 pwsh */
    POWERSHELL,

    /** Windows 命令提示符（cmd.exe），已逐渐被 PowerShell 取代 */
    CMD
}