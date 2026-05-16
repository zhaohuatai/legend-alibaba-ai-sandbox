package org.legend.framework.ai.alibaba.sandbox.tool.core;

import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.tool.ToolInputs;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

/**
 * 搜索文件工具，用于在沙箱工作区中按文件名搜索文件。
 *
 * <p>该工具支持部分匹配搜索，可以指定搜索起始目录。
 *
 * @see ToolInputs.SearchFilesInput 搜索文件工具的输入参数
 * @see SandboxBackend 沙箱后端接口
 */
public class SearchFilesTool implements Function<ToolInputs.SearchFilesInput, String> {

    /**
     * 沙箱后端实例，用于执行文件搜索操作。
     */
    private final SandboxBackend backend;

    /**
     * 创建 SearchFilesTool 实例。
     *
     * @param backend 沙箱后端实例
     */
    public SearchFilesTool(SandboxBackend backend) { this.backend = backend; }

    /**
     * 搜索文件。
     *
     * @param in 搜索文件输入参数，包含文件名和搜索起始目录
     * @return 匹配的文件路径列表
     */
    @Override
    public String apply(ToolInputs.SearchFilesInput in) {
        var results = backend.searchFiles(in.file_name(), in.path());
        if (results.isEmpty()) {
            return "No files found matching: " + in.file_name();
        }
        return String.join("\n", results);
    }

    /**
     * 创建搜索文件工具的 Spring AI ToolCallback 实例。
     *
     * @param backend 沙箱后端实例
     * @return Spring AI ToolCallback 实例
     */
    public static ToolCallback create(SandboxBackend backend) {
        return FunctionToolCallback.builder("search_files", new SearchFilesTool(backend))
            .description("""
                Searches for files by name in the sandbox workspace.
                Supports partial name matching.
                If path is not specified, searches from the workspace root.
                Returns a list of matching file paths.
                """)
            .inputType(ToolInputs.SearchFilesInput.class)
            .build();
    }
}
