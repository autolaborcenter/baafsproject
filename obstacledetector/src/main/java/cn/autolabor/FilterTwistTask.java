package cn.autolabor;

import cn.autolabor.core.annotation.*;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.navigation.Msg2DOdometry;
import cn.autolabor.message.navigation.Msg2DPose;
import cn.autolabor.message.navigation.Msg2DTwist;
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

    private int count = 0;
    private SimpleLogger logger = new SimpleLogger("速度过滤日志");

    @InjectMessage(topic = "${cmdTopicOutput}")
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

    @SubscribeMessage(topic = "${cmdTopicInput}")
    @TaskFunction(name = "filterTwist")
    public void filterTwist(Msg2DOdometry msg) {
        if (poseDetectionTask != null) {
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
