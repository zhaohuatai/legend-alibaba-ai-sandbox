# Spring AI Alibaba 框架 OverAllState 与 RunnableConfig 机制详解

## 一、核心概念定义

### 1.1 OverAllState - 图的"记忆"

**本质**：图的业务数据容器，负责存储和管理 Agent 运行过程中的所有业务数据。

**源码定义**：
```java
// OverAllState.java
public final class OverAllState implements Serializable {
    private final Map<String, Object> data;              // 存储状态数据
    private final Map<String, KeyStrategy> keyStrategies; // 存储合并策略
    private Store store;                                  // 长期存储
}
```

**核心特性**：
- 每个 key 都有对应的 KeyStrategy（合并策略）
- 常见策略：
  - `ReplaceStrategy`：新值替换旧值（业务状态 key 使用）
  - `AppendStrategy`：新值追加到列表（如 messages）
- 数据通过策略智能合并，不是简单的 map.put()

### 1.2 RunnableConfig - 运行的"环境配置"

**本质**：图运行的环境配置，提供单次运行所需的标识符、配置参数和临时上下文。

**源码定义**：
```java
// RunnableConfig.java
public final class RunnableConfig implements HasMetadata<RunnableConfig.Builder> {
    private final String threadId;                        // 线程ID
    private final String checkPointId;                    // 检查点ID
    private final String nextNode;                        // 下一个节点
    private final CompiledGraph.StreamMode streamMode;    // 流模式
    private final Map<String, Object> metadata;           // 不可变的元数据
    private final ConcurrentMap<String, Object> context;  // 可变的上下文
    private final Store store;                            // 存储
    private final Map<String, Object> interruptedNodes;   // 中断节点
}
```

**核心特性**：
- `metadata`：不可变，运行期间只读
- `context`：可变，但不持久化
- 没有合并策略，简单覆盖

---

## 二、OverAllState vs RunnableConfig 对比

| 维度 | OverAllState | RunnableConfig |
|------|--------------|----------------|
| **本质** | 图的业务数据容器 | 图运行的环境配置 |
| **作用域** | 跨节点、跨轮次持久化 | 单次运行（run） |
| **可变性** | 数据可变，有合并策略 | metadata 不可变，context 可变 |
| **持久化** | ✅ 支持 checkpoint 持久化 | ❌ 不持久化（context 部分） |
| **生命周期** | 贯穿整个 Agent 生命周期 | 单次 invoke() 调用 |
| **类比** | 数据库（存储业务数据） | 环境变量 + 会话配置 |

---

## 三、OverAllState 生命周期详解

### 3.1 单次 invoke() 调用内

**关键发现**：OverAllState 在单次 agent.call() 内是同一个实例，持续传递和更新。

```
agent.call("帮我改签航班", config)
  │
  ├─> 创建 OverAllState 实例（只创建一次）
  │   OverAllState state = new OverAllState();
  │
  ├─> [beforeAgent Hooks] → state 传递
  │   Hook 返回的 Map 合并到 state
  │
  ├─> ReAct 循环开始
  │   ├─> [AgentLlmNode] → state 传递
  │   │   从 state 读取 messages
  │   │   构建 ModelRequest
  │   │   调用 LLM
  │   │
  │   ├─> [AgentToolNode] → state 传递
  │   │   从 state 读取工具调用结果
  │   │   写回 state
  │   │
  │   └─> 循环继续...
  │
  └─> [afterAgent Hooks] → state 传递
```

**关键特性**：
- ✅ **单次 invoke() 内只有一个 OverAllState 实例**
- ✅ **所有 Node、Hook 共享同一个 state 对象**
- ✅ **state 在节点间持续累积更新**
- ✅ **Hook 返回的 Map 会通过 KeyStrategy 合并到 OverAllState**

### 3.2 多次 invoke() 调用之间

```java
// 第一次调用
OverAllState state1 = new OverAllState();  // 全新实例
agent.invoke("第一次请求");

// 第二次调用
OverAllState state2 = new OverAllState();  // 全新实例，与 state1 无关
agent.invoke("第二次请求");
```

**关键特性**：
- ✅ **每次 invoke() 都创建新的 OverAllState**
- ✅ **不会保留上一次调用的数据**（除非使用 Checkpoint 持久化）

