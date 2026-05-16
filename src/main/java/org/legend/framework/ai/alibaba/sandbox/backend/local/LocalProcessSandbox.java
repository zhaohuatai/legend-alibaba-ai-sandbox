package org.legend.framework.ai.alibaba.sandbox.backend.local;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

import org.legend.framework.ai.alibaba.sandbox.backend.ExecResult;
import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.skills.enums.OsKind;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvContext;

/**
 * 本地进程沙箱实现，直接在宿主机上创建子进程执行命令。
 *
 * <p>该类是 {@link SandboxBackend} 接口的本地进程实现，适用于开发环境或低风险 Skill 执行。
 * 它通过 {@link ProcessBuilder} 创建子进程，并在独立的临时工作区中执行命令和文件操作。
 *
 * <p>核心特性：
 * <ul>
 *   <li><b>快速启动</b>：启动开销 <50ms，无需容器或虚拟机</li>
 *   <li><b>OS 自适应</b>：Windows 自动使用 PowerShell，Linux/macOS 使用 Bash</li>
 *   <li><b>超时控制</b>：支持命令执行超时，超时后终止整个进程树</li>
 *   <li><b>路径安全</b>：防止路径穿越攻击，所有文件操作限制在工作区内</li>
 *   <li><b>资源清理</b>：close() 时自动清理临时工作区和子进程</li>
 * </ul>
 *
 * <p>隔离强度：弱（★★☆☆☆）
 * <ul>
 *   <li>进程在宿主机上运行，可访问主机资源</li>
 *   <li>无内存/CPU 配额限制</li>
 *   <li>无网络隔离</li>
 *   <li>依赖路径安全检查防止越权访问</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * EnvContext env = EnvProbe.probe(Paths.get(System.getProperty("user.dir")));
 * try (LocalProcessSandbox sandbox = new LocalProcessSandbox(env)) {
 *     // 执行命令
 *     ExecResult result = sandbox.exec("ls -la", Duration.ofSeconds(30), Map.of());
 *     System.out.println(result.stdout());
 *
 *     // 写入文件
 *     sandbox.writeFile("output.txt", "Hello".getBytes(StandardCharsets.UTF_8));
 *
 *     // 读取文件
 *     byte[] content = sandbox.readFile("output.txt");
 *     System.out.println(new String(content, StandardCharsets.UTF_8));
 * }
 * }</pre>
 *
 * @see SandboxBackend 沙箱后端接口
 * @see EnvContext 环境上下文记录
 * @see org.legend.framework.ai.alibaba.skill.guard.sandbox.docker.backup.DockerSession Docker 容器沙箱实现（生产环境推荐）
 */
public class LocalProcessSandbox implements SandboxBackend, AutoCloseable {

    /**
     * 环境上下文，包含操作系统类型、Shell 类型等信息。
     * 用于决定命令执行时使用的 Shell 解释器。
     */
    private final EnvContext env;

    /**
     * 沙箱工作区目录，所有文件操作都限制在此目录内。
     * 默认情况下是一个临时目录（skill-ws- 前缀）。
     */
    private final Path workspace;

