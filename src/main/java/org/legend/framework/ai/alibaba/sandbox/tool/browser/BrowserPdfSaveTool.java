package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.Function;

public class BrowserPdfSaveTool implements Function<BrowserToolInputs.PdfSaveInput, String> {

    private final McpClientManager mcpClient;
    private final ObjectMapper mapper = new ObjectMapper();

    public BrowserPdfSaveTool(McpClientManager mcpClient) {
        this.mcpClient = mcpClient;
    }

    @Override
    public String apply(BrowserToolInputs.PdfSaveInput input) {
        try {
            var args = mapper.createObjectNode();
            if (input.filename() != null) args.put("filename", input.filename());
            return mcpClient.callTool("browser_pdf_save", args);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    public static ToolCallback create(McpClientManager mcpClient) {
        return FunctionToolCallback.builder("browser_pdf_save", new BrowserPdfSaveTool(mcpClient))
            .description("Save the current page as a PDF file.")
            .inputType(BrowserToolInputs.PdfSaveInput.class)
            .build();
    }
}
