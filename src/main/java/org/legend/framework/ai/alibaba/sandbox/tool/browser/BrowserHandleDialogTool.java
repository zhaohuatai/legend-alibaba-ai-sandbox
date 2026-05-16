package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserHandleDialogTool implements Function<BrowserToolInputs.HandleDialogInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserHandleDialogTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.HandleDialogInput input) {
        try {
            var args = mapper.createObjectNode();
            if (input.accept() != null) args.put("accept", input.accept());
            if (input.promptText() != null) args.put("promptText", input.promptText());
            return mcpClient.callTool("browser_handle_dialog", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_handle_dialog", new BrowserHandleDialogTool(mcpClient))
            .description("Handle a browser dialog (alert/confirm/prompt).")
            .inputType(BrowserToolInputs.HandleDialogInput.class)
            .build();
    }
}
