package org.legend.framework.ai.alibaba.skill.guard.demo;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;

import org.legend.framework.ai.alibaba.sandbox.GuardedSkillMetadata;
import org.legend.framework.ai.alibaba.sandbox.agent.hook.GuardedSkillsAgentHook;
import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackendProvider;
import org.legend.framework.ai.alibaba.sandbox.backend.docker.DockerConfig;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvContext;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvProbe;
import org.legend.framework.ai.alibaba.sandbox.skills.registry.GuardedSkillRegistry;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Docker Standalone Sandbox 测试类。
 *
 * <p>测试需要 Docker 容器级隔离的 Skill 场景，包括：
 * <ul>
 *   <li>不可信代码执行（在线代码评测）</li>
 *   <li>第三方依赖安装（pip install / npm install）</li>
 *   <li>网络隔离测试（断开容器网络）</li>
 * </ul>
 *
 * <p>前置条件：
 * <ul>
 *   <li>已安装 Docker Desktop 并运行</li>
 *   <li>已创建 skill-sandbox:latest 镜像（或使用现有镜像）</li>
 *   <li>已配置 DASHSCOPE_API_KEY 环境变量</li>
 * </ul>
 */
public class GuardDockerSandboxSkillTest {

    private static final Path SKILLS_DIR = Paths.get("src/main/resources/skills");

    static ChatModel chatModel = DashScopeChatModel.builder()
            .dashScopeApi(DashScopeApi.builder().apiKey(TestConfig.API_KEY).build())
            .build();

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("   Docker Standalone Sandbox Skill Test");
        System.out.println("═══════════════════════════════════════════════════\n");

        try {
            if (!checkDockerAvailable()) {
                System.err.println("❌ Docker 不可用，跳过测试");
                System.out.println("   请确保 Docker Desktop 已安装并运行");
                System.out.println("   安装地址: https://www.docker.com/products/docker-desktop");
                return;
            }

            EnvContext env = setupEnvironment();
            DockerConfig dockerConfig = createDockerConfig();
            GuardedSkillRegistry registry = createRegistry(env);
            GuardedSkillsAgentHook skillsHook = createSkillsHook(registry, env, dockerConfig);
            ReactAgent agent = createAgent(skillsHook);

            runTests(agent);

        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n✅ Docker Standalone Sandbox Skill 测试完成！");
    }