---

## 四、数据传递机制详解

### 4.1 核心链路：OverAllState → ModelRequest.context → interceptModel

**关键发现**：AgentLlmNode 在构建 ModelRequest 时，会将 OverAllState.data 复制到 context 中。

**源码证据**（AgentLlmNode.apply() 方法）：

```java
public Map<String, Object> apply(OverAllState state, RunnableConfig config) {
    
    // 1. 从 OverAllState 获取业务数据（如 messages）
    List<Message> messages = (List<Message>) state.value("messages").orElse(List.of());
    
    // 2. 构建 ModelRequest 的 context
    Map<String, Object> contextMap = new HashMap<>(state.data());  // ← 关键！复制整个 OverAllState.data
    
    // 3. 合并 RunnableConfig 的 metadata
    Map<String, Object> metadata = config.metadata().orElse(new HashMap<>());
    if (!metadata.isEmpty()) {
        contextMap.putAll(metadata);  // ← RunnableConfig 的 metadata
    }
    
    // 4. 创建 ModelRequest
    ModelRequest request = ModelRequest.builder()
        .messages(messages)
        .context(contextMap)  // ← OverAllState 数据在这里传入
        .build();
    
    // 5. 调用拦截器链
    return interceptModel(request, handler);  // ← interceptModel 可以访问 context
}
```

**结论**：✅ **interceptModel 可以通过 `request.getContext().get("key")` 访问 OverAllState 的数据！**

### 4.2 Hook 返回值合并机制

Hook 的方法签名返回 `CompletableFuture<Map<String, Object>>`，这个 Map 会被**合并回 OverAllState**，按照各 key 注册的 KeyStrategy 处理。

```java
@Override
public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
    String sessionKey = UUID.randomUUID().toString().replace("-", "");
    
    // 返回的 Map 会自动合并到 OverAllState.data
    return CompletableFuture.completedFuture(Map.of(
        SandboxConstants.AGENT_SESSION_KEY, sessionKey
    ));
}
```

**合并流程**：
1. Hook 返回 `Map<String, Object>`
2. 框架遍历 Map 中的每个 key
3. 查找该 key 注册的 KeyStrategy
4. 使用 KeyStrategy 合并到 OverAllState.data
5. 如果没有注册 KeyStrategy，默认使用 ReplaceStrategy

---

## 五、完整的数据流转图

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
  │    │    └─ {"input": "你好", "_SANDBOX_SESSION_KEY_": "uuid-xxx"}
  │    │         ↑ Hook 返回的 Map 合并到 state
  │    │
  │    └─ [LLM 调用后]
  │         └─ {"input": "你好", "_SANDBOX_SESSION_KEY_": "uuid-xxx", 
  │              "messages": [user_msg, assistant_msg]}
  │
  └─> ModelRequest.context (传给 interceptModel)
       ├─ 来自 OverAllState.data: {"input": "你好", "_SANDBOX_SESSION_KEY_": "uuid-xxx"}
       └─ 来自 RunnableConfig.metadata: {"_AGENT_": "myAgent"}
```

---

## 六、在沙箱生命周期管理中的应用

### 6.1 推荐方案：使用 OverAllState 传递 sessionKey

| 方式 | 可用性 | 说明 |
|------|--------|------|
| **OverAllState** | ✅ **推荐** | beforeAgent 返回值合并到 state，interceptModel 通过 `request.getContext()` 访问 |
| **RunnableConfig** | ⚠️ 辅助 | metadata 只读，context 可变但不持久化 |
| **ThreadLocal** | ✅ 可用 | 但需要注意线程安全问题 |

### 6.2 实现示例

#### 1. beforeAgent 中生成 sessionKey

```java
@Override
public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
    String sessionKey = UUID.randomUUID().toString().replace("-", "");
    
    // 返回的 Map 会自动合并到 OverAllState.data
    return CompletableFuture.completedFuture(Map.of(
        SandboxConstants.AGENT_SESSION_KEY, sessionKey
    ));
}
```

#### 2. interceptModel 中读取 sessionKey

```java
@Override
public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
    // ✅ 从 context 中获取 sessionKey（来自 OverAllState）
    String sessionKey = (String) request.getContext().get(SandboxConstants.AGENT_SESSION_KEY);
    
    if (sessionKey != null) {
        // 使用 sessionKey 管理沙箱
        SandboxSession session = sessionManager.getSession(sessionKey);
        // ...
    }
    
    return handler.call(request);
}
```

#### 3. afterAgent 中清理沙箱

```java
@Override
public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
    String sessionKey = (String) state.value(SandboxConstants.AGENT_SESSION_KEY).orElse(null);
    if (sessionKey != null) {
        sessionManager.cleanup(sessionKey);
    }
    return CompletableFuture.completedFuture(Map.of());
}
```

---

## 七、关键注意事项

### 7.1 工程陷阱：不要在 Hook 里往 messages 写东西

`AgentLlmNode` 每轮执行后会往 `messages` 写一条 `AssistantMessage`，`AppendStrategy` 把它追加进去。如果 Hook 返回的 Map 里也带了 `messages` key，同样会被 `AppendStrategy` 追加——**同一条消息就进了列表两遍**，下次发给 LLM 时 token 翻倍，模型还会看到重复上下文。

```java
// ❌ 错误：Hook 返回 Map 里带 messages，会触发 AppendStrategy 再追加一次
return CompletableFuture.completedFuture(Map.of("messages", List.of(someMsg)));

