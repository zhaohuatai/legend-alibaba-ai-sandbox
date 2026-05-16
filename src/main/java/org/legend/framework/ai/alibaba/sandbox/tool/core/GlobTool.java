package org.legend.framework.ai.alibaba.sandbox.tool.core;

import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.tool.ToolInputs;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.function.Function;

/**
 * 文件模式匹配工具，用于在沙箱环境中执行 glob 模式文件路径搜索。
 *
 * <p>该工具实现了 Claude Code 标准工具集中的 Glob 工具，支持以下特性：
 * <ul>
 *   <li><b>模式匹配</b>：支持标准 glob 模式，如 {@code **\/*.java}、{@code src\/**\/*.ts}</li>
 *   <li><b>递归搜索</b>：{@code **} 表示递归搜索所有子目录</li>
 *   <li><b>排序输出</b>：返回的文件路径按修改时间排序</li>
 *   <li><b>绝对路径</b>：返回的文件路径为绝对路径</li>
 * </ul>
 *
 * <p>支持的 glob 模式：
 * <ul>
 *   <li>{@code *} - 匹配任意数量的任意字符（不包括路径分隔符）</li>
 *   <li>{@code **} - 匹配任意数量的任意字符（包括路径分隔符，即递归搜索）</li>
 *   <li>{@code ?} - 匹配单个字符</li>
 *   <li>{@code [abc]} - 匹配括号内的任意一个字符</li>
 *   <li>{@code [!abc]} - 匹配不在括号内的任意一个字符</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 搜索所有 Java 文件
 * ToolInputs.GlobInput input = new ToolInputs.GlobInput("**\/*.java", null);
 * String result = globTool.apply(input);
 *
 * // 搜索 src 目录下的 TypeScript 文件
 * ToolInputs.GlobInput input = new ToolInputs.GlobInput("src\/**\/*.ts", null);
 * String result = globTool.apply(input);
 * }</pre>
 *
 * @see ToolInputs.GlobInput Glob 工具的输入参数
 * @see SandboxBackend 沙箱后端接口，提供文件列表查询的底层实现
 */
public class GlobTool implements Function<ToolInputs.GlobInput, String> {

    /**
     * 沙箱后端实例，用于执行文件列表查询操作。
     */
    private final SandboxBackend backend;

    /**
     * 创建 GlobTool 实例。
     *
     * @param backend 沙箱后端实例，提供文件列表查询的底层实现
     */
    public GlobTool(SandboxBackend backend) { this.backend = backend; }

    /**
     * 执行 glob 模式文件路径搜索。
     *
     * <p>该方法执行以下操作：
     * <ol>
     *   <li>调用沙箱后端的 listFiles 方法，根据 glob 模式匹配文件</li>
     *   <li>如果无匹配结果，返回提示信息 "(no matches)"</li>
     *   <li>如果有匹配结果，将文件路径列表按换行符连接为字符串返回</li>
     * </ol>
     *
     * @param in Glob 输入参数，包含 glob 模式和可选的搜索路径
     * @return 匹配的文件路径列表文本（每行一个路径）或提示信息
     */
    @Override
    public String apply(ToolInputs.GlobInput in) {
        /** 调用沙箱后端执行 glob 模式匹配，返回按修改时间排序的文件路径列表 */
        List<String> matches = backend.listFiles(in.pattern());
        /** 如果无匹配结果返回提示信息，否则返回文件路径列表 */
        return matches.isEmpty() ? "(no matches)" : String.join("\n", matches);
    }

    /**
     * 创建 Glob 工具的 Spring AI ToolCallback 实例。
     *
     * <p>该方法创建一个 {@link ToolCallback} 实例，用于集成到 Spring AI 的工具调用框架中。
     * 工具描述详细说明了工具的功能、支持的模式格式和排序特性。
     *
     * @param backend 沙箱后端实例
     * @return Spring AI ToolCallback 实例
     */
    public static ToolCallback create(SandboxBackend backend) {
        return FunctionToolCallback.builder("glob", new GlobTool(backend))
            .description("""
                Fast file pattern matching, supports patterns like `**/*.java`.
                Returns matching file paths sorted by modification time.
                """)
            .inputType(ToolInputs.GlobInput.class)
            .build();
    }
}
