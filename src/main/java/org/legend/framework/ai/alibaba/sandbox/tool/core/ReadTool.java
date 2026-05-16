package org.legend.framework.ai.alibaba.sandbox.tool.core;

import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.tool.ToolInputs;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * 文件读取工具，用于从沙箱环境中读取文件内容。
 *
 * <p>该工具实现了 Claude Code 标准工具集中的 Read 工具，支持以下特性：
 * <ul>
 *   <li><b>行号显示</b>：返回内容包含行号，格式为 {@code 行号\t内容}（类似 cat -n）</li>
 *   <li><b>分页读取</b>：支持 offset/limit 参数读取大文件的部分内容</li>
 *   <li><b>UTF-8 编码</b>：默认使用 UTF-8 编码读取文件</li>
 *   <li><b>绝对路径</b>：文件路径必须是绝对路径</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 读取文件全部内容（默认最多 2000 行）
 * ToolInputs.ReadInput input = new ToolInputs.ReadInput("/path/to/file.txt", null, null);
 * String content = readTool.apply(input);
 *
 * // 从第 100 行开始读取，最多读取 50 行
 * ToolInputs.ReadInput input = new ToolInputs.ReadInput("/path/to/file.txt", 100, 50);
 * String content = readTool.apply(input);
 * }</pre>
 *
 * @see ToolInputs.ReadInput 读取工具的输入参数
 * @see SandboxBackend 沙箱后端接口，提供文件读取的底层实现
 */
public class ReadTool implements Function<ToolInputs.ReadInput, String> {

    /**
     * 沙箱后端实例，用于执行文件读取操作。
     */
    private final SandboxBackend backend;

    /**
     * 创建 ReadTool 实例。
     *
     * @param backend 沙箱后端实例，提供文件读取的底层实现
     */
    public ReadTool(SandboxBackend backend) { this.backend = backend; }

    /**
     * 读取文件内容并返回带行号的文本。
     *
     * <p>该方法执行以下操作：
     * <ol>
     *   <li>调用沙箱后端读取文件的原始字节数据</li>
     *   <li>将字节数据按 UTF-8 编码转换为字符串</li>
     *   <li>按行分割内容</li>
     *   <li>根据 offset 和 limit 参数确定要返回的行范围</li>
     *   <li>格式化输出，每行格式为 {@code 行号\t内容}</li>
     * </ol>
     *
     * @param in 读取输入参数，包含文件路径、起始行号和最大行数
     * @return 带行号的文件内容文本
     */
    @Override
    public String apply(ToolInputs.ReadInput in) {
        /** 从沙箱后端读取文件的原始字节数据 */
        byte[] raw = backend.readFile(in.file_path());
        /** 将字节数据按 UTF-8 编码转换为字符串 */
        String text = new String(raw, StandardCharsets.UTF_8);
        /** 按行分割内容，-1 表示保留末尾空行 */
        String[] lines = text.split("\n", -1);

        /** 计算起始行号，默认从第 1 行开始 */
        int start = (in.offset() == null ? 1 : Math.max(1, in.offset()));
        /** 计算结束行号，默认到文件末尾 */
        int end = (in.limit() == null ? lines.length : Math.min(lines.length, start - 1 + in.limit()));

        /** 构建带行号的输出文本 */
        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            sb.append(String.format("%6d\t%s%n", i, lines[i - 1]));
        }
        return sb.toString();
    }

    /**
     * 创建 Read 工具的 Spring AI ToolCallback 实例。
     *
     * <p>该方法创建一个 {@link ToolCallback} 实例，用于集成到 Spring AI 的工具调用框架中。
     * 工具描述详细说明了工具的功能、支持的文件类型、默认行为和分页参数。
     *
     * @param backend 沙箱后端实例
     * @return Spring AI ToolCallback 实例
     */
    public static ToolCallback create(SandboxBackend backend) {
        return FunctionToolCallback.builder("read", new ReadTool(backend))
            .description("""
                Reads a file from the local filesystem.
                Returns content with line numbers in `cat -n` format.
                Supports text and image files. Use absolute paths.
                Default reads up to 2000 lines from start; use offset/limit for large files.
                """)
            .inputType(ToolInputs.ReadInput.class)
            .build();
    }
}
