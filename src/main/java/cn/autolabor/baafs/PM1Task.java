package cn.autolabor.baafs;

import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.navigation.Msg2DOdometry;
import cn.autolabor.message.navigation.Msg2DPose;
import cn.autolabor.pm1.Resource;
import cn.autolabor.util.reflect.TypeNode;
import kotlin.Unit;

/**
 * 里程计转发任务
 */
@TaskProperties
public class PM1Task extends AbstractTask {
    private final MessageHandle<Msg2DOdometry> topicSender;
    private final Resource resource;

    // 打开里程计资源，翻译数据帧并发送
    public PM1Task(String topic) {
        //noinspection unchecked
        topicSender = ServerManager.me().getOrCreateMessageHandle(topic, new TypeNode(Msg2DPose.class));
        resource = new Resource(odometry -> {
            Msg2DOdometry temp = new Msg2DOdometry();
            temp.getHeader().setStamp(odometry.getStamp());
            temp.setPose(new Msg2DPose(odometry.getX(), odometry.getY(), odometry.getTheta()));
            topicSender.pushSubData(temp);
            return Unit.INSTANCE;
        });
        asyncRun("run");
    }

    @TaskFunction
    public void run() {
        resource.invoke();
        asyncRun("run");
    }

    @Override
    public void onClose() {
        super.onClose();
        resource.close();
    }
}
