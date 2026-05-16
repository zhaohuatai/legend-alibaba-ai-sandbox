package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserNavigateTool implements Function<BrowserToolInputs.NavigateInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserNavigateTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.NavigateInput input) {
        try {
            var args = mapper.createObjectNode();
            args.put("url", input.url());
            return mcpClient.callTool("browser_navigate", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_navigate", new BrowserNavigateTool(mcpClient))
            .description("Navigate the browser to a URL. Returns the page title and status.")
            .inputType(BrowserToolInputs.NavigateInput.class)
            .build();
    }
}
