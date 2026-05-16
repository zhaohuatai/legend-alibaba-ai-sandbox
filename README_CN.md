# Legend Alibaba AI Sandbox - 沙箱框架

基于 Spring AI Alibaba 的安全 Skill 执行沙箱框架，提供 Docker 容器隔离、风险等级驱动的沙箱选择，以及完整的生命周期管理。

## 概述

本框架在 Spring AI Alibaba 的 Skill 系统基础上，增加了安全门控和沙箱隔离能力。确保在 Agent 调用期间，Docker 容器能够被正确管理，具有清晰的生命周期，防止资源泄漏。

### 核心特性

- **风险驱动沙箱选择**：根据 Skill 风险等级自动选择沙箱后端（LOW → 本地进程，MEDIUM/HIGH → Docker 容器）
- **Docker 生命周期管理**：容器按 `invokeId + skillName` 创建，在 `afterAgent` 阶段统一清理
- **安全门控**：工具调用策略强制执行每个 Skill 允许的命令和参数
- **线程安全会话管理**：基于 `ConcurrentMap` 的会话管理器，支持超时清理作为兜底机制
- **渐进式 Skill 披露**：Skill 先按名称/描述列出，完整指令按需加载

## 项目结构

```
org.legend.framework.ai.alibaba.sandbox
├── agent/                              # Agent 相关组件
│   ├── hook/
│   │   └── GuardedSkillsAgentHook.java    # Agent 生命周期钩子（beforeAgent/afterAgent）
│   ├── interceptor/
│   │   └── GuardedSkillsInterceptor.java  # 模型拦截器，创建沙箱实例
│   └── audit/
│       └── AuditLog.java                  # 审计日志
├── backend/                            # 沙箱后端
│   ├── SandboxBackend.java                # 沙箱后端接口
│   ├── SandboxBackendProvider.java        # 基于风险等级的沙箱选择器
│   ├── SandboxSessionManager.java         # 线程安全的会话管理器
│   ├── BackendLease.java                  # 沙箱租约（AutoCloseable）
│   ├── SandboxAwareToolset.java           # 沙箱感知的工具回调
│   ├── ExecResult.java                    # 执行结果记录
│   ├── docker/
│   │   ├── DockerConfig.java              # Docker 配置
│   │   ├── DockerStandaloneSandbox.java   # Docker 容器沙箱
│   │   └── MountConfig.java               # 卷挂载配置
│   └── local/
│       └── LocalProcessSandbox.java       # 本地进程沙箱
├── skills/                             # Skill 相关组件
│   ├── registry/
│   │   └── GuardedSkillRegistry.java      # 带安全元数据的 Skill 注册表
│   ├── env/
│   │   ├── EnvContext.java                # 环境上下文（操作系统、Shell、可执行文件）
│   │   └── EnvProbe.java                  # 环境探测工具
│   ├── manifest/
│   │   ├── SkillManifest.java             # Skill 清单记录
│   │   ├── SkillManifestParser.java       # YAML frontmatter 解析器
│   │   ├── ToolPolicy.java                # 工具调用策略
│   │   └── PolicyDecision.java            # 策略决策结果
│   └── enums/
│       ├── RiskLevel.java                 # 风险等级：LOW / MEDIUM / HIGH
│       ├── OsKind.java                    # 操作系统：WINDOWS / LINUX / MAC
│       └── ShellKind.java                 # Shell 类型：BASH / POWERSHELL / CMD
├── tool/                               # 工具相关组件
│   ├── core/
│   │   ├── ShellTool.java                 # Shell 命令执行
│   │   ├── ReadTool.java                  # 文件读取
│   │   ├── WriteTool.java                 # 文件写入
│   │   ├── EditTool.java                  # 文件编辑
│   │   └── ...                            # 其他文件工具
│   ├── browser/
│   │   └── BrowserToolset.java            # 浏览器自动化工具
│   ├── python/
│   │   └── PythonTool.java                # Python 代码执行
│   ├── mcp/
│   │   └── McpToolset.java                # MCP 协议工具
│   ├── GuardedToolCallback.java           # 安全门控工具回调
│   └── ToolInputs.java                    # 工具输入记录
├── GuardedSkillMetadata.java              # 带安全字段的 Skill 元数据
├── SandboxConstants.java                  # 常量（invokeId 键、提示模板）
├── GlobalPolicy.java                      # 全局安全策略
└── ToolDeniedException.java               # 工具访问拒绝异常
```

## 快速开始

### 前置条件

- Java 25+
- Maven 3.6+
- Docker Desktop（用于 Docker 沙箱模式）
- DashScope API 密钥

### 1. 添加依赖

```xml
<dependency>
    <groupId>legend-framework</groupId>
    <artifactId>legend-alibaba-ai-sandbox</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. 创建 Skill 目录

在你的 Skill 目录中创建一个 `SKILL.md` 文件：

```markdown
---
name: code-executor
description: 在沙箱中执行 Python 代码
version: 1.0.0
risk-level: high
network: false
allowed-tools:
  - Bash(command:python3 *.py)
  - Read
  - Write
---

# 代码执行器 Skill

本 Skill 在沙箱环境中执行 Python 代码。

## 使用方法
1. 将 Python 代码写入 /work/script.py
2. 执行: python3 /work/script.py
3. 从 /work/output.txt 读取输出
```

### 3. 构建并运行

```java
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvContext;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvProbe;
import org.legend.framework.ai.alibaba.sandbox.skills.registry.GuardedSkillRegistry;
import org.legend.framework.ai.alibaba.sandbox.agent.hook.GuardedSkillsAgentHook;
import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackendProvider;
import org.legend.framework.ai.alibaba.sandbox.backend.docker.DockerConfig;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;

