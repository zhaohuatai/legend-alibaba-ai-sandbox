package org.legend.framework.ai.alibaba.sandbox.tool.mcp;

import org.springframework.ai.tool.ToolCallback;

import java.util.List;

/**
 * MCP (Model Context Protocol) 工具集工厂类。
 *
 * <p>该类是 MCP 工具的一站式工厂，负责创建和管理完整的 MCP 工具集合。
 * 工厂类封装了 MCP 客户端初始化、工具注册和连接管理的完整流程，
 * 使开发者可以通过简单的静态方法调用获取所有可用的 MCP 工具。
 *
 * <p>使用场景：
 * <ul>
 *   <li>快速集成 MCP 服务器提供的工具到 Spring AI 应用</li>
 *   <li>统一管理多个 MCP 服务器的工具集合</li>
 *   <li>简化 MCP 工具的初始化和注册流程</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 方式 1：从单个 MCP 服务器获取所有工具
 * List<ToolCallback> tools = McpToolset.from("ws://localhost:8080/mcp");
 *
 * // 方式 2：从多个 MCP 服务器获取工具
 * List<ToolCallback> tools = McpToolset.fromMultiple(
 *     "ws://server1:8080/mcp",
 *     "ws://server2:8080/mcp"
 * );
 *
 * // 方式 3：自定义超时时间
 * List<ToolCallback> tools = McpToolset.from("ws://localhost:8080/mcp", 60);
 *
 * // 注册到 ChatClient
 * ChatClient client = ChatClient.builder(chatModel)
 *     .defaultTools(tools.toArray(new ToolCallback[0]))
 *     .build();
 * }</pre>
 *
 * @see McpClientManager MCP 客户端连接管理器
 * @see McpToolRegistry MCP 工具注册器
 * @see McpToolCallback MCP 工具回调适配器
 */
public final class McpToolset {

    /**
     * 私有构造函数，防止实例化。
     * 该类仅提供静态工厂方法。
     */
    private McpToolset() {}

    /**
     * 从指定 MCP 服务器获取所有可用工具。
     *
     * <p>该方法执行以下操作：
     * <ol>
     *   <li>创建并初始化 MCP 客户端连接</li>
     *   <li>创建工具注册器并获取工具列表</li>
     *   <li>返回 ToolCallback 列表</li>
     * </ol>
     *
     * @param serverUrl MCP 服务器 WebSocket URL
     * @return MCP 工具回调列表
     */
    public static List<ToolCallback> from(String serverUrl) {
        return from(serverUrl, 30);
    }

    /**
     * 从指定 MCP 服务器获取所有可用工具（自定义超时时间）。
     *
     * @param serverUrl MCP 服务器 WebSocket URL
     * @param timeoutSeconds 连接和请求超时时间（秒）
     * @return MCP 工具回调列表
     */
    public static List<ToolCallback> from(String serverUrl, int timeoutSeconds) {
        McpClientManager client = new McpClientManager(serverUrl, timeoutSeconds);
        client.initialize();

        McpToolRegistry registry = new McpToolRegistry(client, timeoutSeconds);
        return registry.registerTools();
    }

    /**
     * 从多个 MCP 服务器获取所有可用工具。
     *
     * <p>该方法会连接到每个 MCP 服务器，获取所有工具并合并为一个列表。
     * 如果某个服务器连接失败，会记录错误但继续处理其他服务器。
     *
     * @param serverUrls MCP 服务器 WebSocket URL 列表
     * @return 合并后的 MCP 工具回调列表
     */
    public static List<ToolCallback> fromMultiple(String... serverUrls) {
        return fromMultiple(30, serverUrls);
    }

    /**
     * 从多个 MCP 服务器获取所有可用工具（自定义超时时间）。
     *
     * @param timeoutSeconds 连接和请求超时时间（秒）
     * @param serverUrls MCP 服务器 WebSocket URL 列表
     * @return 合并后的 MCP 工具回调列表
     */
    public static List<ToolCallback> fromMultiple(int timeoutSeconds, String... serverUrls) {
        java.util.List<ToolCallback> allTools = new java.util.ArrayList<>();

        for (String url : serverUrls) {
            try {
                List<ToolCallback> tools = from(url, timeoutSeconds);
                allTools.addAll(tools);
            } catch (Exception e) {
                org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(McpToolset.class);
                log.error("Failed to connect to MCP server: {}", url, e);
            }
        }

        return allTools;
    }
}
