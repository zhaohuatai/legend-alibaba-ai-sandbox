package org.legend.framework.ai.alibaba.sandbox.backend;

import java.nio.file.Paths;
import java.util.List;

import org.legend.framework.ai.alibaba.sandbox.GuardedSkillMetadata;
import org.legend.framework.ai.alibaba.sandbox.backend.docker.DockerConfig;
import org.legend.framework.ai.alibaba.sandbox.backend.docker.DockerStandaloneSandbox;
import org.legend.framework.ai.alibaba.sandbox.backend.local.LocalProcessSandbox;
import org.legend.framework.ai.alibaba.sandbox.skills.enums.OsKind;
import org.legend.framework.ai.alibaba.sandbox.skills.enums.RiskLevel;
import org.legend.framework.ai.alibaba.sandbox.skills.enums.ShellKind;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvContext;
import org.legend.framework.ai.alibaba.sandbox.skills.manifest.SkillManifest;

/**
 * 沙箱后端提供者，根据 Skill 的风险等级自动选择合适的沙箱后端。
 *
 * <p>该类是 Claude Code 运行时契约的沙箱隔离层调度器，负责在 Skill 执行前
 * 根据 {@link SkillManifest#riskLevel()} 返回的风险等级动态选择沙箱后端：
 *
 * <ul>
 *   <li><b>LOW</b> - 使用 {@link LocalProcessSandbox}（本地进程沙箱），启动快，隔离弱</li>
 *   <li><b>MEDIUM</b> - 使用 {@link DockerStandaloneSandbox}（Docker 容器沙箱），容器级隔离</li>
 *   <li><b>HIGH</b> - 强制使用 {@link DockerStandaloneSandbox}，强制容器级隔离</li>
 * </ul>
 *
 * <p>核心设计理念：
 * <ul>
 *   <li><b>风险驱动</b> - 根据 Skill 的风险等级自动选择沙箱后端，低风险使用本地进程，高风险使用 Docker 容器</li>
 *   <li><b>租约模式</b> - 返回 {@link BackendLease} 而非直接返回沙箱实例，确保资源正确释放</li>
 *   <li><b>环境隔离</b> - Docker 容器内使用 Linux 环境上下文，避免宿主机环境干扰</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * EnvContext env = EnvProbe.probe(Paths.get(System.getProperty("user.dir")));
 * SandboxBackendProvider provider = new SandboxBackendProvider(env);
 *
 * // 获取 Skill 的沙箱租约
 * SkillManifest skill = SkillManifestParser.parse(Paths.get("skills/pdf-extractor"));
 * try (BackendLease lease = provider.acquire(skill)) {
 *     SandboxBackend backend = lease.backend();
 *     // 使用 backend 执行命令、读写文件
 * }
 * }</pre>
 *
 * @see SkillManifest Skill 元数据记录，包含风险等级和网络权限
 * @see SandboxBackend 沙箱后端接口
 * @see LocalProcessSandbox 本地进程沙箱实现
 * @see DockerStandaloneSandbox Docker 容器沙箱实现
 * @see BackendLease 沙箱租约，封装沙箱实例和关闭器
 */
public class SandboxBackendProvider {

    /**
     * 环境上下文，包含操作系统类型、Shell 类型、工作目录等信息。
     *
     * <p>该字段在构造函数中初始化，并在 {@link #wrapLocal(SkillManifest)} 方法中使用，
     * 用于初始化本地进程沙箱。环境上下文包含以下信息：
     * <ul>
     *   <li>操作系统类型（Windows/Linux/Mac）</li>
     *   <li>Shell 类型（Bash/PowerShell/Cmd）</li>
     *   <li>工作目录路径</li>
     *   <li>可用可执行文件列表</li>
     * </ul>
     */
    private final EnvContext env;

    /**
     * Docker 配置，用于创建 DockerStandaloneSandbox 实例。
     *
     * <p>该字段在构造函数中初始化，并在 {@link #wrapDocker(SkillManifest)} 方法中使用，
     * 用于配置 Docker 容器的各项参数，如：
     * <ul>
     *   <li>Docker 镜像名称和标签</li>
     *   <li>容器资源限制（CPU、内存）</li>
     *   <li>网络配置（是否允许网络访问）</li>
     *   <li>挂载配置（宿主机目录到容器的映射）</li>
     * </ul>
     */
    private final DockerConfig dockerConfig;