    private static boolean checkDockerAvailable() {
        System.out.println("🐳 检查 Docker 可用性...");
        try {
            Process process = new ProcessBuilder("docker", "info").start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("   ✅ Docker 已安装并运行");
                return true;
            } else {
                System.out.println("   ❌ Docker 未运行或无权限");
                printDockerFixInstructions();
                return false;
            }
        } catch (Exception e) {
            System.out.println("   ❌ Docker 未安装");
            printDockerFixInstructions();
            return false;
        }
    }

    private static void printDockerFixInstructions() {
        System.out.println();
        System.out.println("   📖 Docker 修复指南:");
        System.out.println("   1. 打开 Docker Desktop");
        System.out.println("   2. 确保切换到 Linux 容器模式（右键托盘图标 → Switch to Linux containers）");
        System.out.println("   3. 等待 Docker 引擎启动完成（托盘图标不再旋转）");
        System.out.println("   4. 验证: docker info");
        System.out.println();
        System.out.println("   💡 如果 Docker Desktop 未安装:");
        System.out.println("   下载地址: https://www.docker.com/products/docker-desktop");
        System.out.println();
    }

    private static EnvContext setupEnvironment() {
        System.out.println("📋 初始化环境...");
        EnvContext env = EnvProbe.probe(Paths.get(System.getProperty("user.dir")));
        System.out.println("   操作系统: " + env.os());
        System.out.println("   Shell: " + env.shellKind());
        System.out.println("   工作目录: " + env.cwd());
        System.out.println("   可用工具: " + env.availableExecutables());
        System.out.println();
        return env;
    }

    private static DockerConfig createDockerConfig() {
        System.out.println("🐳 创建 Docker 配置...");
        try {
            Path workspaceRoot = Paths.get(System.getProperty("user.home"), "skill-workspaces");
            System.out.println("   📁 工作区目录: " + workspaceRoot);

            ensureImageExists("skill-sandbox:latest", "ubuntu:22.04");

            DockerConfig cfg = DockerConfig.defaults()
                .withHostWorkspaceRoot(workspaceRoot)
                .withMount(Paths.get("E:/skills/data-analysis"), "/skill", true)
                .withMount(Paths.get("E:/data/input"), "/data/input", true)
                .withMount(Paths.get("E:/output"), "/output", false);

            System.out.println("   ✅ Docker 配置已创建");
            System.out.println("   📂 目录映射:");
            System.out.println("      E:\\skills\\data-analysis\\ → /skill/ (只读)");
            System.out.println("      E:\\data\\input\\ → /data/input/ (只读)");
            System.out.println("      E:\\output\\ → /output/ (读写)");
            System.out.println();
            return cfg;
        } catch (Exception e) {
            System.err.println("   ❌ Docker 配置创建失败: " + e.getMessage());
            System.out.println();
            System.out.println("   📖 常见原因:");
            System.out.println("   1. Docker Desktop 未运行或正在启动中");
            System.out.println("   2. 未切换到 Linux 容器模式");
            System.out.println("   3. skill-sandbox:latest 镜像不存在");
            System.out.println();
            System.out.println("   💡 解决方案:");
            System.out.println("   1. 打开 Docker Desktop，等待启动完成");
            System.out.println("   2. 右键托盘图标 → Switch to Linux containers");
            System.out.println("   3. 运行: docker pull python:3.11-slim");
            System.out.println("   4. 运行: docker tag python:3.11-slim skill-sandbox:latest");
            System.out.println();
            throw e;
        }
    }

    private static void ensureImageExists(String targetImage, String baseImage) {
        System.out.println("   🔍 检查镜像: " + targetImage);
        try {
            Process checkProcess = new ProcessBuilder("docker", "image", "inspect", targetImage).start();
            int exitCode = checkProcess.waitFor();
            
            if (exitCode == 0) {
                System.out.println("   ✅ 镜像已存在: " + targetImage);
                return;
            }

            System.out.println("   ⬇️ 镜像不存在，正在拉取基础镜像: " + baseImage);
            System.out.println("   ⏳ 这可能需要几分钟，请耐心等待...");
            
            Process pullProcess = new ProcessBuilder("docker", "pull", baseImage).start();
            
            var pullOutput = new java.io.BufferedReader(new java.io.InputStreamReader(pullProcess.getInputStream()));
            String line;
            while ((line = pullOutput.readLine()) != null) {
                System.out.println("      " + line);
            }
            
            int pullExitCode = pullProcess.waitFor();
            
            if (pullExitCode != 0) {
                System.err.println("   ❌ 拉取基础镜像失败: " + baseImage);
                System.out.println();
                System.out.println("   📖 请手动执行以下命令:");
                System.out.println("   docker pull " + baseImage);
                System.out.println("   docker tag " + baseImage + " " + targetImage);
                System.out.println();
                throw new RuntimeException("Failed to pull base image: " + baseImage);
            }

            System.out.println("   ✅ 基础镜像拉取成功: " + baseImage);
            System.out.println("   🏷️ 创建镜像标签: " + targetImage);

            Process tagProcess = new ProcessBuilder("docker", "tag", baseImage, targetImage).start();
            int tagExitCode = tagProcess.waitFor();
            
            if (tagExitCode != 0) {
                System.err.println("   ❌ 创建镜像标签失败");
                throw new RuntimeException("Failed to tag image");
            }

            System.out.println("   ✅ 镜像标签创建成功: " + targetImage);

        } catch (Exception e) {
            System.err.println("   ❌ 镜像检查/拉取失败: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private static GuardedSkillRegistry createRegistry(EnvContext env) {
        System.out.println("📂 注册 Skill...");
        GuardedSkillRegistry registry = GuardedSkillRegistry.builder()
            .skillsDirectory(SKILLS_DIR.toAbsolutePath().toString())
            .env(env)
            .autoLoad(true)
            .build();

        System.out.println("📚 已注册的 Skill:");
        for (GuardedSkillMetadata skill : registry.listAll().stream()
                .filter(m -> m instanceof GuardedSkillMetadata)
                .map(m -> (GuardedSkillMetadata) m)
                .toList()) {
            System.out.println("   - " + skill.getName() + ": " + skill.getDescription());
        }
        System.out.println();
        return registry;
    }

    private static GuardedSkillsAgentHook createSkillsHook(
            GuardedSkillRegistry registry,
            EnvContext env,
            DockerConfig dockerConfig) {
        SandboxBackendProvider provider = new SandboxBackendProvider(env, dockerConfig);
        return GuardedSkillsAgentHook.builder()
            .skillRegistry(registry)
            .autoReload(true)
            .sandboxBackendProvider(provider)
            .envContext(env)
            .build();
    }

    private static ReactAgent createAgent(GuardedSkillsAgentHook skillsHook) {
        return ReactAgent.builder()
            .name("docker-sandbox-agent")
            .model(chatModel)
            .saver(new MemorySaver())
            .hooks(List.of(skillsHook))
            .enableLogging(true)
            .build();
    }

    private static void runTests(ReactAgent agent) {
        testUntrustedCodeExecution(agent);
        testDependencyInstallation(agent);
        testNetworkIsolation(agent);
    }

    private static void testUntrustedCodeExecution(ReactAgent agent) {
        System.out.println("⚠️ 测试 1: 不可信代码执行（在线代码评测）");
        System.out.println("   场景: 用户提交的 Python 代码需要在 Docker 隔离环境中执行");
        System.out.println();

        try {
            AssistantMessage response = agent.call(
                "请在 Docker 隔离沙箱中执行以下 Python 代码，并返回输出结果：\n\n" +
                "```python\n" +
                "import os\n" +
                "import sys\n" +
                "\n" +
                "print('Python 版本:', sys.version)\n" +
                "print('当前工作目录:', os.getcwd())\n" +
                "print('环境变量 PATH:', os.environ.get('PATH', '未设置'))\n" +
                "print('容器隔离测试成功！')\n" +
                "```\n\n" +
                "请验证代码是否在隔离环境中执行，并输出结果。");

            System.out.println("✅ 执行结果:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    private static void testDependencyInstallation(ReactAgent agent) {
        System.out.println("📦 测试 2: 第三方依赖安装");
        System.out.println("   场景: 需要在 Docker 容器中安装 Python 第三方库");
        System.out.println();

        try {
            AssistantMessage response = agent.call(
                "请在 Docker 容器中安装并使用 requests 库：\n\n" +
                "1. 使用 pip install requests 安装依赖\n" +
                "2. 使用 requests.get() 访问 https://httpbin.org/get\n" +
                "3. 输出响应状态码\n\n" +
                "请验证依赖安装是否成功，并输出执行结果。");

            System.out.println("✅ 安装结果:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    private static void testNetworkIsolation(ReactAgent agent) {
        System.out.println("🔌 测试 3: 网络隔离");
        System.out.println("   场景: 验证 Docker 容器网络隔离能力");
        System.out.println();

        try {
            AssistantMessage response = agent.call(
                "请在 Docker 容器中执行以下网络测试：\n\n" +
                "1. 尝试 ping 外部地址（如 8.8.8.8）\n" +
                "2. 尝试 curl 访问 https://www.baidu.com\n" +
                "3. 检查容器内的网络配置（ip addr 或 ifconfig）\n\n" +
                "请输出测试结果，验证网络是否被隔离。");

            System.out.println("✅ 网络测试结果:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }
}
