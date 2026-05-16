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
 * Skill 安全测试类。
 *
 * <p>测试 Skill 安全策略和防护机制，包括：
 * <ul>
 *   <li>工具调用策略验证</li>
 *   <li>风险级别控制</li>
 *   <li>审计日志记录</li>
 * </ul>
 */
public class GuardSkillSecurityTest {

    private static final Path SKILLS_DIR = Paths.get("src/main/resources/skills");

    static ChatModel chatModel = DashScopeChatModel.builder()
            .dashScopeApi(DashScopeApi.builder().apiKey(TestConfig.API_KEY).build())
            .build();

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("   Skill Security Test");
        System.out.println("═══════════════════════════════════════════════════\n");

        try {
            EnvContext env = setupEnvironment();
            GuardedSkillRegistry registry = createRegistry(env);
            GuardedSkillsAgentHook skillsHook = createSkillsHook(registry, env);
            ReactAgent agent = createAgent(skillsHook);

            runTests(agent, registry);

        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n✅ Skill Security 测试完成！");
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
            System.out.println("   - " + skill.getName() + " (riskLevel=" + skill.getRiskLevel() + ")");
        }
        System.out.println();
        return registry;
    }

    private static GuardedSkillsAgentHook createSkillsHook(GuardedSkillRegistry registry, EnvContext env) {
        SandboxBackendProvider provider = new SandboxBackendProvider(env, DockerConfig.defaults());
        return GuardedSkillsAgentHook.builder()
            .skillRegistry(registry)
            .autoReload(true)
            .sandboxBackendProvider(provider)
            .envContext(env)
            .build();
    }

    private static ReactAgent createAgent(GuardedSkillsAgentHook skillsHook) {
        return ReactAgent.builder()
            .name("security-test-agent")
            .model(chatModel)
            .saver(new MemorySaver())
            .hooks(List.of(skillsHook))
            .enableLogging(true)
            .build();
    }

    private static void runTests(ReactAgent agent, GuardedSkillRegistry registry) {
        testRiskLevelControl(agent, registry);
        testToolPolicy(agent);
        testAuditLogging(agent);
    }

    private static void testRiskLevelControl(ReactAgent agent, GuardedSkillRegistry registry) {
        System.out.println("🔒 测试 1: 风险级别控制");
        System.out.println("   场景: 验证不同风险级别的 Skill 执行策略");
        System.out.println();

        try {
            System.out.println("   📋 已注册 Skill 风险级别:");
            for (GuardedSkillMetadata skill : registry.listAll().stream()
                    .filter(m -> m instanceof GuardedSkillMetadata)
                    .map(m -> (GuardedSkillMetadata) m)
                    .toList()) {
                System.out.println("      - " + skill.getName() + ": " + skill.getRiskLevel());
            }
            System.out.println();

            AssistantMessage response = agent.call(
                "请执行以下操作并输出结果：\n\n" +
                "1. 列出当前目录下的所有文件\n" +
                "2. 读取 SKILL.md 文件（如果存在）\n" +
                "3. 输出当前工作目录\n\n" +
                "请使用 file-organizer Skill 来执行这些操作。");

            System.out.println("✅ 执行结果:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    private static void testToolPolicy(ReactAgent agent) {
        System.out.println("🛡️ 测试 2: 工具调用策略");
        System.out.println("   场景: 验证 Skill 的工具调用是否符合策略");
        System.out.println();

        try {
            AssistantMessage response = agent.call(
                "请使用 code-executor Skill 执行以下 Python 代码：\n\n" +
                "```python\n" +
                "print('Hello from Python!')\n" +
                "import os\n" +
                "print('Current directory:', os.getcwd())\n" +
                "```\n\n" +
                "请输出执行结果。");

            System.out.println("✅ 执行结果:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    private static void testAuditLogging(ReactAgent agent) {
        System.out.println("📝 测试 3: 审计日志记录");
        System.out.println("   场景: 验证 Skill 执行过程中的审计日志");
        System.out.println();

        try {
            AssistantMessage response = agent.call(
                "请执行以下操作并输出结果：\n\n" +
                "1. 使用 data-analysis Skill 分析以下 CSV 文件：\n" +
                "   E:\\worksapce\\sts5.1\\legend-smartmind\\legend-smartmind\\src\\main\\resources\\skills\\data-analysis\\sample_data.csv\n" +
                "2. 输出分析结果\n" +
                "3. 记录操作日志\n\n" +
                "请输出所有执行结果。");

            System.out.println("✅ 执行结果:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }
}
