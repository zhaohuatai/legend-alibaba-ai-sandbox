package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserWaitForTool implements Function<BrowserToolInputs.WaitForInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserWaitForTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.WaitForInput input) {
        try {
            var args = mapper.createObjectNode();
            if (input.time() != null) args.put("time", input.time());
            if (input.text() != null) args.put("text", input.text());
            if (input.textGone() != null) args.put("textGone", input.textGone());
            return mcpClient.callTool("browser_wait_for", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_wait_for", new BrowserWaitForTool(mcpClient))
            .description("Wait for a specified time, or for text to appear/disappear on the page.")
            .inputType(BrowserToolInputs.WaitForInput.class)
            .build();
    }
}
