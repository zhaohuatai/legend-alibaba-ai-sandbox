package org.legend.framework.ai.alibaba.sandbox.tool.core;

import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.tool.ToolInputs;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

/**
 * 创建目录工具，用于在沙箱工作区中创建新目录。
 *
 * <p>该工具支持创建多级目录，如果父目录不存在会自动创建。
 *
 * @see ToolInputs2.CreateDirectoryInput 创建目录工具的输入参数
 * @see SandboxBackend 沙箱后端接口
 */
public class CreateDirectoryTool implements Function<ToolInputs.CreateDirectoryInput, String> {

    /**
     * 沙箱后端实例，用于执行目录创建操作。
     */
    private final SandboxBackend backend;

    /**
     * 创建 CreateDirectoryTool 实例。
     *
     * @param backend 沙箱后端实例
     */
    public CreateDirectoryTool(SandboxBackend backend) { this.backend = backend; }

    /**
     * 创建目录。
     *
     * @param in 创建目录输入参数，包含目录路径
     * @return 创建结果描述
     */
    @Override
    public String apply(ToolInputs.CreateDirectoryInput in) {
        backend.createDirectory(in.path());
        return "Directory created: " + in.path();
    }

    /**
     * 创建创建目录工具的 Spring AI ToolCallback 实例。
     *
     * @param backend 沙箱后端实例
     * @return Spring AI ToolCallback 实例
     */
    public static ToolCallback create(SandboxBackend backend) {
        return FunctionToolCallback.builder("create_directory", new CreateDirectoryTool(backend))
            .description("""
                Creates a new directory in the sandbox workspace.
                Parent directories are created automatically if they don't exist.
                Use absolute paths.
                """)
            .inputType(ToolInputs.CreateDirectoryInput.class)
            .build();
    }
}
