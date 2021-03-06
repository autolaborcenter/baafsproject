package cn.autolabor;

import cn.autolabor.core.annotation.*;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.sensor.MsgLidar;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@TaskProperties(unique = false)
public class LaserFilterTask extends AbstractTask {

    @TaskParameter(name = "angleRange", value = "[[-3.14,3.14]]")
    public List<List<Double>> angleRange;

    @TaskParameter(name = "includeFlag", value = "true")
    public boolean includeFlag;

    @TaskParameter(name = "lidarTopicInput", value = "scan")
    public String lidarTopicInput;

    @TaskParameter(name = "lidarTopicOutput", value = "scan_filter")
    private String lidarTopicOutput;

    @InjectMessage(topic = "${lidarTopicOutput}")
    private MessageHandle<MsgLidar> lidarOutputMessageHandle;

    public LaserFilterTask(String... name) {
        super(name);
    }

    @SubscribeMessage(topic = "${lidarTopicInput}")
    @TaskFunction(name = "filter")
    public void filter(MsgLidar msgLidar) {
        MsgLidar msgLidarOut = new MsgLidar();
        msgLidarOut.setHeader(msgLidar.getHeader());
        if (null != msgLidar.getAngles()) {
            List<Double> angles = new ArrayList<>();
            List<Double> distances = new ArrayList<>();
            IntStream.range(0, msgLidar.getAngles().size())
                .filter(i ->
                    angleRange.stream().allMatch(range -> {
                        double angleItem = msgLidar.getAngles().get(i);
                        return includeFlag == (angleItem >= range.get(0) && angleItem <= range.get(1));
                    }))
                .forEach(j -> {
                    angles.add(msgLidar.getAngles().get(j));
                    distances.add(msgLidar.getDistances().get(j));
                });
            msgLidarOut.setAngles(angles);
            msgLidarOut.setDistances(distances);
        }
        lidarOutputMessageHandle.pushSubData(msgLidarOut);
    }

}
