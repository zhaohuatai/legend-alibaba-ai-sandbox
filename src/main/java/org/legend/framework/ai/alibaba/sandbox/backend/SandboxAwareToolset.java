package org.legend.framework.ai.alibaba.sandbox.backend;

import java.util.ArrayList;
import java.util.List;

import org.legend.framework.ai.alibaba.sandbox.GuardedSkillMetadata;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvContext;
import org.legend.framework.ai.alibaba.sandbox.tool.core.CreateDirectoryTool;
import org.legend.framework.ai.alibaba.sandbox.tool.core.EditTool;
import org.legend.framework.ai.alibaba.sandbox.tool.core.GetFileInfoTool;
import org.legend.framework.ai.alibaba.sandbox.tool.core.GetFilesInfoBatchTool;
import org.legend.framework.ai.alibaba.sandbox.tool.core.GlobTool;
import org.legend.framework.ai.alibaba.sandbox.tool.core.GrepTool;
import org.legend.framework.ai.alibaba.sandbox.tool.core.ListDirectoryRecursiveTool;
import org.legend.framework.ai.alibaba.sandbox.tool.core.ListDirectoryTool;
import org.legend.framework.ai.alibaba.sandbox.tool.core.MoveFileTool;
import org.legend.framework.ai.alibaba.sandbox.tool.core.ReadMultipleFilesTool;
import org.legend.framework.ai.alibaba.sandbox.tool.core.ReadTool;
import org.legend.framework.ai.alibaba.sandbox.tool.core.SearchFilesTool;
import org.legend.framework.ai.alibaba.sandbox.tool.core.ShellTool;
import org.legend.framework.ai.alibaba.sandbox.tool.core.WriteTool;
import org.legend.framework.ai.alibaba.sandbox.tool.python.PythonTool;
import org.springframework.ai.tool.ToolCallback;

public class SandboxAwareToolset {

	 public static List<ToolCallback> create(
		        SandboxBackend backend,
		        EnvContext env,
		        GuardedSkillMetadata skillMetadata
		    ) {
		        List<ToolCallback> tools = new ArrayList<>();

		        tools.add(ReadTool.create(backend));
		        tools.add(WriteTool.create(backend));
		        tools.add(EditTool.create(backend));
		        tools.add(GrepTool.create(backend));
		        tools.add(GlobTool.create(backend));
		        tools.add(ShellTool.create(backend, env));

		        tools.add(ReadMultipleFilesTool.create(backend));
		        tools.add(ListDirectoryTool.create(backend));
		        tools.add(ListDirectoryRecursiveTool.create(backend));
		        tools.add(CreateDirectoryTool.create(backend));
		        tools.add(MoveFileTool.create(backend));
		        tools.add(SearchFilesTool.create(backend));
		        tools.add(GetFileInfoTool.create(backend));
		        tools.add(GetFilesInfoBatchTool.create(backend));
		        tools.add(PythonTool.create(backend, env));

		        return List.copyOf(tools);
		    }
}
