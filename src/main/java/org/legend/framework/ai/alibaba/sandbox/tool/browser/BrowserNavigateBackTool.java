package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserNavigateBackTool implements Function<BrowserToolInputs.NavigateBackInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserNavigateBackTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.NavigateBackInput input) {
        try {
            return mcpClient.callTool("browser_navigate_back", mapper.createObjectNode());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_navigate_back", new BrowserNavigateBackTool(mcpClient))
            .description("Navigate back to the previous page in browser history.")
            .inputType(BrowserToolInputs.NavigateBackInput.class)
            .build();
    }
}
