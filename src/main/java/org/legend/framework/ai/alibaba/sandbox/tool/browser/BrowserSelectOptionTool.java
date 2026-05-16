package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserSelectOptionTool implements Function<BrowserToolInputs.SelectOptionInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserSelectOptionTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.SelectOptionInput input) {
        try {
            var args = mapper.createObjectNode();
            args.put("element", input.element());
            args.put("ref", input.ref());
            var valuesArray = mapper.createArrayNode();
            for (String value : input.values()) {
                valuesArray.add(value);
            }
            args.set("values", valuesArray);
            return mcpClient.callTool("browser_select_option", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_select_option", new BrowserSelectOptionTool(mcpClient))
            .description("Select one or more options in a dropdown/select element.")
            .inputType(BrowserToolInputs.SelectOptionInput.class)
            .build();
    }
}
