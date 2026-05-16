# Legend Alibaba AI Sandbox

A secure skill execution sandbox framework for Spring AI Alibaba, providing Docker container isolation, risk-level-based sandbox selection, and complete lifecycle management.

## Overview

This framework extends Spring AI Alibaba's skill system with security gate controls and sandbox isolation. It ensures that during agent invocation, Docker containers are properly managed with a clear lifecycle, preventing resource leaks.

### Key Features

- **Risk-Driven Sandbox Selection**: Automatically selects sandbox backend based on skill risk level (LOW → local process, MEDIUM/HIGH → Docker container)
- **Docker Lifecycle Management**: Containers are created per `invokeId + skillName` and cleaned up in `afterAgent`
- **Security Gate Control**: Tool call policies enforce allowed commands and parameters per skill
- **Thread-Safe Session Management**: `ConcurrentMap`-based session manager with timeout cleanup as fallback
- **Progressive Skill Disclosure**: Skills are listed by name/description first, full instructions loaded on demand

## Architecture

```
org.legend.framework.ai.alibaba.sandbox
├── agent/
│   ├── hook/
│   │   └── GuardedSkillsAgentHook.java    # Agent lifecycle hooks (beforeAgent/afterAgent)
│   ├── interceptor/
│   │   └── GuardedSkillsInterceptor.java  # Model interception, sandbox creation
│   └── audit/
│       └── AuditLog.java                  # Audit logging
├── backend/
│   ├── SandboxBackend.java                # Sandbox backend interface
│   ├── SandboxBackendProvider.java        # Risk-based sandbox selector
│   ├── SandboxSessionManager.java         # Thread-safe session manager
│   ├── BackendLease.java                  # Sandbox lease (AutoCloseable)
│   ├── SandboxAwareToolset.java           # Sandbox-aware tool callbacks
│   ├── ExecResult.java                    # Execution result record
│   ├── docker/
│   │   ├── DockerConfig.java              # Docker configuration
│   │   ├── DockerStandaloneSandbox.java   # Docker container sandbox
│   │   └── MountConfig.java               # Volume mount configuration
│   └── local/
│       └── LocalProcessSandbox.java       # Local process sandbox
├── skills/
│   ├── registry/
│   │   └── GuardedSkillRegistry.java      # Skill registry with security metadata
│   ├── env/
│   │   ├── EnvContext.java                # Environment context (OS, shell, executables)
│   │   └── EnvProbe.java                  # Environment probing utility
│   ├── manifest/
│   │   ├── SkillManifest.java             # Skill manifest record
│   │   ├── SkillManifestParser.java       # YAML frontmatter parser
│   │   ├── ToolPolicy.java                # Tool call policy
│   │   └── PolicyDecision.java            # Policy decision result
│   └── enums/
│       ├── RiskLevel.java                 # LOW / MEDIUM / HIGH
│       ├── OsKind.java                    # WINDOWS / LINUX / MAC
│       └── ShellKind.java                 # BASH / POWERSHELL / CMD
├── tool/
│   ├── core/
│   │   ├── ShellTool.java                 # Shell command execution
│   │   ├── ReadTool.java                  # File read
│   │   ├── WriteTool.java                 # File write
│   │   ├── EditTool.java                  # File edit
│   │   └── ...                            # Other file tools
│   ├── browser/
│   │   └── BrowserToolset.java            # Browser automation tools
│   ├── python/
│   │   └── PythonTool.java                # Python code execution
│   ├── mcp/
│   │   └── McpToolset.java                # MCP protocol tools
│   ├── GuardedToolCallback.java           # Security-gated tool callback
│   └── ToolInputs.java                    # Tool input records
├── GuardedSkillMetadata.java              # Skill metadata with security fields
├── SandboxConstants.java                  # Constants (invokeId key, prompt templates)
├── GlobalPolicy.java                      # Global security policy
└── ToolDeniedException.java               # Tool access denied exception
```

## Quick Start

### Prerequisites

- Java 25+
- Maven 3.6+
- Docker Desktop (for Docker sandbox mode)
- DashScope API key

### 1. Add Dependency

```xml
<dependency>
    <groupId>legend-framework</groupId>
    <artifactId>legend-alibaba-ai-sandbox</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2. Create Skill Directory

Create a `SKILL.md` file in your skills directory:

```markdown
---
name: code-executor
description: Execute Python code in a sandbox
version: 1.0.0
risk-level: high
network: false
allowed-tools:
  - Bash(command:python3 *.py)
  - Read
  - Write
---

# Code Executor Skill

This skill executes Python code in a sandboxed environment.

