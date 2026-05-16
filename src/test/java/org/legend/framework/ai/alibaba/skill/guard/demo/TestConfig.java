package org.legend.framework.ai.alibaba.skill.guard.demo;

/**
 * 测试配置常量。
 */
public final class TestConfig {
    
    /**
     * DashScope API Key，优先从环境变量读取。
     */
    public static final String API_KEY = System.getenv("DASHSCOPE_API_KEY") != null
        ? System.getenv("DASHSCOPE_API_KEY")
        : "sk-38ccc09116cb4720923d8a2fdaece02e";
    
    private TestConfig() {
    }
}
