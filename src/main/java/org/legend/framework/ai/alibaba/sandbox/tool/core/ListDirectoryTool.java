package org.legend.framework.ai.alibaba.sandbox.tool.core;

import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.tool.ToolInputs;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

/**
 * 列出目录工具，用于查看指定目录下的文件和子目录。
 *
 * <p>该工具返回目录中所有条目的列表，包括文件名和类型标识（[DIR] 或 [FILE]）。
 *
 * @see ToolInputs.ListDirectoryInput 列出目录工具的输入参数
 * @see SandboxBackend 沙箱后端接口
 */
public class ListDirectoryTool implements Function<ToolInputs.ListDirectoryInput, String> {

    /**
     * 沙箱后端实例，用于执行目录列表操作。
     */
    private final SandboxBackend backend;

    /**
     * 创建 ListDirectoryTool 实例。
     *
     * @param backend 沙箱后端实例
     */
    public ListDirectoryTool(SandboxBackend backend) { this.backend = backend; }

    /**
     * 列出目录内容。
     *
     * @param in 列出目录输入参数，包含目录路径
     * @return 目录内容列表，每项包含名称和类型
     */
    @Override
    public String apply(ToolInputs.ListDirectoryInput in) {
        var entries = backend.listDirectory(in.path());
        if (entries.isEmpty()) {
            return "Directory is empty: " + in.path();
        }
        return String.join("\n", entries);
    }

    /**
     * 创建列出目录工具的 Spring AI ToolCallback 实例。
     *
     * @param backend 沙箱后端实例
     * @return Spring AI ToolCallback 实例
     */
    public static ToolCallback create(SandboxBackend backend) {
        return FunctionToolCallback.builder("list_directory", new ListDirectoryTool(backend))
            .description("""
                Lists the contents of a directory, showing files and subdirectories.
                Each entry is prefixed with [DIR] or [FILE] to indicate the type.
                Use absolute paths.
                """)
            .inputType(ToolInputs.ListDirectoryInput.class)
            .build();
    }
}
