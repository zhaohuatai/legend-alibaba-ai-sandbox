package org.legend.framework.ai.alibaba.sandbox.tool.python;

import org.legend.framework.ai.alibaba.sandbox.backend.SandboxBackend;
import org.legend.framework.ai.alibaba.sandbox.skills.enums.OsKind;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvContext;
import org.legend.framework.ai.alibaba.sandbox.tool.ToolInputs;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import java.time.Duration;
import java.util.List;
import java.util.function.Function;

/**
 * Python 代码执行工具，用于在沙箱环境中执行 Python 代码。
 *
 * <p>该工具支持执行 Python 代码片段，适用于数据分析、数学计算等场景。
 * 代码在沙箱环境中执行，确保安全性。
 *
 * <p>跨平台兼容：
 * <ul>
 *   <li>Windows 上自动检测可用的 Python 解释器（py、python、python3）</li>
 *   <li>Linux/macOS 上使用 python3 或 python</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * ToolInputs.PythonInput input = new ToolInputs.PythonInput("print('Hello, World!')");
 * String result = pythonTool.apply(input);
 * }</pre>
 *
 * @see ToolInputs.PythonInput Python 工具的输入参数
 * @see SandboxBackend 沙箱后端接口
 * @see EnvContext 环境上下文
 */
public class PythonTool implements Function<ToolInputs.PythonInput, String> {

    /**
     * 沙箱后端实例，用于执行 Python 代码。
     */
    private final SandboxBackend backend;

    /**
     * 环境上下文记录，用于检测可用的 Python 解释器。
     */
    private final EnvContext env;

    /**
     * 创建 PythonTool 实例。
     *
     * @param backend 沙箱后端实例
     * @param env 环境上下文记录
     */
    public PythonTool(SandboxBackend backend, EnvContext env) {
        this.backend = backend;
        this.env = env;
    }

    /**
     * 执行 Python 代码并返回结果。
     *
     * @param in Python 代码输入参数
     * @return 执行结果（标准输出或错误信息）
     */
    @Override
    public String apply(ToolInputs.PythonInput in) {
        String pythonCmd = resolvePython();
        if (pythonCmd == null) {
            return "Error: No Python interpreter found. Please ensure python3, python, or py is installed.";
        }

        var result = backend.execPython(in.code(), Duration.ofMinutes(2), pythonCmd);

        if (result.ok()) {
            return result.stdout().isEmpty() ? "Python code executed successfully." : result.stdout();
        }
        return "Error (exit code " + result.exitCode() + "):\n" + result.stderr();
    }

    /**
     * 根据环境上下文解析可用的 Python 解释器。
     *
     * @return Python 解释器名称，如 "python3"、"python"、"py"，如果未找到返回 null
     */
    private String resolvePython() {
        if (env.availableExecutables() != null) {
            for (String candidate : List.of("python3", "python", "py")) {
                if (env.availableExecutables().contains(candidate)) {
                    return candidate;
                }
            }
        }
        return env.os() == OsKind.WINDOWS ? "py" : "python3";
    }

    /**
     * 创建 Python 工具的 Spring AI ToolCallback 实例。
     *
     * @param backend 沙箱后端实例
     * @param env 环境上下文记录
     * @return Spring AI ToolCallback 实例
     */
    public static ToolCallback create(SandboxBackend backend, EnvContext env) {
        return FunctionToolCallback.builder("python", new PythonTool(backend, env))
            .description("""
                Executes Python code in a sandboxed environment.
                Useful for data analysis, mathematical computations, and scripting.
                Returns stdout output or error message.
                """)
            .inputType(ToolInputs.PythonInput.class)
            .build();
    }
}
