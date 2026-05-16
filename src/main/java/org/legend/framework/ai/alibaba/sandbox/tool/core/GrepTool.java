package org.legend.framework.ai.alibaba.sandbox.tool.core;

import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.tool.ToolInputs;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.time.Duration;
import java.util.*;
import java.util.function.Function;

/**
 * Grep 搜索工具，用于在沙箱环境中执行正则表达式文件内容搜索。
 *
 * <p>该工具实现了 Claude Code 标准工具集中的 Grep 工具，基于 ripgrep (rg) 实现，支持以下特性：
 * <ul>
 *   <li><b>快速搜索</b>：使用 ripgrep 引擎，比传统 grep 快数倍</li>
 *   <li><b>多种输出模式</b>：支持 content（内容）、files_with_matches（文件名）、count（计数）</li>
 *   <li><b>大小写敏感</b>：支持 -i 参数进行大小写不敏感搜索</li>
 *   <li><b>行号显示</b>：支持 -n 参数显示匹配行号</li>
 *   <li><b>Glob 过滤</b>：支持 --glob 参数按文件模式过滤</li>
 *   <li><b>超时控制</b>：搜索命令超时时间为 30 秒</li>
 * </ul>
 *
 * <p>输出模式：
 * <ul>
 *   <li><b>content</b>（默认）：返回匹配的内容，每行包含文件路径和行号（如果启用 -n）</li>
 *   <li><b>files_with_matches</b>：仅返回包含匹配的文件路径列表</li>
 *   <li><b>count</b>：返回每个文件中匹配的行数</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 搜索包含 "TODO" 的行
 * ToolInputs.GrepInput input = new ToolInputs.GrepInput("TODO", null, null, false, true, "content");
 * String result = grepTool.apply(input);
 *
 * // 搜索所有 Java 文件中的 "class"（大小写不敏感）
 * ToolInputs.GrepInput input = new ToolInputs.GrepInput("class", null, "*.java", true, true, "files_with_matches");
 * String result = grepTool.apply(input);
 * }</pre>
 *
 * @see ToolInputs.GrepInput Grep 工具的输入参数
 * @see SandboxBackend 沙箱后端接口，提供命令执行的底层实现
 */
public class GrepTool implements Function<ToolInputs.GrepInput, String> {

    /**
     * 沙箱后端实例，用于执行 ripgrep 命令。
     */
    private final SandboxBackend backend;

    /**
     * 创建 GrepTool 实例。
     *
     * @param backend 沙箱后端实例，提供命令执行的底层实现
     */
    public GrepTool(SandboxBackend backend) { this.backend = backend; }

    /**
     * 执行正则表达式文件内容搜索。
     *
     * <p>该方法执行以下操作：
     * <ol>
     *   <li>构建 ripgrep 命令参数列表</li>
     *   <li>根据输入参数添加 -i（大小写不敏感）、-n（行号）、-l（文件名）、-c（计数）等选项</li>
     *   <li>添加 glob 过滤参数（如果提供）</li>
     *   <li>对参数进行 shell 引用转义</li>
     *   <li>调用沙箱后端执行命令（超时 30 秒）</li>
     *   <li>检查退出码（0 表示成功，1 表示无匹配，其他表示错误）</li>
     *   <li>返回搜索结果或 "(no matches)"</li>
     * </ol>
     *
     * @param in Grep 输入参数，包含搜索模式、路径、glob 过滤、大小写敏感、行号显示和输出模式
     * @return 搜索结果文本
     * @throws RuntimeException 如果 ripgrep 命令执行失败（退出码不为 0 或 1）
     */
    @Override
    public String apply(ToolInputs.GrepInput in) {
        /** 构建 ripgrep 命令参数列表 */
        List<String> args = new ArrayList<>();
        /** 使用 ripgrep 命令 */
        args.add("rg");
        /** 添加大小写不敏感选项 */
        if (Boolean.TRUE.equals(in.i())) args.add("-i");
        /** 添加行号显示选项 */
        if (Boolean.TRUE.equals(in.n())) args.add("-n");

        /** 根据输出模式添加相应选项 */
        String mode = in.output_mode() == null ? "content" : in.output_mode();
        switch (mode) {
            case "files_with_matches" -> args.add("-l");  /** 仅输出文件名 */
            case "count" -> args.add("-c");               /** 输出匹配计数 */
            default -> { /* content */ }                  /** 默认输出内容 */
        }
        /** 添加 glob 过滤参数 */
        if (in.glob() != null) { args.add("--glob"); args.add(in.glob()); }

        /** 添加搜索模式（使用 -- 分隔符防止模式以 - 开头被解析为选项） */
        args.add("--"); args.add(in.pattern());
        /** 添加搜索路径（如果提供） */
        if (in.path() != null) args.add(in.path());

        /** 对参数进行 shell 引用转义并拼接为命令字符串 */
        String cmd = String.join(" ", args.stream().map(GrepTool::shellQuote).toList());
        /** 执行命令，超时时间 30 秒 */
        var r = backend.exec(cmd, Duration.ofSeconds(30), Map.of());
        /** 检查退出码：0 表示成功，1 表示无匹配，其他表示错误 */
        if (r.exitCode() != 0 && r.exitCode() != 1) {
            throw new RuntimeException("grep failed: " + r.stderr());
        }
        /** 返回搜索结果，如果无匹配则返回提示信息 */
        return r.stdout().isEmpty() ? "(no matches)" : r.stdout();
    }

    /**
     * 对字符串进行 shell 引用转义。
     *
     * <p>如果字符串仅包含安全字符（字母、数字、下划线、点、斜杠、反斜杠、等号、星号、减号），
     * 则直接返回；否则使用单引号包裹，并转义内部的单引号。
     *
     * @param s 要转义的字符串
     * @return shell 安全的字符串
     */
    private static String shellQuote(String s) {
        if (s.matches("[A-Za-z0-9_./\\-=*]+")) return s;
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /**
     * 创建 Grep 工具的 Spring AI ToolCallback 实例。
     *
     * <p>该方法创建一个 {@link ToolCallback} 实例，用于集成到 Spring AI 的工具调用框架中。
     * 工具描述详细说明了工具的功能、输出模式、glob 过滤和大小写不敏感搜索特性。
     *
     * @param backend 沙箱后端实例
     * @return Spring AI ToolCallback 实例
     */
    public static ToolCallback create(SandboxBackend backend) {
        return FunctionToolCallback.builder("grep", new GrepTool(backend))
            .description("""
                Fast regex search using ripgrep. Searches file contents.
                output_mode: 'content' (default), 'files_with_matches', or 'count'.
                Supports glob filtering and case-insensitive search.
                """)
            .inputType(ToolInputs.GrepInput.class)
            .build();
    }
}
