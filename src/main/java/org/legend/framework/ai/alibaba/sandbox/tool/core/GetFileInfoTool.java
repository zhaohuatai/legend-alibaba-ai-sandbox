package org.legend.framework.ai.alibaba.sandbox.tool.core;

import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.tool.ToolInputs;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

/**
 * 获取文件信息工具，用于查看文件或目录的元数据信息。
 *
 * <p>该工具返回文件或目录的大小、修改时间、类型等信息。
 *
 * @see ToolInputs.GetFileInfoInput 获取文件信息工具的输入参数
 * @see SandboxBackend 沙箱后端接口
 */
public class GetFileInfoTool implements Function<ToolInputs.GetFileInfoInput, String> {

    /**
     * 沙箱后端实例，用于执行文件信息查询操作。
     */
    private final SandboxBackend backend;

    /**
     * 创建 GetFileInfoTool 实例。
     *
     * @param backend 沙箱后端实例
     */
    public GetFileInfoTool(SandboxBackend backend) { this.backend = backend; }

    /**
     * 获取文件或目录的信息。
     *
     * @param in 获取文件信息输入参数，包含文件/目录路径
     * @return 文件信息描述（大小、修改时间、类型等）
     */
    @Override
    public String apply(ToolInputs.GetFileInfoInput in) {
        return backend.getFileInfo(in.path());
    }

    /**
     * 创建获取文件信息工具的 Spring AI ToolCallback 实例。
     *
     * @param backend 沙箱后端实例
     * @return Spring AI ToolCallback 实例
     */
    public static ToolCallback create(SandboxBackend backend) {
        return FunctionToolCallback.builder("get_file_info", new GetFileInfoTool(backend))
            .description("""
                Gets metadata information about a file or directory.
                Returns file type, size, last modified time, and other details.
                Use absolute paths.
                """)
            .inputType(ToolInputs.GetFileInfoInput.class)
            .build();
    }
}
