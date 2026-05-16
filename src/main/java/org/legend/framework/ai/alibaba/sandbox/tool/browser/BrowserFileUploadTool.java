package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserFileUploadTool implements Function<BrowserToolInputs.FileUploadInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserFileUploadTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.FileUploadInput input) {
        try {
            var args = mapper.createObjectNode();
            var pathsArray = mapper.createArrayNode();
            for (String path : input.paths()) {
                pathsArray.add(path);
            }
            args.set("paths", pathsArray);
            return mcpClient.callTool("browser_file_upload", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_file_upload", new BrowserFileUploadTool(mcpClient))
            .description("Upload files to a file input element on the page.")
            .inputType(BrowserToolInputs.FileUploadInput.class)
            .build();
    }
}
