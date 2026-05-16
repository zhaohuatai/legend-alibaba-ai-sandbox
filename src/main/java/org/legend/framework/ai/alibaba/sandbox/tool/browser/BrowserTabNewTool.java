package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserTabNewTool implements Function<BrowserToolInputs.TabNewInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserTabNewTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.TabNewInput input) {
        try {
            var args = mapper.createObjectNode();
            if (input.url() != null) args.put("url", input.url());
            return mcpClient.callTool("browser_tab_new", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_tab_new", new BrowserTabNewTool(mcpClient))
            .description("Open a new tab with an optional URL.")
            .inputType(BrowserToolInputs.TabNewInput.class)
            .build();
    }
}
