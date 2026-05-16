package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserClickTool implements Function<BrowserToolInputs.ClickInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserClickTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.ClickInput input) {
        try {
            var args = mapper.createObjectNode();
            args.put("element", input.element());
            args.put("ref", input.ref());
            return mcpClient.callTool("browser_click", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_click", new BrowserClickTool(mcpClient))
            .description("Clicks on the provided element.")
            .inputType(BrowserToolInputs.ClickInput.class)
            .build();
    }
}
