package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserHoverTool implements Function<BrowserToolInputs.HoverInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserHoverTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.HoverInput input) {
        try {
            var args = mapper.createObjectNode();
            args.put("element", input.element());
            args.put("ref", input.ref());
            return mcpClient.callTool("browser_hover", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_hover", new BrowserHoverTool(mcpClient))
            .description("Hover the mouse over an element on the page.")
            .inputType(BrowserToolInputs.HoverInput.class)
            .build();
    }
}
