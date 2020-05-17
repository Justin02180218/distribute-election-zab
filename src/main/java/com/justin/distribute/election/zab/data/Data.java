package com.justin.distribute.election.zab.data;

import com.justin.net.remoting.common.Pair;

/**
 * WX: coding到灯火阑珊
 * @author Justin
 */
public class Data {
    private ZxId zxId;
    private Pair<String, String> kv;

    public Data() {}

    public Data(final ZxId zxId, final Pair<String, String> kv) {
        this.zxId = zxId;
        this.kv = kv;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Data: {");
        sb.append(" zxId=" + zxId);
        sb.append(" kv=[" + kv.getObject1() + ":" + kv.getObject2());
        sb.append("}");
        return sb.toString();
    }

    public ZxId getZxId() {
        return zxId;
    }

    public void setZxId(ZxId zxId) {
        this.zxId = zxId;
    }

    public Pair<String, String> getKv() {
        return kv;
    }

    public void setKv(Pair<String, String> kv) {
        this.kv = kv;
    }
}
