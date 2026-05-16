package org.legend.framework.ai.alibaba.sandbox.backend;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * 沙箱后端接口，定义 Skill 执行环境的统一操作契约。
 *
 * <p>该接口是 Claude Code 运行时契约的沙箱隔离层抽象，所有沙箱实现（本地进程、Docker 容器、
 * Firecracker 微虚拟机等）都必须实现此接口，以便 {@link org.legend.framework.ai.alibaba.skill.guard.tool.ClaudeToolset}
 * 中的工具可以透明地在不同沙箱后端之间切换。
 *
 * <p>沙箱后端提供以下核心能力：
 * <ul>
 *   <li>命令执行（exec）：在隔离环境中执行 shell 命令</li>
 *   <li>文件读取（readFile）：从沙箱工作区读取文件内容</li>
 *   <li>文件写入（writeFile）：向沙箱工作区写入文件内容</li>
 *   <li>文件列表（listFiles）：列出沙箱工作区中匹配 glob 模式的文件</li>
 *   <li>工作区根路径（workspaceRoot）：获取沙箱工作区的根目录路径</li>
 * </ul>
 *
 * <p>实现类示例：
 * <ul>
 *   <li>{@link org.legend.framework.ai.alibaba.sandbox.backend.local.LocalProcessSandbox} - 本地进程沙箱（开发环境）</li>
 *   <li>{@link org.legend.framework.ai.alibaba.skill.guard.sandbox.docker.backup.DockerSession} - Docker 容器沙箱（生产环境）</li>
 * </ul>
 *
 * @see org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackendProvider 沙箱后端提供者，按风险等级选择后端
 * @see org.legend.framework.ai.alibaba.sandbox.backend.local.LocalProcessSandbox 本地进程实现
 * @see org.legend.framework.ai.alibaba.skill.guard.sandbox.docker.backup.DockerSession Docker 容器实现
 */
public interface SandboxBackend extends AutoCloseable{

    /**
     * 在沙箱环境中执行 shell 命令。
     *
     * <p>该方法在隔离的沙箱中执行指定的命令，并返回执行结果（退出码、标准输出、标准错误、执行耗时）。
     * 如果命令执行超时，沙箱实现应该终止进程并返回超时错误。
     *
     * <p>使用示例：
     * <pre>{@code
     * SandboxBackend sandbox = ...;
     * ExecResult result = sandbox.exec("ls -la", Duration.ofSeconds(30), Map.of());
     * if (result.ok()) {
     *     System.out.println(result.stdout());
     * } else {
     *     System.err.println(result.stderr());
     * }
     * }</pre>
     *
     * @param command 要执行的 shell 命令字符串（如 "ls -la"、"python3 script.py"）
     * @param timeout 命令执行的超时时间，超时后沙箱应终止进程
     * @param env     额外的环境变量，将与沙箱默认环境变量合并（键为变量名，值为变量值）
     * @return 执行结果记录，包含退出码、标准输出、标准错误和执行耗时
     */
    ExecResult exec(String command, Duration timeout, Map<String, String> env);

    /**
     * 从沙箱工作区读取文件内容。
     *
     * <p>该方法从沙箱的工作区中读取指定路径的文件，返回文件的原始字节数组。
     * 路径可以是绝对路径或相对于工作区根目录的相对路径。
     *
     * <p>使用示例：
     * <pre>{@code
     * SandboxBackend sandbox = ...;
     * byte[] content = sandbox.readFile("output.txt");
     * String text = new String(content, StandardCharsets.UTF_8);
     * }</pre>
     *
     * @param path 文件路径（绝对路径或相对于工作区根目录的相对路径）
     * @return 文件内容的原始字节数组
     * @throws java.nio.file.NoSuchFileException 如果文件不存在
     * @throws SecurityException 如果路径尝试访问工作区外的文件（路径穿越攻击）
     */
    byte[] readFile(String path);

