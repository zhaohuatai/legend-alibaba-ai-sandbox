package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserNavigateForwardTool implements Function<BrowserToolInputs.NavigateForwardInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserNavigateForwardTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.NavigateForwardInput input) {
        try {
            return mcpClient.callTool("browser_navigate_forward", mapper.createObjectNode());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_navigate_forward", new BrowserNavigateForwardTool(mcpClient))
            .description("Navigate forward to the next page in browser history.")
            .inputType(BrowserToolInputs.NavigateForwardInput.class)
            .build();
    }
}
