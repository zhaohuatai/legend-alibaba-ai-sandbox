package org.legend.framework.ai.alibaba.sandbox.skills.enums;

/**
 * 操作系统类型枚举。
 *
 * <p>用于区分不同平台的命令语法差异：
 * <ul>
 *   <li>LINUX - Linux 系统，使用 Bash 语法</li>
 *   <li>MACOS - macOS 系统，使用 Bash/Zsh 语法</li>
 *   <li>WINDOWS - Windows 系统，使用 PowerShell 语法</li>
 *   <li>UNKNOWN - 未知系统，默认使用 Bash 语法</li>
 * </ul>
 */
public enum OsKind {
    /** Linux 操作系统，包括各种发行版（Ubuntu、CentOS、Debian 等） */
    LINUX,

    /** macOS 操作系统（Apple 桌面系统） */
    MACOS,

    /** Windows 操作系统（包括 Windows 10/11/Server 等） */
    WINDOWS,

    /** 未知操作系统，无法识别时回退到此值 */
    UNKNOWN
}