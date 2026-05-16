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
 * Data Analysis Skill 测试类。
 *
 * <p>测试数据分析 Skill 的完整功能，包括：
 * <ul>
 *   <li>Skill 注册和解析</li>
 *   <li>CSV 文件读取和分析</li>
 *   <li>统计报告生成</li>
 * </ul>
 */
public class GuardDataAnalysisSkillTest {

    private static final Path SKILLS_DIR = Paths.get("src/main/resources/skills");
    private static final String CSV_FILE_PATH = "E:\\worksapce\\sts5.1\\legend-smartmind\\legend-smartmind\\src\\main\\resources\\skills\\data-analysis\\sample_data.csv";
    
    static ChatModel chatModel = DashScopeChatModel.builder()
            .dashScopeApi(DashScopeApi.builder().apiKey(TestConfig.API_KEY).build())
            .build();
    private static void runTests(ReactAgent agent) {
    	//  testCsvAnalysis(agent);
    //    testStatisticsSummary(agent);
      testDataVisualization(agent);
    }

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("   Data Analysis Skill Test");
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

        System.out.println("\n✅ Data Analysis Skill 测试完成！");
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
            .name("data-analysis-agent")
            .model(chatModel)
            .saver(new MemorySaver())
            .hooks(List.of(skillsHook))
            .enableLogging(true)
            .build();
    }

 
    private static void testCsvAnalysis(ReactAgent agent) {
        System.out.println("📊 测试 1: CSV 文件读取和分析");
        System.out.println("   文件: " + CSV_FILE_PATH);
        System.out.println();

        try {
            AssistantMessage response = agent.call(
                "请读取并分析以下 CSV 文件：" + CSV_FILE_PATH + "\n\n" +
                "请输出：\n" +
                "1. 文件的列名和数据类型\n" +
                "2. 数据行数\n" +
                "3. 前 5 行数据示例\n" +
                "4. 分析结果放到 E:\\worksapce\\sts5.1\\legend-smartmind\\legend-smartmind\\src\\main\\resources\\skills\\data-analysis");

            System.out.println("✅ 分析结果:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    private static void testStatisticsSummary(ReactAgent agent) {
        System.out.println("📈 测试 2: 统计摘要生成");
        System.out.println();

        try {
            AssistantMessage response = agent.call(
                "请对以下 CSV 文件生成统计摘要：" + CSV_FILE_PATH + "\n\n" +
                "请计算：\n" +
                "1. 每个部门的员工数量\n" +
                "2. 平均薪资\n" +
                "3. 最高和最低薪资\n" +
                "4. 绩效等级分布");

            System.out.println("✅ 统计摘要:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    private static void testDataVisualization(ReactAgent agent) {
        System.out.println("📉 测试 3: 数据可视化建议");
        System.out.println();

        try {
            AssistantMessage response = agent.call(
                "基于以下 CSV 文件的数据，推荐适合的可视化方案：" + CSV_FILE_PATH + "\n\n" +
                "请推荐：\n" +
                "1. 适合展示部门薪资分布的图表类型\n" +
                "2. 适合展示绩效等级分布的图表类型\n" +
                "3. 具体的 Python 可视化代码示例（使用 matplotlib 或 seaborn）");

            System.out.println("✅ 可视化建议:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }
}
