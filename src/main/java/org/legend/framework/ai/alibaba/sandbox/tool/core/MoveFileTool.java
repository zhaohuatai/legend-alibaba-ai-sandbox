package org.legend.framework.ai.alibaba.sandbox.tool.core;

import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.tool.ToolInputs;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

/**
 * 移动/重命名文件工具，用于在沙箱工作区中移动或重命名文件和目录。
 *
 * <p>该工具支持文件和目录的移动操作，如果目标路径的父目录不存在会自动创建。
 *
 * @see ToolInputs.MoveFileInput 移动文件工具的输入参数
 * @see SandboxBackend 沙箱后端接口
 */
public class MoveFileTool implements Function<ToolInputs.MoveFileInput, String> {

    /**
     * 沙箱后端实例，用于执行文件移动操作。
     */
    private final SandboxBackend backend;

    /**
     * 创建 MoveFileTool 实例。
     *
     * @param backend 沙箱后端实例
     */
    public MoveFileTool(SandboxBackend backend) { this.backend = backend; }

    /**
     * 移动或重命名文件/目录。
     *
     * @param in 移动文件输入参数，包含源路径和目标路径
     * @return 操作结果描述
     */
    @Override
    public String apply(ToolInputs.MoveFileInput in) {
        backend.moveFile(in.source(), in.target());
        return "Moved: " + in.source() + " -> " + in.target();
    }

    /**
     * 创建移动文件工具的 Spring AI ToolCallback 实例。
     *
     * @param backend 沙箱后端实例
     * @return Spring AI ToolCallback 实例
     */
    public static ToolCallback create(SandboxBackend backend) {
        return FunctionToolCallback.builder("move_file", new MoveFileTool(backend))
            .description("""
                Moves or renames a file or directory in the sandbox workspace.
                Parent directories of the target path are created automatically if needed.
                Use absolute paths for both source and target.
                """)
            .inputType(ToolInputs.MoveFileInput.class)
            .build();
    }
}
