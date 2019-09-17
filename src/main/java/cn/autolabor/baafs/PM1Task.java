package cn.autolabor.baafs;

import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskParameter;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.navigation.Msg2DOdometry;
import cn.autolabor.message.navigation.Msg2DPose;
import cn.autolabor.pm1.sdk.Odometry;
import cn.autolabor.pm1.sdk.PM1;
import cn.autolabor.util.reflect.TypeNode;

/**
 * 里程计转发任务
 */
@TaskProperties
public class PM1Task extends AbstractTask {
    @TaskParameter(name = "odometryTopic", value = "odom")
    private String odometryTopic;

    private final MessageHandle<Msg2DOdometry> odomHandle;
    private final MessageHandle<Msg2DOdometry> cmdHandle;
    @TaskParameter(name = "cmdTopic", value = "cmd_vel")
    private String cmdTopic;
    @TaskParameter(name = "controlRate", value = "10.0")
    private double controlRate;
    @TaskParameter(name = "controlTimeout", value = "1000")
    private int controlTimeout;

    // 打开里程计资源，翻译数据帧并发送
    @SuppressWarnings("unchecked")
    public PM1Task(String... name) {
        super(name);
        odomHandle = ServerManager.me().getOrCreateMessageHandle(odometryTopic, new TypeNode(Msg2DOdometry.class));
        cmdHandle = ServerManager.me().getOrCreateMessageHandle(cmdTopic, new TypeNode(Msg2DOdometry.class));
        asyncRun("driver");
    }

    @TaskFunction(name = "driver")
    public void driver() {
        if (cmdHandle.getLastMessageReceiveTime() > 0 && System.currentTimeMillis() - cmdHandle.getLastMessageReceiveTime() < controlTimeout) {
            Msg2DOdometry msg = cmdHandle.getFirstData();
            PM1.drive(msg.getTwist().getX(), msg.getTwist().getYaw());
        }
        asyncRunDelay("driver", Math.round(1000 / controlRate));
    }

    @TaskFunction(name = "getOdometry")
    public void getOdometry() {
        Odometry odometry = PM1.getOdometry();
        Msg2DOdometry temp = new Msg2DOdometry();
        temp.getHeader().setStamp(odometry.getStamp());
        temp.setPose(new Msg2DPose(odometry.getX(), odometry.getY(), odometry.getTheta()));
        odomHandle.pushSubData(temp);
        ServerManager.me().delayRun(this, 100L, "getOdometry");
    }

    @Override
    public void onClose() {
        super.onClose();
        PM1.safeShutdown();
    }
}
