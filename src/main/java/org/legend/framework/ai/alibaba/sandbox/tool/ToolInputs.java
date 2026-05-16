package org.legend.framework.ai.alibaba.sandbox.tool;

/**
 * 工具输入参数记录类集合，定义了 Claude Code 标准工具集的所有输入类型。
 *
 * <p>该类是 Claude Code 运行时契约的工具输入层抽象，为每个工具定义了类型安全的输入参数记录类。
 * 这些记录类用于 Spring AI 的工具调用框架，LLM 会根据工具描述生成对应的 JSON 参数，
 * Spring AI 会自动将 JSON 反序列化为这些记录类实例。
 *
 * <p>输入参数类型：
 * <ul>
 *   <li>{@link ReadInput} - 文件读取工具的输入参数</li>
 *   <li>{@link WriteInput} - 文件写入工具的输入参数</li>
 *   <li>{@link EditInput} - 文件编辑工具的输入参数</li>
 *   <li>{@link GrepInput} - Grep 搜索工具的输入参数</li>
 *   <li>{@link GlobInput} - Glob 模式匹配工具的输入参数</li>
 *   <li>{@link BashInput} - Bash 命令执行工具的输入参数</li>
 * </ul>
 */
public final class ToolInputs {

    /**
     * 私有构造函数，防止实例化。
     * 该类仅作为记录类的容器。
     */
    private ToolInputs() {}

    /**
     * Read 工具的输入参数。
     *
     * @param file_path 要读取的文件的绝对路径
     * @param offset 起始行号（从 1 开始），null 表示从第 1 行开始
     * @param limit 最大读取行数，null 表示读取到文件末尾
     */
    public record ReadInput(
        /** 要读取的文件的绝对路径 */
        String file_path,
        /** 起始行号（从 1 开始），null 表示从第 1 行开始 */
        Integer offset,
        /** 最大读取行数，null 表示读取到文件末尾 */
        Integer limit
    ) {}

    /**
     * Write 工具的输入参数。
     *
     * @param file_path 要写入的文件的绝对路径
     * @param content 要写入的文件内容
     */
    public record WriteInput(
        /** 要写入的文件的绝对路径 */
        String file_path,
        /** 要写入的文件内容（UTF-8 编码） */
        String content
    ) {}

    /**
     * Edit 工具的输入参数。
     *
     * @param file_path 要编辑的文件的绝对路径
     * @param old_string 要替换的旧字符串（必须精确匹配）
     * @param new_string 要替换的新字符串
     * @param replace_all 是否全局替换所有匹配项，null 或 false 表示仅替换第一次出现
     */
    public record EditInput(
        /** 要编辑的文件的绝对路径 */
        String file_path,
        /** 要替换的旧字符串（必须精确匹配，包括空白字符） */
        String old_string,
        /** 要替换的新字符串 */
        String new_string,
        /** 是否全局替换所有匹配项，null 或 false 表示仅替换第一次出现 */
        Boolean replace_all
    ) {}

    /**
     * Grep 工具的输入参数。
     *
     * @param pattern 要搜索的正则表达式模式
     * @param path 要搜索的文件或目录路径，null 表示在当前目录搜索
     * @param glob 文件模式过滤（如 "*.java"），null 表示不过滤
     * @param i 是否大小写不敏感，null 或 false 表示大小写敏感
     * @param n 是否显示行号，null 或 false 表示不显示行号
     * @param output_mode 输出模式："content"（默认）、"files_with_matches" 或 "count"
     */
    public record GrepInput(
        /** 要搜索的正则表达式模式 */
        String pattern,
        /** 要搜索的文件或目录路径，null 表示在当前目录搜索 */
        String path,
        /** 文件模式过滤（如 "*.java"），null 表示不过滤 */
        String glob,
        /** 是否大小写不敏感，null 或 false 表示大小写敏感 */
        Boolean i,
        /** 是否显示行号，null 或 false 表示不显示行号 */
        Boolean n,
        /** 输出模式："content"（默认）、"files_with_matches" 或 "count" */
        String output_mode
    ) {}

