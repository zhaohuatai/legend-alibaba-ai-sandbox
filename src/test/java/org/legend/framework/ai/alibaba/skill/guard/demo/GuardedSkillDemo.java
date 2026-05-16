package org.legend.framework.ai.alibaba.skill.guard.demo;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.shelltool.ShellToolAgentHook;
import com.alibaba.cloud.ai.graph.agent.tools.ShellTool2;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;

import org.legend.framework.ai.alibaba.sandbox.GuardedSkillMetadata;
import org.legend.framework.ai.alibaba.sandbox.agent.hook.GuardedSkillsAgentHook;
import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackendProvider;
import org.legend.framework.ai.alibaba.sandbox.backend.docker.DockerConfig;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvContext;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvProbe;
import org.legend.framework.ai.alibaba.sandbox.skills.registry.GuardedSkillRegistry;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;

import java.nio.file.Paths;
import java.util.List;

/**
 * 带安全门控的 Skill 集成 Demo。
 * 
 * <p>该 Demo 演示如何使用 GuardedSkillRegistry 和 GuardedSkillsAgentHook
 * 与 Spring AI Alibaba 框架集成，提供完整的 Skill 安全门控功能。
 * 
 * <p>使用方式：
 * <ol>
 *   <li>准备 Skill 目录（如 ~/.claude/skills 或 ./skills）</li>
 *   <li>在 Skill 目录中创建 SKILL.md 文件</li>
 *   <li>运行该 Demo，Agent 将自动加载 Skill 并应用安全门控</li>
 * </ol>
 * 
 * <p>示例 SKILL.md：
 * <pre>
 * ---
 * name: pdf-extractor
 * description: Extract text and metadata from PDF files
 * version: 1.0.0
 * risk-level: low
 * network: false
 * allowed-tools:
 *   - Read
 *   - Bash(command:python3 *.py *.pdf)
 * ---
 * 
 * # PDF Extractor Skill
 * 
 * This skill extracts text and metadata from PDF files.
 * 
 * ## Usage
 * 1. Use Read tool to read PDF file path
 * 2. Use Bash tool to run python3 script
 * </pre>
 */
public class GuardedSkillDemo {

    static DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(TestConfig.API_KEY).build();

    static ChatModel chatModel = DashScopeChatModel.builder()
            .dashScopeApi(dashScopeApi)
            .defaultOptions(DashScopeChatOptions.builder()
                .model(DashScopeChatModel.DEFAULT_MODEL_NAME)
                .temperature(0.5)
                .maxToken(1000)
                .build())
            .build();

    public static void main(String[] args) {
        try {
            EnvContext envContext = EnvProbe.probe(Paths.get(System.getProperty("user.dir")));

            SandboxBackendProvider sandboxBackendProvider = new SandboxBackendProvider(envContext, DockerConfig.defaults());

            GuardedSkillRegistry registry = GuardedSkillRegistry.builder()
                .skillsDirectory("E:\\worksapce\\sts5.1\\legend-smartmind\\legend-smartmind\\src\\main\\resources\\skills")
//                .skillsDirectory(Paths.get(System.getProperty("user.dir"), "skills").toString(), "project")
                .env(envContext)
                .autoLoad(true)
                .build();

            System.out.println("Loaded " + registry.size() + " guarded skills");
            for (GuardedSkillMetadata skill : registry.listAll().stream()
                    .filter(m -> m instanceof GuardedSkillMetadata)
                    .map(m -> (GuardedSkillMetadata) m)
                    .toList()) {
                System.out.println("  - " + skill.getName() + " (riskLevel=" + skill.getRiskLevel() + ")");
            }

            GuardedSkillsAgentHook skillsHook = GuardedSkillsAgentHook.builder()
                .skillRegistry(registry)
                .autoReload(true)
                .sandboxBackendProvider(sandboxBackendProvider)
                .envContext(envContext)
                .build();

//            ShellToolAgentHook shellHook = ShellToolAgentHook.builder()
//                .shellTool2(ShellTool2.builder(System.getProperty("user.dir")).build())
//                .build();

            ReactAgent agent = ReactAgent.builder()
                .name("guarded-skill-agent")
                .model(chatModel)
                .saver(new MemorySaver())
                .hooks(List.of(skillsHook))
                .enableLogging(true)
                .build();

            System.out.println("\n=== Guarded Skill Demo ===");
            AssistantMessage response = agent.call("请帮我查看当前目录下的文件列表");
            System.out.println("-----------------------");
            System.out.println(response.getText());

        } catch (GraphRunnerException e) {
            e.printStackTrace();
        }
    }
}
