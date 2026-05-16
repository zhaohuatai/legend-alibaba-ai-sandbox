# Docker 沙箱生命周期管理设计方案

## 一、设计目标

### 1.1 核心需求

- **唯一性保证**：在单次 agent.invoke() 期间，同一个 skill 只对应一个 Docker 容器
- **生命周期绑定**：从 interceptModel 开始创建容器，到 afterAgent 结束统一清理
- **双层绑定**：容器与 invokeId + skillName 两层绑定
- **防泄漏**：无论正常结束还是异常退出，容器都能被正确清理

### 1.2 设计原则

- **简洁性**：避免冗余的 DynamicBackendAdapter、SandboxAwareToolset 等设计
- **安全性**：异常路径也能保证容器清理
- **可观测性**：完整的日志和审计跟踪

---

## 二、核心概念

### 2.1 关键术语

| 术语 | 说明 |
|------|------|
| **invokeId** | 单次 agent.invoke() 调用的唯一标识，由 beforeAgent 生成 |
| **skillName** | Skill 的名称，从 read_skill 工具调用中提取 |
| **BackendLease** | 沙箱租约，封装 SandboxBackend 实例和关闭器（已有组件） |
| **SandboxSessionManager** | 管理所有租约的核心组件，使用扁平嵌套 Map 结构 |

### 2.2 数据传递机制

根据源码验证，数据流转路径如下：

```
beforeAgent 返回 Map
    ↓
合并到 OverAllState.data (通过 KeyStrategy)
    ↓
AgentLlmNode.apply() 构建 ModelRequest
    ↓
contextMap = new HashMap<>(state.data())  ← 复制整个 OverAllState
    ↓
ModelRequest.context 包含 OverAllState 数据
    ↓
interceptModel 通过 request.getContext() 访问
```

**核心结论**：
- ✅ OverAllState 在单次 invoke() 内是同一个实例
- ✅ beforeAgent 返回值会自动合并到 OverAllState
- ✅ interceptModel 可以通过 `request.getContext()` 访问 OverAllState 数据
- ✅ 沙箱会话 key 是业务数据，应该放在 OverAllState 中

---

## 三、架构设计

### 3.1 整体架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                        Agent.invoke()                           │
│                                                                 │
│  ┌──────────────┐                                               │
│  │ beforeAgent  │── 生成 invokeId ──→ OverAllState              │
│  └──────────────┘                                               │
│         │                                                       │
│         ▼                                                       │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │              ReAct 循环 (多轮)                            │   │
│  │                                                          │   │
│  │  ┌────────────────────────────────────────────────────┐  │   │
│  │  │         AgentLlmNode (LLM 推理)                     │  │   │
│  │  │                                                    │  │   │
│  │  │  构建 ModelRequest:                                │  │   │
│  │  │    context = OverAllState.data                     │  │   │
│  │  └────────────────────────────────────────────────────┘  │   │
│  │                          │                                │   │
│  │                          ▼                                │   │
│  │  ┌────────────────────────────────────────────────────┐  │   │
│  │  │     GuardedSkillsInterceptor (interceptModel)       │  │   │
│  │  │                                                    │  │   │
│  │  │  1. 从 request.getContext() 获取 invokeId          │  │   │
│  │  │  2. 从 messages 提取 skillName                     │  │   │
│  │  │  3. 通过 SandboxSessionManager 获取/创建容器       │  │   │
│  │  │  4. 为 skill 创建工具集并注入                      │  │   │
│  │  └────────────────────────────────────────────────────┘  │   │
│  │                          │                                │   │
│  │                          ▼                                │   │
│  │  ┌────────────────────────────────────────────────────┐  │   │
│  │  │         AgentToolNode (工具执行)                    │  │   │
│  │  │                                                    │  │   │
│  │  │  使用 SandboxSession 中的容器执行工具              │  │   │
│  │  └────────────────────────────────────────────────────┘  │   │
│  │                                                          │   │
│  └──────────────────────────────────────────────────────────┘   │
│         │                                                       │
│         ▼                                                       │
│  ┌──────────────┐                                               │
│  │ afterAgent   │── 从 OverAllState 获取 invokeId               │
│  │              │── 通过 SandboxSessionManager 清理所有容器     │
│  └──────────────┘                                               │
└─────────────────────────────────────────────────────────────────┘
```

### 3.2 核心组件

#### 3.2.1 SandboxSessionManager（沙箱会话管理器）

```java
/**
 * 沙箱会话管理器，负责管理所有沙箱租约。
 * 
 * <p>数据结构：
 * <pre>
 * ConcurrentMap&lt;String, Map&lt;String, List&lt;BackendLease&gt;&gt;&gt;
 *   ├─ invokeId (agent 调用标识)
 *   │    └─ skillName (skill 名称)
 *   │         └─ List&lt;BackendLease&gt; (租约列表)
 * </pre>
 */
public class SandboxSessionManager {
    
