package org.legend.framework.ai.alibaba.sandbox.backend;

import java.time.Duration;
/**
 * 命令执行结果记录。
 *
 * <p>该记录封装了沙箱中命令执行的完整结果，包括：
 * <ul>
 *   <li>exitCode - 命令的退出码（0 表示成功，非 0 表示失败）</li>
 *   <li>stdout - 命令的标准输出内容</li>
 *   <li>stderr - 命令的标准错误内容</li>
 *   <li>duration - 命令执行的耗时</li>
 * </ul>
 *
 * @param exitCode 命令的退出码（0 表示成功，非 0 表示失败）
 * @param stdout   命令的标准输出内容
 * @param stderr   命令的标准错误内容
 * @param duration 命令执行的耗时
 */
public record ExecResult(
        /** 命令的退出码，0 表示成功执行，非 0 表示执行失败 */
        int exitCode,

        /** 命令的标准输出内容（UTF-8 编码） */
        String stdout,

        /** 命令的标准错误内容（UTF-8 编码） */
        String stderr,

        /** 命令执行的耗时 */
        Duration duration
    ) {
    /**
     * 判断命令是否成功执行。
     *
     * <p>该方法检查退出码是否为 0，为 0 表示命令成功执行，否则表示执行失败。
     *
     * @return 如果 exitCode == 0 返回 true，否则返回 false
     */
    public boolean ok() { return exitCode == 0; }


}
