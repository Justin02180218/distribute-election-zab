package com.justin.distribute.election.zab;

import com.justin.distribute.election.zab.common.PropertiesUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * WX: coding到灯火阑珊
 * @author Justin
 */
public class NodeConfig {
    private final Map<Integer, String> nodeMap = new HashMap<>();
    private final Map<Integer, String> nodeMgrMap = new HashMap<>();

    private int nodeId;
    private String host;
    private int port;
    private int nodeMgrPort;

    private int cup = Runtime.getRuntime().availableProcessors();
    private int maxPoolSize = cup * 2;
    private int queueSize = 1024;
    private long keepTime = 60 * 1000;

    private int electionTimeout = 15 * 1000;
    private int heartbeatTimeout = 5 * 1000;

    private volatile long preHeartbeatTime = 0;
    private volatile long preElectionTime = 0;

    private NodeConfig() {
        this.nodeId = PropertiesUtil.getNodeId();

        Map<Integer, String> map = PropertiesUtil.getNodesAddress();
        for (Map.Entry<Integer, String> entry : map.entrySet()) {
            Integer id = entry.getKey();
            String[] addrs = entry.getValue().split(":");
//            nodeMap.put(id, addrs[0]+":"+addrs[2]);
            nodeMgrMap.put(id, addrs[0]+":"+addrs[1]);

            if (nodeId == entry.getKey()) {
                this.host = addrs[0];
                this.port = Integer.parseInt(addrs[2]);
                this.nodeMgrPort = Integer.parseInt(addrs[1]);
            }
        }

        nodeMap.put(nodeId, host+":"+port);
    }

    private static class NodeConfigSingle {
        private static final NodeConfig INSTANCE = new NodeConfig();
    }

    public static NodeConfig getInstance() {
        return NodeConfigSingle.INSTANCE;
    }

    public boolean resetElectionTick() {
        long current = System.currentTimeMillis();
        if (current - preElectionTime < electionTimeout) {
            return false;
        }
        preElectionTime = System.currentTimeMillis() + ThreadLocalRandom.current().nextInt(200) + 150;
        return true;
    }

    public boolean resetHeartbeatTick() {
        long current = System.currentTimeMillis();
        if (current - preHeartbeatTime < heartbeatTimeout) {
            return false;
        }
        preHeartbeatTime = System.currentTimeMillis();
        return true;
    }

    public int getNodeId() {
        return nodeId;
    }

    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getNodeMgrPort() {
        return nodeMgrPort;
    }

    public void setNodeMgrPort(int nodeMgrPort) {
        this.nodeMgrPort = nodeMgrPort;
    }

    public int getCup() {
        return cup;
    }

    public void setCup(int cup) {
        this.cup = cup;
    }

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
        this.maxPoolSize = maxPoolSize;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }

    public long getKeepTime() {
        return keepTime;
    }

    public void setKeepTime(long keepTime) {
        this.keepTime = keepTime;
    }

    public int getElectionTimeout() {
        return electionTimeout;
    }

    public void setElectionTimeout(int electionTimeout) {
        this.electionTimeout = electionTimeout;
    }

    public int getHeartbeatTimeout() {
        return heartbeatTimeout;
    }

    public void setHeartbeatTimeout(int heartbeatTimeout) {
        this.heartbeatTimeout = heartbeatTimeout;
    }

    public long getPreHeartbeatTime() {
        return preHeartbeatTime;
    }

    public void setPreHeartbeatTime(long preHeartbeatTime) {
        this.preHeartbeatTime = preHeartbeatTime;
    }

    public long getPreElectionTime() {
        return preElectionTime;
    }

    public void setPreElectionTime(long preElectionTime) {
        this.preElectionTime = preElectionTime;
    }

    public Map<Integer, String> getNodeMap() {
        return nodeMap;
    }

    public Map<Integer, String> getNodeMgrMap() {
        return nodeMgrMap;
    }
}