    /**
     * 所有活跃的租约，三层嵌套结构：
     * - 第一层 key: invokeId
     * - 第二层 key: skillName
     * - 第三层 value: BackendLease 列表
     */
    private final ConcurrentMap<String, Map<String, List<BackendLease>>> sessionSets;
    
    /** 获取或创建指定 skill 的沙箱租约 */
    public BackendLease getOrCreateLease(String invokeId, String skillName, Supplier<BackendLease> creator);
    
    /** 清理指定 invokeId 的所有沙箱租约 */
    public void cleanupByInvokeId(String invokeId);
    
    /** 定时清理超时的租约 */
    public void cleanupTimeoutSessions(Duration timeout);
    
    /** 获取所有活跃的 invokeId */
    public Set<String> getActiveInvokeIds();
}
```

---

## 四、完整数据流转图

```
用户调用: agent.call("你好", config)
  │
  ├─> RunnableConfig (环境配置)
  │    ├─ threadId: "user-123"
  │    ├─ metadata: {"_AGENT_": "myAgent"}
  │    └─ context: {}
  │
  ├─> OverAllState (业务数据) ← 创建新实例
  │    ├─ 初始: {"input": "你好"}
  │    │
  │    ├─ [beforeAgent 后]
  │    │    └─ {"input": "你好", "_INVOKE_ID_": "invoke-uuid-xxx"}
  │    │         ↑ Hook 返回的 Map 合并到 state
  │    │
  │    └─ [LLM 调用后]
  │         └─ {"input": "你好", "_INVOKE_ID_": "invoke-uuid-xxx", 
  │              "messages": [user_msg, assistant_msg]}
  │
  ├─> ModelRequest.context (传给 interceptModel)
  │    ├─ 来自 OverAllState.data: {"input": "你好", "_INVOKE_ID_": "invoke-uuid-xxx"}
  │    └─ 来自 RunnableConfig.metadata: {"_AGENT_": "myAgent"}
  │
  └─> SandboxSessionManager (内存管理)
       ├─ sessionSets: {
       │    "invoke-uuid-xxx": {
       │         "data-analysis": [BackendLease(containerId="abc123")],
       │         "code-executor": [BackendLease(containerId="def456")]
       │    }
       │  }
       │
       └─ [afterAgent 清理]
            └─ 关闭 invoke-uuid-xxx 对应的所有 BackendLease
```

---

## 五、详细执行流程

### 5.1 beforeAgent：生成 invokeId

```java
@Override
public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
    // 1. 生成唯一的 invokeId
    String invokeId = UUID.randomUUID().toString().replace("-", "");
    
    // 2. 在 SessionManager 中注册 SessionSet
    sessionManager.getOrCreateSessionSet(invokeId);
    
    // 3. 调用 delegate 并合并返回值
    CompletableFuture<Map<String, Object>> completableFuture = delegate.beforeAgent(state, config);
    
    return completableFuture.thenApply(delegateResult -> {
        Map<String, Object> merged = new HashMap<>(delegateResult);
        // 4. 将 invokeId 放入返回的 Map，会自动合并到 OverAllState
        merged.put(SandboxConstants.INVOKE_ID_KEY, invokeId);
        return merged;
    });
}
```

**关键点**：
- invokeId 在 beforeAgent 中生成
- 同时在 SessionManager 中注册空的 SessionSet
- invokeId 通过返回值合并到 OverAllState

### 5.2 interceptModel：创建/复用容器

```java
@Override
public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
    List<SkillMetadata> skills = skillRegistry.listAll();
    if (skills.isEmpty()) {
        return handler.call(request);
    }
    
    // 1. 从 context 中获取 invokeId（来自 OverAllState）
    String invokeId = (String) request.getContext().get(SandboxConstants.INVOKE_ID_KEY);
    
    // 2. 从 messages 中提取被读取的 skill
    List<SkillMetadata> extractedSkills = extractReadSkills(request.getMessages());
    if (extractedSkills.isEmpty()) {
        return handler.call(request);
    }
    
    // 3. 为每个 skill 获取或创建沙箱会话
    List<ToolCallback> skillTools = new ArrayList<>(request.getDynamicToolCallbacks());
    
    for (SkillMetadata skillMeta : extractedSkills) {
        if (skillMeta instanceof GuardedSkillMetadata guardedSkillMeta) {
            // 3.1 通过 SessionManager 获取或创建沙箱租约
            BackendLease lease = sessionManager.getOrCreateLease(
                invokeId, 
                skillMeta.getName(),
                () -> sandboxBackendProvider.acquireForGuardedSkill(guardedSkillMeta)
            );
            
            // 3.2 使用 lease 中的 backend 创建工具集
            List<ToolCallback> guardedTools = SandboxAwareToolset.create(
                lease.backend(), 
                envContext, 
                guardedSkillMeta
            );
            skillTools.addAll(guardedTools);
        }
        
        // 3.3 添加分组工具和 allowed_tools
        skillTools.addAll(getGroupedToolsForSkill(skillMeta));
        skillTools.addAll(resolveAllowedTools(skillMeta));
    }
    
    skillTools = deduplicateByName(skillTools);
    
    // 4. 增强系统提示
    String skillsPrompt = buildSkillsPrompt(skills, skillRegistry, skillRegistry.getSystemPromptTemplate());
    SystemMessage enhanced = enhanceSystemMessage(request.getSystemMessage(), skillsPrompt);
    
    // 5. 构建修改后的请求
    ModelRequest modified = ModelRequest.builder(request)
            .systemMessage(enhanced)
            .dynamicToolCallbacks(skillTools)
            .build();
    
    return handler.call(modified);
}
```

**关键点**：
- 从 `request.getContext()` 获取 invokeId
- 通过 SessionManager 按 invokeId + skillName 获取或创建容器
- 同一个 skill 在单次 invoke 内复用同一个容器
- 工具集直接绑定到 session 的 backend

### 5.3 afterAgent：清理所有容器

```java
@Override
public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
    // 1. 从 OverAllState 获取 invokeId
    String invokeId = (String) state.value(SandboxConstants.INVOKE_ID_KEY).orElse(null);
    
    if (invokeId != null) {
        try {
            // 2. 清理该 invokeId 对应的所有沙箱会话
            sessionManager.cleanupByInvokeId(invokeId);
            logger.info("Cleaned up all sandbox sessions for invokeId: {}", invokeId);
        } catch (Exception e) {
            logger.error("Failed to cleanup sandbox sessions for invokeId: {}", invokeId, e);
        }
    }
    
    return delegate.afterAgent(state, config);
}
```

**关键点**：
- 从 OverAllState 读取 invokeId
- 调用 SessionManager 清理该 invokeId 的所有容器
- 异常捕获确保清理失败不影响主流程

---

## 六、核心组件实现

### 6.1 SandboxSessionManager

```java
package org.legend.framework.ai.alibaba.skill.guard.sandbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * 沙箱会话管理器，负责管理所有沙箱租约。
 * 
 * <p>数据结构：
 * <pre>
 * ConcurrentMap&lt;String, Map&lt;String, List&lt;BackendLease&gt;&gt;&gt;
 *   ├─ invokeId (agent 调用标识)
 *   │    └─ skillName (skill 名称)
 *   │         └─ List&lt;BackendLease&gt; (租约列表)
 * </pre>
 * 
 * <p>核心职责：
 * <ol>
 *   <li>管理 invokeId + skillName 到租约的映射</li>
 *   <li>提供按 invokeId + skillName 获取/创建沙箱租约的能力</li>
 *   <li>在 afterAgent 中清理指定 invokeId 的所有容器</li>
 *   <li>定时清理超时的租约（兜底机制）</li>
 * </ol>
 */
