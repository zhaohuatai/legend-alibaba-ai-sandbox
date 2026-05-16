package org.legend.framework.ai.alibaba.sandbox.skills.enums;


/**
 * 风险等级枚举，定义 Skill 的安全风险级别。
 *
 * <p>风险等级用于决定 Skill 执行时使用的沙箱后端和隔离级别：
 * <ul>
 *   <li><b>LOW</b> - 低风险，使用本地进程沙箱，最小隔离</li>
 *   <li><b>MEDIUM</b> - 中等风险，使用本地进程沙箱或 Docker 容器</li>
 *   <li><b>HIGH</b> - 高风险，使用 Docker 容器沙箱，最大隔离</li>
 * </ul>
 *
 * <p>风险等级从 SKILL.md 的 YAML frontmatter 中的 {@code risk-level} 字段解析而来。
 * 如果字段缺失，默认使用 MEDIUM 级别。
 */
public enum RiskLevel {
    /** 低风险 Skill，仅使用只读工具（如 Read、Grep、Glob） */
    LOW,
    /** 中等风险 Skill，使用读写工具（如 Write、Edit） */
    MEDIUM,
    /** 高风险 Skill，使用命令执行工具（如 Bash）或网络访问 */
    HIGH;

    /**
     * 从对象解析风险等级。
     *
     * <p>该方法将对象转换为字符串，然后按以下规则解析：
     * <ul>
     *   <li>"low" → LOW</li>
     *   <li>"high" → HIGH</li>
     *   <li>其他值（包括 null）→ MEDIUM</li>
     * </ul>
     *
     * @param o 要解析的对象（通常为字符串）
     * @return 对应的风险等级枚举值
     */
    public static RiskLevel parse(Object o) {
        if (o == null) return MEDIUM;
        return switch (o.toString().toLowerCase()) {
            case "low" -> LOW;
            case "high" -> HIGH;
            default -> MEDIUM;
        };
    }
}
