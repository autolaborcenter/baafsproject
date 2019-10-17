package cn.autolabor;

import cn.autolabor.core.annotation.*;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.navigation.Msg2DOdometry;
import cn.autolabor.message.navigation.Msg2DPose;
import cn.autolabor.message.navigation.Msg2DTwist;
import cn.autolabor.message.navigation.MsgPolygon;
import org.mechdancer.SimpleLogger;
import org.mechdancer.exceptions.DataTimeoutException;

import java.util.List;

@TaskProperties
public class FilterTwistTask extends AbstractTask {

    @TaskParameter(name = "stopToRunCount", value = "5")
    private int stopToRunCount;

    @TaskParameter(name = "cmdTopicInput", value = "cmdvel_in")
    private String cmdTopicInput;
    @TaskParameter(name = "cmdTopicOutput", value = "cmdvel")
    private String cmdTopicOutput;
    @TaskParameter(name = "runToStopCount", value = "5")
    private int runToStopCount;
    private StatusType status = StatusType.stop;
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
    private MessageHandle<List<MsgPolygon>> lidarMessageHandle;
    private PoseDetectionTask poseDetectionTask;

    public FilterTwistTask(String... name) {
        super(name);
    }

    @SubscribeMessage(topic = "${cmdTopicInput}")
    @TaskFunction(name = "filterTwist")
    public void filterTwist(Msg2DOdometry msg) {
        if (poseDetectionTask != null) {
            long timeDiff = System.currentTimeMillis() - lidarMessageHandle.getLastMessageReceiveTime();
            if (timeDiff >= lidarTimeout) {
                logger.log(String.format("Lidar timeout : %d", timeDiff));
                twistOutHandle.pushSubData(new Msg2DOdometry(new Msg2DPose(0, 0, 0), new Msg2DTwist(0, 0, 0)));
                throw new DataTimeoutException("faselase lidar");
            }
            Msg2DTwist twist = smartChoice ? poseDetectionTask.smartChoiceTwist(msg.getTwist()) : poseDetectionTask.choiceTwist(msg.getTwist(), true);
            switch (status) {
                case run:
                    if (null == twist) {
                        count += 1;
                        if (count >= runToStopCount) {
                            status = StatusType.stop;
                            count = 0;
                        }
                    } else {
                        count = 0;
                    }
                    break;
                case stop:
                    if (null != twist) {
                        count += 1;
                        if (count >= stopToRunCount) {
                            status = StatusType.run;
                            count = 0;
                        }
                    } else {
                        count = 0;
                    }
                    break;
            }
            Msg2DOdometry out = new Msg2DOdometry(new Msg2DPose(0, 0, 0),
                status.equals(StatusType.stop)
                    ? new Msg2DTwist(0, 0, 0)
                    : msg.getTwist());

            logger.log(String.format("count : %2d  result : %-5s | in -> v : %-5.4f, w : %-5.4f | out -> v : %-5.4f, w : %-5.4f |",
                count,
                status.equals(StatusType.stop) ? "stop" : "run ",
                msg.getTwist().getX(), msg.getTwist().getYaw(),
                out.getTwist().getX(), out.getTwist().getYaw()));
            twistOutHandle.pushSubData(out);
        }
    }

    @FilterTask
    @TaskFunction(name = "filterTask")
    public void filterTask(AbstractTask task) {
        if (task instanceof PoseDetectionTask) {
            poseDetectionTask = (PoseDetectionTask) task;
        }
    }

    public enum StatusType {
        run, stop
    }
}