    /**
     * Glob 工具的输入参数。
     *
     * @param pattern Glob 模式（如 "**\/*.java"）
     * @param path 要搜索的根目录路径，null 表示在当前目录搜索
     */
    public record GlobInput(
        /** Glob 模式（如 "**\/*.java"、"src/**\/*.ts"） */
        String pattern,
        /** 要搜索的根目录路径，null 表示在当前目录搜索 */
        String path
    ) {}

    /**
     * Bash 工具的输入参数。
     *
     * @param command 要执行的 shell 命令字符串
     * @param timeout 命令执行超时时间（毫秒），null 表示使用默认超时（120 秒）
     * @param description 命令的描述，用于审计日志
     */
    public record BashInput(
        /** 要执行的 shell 命令字符串 */
        String command,
        /** 命令执行超时时间（毫秒），null 表示使用默认超时（120 秒） */
        Long timeout,
        /** 命令的描述，用于审计日志和调试 */
        String description
    ) {}

    /**
     * Python 工具的输入参数。
     *
     * @param code 要执行的 Python 代码字符串
     */
    public record PythonInput(
        /** 要执行的 Python 代码字符串 */
        String code
    ) {}

    /**
     * 批量读取文件工具的输入参数。
     *
     * @param file_paths 要读取的文件绝对路径列表
     */
    public record ReadMultipleFilesInput(
        /** 要读取的文件绝对路径列表 */
        String[] file_paths
    ) {}

    /**
     * 列出目录工具的输入参数。
     *
     * @param path 要列出的目录绝对路径
     */
    public record ListDirectoryInput(
        /** 要列出的目录绝对路径 */
        String path
    ) {}

    /**
     * 创建目录工具的输入参数。
     *
     * @param path 要创建的目录绝对路径
     */
    public record CreateDirectoryInput(
        /** 要创建的目录绝对路径 */
        String path
    ) {}

    /**
     * 移动文件工具的输入参数。
     *
     * @param source 源文件/目录的绝对路径
     * @param target 目标文件/目录的绝对路径
     */
    public record MoveFileInput(
        /** 源文件/目录的绝对路径 */
        String source,
        /** 目标文件/目录的绝对路径 */
        String target
    ) {}

    /**
     * 获取文件信息工具的输入参数。
     *
     * @param path 文件/目录的绝对路径
     */
    public record GetFileInfoInput(
        /** 文件/目录的绝对路径 */
        String path
    ) {}

    /**
     * 搜索文件工具的输入参数。
     *
     * @param file_name 要搜索的文件名（支持部分匹配）
     * @param path 搜索起始目录，null 表示从工作区根目录开始
     */
    public record SearchFilesInput(
        /** 要搜索的文件名（支持部分匹配） */
        String file_name,
        /** 搜索起始目录，null 表示从工作区根目录开始 */
        String path
    ) {}

    /**
     * 递归列出目录工具的输入参数。
     *
     * @param path 要递归列出的目录绝对路径
     * @param maxDepth 最大递归深度，null 表示默认 3 层
     * @param includeSize 是否包含文件大小，null 表示默认 true
     */
    public record ListDirectoryRecursiveInput(
        /** 要递归列出的目录绝对路径 */
        String path,
        /** 最大递归深度，null 表示默认 3 层 */
        Integer maxDepth,
        /** 是否包含文件大小，null 表示默认 true */
        Boolean includeSize
    ) {}

    /**
     * 批量获取文件信息工具的输入参数。
     *
     * @param paths 文件绝对路径列表（最多 100 个）
     */
    public record GetFilesInfoBatchInput(
        /** 文件绝对路径列表（最多 100 个） */
        String[] paths
    ) {}
}
