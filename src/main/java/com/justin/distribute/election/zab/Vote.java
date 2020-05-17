package com.justin.distribute.election.zab;

import com.justin.distribute.election.zab.data.ZxId;

/**
 * WX: coding到灯火阑珊
 * @author Justin
 */
public class Vote implements Comparable<Vote>{
    private int nodeId;
    private volatile long epoch;
    private volatile int voteId;
    private volatile ZxId lastZxId;

    public Vote() {}

    public Vote(final int nodeId, final int voteId, final long epoch, final ZxId lastZxId) {
        this.nodeId = nodeId;
        this.voteId = voteId;
        this.epoch = epoch;
        this.lastZxId = lastZxId;
    }

    public void incrementEpoch() {
        this.setEpoch(++epoch);
    }

    @Override
    public int compareTo(Vote o) {
        if (this.lastZxId.compareTo(o.lastZxId) != 0) {
            return this.lastZxId.compareTo(o.lastZxId);
        }else if (this.nodeId < o.nodeId) {
            return -1;
        }else if (this.nodeId > o.nodeId) {
            return 1;
        }
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Vote: [");
        sb.append(" nodeId=" + nodeId);
        sb.append(" voteId=" + voteId);
        sb.append(" epoch=" + epoch);
        sb.append(" lastZxId=" + lastZxId);
        sb.append("]");
        return sb.toString();
    }

    public int getNodeId() {
        return nodeId;
    }

    public void setNodeId(int nodeId) {
        this.nodeId = nodeId;
    }

    public ZxId getLastZxId() {
        return lastZxId;
    }

    public void setLastZxId(ZxId lastZxId) {
        this.lastZxId = lastZxId;
    }

    public int getVoteId() {
        return voteId;
    }

    public void setVoteId(int voteId) {
        this.voteId = voteId;
    }

    public long getEpoch() {
        return epoch;
    }

    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }
}
