package com.justin.distribute.election.zab.processor.nodeprocessor;

import com.justin.distribute.election.zab.Node;
import com.justin.distribute.election.zab.NodeStatus;
import com.justin.distribute.election.zab.Vote;
import com.justin.distribute.election.zab.message.nodemsg.VoteMessage;
import com.justin.net.remoting.netty.NettyRequestProcessor;
import com.justin.net.remoting.protocol.RemotingMessage;
import io.netty.channel.ChannelHandlerContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * WX: coding到灯火阑珊
 * @author Justin
 */
public class VoteRequestProcessor implements NettyRequestProcessor {
    private static final Logger logger = LogManager.getLogger(VoteRequestProcessor.class.getSimpleName());

    private final Node node;

    public VoteRequestProcessor(final Node node) {
        this.node = node;
    }

    @Override
    public RemotingMessage processRequest(ChannelHandlerContext ctx, RemotingMessage request) throws Exception {
        try {
            if (node.getVoteLock().tryLock(3000, TimeUnit.MILLISECONDS)) {
                VoteMessage resVoteMsg = VoteMessage.getInstance().parseMessage(request);
                Vote peerVote = resVoteMsg.getVote();
                logger.info("Receive peer vote: {}", peerVote);

                node.getVoteBox().put(peerVote.getNodeId(), peerVote);
                if (peerVote.getEpoch() > node.getMyVote().getEpoch()) {
                    node.getMyVote().setEpoch(peerVote.getEpoch());
                    node.getMyVote().setVoteId(peerVote.getNodeId());
                    node.setStatus(NodeStatus.LOOKING);
                }else if (peerVote.getEpoch() == node.getMyVote().getEpoch()) {
                    if (peerVote.compareTo(node.getMyVote()) == 1) {
                        node.getMyVote().setVoteId(peerVote.getNodeId());
                        node.setStatus(NodeStatus.LOOKING);
                    }
                }

                if (node.isHalf()) {
                    logger.info("Node:{} become leader!", node.getNodeConfig().getNodeId());
                    node.becomeLeader();
                }else if (node.getStatus() == NodeStatus.LOOKING){
                    VoteMessage voteMsg = VoteMessage.getInstance();
                    voteMsg.setVote(node.getMyVote());
                    node.sendOneWayMsg(voteMsg.request());
                }
            }

            return null;
        }finally {
            node.getVoteLock().unlock();
        }
    }

    @Override
    public boolean rejectRequest() {
        return false;
    }
}