public class SandboxSessionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SandboxSessionManager.class);
    
    /**
     * 所有活跃的租约，三层嵌套结构：
     * - 第一层 key: invokeId
     * - 第二层 key: skillName
     * - 第三层 value: BackendLease 列表
     */
    private final ConcurrentMap<String, Map<String, List<BackendLease>>> sessionSets;
    
    public SandboxSessionManager() {
        this.sessionSets = new ConcurrentHashMap<>();
    }
    
    /**
     * 获取或创建指定 skill 的沙箱租约。
     * 
     * @param invokeId agent 调用标识
     * @param skillName skill 名称
     * @param creator 租约创建器（仅在不存在时调用）
     * @return 沙箱租约
     */
    public BackendLease getOrCreateLease(String invokeId, String skillName, Supplier<BackendLease> creator) {
        // 获取或创建 invokeId 对应的内层 Map
        Map<String, List<BackendLease>> skillMap = sessionSets.computeIfAbsent(
            invokeId, 
            k -> new ConcurrentHashMap<>()
        );
        
        // 获取或创建 skillName 对应的租约列表
        List<BackendLease> leaseList = skillMap.computeIfAbsent(
            skillName, 
            k -> new ArrayList<>()
        );
        
        // 如果列表为空，创建新租约
        if (leaseList.isEmpty()) {
            BackendLease lease = creator.get();
            leaseList.add(lease);
            logger.info("Lease created: invokeId={}, skillName={}", invokeId, skillName);
            return lease;
        }
        
        // 返回已存在的租约（复用）
        BackendLease lease = leaseList.get(0);
        logger.info("Lease retrieved: invokeId={}, skillName={}", invokeId, skillName);
        return lease;
    }
    
    /**
     * 清理指定 invokeId 的所有沙箱租约。
     * 
     * <p>该方法在 afterAgent 中调用，确保单次 invoke 期间创建的所有容器都被清理。
     */
    public void cleanupByInvokeId(String invokeId) {
        Map<String, List<BackendLease>> skillMap = sessionSets.remove(invokeId);
        if (skillMap != null) {
            int count = 0;
            for (List<BackendLease> leaseList : skillMap.values()) {
                for (BackendLease lease : leaseList) {
                    try {
                        lease.close();
                        count++;
                    } catch (Exception e) {
                        logger.error("Failed to close lease: invokeId={}, error={}", invokeId, e.getMessage());
                    }
                }
            }
            logger.info("Cleaned up {} lease(s) for invokeId: {}", count, invokeId);
        }
    }
    
    /**
     * 定时清理超时的租约（兜底机制）。
     * 
     * <p>如果 afterAgent 由于异常没有被调用，该方法可以清理泄漏的容器。
     */
    public void cleanupTimeoutSessions(Duration timeout) {
        // 简化实现：清理所有超过超时时间的 invokeId
        // 实际实现中可能需要记录每个 invokeId 的创建时间
        Set<String> invokeIds = sessionSets.keySet();
        for (String invokeId : invokeIds) {
            // 这里可以添加更复杂的超时逻辑
            // 例如：记录每个 invokeId 的创建时间
        }
    }
    
    /**
     * 获取所有活跃的 invokeId。
     */
    public Set<String> getActiveInvokeIds() {
        return sessionSets.keySet();
    }
    
    /**
     * 获取活跃的 invokeId 数量。
     */
    public int getActiveSessionSetCount() {
        return sessionSets.size();
    }
}
```

---

## 七、关键设计决策

### 7.1 为什么使用 OverAllState 传递 invokeId？

| 方案 | 优点 | 缺点 |
|------|------|------|
| **OverAllState** | ✅ 单次 invoke 内共享<br>✅ 自动合并到 ModelRequest.context<br>✅ interceptModel 可访问 | ❌ 多次 invoke 之间不共享 |
| **RunnableConfig** | ✅ 单次运行有效 | ❌ metadata 只读<br>❌ 无法在 beforeAgent 中修改并传递 |
| **ThreadLocal** | ✅ 线程内共享 | ❌ 需要注意线程安全<br>❌ 异步场景可能失效 |

**结论**：OverAllState 是最适合传递 invokeId 的方案，因为：
1. 单次 invoke 内只有一个 OverAllState 实例
2. beforeAgent 返回值会自动合并到 OverAllState
3. AgentLlmNode 会将 OverAllState.data 复制到 ModelRequest.context
4. interceptModel 可以通过 `request.getContext()` 访问

### 7.2 为什么按 invokeId + skillName 双层绑定？

**invokeId 层**：
- 标识单次 agent 调用
- 确保单次调用内的容器在 afterAgent 中统一清理

**skillName 层**：
- 标识具体的 skill
- 确保同一个 skill 在单次 invoke 内复用同一个容器
- 避免重复创建容器

**示例**：
```
invokeId: "abc123"
  ├─ skillName: "data-analysis" → BackendLease(containerId="docker-001")
  └─ skillName: "code-executor" → BackendLease(containerId="docker-002")

