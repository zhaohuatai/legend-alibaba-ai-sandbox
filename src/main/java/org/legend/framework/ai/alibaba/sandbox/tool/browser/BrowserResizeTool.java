package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserResizeTool implements Function<BrowserToolInputs.ResizeInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserResizeTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.ResizeInput input) {
        try {
            var args = mapper.createObjectNode();
            if (input.width() != null) args.put("width", input.width());
            if (input.height() != null) args.put("height", input.height());
            return mcpClient.callTool("browser_resize", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_resize", new BrowserResizeTool(mcpClient))
            .description("Resize the browser window to the specified dimensions.")
            .inputType(BrowserToolInputs.ResizeInput.class)
            .build();
    }
}
