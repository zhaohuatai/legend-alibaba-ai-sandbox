package org.legend.framework.ai.alibaba.sandbox.backend.docker;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.legend.framework.ai.alibaba.sandbox.backend.ExecResult;
import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvContext;

/**
 * 独立的 Docker 沙箱实现，生命周期绑定到单次 Skill 调用。
 *
 * <p>该类是 {@link SandboxBackend} 接口的独立 Docker 容器实现，适用于中高风险 Skill 执行。
 * 它在构造时创建容器，在 close() 时销毁容器，生命周期完全绑定到 Skill 调用周期。
 *
 * <p>核心特性：
 * <ul>
 *   <li><b>独立生命周期</b>：构造时创建容器，close() 时销毁，无需外部管理</li>
 *   <li><b>容器级隔离</b>：每个实例使用独立的 Docker 容器，与宿主机隔离</li>
 *   <li><b>资源配额</b>：容器配置了内存/CPU 限制、PIDs 限制、只读根文件系统等</li>
 *   <li><b>安全配置</b>：非 root 用户、丢弃所有 Capabilities、no-new-privileges</li>
 *   <li><b>动态挂载</b>：根据 Session ID 动态创建和挂载工作目录</li>
 *   <li><b>超时控制</b>：支持命令执行超时，超时后通过 pkill 终止进程</li>
 * </ul>
 *
 * <p>隔离强度：强（★★★★☆）
 * <ul>
 *   <li>容器级文件系统隔离（只读根文件系统 + /work 挂载）</li>
 *   <li>内存/CPU 配额限制</li>
 *   <li>PIDs 限制（防止 fork bomb）</li>
 *   <li>丢弃所有 Linux Capabilities</li>
 *   <li>非 root 用户运行</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * DockerConfig cfg = DockerConfig.defaults()
 *     .withImage("skill-sandbox:latest")
 *     .withMemBytes(512L * 1024 * 1024)
 *     .withCpuQuota(50000L);
 *
 * try (DockerStandaloneSandbox sandbox = new DockerStandaloneSandbox(cfg)) {
 *     // 执行命令
 *     ExecResult result = sandbox.exec("python3 script.py", Duration.ofMinutes(2), Map.of());
 *     System.out.println(result.stdout());
 *
 *     // 写入文件
 *     sandbox.writeFile("output.txt", "Hello".getBytes(StandardCharsets.UTF_8));
 *
 *     // 读取文件
 *     byte[] content = sandbox.readFile("output.txt");
 * } // close() 时自动销毁容器
 * }</pre>
 *
 * @see SandboxBackend 沙箱后端接口
 * @see DockerConfig Docker 容器配置
 * @see DockerPoolSandbox Docker 容器预热池（备选方案）
 * @see org.legend.framework.ai.alibaba.sandbox.backend.local.LocalProcessSandbox 本地进程沙箱实现
 */
public class DockerStandaloneSandbox implements SandboxBackend, AutoCloseable {

    /**
     * Docker Java API 客户端，用于与 Docker 守护进程通信。
     */
    private final DockerClient docker;

    /**
     * 容器 ID，唯一标识当前沙箱使用的 Docker 容器。
     */
    private final String containerId;

    /**
     * 宿主机上的工作区目录路径，该路径被挂载到容器内的 /work 目录。
     */
    private final String workspaceHostPath;

    /**
     * 沙箱是否已关闭的标志。
     * 用于防止重复关闭和检测沙箱状态。
     */
    private volatile boolean closed = false;

    /**
     * 容器环境上下文，包含 Linux 系统信息。
     * 用于覆盖宿主机的 Windows/PowerShell 信息，避免 LLM 生成错误的命令。
     */
    private final EnvContext env;

