package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserTabCloseTool implements Function<BrowserToolInputs.TabCloseInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserTabCloseTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.TabCloseInput input) {
        try {
            var args = mapper.createObjectNode();
            args.put("index", input.index());
            return mcpClient.callTool("browser_tab_close", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_tab_close", new BrowserTabCloseTool(mcpClient))
            .description("Close a browser tab by index.")
            .inputType(BrowserToolInputs.TabCloseInput.class)
            .build();
    }
}
