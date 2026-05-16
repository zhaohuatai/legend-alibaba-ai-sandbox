package org.legend.framework.ai.alibaba.sandbox.skills.env;

import java.io.File;
import java.nio.file.Path;
import java.util.*;

import org.legend.framework.ai.alibaba.sandbox.skills.enums.OsKind;
import org.legend.framework.ai.alibaba.sandbox.skills.enums.ShellKind;

/**
 * 环境探测工具类，用于自动检测当前运行环境的操作系统、Shell 类型和可用可执行文件。
 *
 * <p>该类是 Claude Code 运行时契约的 OS 感知层实现，在 Agent 启动时调用一次，
 * 将探测结果注入到 LLM 的 system prompt 中，使模型能够根据实际操作系统
 * 生成正确的命令语法。
 *
 * <p>探测内容包括：
 * <ul>
 *   <li>操作系统类型：通过 {@code os.name} 系统属性判断</li>
 *   <li>Shell 类型：Windows 优先检测 PowerShell 7 (pwsh)，Linux/macOS 读取 $SHELL 环境变量</li>
 *   <li>可用可执行文件：遍历 PATH 环境变量，检测常用工具是否可用</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * // 探测当前工作目录的环境信息
 * EnvContext env = EnvProbe.probe(Paths.get(System.getProperty("user.dir")));
 *
 * // 探测指定工作目录的环境信息
 * EnvContext env = EnvProbe.probe(Paths.get("/tmp/skill-workspace"));
 * }</pre>
 *
 * @see EnvContext 环境上下文记录类，存储探测结果
 * @see SystemPromptInjector 将环境信息注入到 system prompt 的工具类
 */
public final class EnvProbe {

    /**
     * 私有构造函数，防止实例化。
     * 该类仅提供静态工具方法。
     */
    private EnvProbe() {}

    /**
     * 探测当前运行环境并返回环境上下文。
     *
     * <p>该方法执行以下探测步骤：
     * <ol>
     *   <li>调用 {@link #detectOs()} 检测操作系统类型</li>
     *   <li>调用 {@link #detectShell(OsKind)} 根据 OS 类型检测 Shell 类型</li>
     *   <li>调用 {@link #which(OsKind, List)} 检测常用可执行文件的可用性</li>
     *   <li>组装并返回 {@link EnvContext} 记录</li>
     * </ol>
     *
     * <p>检测的可执行文件列表包括：
     * python3, python,  node, git, rg (ripgrep), jq, curl, pwsh
     *
     * @param cwd 当前工作目录的绝对路径，将作为环境上下文的 cwd 字段
     * @return 包含完整环境信息的 {@link EnvContext} 记录
     */
    public static EnvContext probe(Path cwd) {
        // 步骤 1: 检测操作系统类型
        OsKind os = detectOs();

        // 步骤 2: 根据 OS 类型检测 Shell 类型
        var shell = detectShell(os);

        // 步骤 3: 检测常用可执行文件的可用性
        var avail = which(os, List.of(
            "python3", "python",  "node", "git", "rg", "jq", "curl", "pwsh"));

        // 步骤 4: 组装并返回环境上下文
        return new EnvContext(os, shell.kind(), shell.executable(), cwd, avail);
    }

    /**
     * 检测当前操作系统类型。
     *
     * <p>通过读取 {@code os.name} 系统属性并转换为小写后进行关键字匹配：
     * <ul>
     *   <li>包含 "win" → {@link EnvContext.OsKind#WINDOWS}</li>
     *   <li>包含 "mac" → {@link EnvContext.OsKind#MACOS}</li>
     *   <li>包含 "nux" 或 "nix" → {@link EnvContext.OsKind#LINUX}</li>
     *   <li>其他 → {@link EnvContext.OsKind#UNKNOWN}</li>
     * </ul>
     *
     * @return 检测到的操作系统类型枚举值
     */
    private static OsKind detectOs() {
        // 读取 os.name 系统属性，默认空字符串避免 NPE
        String n = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);

        // Windows 系统名称通常包含 "windows"
        if (n.contains("win")) return OsKind.WINDOWS;

        // macOS 系统名称通常包含 "mac"
        if (n.contains("mac")) return OsKind.MACOS;

        // Linux 系统名称通常包含 "linux" 或 "unix"
        if (n.contains("nux") || n.contains("nix")) return OsKind.LINUX;

