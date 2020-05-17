package com.justin.distribute.election.zab;

import com.justin.distribute.election.zab.data.Data;
import com.justin.distribute.election.zab.data.DataManager;
import com.justin.distribute.election.zab.data.ZxId;
import com.justin.distribute.election.zab.message.MessageType;
import com.justin.distribute.election.zab.message.nodemgrmsg.JoinGroupMessage;
import com.justin.distribute.election.zab.message.nodemsg.DataMessage;
import com.justin.distribute.election.zab.message.nodemsg.VoteMessage;
import com.justin.distribute.election.zab.processor.nodemgrprocessor.JoinGroupProcessor;
import com.justin.distribute.election.zab.processor.nodeprocessor.ClientRequestProcessor;
import com.justin.distribute.election.zab.processor.nodeprocessor.DataRequestProcessor;
import com.justin.distribute.election.zab.processor.nodeprocessor.VoteRequestProcessor;
import com.justin.net.remoting.RemotingClient;
import com.justin.net.remoting.RemotingServer;
import com.justin.net.remoting.common.Pair;
import com.justin.net.remoting.netty.NettyRemotingClient;
import com.justin.net.remoting.netty.NettyRemotingServer;
import com.justin.net.remoting.netty.conf.NettyClientConfig;
import com.justin.net.remoting.netty.conf.NettyServerConfig;
import com.justin.net.remoting.protocol.RemotingMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * WX: coding到灯火阑珊
 * @author Justin
 */
public class Node {
    private static final Logger logger = LogManager.getLogger(Node.class.getSimpleName());

    private final ConcurrentMap<Integer, Vote> voteBox = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, ZxId> zxIdMap = new ConcurrentHashMap<>();
    private final ConcurrentMap<Integer, Boolean> snapshotMap = new ConcurrentHashMap<>();
    private final Lock voteLock = new ReentrantLock();
    private final Lock dataLock = new ReentrantLock();
    private final CountDownLatch countDownLatch = new CountDownLatch(1);

    private final ExecutorService executorService;
    private final ScheduledExecutorService scheduledExecutorService;
    private final NodeConfig nodeConfig;
    private final DataManager dataManager;

    private volatile NodeStatus status = NodeStatus.FOLLOWING;
    private volatile int leaderId;
    private volatile long epoch;
    private volatile boolean running = false;

    private RemotingServer nodeServer;
    private RemotingServer nodeMgrServer;
    private RemotingClient client;

    private Vote myVote;

    public Node(final NodeConfig nodeConfig) {
        this.nodeConfig = nodeConfig;
        this.dataManager = DataManager.getInstance();
        this.executorService = new ThreadPoolExecutor(nodeConfig.getCup(), nodeConfig.getMaxPoolSize(),
                nodeConfig.getKeepTime(), TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(nodeConfig.getQueueSize()));
        this.scheduledExecutorService = Executors.newScheduledThreadPool(3);
    }