    /**
     * 构造独立的 Docker 沙箱。
     *
     * <p>该构造函数执行以下初始化步骤：
     * <ol>
     *   <li>创建 Docker Java API 客户端</li>
     *   <li>创建宿主机工作区目录（基于 Session ID）</li>
     *   <li>创建并启动 Docker 容器</li>
     * </ol>
     *
     * <p>使用示例：
     * <pre>{@code
     * DockerConfig cfg = DockerConfig.defaults()
     *     .withImage("skill-sandbox:latest")
     *     .withMemBytes(512L * 1024 * 1024);
     * EnvContext containerEnv = new EnvContext(
     *     EnvContext.OsKind.LINUX,
     *     EnvContext.ShellKind.BASH,
     *     "bash",
     *     Paths.get("/work"),
     *     List.of("python3", "python", "node", "git", "curl", "bash")
     * );
     * DockerStandaloneSandbox sandbox = new DockerStandaloneSandbox(cfg, containerEnv);
     * }</pre>
     *
     * @param cfg 沙箱配置对象，包含镜像、内存、CPU、工作区等参数
     * @param containerEnv 容器环境上下文，覆盖宿主机的 Windows/PowerShell 信息
     * @throws RuntimeException 如果创建 Docker 客户端、工作区或容器失败
     */
    public DockerStandaloneSandbox(DockerConfig cfg, EnvContext containerEnv) {
        // 存储容器环境上下文
        this.env = containerEnv;
        
        // 创建 Docker Java API 客户端
        this.docker = createDockerClient();

        // 创建宿主机工作区目录
        this.workspaceHostPath = createWorkspace(cfg);

        // 创建并启动容器
        this.containerId = createAndStartContainer(cfg);
        
        // 调试日志
//        System.out.println("[DockerStandaloneSandbox] ✅ 容器已创建: " + containerId);
//        System.out.println("[DockerStandaloneSandbox]    工作区: " + workspaceHostPath);
    }

    /**
     * 创建 Docker Java API 客户端。
     *
     * @return DockerClient 实例
     */
    private DockerClient createDockerClient() {
        var dockerCfg = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
        var http = new ApacheDockerHttpClient.Builder()
            .dockerHost(dockerCfg.getDockerHost())
            .sslConfig(dockerCfg.getSSLConfig())
            .build();
        return DockerClientImpl.getInstance(dockerCfg, http);
    }

    /**
     * 创建宿主机工作区目录。
     *
     * <p>基于 Session ID 创建唯一的工作区目录，用于隔离不同 Skill 调用的文件。
     *
     * @param cfg 沙箱配置
     * @return 宿主机工作区目录路径
     */
    private String createWorkspace(DockerConfig cfg) {
        String sessionId = UUID.randomUUID().toString();
        Path workspace = cfg.hostWorkspaceRoot().resolve(sessionId);
        try {
            Files.createDirectories(workspace);
        } catch (IOException e) {
            throw new RuntimeException("创建工作区目录失败：" + workspace, e);
        }
        return workspace.toAbsolutePath().toString();
    }

