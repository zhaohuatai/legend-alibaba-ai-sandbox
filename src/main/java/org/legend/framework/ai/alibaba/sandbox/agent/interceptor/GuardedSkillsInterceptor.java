package org.legend.framework.ai.alibaba.sandbox.agent.interceptor;

import com.alibaba.cloud.ai.graph.agent.hook.skills.ReadSkillTool;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.alibaba.cloud.ai.graph.skills.SkillMetadata;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.ai.util.json.JsonParser;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.legend.framework.ai.alibaba.sandbox.GuardedSkillMetadata;
import org.legend.framework.ai.alibaba.sandbox.SandboxConstants;
import org.legend.framework.ai.alibaba.sandbox.backend.BackendLease;
import org.legend.framework.ai.alibaba.sandbox.backend.SandboxAwareToolset;
import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackendProvider;
import org.legend.framework.ai.alibaba.sandbox.backend.SandboxSessionManager;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvContext;
import org.legend.framework.ai.alibaba.sandbox.skills.manifest.ToolPolicy;
import org.legend.framework.ai.alibaba.sandbox.skills.registry.GuardedSkillRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY;
import static com.alibaba.cloud.ai.graph.skills.SkillPromptConstants.buildSkillsPrompt;

/**
 * 带安全门控的 Skills Interceptor，实现 ModelInterceptor 接口。
 * 
 * <p>该类实现完整的 Skill 安全门控功能：
 * <ul>
 *   <li>使用 GuardedSkillRegistry 获取带安全元数据的 Skill</li>
 *   <li>支持 groupedTools 配置，用于动态工具注入</li>
 *   <li>动态创建带 GuardedSkillMetadata 的工具回调，直接传递安全元数据</li>
 * </ul>
 * 
 * <p>安全门控流程：
 * <ol>
 *   <li>LLM 调用 read_skill 工具读取 Skill 内容</li>
 *   <li>GuardedSkillsInterceptor 拦截模型调用</li>
 *   <li>从 AssistantMessage 中提取 read_skill 调用</li>
 *   <li>为每个被读取的 Skill 创建带安全门控的工具回调</li>
 *   <li>增强系统提示，注入 Skill 列表和使用说明</li>
 * </ol>
 */
