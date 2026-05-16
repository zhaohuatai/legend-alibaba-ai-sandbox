package org.legend.framework.ai.alibaba.sandbox.tool.core;

import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.tool.ToolInputs;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

/**
 * 递归列出目录工具，用于一次性获取整个目录树的结构和文件信息。
 *
 * <p>该工具支持递归遍历目录，返回所有文件和子目录的列表，
 * 可选包含文件大小信息，避免 Agent 多次调用 list_directory 和 get_file_info。
 *
 * @see ToolInputs.ListDirectoryRecursiveInput 递归列出目录工具的输入参数
 * @see SandboxBackend 沙箱后端接口
 */
public class ListDirectoryRecursiveTool implements Function<ToolInputs.ListDirectoryRecursiveInput, String> {

    /**
     * 沙箱后端实例，用于执行目录遍历操作。
     */
    private final SandboxBackend backend;

    /**
     * 创建 ListDirectoryRecursiveTool 实例。
     *
     * @param backend 沙箱后端实例
     */
    public ListDirectoryRecursiveTool(SandboxBackend backend) {
        this.backend = backend;
    }

    /**
     * 递归列出目录内容。
     *
     * @param in 递归列出目录输入参数，包含路径、最大深度、是否包含大小
     * @return 目录树内容列表，每项包含类型和可选的大小信息
     */
    @Override
    public String apply(ToolInputs.ListDirectoryRecursiveInput in) {
        String path = in.path();
        int maxDepth = in.maxDepth() != null ? in.maxDepth() : 3;
        boolean includeSize = in.includeSize() != null ? in.includeSize() : true;

        try {
            var entries = backend.listDirectoryRecursive(path, maxDepth, includeSize);
            if (entries.isEmpty()) {
                return "Directory is empty: " + path;
            }
            return String.join("\n", entries);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    /**
     * 创建递归列出目录工具的 Spring AI ToolCallback 实例。
     *
     * @param backend 沙箱后端实例
     * @return Spring AI ToolCallback 实例
     */
    public static ToolCallback create(SandboxBackend backend) {
        return FunctionToolCallback.builder("list_directory_recursive", new ListDirectoryRecursiveTool(backend))
            .description("""
                Recursively lists all files and directories in a directory tree.
                Returns a flat list with [DIR] or [FILE] prefix, optionally including file sizes.
                Supports maxDepth parameter to limit recursion depth (default: 3).
                Use absolute paths.
                """)
            .inputType(ToolInputs.ListDirectoryRecursiveInput.class)
            .build();
    }
}
