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

    private final MessageHandle<Msg2DOdometry> topicSender;

    // 打开里程计资源，翻译数据帧并发送
    @SuppressWarnings("unchecked")
    public PM1Task(String... name) {
        super(name);
        topicSender = ServerManager.me().getOrCreateMessageHandle(odometryTopic, new TypeNode(Msg2DPose.class));
    }

    @TaskFunction
    public void sendOdometry() {
        Odometry odometry = PM1.getOdometry();
        Msg2DOdometry temp = new Msg2DOdometry();
        temp.getHeader().setStamp(odometry.getStamp());
        temp.setPose(new Msg2DPose(odometry.getX(), odometry.getY(), odometry.getTheta()));
        topicSender.pushSubData(temp);
        ServerManager.me().delayRun(this, 100L, "sendOdometry");
    }

    @Override
    public void onClose() {
        super.onClose();
        PM1.safeShutdown();
    }
}
