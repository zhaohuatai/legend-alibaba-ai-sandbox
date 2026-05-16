package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserCloseTool implements Function<BrowserToolInputs.CloseInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserCloseTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.CloseInput input) {
        try {
            return mcpClient.callTool("browser_close", mapper.createObjectNode());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_close", new BrowserCloseTool(mcpClient))
            .description("Close the browser instance.")
            .inputType(BrowserToolInputs.CloseInput.class)
            .build();
    }
}
