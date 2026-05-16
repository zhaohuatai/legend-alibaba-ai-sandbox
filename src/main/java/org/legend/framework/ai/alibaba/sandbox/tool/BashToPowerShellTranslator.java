package org.legend.framework.ai.alibaba.sandbox.tool;

import java.util.*;
import java.util.regex.*;

/**
 * Bash 到 PowerShell 的命令翻译器，用于在 Windows 环境下将 Linux 命令转换为 PowerShell 语法。
 *
 * <p>该类是 Claude Code 运行时契约的跨平台兼容层实现，解决了 Windows 环境下 LLM 生成的 Bash 命令
 * 无法直接执行的问题。它通过正则表达式规则匹配常见的 Linux 命令，并将其转换为等效的 PowerShell 命令。
 *
 * <p>翻译规则包括：
 * <ul>
 *   <li><b>文件列表</b>：ls → Get-ChildItem</li>
 *   <li><b>文件读取</b>：cat → Get-Content, head → Get-Content -TotalCount, tail → Get-Content -Tail</li>
 *   <li><b>文件操作</b>：rm → Remove-Item, cp → Copy-Item, mv → Move-Item, mkdir → New-Item</li>
 *   <li><b>文本搜索</b>：grep → Select-String</li>
 *   <li><b>环境变量</b>：export VAR= → $env:VAR=, echo $VAR → echo $env:VAR</li>
 *   <li><b>进程管理</b>：ps → Get-Process, kill → Stop-Process</li>
 *   <li><b>其他</b>：pwd → Get-Location, which → Get-Command, touch → New-Item</li>
 * </ul>
 *
 * <p>翻译策略：
 * <ul>
 *   <li>如果命令看起来已经是 PowerShell 语法，则跳过翻译</li>
 *   <li>按顺序应用所有匹配的规则</li>
 *   <li>记录所有应用的规则，便于调试</li>
 *   <li>返回翻译结果和是否发生了修改的标志</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 翻译简单命令
 * Translation result = BashToPowerShellTranslator.translate("ls -la");
 * // result.command() = "Get-ChildItem -Force"
 * // result.modified() = true
 *
 * // 翻译带有参数的命令
 * Translation result = BashToPowerShellTranslator.translate("grep -r pattern /path");
 * // result.command() = "Select-String -Recurse pattern /path"
 *
 * // PowerShell 命令不会被翻译
 * Translation result = BashToPowerShellTranslator.translate("Get-ChildItem -Force");
 * // result.command() = "Get-ChildItem -Force"
 * // result.modified() = false
 * }</pre>
 *
 * @see org.legend.framework.ai.alibaba.sandbox.tool.core.ShellTool Bash 工具，使用此翻译器处理 Windows 命令
 */
public final class BashToPowerShellTranslator {

    /**
     * 翻译规则列表，按顺序存储所有 Bash 到 PowerShell 的转换规则。
     */
    private static final List<Rule> RULES = new ArrayList<>();

    /**
     * 翻译规则记录类，封装正则表达式匹配模式、替换字符串和规则说明。
     *
     * @param match 正则表达式模式，用于匹配 Bash 命令
     * @param replacement 替换字符串，PowerShell 等效命令
     * @param reason 规则说明，用于调试和记录应用的规则
     */
    public record Rule(Pattern match, String replacement, String reason) {}

