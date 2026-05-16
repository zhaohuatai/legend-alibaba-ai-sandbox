package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserTypeTool implements Function<BrowserToolInputs.TypeInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserTypeTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.TypeInput input) {
        try {
            var args = mapper.createObjectNode();
            args.put("element", input.element());
            args.put("ref", input.ref());
            args.put("text", input.text());
            if (input.submit() != null) args.put("submit", input.submit());
            if (input.slowly() != null) args.put("slowly", input.slowly());
            return mcpClient.callTool("browser_type", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_type", new BrowserTypeTool(mcpClient))
            .description("Type text into an input field. Optionally submit or type slowly.")
            .inputType(BrowserToolInputs.TypeInput.class)
            .build();
    }
}
