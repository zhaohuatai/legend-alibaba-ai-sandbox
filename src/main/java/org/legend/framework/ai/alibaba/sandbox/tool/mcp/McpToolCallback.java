package org.legend.framework.ai.alibaba.sandbox.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

/**
 * MCP (Model Context Protocol) 工具回调适配器。
 *
 * <p>该类将 MCP 服务器提供的工具适配为 Spring AI 的 {@link ToolCallback} 接口，
 * 使 MCP 工具可以无缝集成到 Spring AI 的工具调用框架中。
 *
 * <p>MCP 工具调用流程：
 * <pre>
 * LLM 发起工具调用请求
 *     ↓
 * McpToolCallback.call(toolInput)
 *     ↓
 * 解析 JSON 参数
 *     ↓
 * 通过 MCP 客户端发送 tools/call 请求到 MCP 服务器
 *     ↓
 * 接收 MCP 服务器响应
 *     ↓
 * 返回工具执行结果
 * </pre>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 创建 MCP 工具回调
 * McpToolCallback mcpTool = new McpToolCallback(
 *     mcpClient,
 *     "fetch_url",
 *     "Fetches content from a URL",
 *     parameterSchema
 * );
 *
 * // 注册到 ChatClient
 * ChatClient client = ChatClient.builder(chatModel)
 *     .defaultTools(mcpTool)
 *     .build();
 * }</pre>
 *
 * @see ToolCallback Spring AI 工具回调接口
 * @see ToolDefinition 工具定义，包含名称、描述和参数 schema
 * @see ToolMetadata 工具元数据
 */
public class McpToolCallback implements ToolCallback {

    /**
     * MCP 客户端实例，用于与 MCP 服务器通信。
     */
    private final McpClientManager mcpClient;

    /**
     * MCP 工具名称。
     */
    private final String toolName;

    /**
     * MCP 工具描述。
     */
    private final String toolDescription;

    /**
     * MCP 工具参数 JSON Schema。
     */
    private final String parameterSchema;

    /**
     * JSON 对象映射器，用于解析和生成 JSON。
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 创建 MCP 工具回调适配器实例。
     *
     * @param mcpClient MCP 客户端实例，用于与 MCP 服务器通信
     * @param toolName MCP 工具名称
     * @param toolDescription MCP 工具描述
     * @param parameterSchema MCP 工具参数 JSON Schema
     */
    public McpToolCallback(
        McpClientManager mcpClient,
        String toolName,
        String toolDescription,
        String parameterSchema
    ) {
        this.mcpClient = mcpClient;
        this.toolName = toolName;
        this.toolDescription = toolDescription;
        this.parameterSchema = parameterSchema;
    }

    /**
     * 获取工具定义。
     *
     * @return 工具定义实例，包含工具名称、描述和参数 schema
     */
    @Override
    public ToolDefinition getToolDefinition() {
        return ToolDefinition.builder()
            .name(toolName)
            .description(toolDescription)
            .inputSchema(parameterSchema)
            .build();
    }

    /**
     * 获取工具元数据。
     *
     * @return 工具元数据实例
     */
    @Override
    public ToolMetadata getToolMetadata() {
        return ToolMetadata.builder().build();
    }

    /**
     * 执行 MCP 工具调用。
     *
     * <p>该方法将工具调用请求转发到 MCP 服务器，并返回执行结果。
     *
     * @param toolInput 工具调用的 JSON 参数字符串
     * @return MCP 服务器返回的工具执行结果
     * @throws RuntimeException 如果 MCP 工具调用失败
     */
    @Override
    public String call(String toolInput) {
        try {
            JsonNode args = mapper.readTree(toolInput);
            String result = mcpClient.callTool(toolName, args);
            return result;
        } catch (Exception e) {
            throw new RuntimeException("MCP tool call failed: " + toolName, e);
        }
    }

    /**
     * 执行 MCP 工具调用（带 ToolContext 参数）。
     *
     * @param toolInput 工具调用的 JSON 参数字符串
     * @param toolContext Spring AI 的工具上下文
     * @return MCP 服务器返回的工具执行结果
     */
    @Override
    public String call(String toolInput, org.springframework.ai.chat.model.ToolContext toolContext) {
        return call(toolInput);
    }
}
