package cn.autolabor;

import cn.autolabor.core.annotation.*;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.navigation.Msg2DOdometry;
import cn.autolabor.message.navigation.Msg2DPose;
import cn.autolabor.message.navigation.Msg2DTwist;
import cn.autolabor.message.sensor.MsgLidar;

@TaskProperties
public class FilterTwistTask extends AbstractTask {

    @InjectMessage(topic = "scan_out")
    private MessageHandle<MsgLidar> lidarOutHandle;

    @InjectMessage(topic = "cmdvel")
    private MessageHandle<Msg2DOdometry> twistOutHandle;

    private PoseDetectionTask poseDetectionTask;

    public FilterTwistTask(String... name) {
        super(name);
    }

    @FilterTask
    @TaskFunction(name = "filterTask")
    public void filterTask(AbstractTask task) {
        if (task instanceof PoseDetectionTask) {
            poseDetectionTask = (PoseDetectionTask) task;
        }
    }

    @SubscribeMessage(topic = "scan")
    @TaskFunction(name = "addFrame")
    public void addFrame(MsgLidar msg) {
        msg.getHeader().setCoordinate("lidar");
        lidarOutHandle.pushSubData(msg);
    }

    @SubscribeMessage(topic = "cmdvel_in")
    @TaskFunction(name = "filterTwist")
    public void filterTwist(Msg2DOdometry msg) {
        if (poseDetectionTask != null) {
            Msg2DTwist twist = poseDetectionTask.choiceTwist(msg.getTwist(), true);
//            Msg2DTwist twist = poseDetectionTask.smartChoiceTwist(msg.getTwist());
            Msg2DOdometry out = new Msg2DOdometry(new Msg2DPose(0, 0, 0), null == twist ? new Msg2DTwist(0, 0, 0) : twist);
            System.out.println(out);
            twistOutHandle.pushSubData(out);
        }
    }


}
