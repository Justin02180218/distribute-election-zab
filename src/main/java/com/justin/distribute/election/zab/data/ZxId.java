package com.justin.distribute.election.zab.data;

/**
 * WX: coding到灯火阑珊
 * @author Justin
 */
public class ZxId implements Comparable<ZxId> {
    private long epoch;
    private long counter;

    public ZxId() {}

    public ZxId(final long epoch, final long counter) {
        this.epoch = epoch;
        this.counter = counter;
    }

    @Override
    public int compareTo(ZxId o) {
        if (this.epoch < o.epoch) {
            return -1;
        }else if (this.epoch > o.epoch) {
            return 1;
        }else if (this.counter < o.counter) {
            return -1;
        }else if (this.counter > o.counter) {
            return 1;
        }
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ZxId: [");
        sb.append(" epoch=" + epoch);
        sb.append(" counter=" + counter);
        sb.append("]");
        return sb.toString();
    }

    public long getEpoch() {
        return epoch;
    }

    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }

    public long getCounter() {
        return counter;
    }

    public void setCounter(long counter) {
        this.counter = counter;
    }
}
