package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserScreenshotTool implements Function<BrowserToolInputs.ScreenshotInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserScreenshotTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.ScreenshotInput input) {
        try {
            var args = mapper.createObjectNode();
            if (input.raw() != null) args.put("raw", input.raw());
            if (input.filename() != null) args.put("filename", input.filename());
            if (input.element() != null) args.put("element", input.element());
            if (input.ref() != null) args.put("ref", input.ref());
            return mcpClient.callTool("browser_screenshot", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_screenshot", new BrowserScreenshotTool(mcpClient))
            .description("Take a screenshot of the current page or a specific element.")
            .inputType(BrowserToolInputs.ScreenshotInput.class)
            .build();
    }
}