import java.nio.file.Paths;
import java.util.List;

public class MyAgentApp {
    public static void main(String[] args) {
        // 1. 探测当前环境
        EnvContext env = EnvProbe.probe(Paths.get(System.getProperty("user.dir")));

        // 2. 创建沙箱提供者（使用 Docker 配置）
        SandboxBackendProvider provider = new SandboxBackendProvider(env, DockerConfig.defaults());

        // 3. 注册 Skills
        GuardedSkillRegistry registry = GuardedSkillRegistry.builder()
            .skillsDirectory("/path/to/skills")
            .env(env)
            .autoLoad(true)
            .build();

        // 4. 创建安全门控 Hook
        GuardedSkillsAgentHook hook = GuardedSkillsAgentHook.builder()
            .skillRegistry(registry)
            .autoReload(true)
            .sandboxBackendProvider(provider)
            .envContext(env)
            .build();

        // 5. 创建聊天模型
        DashScopeApi dashScopeApi = DashScopeApi.builder()
            .apiKey(System.getenv("DASHSCOPE_API_KEY"))
            .build();
        ChatModel chatModel = DashScopeChatModel.builder()
            .dashScopeApi(dashScopeApi)
            .build();

        // 6. 创建 Agent
        ReactAgent agent = ReactAgent.builder()
            .name("my-guarded-agent")
            .model(chatModel)
            .saver(new MemorySaver())
            .hooks(List.of(hook))
            .enableLogging(true)
            .build();

        // 7. 运行
        agent.call("使用 code-executor Skill 运行一个 Python 脚本");
    }
}
```

## 核心概念

### 风险等级

| 等级 | 沙箱 | 隔离级别 | 适用场景 |
|------|------|---------|---------|
| `LOW` | LocalProcessSandbox | 进程级 | 只读操作（Read、Grep、Glob） |
| `MEDIUM` | DockerStandaloneSandbox | 容器级 | 读写操作（Write、Edit） |
| `HIGH` | DockerStandaloneSandbox | 容器级 + 网络隔离 | 命令执行（Bash）、不可信代码 |

### Docker 生命周期

```
beforeAgent()                    interceptModel()                    afterAgent()
    │                                 │                                    │
    ├─ 生成 invokeId                  ├─ 从 read_tool 调用中提取           ├─ 从 OverAllState
    │   (UUID)                        │   Skill 名称                        │   获取 invokeId
    │                                 ├─ 对每个 Skill:                      ├─ cleanupByInvokeId()
    │                                 │   getOrCreateLease()                   - 删除所有容器
    │                                 │   创建 Docker 容器                     - 关闭所有租约
    │                                 │   注入沙箱感知工具                    │
    │                                 │                                    │
    ▼                                 ▼                                    ▼
```

### 会话管理器

`SandboxSessionManager` 使用两级嵌套 Map：

```
ConcurrentMap<String, Map<String, BackendLease>>
  ├─ invokeId（Agent 调用标识符）
  │    └─ skillName（Skill 名称）
  │         └─ BackendLease（沙箱租约）
```

- **创建**：在 `GuardedSkillsInterceptor.interceptModel()` 中，检测到 Skill 时创建
- **清理**：在 `GuardedSkillsAgentHook.afterAgent()` 中，关闭某个 invokeId 的所有租约
- **超时兜底**：`cleanupTimeoutSessions()` 定期清理泄漏的容器

### Skill 清单格式

```yaml
---
name: my-skill
description: 这个 Skill 的功能描述
version: 1.0.0
risk-level: low | medium | high
network: true | false
allowed-tools:
  - Read
  - Write
  - Bash(command:python3 *.py)
  - Bash(command:ls *)
---

# Skill 正文
使用说明、工作流、示例...
```

## 配置说明

### Docker 配置

```java
DockerConfig config = DockerConfig.defaults()
    .withImage("skill-sandbox:latest")
    .withHostWorkspaceRoot(Paths.get("/path/to/workspaces"))
    .withMount(Paths.get("/host/skill"), "/skill", true)   // 只读挂载
    .withMount(Paths.get("/host/output"), "/output", false); // 读写挂载
```

### 环境上下文

```java
EnvContext env = EnvProbe.probe(Paths.get(System.getProperty("user.dir")));
// env.os()        → OsKind.WINDOWS
// env.shellKind() → ShellKind.POWERSHELL
// env.cwd()       → 当前工作目录
// env.availableExecutables() → [python, node, git, ...]
```

## 测试

测试类位于 `src/test/java/org/legend/framework/ai/alibaba/sandbox/demo/` 目录：

| 测试类 | 说明 |
|--------|------|
| `GuardedSkillDemo` | 基础 Skill 集成演示 |
| `GuardDockerMountTest` | Docker 卷挂载测试 |
| `ContainerLifecycleTest` | 容器生命周期验证 |
| `GuardDockerSandboxSkillTest` | Docker 沙箱隔离测试 |
| `GuardSkillSecurityTest` | 安全策略测试 |
| `GuardBashToolTest` | Shell 工具执行测试 |
| `GuardDataAnalysisSkillTest` | 数据分析 Skill 测试 |
| `GuardFileOrganizerSkillTest` | 文件整理 Skill 测试 |
| `EnvInfoTest` | 环境信息工具测试 |

运行测试：

```bash
mvn test -Dtest=GuardedSkillDemo
```

## 依赖项

| 依赖 | 版本 | 用途 |
|------|------|------|
| Spring AI Alibaba | 1.1.2.2 | Agent 框架 |
| Spring Boot | 3.5.13 | 应用框架 |
| docker-java | 3.7.1 | Docker API 客户端 |
| Lombok | 1.18.42 | 代码生成 |

## 许可证

legend-framework
