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
 * File Organizer Skill 测试类。
 *
 * <p>测试文件整理 Skill 的完整功能，包括：
 * <ul>
 *   <li>Skill 注册和解析</li>
 *   <li>目录扫描和文件分类</li>
 *   <li>文件整理建议生成</li>
 * </ul>
 */
public class GuardFileOrganizerSkillTest {

    private static final Path SKILLS_DIR = Paths.get("src/main/resources/skills");
    private static final String TARGET_DIR = "E:\\worksapce\\sts5.1\\legend-smartmind\\legend-smartmind\\src\\test-resources\\file-organizer";

    static ChatModel chatModel = DashScopeChatModel.builder()
            .dashScopeApi(DashScopeApi.builder().apiKey(TestConfig.API_KEY).build())
            .build();

    private static void runTests(ReactAgent agent) {
    	 testDirectoryScanning(agent);
    //      testFileCategorization(agent);
    	 //testOrganizationScript(agent);
    }
    
    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("   File Organizer Skill Test");
        System.out.println("═══════════════════════════════════════════════════\n");

        try {
            EnvContext env = setupEnvironment();
            GuardedSkillRegistry registry = createRegistry(env);
            GuardedSkillsAgentHook skillsHook = createSkillsHook(registry, env);
            ReactAgent agent = createAgent(skillsHook);

            runTests(agent);

        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n✅ File Organizer Skill 测试完成！");
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
            System.out.println("   - " + skill.getName() + ": " + skill.getDescription());
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
            .name("file-organizer-agent")
            .model(chatModel)
            .saver(new MemorySaver())
            .hooks(List.of(skillsHook))
            .enableLogging(true)
            .build();
    }



    private static void testDirectoryScanning(ReactAgent agent) {
        System.out.println("📁 测试 1: 目录扫描");
        System.out.println("   目标目录: " + TARGET_DIR);
        System.out.println();
        
        try {
            AssistantMessage response = agent.call(
                "请使用选择合适的 skill 来扫描以下目录结构，列出所有文件和子目录：" + TARGET_DIR + "\n\n" +
                "请输出：\n" +
                "1. 目录树结构（最多 3 层）\n" +
                "2. 文件总数和总大小\n" +
                "3. 按扩展名分类的文件数量统计");

            System.out.println("✅ 扫描结果:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    private static void testFileCategorization(ReactAgent agent) {
        System.out.println("🗂️ 测试 2: 文件分类");
        System.out.println();

        try {
            AssistantMessage response = agent.call(
                "请使用 file-organizer skill 来对以下目录中的文件进行分类整理：" + TARGET_DIR + "\n\n" +
                "请先调用 read_skill 读取 file-organizer 的完整指令，然后按照 skill 的要求执行。\n" +
                "请按以下类别分类：\n" +
                "1. 源代码文件（.java, .py, .js, .ts 等）\n" +
                "2. 配置文件（.xml, .yml, .yaml, .properties, .json 等）\n" +
                "3. 文档文件（.md, .txt, .pdf, .docx 等）\n" +
                "4. 构建产物（.jar, .class, .war 等）\n" +
                "5. 其他文件\n\n" +
                "请输出每个类别的文件列表和数量");

            System.out.println("✅ 分类结果:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    private static void testOrganizationScript(ReactAgent agent) {
        System.out.println("📝 测试 3: 生成整理脚本");
        System.out.println();

        try {
            AssistantMessage response = agent.call(
                "请使用 file-organizer skill 来为以下目录生成一个 PowerShell 脚本，用于自动整理文件：" + TARGET_DIR + "\n\n" +
                "请先调用 read_skill 读取 file-organizer 的完整指令，然后按照 skill 的要求执行。\n" +
                "脚本要求：\n" +
                "1. 创建子目录：source-code, config, docs, build-artifacts, other\n" +
                "2. 按扩展名移动文件到对应子目录\n" +
                "3. 不要移动已存在的子目录\n" +
                "4. 输出移动日志\n\n" +
                "请输出完整的 PowerShell 脚本代码");

            System.out.println("✅ 整理脚本:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }
}