        // 无法识别的系统名称回退到 UNKNOWN
        return OsKind.UNKNOWN;
    }

    /**
     * Shell 探测结果的内部记录类。
     *
     * @param kind       Shell 类型枚举值
     * @param executable Shell 可执行文件名称（如 "bash"、"pwsh"）
     */
    private record Shell(ShellKind kind, String executable) {}

    /**
     * 根据操作系统类型检测 Shell 类型。
     *
     * <p>检测逻辑：
     * <ul>
     *   <li><b>Windows</b>：优先检测 PowerShell 7+ (pwsh)，若不存在则回退到 Windows PowerShell 5.x (powershell)</li>
     *   <li><b>Linux/macOS</b>：读取 $SHELL 环境变量，若以 "zsh" 结尾则为 ZSH，否则默认为 BASH</li>
     * </ul>
     *
     * @param os 操作系统类型，用于决定检测策略
     * @return 包含 Shell 类型和可执行文件名称的 {@link Shell} 记录
     */
    private static Shell detectShell(OsKind os) {
        // Windows 系统：优先使用 PowerShell 7+ (pwsh)
        if (os == OsKind.WINDOWS) {
            // 检测 PowerShell 7+ 是否可用（跨平台版本）
            if (whichOne("pwsh") != null) { 
            	
            	return new Shell(ShellKind.POWERSHELL, "pwsh");
            
            }
            // 回退到 Windows PowerShell 5.x（系统内置）
            return new Shell(ShellKind.POWERSHELL, "powershell");
        }

        // Linux/macOS 系统：读取 $SHELL 环境变量
        String envShell = System.getenv("SHELL");

        // 如果 $SHELL 以 "zsh" 结尾，则为 ZSH
        if (envShell != null && envShell.endsWith("zsh")) return new Shell(ShellKind.ZSH, "zsh");

        // 默认使用 BASH
        return new Shell(ShellKind.BASH, "bash");
    }

    /**
     * 批量检测多个可执行文件的可用性。
     *
     * <p>遍历给定的可执行文件名称列表，对每个名称调用 {@link #whichOne(String)}
     * 进行检测，收集所有可用的可执行文件名称。
     *
     * @param os   操作系统类型（当前未使用，保留用于未来扩展）
     * @param bins 待检测的可执行文件名称列表
     * @return 可用的可执行文件名称列表（不可变列表）
     */
    private static List<String> which(OsKind os, List<String> bins) {
        List<String> found = new ArrayList<>();

        // 遍历每个可执行文件名称，检测是否可用
        for (String b : bins) {
            if (whichOne(b) != null) found.add(b);
        }

        // 返回不可变列表，防止外部修改
        return List.copyOf(found);
    }

    /**
     * 检测单个可执行文件是否在 PATH 中可用。
     *
     * <p>检测逻辑：
     * <ol>
     *   <li>读取 PATH 环境变量</li>
     *   <li>根据操作系统确定文件扩展名（Windows 需要检查 .exe/.cmd/.bat）</li>
     *   <li>遍历 PATH 中的每个目录，检查是否存在对应可执行文件</li>
     *   <li>使用 {@link File#canExecute()} 验证文件是否具有执行权限</li>
     * </ol>
     *
     * <p>Windows 扩展名检测顺序：.exe → .cmd → .bat → 无扩展名
     * Linux/macOS 仅检测无扩展名文件。
     *
     * @param bin 可执行文件名称（不含路径，如 "python3"、"git"）
     * @return 可执行文件的绝对路径，如果不可用则返回 null
     */
    private static String whichOne(String bin) {
        // 读取 PATH 环境变量
        String pathEnv = System.getenv("PATH");
        if (pathEnv == null) return null;

        // 根据操作系统确定需要检查的文件扩展名
        // Windows 需要检查 .exe, .cmd, .bat 以及无扩展名
        // Linux/macOS 仅检查无扩展名
        String[] exts = System.getProperty("os.name", "").toLowerCase().contains("win")
                ? new String[]{".exe", ".cmd", ".bat", ""} : new String[]{""};

        // 遍历 PATH 中的每个目录
        for (String dir : pathEnv.split(File.pathSeparator)) {
            // 对每个扩展名进行检查
            for (String ext : exts) {
                File f = new File(dir, bin + ext);

                // 检查文件是否存在且具有执行权限
                if (f.canExecute()) return f.getAbsolutePath();
            }
        }

        // 所有目录和扩展名都未找到可执行文件
        return null;
    }
}
