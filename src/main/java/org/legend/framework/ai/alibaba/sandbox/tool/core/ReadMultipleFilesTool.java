package org.legend.framework.ai.alibaba.sandbox.tool.core;

import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.tool.ToolInputs;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * 批量文件读取工具，用于一次性读取多个文件的内容。
 *
 * <p>该工具接受文件路径列表，返回每个文件的内容，
 * 适用于需要同时查看多个文件的场景。
 *
 * @see ToolInputs.ReadMultipleFilesInput 批量读取工具的输入参数
 * @see SandboxBackend 沙箱后端接口
 */
public class ReadMultipleFilesTool implements Function<ToolInputs.ReadMultipleFilesInput, String> {

    /**
     * 沙箱后端实例，用于执行文件读取操作。
     */
    private final SandboxBackend backend;

    /**
     * 创建 ReadMultipleFilesTool 实例。
     *
     * @param backend 沙箱后端实例
     */
    public ReadMultipleFilesTool(SandboxBackend backend) { this.backend = backend; }

    /**
     * 批量读取文件内容。
     *
     * @param in 批量读取输入参数，包含文件路径列表
     * @return 所有文件的内容，每个文件以分隔符分隔
     */
    @Override
    public String apply(ToolInputs.ReadMultipleFilesInput in) {
        StringBuilder sb = new StringBuilder();
        for (String path : in.file_paths()) {
            try {
                byte[] raw = backend.readFile(path);
                String text = new String(raw, StandardCharsets.UTF_8);
                sb.append("=== ").append(path).append(" ===\n");
                sb.append(text).append("\n\n");
            } catch (Exception e) {
                sb.append("=== ").append(path).append(" ===\n");
                sb.append("Error: ").append(e.getMessage()).append("\n\n");
            }
        }
        return sb.toString();
    }

    /**
     * 创建批量读取工具的 Spring AI ToolCallback 实例。
     *
     * @param backend 沙箱后端实例
     * @return Spring AI ToolCallback 实例
     */
    public static ToolCallback create(SandboxBackend backend) {
        return FunctionToolCallback.builder("read_multiple_files", new ReadMultipleFilesTool(backend))
            .description("""
                Reads multiple files at once and returns their contents.
                Each file content is separated by a header with the file path.
                If a file cannot be read, an error message is returned for that file.
                Use absolute paths.
                """)
            .inputType(ToolInputs.ReadMultipleFilesInput.class)
            .build();
    }
}
