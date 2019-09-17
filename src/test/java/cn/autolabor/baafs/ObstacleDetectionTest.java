package cn.autolabor.baafs;

import cn.autolabor.ObstacleDetectionTask;
import cn.autolabor.PoseDetectionTask;
import cn.autolabor.core.annotation.*;
import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.navigation.Msg2DTwist;
import cn.autolabor.message.sensor.MsgLidar;

@TaskProperties
public class ObstacleDetectionTest extends AbstractTask {


    @InjectMessage(topic = "scan_out")
    private MessageHandle<MsgLidar> outLidarHandle;

    private PoseDetectionTask poseDetectionTask;

    public ObstacleDetectionTest(String... name) {
        super(name);
        asyncRun("loop");
    }

    public static void main(String[] args) {
        ServerManager.me().loadConfig("conf/obstacle.conf");
        ServerManager.me().register(new ObstacleDetectionTest());
        ServerManager.me().register(new ObstacleDetectionTask("ObstacleDetectionTask"));
        ServerManager.me().register(new PoseDetectionTask("PoseDetectionTask"));


//        ServerManager.me().register(new PoseDetectionTask("PoseDetectionTask"));
//        ServerManager.me().loadConfig("conf/obstacle.conf");
//        ServerManager.me().register(new ObstacleDetectionTask("ObstacleDetectionTask"));
//        ServerManager.me().register(new PoseDetectionTask("PoseDetectionTask"));
    }

    @FilterTask
    @TaskFunction(name = "filterTask")
    public void filterTask(AbstractTask task) {
        if (task instanceof PoseDetectionTask) {
            poseDetectionTask = (PoseDetectionTask) task;
        }
    }

//    @SubscribeMessage(topic = "obstacles")
//    @TaskFunction(name = "print")
//    public void print(List<MsgPolygon> msg) {
//        System.out.println(msg);
//    }

    @SubscribeMessage(topic = "scan")
    @TaskFunction(name = "addFrame")
    public void addFrame(MsgLidar msg) {
        msg.getHeader().setCoordinate("lidar");
        outLidarHandle.pushSubData(msg);
    }

    @TaskFunction(name = "loop")
    public void loop() {
        if (poseDetectionTask != null) {
            Msg2DTwist twist = poseDetectionTask.choiceTwist(new Msg2DTwist(1, 0, 0));
            System.out.println(twist);
        }
        asyncRunLater("loop", 500L);
    }

}
