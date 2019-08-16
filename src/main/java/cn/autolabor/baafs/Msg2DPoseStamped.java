package cn.autolabor.baafs;

import cn.autolabor.util.autobuf.SerializableMessage;

public class Msg2DPoseStamped implements SerializableMessage {
    private long stamp;
    private double x;
    private double y;
    private double theta;

    public Msg2DPoseStamped(long stamp, double x, double y, double theta) {
        this.stamp = stamp;
        this.x = x;
        this.y = y;
        this.theta = theta;
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

    public double getTheta() {
        return this.theta;
    }
}