    /**
     * 静态初始化块，注册所有翻译规则。
     *
     * <p>规则按顺序注册，确保常见的 Linux 命令都能被正确翻译。
     * 规则的顺序很重要，更具体的规则（如 ls -la）应该在更通用的规则（如 ls）之前。
     */
    static {
        /** 文件列表命令翻译 */
        add("\\bls\\s+-la?h?\\b",          "Get-ChildItem -Force",        "ls -la");
        add("\\bls\\s+-l\\b",              "Get-ChildItem",                "ls -l");
        add("\\bls\\b",                    "Get-ChildItem -Name",          "ls");
        add("\\bpwd\\b",                   "(Get-Location).Path",          "pwd");

        /** CMD dir 命令翻译 */
        add("\\bdir\\s+\"([^\"]+)\"\\s+/s\\s+/b", "Get-ChildItem -Path \"$1\" -Recurse -Name", "dir path /s /b");
        add("\\bdir\\s+\"([^\"]+)\"\\s+/b", "Get-ChildItem -Path \"$1\" -Name", "dir path /b");
        add("\\bdir\\s+\"([^\"]+)\"\\s+/s", "Get-ChildItem -Path \"$1\" -Recurse", "dir path /s");
        add("\\bdir\\s+\"([^\"]+)\"",      "Get-ChildItem -Path \"$1\"",   "dir path");
        add("\\bdir\\s+(/s\\s+)?(/b\\s+)?(.+)", "Get-ChildItem -Path $3", "dir with flags");

        /** CMD if exist 命令翻译 */
        add("\\bif\\s+exist\\s+\"([^\"]+)\"\\s+\\(echo\\s+(.+?)\\)\\s+else\\s+\\(echo\\s+(.+?)\\)", "if (Test-Path \"$1\") { Write-Output \"$2\" } else { Write-Output \"$3\" }", "if exist path (echo X) else (echo Y)");
        add("\\bif\\s+exist\\s+\"([^\"]+)\"", "Test-Path \"$1\"",         "if exist path");

        /** 特殊字符处理规则 - 将树形结构字符转换为安全字符串 */
        add("├──", "Write-Output '├── '", "tree char ├──");
        add("└──", "Write-Output '└── '", "tree char └──");
        add("│\\s{2}", "Write-Output '│   '", "tree char │");

        /** 特殊字符转义规则 - 防止 PowerShell 解析错误 */
        add("\\[FILE\\]", "'[FILE]'", "escape [FILE] brackets");
        add("\\[DIR\\]", "'[DIR]'", "escape [DIR] brackets");
        add("\\bB\\b(?=\\s*\\))", "'B'", "escape B in size output");
        add("<no extension>", "'<no extension>'", "escape <no extension> angle brackets");
        add("<([^>]+)>", "'''<$1>'''", "escape angle brackets in strings");

        /** 文件读取命令翻译 */
        add("\\bcat\\s+",                  "Get-Content ",                 "cat → Get-Content");
        add("\\btail\\s+-f\\s+",           "Get-Content -Wait -Tail 10 ",  "tail -f");
        add("\\btail\\s+-n\\s+(\\d+)\\s+", "Get-Content -Tail $1 ",        "tail -n N");
        add("\\bhead\\s+-n\\s+(\\d+)\\s+", "Get-Content -TotalCount $1 ",  "head -n N");
        add("\\btouch\\s+",                "New-Item -ItemType File -Force -Path ", "touch");

        /** 文件操作命令翻译 */
        add("\\brm\\s+-rf?\\s+",           "Remove-Item -Recurse -Force ", "rm -rf");
        add("\\brm\\s+-f\\s+",             "Remove-Item -Force ",          "rm -f");
        add("\\brm\\s+",                   "Remove-Item ",                 "rm");
        add("\\bcp\\s+-r\\s+",             "Copy-Item -Recurse ",          "cp -r");
        add("\\bcp\\s+",                   "Copy-Item ",                   "cp");
        add("\\bmv\\s+",                   "Move-Item ",                   "mv");
        add("\\bmkdir\\s+-p\\s+",          "New-Item -ItemType Directory -Force -Path ", "mkdir -p");
        add("\\bmkdir\\s+",                "New-Item -ItemType Directory -Path ", "mkdir");

        /** 文本搜索命令翻译 */
        add("\\bgrep\\s+-r\\b",            "Select-String -Recurse",       "grep -r");
        add("\\bgrep\\s+",                 "Select-String -Pattern ",      "grep");
        add("\\bwc\\s+-l\\s+",             "(Get-Content $1 | Measure-Object -Line).Lines ", "wc -l");
        add("\\bsed\\s+-i\\s+",            "# WARN: sed -i has no direct equivalent; use (Get-Content x) -replace ... | Set-Content x\nsed -i ", "sed -i");

        /** 环境变量命令翻译 */
        add("\\bexport\\s+([A-Z_]+)=",     "$env:$1=",                     "export VAR=");
        add("\\becho\\s+\\$([A-Z_]+)\\b",  "echo $env:$1",                 "echo $VAR");
        add("\\bwhich\\s+",                "Get-Command ",                 "which");

        /** 进程管理命令翻译 */
        add("\\bps\\s+aux\\b",             "Get-Process",                  "ps aux");
        add("\\bkill\\s+-9\\s+",           "Stop-Process -Force -Id ",     "kill -9");
        add("\\bkill\\s+",                 "Stop-Process -Id ",            "kill");
    }

