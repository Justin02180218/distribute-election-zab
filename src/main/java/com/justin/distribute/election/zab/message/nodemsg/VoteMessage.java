package com.justin.distribute.election.zab.message.nodemsg;

import com.justin.distribute.election.zab.Vote;
import com.justin.distribute.election.zab.message.AbstractMessage;
import com.justin.distribute.election.zab.message.MessageType;

/**
 * WX: coding到灯火阑珊
 * @author Justin
 */
public class VoteMessage extends AbstractMessage<VoteMessage> {
    private Vote vote;

    private Boolean success;

    private VoteMessage() {}

    public static VoteMessage getInstance() {
        return new VoteMessage();
    }

    @Override
    public int getMessageType() {
        return MessageType.VOTE;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("VoteMessage: [");
        sb.append(" vote=" + vote);
        sb.append(" success=" + success);
        sb.append("]");
        return sb.toString();
    }

    public Vote getVote() {
        return vote;
    }

    public void setVote(Vote vote) {
        this.vote = vote;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }
}
