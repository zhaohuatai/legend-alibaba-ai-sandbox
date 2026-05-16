package org.legend.framework.ai.alibaba.sandbox.tool.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP (Model Context Protocol) 客户端连接管理器。
 *
 * <p>该类负责管理与 MCP 服务器的 WebSocket 连接，处理 JSON-RPC 2.0 协议的请求和响应。
 *
 * <p>JSON-RPC 消息格式：
 * <pre>{@code
 * {
 *   "jsonrpc": "2.0",
 *   "id": 1,
 *   "method": "tools/call",
 *   "params": {
 *     "name": "fetch_url",
 *     "arguments": {"url": "https://example.com"}
 *   }
 * }
 * }</pre>
 *
 * @see McpToolCallback MCP 工具回调适配器
 * @see McpToolRegistry MCP 工具注册器
 */
public class McpClientManager implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(McpClientManager.class);

    private final String serverUrl;

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build();

    private volatile WebSocket webSocket;

    private final AtomicLong requestIdGenerator = new AtomicLong(1);

    private final Map<Long, CompletableFuture<JsonNode>> pendingRequests = new ConcurrentHashMap<>();

    private final ObjectMapper mapper = new ObjectMapper();

    private final int timeoutSeconds;

    public McpClientManager(String serverUrl) {
        this(serverUrl, 30);
    }

    public McpClientManager(String serverUrl, int timeoutSeconds) {
        this.serverUrl = serverUrl;
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * 初始化 MCP 客户端连接。
     */
    public void initialize() {
        try {
            CompletableFuture<WebSocket> wsFuture = httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .buildAsync(URI.create(serverUrl), new McpWebSocketListener());

            webSocket = wsFuture.get(timeoutSeconds, TimeUnit.SECONDS);
            log.info("MCP client connected to {}", serverUrl);

            sendInitializeRequest();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize MCP client", e);
        }
    }

    private void sendInitializeRequest() throws ExecutionException, InterruptedException, TimeoutException {
        ObjectNode params = mapper.createObjectNode();
        params.put("protocolVersion", "2024-11-05");
        params.putObject("clientInfo")
            .put("name", "legend-smartmind")
            .put("version", "1.0.0");

        JsonNode response = sendJsonRpcRequest("initialize", params);
        log.debug("MCP server initialized: {}", response);

        sendNotification("notifications/initialized", mapper.createObjectNode());
    }

    /**
     * 调用 MCP 工具。
     *
     * @param toolName 工具名称
     * @param arguments 工具参数 JSON
     * @return 工具执行结果文本
     */
    public String callTool(String toolName, JsonNode arguments)
            throws ExecutionException, InterruptedException, TimeoutException {
        ObjectNode params = mapper.createObjectNode();
        params.put("name", toolName);
        params.set("arguments", arguments);

        JsonNode response = sendJsonRpcRequest("tools/call", params);

        JsonNode content = response.get("content");
        if (content != null && content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode item : content) {
                if (item.has("text")) {
                    sb.append(item.get("text").asText());
                }
            }
            return sb.toString();
        }

        return response.toString();
    }

    /**
     * 调用 MCP 工具（使用 Map 参数）。
     */
    public String callToolWithMap(String toolName, Map<String, Object> arguments)
            throws ExecutionException, InterruptedException, TimeoutException {
        JsonNode argsNode = mapper.valueToTree(arguments);
        return callTool(toolName, argsNode);
    }

    /**
     * 获取 MCP 工具列表。
     */
    public JsonNode listTools() throws ExecutionException, InterruptedException, TimeoutException {
        return sendJsonRpcRequest("tools/list", mapper.createObjectNode());
    }

    /**
     * 发送 JSON-RPC 请求并等待响应。
     */
    private JsonNode sendJsonRpcRequest(String method, JsonNode params)
            throws ExecutionException, InterruptedException, TimeoutException {
        long requestId = requestIdGenerator.getAndIncrement();

        ObjectNode request = mapper.createObjectNode();
        request.put("jsonrpc", "2.0");
        request.put("id", requestId);
        request.put("method", method);
        request.set("params", params);

        CompletableFuture<JsonNode> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        webSocket.sendText(request.toString(), true);
        log.debug("Sent MCP request: {}", request);

        try {
            return future.get(timeoutSeconds, TimeUnit.SECONDS);
        } finally {
            pendingRequests.remove(requestId);
        }
    }

    /**
     * 发送 JSON-RPC 通知（不需要响应）。
     */
    private void sendNotification(String method, JsonNode params) {
        ObjectNode notification = mapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", method);
        notification.set("params", params);

        webSocket.sendText(notification.toString(), true);
        log.debug("Sent MCP notification: {}", notification);
    }

    /**
     * 处理接收到的 JSON-RPC 响应。
     */
    private void handleResponse(String message) {
        try {
            JsonNode json = mapper.readTree(message);

            if (json.has("id")) {
                long requestId = json.get("id").asLong();
                CompletableFuture<JsonNode> future = pendingRequests.remove(requestId);

                if (future != null) {
                    if (json.has("error")) {
                        future.completeExceptionally(new RuntimeException(json.get("error").toString()));
                    } else {
                        future.complete(json.get("result"));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle MCP response", e);
        }
    }

    @Override
    public void close() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client closing");
            log.info("MCP client connection closed");
        }
    }

    /**
     * MCP WebSocket 监听器实现。
     */
    private class McpWebSocketListener implements Listener {

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            handleResponse(data.toString());
            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("MCP WebSocket connection closed: {} - {}", statusCode, reason);

            for (CompletableFuture<JsonNode> future : pendingRequests.values()) {
                future.completeExceptionally(new RuntimeException("Connection closed"));
            }
            pendingRequests.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.error("MCP WebSocket error", error);

            for (CompletableFuture<JsonNode> future : pendingRequests.values()) {
                future.completeExceptionally(error);
            }
            pendingRequests.clear();
        }
    }
}