    /**
     * 创建并启动 Docker 容器。
     *
     * <p>该方法执行以下操作：
     * <ol>
     *   <li>将 Windows 路径转换为 Docker 兼容格式</li>
     *   <li>配置容器安全参数（内存、CPU、PIDs、只读根文件系统等）</li>
     *   <li>配置多目录挂载（工作区 + 额外挂载列表）</li>
     *   <li>创建容器（使用 skill 用户，工作目录 /work，启动命令 sleep infinity）</li>
     *   <li>启动容器</li>
     *   <li>返回容器 ID</li>
     * </ol>
     *
     * @param cfg 沙箱配置
     * @return 容器 ID
     */
    private String createAndStartContainer(DockerConfig cfg) {
        // 将 Windows 路径转换为 Docker 兼容格式
        String dockerHostPath = toDockerPath(Paths.get(workspaceHostPath));
//        System.out.println("[DockerStandaloneSandbox] 工作区挂载: " + dockerHostPath + " → /work");

        // 构建挂载列表
        List<Bind> binds = new ArrayList<>();
        
        // 1. 挂载工作区（必须，读写模式）
        binds.add(new Bind(dockerHostPath, new Volume("/work")));
        
        // 2. 挂载额外目录（可选）
        for (MountConfig mount : cfg.mounts()) {
            String hostPath = toDockerPath(mount.hostPath());
            String containerPath = mount.containerPath();
            
            // 使用 Bind.parse() 解析挂载字符串，支持 :ro 只读模式
            String bindSpec = mount.readOnly() 
                ? hostPath + ":" + containerPath + ":ro"
                : hostPath + ":" + containerPath;
//            System.out.println("[DockerStandaloneSandbox] 额外挂载: " + bindSpec);
            binds.add(Bind.parse(bindSpec));
        }

        // 配置容器安全参数
        HostConfig hc = HostConfig.newHostConfig()
            .withMemory(cfg.memBytes())                                // 内存限制
            .withMemorySwap(cfg.memBytes())                            // 禁用 Swap
            .withCpuQuota(cfg.cpuQuota())                              // CPU 配额
            .withCpuPeriod(100_000L)                                   // CPU 周期（100ms）
            .withPidsLimit(256L)                                       // PIDs 限制（防止 fork bomb）
            .withReadonlyRootfs(true)                                  // 只读根文件系统
            .withCapDrop(Capability.ALL)                               // 丢弃所有 Capabilities
            .withSecurityOpts(List.of("no-new-privileges:true"))       // 防止提权
            .withNetworkMode("bridge")                                 // 网络模式（bridge）
            .withTmpFs(Map.of("/tmp", "rw,size=64m"))                  // 临时文件系统
            .withBinds(binds);                                         // 挂载所有目录

        // 创建容器
        CreateContainerResponse c = docker.createContainerCmd(cfg.image())
            .withHostConfig(hc)
            .withUser("skill")                                         // 非 root 用户
            .withWorkingDir("/work")                                   // 工作目录
            .withCmd("sleep", "infinity")                              // 启动命令（保持容器运行）
            .withLabels(Map.of("skill-sandbox", "true"))               // 标签
            .exec();

        // 启动容器
        docker.startContainerCmd(c.getId()).exec();

        return c.getId();
    }

    /**
     * 将宿主机路径转换为 Docker 兼容格式。
     *
     * <p>该方法利用 Java Path API 的跨平台特性：
     * <ul>
     *   <li>Windows 上：返回 Windows 原生路径（如 E:\xxx），Docker Desktop 自动识别</li>
     *   <li>Linux 上：返回 Unix 风格路径（如 /var/xxx），Docker 原生支持</li>
     * </ul>
     *
     * <p>与 docker-compose 的行为一致，无需手动转换路径格式。
     *
     * @param path 原始路径
     * @return Docker 兼容格式的路径
     */
    private String toDockerPath(Path path) {
        // Java Path API 会根据操作系统自动返回正确的路径格式
        return path.toAbsolutePath().toString();
    }

