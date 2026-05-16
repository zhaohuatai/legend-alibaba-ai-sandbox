//package org.legend.framework.ai.alibaba.skill.guard;
//
//import org.legend.framework.ai.alibaba.skill.guard.env.EnvContext;
//import org.legend.framework.ai.alibaba.skill.guard.sandbox.SandboxBackend;
//import org.legend.framework.ai.alibaba.skill.guard.sandbox.SandboxBackendProvider;
//import org.legend.framework.ai.alibaba.skill.guard.tool.*;
//import org.legend.framework.ai.alibaba.skill.guard.tool.core.ShellTool;
//import org.legend.framework.ai.alibaba.skill.guard.tool.core.CreateDirectoryTool;
//import org.legend.framework.ai.alibaba.skill.guard.tool.core.EditTool;
//import org.legend.framework.ai.alibaba.skill.guard.tool.core.GetFileInfoTool;
//import org.legend.framework.ai.alibaba.skill.guard.tool.core.GetFilesInfoBatchTool;
//import org.legend.framework.ai.alibaba.skill.guard.tool.core.GlobTool;
//import org.legend.framework.ai.alibaba.skill.guard.tool.core.GrepTool;
//import org.legend.framework.ai.alibaba.skill.guard.tool.core.ListDirectoryTool;
//import org.legend.framework.ai.alibaba.skill.guard.tool.core.ListDirectoryRecursiveTool;
//import org.legend.framework.ai.alibaba.skill.guard.tool.core.MoveFileTool;
//import org.legend.framework.ai.alibaba.skill.guard.tool.core.ReadMultipleFilesTool;
//import org.legend.framework.ai.alibaba.skill.guard.tool.core.ReadTool;
//import org.legend.framework.ai.alibaba.skill.guard.tool.core.SearchFilesTool;
//import org.legend.framework.ai.alibaba.skill.guard.tool.core.WriteTool;
//import org.legend.framework.ai.alibaba.skill.guard.tool.python.PythonTool;
//import org.springframework.ai.tool.ToolCallback;
//
//import java.util.ArrayList;
//import java.util.List;
//
///**
// * 沙箱感知工具集工厂，创建绑定到动态沙箱后端的工具实例。
// *
// * <p>该类是 Skill 运行时工具层核心组件，负责创建对标 AgentScope 的 12 个标准工具，
// * 所有工具通过 {@link DynamicBackendAdapter} 动态获取沙箱后端。
// *
// * <p>核心设计理念：
// * <ul>
// *   <li><b>动态沙箱绑定</b> - 工具在构建时绑定到特定 Skill，通过 GuardedSkillMetadata 获取风险等级</li>
// *   <li><b>风险等级驱动</b> - 根据 Skill 的 risk-level 自动选择合适的沙箱后端（Local 或 Docker）</li>
// *   <li><b>安全门控集成</b> - 创建的工具可以与 {@link GuardedToolCallback} 配合使用，提供完整的安全检查</li>
// * </ul>
// *
// * <p>创建的工具（对标 AgentScope）：
// * <ol>
// *   <li><b>Read</b> - 文件读取工具，从沙箱工作区读取文件内容</li>
// *   <li><b>Write</b> - 文件写入工具，向沙箱工作区创建或覆盖文件</li>
// *   <li><b>Edit</b> - 文件编辑工具，在沙箱工作区执行精确的字符串替换</li>
// *   <li><b>Grep</b> - 正则表达式搜索工具，在沙箱工作区搜索文件内容</li>
// *   <li><b>Glob</b> - 文件模式匹配工具，在沙箱工作区搜索匹配 glob 模式的文件</li>
// *   <li><b>Bash</b> - 命令执行工具，在沙箱环境中执行 shell 命令</li>
// *   <li><b>ReadMultipleFiles</b> - 批量读取多个文件内容</li>
// *   <li><b>ListDirectory</b> - 列出目录内容</li>
// *   <li><b>CreateDirectory</b> - 创建新目录</li>
// *   <li><b>MoveFile</b> - 移动/重命名文件或目录</li>
// *   <li><b>SearchFiles</b> - 按文件名搜索文件</li>
// *   <li><b>GetFileInfo</b> - 获取文件/目录详细信息</li>
// * </ol>
// *
// * <p>使用示例：
// * <pre>{@code
// * GuardedSkillMetadata skill = ...;
// * List<ToolCallback> tools = SandboxAwareToolset.create(provider, env, skill);
// * List<ToolCallback> guarded = tools.stream()
// *     .map(t -> new GuardedToolCallback(t, skill))
// *     .toList();
// * }</pre>
// *
// * @see SandboxBackendProvider 沙箱后端提供者
// * @see GuardedSkillMetadata 带安全门控的 Skill 元数据
// * @see GuardedToolCallback 门控工具回调
// * @see DynamicBackendAdapter 动态沙箱后端适配器
// */
//public final class SandboxAwareToolset {
//
//    private SandboxAwareToolset() {}
//
//    /**
//     * 创建完整的沙箱感知工具集。
//     *
//     * <p>该方法根据传入的沙箱后端提供者、环境上下文和 Skill 元数据，
//     * 创建 12 个标准工具回调实例。这些工具可用于文件操作、目录管理、
//     * 内容搜索和命令执行等场景。
//     *
//     * @param provider 沙箱后端提供者，用于根据 Skill 风险等级选择沙箱
//     * @param env 环境上下文，包含操作系统、Shell 类型、可用可执行文件等信息
//     * @param skillMetadata 带安全门控的 Skill 元数据
//     * @return 包含 12 个标准工具的不可变列表
//     */
//    public static List<ToolCallback> create(
//        SandboxBackendProvider provider,
//        EnvContext env,
//        GuardedSkillMetadata skillMetadata
//    ) {
//        List<ToolCallback> tools = new ArrayList<>();
//
//        tools.add(createReadTool(provider, skillMetadata));
//        tools.add(createWriteTool(provider, skillMetadata));
//        tools.add(createEditTool(provider, skillMetadata));
//        tools.add(createGrepTool(provider, skillMetadata));
//        tools.add(createGlobTool(provider, skillMetadata));
//        tools.add(createBashTool(provider, env, skillMetadata));
//        
//       tools.add(createReadMultipleFilesTool(provider, skillMetadata));
//       tools.add(createListDirectoryTool(provider, skillMetadata));
//       tools.add(createListDirectoryRecursiveTool(provider, skillMetadata));
//       tools.add(createCreateDirectoryTool(provider, skillMetadata));
//       tools.add(createMoveFileTool(provider, skillMetadata));
//       tools.add(createSearchFilesTool(provider, skillMetadata));
//       tools.add(createGetFileInfoTool(provider, skillMetadata));
//        tools.add(createGetFilesInfoBatchTool(provider, skillMetadata));
//       tools.add(createPythonTool(provider, env, skillMetadata));
//
//        return List.copyOf(tools);
//    }
//
//    /**
//     * 使用预创建的 SandboxBackend 创建工具集。
//     *
//     * <p>与 {@link #create(SandboxBackendProvider, EnvContext, GuardedSkillMetadata)} 不同，
//     * 该方法直接使用传入的 backend，不会内部创建 DynamicBackendAdapter。
//     * 适用于外部已缓存 DynamicBackendAdapter 的场景，确保所有工具共享同一个后端实例。
//     *
//     * @param backend 预创建的沙箱后端实例
//     * @param env 环境上下文
//     * @param skillMetadata Skill 元数据
//     * @return 包含标准工具的不可变列表
//     */
//    public static List<ToolCallback> create(
//        SandboxBackend backend,
//        EnvContext env,
//        GuardedSkillMetadata skillMetadata
//    ) {
//        List<ToolCallback> tools = new ArrayList<>();
//
//        tools.add(ReadTool.create(backend));
//        tools.add(WriteTool.create(backend));
//        tools.add(EditTool.create(backend));
//        tools.add(GrepTool.create(backend));
//        tools.add(GlobTool.create(backend));
//        tools.add(ShellTool.create(backend, env));
//
//        tools.add(ReadMultipleFilesTool.create(backend));
//        tools.add(ListDirectoryTool.create(backend));
//        tools.add(ListDirectoryRecursiveTool.create(backend));
//        tools.add(CreateDirectoryTool.create(backend));
//        tools.add(MoveFileTool.create(backend));
//        tools.add(SearchFilesTool.create(backend));
//        tools.add(GetFileInfoTool.create(backend));
//        tools.add(GetFilesInfoBatchTool.create(backend));
//        tools.add(PythonTool.create(backend, env));
//
//        return List.copyOf(tools);
//    }
//
//    /**
//     * 创建文件读取工具。
//     *
//     * <p>从沙箱工作区读取文件内容，支持行号显示和分页读取。
//     *
//     * @param provider 沙箱后端提供者
//     * @param skillMetadata Skill 元数据
//     * @return Read 工具回调
//     */
//    private static ToolCallback createReadTool(SandboxBackendProvider provider, GuardedSkillMetadata skillMetadata) {
//        return ReadTool.create(new DynamicBackendAdapter(provider, skillMetadata));
//    }
//
//    /**
//     * 创建文件写入工具。
//     *
//     * <p>向沙箱工作区创建或覆盖文件，自动创建父目录。
//     *
//     * @param provider 沙箱后端提供者
//     * @param skillMetadata Skill 元数据
//     * @return Write 工具回调
//     */
//    private static ToolCallback createWriteTool(SandboxBackendProvider provider, GuardedSkillMetadata skillMetadata) {
//        return WriteTool.create(new DynamicBackendAdapter(provider, skillMetadata));
//    }
//
//    /**
//     * 创建文件编辑工具。
//     *
//     * <p>在沙箱工作区执行精确的字符串替换，支持全局替换。
//     *
//     * @param provider 沙箱后端提供者
//     * @param skillMetadata Skill 元数据
//     * @return Edit 工具回调
//     */
//    private static ToolCallback createEditTool(SandboxBackendProvider provider, GuardedSkillMetadata skillMetadata) {
//        return EditTool.create(new DynamicBackendAdapter(provider, skillMetadata));
//    }
//
//    /**
//     * 创建正则表达式搜索工具。
//     *
//     * <p>在沙箱工作区搜索文件内容，支持大小写敏感、行号显示等选项。
//     *
//     * @param provider 沙箱后端提供者
//     * @param skillMetadata Skill 元数据
//     * @return Grep 工具回调
//     */
//    private static ToolCallback createGrepTool(SandboxBackendProvider provider, GuardedSkillMetadata skillMetadata) {
//        return GrepTool.create(new DynamicBackendAdapter(provider, skillMetadata));
//    }
//
//    /**
//     * 创建文件模式匹配工具。
//     *
//     * <p>在沙箱工作区搜索匹配 glob 模式的文件路径。
//     *
//     * @param provider 沙箱后端提供者
//     * @param skillMetadata Skill 元数据
//     * @return Glob 工具回调
//     */
//    private static ToolCallback createGlobTool(SandboxBackendProvider provider, GuardedSkillMetadata skillMetadata) {
//        return GlobTool.create(new DynamicBackendAdapter(provider, skillMetadata));
//    }
//
//    /**
//     * 创建命令执行工具。
//     *
//     * <p>在沙箱环境中执行 shell 命令，支持超时控制和环境变量注入。
//     *
//     * @param provider 沙箱后端提供者
//     * @param env 环境上下文
//     * @param skillMetadata Skill 元数据
//     * @return Bash 工具回调
//     */
//    private static ToolCallback createBashTool(
//        SandboxBackendProvider provider,
//        EnvContext env,
//        GuardedSkillMetadata skillMetadata
//    ) {
//        return ShellTool.create(new DynamicBackendAdapter(provider, skillMetadata), env);
//    }
//
//    /**
//     * 创建批量文件读取工具。
//     *
//     * <p>一次性读取多个文件内容，适用于需要同时处理多个文件的场景。
//     *
//     * @param provider 沙箱后端提供者
//     * @param skillMetadata Skill 元数据
//     * @return ReadMultipleFiles 工具回调
//     */
//    private static ToolCallback createReadMultipleFilesTool(SandboxBackendProvider provider, GuardedSkillMetadata skillMetadata) {
//        return ReadMultipleFilesTool.create(new DynamicBackendAdapter(provider, skillMetadata));
//    }
//
//    /**
//     * 创建目录列表工具。
//     *
//     * <p>列出指定目录下的文件和子目录信息。
//     *
//     * @param provider 沙箱后端提供者
//     * @param skillMetadata Skill 元数据
//     * @return ListDirectory 工具回调
//     */
//    private static ToolCallback createListDirectoryTool(SandboxBackendProvider provider, GuardedSkillMetadata skillMetadata) {
//        return ListDirectoryTool.create(new DynamicBackendAdapter(provider, skillMetadata));
//    }
//
//    /**
//     * 创建目录创建工具。
//     *
//     * <p>在沙箱工作区创建新目录，支持递归创建多级目录。
//     *
//     * @param provider 沙箱后端提供者
//     * @param skillMetadata Skill 元数据
//     * @return CreateDirectory 工具回调
//     */
//    private static ToolCallback createCreateDirectoryTool(SandboxBackendProvider provider, GuardedSkillMetadata skillMetadata) {
//        return CreateDirectoryTool.create(new DynamicBackendAdapter(provider, skillMetadata));
//    }
//
//    /**
//     * 创建文件移动工具。
//     *
//     * <p>移动或重命名沙箱工作区中的文件/目录。
//     *
//     * @param provider 沙箱后端提供者
//     * @param skillMetadata Skill 元数据
//     * @return MoveFile 工具回调
//     */
//    private static ToolCallback createMoveFileTool(SandboxBackendProvider provider, GuardedSkillMetadata skillMetadata) {
//        return MoveFileTool.create(new DynamicBackendAdapter(provider, skillMetadata));
//    }
//
//    /**
//     * 创建文件搜索工具。
//     *
//     * <p>按文件名在沙箱工作区中搜索匹配的文件路径。
//     *
//     * @param provider 沙箱后端提供者
//     * @param skillMetadata Skill 元数据
//     * @return SearchFiles 工具回调
//     */
//    private static ToolCallback createSearchFilesTool(SandboxBackendProvider provider, GuardedSkillMetadata skillMetadata) {
//        return SearchFilesTool.create(new DynamicBackendAdapter(provider, skillMetadata));
//    }
//
//    /**
//     * 创建文件信息获取工具。
//     *
//     * <p>获取沙箱工作区中文件/目录的详细信息（大小、修改时间、类型等）。
//     *
//     * @param provider 沙箱后端提供者
//     * @param skillMetadata Skill 元数据
//     * @return GetFileInfo 工具回调
//     */
//    private static ToolCallback createGetFileInfoTool(SandboxBackendProvider provider, GuardedSkillMetadata skillMetadata) {
//        return GetFileInfoTool.create(new DynamicBackendAdapter(provider, skillMetadata));
//    }
//
//    /**
//     * 创建递归列出目录工具。
//     *
//     * <p>一次性获取整个目录树的结构和文件信息，支持深度限制和大小显示。
//     *
//     * @param provider 沙箱后端提供者
//     * @param skillMetadata Skill 元数据
//     * @return ListDirectoryRecursive 工具回调
//     */
//    private static ToolCallback createListDirectoryRecursiveTool(SandboxBackendProvider provider, GuardedSkillMetadata skillMetadata) {
//        return ListDirectoryRecursiveTool.create(new DynamicBackendAdapter(provider, skillMetadata));
//    }
//
//    /**
//     * 创建批量获取文件信息工具。
//     *
//     * <p>一次性获取多个文件的详细信息（最多 100 个），避免多次调用 get_file_info。
//     *
//     * @param provider 沙箱后端提供者
//     * @param skillMetadata Skill 元数据
//     * @return GetFilesInfoBatch 工具回调
//     */
//    private static ToolCallback createGetFilesInfoBatchTool(SandboxBackendProvider provider, GuardedSkillMetadata skillMetadata) {
//        return GetFilesInfoBatchTool.create(new DynamicBackendAdapter(provider, skillMetadata));
//    }
//
//    /**
//     * 创建 Python 代码执行工具。
//     *
//     * <p>在沙箱环境中执行 Python 代码，适用于数据分析、数学计算等场景。
//     * 相比 Bash 工具执行 python -c，该工具自动处理跨平台 Python 解释器检测和引号转义。
//     *
//     * @param provider 沙箱后端提供者
//     * @param env 环境上下文
//     * @param skillMetadata Skill 元数据
//     * @return Python 工具回调
//     */
//    private static ToolCallback createPythonTool(
//        SandboxBackendProvider provider,
//        EnvContext env,
//        GuardedSkillMetadata skillMetadata
//    ) {
//        return PythonTool.create(new DynamicBackendAdapter(provider, skillMetadata), env);
//    }
//}




