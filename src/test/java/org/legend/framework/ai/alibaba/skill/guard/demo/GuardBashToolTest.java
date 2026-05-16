package org.legend.framework.ai.alibaba.skill.guard.demo;

import java.nio.file.Paths;

import org.legend.framework.ai.alibaba.sandbox.backend.local.LocalProcessSandbox;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvContext;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvProbe;
import org.legend.framework.ai.alibaba.sandbox.tool.ToolInputs;
import org.legend.framework.ai.alibaba.sandbox.tool.core.ShellTool;

/**
 * Bash 工具测试，验证 PowerShell 命令执行和结果获取。
 */
public class GuardBashToolTest {

    public static void main(String[] args) {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("   Bash Tool Execution Test");
        System.out.println("═══════════════════════════════════════════════════\n");

        EnvContext env = EnvProbe.probe(Paths.get(System.getProperty("user.dir")));
        System.out.println("📋 环境信息:");
        System.out.println("   操作系统: " + env.os());
        System.out.println("   Shell: " + env.shellKind());
        System.out.println("   工作目录: " + env.cwd());
        System.out.println();

        try (LocalProcessSandbox sandbox = new LocalProcessSandbox(env)) {
            System.out.println("🔧 沙箱工作区: " + sandbox.workspaceRoot());
            System.out.println();

            ShellTool bashTool = new ShellTool(sandbox, env);

            // 测试 1：简单命令
            System.out.println("📝 测试 1: 简单 PowerShell 命令");
            System.out.println("   命令: Get-Location");
            String result1 = bashTool.apply(new ToolInputs.BashInput(
                "Get-Location", 
                10000L, 
                "Get current directory"
            ));
            System.out.println("   结果: " + result1.replace("\n", "\n   "));
            System.out.println();

            // 测试 2：列出文件
            System.out.println("📝 测试 2: 列出当前目录文件");
            System.out.println("   命令: Get-ChildItem -Name");
            String result2 = bashTool.apply(new ToolInputs.BashInput(
                "Get-ChildItem -Name", 
                10000L, 
                "List files"
            ));
            System.out.println("   结果: " + (result2.length() > 500 ? result2.substring(0, 500) + "..." : result2.replace("\n", "\n   ")));
            System.out.println();

            // 测试 3：Bash 命令翻译
            System.out.println("📝 测试 3: Bash 命令翻译 (ls)");
            System.out.println("   命令: ls");
            String result3 = bashTool.apply(new ToolInputs.BashInput(
                "ls", 
                10000L, 
                "List files using ls"
            ));
            System.out.println("   结果: " + (result3.length() > 500 ? result3.substring(0, 500) + "..." : result3.replace("\n", "\n   ")));
            System.out.println();

            // 测试 4：带管道的命令
            System.out.println("📝 测试 4: 带管道的命令");
            System.out.println("   命令: Get-ChildItem | Select-Object -First 5 Name");
            String result4 = bashTool.apply(new ToolInputs.BashInput(
                "Get-ChildItem | Select-Object -First 5 Name", 
                10000L, 
                "List first 5 files"
            ));
            System.out.println("   结果: " + result4.replace("\n", "\n   "));
            System.out.println();

            // 测试 5：JSON 输出
            System.out.println("📝 测试 5: JSON 输出");
            System.out.println("   命令: Get-ChildItem -Name | ConvertTo-Json");
            String result5 = bashTool.apply(new ToolInputs.BashInput(
                "Get-ChildItem -Name | ConvertTo-Json", 
                10000L, 
                "List files as JSON"
            ));
            System.out.println("   结果: " + (result5.length() > 500 ? result5.substring(0, 500) + "..." : result5.replace("\n", "\n   ")));
            System.out.println();

            // 测试 6：检查文件是否存在
            System.out.println("📝 测试 6: 检查文件是否存在");
            String testPath = "E:\\worksapce\\sts5.1\\legend-smartmind\\legend-smartmind\\src\\main\\resources\\skills\\data-analysis\\sample_data.csv";
            System.out.println("   命令: Test-Path '" + testPath + "'");
            String result6 = bashTool.apply(new ToolInputs.BashInput(
                "Test-Path '" + testPath + "'", 
                10000L, 
                "Check if file exists"
            ));
            System.out.println("   结果: " + result6.replace("\n", "\n   "));
            System.out.println();

            // 测试 7：读取文件内容
            System.out.println("📝 测试 7: 读取文件内容");
            System.out.println("   命令: Get-Content '" + testPath + "'");
            String result7 = bashTool.apply(new ToolInputs.BashInput(
                "Get-Content '" + testPath + "'", 
                10000L, 
                "Read CSV file"
            ));
            System.out.println("   结果: " + (result7.length() > 500 ? result7.substring(0, 500) + "..." : result7.replace("\n", "\n   ")));
            System.out.println();

        } catch (Exception e) {
            System.err.println("❌ 测试失败: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("\n✅ 测试完成！");
    }
}
