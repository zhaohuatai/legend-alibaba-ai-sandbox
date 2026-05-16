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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Docker 目录挂载测试类。
 *
 * <p>测试 Docker 容器多目录挂载功能，验证以下目录映射：
 * <ul>
 *   <li>src/main/resources/skills/code-executor/skill → /skill/ (Skill 目录，只读)</li>
 *   <li>src/main/resources/skills/code-executor/data/input → /data/input/ (输入数据，只读)</li>
 *   <li>src/main/resources/skills/code-executor/workspaces/{uuid} → /work/ (工作区，读写)</li>
 *   <li>src/main/resources/skills/code-executor/output → /output/ (输出目录，读写)</li>
 * </ul>
 *
 * <p>前置条件：
 * <ul>
 *   <li>已安装 Docker Desktop 并运行</li>
 *   <li>已构建 skill-sandbox:latest 镜像</li>
 *   <li>已配置 DASHSCOPE_API_KEY 环境变量</li>
 * </ul>
 */
public class GuardDockerMountTest {

    private static final Path SKILLS_DIR = Paths.get("src/main/resources/skills");
    private static final Path TEST_BASE_DIR = Paths.get("src/main/resources/skills/code-executor");

    static ChatModel chatModel = DashScopeChatModel.builder()
            .dashScopeApi(DashScopeApi.builder().apiKey(TestConfig.API_KEY).build())
            .build();

    private static void runMountTests(ReactAgent agent) {
        testWorkspaceReadWrite(agent);
//        testSkillDirectoryReadOnly(agent);
//        testInputDirectoryReadOnly(agent);
//        testOutputDirectoryReadWrite(agent);
//        testCrossDirectoryDataFlow(agent);
    }
    
    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("   Docker 目录挂载测试");
        System.out.println("═══════════════════════════════════════════════════\n");

