package cn.autolabor;

import cn.autolabor.core.annotation.*;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.navigation.Msg2DOdometry;
import cn.autolabor.message.navigation.Msg2DPose;
import cn.autolabor.message.navigation.Msg2DTwist;
import cn.autolabor.message.sensor.MsgLidar;
import org.mechdancer.SimpleLogger;

@TaskProperties
public class FilterTwistTask extends AbstractTask {

    @TaskParameter(name = "cmdTopicInput", value = "cmdvel_in")
    private String cmdTopicInput;
    @TaskParameter(name = "cmdTopicOutput", value = "cmdvel")
    private String cmdTopicOutput;
    @TaskParameter(name = "tryCount", value = "5")
    private int tryCount;
    @TaskParameter(name = "smartChoice", value = "false")
    private boolean smartChoice;
    @TaskParameter(name = "lidarTimeout", value = "1000")
    private int lidarTimeout;
    @TaskParameter(name = "lidarTopic", value = "scan_out")
    private String lidarTopic;

    private int count = 0;
    private SimpleLogger logger = new SimpleLogger("Filter_Twist_logger");

    @InjectMessage(topic = "${cmdTopicOutput}")
    private MessageHandle<Msg2DOdometry> twistOutHandle;

    @InjectMessage(topic = "${lidarTopic}")
    private MessageHandle<MsgLidar> lidarMessageHandle;

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

    @SubscribeMessage(topic = "${cmdTopicInput}")
    @TaskFunction(name = "filterTwist")
    public void filterTwist(Msg2DOdometry msg) {
        if (poseDetectionTask != null) {
            long timeDiff = System.currentTimeMillis() - lidarMessageHandle.getLastMessageReceiveTime();
            if (timeDiff >= lidarTimeout) {
                logger.log(String.format("Lidar timeout : %d", timeDiff));
                twistOutHandle.pushSubData(new Msg2DOdometry(new Msg2DPose(0, 0, 0), new Msg2DTwist(0, 0, 0)));
                return;
            }
            Msg2DTwist twist = smartChoice ? poseDetectionTask.smartChoiceTwist(msg.getTwist()) : poseDetectionTask.choiceTwist(msg.getTwist(), true);
            count = twist == null ? 0 : count + 1;
            Msg2DOdometry out = new Msg2DOdometry(new Msg2DPose(0, 0, 0),
                    count < tryCount
                            ? new Msg2DTwist(0, 0, 0)
                            : twist);
            logger.log(String.format("count : %2d  result : %-5s | in -> v : %-5.4f, w : %-5.4f | out -> v : %-5.4f, w : %-5.4f |",
                    count,
                    count < tryCount ? "stop" : "run ",
                    msg.getTwist().getX(), msg.getTwist().getYaw(),
                    out.getTwist().getX(), out.getTwist().getYaw()));
            twistOutHandle.pushSubData(out);
        }
    }
}