    /**
     * 构造沙箱后端提供者。
     *
     * <p>该构造函数使用默认的 Docker 配置（{@link DockerConfig#defaults()}）。
     *
     * @param env 环境上下文，用于初始化本地进程沙箱
     */
    public SandboxBackendProvider(EnvContext env) {
        this(env, DockerConfig.defaults());
    }

    /**
     * 构造沙箱后端提供者。
     *
     * <p>该构造函数允许自定义 Docker 配置，用于控制容器的各项参数。
     *
     * @param env           环境上下文，用于初始化本地进程沙箱
     * @param dockerConfig  Docker 配置，用于创建 DockerStandaloneSandbox；如果为 null，则使用默认配置
     */
    public SandboxBackendProvider(EnvContext env, DockerConfig dockerConfig) {
        // 初始化环境上下文
        this.env = env;
        // 初始化 Docker 配置，如果为 null 则使用默认配置
        this.dockerConfig = dockerConfig != null ? dockerConfig : DockerConfig.defaults();
    }

    /**
     * 根据 Skill 的风险等级获取沙箱租约。
     *
     * <p>该方法根据 {@link SkillManifest#riskLevel()} 返回的风险等级选择沙箱后端：
     *
     * <table>
     *   <tr><th>风险等级</th><th>沙箱后端</th><th>网络隔离</th><th>说明</th></tr>
     *   <tr><td>LOW</td><td>LocalProcessSandbox</td><td>无</td><td>本地进程，启动快，隔离弱</td></tr>
     *   <tr><td>MEDIUM</td><td>DockerStandaloneSandbox</td><td>可选</td><td>容器级隔离</td></tr>
     *   <tr><td>HIGH</td><td>DockerStandaloneSandbox</td><td>默认断开</td><td>强制容器级隔离</td></tr>
     * </table>
     *
     * <p>选择逻辑：
     * <ol>
     *   <li>获取 Skill 的风险等级（{@link SkillManifest#riskLevel()}）</li>
     *   <li>如果风险等级为 LOW，调用 {@link #wrapLocal(SkillManifest)} 创建本地进程沙箱租约</li>
     *   <li>如果风险等级为 MEDIUM 或 HIGH，调用 {@link #wrapDocker(SkillManifest)} 创建 Docker 容器沙箱租约</li>
     * </ol>
     *
     * <p>使用示例：
     * <pre>{@code
     * SkillManifest skill = SkillManifestParser.parse(Paths.get("skills/pdf-extractor"));
     * try (BackendLease lease = provider.acquire(skill)) {
     *     SandboxBackend backend = lease.backend();
     *     ExecResult result = backend.exec("python3 extract.py", Duration.ofMinutes(2), Map.of());
     *     System.out.println(result.stdout());
     * }
     * }</pre>
     *
     * @param skillMetadata Skill 元数据，包含风险等级和网络权限信息
     * @return 沙箱租约，包含沙箱后端实例和关闭器
     * @see BackendLease 沙箱租约
     * @see RiskLevel 风险等级枚举
     */
    public BackendLease acquire(SkillManifest skillMetadata) {
        RiskLevel level = skillMetadata.riskLevel();
        return switch (level) {
            case LOW -> wrapLocal(skillMetadata);
            case MEDIUM, HIGH -> wrapDocker(skillMetadata);
        };
    }

    /**
     * 根据 GuardedSkillMetadata 的风险等级获取沙箱租约。
     *
     * <p>该方法与 {@link #acquire(SkillManifest)} 类似，但接收 GuardedSkillMetadata 参数。
     *
     * @param skillMetadata 带安全门控的 Skill 元数据，包含风险等级和网络权限信息
     * @return 沙箱租约，包含沙箱后端实例和关闭器
     */
    public BackendLease acquireForGuardedSkill(GuardedSkillMetadata skillMetadata) {
        RiskLevel level = skillMetadata.getRiskLevel();
        return switch (level) {
            case LOW -> wrapLocalForGuardedSkill(skillMetadata);
            case MEDIUM, HIGH -> wrapDockerForGuardedSkill(skillMetadata);
        };
    }

