package org.legend.framework.ai.alibaba.sandbox.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.ToolCallback;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * MCP (Model Context Protocol) 工具注册器。
 *
 * <p>该类负责从 MCP 服务器获取可用工具列表，并将它们注册为 Spring AI 的 {@link ToolCallback} 实例。
 * 注册器在初始化时自动查询 MCP 服务器的 tools/list 接口，获取所有可用工具的定义信息。
 *
 * <p>工具注册流程：
 * <pre>
 * 创建注册器实例
 *     ↓
 * 发送 tools/list 请求到 MCP 服务器
 *     ↓
 * 接收工具列表响应
 *     ↓
 * 为每个工具创建 McpToolCallback 实例
 *     ↓
 * 返回 ToolCallback 列表供注册使用
 * </pre>
 *
 * <p>MCP 工具定义格式：
 * <pre>{@code
 * {
 *   "name": "fetch_url",
 *   "description": "Fetches content from a URL",
 *   "inputSchema": {
 *     "type": "object",
 *     "properties": {
 *       "url": {
 *         "type": "string",
 *         "description": "The URL to fetch"
 *       }
 *     },
 *     "required": ["url"]
 *   }
 * }
 * }</pre>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 创建 MCP 客户端
 * McpClientManager client = new McpClientManager("ws://localhost:8080/mcp");
 * client.initialize();
 *
 * // 创建工具注册器并获取工具列表
 * McpToolRegistry registry = new McpToolRegistry(client);
 * List<ToolCallback> tools = registry.registerTools();
 *
 * // 注册到 ChatClient
 * ChatClient chatClient = ChatClient.builder(chatModel)
 *     .defaultTools(tools.toArray(new ToolCallback[0]))
 *     .build();
 * }</pre>
 *
 * @see McpClientManager MCP 客户端连接管理器
 * @see McpToolCallback MCP 工具回调适配器
 */
public class McpToolRegistry {

    /**
     * 日志记录器。
     */
    private static final Logger log = LoggerFactory.getLogger(McpToolRegistry.class);

    /**
     * MCP 客户端实例。
     */
    private final McpClientManager mcpClient;

    /**
     * JSON 对象映射器。
     */
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 请求超时时间（秒）。
     */
    private final int timeoutSeconds;

    /**
     * 创建 MCP 工具注册器实例。
     *
     * @param mcpClient MCP 客户端实例
     */
    public McpToolRegistry(McpClientManager mcpClient) {
        this(mcpClient, 30);
    }

    /**
     * 创建 MCP 工具注册器实例。
     *
     * @param mcpClient MCP 客户端实例
     * @param timeoutSeconds 请求超时时间（秒）
     */
    public McpToolRegistry(McpClientManager mcpClient, int timeoutSeconds) {
        this.mcpClient = mcpClient;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * 从 MCP 服务器注册所有可用工具。
     *
     * <p>该方法执行以下操作：
     * <ol>
     *   <li>发送 tools/list 请求到 MCP 服务器</li>
     *   <li>解析响应中的工具列表</li>
     *   <li>为每个工具创建 McpToolCallback 实例</li>
     *   <li>返回 ToolCallback 列表</li>
     * </ol>
     *
     * @return MCP 工具回调列表
     * @throws RuntimeException 如果工具注册失败
     */
    public List<ToolCallback> registerTools() {
        try {
            JsonNode toolsList = fetchToolsList();
            return createToolCallbacks(toolsList);
        } catch (Exception e) {
            throw new RuntimeException("Failed to register MCP tools", e);
        }
    }

    /**
     * 从 MCP 服务器获取工具列表。
     *
     * @return 工具列表 JSON 节点
     * @throws ExecutionException 如果请求执行失败
     * @throws InterruptedException 如果请求被中断
     * @throws TimeoutException 如果请求超时
     */
    private JsonNode fetchToolsList() throws ExecutionException, InterruptedException, TimeoutException {
        return mcpClient.listTools();
    }

    /**
     * 为工具列表创建 ToolCallback 实例。
     *
     * @param toolsList 工具列表 JSON 节点
     * @return ToolCallback 实例列表
     */
    private List<ToolCallback> createToolCallbacks(JsonNode toolsList) {
        List<ToolCallback> callbacks = new ArrayList<>();

        JsonNode tools = toolsList.has("tools") ? toolsList.get("tools") : toolsList;

        if (tools.isArray()) {
            for (JsonNode tool : tools) {
                String name = tool.get("name").asText();
                String description = tool.has("description") ? tool.get("description").asText() : "";

                String schema = "{}";
                if (tool.has("inputSchema")) {
                    schema = tool.get("inputSchema").toString();
                } else if (tool.has("parameters")) {
                    schema = tool.get("parameters").toString();
                }

                McpToolCallback callback = new McpToolCallback(
                    mcpClient,
                    name,
                    description,
                    schema
                );

                callbacks.add(callback);
                log.info("Registered MCP tool: {}", name);
            }
        }

        return callbacks;
    }
}