    /**
     * 添加翻译规则到规则列表。
     *
     * @param regex 正则表达式模式字符串
     * @param repl 替换字符串，支持捕获组引用（如 $1）
     * @param reason 规则说明，用于调试
     */
    private static void add(String regex, String repl, String reason) {
        RULES.add(new Rule(Pattern.compile(regex), repl, reason));
    }

    /**
     * 翻译结果记录类，包含翻译后的命令、应用的规则列表和是否修改的标志。
     *
     * @param command 翻译后的命令字符串
     * @param appliedRules 应用的规则说明列表，用于调试
     * @param modified 是否发生了修改（true 表示命令被翻译，false 表示命令未被翻译）
     */
    public record Translation(String command, List<String> appliedRules, boolean modified) {}

    /**
     * 将 Bash 命令翻译为 PowerShell 语法。
     *
     * <p>该方法执行以下操作：
     * <ol>
     *   <li>检查命令是否已经是 PowerShell 语法，如果是则跳过翻译</li>
     *   <li>按顺序应用所有匹配的翻译规则</li>
     *   <li>记录所有应用的规则</li>
     *   <li>重写 && 操作符（当前未实现）</li>
     *   <li>返回翻译结果</li>
     * </ol>
     *
     * <p>翻译策略：
     * <ul>
     *   <li>如果命令包含 PowerShell 特征（如 Get-、Set-、$env: 等），则跳过翻译</li>
     *   <li>按规则列表顺序依次尝试匹配和替换</li>
     *   <li>每条规则最多应用一次（使用 Matcher.replaceAll）</li>
     * </ul>
     *
     * @param bash 要翻译的 Bash 命令字符串
     * @return 翻译结果记录，包含翻译后的命令、应用的规则列表和是否修改的标志
     */
    public static Translation translate(String bash) {
        /** 检查命令是否已经是 PowerShell 语法，如果是则跳过翻译 */
        if (looksLikePowerShell(bash)) {
            return new Translation(bash, List.of(), false);
        }

        /** 当前命令字符串（在翻译过程中逐步修改） */
        String cur = bash;
        /** 记录应用的规则 */
        List<String> applied = new ArrayList<>();
        /** 按顺序应用所有翻译规则 */
        for (Rule r : RULES) {
            Matcher m = r.match().matcher(cur);
            if (m.find()) {
                cur = m.replaceAll(r.replacement());
                applied.add(r.reason());
            }
        }
        /** 重写 && 操作符（当前未实现） */
        cur = rewriteAndOperator(cur);
        return new Translation(cur, applied, !cur.equals(bash));
    }

    /**
     * 检查命令是否看起来已经是 PowerShell 语法。
     *
     * <p>如果命令包含以下特征，则认为是 PowerShell 语法：
     * <ul>
     *   <li>包含 PowerShell 标准命令（如 Get-、Set-、New-、Remove- 等）</li>
     *   <li>包含环境变量语法（$env:）</li>
     *   <li>包含 PowerShell 表达式语法（(Get-）</li>
     * </ul>
     *
     * @param cmd 要检查的命令字符串
     * @return 如果看起来是 PowerShell 语法返回 true，否则返回 false
     */
    private static boolean looksLikePowerShell(String cmd) {
        return cmd.matches(".*\\b(Get|Set|New|Remove|Copy|Move|Stop|Start|Out|Select|Where|ForEach)-[A-Z][A-Za-z]+\\b.*")
            || cmd.contains("$env:")
            || cmd.contains("(Get-");
    }

    /**
     * 重写 && 操作符。
     *
     * <p>在 Bash 中，&& 表示前一个命令成功后才执行后一个命令。
     * 在 PowerShell 中，等效的语法是 ; 或使用 if ($LASTEXITCODE -eq 0) { ... }。
     * 当前实现暂未处理此操作符，直接返回原字符串。
     *
     * @param s 要重写的命令字符串
     * @return 重写后的命令字符串（当前返回原字符串）
     */
    private static String rewriteAndOperator(String s) {
        return s;
    }
}