    /**
     * 向沙箱工作区写入文件内容。
     *
     * <p>该方法将指定的字节数组写入沙箱工作区中的指定路径。
     * 如果文件的父目录不存在，沙箱实现应该自动创建父目录。
     *
     * <p>使用示例：
     * <pre>{@code
     * SandboxBackend sandbox = ...;
     * String content = "Hello, Skill!";
     * sandbox.writeFile("output.txt", content.getBytes(StandardCharsets.UTF_8));
     * }</pre>
     *
     * @param path    文件路径（绝对路径或相对于工作区根目录的相对路径）
     * @param content 要写入的文件内容的原始字节数组
     * @throws SecurityException 如果路径尝试访问工作区外的文件（路径穿越攻击）
     */
    void writeFile(String path, byte[] content);

    /**
     * 列出沙箱工作区中匹配 glob 模式的文件。
     *
     * <p>该方法在工作区中搜索匹配指定 glob 模式的文件路径，返回按修改时间倒序排列的路径列表。
     * 支持的 glob 模式包括：
     * <ul>
     *   <li>{@code *.txt} - 匹配当前目录下所有 .txt 文件</li>
     *   <li>{@code **\/*.java} - 递归匹配所有 .java 文件</li>
     *   <li>{@code src/main/**\/*.java} - 匹配 src/main 目录下所有 .java 文件</li>
     * </ul>
     *
     * <p>使用示例：
     * <pre>{@code
     * SandboxBackend sandbox = ...;
     * List<String> javaFiles = sandbox.listFiles("**\/*.java");
     * System.out.println("找到 " + javaFiles.size() + " 个 Java 文件");
     * }</pre>
     *
     * @param glob glob 模式字符串（如 "*.txt"、"**\/*.java"）
     * @return 匹配的文件路径列表（按修改时间倒序排列）
     */
    List<String> listFiles(String glob);

    /**
     * 获取沙箱工作区的根目录路径。
     *
     * <p>该方法返回沙箱工作区的根目录绝对路径，所有文件操作（readFile/writeFile/listFiles）
     * 都基于此路径进行。对于本地进程沙箱，这通常是临时目录；对于 Docker 容器沙箱，
     * 这通常是容器内挂载的 /work 目录。
     *
     * <p>使用示例：
     * <pre>{@code
     * SandboxBackend sandbox = ...;
     * String root = sandbox.workspaceRoot();
     * System.out.println("工作区根目录: " + root);
     * }</pre>
     *
     * @return 沙箱工作区的根目录绝对路径
     */
    String workspaceRoot();

    /**
     * 列出指定目录下的文件和子目录。
     *
     * @param path 目录路径（绝对路径或相对于工作区根目录的相对路径）
     * @return 目录内容列表，每项包含名称和类型信息
     */
    List<String> listDirectory(String path);

    /**
     * 在沙箱工作区中创建目录。
     *
     * @param path 目录路径（绝对路径或相对于工作区根目录的相对路径）
     */
    void createDirectory(String path);

    /**
     * 移动或重命名文件/目录。
     *
     * @param source 源路径
     * @param target 目标路径
     */
    void moveFile(String source, String target);

    /**
     * 获取文件或目录的信息。
     *
     * @param path 文件或目录路径
     * @return 文件信息描述字符串（大小、修改时间、类型等）
     */
    String getFileInfo(String path);

    /**
     * 按文件名搜索文件。
     *
     * @param fileName 要搜索的文件名（支持通配符）
     * @param path 搜索起始目录，null 表示从工作区根目录开始
     * @return 匹配的文件路径列表
     */
    List<String> searchFiles(String fileName, String path);

    /**
     * 递归列出目录下的所有文件和子目录。
     *
     * @param path 目录路径（绝对路径或相对于工作区根目录的相对路径）
     * @param maxDepth 最大递归深度
     * @param includeSize 是否包含文件大小
     * @return 目录树内容列表，每项包含类型和可选的大小信息
     */
    List<String> listDirectoryRecursive(String path, int maxDepth, boolean includeSize);

    /**
     * 在沙箱环境中执行 Python 代码。
     *
     * @param code Python 代码字符串
     * @param timeout 执行超时时间
     * @param pythonExecutable Python 解释器可执行文件名称（如 "python3"、"python"、"py"）
     * @return 执行结果
     */
    ExecResult execPython(String code, Duration timeout, String pythonExecutable);
}
