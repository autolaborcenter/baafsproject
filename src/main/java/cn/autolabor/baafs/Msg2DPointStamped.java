package cn.autolabor.baafs;

import cn.autolabor.util.autobuf.SerializableMessage;

public class Msg2DPointStamped implements SerializableMessage {
    private long stamp;
    private double x;
    private double y;

    public Msg2DPointStamped(long stamp, double x, double y) {
        this.stamp = stamp;
        this.x = x;
        this.y = y;
    }

    public long getStamp() {
        return this.stamp;
    }

    public double getX() {
        return this.x;
    }

    public double getY() {
        return this.y;
    }
}
