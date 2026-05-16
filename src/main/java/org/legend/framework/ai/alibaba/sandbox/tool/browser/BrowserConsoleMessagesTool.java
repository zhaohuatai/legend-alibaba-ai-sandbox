package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserConsoleMessagesTool implements Function<BrowserToolInputs.ConsoleMessagesInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserConsoleMessagesTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.ConsoleMessagesInput input) {
        try {
            return mcpClient.callTool("browser_console_messages", mapper.createObjectNode());
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_console_messages", new BrowserConsoleMessagesTool(mcpClient))
            .description("Retrieve console messages from the current browser page.")
            .inputType(BrowserToolInputs.ConsoleMessagesInput.class)
            .build();
    }
}
