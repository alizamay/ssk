package dev.niceone.smartsiteappserver.service;

import dev.niceone.smartsiteappserver.model.Site;
import dev.niceone.smartsiteappserver.model.SiteOnlineStatusLog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SiteDistributionService {
    
    @Value("${app.node.id:1}")
    private int nodeId;
    
    @Value("${app.node.total:8}")
    private int totalNodes;
    
    @Value("${app.distribution.strategy:hash_based}")
    private String distributionStrategy;
    
    @PostConstruct
    public void init() {
        log.info("Site Distribution Service initialized:");
        log.info("- Node ID: {}", nodeId);
        log.info("- Total Nodes: {}", totalNodes);
        log.info("- Distribution Strategy: {}", distributionStrategy);
    }
    
    /**
     * Filter sites assigned to this node
     */
    public List<Site> filterSitesForNode(List<Site> allSites) {
        if (totalNodes <= 1) {
            log.debug("Single node mode - processing all {} sites", allSites.size());
            return allSites;
        }
        
        List<Site> assignedSites = allSites.stream()
                .filter(this::isSiteAssignedToThisNode)
                .collect(Collectors.toList());
                
        log.info("Node {} assigned {} sites out of {} total sites", 
                nodeId, assignedSites.size(), allSites.size());
                
        return assignedSites;
    }
    
    /**
     * Filter site logs assigned to this node
     */
    public List<SiteOnlineStatusLog> filterLogsForNode(List<SiteOnlineStatusLog> allLogs) {
        if (totalNodes <= 1) {
            log.debug("Single node mode - processing all {} logs", allLogs.size());
            return allLogs;
        }
        
        List<SiteOnlineStatusLog> assignedLogs = allLogs.stream()
                .filter(this::isLogAssignedToThisNode)
                .collect(Collectors.toList());
                
        log.debug("Node {} assigned {} logs out of {} total logs", 
                nodeId, assignedLogs.size(), allLogs.size());
                
        return assignedLogs;
    }
    
    /**
     * Check if site is assigned to this node
     */
    public boolean isSiteAssignedToThisNode(Site site) {
        return isSiteAssignedToNode(site, nodeId);
    }
    
    /**
     * Check if site log is assigned to this node
     */
    public boolean isLogAssignedToThisNode(SiteOnlineStatusLog log) {
        return isLogAssignedToNode(log, nodeId);
    }
    
    /**
     * Check if site is assigned to specific node
     */
    public boolean isSiteAssignedToNode(Site site, int targetNodeId) {
        if (totalNodes <= 1) return true;
        
        switch (distributionStrategy) {
            case "hash_based":
                return getHashBasedNodeId(site.getCode()) == targetNodeId;
            case "round_robin":
                return getRoundRobinNodeId(site.getId()) == targetNodeId;
            case "modulo":
                return getModuloNodeId(site.getId()) == targetNodeId;
            default:
                log.warn("Unknown distribution strategy: {}, using hash_based", distributionStrategy);
                return getHashBasedNodeId(site.getCode()) == targetNodeId;
        }
    }
    
    /**
     * Check if site log is assigned to specific node
     */
    public boolean isLogAssignedToNode(SiteOnlineStatusLog statusLog, int targetNodeId) {
        if (totalNodes <= 1) return true;
        
        switch (distributionStrategy) {
            case "hash_based":
                return getHashBasedNodeId(statusLog.getSiteCode()) == targetNodeId;
            case "round_robin":
                return getRoundRobinNodeId(statusLog.getId()) == targetNodeId;
            case "modulo":
                return getModuloNodeId(statusLog.getId()) == targetNodeId;
            default:
                log.warn("Unknown distribution strategy: {}, using hash_based", distributionStrategy);
                return getHashBasedNodeId(statusLog.getSiteCode()) == targetNodeId;
        }
    }
    
    /**
     * Hash-based distribution (recommended for consistent assignment)
     */
    private int getHashBasedNodeId(String siteCode) {
        if (siteCode == null) return 1;
        
        int hash = Math.abs(siteCode.hashCode());
        int assignedNode = (hash % totalNodes) + 1;
        
        log.trace("Site {} hash {} assigned to node {}", siteCode, hash, assignedNode);
        return assignedNode;
    }
    
    /**
     * Round-robin distribution based on ID
     */
    private int getRoundRobinNodeId(Long id) {
        if (id == null) return 1;
        
        int assignedNode = (int)((id % totalNodes) + 1);
        log.trace("ID {} assigned to node {}", id, assignedNode);
        return assignedNode;
    }
    
    /**
     * Simple modulo distribution
     */
    private int getModuloNodeId(Long id) {
        if (id == null) return 1;
        
        int assignedNode = (int)((id % totalNodes) + 1);
        return assignedNode;
    }
    
    /**
     * Get distribution statistics
     */
    public String getDistributionStats(List<Site> allSites) {
        if (totalNodes <= 1) {
            return String.format("Single node mode: %d sites", allSites.size());
        }
        
        int[] nodeDistribution = new int[totalNodes + 1]; // index 0 unused
        
        for (Site site : allSites) {
            int assignedNode = 0;
            switch (distributionStrategy) {
                case "hash_based":
                    assignedNode = getHashBasedNodeId(site.getCode());
                    break;
                case "round_robin":
                case "modulo":
                    assignedNode = getModuloNodeId(site.getId());
                    break;
            }
            if (assignedNode > 0 && assignedNode <= totalNodes) {
                nodeDistribution[assignedNode]++;
            }
        }
        
        StringBuilder stats = new StringBuilder();
        stats.append(String.format("Distribution (%s): ", distributionStrategy));
        for (int i = 1; i <= totalNodes; i++) {
            stats.append(String.format("Node%d:%d ", i, nodeDistribution[i]));
        }
        
        return stats.toString();
    }
    
    // Getters
    public int getNodeId() { return nodeId; }
    public int getTotalNodes() { return totalNodes; }
    public String getDistributionStrategy() { return distributionStrategy; }
}