// ✓ 正确：只观测不写，返回空 Map
return CompletableFuture.completedFuture(Map.of());

// ✓ 正确：需要存业务数据，写自定义 key（ReplaceStrategy，覆盖语义，安全）
return CompletableFuture.completedFuture(Map.of("llmCallCount", count + 1));
```

### 7.2 KeyStrategy 注册

自定义 key 需要在 Hook 的 `getKeyStrategys()` 方法中声明策略：

```java
@Override
public Map<String, KeyStrategy> getKeyStrategys() {
    // 框架构建时会把这里的 key 和策略注册到 StateGraph schema
    return Map.of(
        SandboxConstants.AGENT_SESSION_KEY, new ReplaceStrategy()
    );
}
```

### 7.3 CheckpointSaver 持久化

`MemorySaver`（`BaseCheckpointSaver` 的默认实现）在每次图执行完毕后，以 `threadId` 为 key 把整个 OverAllState 做一次快照，下次用同一个 `threadId` 请求时恢复。

**单机没问题，集群必须替换**。`MemorySaver` 是纯 JVM 内存，多实例部署时状态会丢失。需要实现 `BaseCheckpointSaver` 接入 Redis 或数据库。

---

## 八、总结

### 8.1 一句话总结

| 概念 | 一句话总结 |
|------|-----------|
| **OverAllState** | 图的业务数据库，存储和管理 Agent 运行过程中的所有业务数据，支持智能合并和持久化 |
| **RunnableConfig** | 图的运行环境配置，提供单次运行所需的标识符、配置参数和临时上下文 |
| **数据传递链路** | beforeAgent 返回值 → OverAllState.data → ModelRequest.context → interceptModel 可访问 |

### 8.2 简单记忆

- **OverAllState = 数据（Data）**
- **RunnableConfig = 配置（Config）**
- **沙箱会话 key 是业务数据，所以应该放在 OverAllState 中！** ✅

### 8.3 核心结论

1. ✅ **OverAllState 在单次 invoke() 内是同一个实例**，持续传递和更新
2. ✅ **每次 invoke() 都创建新的 OverAllState**，不会保留上一次调用的数据
3. ✅ **beforeAgent 返回值会自动合并到 OverAllState**
4. ✅ **AgentLlmNode 会将 OverAllState.data 复制到 ModelRequest.context**
5. ✅ **interceptModel 可以通过 `request.getContext()` 访问 OverAllState 数据**
6. ✅ **沙箱会话 key 应该放在 OverAllState 中**，这是业务数据，不是运行配置

---

## 九、参考资源

- [Spring AI Alibaba 1.x 系列【38】AgentLlmNode、AgentToolNode 构建、执行流程分析](https://blog.csdn.net/qq_43437874/article/details/159927940)
- [Agent账单多了一倍？从 OverAllState 到 Hook 的 ReactAgent 控制面全解](https://juejin.cn/post/7625956654749974574)
- Spring AI Alibaba 官方文档
- OverAllState.java 源码
- RunnableConfig.java 源码
- AgentLlmNode.java 源码
