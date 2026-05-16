package org.legend.framework.ai.alibaba.skill.guard.demo;

import java.nio.file.Paths;

import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvContext;
import org.legend.framework.ai.alibaba.sandbox.skills.env.EnvProbe;

/**
 * 环境信息输出测试程序。
 */
public class EnvInfoTest {

    public static void main(String[] args) {
        EnvContext env = EnvProbe.probe(Paths.get(System.getProperty("user.dir")));

        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("   当前环境信息");
        System.out.println("═══════════════════════════════════════════════════\n");

        System.out.println("📋 操作系统: " + env.os());
        System.out.println("📋 Shell 类型: " + env.shellKind());
        System.out.println("📋 Shell 可执行文件: " + env.shellExecutable());
        System.out.println("📋 工作目录: " + env.cwd());
        System.out.println("📋 可用工具: " + env.availableExecutables());
        System.out.println();

        boolean isDocker = System.getenv("CONTAINER") != null 
            || System.getenv("DOCKER_CONTAINER") != null
            || new java.io.File("/.dockerenv").exists();
        
        System.out.println("📋 运行环境: " + (isDocker ? "Docker 容器" : "宿主机"));
        System.out.println();

        System.out.println("═══════════════════════════════════════════════════");
    }
}