invokeId: "def456"
  ├─ skillName: "data-analysis" → BackendLease(containerId="docker-003")  ← 新的租约
  └─ skillName: "code-executor" → BackendLease(containerId="docker-004")  ← 新的租约
```

### 7.3 容器创建时机

**在 interceptModel 中创建**，而不是在 beforeAgent 中创建：

| 时机 | 优点 | 缺点 |
|------|------|------|
| **beforeAgent** | ✅ 提前准备 | ❌ 不知道会调用哪些 skill<br>❌ 可能创建不需要的容器 |
| **interceptModel** | ✅ 按需创建<br>✅ 知道具体 skill | ❌ 首次调用有延迟 |
| **工具执行时** | ✅ 最精确 | ❌ 增加工具执行复杂度 |

**结论**：interceptModel 是最佳时机，因为：
1. 此时已经知道 LLM 决定调用哪些 skill（从 messages 中提取）
2. 可以按需创建容器，避免浪费
3. 容器创建后可以立即用于工具集

### 7.4 容器清理时机

**在 afterAgent 中统一清理**：

| 时机 | 优点 | 缺点 |
|------|------|------|
| **skill 执行完** | ✅ 及时释放 | ❌ 同一个 skill 可能被多次调用<br>❌ 容器频繁创建销毁 |
| **afterAgent** | ✅ 统一清理<br>✅ 支持 skill 多次调用 | ❌ 容器存活时间较长 |
| **超时清理** | ✅ 兜底机制 | ❌ 可能泄漏资源 |

**结论**：afterAgent 是最佳时机，因为：
1. 确保单次 invoke 内所有 skill 都能复用容器
2. 统一清理简化了生命周期管理
3. 超时清理作为兜底机制防止泄漏

---

## 八、异常处理

### 8.1 异常路径保证

```
正常路径：
beforeAgent → interceptModel (创建容器) → 工具执行 → afterAgent (清理容器)

异常路径 1：interceptModel 异常
beforeAgent → interceptModel (异常) → afterAgent (清理容器) ✅

异常路径 2：工具执行异常
beforeAgent → interceptModel (创建容器) → 工具执行 (异常) → afterAgent (清理容器) ✅

异常路径 3：afterAgent 异常
beforeAgent → interceptModel (创建容器) → 工具执行 → afterAgent (异常) 
    → 超时清理 (兜底) ✅