    public void start() {
        if (running) {
            return;
        }

        synchronized (this) {
            if (running) {
                return;
            }

            nodeMgrServer = new NettyRemotingServer(new NettyServerConfig(nodeConfig.getHost(), nodeConfig.getNodeMgrPort()));
            nodeMgrServer.registerProcessor(MessageType.JOIN_GROUP, new JoinGroupProcessor(this), executorService);
            nodeMgrServer.start();

            nodeServer = new NettyRemotingServer(new NettyServerConfig(nodeConfig.getHost(), nodeConfig.getPort()));
            nodeServer.registerProcessor(MessageType.VOTE, new VoteRequestProcessor(this), executorService);
            nodeServer.registerProcessor(MessageType.DATA_SYNC, new DataRequestProcessor(this), executorService);
            nodeServer.registerProcessor(MessageType.CLIENT, new ClientRequestProcessor(this), executorService);
            nodeServer.start();

            client = new NettyRemotingClient(new NettyClientConfig());
            client.start();

            scheduledExecutorService.schedule(new Runnable() {
                @Override
                public void run() {
                    init();
                }
            }, 2000, TimeUnit.MILLISECONDS);

            scheduledExecutorService.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    election();
                }
            }, 4000, 500, TimeUnit.MILLISECONDS);

            scheduledExecutorService.scheduleWithFixedDelay(new Runnable() {
                @Override
                public void run() {
                    heartbeat();
                }
            }, 0, nodeConfig.getHeartbeatTimeout(), TimeUnit.MILLISECONDS);
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                shutdown();
            }
        });
    }

    public void shutdown() {
        synchronized (this) {
            if (nodeMgrServer != null) {
                nodeMgrServer.shutdown();
            }
            if (nodeServer != null) {
                nodeServer.shutdown();
            }
            if (client != null) {
                client.shutdown();
            }
            if (dataManager != null) {
                dataManager.close();
            }
            if (scheduledExecutorService != null) {
                scheduledExecutorService.shutdown();
            }
            if (executorService != null) {
                executorService.shutdown();
            }
            running = false;
        }
    }

    private void init() {
        JoinGroupMessage joinGroupMsg = JoinGroupMessage.getInstance();
        joinGroupMsg.setNodeId(nodeConfig.getNodeId());
        joinGroupMsg.setHost(nodeConfig.getHost());
        joinGroupMsg.setPort(nodeConfig.getPort());
        joinGroupMsg.setNodeMgrPort(nodeConfig.getNodeMgrPort());

        for (Map.Entry<Integer, String> entry : nodeConfig.getNodeMgrMap().entrySet()) {
            if (entry.getKey() == nodeConfig.getNodeId()) {
                continue;
            }

            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        RemotingMessage response = client.invokeSync(entry.getValue(), joinGroupMsg.request(), 3*1000);
                        JoinGroupMessage res = JoinGroupMessage.getInstance().parseMessage(response);
                        if (res.getSuccess()) {
                            int peerNodeId = res.getNodeId();
                            String host = res.getHost();
                            int port = res.getPort();
                            int nodeMgrPort = res.getNodeMgrPort();
                            nodeConfig.getNodeMap().putIfAbsent(peerNodeId, host+":"+port);
                            nodeConfig.getNodeMgrMap().putIfAbsent(peerNodeId, host+":"+nodeMgrPort);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    private void election() {
        if (status == NodeStatus.LEADING) {
            return;
        }

        if (!nodeConfig.resetElectionTick()) {
            return;
        }

        status = NodeStatus.LOOKING;
        epoch += 1;
        zxIdMap.clear();

        this.myVote = new Vote(nodeConfig.getNodeId(), nodeConfig.getNodeId(), 0, getLastZxId());
        this.myVote.setEpoch(epoch);
        this.voteBox.put(nodeConfig.getNodeId(), myVote);

        VoteMessage voteMessage = VoteMessage.getInstance();
        voteMessage.setVote(myVote);
        sendOneWayMsg(voteMessage.request());
    }

    private void heartbeat() {
        if (status != NodeStatus.LEADING) {
            return;
        }

        if (!nodeConfig.resetHeartbeatTick()) {
            return;
        }

        for (Map.Entry<Integer, String> entry : nodeConfig.getNodeMap().entrySet()) {
            if (!voteBox.containsKey(entry.getKey())) {
                continue;
            }

            if (entry.getKey() == nodeConfig.getNodeId()) {
                continue;
            }

            long index = -1;
            if (zxIdMap.containsKey(entry.getKey())) {
                index = zxIdMap.get(entry.getKey()).getCounter();
            }else {
                index = dataManager.getLastIndex();
            }

            Data data = dataManager.read(index);
            if (data.getZxId().getEpoch() == 0) {
                data.getZxId().setEpoch(epoch);
            }

            DataMessage dataMsg = DataMessage.getInstance();
            dataMsg.setNodeId(nodeConfig.getNodeId());
            dataMsg.setType(DataMessage.Type.SYNC);
            dataMsg.setData(data);

            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        RemotingMessage response = client.invokeSync(entry.getValue(), dataMsg.request(), 3*1000);
                        DataMessage res = DataMessage.getInstance().parseMessage(response);
                        if (res.getSuccess()) {
                            int peerId = res.getNodeId();
                            ZxId peerZxId = res.getData().getZxId();
                            zxIdMap.put(peerId, peerZxId);
                        }
                    } catch (Exception e) {
                        logger.error(e);
                    }
                }
            });
        }
    }

    public void sendOneWayMsg(RemotingMessage msg) {
        for (Map.Entry<Integer, String> entry : nodeConfig.getNodeMap().entrySet()) {
            if (entry.getKey() == nodeConfig.getNodeId()) {
                continue;
            }

            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        client.invokeOneway(entry.getValue(), msg, 3*1000);
                    } catch (Exception e) {
                        logger.error(e);
                    }
                }
            });
        }
    }

    public boolean isHalf() {
        if (voteBox.size() != nodeConfig.getNodeMap().size()) {
            return false;
        }

        int voteCounter = 0;
        for (Map.Entry<Integer, Vote> entry : voteBox.entrySet()) {
            if (entry.getValue().getVoteId() == myVote.getNodeId()) {
                voteCounter += 1;
            }
        }
        if (voteCounter > nodeConfig.getNodeMap().size()/2) {
            return true;
        }else {
            return false;
        }
    }

    public void becomeLeader() {
        this.leaderId = nodeConfig.getNodeId();
        this.status = NodeStatus.LEADING;
    }

    public RemotingMessage redirect(RemotingMessage request) {
        RemotingMessage response = null;
        try {
            response = client.invokeSync(nodeConfig.getNodeMap().get(leaderId), request, 3*1000);
        } catch (Exception e) {
            logger.error(e);
        }
        return response;
    }

    public void appendData(final String key, final String value) {
        Data data = new Data();
        data.setKv(new Pair<>(key, value));

        DataMessage dataMessage = DataMessage.getInstance();
        dataMessage.setNodeId(nodeConfig.getNodeId());
        dataMessage.setData(data);
        dataMessage.setType(DataMessage.Type.SNAPSHOT);

        for (Map.Entry<Integer, String> entry : nodeConfig.getNodeMap().entrySet()) {
            if (entry.getKey() == nodeConfig.getNodeId()) {
                continue;
            }

            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    try {
                        RemotingMessage response = client.invokeSync(entry.getValue(), dataMessage.request(), 3*1000);
                        DataMessage resDataMsg = DataMessage.getInstance().parseMessage(response);
                        int peerId = resDataMsg.getNodeId();
                        boolean success = resDataMsg.getSuccess();
                        snapshotMap.put(peerId, success);

                        int snapshotCounter = 0;
                        for (Boolean flag : snapshotMap.values()) {
                            if (flag) {
                                snapshotCounter += 1;
                            }
                        }
                        if (snapshotCounter > nodeConfig.getNodeMap().size()/2) {
                            countDownLatch.countDown();
                        }
                    } catch (Exception e) {
                        logger.error(e);
                    }
                }
            });
        }
    }

    public boolean commitData(final String key) throws InterruptedException {
        if (countDownLatch.await(6000, TimeUnit.MILLISECONDS)) {
            snapshotMap.clear();
            long lastIndex = dataManager.getLastIndex();
            String value = dataManager.get(key);
            ZxId zxId = new ZxId(epoch, lastIndex+1);
            Pair<String, String> kv = new Pair<>(key, value);
            Data data = new Data(zxId, kv);

            boolean flag = dataManager.write(data);
            if (flag) {
                DataMessage dataMessage = DataMessage.getInstance();
                dataMessage.setNodeId(nodeConfig.getNodeId());
                dataMessage.setData(data);
                dataMessage.setType(DataMessage.Type.COMMIT);

                for (Map.Entry<Integer, String> entry : nodeConfig.getNodeMap().entrySet()) {
                    if (entry.getKey() == nodeConfig.getNodeId()) {
                        continue;
                    }

                    executorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                client.invokeOneway(entry.getValue(), dataMessage.request(), 3000);
                            } catch (Exception e) {
                                logger.error(e);
                            }
                        }
                    });
                }
            }

            return flag;
        }else {
            return false;
        }
    }

    private ZxId getLastZxId() {
        long lastIndex = dataManager.getLastIndex();
        if (lastIndex == -1) {
            return new ZxId(0, 0);
        }else {
            Data data = dataManager.read(lastIndex);
            return data.getZxId();
        }
    }

    public ConcurrentMap<Integer, Vote> getVoteBox() {
        return voteBox;
    }

    public NodeConfig getNodeConfig() {
        return nodeConfig;
    }

    public DataManager getDataManager() {
        return dataManager;
    }

    public Vote getMyVote() {
        return myVote;
    }

    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status;
    }

    public Lock getVoteLock() {
        return voteLock;
    }

    public Lock getDataLock() {
        return dataLock;
    }

    public ConcurrentMap<Integer, ZxId> getZxIdMap() {
        return zxIdMap;
    }

    public int getLeaderId() {
        return leaderId;
    }

    public void setLeaderId(int leaderId) {
        this.leaderId = leaderId;
    }

    public long getEpoch() {
        return epoch;
    }

    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }

    public ConcurrentMap<Integer, Boolean> getSnapshotMap() {
        return snapshotMap;
    }

    public CountDownLatch getCountDownLatch() {
        return countDownLatch;
    }
}