    /**
     * 创建本地进程沙箱租约。
     *
     * <p>该方法创建一个 {@link LocalProcessSandbox} 实例，并将其包装为 {@link BackendLease}。
     * 本地进程沙箱直接在宿主机上启动子进程，启动速度快，但隔离性较弱。
     *
     * <p>租约生命周期：
     * <ol>
     *   <li>创建 LocalProcessSandbox 实例</li>
     *   <li>创建 BackendLease，绑定沙箱实例和关闭器</li>
     *   <li>调用者使用 try-with-resources 语法确保租约正确关闭</li>
     *   <li>租约关闭时调用 localProcessSandbox.close() 清理资源</li>
     * </ol>
     *
     * @param skill Skill 元数据（当前未使用，保留用于未来扩展）
     * @return 本地进程沙箱租约
     */
    private BackendLease wrapLocal(SkillManifest skill) {
        LocalProcessSandbox localProcessSandbox = new LocalProcessSandbox(env);
        return new BackendLease(localProcessSandbox, () -> localProcessSandbox.close(), "local");
    }

    /**
     * 创建 Docker 容器沙箱租约。
     *
     * <p>该方法创建一个新的 {@link DockerStandaloneSandbox} 实例，
     * 生命周期绑定到 Skill 调用周期。
     *
     * <p>注意：Docker 容器内是 Linux 环境，因此需要创建容器专用的 EnvContext，
     * 覆盖宿主机的 Windows/PowerShell 信息，避免 LLM 生成错误的 PowerShell 命令。
     *
     * <p>容器环境配置：
     * <ul>
     *   <li>操作系统：Linux</li>
     *   <li>Shell 类型：Bash</li>
     *   <li>Shell 路径：/bin/bash</li>
     *   <li>工作目录：/work</li>
     *   <li>可用可执行文件：python3、python、node、git、curl、bash、ls、cat、pwd</li>
     * </ul>
     *
     * <p>租约生命周期：
     * <ol>
     *   <li>创建容器专用的 EnvContext</li>
     *   <li>创建 DockerStandaloneSandbox 实例</li>
     *   <li>创建 BackendLease，绑定沙箱实例和关闭器</li>
     *   <li>调用者使用 try-with-resources 语法确保租约正确关闭</li>
     *   <li>租约关闭时调用 sandbox.close() 停止并删除容器</li>
     * </ol>
     *
     * @param skill Skill 元数据，包含网络权限信息
     * @return Docker 容器沙箱租约
     */
    private BackendLease wrapDocker(SkillManifest skill) {
        EnvContext containerEnv = new EnvContext(
            OsKind.LINUX,
            ShellKind.BASH,
            "bash",
            Paths.get("/work"),
            List.of("python3", "python", "node", "git", "curl", "bash", "ls", "cat", "pwd")
        );

        DockerStandaloneSandbox sandbox = new DockerStandaloneSandbox(dockerConfig, containerEnv);
        return new BackendLease(sandbox, sandbox::close, "docker-standalone");
    }

    /**
     * 为 GuardedSkillMetadata 创建本地进程沙箱租约。
     *
     * @param skillMetadata 带安全门控的 Skill 元数据
     * @return 本地进程沙箱租约
     */
    private BackendLease wrapLocalForGuardedSkill(GuardedSkillMetadata skillMetadata) {
        LocalProcessSandbox localProcessSandbox = new LocalProcessSandbox(env);
        return new BackendLease(localProcessSandbox, () -> localProcessSandbox.close(), "local");
    }

    /**
     * 为 GuardedSkillMetadata 创建 Docker 容器沙箱租约。
     *
     * @param skillMetadata 带安全门控的 Skill 元数据
     * @return Docker 容器沙箱租约
     */
    private BackendLease wrapDockerForGuardedSkill(GuardedSkillMetadata skillMetadata) {
        EnvContext containerEnv = new EnvContext(
            OsKind.LINUX,
            ShellKind.BASH,
            "bash",
            Paths.get("/work"),
            List.of("python3", "python", "node", "git", "curl", "bash", "ls", "cat", "pwd")
        );

        DockerStandaloneSandbox sandbox = new DockerStandaloneSandbox(dockerConfig, containerEnv);
        return new BackendLease(sandbox, sandbox::close, "docker-standalone");
    }
}