```

### 8.2 超时清理机制

```java
// 定时任务，每 5 分钟执行一次
@Scheduled(fixedRate = 300000)
public void cleanupTimeoutSessions() {
    sessionManager.cleanupTimeoutSessions(Duration.ofMinutes(30));
}
```

---

## 九、常量定义

```java
package org.legend.framework.ai.alibaba.skill.guard;

/**
 * 沙箱相关常量。
 */
public final class SandboxConstants {
    
    /**
     * OverAllState 中存储 invokeId 的 key。
     */
    public static final String INVOKE_ID_KEY = "_SANDBOX_INVOKE_ID_";
    
    /**
     * OverAllState 中存储 sessionKey 的 key（保留兼容）。
     */
    public static final String AGENT_SESSION_KEY = "_SANDBOX_SESSION_KEY_";
}
```

---

## 十、使用示例

### 10.1 配置 GuardedSkillsAgentHook

```java
// 1. 创建 SessionManager
SandboxSessionManager sessionManager = new SandboxSessionManager();

// 2. 创建 Hook
GuardedSkillsAgentHook hook = GuardedSkillsAgentHook.builder()
    .skillRegistry(registry)
    .autoReload(true)
    .sandboxBackendProvider(provider)
    .envContext(env)
    .sessionManager(sessionManager)  // 注入 SessionManager
    .build();

// 3. 创建 Agent
ReactAgent agent = ReactAgent.builder()
    .name("myAgent")
    .model(chatModel)
    .hooks(hook)
    .build();

// 4. 调用 Agent
agent.call("请帮我分析数据", RunnableConfig.builder().threadId("user-123").build());
// afterAgent 会自动清理所有容器
```

### 10.2 监控和日志

```
[INFO] beforeAgent: generated invokeId=abc123def456
[INFO] interceptModel: creating session for skill=data-analysis, invokeId=abc123def456
[INFO] Docker container created: containerId=docker-001, skill=data-analysis
[INFO] interceptModel: reusing session for skill=data-analysis, invokeId=abc123def456
[INFO] afterAgent: cleaning up 2 session(s) for invokeId=abc123def456
[INFO] Docker container destroyed: containerId=docker-001
[INFO] Docker container destroyed: containerId=docker-002
```

---

## 十一、方案评审问题与修复

### 11.1 评审发现的问题

#### 🔴 严重问题（设计缺陷）

**问题 1：`List<BackendLease>` 结构设计不合理**

- **位置**：`ConcurrentMap<String, Map<String, List<BackendLease>>>`
- **问题**：设计目标是"同一个 skill 在单次 invoke 内只对应一个容器"，但第三层用了 `List`，实际只取第一个元素 `leaseList.get(0)`
- **影响**：设计复杂度高，违背设计目标
- **修复**：改为 `ConcurrentMap<String, Map<String, BackendLease>>`

**问题 2：`getOrCreateLease` 存在线程安全漏洞**

- **位置**：`getOrCreateLease` 方法
- **问题**：`isEmpty()` → `creator.get()` → `add()` 这三步不是原子操作，多线程可能同时进入 if 块，创建多个租约
- **影响**：可能创建多个容器，违背唯一性保证
- **修复**：用 `computeIfAbsent` 直接完成创建，保证原子性

**问题 3：`cleanupTimeoutSessions` 是空实现**

- **位置**：`cleanupTimeoutSessions` 方法
- **问题**：没有记录创建时间，无法判断是否超时，如果 afterAgent 异常，容器会永久泄漏
- **影响**：兜底机制失效，容器可能泄漏
- **修复**：增加 `Map<String, Instant> invokeIdCreatedAt` 记录创建时间

**问题 4：`beforeAgent` 调用了不存在的方法**

- **位置**：`beforeAgent` 方法中的 `sessionManager.getOrCreateSessionSet(invokeId)`
- **问题**：`SandboxSessionManager` 接口定义中没有 `getOrCreateSessionSet` 方法
- **影响**：编译错误
- **修复**：删除这行，或在接口中补充该方法

#### 🟡 中等问题

**问题 5：`invokeId` 为 null 的处理不完整**

- **位置**：`interceptModel` 方法
- **问题**：如果 `invokeId` 为 null（beforeAgent 还没执行完），但 extractedSkills 不为空，会继续执行导致 NPE
- **修复**：增加 `if (invokeId == null) return handler.call(request);`

**问题 6：`cleanupByInvokeId` 存在并发竞态**

- **位置**：`cleanupByInvokeId` 方法
- **问题**：`remove()` 返回后，另一个线程可能立即调用 `getOrCreateLease()` 创建新的 skillMap
- **修复**：清理后检查是否还有残留，或加锁保护

**问题 7：`extractReadSkills` 可能返回重复 skill**

- **位置**：`interceptModel` 方法
- **问题**：如果 messages 中同一个 skill 被多次引用，会返回重复项
- **修复**：对 extractedSkills 去重

#### 🟢 轻微问题

**问题 8：缺少 invokeId 创建时间记录**
- **修复**：增加创建时间记录，用于超时清理和监控

**问题 9：日志不够详细**
- **修复**：`cleanupByInvokeId` 记录具体关闭了哪些 skill

**问题 10：没有考虑嵌套 agent 调用**
- **问题**：如果 skill 执行过程中触发了另一个 agent.invoke()，invokeId 会覆盖
- **修复**：后续版本考虑

### 11.2 修复后的设计

#### 修复后的数据结构

```java
// 修复前
ConcurrentMap<String, Map<String, List<BackendLease>>> sessionSets;

