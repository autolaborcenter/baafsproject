package cn.autolabor;

import cn.autolabor.core.annotation.*;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.navigation.Msg2DOdometry;
import cn.autolabor.message.navigation.Msg2DPose;
import cn.autolabor.message.navigation.Msg2DTwist;

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
            Msg2DOdometry out;
            if (twist == null) {
                count = 0;
                out = new Msg2DOdometry(new Msg2DPose(0, 0, 0), new Msg2DTwist(0, 0, 0));
            } else {
                if (count < tryCount) {
                    count += 1;
                    out = new Msg2DOdometry(new Msg2DPose(0, 0, 0), new Msg2DTwist(0, 0, 0));
                } else {
                    out = new Msg2DOdometry(new Msg2DPose(0, 0, 0), twist);
                }
            }
            twistOutHandle.pushSubData(out);
        }
    }


}