## Usage
1. Write Python code to /work/script.py
2. Execute: python3 /work/script.py
3. Read output from /work/output.txt
```

### 3. Build and Run

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
        // 1. Probe environment
        EnvContext env = EnvProbe.probe(Paths.get(System.getProperty("user.dir")));

        // 2. Create sandbox provider with Docker config
        SandboxBackendProvider provider = new SandboxBackendProvider(env, DockerConfig.defaults());

        // 3. Register skills
        GuardedSkillRegistry registry = GuardedSkillRegistry.builder()
            .skillsDirectory("/path/to/skills")
            .env(env)
            .autoLoad(true)
            .build();

        // 4. Create guarded hook
        GuardedSkillsAgentHook hook = GuardedSkillsAgentHook.builder()
            .skillRegistry(registry)
            .autoReload(true)
            .sandboxBackendProvider(provider)
            .envContext(env)
            .build();

        // 5. Create chat model
        DashScopeApi dashScopeApi = DashScopeApi.builder()
            .apiKey(System.getenv("DASHSCOPE_API_KEY"))
            .build();
        ChatModel chatModel = DashScopeChatModel.builder()
            .dashScopeApi(dashScopeApi)
            .build();

        // 6. Create agent
        ReactAgent agent = ReactAgent.builder()
            .name("my-guarded-agent")
            .model(chatModel)
            .saver(new MemorySaver())
            .hooks(List.of(hook))
            .enableLogging(true)
            .build();

        // 7. Run
        agent.call("Use the code-executor skill to run a Python script");
    }
}
```

## Core Concepts

### Risk Levels

| Level | Sandbox | Isolation | Use Case |
|-------|---------|-----------|----------|
| `LOW` | LocalProcessSandbox | Process-level | Read-only operations (Read, Grep, Glob) |
| `MEDIUM` | DockerStandaloneSandbox | Container-level | Read-write operations (Write, Edit) |
| `HIGH` | DockerStandaloneSandbox | Container-level + Network isolation | Command execution (Bash), untrusted code |

### Docker Lifecycle

```
beforeAgent()                    interceptModel()                    afterAgent()
    │                                 │                                    │
    ├─ Generate invokeId              ├─ Extract skill names               ├─ Get invokeId
    │   (UUID)                        │   from read_tool calls              │   from OverAllState
    │                                 ├─ For each skill:                    ├─ cleanupByInvokeId()
    │                                 │   getOrCreateLease()                   - Remove all containers
    │                                 │   Create Docker container              - Close all leases
    │                                 │   Inject sandbox-aware tools         │
    │                                 │                                    │
    ▼                                 ▼                                    ▼
```

### Session Manager

The `SandboxSessionManager` uses a two-level nested map:

```
ConcurrentMap<String, Map<String, BackendLease>>
  ├─ invokeId (agent call identifier)
  │    └─ skillName (skill name)
  │         └─ BackendLease (sandbox lease)
```

- **Creation**: In `GuardedSkillsInterceptor.interceptModel()`, when a skill is detected
- **Cleanup**: In `GuardedSkillsAgentHook.afterAgent()`, all leases for an invokeId are closed
- **Timeout Fallback**: `cleanupTimeoutSessions()` periodically cleans up leaked containers

### Skill Manifest Format

```yaml
---
name: my-skill
description: What this skill does
version: 1.0.0
risk-level: low | medium | high
network: true | false
allowed-tools:
  - Read
  - Write
  - Bash(command:python3 *.py)
  - Bash(command:ls *)
---

# Skill Body
Instructions, workflows, examples...
```

## Configuration

### Docker Configuration

```java
DockerConfig config = DockerConfig.defaults()
    .withImage("skill-sandbox:latest")
    .withHostWorkspaceRoot(Paths.get("/path/to/workspaces"))
    .withMount(Paths.get("/host/skill"), "/skill", true)   // read-only mount
    .withMount(Paths.get("/host/output"), "/output", false); // read-write mount
```

### Environment Context

```java
EnvContext env = EnvProbe.probe(Paths.get(System.getProperty("user.dir")));
// env.os()        → OsKind.WINDOWS
// env.shellKind() → ShellKind.POWERSHELL
// env.cwd()       → current working directory
// env.availableExecutables() → [python, node, git, ...]
```

## Testing

Test classes are located in `src/test/java/org/legend/framework/ai/alibaba/sandbox/demo/`:

| Test Class | Description |
|-----------|-------------|
| `GuardedSkillDemo` | Basic skill integration demo |
| `GuardDockerMountTest` | Docker volume mount tests |
| `ContainerLifecycleTest` | Container lifecycle verification |
| `GuardDockerSandboxSkillTest` | Docker sandbox isolation tests |
| `GuardSkillSecurityTest` | Security policy tests |
| `GuardBashToolTest` | Shell tool execution tests |
| `GuardDataAnalysisSkillTest` | Data analysis skill tests |
| `GuardFileOrganizerSkillTest` | File organizer skill tests |
| `EnvInfoTest` | Environment info utility test |

Run a test:

```bash
mvn test -Dtest=GuardedSkillDemo
```

## Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| Spring AI Alibaba | 1.1.2.2 | Agent framework |
| Spring Boot | 3.5.13 | Application framework |
| docker-java | 3.7.1 | Docker API client |
| Lombok | 1.18.42 | Code generation |

## License

legend-framework