// 修复后
ConcurrentMap<String, Map<String, BackendLease>> sessionSets;  // 去掉 List
ConcurrentMap<String, Instant> invokeIdCreatedAt;  // 新增：记录创建时间
```

#### 修复后的 `getOrCreateLease`

```java
public BackendLease getOrCreateLease(String invokeId, String skillName, Supplier<BackendLease> creator) {
    // 记录创建时间
    invokeIdCreatedAt.putIfAbsent(invokeId, Instant.now());
    
    // 获取或创建 invokeId 对应的内层 Map
    Map<String, BackendLease> skillMap = sessionSets.computeIfAbsent(
        invokeId, 
        k -> new ConcurrentHashMap<>()
    );
    
    // 使用 computeIfAbsent 保证原子性
    return skillMap.computeIfAbsent(skillName, k -> {
        BackendLease lease = creator.get();
        logger.info("Lease created: invokeId={}, skillName={}", invokeId, skillName);
        return lease;
    });
}
```

#### 修复后的 `cleanupByInvokeId`

```java
public void cleanupByInvokeId(String invokeId) {
    Map<String, BackendLease> skillMap = sessionSets.remove(invokeId);
    invokeIdCreatedAt.remove(invokeId);
    
    if (skillMap != null) {
        int count = 0;
        List<String> closedSkills = new ArrayList<>();
        for (Map.Entry<String, BackendLease> entry : skillMap.entrySet()) {
            try {
                entry.getValue().close();
                closedSkills.add(entry.getKey());
                count++;
            } catch (Exception e) {
                logger.error("Failed to close lease: invokeId={}, skillName={}, error={}", 
                    invokeId, entry.getKey(), e.getMessage());
            }
        }
        logger.info("Cleaned up {} lease(s) for invokeId: {}, skills: {}", 
            count, invokeId, closedSkills);
    }
}
```

#### 修复后的 `cleanupTimeoutSessions`

```java
public void cleanupTimeoutSessions(Duration timeout) {
    Instant cutoff = Instant.now().minus(timeout);
    
    // 找出超时的 invokeId
    List<String> timeoutInvokeIds = new ArrayList<>();
    for (Map.Entry<String, Instant> entry : invokeIdCreatedAt.entrySet()) {
        if (entry.getValue().isBefore(cutoff)) {
            timeoutInvokeIds.add(entry.getKey());
        }
    }
    
    // 清理超时的 invokeId
    for (String invokeId : timeoutInvokeIds) {
        logger.warn("Cleaning up timeout invokeId: {}, age={}s", 
            invokeId, Duration.between(invokeIdCreatedAt.get(invokeId), Instant.now()).getSeconds());
        cleanupByInvokeId(invokeId);
    }
}
```

#### 修复后的 `beforeAgent`

```java
@Override
public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
    String invokeId = UUID.randomUUID().toString().replace("-", "");
    
    // 删除了 sessionManager.getOrCreateSessionSet(invokeId) 调用
    
    CompletableFuture<Map<String, Object>> completableFuture = delegate.beforeAgent(state, config);
    
    return completableFuture.thenApply(delegateResult -> {
        Map<String, Object> merged = new HashMap<>(delegateResult);
        merged.put(SandboxConstants.INVOKE_ID_KEY, invokeId);
        return merged;
    });
}
```

#### 修复后的 `interceptModel`

```java
@Override
public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
    List<SkillMetadata> skills = skillRegistry.listAll();
    if (skills.isEmpty()) {
        return handler.call(request);
    }
    
    String invokeId = (String) request.getContext().get(SandboxConstants.INVOKE_ID_KEY);
    if (invokeId == null) {  // 新增：处理 invokeId 为 null 的情况
        logger.warn("invokeId is null, skipping sandbox creation");
        return handler.call(request);
    }
    
    List<SkillMetadata> extractedSkills = extractReadSkills(request.getMessages());
    if (extractedSkills.isEmpty()) {
        return handler.call(request);
    }
    
    // 新增：去重
    List<SkillMetadata> uniqueSkills = extractedSkills.stream()
        .distinct()
        .collect(Collectors.toList());
    
    List<ToolCallback> skillTools = new ArrayList<>(request.getDynamicToolCallbacks());
    
    for (SkillMetadata skillMeta : uniqueSkills) {
        if (skillMeta instanceof GuardedSkillMetadata guardedSkillMeta) {
            BackendLease lease = sessionManager.getOrCreateLease(
                invokeId, 
                skillMeta.getName(),
                () -> sandboxBackendProvider.acquireForGuardedSkill(guardedSkillMeta)
            );
            
            List<ToolCallback> guardedTools = SandboxAwareToolset.create(
                lease.backend(), 
                envContext, 
                guardedSkillMeta
            );
            skillTools.addAll(guardedTools);
        }
        
        skillTools.addAll(getGroupedToolsForSkill(skillMeta));
        skillTools.addAll(resolveAllowedTools(skillMeta));
    }
    
    skillTools = deduplicateByName(skillTools);
    
    String skillsPrompt = buildSkillsPrompt(skills, skillRegistry, skillRegistry.getSystemPromptTemplate());
    SystemMessage enhanced = enhanceSystemMessage(request.getSystemMessage(), skillsPrompt);
    
    ModelRequest modified = ModelRequest.builder(request)
            .systemMessage(enhanced)
            .dynamicToolCallbacks(skillTools)
            .build();
    
    return handler.call(modified);
}
```

---

## 十二、总结

### 11.1 设计优势

| 优势 | 说明 |
|------|------|
| **简洁性** | 去除了冗余的 DynamicBackendAdapter 缓存逻辑 |
| **安全性** | 异常路径也能保证容器清理 |
| **可观测性** | 完整的日志和审计跟踪 |
| **可扩展性** | SessionManager 可轻松扩展监控和统计功能 |

### 11.2 核心流程

1. **beforeAgent**：生成 invokeId，注册 SessionSet，写入 OverAllState
2. **interceptModel**：从 context 获取 invokeId，按 invokeId + skillName 获取/创建容器
3. **工具执行**：使用 session 中的 backend 执行工具
4. **afterAgent**：从 OverAllState 获取 invokeId，清理所有容器
5. **超时清理**：兜底机制，防止 afterAgent 异常导致容器泄漏

### 11.3 关键结论

- ✅ **OverAllState 是传递 invokeId 的最佳方案**
- ✅ **容器按 invokeId + skillName 双层绑定**
- ✅ **容器在 interceptModel 中按需创建**
- ✅ **容器在 afterAgent 中统一清理**
- ✅ **超时清理作为兜底机制**

---

## 八、评审问题与修复

### 8.1 评审发现的问题

在方案评审中发现了以下问题：

#### 问题 1：List<BackendLease> 结构冗余

**问题描述**：
```java
// 原始设计
ConcurrentMap<String, Map<String, List<BackendLease>>> sessionSets;
```
第三层的 `List<BackendLease>` 是冗余的，因为每个 skillName 在单次 invoke 内只需要一个租约。

**修复方案**：
```java
// 修复后：去掉 List，直接使用 BackendLease
ConcurrentMap<String, Map<String, BackendLease>> sessionSets;
```

#### 问题 2：getOrCreateLease 线程安全漏洞

**问题描述**：
```java
// 原始设计：isEmpty() → creator.get() → add() 不是原子操作
if (leaseList.isEmpty()) {
    BackendLease lease = creator.get();  // 多线程可能同时进入
    leaseList.add(lease);
    return lease;
}
```

**修复方案**：
```java
// 使用 computeIfAbsent 保证原子性
return skillMap.computeIfAbsent(skillName, k -> {
    BackendLease lease = creator.get();
    logger.info("Lease created: invokeId={}, skillName={}", invokeId, skillName);
    return lease;
});
```

#### 问题 3：cleanupTimeoutSessions 空实现

**问题描述**：
原始方案中 `cleanupTimeoutSessions` 方法只有注释，没有实际清理逻辑，可能导致资源泄漏。

**修复方案**：
```java
// 新增：记录每个 invokeId 的创建时间
private final ConcurrentMap<String, Instant> invokeIdCreatedAt;