    /**
     * 在 Docker 容器中执行 shell 命令。
     *
     * <p>该方法通过 Docker Exec API 在容器中创建并执行一个命令。
     *
     * @param command 要执行的 shell 命令字符串
     * @param timeout 命令执行的超时时间
     * @param env     额外的环境变量
     * @return 执行结果记录
     * @throws RuntimeException 如果命令执行被中断
     */
    @Override
    public ExecResult exec(String command, Duration timeout, Map<String, String> env) {
        if (closed) throw new IllegalStateException("沙箱已关闭");

        try {
            // 将环境变量 Map 转换为 Docker Exec API 需要的列表格式
            List<String> envList = env.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue()).toList();

            // 创建 Exec 命令
            ExecCreateCmdResponse exec = docker.execCreateCmd(containerId)
                .withCmd("bash", "-lc", command)
                .withAttachStdout(true)
                .withAttachStderr(true)
                .withEnv(envList)
                .withWorkingDir("/work")
                .withUser("skill")
                .exec();

            // 用于收集标准输出和标准错误的缓冲区
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();

            // 记录命令开始执行的时间
            Instant start = Instant.now();

            // 执行命令并等待完成或超时
            boolean finished = docker.execStartCmd(exec.getId())
                .exec(new ExecStartResultCallback(stdout, stderr))
                .awaitCompletion(timeout.toMillis(), TimeUnit.MILLISECONDS);

            // 命令执行超时，终止容器内的所有子进程
            if (!finished) {
                docker.execCreateCmd(containerId)
                    .withCmd("pkill", "-9", "-P", "1").exec();
                return new ExecResult(124, stdout.toString(StandardCharsets.UTF_8),
                    stderr.toString(StandardCharsets.UTF_8) + "\n[timeout]",
                    Duration.between(start, Instant.now()));
            }

            // 获取命令的退出码
            Long exitCode = docker.inspectExecCmd(exec.getId()).exec().getExitCodeLong();
            return new ExecResult(
                exitCode == null ? -1 : exitCode.intValue(),
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8),
                Duration.between(start, Instant.now()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /**
     * 从容器工作区读取文件内容。
     *
     * @param path 文件路径（绝对路径或相对于 /work 目录的相对路径）
     * @return 文件内容的原始字节数组
     * @throws RuntimeException 如果文件读取失败
     */
    @Override
    public byte[] readFile(String path) {
        if (closed) throw new IllegalStateException("沙箱已关闭");
        
        var r = exec("cat " + shellQuote(absolutize(path)), Duration.ofSeconds(30), Map.of());
        if (!r.ok()) throw new RuntimeException("read failed: " + r.stderr());
        return r.stdout().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * 向容器工作区写入文件内容。
     *
     * @param path    文件路径（绝对路径或相对于 /work 目录的相对路径）
     * @param content 要写入的文件内容的原始字节数组
     * @throws RuntimeException 如果文件写入失败
     */
    @Override
    public void writeFile(String path, byte[] content) {
        if (closed) throw new IllegalStateException("沙箱已关闭");
        
        String b64 = Base64.getEncoder().encodeToString(content);
        String abs = absolutize(path);
        String cmd = "mkdir -p \"$(dirname " + shellQuote(abs) + ")\" && "
                   + "echo " + shellQuote(b64) + " | base64 -d > " + shellQuote(abs);
        var r = exec(cmd, Duration.ofSeconds(30), Map.of());
        if (!r.ok()) throw new RuntimeException("write failed: " + r.stderr());
    }

    /**
     * 列出容器工作区中匹配 glob 模式的文件。
     *
     * @param glob glob 模式字符串
     * @return 匹配的文件路径列表
     */
    @Override
    public List<String> listFiles(String glob) {
        if (closed) throw new IllegalStateException("沙箱已关闭");
        
        String cmd = "shopt -s globstar nullglob && for f in " + glob + "; do echo \"$f\"; done";
        var r = exec(cmd, Duration.ofSeconds(15), Map.of());
        if (!r.ok()) return List.of();
        return Arrays.stream(r.stdout().split("\n"))
                     .filter(s -> !s.isBlank()).toList();
    }

    /**
     * 列出指定目录下的文件和子目录。
     *
     * @param path 目录路径（绝对路径或相对于 /work 目录的相对路径）
     * @return 目录内容列表，每项包含名称和类型信息
     */
    @Override
    public List<String> listDirectory(String path) {
        if (closed) throw new IllegalStateException("沙箱已关闭");
        
        String abs = absolutize(path);
        var r = exec("ls -1 " + shellQuote(abs), Duration.ofSeconds(15), Map.of());
        if (!r.ok()) throw new RuntimeException("listDirectory failed: " + r.stderr());
        
        List<String> entries = new ArrayList<>();
        for (String line : r.stdout().split("\n")) {
            if (!line.isBlank()) {
                String fullPath = abs + "/" + line.trim();
                var stat = exec("test -d " + shellQuote(fullPath) + " && echo DIR || echo FILE",
                    Duration.ofSeconds(5), Map.of());
                String type = stat.ok() && stat.stdout().contains("DIR") ? "[DIR]" : "[FILE]";
                entries.add(type + " " + line.trim());
            }
        }
        Collections.sort(entries);
        return entries;
    }

    /**
     * 在容器工作区中创建目录。
     *
     * @param path 目录路径（绝对路径或相对于 /work 目录的相对路径）
     */
    @Override
    public void createDirectory(String path) {
        if (closed) throw new IllegalStateException("沙箱已关闭");
        
        String abs = absolutize(path);
        var r = exec("mkdir -p " + shellQuote(abs), Duration.ofSeconds(15), Map.of());
        if (!r.ok()) throw new RuntimeException("createDirectory failed: " + r.stderr());
    }

    /**
     * 移动或重命名文件/目录。
     *
     * @param source 源路径
     * @param target 目标路径
     */
    @Override
    public void moveFile(String source, String target) {
        if (closed) throw new IllegalStateException("沙箱已关闭");
        
        String src = absolutize(source);
        String tgt = absolutize(target);
        String cmd = "mkdir -p \"$(dirname " + shellQuote(tgt) + ")\" && mv " + shellQuote(src) + " " + shellQuote(tgt);
        var r = exec(cmd, Duration.ofSeconds(30), Map.of());
        if (!r.ok()) throw new RuntimeException("moveFile failed: " + r.stderr());
    }

    /**
     * 获取文件或目录的信息。
     *
     * @param path 文件或目录路径
     * @return 文件信息描述字符串（大小、修改时间、类型等）
     */
    @Override
    public String getFileInfo(String path) {
        if (closed) throw new IllegalStateException("沙箱已关闭");
        
        String abs = absolutize(path);
        var r = exec("stat " + shellQuote(abs), Duration.ofSeconds(15), Map.of());
        if (!r.ok()) throw new RuntimeException("getFileInfo failed: " + r.stderr());
        return r.stdout();
    }

    /**
     * 按文件名搜索文件。
     *
     * @param fileName 要搜索的文件名（支持通配符）
     * @param path 搜索起始目录，null 表示从工作区根目录开始
     * @return 匹配的文件路径列表
     */
    @Override
    public List<String> searchFiles(String fileName, String path) {
        if (closed) throw new IllegalStateException("沙箱已关闭");
        
        String startDir = (path != null) ? absolutize(path) : "/work";
        String cmd = "find " + shellQuote(startDir) + " -type f -name " + shellQuote("*" + fileName + "*");
        var r = exec(cmd, Duration.ofSeconds(30), Map.of());
        if (!r.ok()) return List.of();
        return Arrays.stream(r.stdout().split("\n"))
                     .filter(s -> !s.isBlank())
                     .toList();
    }

    /**
     * 在容器环境中执行 Python 代码。
     *
     * @param code Python 代码字符串
     * @param timeout 执行超时时间
     * @param pythonExecutable Python 解释器可执行文件名称（如 "python3"、"python"）
     * @return 执行结果
     */
    @Override
    public ExecResult execPython(String code, Duration timeout, String pythonExecutable) {
        if (closed) throw new IllegalStateException("沙箱已关闭");
        
        String cmd = pythonExecutable != null ? pythonExecutable : "python3";
        
        try {
            String scriptName = "python_script_" + System.currentTimeMillis() + ".py";
            Path scriptPath = Paths.get(workspaceHostPath, scriptName);
            Files.writeString(scriptPath, code, StandardCharsets.UTF_8);
            
            try {
                String command = cmd + " " + scriptName;
                return exec(command, timeout, Map.of());
            } finally {
                Files.deleteIfExists(scriptPath);
            }
        } catch (IOException e) {
            return new ExecResult(127, "", "Failed to create temporary Python script: " + e.getMessage(), Duration.ZERO);
        }
    }

    @Override
    public List<String> listDirectoryRecursive(String path, int maxDepth, boolean includeSize) {
        if (closed) throw new IllegalStateException("沙箱已关闭");
        
        String abs = absolutize(path);
        String cmd = includeSize
            ? String.format("find %s -maxdepth %d -mindepth 1 | while read -r f; do if [ -d \"$f\" ]; then echo \"[DIR] ${f#%s/}/\"; else sz=$(stat -c%%s \"$f\" 2>/dev/null || echo 0); echo \"[FILE] ${f#%s/} ($sz bytes)\"; fi; done", shellQuote(abs), maxDepth, shellQuote(abs), shellQuote(abs))
            : String.format("find %s -maxdepth %d -mindepth 1 | while read -r f; do if [ -d \"$f\" ]; then echo \"[DIR] ${f#%s/}/\"; else echo \"[FILE] ${f#%s/}\"; fi; done", shellQuote(abs), maxDepth, shellQuote(abs), shellQuote(abs));
        
        var r = exec(cmd, Duration.ofSeconds(30), Map.of());
        if (!r.ok()) throw new RuntimeException("listDirectoryRecursive failed: " + r.stderr());
        
        List<String> entries = new ArrayList<>();
        for (String line : r.stdout().split("\n")) {
            if (!line.isBlank()) {
                entries.add(line.trim());
            }
        }
        Collections.sort(entries);
        return entries;
    }

    /**
     * 获取容器工作区的根目录路径。
     *
     * @return 容器工作区的根目录路径（固定为 "/work"）
     */
    @Override
    public String workspaceRoot() {
        return "/work";
    }

    /**
     * 将路径转换为容器内的绝对路径。
     *
     * @param path 文件路径
     * @return 容器内的绝对路径
     */
    private String absolutize(String path) {
        if (path == null) throw new IllegalArgumentException("path required");
        return path.startsWith("/") ? path : "/work/" + path;
    }

    /**
     * 对字符串进行 shell 安全转义。
     *
     * @param s 要转义的字符串
     * @return shell 安全转义后的字符串
     */
    private static String shellQuote(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    /**
     * 关闭沙箱，终止并删除 Docker 容器。
     *
     * <p>该方法执行以下清理操作：
     * <ol>
     *   <li>发送 SIGKILL 信号终止容器</li>
     *   <li>强制删除容器及其卷</li>
     *   <li>关闭 Docker 客户端</li>
     * </ol>
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;

//        System.out.println("[DockerStandaloneSandbox] 🗑️ 容器销毁中: " + containerId);

        // 终止容器
        try {
            docker.killContainerCmd(containerId).withSignal("SIGKILL").exec();
        } catch (Exception ignored) {
            // 静默忽略终止失败
        }

        // 删除容器及其卷
        try {
            docker.removeContainerCmd(containerId).withForce(true).withRemoveVolumes(true).exec();
        } catch (Exception ignored) {
            // 静默忽略删除失败
        }

        // 关闭 Docker 客户端
        try {
            docker.close();
        } catch (IOException ignored) {
            // 静默忽略关闭失败
        }
        
//        System.out.println("[DockerStandaloneSandbox] ✅ 容器已销毁: " + containerId);
    }

    /**
     * 检查沙箱是否已关闭。
     *
     * @return 如果沙箱已关闭返回 true，否则返回 false
     */
    public boolean isClosed() {
        return closed;
    }

    /**
     * 获取容器 ID。
     *
     * @return Docker 容器 ID
     */
    public String containerId() {
        return containerId;
    }

    /**
     * 获取宿主机工作区路径。
     *
     * @return 宿主机工作区路径
     */
    public String workspaceHostPath() {
        return workspaceHostPath;
    }

    /**
     * 获取容器环境上下文。
     *
     * <p>该方法返回容器专用的环境上下文（Linux + Bash），
     * 用于在 SystemPromptInjector 中渲染正确的环境信息，
     * 避免 LLM 生成宿主机的 PowerShell 命令。
     *
     * @return 容器环境上下文
     */
    public EnvContext getEnv() {
        return env;
    }
}