public class GuardedSkillsInterceptor extends ModelInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(GuardedSkillsInterceptor.class);

    private final GuardedSkillRegistry skillRegistry;

    private final SandboxBackendProvider sandboxBackendProvider;

    private final EnvContext envContext;

    private final Map<String, List<ToolCallback>> groupedTools;

    private final ToolCallbackResolver toolCallbackResolver;

    private final SandboxSessionManager sessionManager;


    private GuardedSkillsInterceptor(Builder builder) {
        if (builder.skillRegistry == null) {
            throw new IllegalArgumentException("GuardedSkillRegistry must be provided");
        }
        this.skillRegistry = builder.skillRegistry;
        this.sandboxBackendProvider = builder.sandboxBackendProvider;
        this.envContext = builder.envContext;
        this.groupedTools = builder.groupedTools != null ? builder.groupedTools : Collections.emptyMap();
        this.toolCallbackResolver = builder.toolCallbackResolver;
        this.sessionManager = builder.sessionManager;
    }

    public static Builder builder() {
        return new Builder();
    }
    
    /**
     * 为指定 Skill 创建带安全门控的工具回调列表。
     */
    private List<ToolCallback> createGuardedToolsForSkill(GuardedSkillMetadata skillMetadata, String invokeId) {
        if (sandboxBackendProvider == null || envContext == null || sessionManager == null) {
            return List.of();
        }
        
        BackendLease backendLease = sessionManager.getOrCreateLease(
            invokeId,
            skillMetadata.getName(),
            () -> sandboxBackendProvider.acquireForGuardedSkill(skillMetadata)
        );
        
        SandboxBackend sandboxBackend = backendLease.backend();
        
        return SandboxAwareToolset.create(sandboxBackend, envContext, skillMetadata);
    }

    @Override
    public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
        List<SkillMetadata> skills = skillRegistry.listAll();

        if (skills.isEmpty()) {
            return handler.call(request);
        }
        
        String invokeId = (String) request.getContext().get(SandboxConstants.INVOKE_ID_KEY);
        if (invokeId == null) {
            logger.warn("invokeId is null, skipping sandbox creation");
            return handler.call(request);
        }
        
        // 1. Extract skill names from AssistantMessage with read_skill tool calls
        List<SkillMetadata> extractedkillMetadatas = extractReadSkills(request.getMessages());

     // 2. Collect tools from getGroupedTools for those skill names
        List<ToolCallback> skillTools = new ArrayList<>(request.getDynamicToolCallbacks());
        
        Map<String, List<ToolCallback>> grouped = getGroupedTools();
        
        for (SkillMetadata skillMeta : extractedkillMetadatas) {
            if (skillMeta instanceof GuardedSkillMetadata guardedSkillMeta) {
                List<ToolCallback> guardedTools = createGuardedToolsForSkill(guardedSkillMeta, invokeId);
                skillTools.addAll(guardedTools);
                if (logger.isInfoEnabled()) {
                    logger.info("GuardedSkillsInterceptor: added {} guarded tool(s) for skill '{}'",
                            guardedTools.size(), skillMeta.getName());
                }
            }

            List<ToolCallback> toolsForSkill = grouped.get(skillMeta.getName());
            
            if (toolsForSkill != null && !toolsForSkill.isEmpty()) {
                skillTools.addAll(toolsForSkill);
                if (logger.isInfoEnabled()) {
                    logger.info("GuardedSkillsInterceptor: added {} grouped tool(s) for skill '{}'",
                            toolsForSkill.size(), skillMeta.getName());
                }
            }
            skillTools.addAll(resolveAllowedTools(skillMeta));
        }
        skillTools = deduplicateByName(skillTools);

        String skillsPrompt = buildSkillsPrompt(skills, skillRegistry, skillRegistry.getSystemPromptTemplate());
        SystemMessage enhanced = enhanceSystemMessage(request.getSystemMessage(), skillsPrompt);

        if (logger.isDebugEnabled()) {
            logger.debug("Enhanced system message:\n{}", enhanced.getText());
        }

        ModelRequest modified = ModelRequest.builder(request)
                .systemMessage(enhanced)
                .dynamicToolCallbacks(skillTools)
                .build();

        return handler.call(modified);
    }

   

    /**
     * 从消息列表中提取 read_skill 调用对应的 Skill。
     */
    private List<SkillMetadata> extractReadSkills(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        Map<String, SkillMetadata> skillsByName = new LinkedHashMap<>();
        // 防御性复制
        List<Message> snapshot = new ArrayList<>(messages);
        for (Message message : snapshot) {
            if (!(message instanceof AssistantMessage assistantMessage) || !assistantMessage.hasToolCalls()) {
                continue;
            }
            for (AssistantMessage.ToolCall toolCall : assistantMessage.getToolCalls()) {
                if (!ReadSkillTool.READ_SKILL.equals(toolCall.name())) {
                    continue;
                }
                resolveSkillFromArguments(toolCall.arguments())
                        .ifPresent(skillMeta -> skillsByName.putIfAbsent(skillMeta.getName(), skillMeta));
            }
        }
        return List.copyOf(skillsByName.values());
    }

    /**
     * 从参数中解析 Skill。
     */
    private Optional<SkillMetadata> resolveSkillFromArguments(String arguments) {
        Map<String, Object> parsedArguments = parseArguments(arguments);
        if (parsedArguments.isEmpty()) {
            return Optional.empty();
        }

        String skillName = getStringValue(parsedArguments, "skill_name");
        String skillPath = getStringValue(parsedArguments, "skill_path");
        if (skillName == null && skillPath == null) {
            return Optional.empty();
        }

        Optional<SkillMetadata> skillByName = skillName != null ? skillRegistry.get(skillName) : Optional.empty();
        Optional<SkillMetadata> skillByPath = skillPath != null ? findSkillByPath(skillPath) : Optional.empty();
        if (skillName != null && skillPath != null) {
            if (skillByName.isEmpty() || skillByPath.isEmpty()) {
                return Optional.empty();
            }
            if (!skillByName.get().getName().equals(skillByPath.get().getName())) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Ignoring read_skill call because skill_name '{}' and skill_path '{}' do not match",skillName, skillPath);
                }
                return Optional.empty();
            }
            return skillByName;
        }
        return skillByName.isPresent() ? skillByName : skillByPath;
    }

    /**
     * 通过路径查找 Skill。
     */
    private Optional<SkillMetadata> findSkillByPath(String skillPath) {
        return skillRegistry.listAll().stream()
            .filter(s -> {
                String path = s.getSkillPath();
                return path != null && (path.equals(skillPath) || path.endsWith(skillPath) || skillPath.endsWith(path));
            })
            .findFirst();
    }

    /**
     * 解析参数 JSON。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return Map.of();
        }
        try {
            Object parsed = JsonParser.fromJson(arguments, Map.class);
            if (parsed instanceof Map<?, ?> map) {
                return (Map<String, Object>) map;
            }
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Failed to parse read_skill arguments: {}", e.getMessage());
            }
        }
        return Map.of();
    }

    /**
     * 从参数 Map 中获取字符串值。
     */
    private static String getStringValue(Map<String, Object> arguments, String key) {
        if (arguments == null || arguments.isEmpty()) {
            return null;
        }
        Object value = arguments.get(key);
        if (value == null) {
            return null;
        }
        String text = value.toString().trim();
        return StringUtils.hasText(text) ? text : null;
    }

    /**
     * 解析 Skill 声明的 allowed_tools。
     */
    private List<ToolCallback> resolveAllowedTools(SkillMetadata skill) {
        if (toolCallbackResolver == null) {
            return List.of();
        }
        
        List<String> allowedToolNames;
        if (skill instanceof GuardedSkillMetadata guarded) {
            allowedToolNames = extractToolNames(guarded.getToolPolicies());
        } else {
            allowedToolNames = List.of();
        }
        
        if (allowedToolNames.isEmpty()) {
            return List.of();
        }
        
        List<ToolCallback> resolvedTools = new ArrayList<>();
        for (String toolName : allowedToolNames) {
            ToolCallback toolCallback = toolCallbackResolver.resolve(toolName);
            if (toolCallback == null) {
                logger.debug("GuardedSkillsInterceptor: allowed tool '{}' declared by skill '{}' could not be resolved",
                        toolName, skill.getName());
                continue;
            }
            resolvedTools.add(toolCallback);
        }
        return resolvedTools;
    }

    /**
     * 从工具策略列表中提取工具名称。
     */
    private List<String> extractToolNames(List<ToolPolicy> policies) {
        if (policies == null) {
            return List.of();
        }
        return policies.stream()
            .map(ToolPolicy::tool)
            .distinct()
            .toList();
    }

    /**
     * 增强系统提示消息。
     */
    private SystemMessage enhanceSystemMessage(SystemMessage existing, String skillsSection) {
        if (existing == null) {
            return new SystemMessage(skillsSection);
        }
        return new SystemMessage(existing.getText() + "\n\n" + skillsSection);
    }

    /**
     * 获取分组工具映射。
     */
    public Map<String, List<ToolCallback>> getGroupedTools() {
        if (groupedTools.isEmpty()) {
            return Collections.emptyMap();
        }
        return groupedTools.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> List.copyOf(e.getValue())));
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    /**
     * 按名称去重工具回调。
     */
    private static List<ToolCallback> deduplicateByName(List<ToolCallback> callbacks) {
        Map<String, ToolCallback> map = new LinkedHashMap<>();
        for (ToolCallback callback : callbacks) {
            map.putIfAbsent(callback.getToolDefinition().name(), callback);
        }
        return List.copyOf(map.values());
    }

    /**
     * Builder for creating GuardedSkillsInterceptor instances.
     */
    public static class Builder {
        private GuardedSkillRegistry skillRegistry;
        private Map<String, List<ToolCallback>> groupedTools;
        private ToolCallbackResolver toolCallbackResolver;
        private SandboxBackendProvider sandboxBackendProvider;
        private EnvContext envContext;
        private SandboxSessionManager sessionManager;

        public Builder skillRegistry(GuardedSkillRegistry skillRegistry) {
            this.skillRegistry = skillRegistry;
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

        public GuardedSkillsInterceptor build() {
            return new GuardedSkillsInterceptor(this);
        }
    }
}
