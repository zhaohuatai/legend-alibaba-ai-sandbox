package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserTabSelectTool implements Function<BrowserToolInputs.TabSelectInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserTabSelectTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.TabSelectInput input) {
        try {
            var args = mapper.createObjectNode();
            args.put("index", input.index());
            return mcpClient.callTool("browser_tab_select", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_tab_select", new BrowserTabSelectTool(mcpClient))
            .description("Select a browser tab by index.")
            .inputType(BrowserToolInputs.TabSelectInput.class)
            .build();
    }
}
