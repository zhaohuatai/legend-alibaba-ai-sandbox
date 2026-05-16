package org.legend.framework.ai.alibaba.sandbox.tool.core;

import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.tool.ToolInputs;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

/**
 * 批量获取文件信息工具，用于一次性获取多个文件的详细信息。
 *
 * <p>该工具接受文件路径列表，返回每个文件的大小、修改时间等信息，
 * 避免 Agent 多次调用 get_file_info 工具。
 *
 * @see ToolInputs.GetFilesInfoBatchInput 批量获取文件信息工具的输入参数
 * @see SandboxBackend 沙箱后端接口
 */
public class GetFilesInfoBatchTool implements Function<ToolInputs.GetFilesInfoBatchInput, String> {

    /**
     * 沙箱后端实例，用于执行文件信息获取操作。
     */
    private final SandboxBackend backend;

    /**
     * 创建 GetFilesInfoBatchTool 实例。
     *
     * @param backend 沙箱后端实例
     */
    public GetFilesInfoBatchTool(SandboxBackend backend) {
        this.backend = backend;
    }

    /**
     * 批量获取文件信息。
     *
     * @param in 批量获取文件信息输入参数，包含文件路径列表
     * @return 每个文件的信息描述，以分隔符分隔
     */
    @Override
    public String apply(ToolInputs.GetFilesInfoBatchInput in) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(in.paths().length, 100);
        for (int i = 0; i < limit; i++) {
            String path = in.paths()[i];
            try {
                String info = backend.getFileInfo(path);
                sb.append(info).append("\n");
            } catch (Exception e) {
                sb.append("Error: ").append(path).append(" - ").append(e.getMessage()).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 创建批量获取文件信息工具的 Spring AI ToolCallback 实例。
     *
     * @param backend 沙箱后端实例
     * @return Spring AI ToolCallback 实例
     */
    public static ToolCallback create(SandboxBackend backend) {
        return FunctionToolCallback.builder("get_files_info_batch", new GetFilesInfoBatchTool(backend))
            .description("""
                Gets detailed information for multiple files at once (max 100 files).
                Returns file size, last modified time, and type for each file.
                If a file cannot be accessed, an error message is returned for that file.
                Use absolute paths.
                """)
            .inputType(ToolInputs.GetFilesInfoBatchInput.class)
            .build();
    }
}
