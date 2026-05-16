package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserPressKeyTool implements Function<BrowserToolInputs.PressKeyInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserPressKeyTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.PressKeyInput input) {
        try {
            var args = mapper.createObjectNode();
            args.put("key", input.key());
            return mcpClient.callTool("browser_press_key", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_press_key", new BrowserPressKeyTool(mcpClient))
            .description("Press a key on the keyboard (e.g., Enter, Tab, Escape).")
            .inputType(BrowserToolInputs.PressKeyInput.class)
            .build();
    }
}
