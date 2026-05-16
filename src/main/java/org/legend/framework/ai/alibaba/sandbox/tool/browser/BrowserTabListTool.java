package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserTabListTool implements Function<BrowserToolInputs.TabListInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserTabListTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.TabListInput input) {
        try {
            return mcpClient.callTool("browser_tab_list", mapper.createObjectNode());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_tab_list", new BrowserTabListTool(mcpClient))
            .description("List all open browser tabs with their URLs and titles.")
            .inputType(BrowserToolInputs.TabListInput.class)
            .build();
    }
}