public void cleanupTimeoutSessions(Duration timeout) {
    Instant cutoff = Instant.now().minus(timeout);
    
    // 找出超时的 invokeId
    List<String> timeoutInvokeIds = new ArrayList<>();
    for (Map.Entry<String, Instant> entry : invokeIdCreatedAt.entrySet()) {
        if (entry.getValue().isBefore(cutoff)) {
            timeoutInvokeIds.add(entry.getKey());
        }
    }
    
    // 清理超时的 invokeId
    for (String invokeId : timeoutInvokeIds) {
        cleanupByInvokeId(invokeId);
    }
}
```

#### 问题 4：beforeAgent 调用不存在的方法

**问题描述**：
```java
// 原始设计
sessionManager.getOrCreateSessionSet(invokeId);  // 此方法不存在
```

**修复方案**：
移除该调用，因为 `getOrCreateLease` 中的 `computeIfAbsent` 会自动创建内层 Map。

#### 问题 5：invokeId 为 null 时处理不完整

**问题描述**：
原始方案在 `interceptModel` 中没有检查 `invokeId` 是否为 null，可能导致 NPE。

**修复方案**：
```java
String invokeId = (String) request.getContext().get(SandboxConstants.INVOKE_ID_KEY);
if (invokeId == null) {
    logger.warn("invokeId is null, skipping sandbox creation");
    return handler.call(request);
}
```

#### 问题 6：cleanupByInvokeId 并发竞态

**问题描述**：
多个线程可能同时调用 `cleanupByInvokeId`，导致重复关闭或 NPE。

**修复方案**：
```java
public void cleanupByInvokeId(String invokeId) {
    Map<String, BackendLease> skillMap = sessionSets.remove(invokeId);
    invokeIdCreatedAt.remove(invokeId);
    
    if (skillMap != null) {
        // 清理逻辑...
    }
}
```
使用 `remove()` 的原子性保证只有一个线程能获取到 skillMap。

#### 问题 7：extractReadSkills 可能返回重复 skill

**问题描述**：
如果 LLM 在同一轮对话中多次调用 `read_skill` 同一个 skill，`extractReadSkills` 可能返回重复的 skill。

**修复方案**：
在 `extractReadSkills` 中使用 `LinkedHashSet` 去重，或在后续处理中去重。

### 8.2 修复后的核心代码

#### 修复后的数据结构

```java
public class SandboxSessionManager {
    // 修复 1：去掉 List，直接使用 BackendLease
    private final ConcurrentMap<String, Map<String, BackendLease>> sessionSets;
    