        try {
            if (!checkDockerAvailable()) {
                System.err.println("❌ Docker 不可用，跳过测试");
                return;
            }

            EnvContext env = setupEnvironment();
            DockerConfig dockerConfig = createDockerConfig();
            GuardedSkillRegistry registry = createRegistry(env);
            GuardedSkillsAgentHook skillsHook = createSkillsHook(registry, env, dockerConfig);
            ReactAgent agent = createAgent(skillsHook);

            runMountTests(agent);

        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n✅ Docker 目录挂载测试完成！");
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
                return false;
            }
        } catch (Exception e) {
            System.out.println("   ❌ Docker 未安装");
            return false;
        }
    }

    private static EnvContext setupEnvironment() {
        System.out.println("📋 初始化环境...");
        EnvContext env = EnvProbe.probe(Paths.get(System.getProperty("user.dir")));
        System.out.println("   操作系统: " + env.os());
        System.out.println("   Shell: " + env.shellKind());
        System.out.println("   工作目录: " + env.cwd());
        System.out.println();
        return env;
    }

    private static DockerConfig createDockerConfig() {
        System.out.println("🐳 创建 Docker 配置...");
        try {
            Path workspaceRoot = TEST_BASE_DIR.resolve("workspaces");
            System.out.println("   📁 工作区目录: " + workspaceRoot.toAbsolutePath());

            ensureDirectoryExists(TEST_BASE_DIR.resolve("skill"));
            ensureDirectoryExists(TEST_BASE_DIR.resolve("data/input"));
            ensureDirectoryExists(TEST_BASE_DIR.resolve("output"));

            DockerConfig cfg = DockerConfig.defaults()
                .withHostWorkspaceRoot(workspaceRoot)
                .withMount(TEST_BASE_DIR.resolve("skill"), "/skill", true)
                .withMount(TEST_BASE_DIR.resolve("data/input"), "/data/input", true)
                .withMount(TEST_BASE_DIR.resolve("output"), "/output", false);

            System.out.println("   ✅ Docker 配置已创建");
            System.out.println("   📂 目录映射:");
            System.out.println("      " + TEST_BASE_DIR.resolve("skill").toAbsolutePath() + " → /skill/ (只读)");
            System.out.println("      " + TEST_BASE_DIR.resolve("data/input").toAbsolutePath() + " → /data/input/ (只读)");
            System.out.println("      " + TEST_BASE_DIR.resolve("output").toAbsolutePath() + " → /output/ (读写)");
            System.out.println("      " + workspaceRoot.toAbsolutePath() + " → /work/ (读写)");
            System.out.println();
            return cfg;
        } catch (Exception e) {
            System.err.println("   ❌ Docker 配置创建失败: " + e.getMessage());
            throw e;
        }
    }

    private static void ensureDirectoryExists(Path dir) {
        try {
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
                System.out.println("   📁 创建目录: " + dir);
            }
        } catch (IOException e) {
            System.err.println("   ⚠️ 创建目录失败: " + dir + " - " + e.getMessage());
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
            .name("docker-mount-agent")
            .model(chatModel)
            .saver(new MemorySaver())
            .hooks(List.of(skillsHook))
            .enableLogging(true)
            .build();
    }

   

    private static void testWorkspaceReadWrite(ReactAgent agent) {
        System.out.println("📝 测试 1: 工作区 /work/ 读写能力");
        System.out.println("   场景: 在 /work/ 目录创建文件并读取");
        System.out.println();

        try {
            AssistantMessage response = agent.call(
               
            	"请利用docker的相关skilln\\n"+
            	 " 在 /work/ 目录执行以下操作：\n\n" +
                "1. 创建文件 /work/test.txt，内容为 'Hello from /work/'\n" +
                "2. 读取 /work/test.txt 的内容并输出\n" +
                "3. 使用 ls -la /work/ 列出目录内容\n" +
                "4. 使用 pwd 确认当前工作目录\n\n" +
                "请输出所有执行结果。");

            System.out.println("✅ 测试结果:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    private static void testSkillDirectoryReadOnly(ReactAgent agent) {
        System.out.println("📝 测试 2: Skill 目录 /skill/ 只读验证");
        System.out.println("   场景: 验证 /skill/ 目录存在但不可写入");
        System.out.println();

        try {
            AssistantMessage response = agent.call(
                "请对 /skill/ 目录执行以下操作：\n\n" +
                "1. 使用 ls -la /skill/ 列出目录内容\n" +
                "2. 尝试在 /skill/ 创建文件（应该失败）：touch /skill/test.txt\n" +
                "3. 如果创建失败，输出 '✅ 只读验证成功：/skill/ 不可写入'\n" +
                "4. 如果创建成功，输出 '❌ 只读验证失败：/skill/ 可写入'\n\n" +
                "请输出所有执行结果。");

            System.out.println("✅ 测试结果:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    private static void testInputDirectoryReadOnly(ReactAgent agent) {
        System.out.println("📝 测试 3: 输入数据目录 /data/input/ 只读验证");
        System.out.println("   场景: 验证 /data/input/ 目录存在但不可写入");
        System.out.println();

        try {
            Path inputDir = TEST_BASE_DIR.resolve("data/input");
            Path testFile = inputDir.resolve("test_input.txt");
            Files.writeString(testFile, "This is test input data from host");
            System.out.println("   📁 已在宿主机创建测试文件: " + testFile.toAbsolutePath());

            AssistantMessage response = agent.call(
                "请对 /data/input/ 目录执行以下操作：\n\n" +
                "1. 使用 ls -la /data/input/ 列出目录内容\n" +
                "2. 读取 /data/input/test_input.txt 的内容\n" +
                "3. 尝试在 /data/input/ 创建文件（应该失败）：touch /data/input/new_file.txt\n" +
                "4. 如果创建失败，输出 '✅ 只读验证成功：/data/input/ 不可写入'\n" +
                "5. 如果创建成功，输出 '❌ 只读验证失败：/data/input/ 可写入'\n\n" +
                "请输出所有执行结果。");

            System.out.println("✅ 测试结果:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));

            Files.deleteIfExists(testFile);
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    private static void testOutputDirectoryReadWrite(ReactAgent agent) {
        System.out.println("📝 测试 4: 输出目录 /output/ 读写能力");
        System.out.println("   场景: 在 /output/ 目录创建文件并在宿主机验证");
        System.out.println();

        try {
            AssistantMessage response = agent.call(
                "请在 /output/ 目录执行以下操作：\n\n" +
                "1. 使用 ls -la /output/ 列出目录内容\n" +
                "2. 创建文件 /output/test_output.txt，内容为 'Hello from container!'\n" +
                "3. 读取 /output/test_output.txt 的内容并输出\n" +
                "4. 使用 pwd 确认当前工作目录\n\n" +
                "请输出所有执行结果。");

            System.out.println("✅ 容器内测试结果:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));

            Path outputFile = TEST_BASE_DIR.resolve("output/test_output.txt");
            if (Files.exists(outputFile)) {
                String content = Files.readString(outputFile);
                System.out.println("✅ 宿主机验证成功:");
                System.out.println("   文件路径: " + outputFile.toAbsolutePath());
                System.out.println("   文件内容: " + content);
                Files.delete(outputFile);
            } else {
                System.out.println("❌ 宿主机验证失败: 文件不存在 - " + outputFile.toAbsolutePath());
            }
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }

    private static void testCrossDirectoryDataFlow(ReactAgent agent) {
        System.out.println("📝 测试 5: 跨目录数据流测试");
        System.out.println("   场景: 从 /data/input/ 读取数据，在 /work/ 处理，输出到 /output/");
        System.out.println();

        try {
            Path inputDir = TEST_BASE_DIR.resolve("data/input");
            Path inputFile = inputDir.resolve("data.csv");
            Files.writeString(inputFile, "name,age,city\nAlice,30,Beijing\nBob,25,Shanghai\nCharlie,35,Guangzhou");
            System.out.println("   📁 已创建输入数据: " + inputFile.toAbsolutePath());

            AssistantMessage response = agent.call(
                "请执行以下数据处理流程：\n\n" +
                "1. 读取 /data/input/data.csv 的内容\n" +
                "2. 使用 Python 解析 CSV 数据\n" +
                "3. 计算平均年龄\n" +
                "4. 将处理结果写入 /output/result.txt\n" +
                "5. 将处理脚本保存到 /work/process.py\n" +
                "6. 输出所有执行结果\n\n" +
                "请使用以下 Python 代码：\n" +
                "```python\n" +
                "import csv\n\n" +
                "with open('/data/input/data.csv', 'r') as f:\n" +
                "    reader = csv.DictReader(f)\n" +
                "    rows = list(reader)\n\n" +
                "avg_age = sum(int(r['age']) for r in rows) / len(rows)\n\n" +
                "with open('/output/result.txt', 'w') as f:\n" +
                "    f.write(f'总人数：{len(rows)}\\n')\n" +
                "    f.write(f'平均年龄：{avg_age:.1f}\\n')\n" +
                "    f.write(f'处理完成！\\n')\n\n" +
                "print('处理完成！')\n" +
                "print(f'总人数：{len(rows)}')\n" +
                "print(f'平均年龄：{avg_age:.1f}')\n" +
                "```\n\n" +
                "请输出所有执行结果。");

            System.out.println("✅ 容器内处理结果:");
            System.out.println("   " + response.getText().replace("\n", "\n   "));

            Path outputFile = TEST_BASE_DIR.resolve("output/result.txt");
            if (Files.exists(outputFile)) {
                String content = Files.readString(outputFile);
                System.out.println("✅ 输出文件验证成功:");
                System.out.println("   文件路径: " + outputFile.toAbsolutePath());
                System.out.println("   文件内容:");
                System.out.println("   " + content.replace("\n", "\n   "));
                Files.delete(outputFile);
            } else {
                System.out.println("❌ 输出文件验证失败: 文件不存在 - " + outputFile.toAbsolutePath());
            }

            Files.deleteIfExists(inputFile);
        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println();
    }
}
