package org.legend.framework.ai.alibaba.sandbox.backend;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Supplier;

/**
 * 沙箱会话管理器，负责管理所有沙箱租约。
 * 
 * <p>数据结构：
 * <pre>
 * ConcurrentMap&lt;String, Map&lt;String, BackendLease&gt;&gt;
 *   ├─ invokeId (agent 调用标识)
 *   │    └─ skillName (skill 名称)
 *   │         └─ BackendLease (租约)
 * </pre>
 * 
 * <p>核心职责：
 * <ol>
 *   <li>管理 invokeId + skillName 到租约的映射</li>
 *   <li>提供按 invokeId + skillName 获取/创建沙箱租约的能力</li>
 *   <li>在 afterAgent 中清理指定 invokeId 的所有容器</li>
 *   <li>定时清理超时的租约（兜底机制）</li>
 * </ol>
 */
public class SandboxSessionManager {
    
    private static final Logger logger = LoggerFactory.getLogger(SandboxSessionManager.class);
    
    /**
     * 所有活跃的租约，两层嵌套结构：
     * - 第一层 key: invokeId
     * - 第二层 key: skillName
     * - value: BackendLease
     */
    private final ConcurrentMap<String, Map<String, BackendLease>> sessionSets;
    
    /**
     * 记录每个 invokeId 的创建时间，用于超时清理。
     */
    private final ConcurrentMap<String, Instant> invokeIdCreatedAt;
    
    public SandboxSessionManager() {
        this.sessionSets = new ConcurrentHashMap<>();
        this.invokeIdCreatedAt = new ConcurrentHashMap<>();
    }
    
    /**
     * 获取或创建指定 skill 的沙箱租约。
     * 
     * @param invokeId agent 调用标识
     * @param skillName skill 名称
     * @param creator 租约创建器（仅在不存在时调用）
     * @return 沙箱租约
     */
    public BackendLease getOrCreateLease(String invokeId, String skillName, Supplier<BackendLease> creator) {
        	
    	   Map<String, BackendLease> skillMap = sessionSets.computeIfAbsent(
                invokeId, k -> new ConcurrentHashMap<>()
            );
    	   BackendLease lease = skillMap.computeIfAbsent(skillName, k -> {
    	        try {
    	            return creator.get();
    	        } catch (Exception e) {
    	            skillMap.remove(skillName);  // 清理残留
    	            throw e;
    	        }
    	    });
            // 创建成功后才记录时间
            invokeIdCreatedAt.putIfAbsent(invokeId, Instant.now());
            
            logger.info("Lease created: invokeId={}, skillName={}", invokeId, skillName);
            return lease;
    }
    
    /**
     * 清理指定 invokeId 的所有沙箱租约。
     * 
     * <p>该方法在 afterAgent 中调用，确保单次 invoke 期间创建的所有容器都被清理。
     */
    public void cleanupByInvokeId(String invokeId) {
        Map<String, BackendLease> skillMap = sessionSets.remove(invokeId);
        invokeIdCreatedAt.remove(invokeId);
        
        if (skillMap != null) {
            int count = 0;
            List<String> closedSkills = new ArrayList<>();
            for (Map.Entry<String, BackendLease> entry : skillMap.entrySet()) {
                try {
                    entry.getValue().close();
                    closedSkills.add(entry.getKey());
                    count++;
                } catch (Exception e) {
                    logger.error("Failed to close lease: invokeId={}, skillName={}, error={}", 
                        invokeId, entry.getKey(), e.getMessage());
                }
            }
            logger.info("Cleaned up {} lease(s) for invokeId: {}, skills: {}", 
                count, invokeId, closedSkills);
        }
    }
    
    /**
     * 定时清理超时的租约（兜底机制）。
     * 
     * <p>如果 afterAgent 由于异常没有被调用，该方法可以清理泄漏的容器。
     */
    public void cleanupTimeoutSessions(Duration timeout) {
        Instant cutoff = Instant.now().minus(timeout);
        
    // 先收集超时的 invokeId，避免在遍历期间修改
        List<String> timeoutInvokeIds = new ArrayList<>();
        for (Map.Entry<String, Instant> entry : invokeIdCreatedAt.entrySet()) {
            if (entry.getValue().isBefore(cutoff)) {
                timeoutInvokeIds.add(entry.getKey());
            }
        }
        
        // 再统一清理
        for (String invokeId : timeoutInvokeIds) {
            Instant createdAt = invokeIdCreatedAt.get(invokeId);
            if (createdAt != null) {
                logger.warn("Cleaning up timeout invokeId: {}, age={}s", 
                    invokeId, Duration.between(createdAt, Instant.now()).getSeconds());
            }
            cleanupByInvokeId(invokeId);
        }

    }
    
    /**
     * 获取所有活跃的 invokeId。
     */
    public Set<String> getActiveInvokeIds() {
        return sessionSets.keySet();
    }
    
    /**
     * 获取活跃的 invokeId 数量。
     */
    public int getActiveSessionSetCount() {
        return sessionSets.size();
    }
}
