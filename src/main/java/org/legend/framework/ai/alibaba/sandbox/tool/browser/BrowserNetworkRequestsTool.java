package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserNetworkRequestsTool implements Function<BrowserToolInputs.NetworkRequestsInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserNetworkRequestsTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.NetworkRequestsInput input) {
        try {
            return mcpClient.callTool("browser_network_requests", mapper.createObjectNode());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_network_requests", new BrowserNetworkRequestsTool(mcpClient))
            .description("Retrieve network requests made by the current page.")
            .inputType(BrowserToolInputs.NetworkRequestsInput.class)
            .build();
    }
}
