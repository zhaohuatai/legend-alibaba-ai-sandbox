package org.legend.framework.ai.alibaba.sandbox.tool.core;

import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.tool.ToolInputs;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * 文件写入工具，用于在沙箱环境中创建或覆盖文件。
 *
 * <p>该工具实现了 Claude Code 标准工具集中的 Write 工具，支持以下特性：
 * <ul>
 *   <li><b>创建/覆盖</b>：如果文件不存在则创建，如果存在则覆盖全部内容</li>
 *   <li><b>自动创建目录</b>：如果父目录不存在，自动创建所有必需的父目录</li>
 *   <li><b>UTF-8 编码</b>：使用 UTF-8 编码写入文件内容</li>
 *   <li><b>绝对路径</b>：文件路径必须是绝对路径</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 创建新文件或覆盖现有文件
 * ToolInputs.WriteInput input = new ToolInputs.WriteInput("/path/to/file.txt", "Hello, World!");
 * String result = writeTool.apply(input);
 * // 返回: "File written: /path/to/file.txt (13 chars)"
 * }</pre>
 *
 * @see ToolInputs.WriteInput 写入工具的输入参数
 * @see SandboxBackend 沙箱后端接口，提供文件写入的底层实现
 */
public class WriteTool implements Function<ToolInputs.WriteInput, String> {

    /**
     * 沙箱后端实例，用于执行文件写入操作。
     */
    private final SandboxBackend backend;

    /**
     * 创建 WriteTool 实例。
     *
     * @param backend 沙箱后端实例，提供文件写入的底层实现
     */
    public WriteTool(SandboxBackend backend) { this.backend = backend; }

    /**
     * 将内容写入文件。
     *
     * <p>该方法执行以下操作：
     * <ol>
     *   <li>调用沙箱后端将内容写入指定文件（自动创建父目录）</li>
     *   <li>返回写入结果，包含文件路径和字符数</li>
     * </ol>
     *
     * @param in 写入输入参数，包含文件路径和要写入的内容
     * @return 写入结果描述字符串
     */
    @Override
    public String apply(ToolInputs.WriteInput in) {
        /** 将内容按 UTF-8 编码写入文件 */
        backend.writeFile(in.file_path(), in.content().getBytes(StandardCharsets.UTF_8));
        /** 返回写入结果，包含文件路径和字符数 */
        return "File written: " + in.file_path() + " (" + in.content().length() + " chars)";
    }

    /**
     * 创建 Write 工具的 Spring AI ToolCallback 实例。
     *
     * <p>该方法创建一个 {@link ToolCallback} 实例，用于集成到 Spring AI 的工具调用框架中。
     * 工具描述详细说明了工具的功能、自动创建目录特性和路径要求。
     *
     * @param backend 沙箱后端实例
     * @return Spring AI ToolCallback 实例
     */
    public static ToolCallback create(SandboxBackend backend) {
        return FunctionToolCallback.builder("write", new WriteTool(backend))
            .description("""
                Creates or overwrites a file with the given content.
                Parent directories are created automatically.
                File paths must be absolute.
                """)
            .inputType(ToolInputs.WriteInput.class)
            .build();
    }
}
