package org.legend.framework.ai.alibaba.sandbox.tool.core;

import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.tool.ToolInputs;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.nio.charset.StandardCharsets;
import java.util.function.Function;

/**
 * 文件编辑工具，用于在沙箱环境中执行精确的字符串替换。
 *
 * <p>该工具实现了 Claude Code 标准工具集中的 Edit 工具，支持以下特性：
 * <ul>
 *   <li><b>精确匹配</b>：old_string 必须完全匹配（包括空白字符）</li>
 *   <li><b>唯一性检查</b>：默认情况下 old_string 必须在文件中唯一出现</li>
 *   <li><b>全局替换</b>：支持 replace_all=true 进行全局替换</li>
 *   <li><b>UTF-8 编码</b>：使用 UTF-8 编码读写文件</li>
 * </ul>
 *
 * <p>编辑模式：
 * <ul>
 *   <li><b>单次替换</b>（默认）：old_string 必须唯一匹配，否则抛出异常</li>
 *   <li><b>全局替换</b>（replace_all=true）：替换所有匹配项，如果未找到则抛出异常</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 单次替换（old_string 必须唯一）
 * ToolInputs.EditInput input = new ToolInputs.EditInput(
 *     "/path/to/file.txt", "old text", "new text", false);
 * String result = editTool.apply(input);
 *
 * // 全局替换
 * ToolInputs.EditInput input = new ToolInputs.EditInput(
 *     "/path/to/file.txt", "old text", "new text", true);
 * String result = editTool.apply(input);
 * }</pre>
 *
 * @see ToolInputs.EditInput 编辑工具的输入参数
 * @see SandboxBackend 沙箱后端接口，提供文件读写的底层实现
 */
public class EditTool implements Function<ToolInputs.EditInput, String> {

    /**
     * 沙箱后端实例，用于执行文件读写操作。
     */
    private final SandboxBackend backend;

    /**
     * 创建 EditTool 实例。
     *
     * @param backend 沙箱后端实例，提供文件读写的底层实现
     */
    public EditTool(SandboxBackend backend) { this.backend = backend; }

    /**
     * 执行文件内容的精确字符串替换。
     *
     * <p>该方法执行以下操作：
     * <ol>
     *   <li>从沙箱后端读取文件的当前内容</li>
     *   <li>根据 replace_all 参数选择替换模式</li>
     *   <li>执行字符串替换并验证匹配</li>
     *   <li>将更新后的内容写回文件</li>
     *   <li>返回编辑结果描述</li>
     * </ol>
     *
     * <p>替换逻辑：
     * <ul>
     *   <li><b>全局替换</b>：使用 String.replace() 替换所有匹配项，如果内容未改变则抛出异常</li>
     *   <li><b>单次替换</b>：查找第一次出现位置，验证唯一性，执行替换</li>
     * </ul>
     *
     * @param in 编辑输入参数，包含文件路径、旧字符串、新字符串和是否全局替换
     * @return 编辑结果描述字符串
     * @throws IllegalArgumentException 如果 old_string 未找到或匹配多次（单次替换模式）
     */
    @Override
    public String apply(ToolInputs.EditInput in) {
        /** 读取文件的当前内容 */
        String content = new String(backend.readFile(in.file_path()), StandardCharsets.UTF_8);
        /** 判断是否启用全局替换模式 */
        boolean replaceAll = Boolean.TRUE.equals(in.replace_all());

        /** 替换后的内容 */
        String updated;
        if (replaceAll) {
            /** 全局替换：替换所有匹配项 */
            updated = content.replace(in.old_string(), in.new_string());
            /** 验证是否发生了替换 */
            if (updated.equals(content)) {
                throw new IllegalArgumentException("old_string not found: " + abbreviate(in.old_string()));
            }
        } else {
            /** 单次替换：查找第一次出现位置 */
            int first = content.indexOf(in.old_string());
            if (first < 0) {
                throw new IllegalArgumentException("old_string not found: " + abbreviate(in.old_string()));
            }
            /** 验证唯一性：查找第二次出现位置 */
            int second = content.indexOf(in.old_string(), first + in.old_string().length());
            if (second >= 0) {
                throw new IllegalArgumentException(
                    "old_string matched multiple times; provide more context or set replace_all=true");
            }
            /** 执行单次替换 */
            updated = content.substring(0, first) + in.new_string()
                    + content.substring(first + in.old_string().length());
        }
        /** 将更新后的内容写回文件 */
        backend.writeFile(in.file_path(), updated.getBytes(StandardCharsets.UTF_8));
        return "File edited: " + in.file_path();
    }

    /**
     * 截断长字符串用于错误消息显示。
     *
     * <p>如果字符串长度超过 80 个字符，则截断为前 77 个字符并添加 "..."。
     *
     * @param s 要截断的字符串
     * @return 截断后的字符串（最多 80 个字符）
     */
    private static String abbreviate(String s) {
        return s.length() <= 80 ? s : s.substring(0, 77) + "...";
    }

    /**
     * 创建 Edit 工具的 Spring AI ToolCallback 实例。
     *
     * <p>该方法创建一个 {@link ToolCallback} 实例，用于集成到 Spring AI 的工具调用框架中。
     * 工具描述详细说明了工具的功能、匹配规则和编辑前需要先读取文件的最佳实践。
     *
     * @param backend 沙箱后端实例
     * @return Spring AI ToolCallback 实例
     */
    public static ToolCallback create(SandboxBackend backend) {
        return FunctionToolCallback.builder("edit", new EditTool(backend))
            .description("""
                Performs a precise string replacement in a file.
                old_string must match EXACTLY (whitespace-sensitive) and uniquely,
                unless replace_all=true. Always Read the file before editing.
                """)
            .inputType(ToolInputs.EditInput.class)
            .build();
    }
}
