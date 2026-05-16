package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserSnapshotTool implements Function<BrowserToolInputs.SnapshotInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserSnapshotTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.SnapshotInput input) {
        try {
            var args = mapper.createObjectNode();
            return mcpClient.callTool("browser_snapshot", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_snapshot", new BrowserSnapshotTool(mcpClient))
            .description("Capture the accessibility snapshot of the current page in text format.")
            .inputType(BrowserToolInputs.SnapshotInput.class)
            .build();
    }
}
