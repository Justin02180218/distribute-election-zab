package com.justin.distribute.election.zab.processor.nodemgrprocessor;

import com.justin.distribute.election.zab.Node;
import com.justin.distribute.election.zab.Vote;
import com.justin.distribute.election.zab.data.ZxId;
import com.justin.distribute.election.zab.message.nodemgrmsg.JoinGroupMessage;
import com.justin.net.remoting.netty.NettyRequestProcessor;
import com.justin.net.remoting.protocol.RemotingMessage;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * WX: coding到灯火阑珊
 * @author Justin
 */
public class JoinGroupProcessor implements NettyRequestProcessor {
    private static final Logger logger = LogManager.getLogger(JoinGroupProcessor.class.getSimpleName());

    private final Node node;

    public JoinGroupProcessor(final Node node) {
        this.node = node;
    }

    @Override
    public RemotingMessage processRequest(ChannelHandlerContext ctx, RemotingMessage request) throws Exception {
        JoinGroupMessage req = JoinGroupMessage.getInstance().parseMessage(request);
        try {
            int peerId = req.getNodeId();
            String peerHost = req.getHost();
            int peerPort = req.getPort();
            int peerNodeMgrPort = req.getNodeMgrPort();

            node.getNodeConfig().getNodeMap().putIfAbsent(peerId, peerHost+":"+peerPort);
            node.getNodeConfig().getNodeMgrMap().putIfAbsent(peerId, peerHost+":"+peerNodeMgrPort);

            req.setNodeId(node.getNodeConfig().getNodeId());
            req.setHost(node.getNodeConfig().getHost());
            req.setPort(node.getNodeConfig().getPort());
            req.setNodeMgrPort(node.getNodeConfig().getNodeMgrPort());
            req.setSuccess(true);
            return req.response(request);
        }catch (Exception e) {
            logger.error(e);
            req.setSuccess(false);
            return req.response(request);
        }
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}
