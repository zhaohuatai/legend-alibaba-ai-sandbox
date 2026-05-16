package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserDragTool implements Function<BrowserToolInputs.DragInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserDragTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.DragInput input) {
        try {
            var args = mapper.createObjectNode();
            args.put("startElement", input.startElement());
            args.put("startRef", input.startRef());
            args.put("endElement", input.endElement());
            args.put("endRef", input.endRef());
            return mcpClient.callTool("browser_drag", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_drag", new BrowserDragTool(mcpClient))
            .description("Drag an element from one position to another on the page.")
            .inputType(BrowserToolInputs.DragInput.class)
            .build();
    }
}