    // 修复 3：新增创建时间记录
    private final ConcurrentMap<String, Instant> invokeIdCreatedAt;
    
    public SandboxSessionManager() {
        this.sessionSets = new ConcurrentHashMap<>();
        this.invokeIdCreatedAt = new ConcurrentHashMap<>();
    }
}
```

#### 修复后的 getOrCreateLease

```java
public BackendLease getOrCreateLease(String invokeId, String skillName, Supplier<BackendLease> creator) {
    // 修复 3：记录创建时间
    invokeIdCreatedAt.putIfAbsent(invokeId, Instant.now());
    
    // 获取或创建 invokeId 对应的内层 Map
    Map<String, BackendLease> skillMap = sessionSets.computeIfAbsent(
        invokeId, 
        k -> new ConcurrentHashMap<>()
    );
    
    // 修复 2：使用 computeIfAbsent 保证原子性
    return skillMap.computeIfAbsent(skillName, k -> {
        BackendLease lease = creator.get();
        logger.info("Lease created: invokeId={}, skillName={}", invokeId, skillName);
        return lease;
    });
}
```

#### 修复后的 cleanupByInvokeId

```java
public void cleanupByInvokeId(String invokeId) {
    // 修复 6：使用 remove() 的原子性
    Map<String, BackendLease> skillMap = sessionSets.remove(invokeId);
    invokeIdCreatedAt.remove(invokeId);
    
    if (skillMap != null) {
        int count = 0;
        List<String> closedSkills = new ArrayList<>();
        for (Map.Entry<String, BackendLease> entry : skillMap.entrySet()) {
            try {
                entry.getValue().close();
                closedSkills.add(entry.getKey());
                count++;
            } catch (Exception e) {
                logger.error("Failed to close lease: invokeId={}, skillName={}, error={}", 
                    invokeId, entry.getKey(), e.getMessage());
            }
        }
        logger.info("Cleaned up {} lease(s) for invokeId: {}, skills: {}", 
            count, invokeId, closedSkills);
    }
}
```

#### 修复后的 cleanupTimeoutSessions

```java
public void cleanupTimeoutSessions(Duration timeout) {
    Instant cutoff = Instant.now().minus(timeout);
    
    // 找出超时的 invokeId
    List<String> timeoutInvokeIds = new ArrayList<>();
    for (Map.Entry<String, Instant> entry : invokeIdCreatedAt.entrySet()) {
        if (entry.getValue().isBefore(cutoff)) {
            timeoutInvokeIds.add(entry.getKey());
        }
    }
    
    // 清理超时的 invokeId
    for (String invokeId : timeoutInvokeIds) {
        Instant createdAt = invokeIdCreatedAt.get(invokeId);
        if (createdAt != null) {
            logger.warn("Cleaning up timeout invokeId: {}, age={}s", 
                invokeId, Duration.between(createdAt, Instant.now()).getSeconds());
        }
        cleanupByInvokeId(invokeId);
    }
}
```

#### 修复后的 interceptModel

```java
@Override
public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
    List<SkillMetadata> skills = skillRegistry.listAll();
    if (skills.isEmpty()) {
        return handler.call(request);
    }
    
    // 修复 5：检查 invokeId 是否为 null
    String invokeId = (String) request.getContext().get(SandboxConstants.INVOKE_ID_KEY);
    if (invokeId == null) {
        logger.warn("invokeId is null, skipping sandbox creation");
        return handler.call(request);
    }
    
    // 提取 skill 并创建工具...
}
```

#### 修复后的 beforeAgent

```java
@Override
public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
    String invokeId = UUID.randomUUID().toString().replace("-", "");
    
    // 修复 4：移除 sessionManager.getOrCreateSessionSet(invokeId) 调用
    
    CompletableFuture<Map<String, Object>> completableFuture = delegate.beforeAgent(state, config);
    
    return completableFuture.thenApply(delegateResult -> {
        Map<String, Object> merged = new HashMap<>(delegateResult);
        merged.put(SandboxConstants.INVOKE_ID_KEY, invokeId);
        return merged;
    });
}
```

---
