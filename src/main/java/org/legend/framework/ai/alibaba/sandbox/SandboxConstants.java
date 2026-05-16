package org.legend.framework.ai.alibaba.sandbox;

/**
 * 沙箱相关常量。
 */
public final class SandboxConstants {
	  /**
     * 私有构造函数，防止实例化。
     */
    private SandboxConstants() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    
    /**
     * OverAllState 中存储 invokeId 的 key。
     */
    public static final String INVOKE_ID_KEY = "_SANDBOX_INVOKE_ID_";
    
    /**
     * OverAllState 中存储 sessionKey 的 key（保留兼容）。
     */
    public static final String AGENT_SESSION_KEY = "_SANDBOX_SESSION_KEY_";
    
    /**
     * 环境信息模板片段。
     * 
     * <p>该片段包含操作系统、Shell 类型和可用可执行文件的信息，
     * 用于帮助 LLM 生成适合当前环境的命令。
     */
    public static final String ENVIRONMENT_INFO_TEMPLATE = """
        Environment Information:
        - Operating System: {os}
        - Shell Type: {shell}
        - Available Executables: {executables}
        """;

    /**
     * 默认系统提示模板。
     * 
     * <p>该模板包含以下部分：
     * <ol>
     *   <li>Skills 能力说明</li>
     *   <li>环境信息（操作系统、Shell、可用工具）</li>
     *   <li>可用 Skills 列表</li>
     *   <li>Skill 使用说明</li>
     *   <li>重要注意事项</li>
     * </ol>
     * 
     * <p>使用说明：
     * <pre>{@code
     * String rendered = SkillSystemPromptConstants.DEFAULT_SYSTEM_PROMPT_TEMPLATE
     *     .replace("{os}", osName)
     *     .replace("{shell}", shellName)
     *     .replace("{executables}", executables)
     *     .replace("{skills_list}", skillsList);
     * }</pre>
     */
    public static final String DEFAULT_SYSTEM_PROMPT_TEMPLATE = """
        You have access to a set of Skills that extend your capabilities.
        
        Environment Information:
        - Operating System: {os}
        - Shell Type: {shell}
        - Available Executables: {executables}
        
        Available Skills:
        {skills_list}
        
        How to use Skills:
        - Skills are NOT tools. You cannot call them directly.
        - When a user's request matches a Skill's description, use the read_skill tool with the exact skill_name shown above.
        - After reading the Skill, follow its instructions and use the tools it allows.
        
        Important:
        - Only use skill names that are listed in the Available Skills section above.
        - Only use skill names as the skill_name parameter of the read_skill tool.
        - When using Bash tool, generate commands appropriate for {shell} on {os}.
        - Prefer simple commands (like Get-ChildItem, dir) over complex inline scripts.
        - DO NOT use colons (:) in ForEach-Object string output (causes PowerShell parsing errors)
        """;

    /**
     * 框架原始系统提示模板（来自 FileSystemSkillRegistry）。
     * 
     * <p>该模板包含以下部分：
     * <ol>
     *   <li>Skills 系统说明</li>
     *   <li>可用 Skills 列表</li>
     *   <li>渐进式披露使用说明</li>
     *   <li>Skill 加载指南</li>
     *   <li>重要注意事项</li>
     *   <li>示例工作流</li>
     * </ol>
     * 
     * <p>模板中的占位符：
     * <ul>
     *   <li>{skills_list} - 可用 Skill 列表</li>
     *   <li>{skills_load_instructions} - Skill 加载指南</li>
     * </ul>
     */
    public static final String FRAME_DEFAULT_SYSTEM_PROMPT_TEMPLATE = """
			
			## Skills System
			
			You have access to a skills library that provides specialized capabilities and domain knowledge. All skills are stored in a Skill Registry with a file system based storage.
			
			### Available Skills
			
			{skills_list}
			
			### How to Use Skills (Progressive Disclosure)
			
			Skills follow a **progressive disclosure** pattern - you know they exist (name + description above), but you only read the full instructions when needed:
			
			1. **Recognize when a skill applies**: Check if the user's task matches any skill's description
			2. **Read the skill's full instructions**: The skill list above shows the exact skill id to use with `read_skill`
			3. **Follow the skill's instructions**: SKILL.md contains step-by-step workflows, best practices, and examples
			4. **Access supporting files**: Skills may include Python scripts, configs, or reference docs - use absolute paths
			
			#### How to Read The Full Skill Instruction
			
			You are currently using the file system based Skill Registry. Please follow the skill loading guidelines below:
			
			{skills_load_instructions}
			
			**Important:**
			
			  - **For SKILL.md files (skill instructions)**: Always use `read_skill` to read skill instructions. Do not attempt to access SKILL.md files through other methods.
			  - **For other supporting files that skill uses (scripts, references, etc.)**: You may use other appropriate tools to read or access these files as needed, always use absolute paths from the skill list.
			
			#### When to Use Skills
			
			  - When the user's request matches a skill's domain (e.g., "research X" → web-research skill)
			  - When you need specialized knowledge or structured workflows
			  - When a skill provides proven patterns for complex tasks
			
			#### Skills are Self-Documenting
			
			  - Each SKILL.md tells you exactly what the skill does and how to use it
			  - The skill list above shows the full path for each skill's SKILL.md file
			
			#### Executing Skill Scripts
			
			Skills may contain Python scripts or other executable files. Always use absolute paths from the skill list.
			
			### Example Workflow
			
			User: "Can you research the latest developments in quantum computing?"
			
			1. Check available skills above → See "web-research" skill with its skill id
			2. Read the skill using the id shown in the list
			3. Follow the skill's research workflow (search → organize → synthesize)
			4. Use any helper scripts with absolute paths
			
			Remember: Skills are tools to make you more capable and consistent. When in doubt, check if a skill exists for the task!
			""";

