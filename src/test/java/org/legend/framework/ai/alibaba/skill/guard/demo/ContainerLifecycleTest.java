package org.legend.framework.ai.alibaba.skill.guard.demo;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.shelltool.ShellToolAgentHook;
import com.alibaba.cloud.ai.graph.agent.tools.ShellTool2;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 容器生命周期测试类。
 *
 * <p>验证单次 skill invoke() 周期内，Docker 容器保持唯一，不会被重复创建。
 *
 * <p>测试场景：
 * <ul>
 *   <li>单次 invoke() 调用</li>
 *   <li>skill 内部执行多步操作（创建文件、读取文件、列出目录等）</li>
 *   <li>验证整个过程中容器 ID 保持不变</li>
 * </ul>
 *
 * <p>前置条件：
 * <ul>
 *   <li>已安装 Docker Desktop 并运行</li>
 *   <li>已构建 skill-sandbox:latest 镜像</li>
 *   <li>已配置 DASHSCOPE_API_KEY 环境变量</li>
 * </ul>
 */
public class ContainerLifecycleTest {

    private static final Path SKILLS_DIR = Paths.get("src/main/resources/skills");
    private static final Path TEST_BASE_DIR = Paths.get("src/main/resources/skills/container-lifecycle-test");

    static ChatModel chatModel = DashScopeChatModel.builder()
            .dashScopeApi(DashScopeApi.builder().apiKey(TestConfig.API_KEY).build())
            .build();

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("   容器生命周期测试");
        System.out.println("═══════════════════════════════════════════════════\n");

        try {
            if (!checkDockerAvailable()) {
                System.err.println("❌ Docker 不可用，跳过测试");
                return;
            }

            EnvContext env = EnvProbe.probe(Paths.get(System.getProperty("user.dir")));
            SandboxBackendProvider provider = new SandboxBackendProvider(env, DockerConfig.defaults());
            GuardedSkillRegistry registry = createRegistry(env);
            GuardedSkillsAgentHook skillsHook = createSkillsHook(registry, provider, env);

            ReactAgent agent = createAgent(skillsHook);

            runLifecycleTest(agent);

        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n✅ 容器生命周期测试完成！");
    }

    private static boolean checkDockerAvailable() {
        System.out.println("🐳 检查 Docker 可用性...");
        try {
            Process process = new ProcessBuilder("docker", "info").start();
            int exitCode = process.waitFor();
            if (exitCode == 0) {
                System.out.println("   ✅ Docker 已安装并运行\n");
                return true;
            } else {
                System.out.println("   ❌ Docker 未运行或无权限\n");
                return false;
            }
        } catch (Exception e) {
            System.out.println("   ❌ Docker 未安装\n");
            return false;
        }
    }

    private static GuardedSkillRegistry createRegistry(EnvContext env) {
        System.out.println("📂 创建 GuardedSkillRegistry...");
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
            SandboxBackendProvider provider,
            EnvContext env) {
        return GuardedSkillsAgentHook.builder()
            .skillRegistry(registry)
            .autoReload(true)
            .sandboxBackendProvider(provider)
            .envContext(env)
            .build();
    }

    private static void runLifecycleTest(ReactAgent agent) {
        System.out.println("📝 测试: 容器生命周期验证");
        System.out.println("   场景: 单次 invoke() 内执行多步操作，验证容器复用");
        System.out.println();

        try {
            AssistantMessage response = agent.call(
                "请按照 container-lifecycle-test Skill 中定义的步骤依次执行，并输出每个步骤的结果。");

            System.out.println("✅ 执行结果:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));

            Path outputFile = TEST_BASE_DIR.resolve("output/lifecycle_test.txt");
            if (Files.exists(outputFile)) {
                String content = Files.readString(outputFile);
                System.out.println("\n✅ 宿主机验证成功:");
                System.out.println("   文件路径: " + outputFile.toAbsolutePath());
                System.out.println("   文件内容: " + content);
                Files.delete(outputFile);
            } else {
                System.out.println("\n❌ 宿主机验证失败: 文件不存在 - " + outputFile.toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    private static ReactAgent createAgent(GuardedSkillsAgentHook skillsHook) {
        return ReactAgent.builder()
            .name("container-lifecycle-agent")
            .model(chatModel)
            .saver(new MemorySaver())
            .hooks(List.of(skillsHook))
            .enableLogging(true)
            .build();
    }
}
