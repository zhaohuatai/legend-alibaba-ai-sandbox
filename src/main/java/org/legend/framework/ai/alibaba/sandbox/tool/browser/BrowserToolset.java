package org.legend.framework.ai.alibaba.sandbox.tool.browser;

import org.legend.framework.ai.alibaba.sandbox.tool.mcp.McpClientManager;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.List;

/**
 * 浏览器工具集工厂，创建基于 MCP 协议的浏览器自动化工具实例。
 *
 * <p>该类对标 AgentScope 的 Browser 工具集，通过 MCP 协议与浏览器沙箱通信，
 * 提供完整的浏览器自动化能力。
 *
 * <p>核心设计理念：
 * <ul>
 *   <li><b>MCP 协议通信</b> - 通过 Model Context Protocol 与 Playwright MCP 服务器交互</li>
 *   <li><b>独立沙箱隔离</b> - 浏览器工具运行在独立的 Browser Sandbox 容器中</li>
 *   <li><b>按需启用</b> - 仅在需要浏览器功能时创建这些工具</li>
 * </ul>
 *
 * <p>创建的工具（对标 AgentScope）：
 * <ol>
 *   <li><b>browser_navigate</b> - 导航到指定 URL</li>
 *   <li><b>browser_click</b> - 点击页面元素</li>
 *   <li><b>browser_type</b> - 在输入框中输入文本</li>
 *   <li><b>browser_screenshot</b> - 截取页面截图</li>
 *   <li><b>browser_snapshot</b> - 获取页面可访问性快照</li>
 *   <li><b>browser_tab_new</b> - 打开新标签页</li>
 *   <li><b>browser_tab_select</b> - 选择指定标签页</li>
 *   <li><b>browser_tab_close</b> - 关闭指定标签页</li>
 *   <li><b>browser_tab_list</b> - 列出所有标签页</li>
 *   <li><b>browser_wait_for</b> - 等待指定时间或条件</li>
 *   <li><b>browser_resize</b> - 调整浏览器窗口大小</li>
 *   <li><b>browser_close</b> - 关闭浏览器</li>
 *   <li><b>browser_console_messages</b> - 获取控制台消息</li>
 *   <li><b>browser_handle_dialog</b> - 处理对话框</li>
 *   <li><b>browser_file_upload</b> - 上传文件</li>
 *   <li><b>browser_press_key</b> - 按键操作</li>
 *   <li><b>browser_navigate_back</b> - 后退导航</li>
 *   <li><b>browser_navigate_forward</b> - 前进导航</li>
 *   <li><b>browser_network_requests</b> - 获取网络请求</li>
 *   <li><b>browser_pdf_save</b> - 保存为 PDF</li>
 *   <li><b>browser_drag</b> - 拖拽操作</li>
 *   <li><b>browser_hover</b> - 悬停操作</li>
 *   <li><b>browser_select_option</b> - 选择下拉选项</li>
 * </ol>
 *
 * <p>使用示例：
 * <pre>{@code
 * McpClientManager mcpClient = new McpClientManager("ws://localhost:3000");
 * List<ToolCallback> browserTools = BrowserToolset.create(mcpClient);
 * }</pre>
 *
 * @see McpClientManager MCP 客户端管理器
 */
public final class BrowserToolset {

    private BrowserToolset() {}

    /**
     * 创建完整的浏览器工具集。
     *
     * <p>该方法创建 22 个浏览器自动化工具回调实例，涵盖导航、交互、截图、
     * 标签页管理等完整浏览器操作能力。
     *
     * @param mcpClient MCP 客户端管理器，用于与浏览器沙箱通信
     * @return 包含 22 个浏览器工具的不可变列表
     */
    public static List<ToolCallback> create(McpClientManager mcpClient) {
        List<ToolCallback> tools = new ArrayList<>();

        tools.add(BrowserNavigateTool.create(mcpClient));
        tools.add(BrowserClickTool.create(mcpClient));
        tools.add(BrowserTypeTool.create(mcpClient));
        tools.add(BrowserScreenshotTool.create(mcpClient));
        tools.add(BrowserSnapshotTool.create(mcpClient));
        tools.add(BrowserTabNewTool.create(mcpClient));
        tools.add(BrowserTabSelectTool.create(mcpClient));
        tools.add(BrowserTabCloseTool.create(mcpClient));
        tools.add(BrowserTabListTool.create(mcpClient));
        tools.add(BrowserWaitForTool.create(mcpClient));
        tools.add(BrowserResizeTool.create(mcpClient));
        tools.add(BrowserCloseTool.create(mcpClient));
        tools.add(BrowserConsoleMessagesTool.create(mcpClient));
        tools.add(BrowserHandleDialogTool.create(mcpClient));
        tools.add(BrowserFileUploadTool.create(mcpClient));
        tools.add(BrowserPressKeyTool.create(mcpClient));
        tools.add(BrowserNavigateBackTool.create(mcpClient));
        tools.add(BrowserNavigateForwardTool.create(mcpClient));
        tools.add(BrowserNetworkRequestsTool.create(mcpClient));
        tools.add(BrowserPdfSaveTool.create(mcpClient));
        tools.add(BrowserDragTool.create(mcpClient));
        tools.add(BrowserHoverTool.create(mcpClient));
        tools.add(BrowserSelectOptionTool.create(mcpClient));

        return List.copyOf(tools);
    }
}