    /**
     * 拼接环境信息后的框架系统提示模板。
     * 
     * <p>该模板在 FRAME_DEFAULT_SYSTEM_PROMPT_TEMPLATE 基础上，
     * 在 "## Skills System" 之后、"### Available Skills" 之前插入了环境信息。
     * 
     * <p>模板中的占位符：
     * <ul>
     *   <li>{os} - 操作系统名称（如 WINDOWS、LINUX）</li>
     *   <li>{shell} - Shell 类型（如 POWERSHELL、BASH）</li>
     *   <li>{executables} - 可用可执行文件列表（如 python, node, git）</li>
     *   <li>{skills_list} - 可用 Skill 列表</li>
     *   <li>{skills_load_instructions} - Skill 加载指南</li>
     * </ul>
     * 
     * <p>使用说明：
     * <pre>{@code
     * String rendered = SkillSystemPromptConstants.FRAME_SYSTEM_PROMPT_WITH_ENV_TEMPLATE
     *     .replace("{os}", osName)
     *     .replace("{shell}", shellName)
     *     .replace("{executables}", executables)
     *     .replace("{skills_list}", skillsList)
     *     .replace("{skills_load_instructions}", loadInstructions);
     * }</pre>
     */
    public static final String FRAME_SYSTEM_PROMPT_WITH_ENV_TEMPLATE = """
			
			## Skills System
			
			You have access to a skills library that provides specialized capabilities and domain knowledge. All skills are stored in a Skill Registry with a file system based storage.
			
			Environment Information:
			- Operating System: {os}
			- Shell Type: {shell}
			- Available Executables: {executables}
			
			### Available Skills
			
			{skills_list}
			
			### How to Use Skills (Progressive Disclosure)
			
			Skills follow a **progressive disclosure** pattern - you know they exist (name + description above), but you only read the full instructions when needed:
			
			1. **Recognize when a skill applies**: Check if the user's task matches any skill's description
			2. **Read the skill's full instructions**: The skill list above shows the exact skill id to use with `read_skill`
			3. **Follow the skill's instructions**: SKILL.md contains step-by-step workflows, best practices, and examples
			4. **Access supporting files**: Skills may include Python scripts, configs, or reference docs - use absolute paths
			
			#### How to Read The Full Skill Instruction
			
			You are currently using the file system based Skill Registry. Please follow the skill loading guidelines below:
			
			{skills_load_instructions}
			
			**Important:**
			
			  - **For SKILL.md files (skill instructions)**: Always use `read_skill` to read skill instructions. Do not attempt to access SKILL.md files through other methods.
			  - **For other supporting files that skill uses (scripts, references, etc.)**: You may use other appropriate tools to read or access these files as needed, always use absolute paths from the skill list.
			
			#### When to Use Skills
			
			  - When the user's request matches a skill's domain (e.g., "research X" → web-research skill)
			  - When you need specialized knowledge or structured workflows
			  - When a skill provides proven patterns for complex tasks
			
			#### Skills are Self-Documenting
			
			  - Each SKILL.md tells you exactly what the skill does and how to use it
			  - The skill list above shows the full path for each skill's SKILL.md file
			
			#### Executing Skill Scripts
			
			Skills may contain Python scripts or other executable files. Always use absolute paths from the skill list.
			
			### Example Workflow
			
			User: "Can you research the latest developments in quantum computing?"
			
			1. Check available skills above → See "web-research" skill with its skill id
			2. Read the skill using the id shown in the list
			3. Follow the skill's research workflow (search → organize → synthesize)
			4. Use any helper scripts with absolute paths
			
			Remember: Skills are tools to make you more capable and consistent. When in doubt, check if a skill exists for the task!
			""";

  
}
