package org.legend.framework.ai.alibaba.sandbox.agent.hook;

import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;

import org.legend.framework.ai.alibaba.sandbox.SandboxConstants;
import org.legend.framework.ai.alibaba.sandbox.agent.interceptor.GuardedSkillsInterceptor;
import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackendProvider;
import org.legend.framework.ai.alibaba.sandbox.backend.SandboxSessionManager;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvContext;
import org.legend.framework.ai.alibaba.sandbox.skills.registry.GuardedSkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 带安全门控的 Skills Agent Hook，继承 Spring AI 的 AgentHook。
 * 
 * <p>该类在标准 SkillsAgentHook 基础上添加了安全门控功能：
 * <ul>
 *   <li>使用 GuardedSkillRegistry 替代标准 SkillRegistry</li>
 *   <li>返回 GuardedSkillsInterceptor 而非标准 SkillsInterceptor</li>
 *   <li>支持 groupedTools 配置，用于动态工具注入</li>
 * </ul>
 * 
 * <p>继承关系：
 * <pre>
 * AgentHook (接口)
 *   └── SkillsAgentHook (Spring AI 实现)
 *         └── GuardedSkillsAgentHook (我们的安全门控实现)
 * </pre>
 * 
 * <p>使用示例：
 * <pre>{@code
 * GuardedSkillRegistry registry = GuardedSkillRegistry.builder()
 *     .skillsDirectory("C:\\Users\\xxx\\.claude\\skills", "user")
 *     .env(envContext)
 *     .build();
 * 
 * GuardedSkillsAgentHook hook = GuardedSkillsAgentHook.builder()
 *     .skillRegistry(registry)
 *     .autoReload(true)
 *     .build();
 * }</pre>
 */
@HookPositions({HookPosition.BEFORE_AGENT, HookPosition.AFTER_AGENT})
public class GuardedSkillsAgentHook extends AgentHook {
	private static final Logger logger = LoggerFactory.getLogger(GuardedSkillsAgentHook.class);
	
    // 委托给官方实现
    private final SkillsAgentHook delegate;
    private final Map<String, List<ToolCallback>> groupedTools;
    private final ToolCallbackResolver toolCallbackResolver;
    
    private final SandboxBackendProvider sandboxBackendProvider;
    private final EnvContext envContext;
    private final SandboxSessionManager sessionManager;

    private GuardedSkillsAgentHook(Builder builder) {
    	   // 构建官方的 Hook
        this.delegate = SkillsAgentHook.builder()
            .skillRegistry(builder.skillRegistry)
            .autoReload(builder.autoReload)
            .groupedTools(builder.groupedTools)
//            .toolCallbackResolver(builder.toolCallbackResolver) // 更新以后才有的功能
            .build();
        
        this.groupedTools = builder.groupedTools != null ? builder.groupedTools : Collections.emptyMap();
        this.toolCallbackResolver = builder.toolCallbackResolver;
        
        this.sandboxBackendProvider = builder.sandboxBackendProvider;
        this.envContext = builder.envContext;
        this.sessionManager = builder.sessionManager != null ? builder.sessionManager : new SandboxSessionManager();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
    	String invokeId = UUID.randomUUID().toString().replace("-", "");
    	
    	 // 调用 delegate 并合并返回值
        CompletableFuture<Map<String, Object>> completableFuture = delegate.beforeAgent(state, config);
        
        return completableFuture.thenApply(delegateResult -> {
            Map<String, Object> merged = new HashMap<>(delegateResult);
            merged.put(SandboxConstants.INVOKE_ID_KEY, invokeId);
            return merged;
        });
    }

    @Override
    public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
    	// 先调用 delegate，确保 delegate 能访问所有资源
        CompletableFuture<Map<String, Object>> result = delegate.afterAgent(state, config);
        
        // 再清理沙箱资源
        String invokeId = (String) state.value(SandboxConstants.INVOKE_ID_KEY).orElse(null);
        if (invokeId != null) {
            try {
                sessionManager.cleanupByInvokeId(invokeId);
                logger.info("Cleaned up all sandbox sessions for invokeId: {}", invokeId);
            } catch (Exception e) {
                logger.error("Failed to cleanup sandbox sessions for invokeId: {}", invokeId, e);
            }
        }
        
        return result;
    }

    @Override
    public List<ModelInterceptor> getModelInterceptors() {
          	GuardedSkillsInterceptor.Builder interceptorBuilder = GuardedSkillsInterceptor.builder()
                        .skillRegistry((GuardedSkillRegistry)delegate.getSkillRegistry())
                        .sandboxBackendProvider(this.sandboxBackendProvider)
                        .envContext(this.envContext)
                        .sessionManager(this.sessionManager);

              if (!this.groupedTools.isEmpty()) {
                   interceptorBuilder.groupedTools(this.groupedTools);
              }
              if (this.toolCallbackResolver != null) {
                    interceptorBuilder.toolCallbackResolver(this.toolCallbackResolver);
               }
        return List.of(interceptorBuilder.build());
    }

    @Override
    public List<ToolCallback> getTools() {
    	 return delegate.getTools();  // 自动获得 3 个工具
    }
    
//    /**
//     * 获取带安全门控的 Skill 注册表。
//     */
//    public GuardedSkillRegistry getSkillRegistry() {
//        return skillRegistry;
//    }
//
//    public int getSkillCount() {
//        return skillRegistry.size();
//    }
//
//    public boolean hasSkill(String skillName) {
//        return skillRegistry.contains(skillName);
//    }
//
//    public List<GuardedSkillMetadata> listSkills() {
//        return skillRegistry.listAll().stream()
//            .filter(m -> m instanceof GuardedSkillMetadata)
//            .map(m -> (GuardedSkillMetadata) m)
//            .toList();
//    }

    @Override
    public String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * Builder for creating GuardedSkillsAgentHook instances.
     */
    public static class Builder {
        private GuardedSkillRegistry skillRegistry;
        private boolean autoReload = false;
        private Map<String, List<ToolCallback>> groupedTools;
        private ToolCallbackResolver toolCallbackResolver;
        private SandboxBackendProvider sandboxBackendProvider;
        private EnvContext envContext;
        private SandboxSessionManager sessionManager;

        public Builder skillRegistry(GuardedSkillRegistry skillRegistry) {
            this.skillRegistry = skillRegistry;
            return this;
        }

        public Builder autoReload(boolean autoReload) {
            this.autoReload = autoReload;
            return this;
        }

        public Builder groupedTools(Map<String, List<ToolCallback>> groupedTools) {
            this.groupedTools = groupedTools;
            return this;
        }

        public Builder toolCallbackResolver(ToolCallbackResolver toolCallbackResolver) {
            this.toolCallbackResolver = toolCallbackResolver;
            return this;
        }

        public Builder sandboxBackendProvider(SandboxBackendProvider sandboxBackendProvider) {
            this.sandboxBackendProvider = sandboxBackendProvider;
            return this;
        }

        public Builder envContext(EnvContext envContext) {
            this.envContext = envContext;
            return this;
        }

        public Builder sessionManager(SandboxSessionManager sessionManager) {
            this.sessionManager = sessionManager;
            return this;
        }

        public GuardedSkillsAgentHook build() {
            return new GuardedSkillsAgentHook(this);
        }
    }
}