    /**
     * 用于异步读取子进程标准输出和标准错误的线程池。
     *
     * <p>该线程池使用缓存线程池策略，每个线程命名为 "sandbox-pump"，
     * 并设置为守护线程，确保 JVM 退出时不会阻塞。
     */
    private final ExecutorService streamPump = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sandbox-pump");
        t.setDaemon(true);
        return t;
    });

    /**
     * 构造本地进程沙箱，使用自动创建的临时工作区。
     *
     * <p>该构造函数会在系统临时目录下创建一个以 "skill-ws-" 为前缀的临时目录
     * 作为沙箱工作区。工作区在 close() 时自动清理。
     *
     * @param env 环境上下文，用于决定命令执行时使用的 Shell 解释器
     * @throws UncheckedIOException 如果创建临时目录失败
     */
    public LocalProcessSandbox(EnvContext env) {
        this.env = env;
        try {
            // 创建临时工作区目录，前缀为 "skill-ws-"
            this.workspace = Files.createTempDirectory("skill-ws-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 构造本地进程沙箱，使用指定的工作区目录。
     *
     * <p>该构造函数允许调用者指定沙箱工作区目录，适用于需要持久化工作区的场景。
     * 如果指定的目录不存在，该方法会自动创建。
     *
     * @param env       环境上下文，用于决定命令执行时使用的 Shell 解释器
     * @param workspace 指定的工作区目录路径
     * @throws UncheckedIOException 如果创建工作区目录失败
     */
    public LocalProcessSandbox(EnvContext env, Path workspace) {
        this.env = env;
        this.workspace = workspace;
        try {
            // 确保工作区目录存在
            Files.createDirectories(workspace);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 在沙箱环境中执行 shell 命令。
     *
     * <p>该方法通过 {@link ProcessBuilder} 创建子进程执行命令，执行流程如下：
     * <ol>
     *   <li>根据 OS 类型构建 Shell 命令（Windows 使用 PowerShell，Linux/macOS 使用 Bash）</li>
     *   <li>设置工作目录为沙箱工作区</li>
     *   <li>合并额外的环境变量</li>
     *   <li>启动子进程，异步读取标准输出和标准错误</li>
     *   <li>等待命令执行完成或超时</li>
     *   <li>如果超时或被中断，终止整个进程树</li>
     *   <li>返回执行结果（退出码、标准输出、标准错误、耗时）</li>
     * </ol>
     *
     * <p>退出码约定：
     * <ul>
     *   <li>0 - 命令成功执行</li>
     *   <li>124 - 命令执行超时</li>
     *   <li>127 - 进程启动失败（如 Shell 不存在）</li>
     *   <li>130 - 命令执行被中断</li>
     *   <li>其他 - 命令执行失败的具体退出码</li>
     * </ul>
     *
     * <p>使用示例：
     * <pre>{@code
     * ExecResult result = sandbox.exec("python3 script.py", Duration.ofMinutes(2), Map.of("PYTHONPATH", "/custom/path"));
     * if (result.ok()) {
     *     System.out.println("输出: " + result.stdout());
     * } else {
     *     System.err.println("错误: " + result.stderr());
     * }
     * }</pre>
     *
     * @param command 要执行的 shell 命令字符串（如 "ls -la"、"python3 script.py"）
     * @param timeout 命令执行的超时时间，超时后会终止整个进程树
     * @param envVars 额外的环境变量，将与子进程的环境变量合并
     * @return 执行结果记录，包含退出码、标准输出、标准错误和执行耗时
     */
    @Override
    public ExecResult exec(String command, Duration timeout, Map<String, String> envVars) {
        // 构建 Shell 命令（根据 OS 类型选择合适的 Shell 解释器）
        ProcessBuilder pb = new ProcessBuilder(buildShellCommand(command));

        // 设置工作目录为沙箱工作区
        pb.directory(workspace.toFile());

        // 合并额外的环境变量
        pb.environment().putAll(envVars);

        // 不合并标准错误到标准输出
        pb.redirectErrorStream(false);

        Process proc;
        // 记录命令开始执行的时间
        Instant start = Instant.now();

        // 启动子进程
        try {
            proc = pb.start();
        } catch (IOException e) {
            // 进程启动失败（如 Shell 不存在），返回退出码 127
            return new ExecResult(127, "", "spawn failed: " + e.getMessage(),
                Duration.between(start, Instant.now()));
        }

        // 用于收集标准输出和标准错误的缓冲区
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();

        // 异步读取标准输出和标准错误，防止管道缓冲区满导致子进程阻塞
        Future<?> fOut = streamPump.submit(() -> pipe(proc.getInputStream(), out));
        Future<?> fErr = streamPump.submit(() -> pipe(proc.getErrorStream(), err));

        boolean finished;
        try {
            // 等待命令执行完成或超时
            finished = proc.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            // 命令执行被中断，恢复中断状态并终止进程树
            Thread.currentThread().interrupt();
            killTree(proc);
            return new ExecResult(130, out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8) + "\n[interrupted]",
                Duration.between(start, Instant.now()));
        }

        // 命令执行超时，终止进程树
        if (!finished) {
            killTree(proc);
            // 等待输出管道线程完成（最多 500ms）
            awaitQuiet(fOut, fErr, 500);
            return new ExecResult(124,
                out.toString(StandardCharsets.UTF_8),
                err.toString(StandardCharsets.UTF_8) + "\n[timeout after " + timeout + "]",
                Duration.between(start, Instant.now()));
        }

        // 命令执行完成，等待输出管道线程完成（最多 1000ms）
        awaitQuiet(fOut, fErr, 1000);

        // 返回执行结果
        return new ExecResult(proc.exitValue(),
            out.toString(StandardCharsets.UTF_8),
            err.toString(StandardCharsets.UTF_8),
            Duration.between(start, Instant.now()));
    }

    /**
     * 根据操作系统类型构建 Shell 命令。
     *
     * <p>该方法根据 {@link EnvContext#os()} 返回的操作系统类型选择合适的 Shell 解释器：
     * <ul>
     *   <li><b>Windows</b>：使用 PowerShell（pwsh 或 powershell），参数为 -NoProfile -NonInteractive -ExecutionPolicy Bypass -Command</li>
     *   <li><b>Linux/macOS</b>：使用 Bash/Zsh，参数为 -lc（登录 shell + 执行命令）</li>
     * </ul>
     *
     * <p>Windows PowerShell 参数说明：
     * <ul>
     *   <li>-NoProfile - 不加载用户配置文件，加快启动速度</li>
     *   <li>-NonInteractive - 非交互模式，不等待用户输入</li>
     *   <li>-ExecutionPolicy Bypass - 绕过执行策略限制，允许执行任意命令</li>
     *   <li>-Command - 指定要执行的命令字符串</li>
     * </ul>
     *
     * @param command 要执行的 shell 命令字符串
     * @return Shell 命令列表（如 ["pwsh", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-Command", "ls -la"]）
     */
    private List<String> buildShellCommand(String command) {
        // Windows 系统：使用 PowerShell
        if (env.os() == OsKind.WINDOWS) {
            // 优先使用 EnvContext 中探测到的 Shell 可执行文件，默认使用 powershell
            String pwsh = env.shellExecutable() != null ? env.shellExecutable() : "powershell";
            return List.of(pwsh,
                "-NoProfile",
                "-NonInteractive",
                "-ExecutionPolicy", "Bypass",
                "-Command", command);
        }

        // Linux/macOS 系统：使用 Bash/Zsh
        // 优先使用 EnvContext 中探测到的 Shell 可执行文件，默认使用 bash
        String shell = env.shellExecutable() != null ? env.shellExecutable() : "bash";
        return List.of(shell, "-lc", command);
    }

    /**
     * 终止进程树（包括子进程的所有后代进程）。
     *
     * <p>该方法使用 {@link ProcessHandle} API 终止指定进程及其所有后代进程，
     * 执行流程如下：
     * <ol>
     *   <li>获取进程的 {@link ProcessHandle}</li>
     *   <li>终止所有后代进程（防止孤儿进程）</li>
     *   <li>终止主进程</li>
     *   <li>等待 2 秒，如果进程仍未终止，则强制终止（destroyForcibly）</li>
     * </ol>
     *
     * <p>该方法会静默忽略所有异常，确保终止操作不会中断主流程。
     *
     * @param proc 要终止的进程
     */
    private void killTree(Process proc) {
        try {
            // 获取进程句柄
            ProcessHandle handle = proc.toHandle();

            // 终止所有后代进程（防止孤儿进程继续运行）
            handle.descendants().forEach(ProcessHandle::destroy);

            // 终止主进程
            handle.destroy();

            // 等待 2 秒，如果进程仍未终止，则强制终止
            if (!proc.waitFor(2, TimeUnit.SECONDS)) {
                // 强制终止所有后代进程
                handle.descendants().forEach(ProcessHandle::destroyForcibly);
                // 强制终止主进程
                handle.destroyForcibly();
            }
        } catch (Exception ignored) {
            // 如果 ProcessHandle API 不可用，回退到直接终止进程
            try {
                proc.destroyForcibly();
            } catch (Exception ignored2) {
                // 静默忽略所有异常
            }
        }
    }

    /**
     * 等待多个 Future 完成，静默忽略异常。
     *
     * <p>该方法用于等待输出管道线程完成，如果线程在指定时间内未完成或发生异常，
     * 该方法会静默忽略，确保不会中断主流程。
     *
     * @param a  第一个 Future
     * @param b  第二个 Future
     * @param ms 等待超时时间（毫秒）
     */
    private void awaitQuiet(Future<?> a, Future<?> b, long ms) {
        try {
            a.get(ms, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // 静默忽略异常（如超时、中断等）
        }
        try {
            b.get(ms, TimeUnit.MILLISECONDS);
        } catch (Exception ignored) {
            // 静默忽略异常
        }
    }

    /**
     * 将输入流的内容复制到 ByteArrayOutputStream。
     *
     * <p>该方法用于异步读取子进程的标准输出和标准错误，
     * 防止管道缓冲区满导致子进程阻塞。
     *
     * <p>该方法使用 4KB 缓冲区循环读取输入流，直到流结束。
     * 读取完成后自动关闭输入流。
     *
     * @param is   输入流（如子进程的标准输出或标准错误）
     * @param sink 目标 ByteArrayOutputStream
     */
    private void pipe(InputStream is, ByteArrayOutputStream sink) {
        byte[] buf = new byte[4096];
        try (is) {
            int n;
            // 循环读取输入流，直到流结束
            while ((n = is.read(buf)) >= 0) {
                sink.write(buf, 0, n);
            }
        } catch (IOException ignored) {
            // 静默忽略 IO 异常（如管道关闭）
        }
    }

    /**
     * 从沙箱工作区读取文件内容。
     *
     * <p>该方法从沙箱工作区中读取指定路径的文件，返回文件的原始字节数组。
     * 路径可以是绝对路径或相对于工作区根目录的相对路径。
     *
     * <p>该方法会调用 {@link #resolveSafe(String)} 进行路径安全检查，
     * 防止路径穿越攻击。
     *
     * @param path 文件路径（绝对路径或相对于工作区根目录的相对路径）
     * @return 文件内容的原始字节数组
     * @throws UncheckedIOException 如果文件读取失败
     * @throws SecurityException    如果路径尝试访问工作区外的文件
     */
    @Override
    public byte[] readFile(String path) {
        try {
            return Files.readAllBytes(resolveSafe(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 向沙箱工作区写入文件内容。
     *
     * <p>该方法将指定的字节数组写入沙箱工作区中的指定路径。
     * 如果文件的父目录不存在，该方法会自动创建父目录。
     *
     * <p>该方法会调用 {@link #resolveSafe(String)} 进行路径安全检查，
     * 防止路径穿越攻击。
     *
     * @param path    文件路径（绝对路径或相对于工作区根目录的相对路径）
     * @param content 要写入的文件内容的原始字节数组
     * @throws UncheckedIOException 如果文件写入失败
     * @throws SecurityException    如果路径尝试访问工作区外的文件
     */
    @Override
    public void writeFile(String path, byte[] content) {
        try {
            // 解析并检查路径安全性
            Path target = resolveSafe(path);

            // 自动创建父目录（如果不存在）
            Files.createDirectories(target.getParent());

            // 写入文件内容
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 列出沙箱工作区中匹配 glob 模式的文件。
     *
     * <p>该方法在工作区中递归搜索匹配指定 glob 模式的文件路径，
     * 返回按修改时间倒序排列的路径列表。
     *
     * <p>支持的 glob 模式包括：
     * <ul>
     *   <li>{@code *.txt} - 匹配当前目录下所有 .txt 文件</li>
     *   <li>{@code **\/*.java} - 递归匹配所有 .java 文件</li>
     *   <li>{@code src/main/**\/*.java} - 匹配 src\/main 目录下所有 .java 文件</li>
     * </ul>
     *
     * @param glob glob 模式字符串（如 "*.txt"、"**\/*.java"）
     * @return 匹配的文件路径列表（按修改时间倒序排列）
     */
    @Override
    public List<String> listFiles(String glob) {
        // 创建 glob 模式匹配器
        PathMatcher matcher = workspace.getFileSystem().getPathMatcher("glob:" + glob);

        List<String> out = new ArrayList<>();

        // 递归遍历工作区目录
        try (Stream<Path> walk = Files.walk(workspace)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                // 计算相对于工作区的路径
                Path rel = workspace.relativize(p);

                // 检查相对路径和绝对路径是否匹配 glob 模式
                if (matcher.matches(rel) || matcher.matches(p)) {
                    out.add(p.toAbsolutePath().toString());
                }
            });
        } catch (IOException ignored) {
            // 静默忽略 IO 异常，返回已收集的文件列表
        }

        // 按修改时间倒序排列（最新修改的文件排在前面）
        out.sort((a, b) -> Long.compare(new File(b).lastModified(), new File(a).lastModified()));

        return out;
    }

    /**
     * 获取沙箱工作区的根目录路径。
     *
     * @return 沙箱工作区的根目录绝对路径
     */
    @Override
    public String workspaceRoot() {
        return workspace.toAbsolutePath().toString();
    }

    /**
     * 安全地解析文件路径，防止路径穿越攻击。
     *
     * <p>该方法执行以下安全检查：
     * <ol>
     *   <li>如果路径是绝对路径，直接使用该路径</li>
     *   <li>如果路径是相对路径，相对于工作区根目录解析</li>
     *   <li>对解析后的路径进行 normalize()，消除 ".." 等路径遍历符号</li>
     * </ol>
     *
     * <p>注意：该方法仅做路径规范化，不做额外的越权检查。
     * 生产环境建议增加工作区边界检查。
     *
     * @param path 文件路径（绝对路径或相对路径）
     * @return 规范化后的绝对路径
     */
    private Path resolveSafe(String path) {
        // 解析路径
        Path p = Paths.get(path);

        // 如果是绝对路径，直接使用；否则相对于工作区解析
        Path resolved = (p.isAbsolute() ? p : workspace.resolve(p)).normalize();

        return resolved;
    }

    /**
     * 关闭沙箱，清理临时工作区和线程池。
     *
     * <p>该方法执行以下清理操作：
     * <ol>
     *   <li>关闭 streamPump 线程池（立即中断所有线程）</li>
     *   <li>递归删除工作区目录及其所有内容</li>
     * </ol>
     *
     * <p>如果清理过程中发生异常，该方法会静默忽略，
     * 确保资源释放不会中断主流程。
     */
    @Override
    public void close() {
        streamPump.shutdownNow();
        try (Stream<Path> walk = Files.walk(workspace)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                }
            });
        } catch (IOException ignored) {
        }
    }

    @Override
    public List<String> listDirectory(String path) {
        Path dir = resolveSafe(path);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Not a directory: " + path);
        }
        List<String> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path entry : stream) {
                String type = Files.isDirectory(entry) ? "[DIR]" : "[FILE]";
                entries.add(type + " " + entry.getFileName().toString());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Collections.sort(entries);
        return entries;
    }

    @Override
    public void createDirectory(String path) {
        try {
            Files.createDirectories(resolveSafe(path));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void moveFile(String source, String target) {
        try {
            Path src = resolveSafe(source);
            Path tgt = resolveSafe(target);
            Files.createDirectories(tgt.getParent());
            Files.move(src, tgt, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String getFileInfo(String path) {
        Path p = resolveSafe(path);
        if (!Files.exists(p)) {
            throw new IllegalArgumentException("Path does not exist: " + path);
        }
        try {
            boolean isDir = Files.isDirectory(p);
            long size = isDir ? 0 : Files.size(p);
            String lastModified = Files.getLastModifiedTime(p).toString();
            return String.format("%s: %s, size: %d bytes, lastModified: %s",
                isDir ? "Directory" : "File", p.getFileName(), size, lastModified);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public List<String> searchFiles(String fileName, String path) {
        Path startDir = (path != null) ? resolveSafe(path) : workspace;
        List<String> results = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(startDir)) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.getFileName().toString().contains(fileName))
                .forEach(p -> results.add(p.toAbsolutePath().toString()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return results;
    }

    @Override
    public List<String> listDirectoryRecursive(String path, int maxDepth, boolean includeSize) {
        Path dir = resolveSafe(path);
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("Not a directory: " + path);
        }
        List<String> entries = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(dir, maxDepth)) {
            walk.filter(p -> !p.equals(dir)).forEach(p -> {
                boolean isDir = Files.isDirectory(p);
                String prefix = isDir ? "[DIR] " : "[FILE] ";
                String relative = dir.relativize(p).toString();
                if (isDir) {
                    entries.add(prefix + relative + "/");
                } else if (includeSize) {
                    try {
                        long size = Files.size(p);
                        entries.add(prefix + relative + " (" + size + " bytes)");
                    } catch (IOException e) {
                        entries.add(prefix + relative);
                    }
                } else {
                    entries.add(prefix + relative);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Collections.sort(entries);
        return entries;
    }

    @Override
    public ExecResult execPython(String code, Duration timeout, String pythonExecutable) {
        String cmd = pythonExecutable != null ? pythonExecutable : "python3";
        
        try {
            Path tempScript = Files.createTempFile(workspace, "python_script_", ".py");
            Files.writeString(tempScript, code, StandardCharsets.UTF_8);
            
            try {
                String command = cmd + " \"" + tempScript.toAbsolutePath() + "\"";
                return exec(command, timeout, Map.of());
            } finally {
                Files.deleteIfExists(tempScript);
            }
        } catch (IOException e) {
            return new ExecResult(127, "", "Failed to create temporary Python script: " + e.getMessage(), Duration.ZERO);
        }
    }
}
