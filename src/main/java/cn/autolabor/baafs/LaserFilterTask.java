package cn.autolabor.baafs;

import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskParameter;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.core.server.message.MessageSourceType;
import cn.autolabor.message.sensor.MsgLidar;
import cn.autolabor.util.reflect.TypeNode;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

@TaskProperties(unique = false)
public class LaserFilterTask extends AbstractTask {

    @TaskParameter(name = "angleRange", value = "[-3.14,3.14]")
    public List<Double> angleRange;

    @TaskParameter(name = "includeFlag", value = "true")
    public boolean includeFlag;

    @TaskParameter(name = "lidarTopicInput", value = "scan")
    public String lidarTopicInput;

    @TaskParameter(name = "lidarTopicOutput", value = "scan_filter")
    private String lidarTopicOutput;

    private MessageHandle<MsgLidar> lidarOutputMessageHandle;

    @SuppressWarnings("unchecked")
    public LaserFilterTask(String... name) {
        super(name);
        lidarOutputMessageHandle = ServerManager.me().getOrCreateMessageHandle(lidarTopicOutput, new TypeNode(MsgLidar.class));
        MessageHandle<MsgLidar> lidarInputMessageHandle = ServerManager.me().getOrCreateMessageHandle(lidarTopicInput, new TypeNode(MsgLidar.class));
        if (angleRange.size() == 2) {
            lidarInputMessageHandle.addCallback(this, "filter", new MessageSourceType[]{});
        }
    }

    @TaskFunction(name = "filter")
    public void filter(MsgLidar msgLidar) {
        MsgLidar msgLidarOut = new MsgLidar();
        msgLidarOut.setHeader(msgLidar.getHeader());
        if (null != msgLidar.getAngles()) {
            List<Double> angles = new ArrayList<>();
            List<Double> distances = new ArrayList<>();
            IntStream.range(0, msgLidar.getAngles().size())
                    .filter(i -> includeFlag == (msgLidar.getAngles().get(i) >= angleRange.get(0) && msgLidar.getAngles().get(i) <= angleRange.get(1)))
                    .forEach(i -> {
                        angles.add(msgLidar.getAngles().get(i));
                        distances.add(msgLidar.getDistances().get(i));
                    });
            msgLidarOut.setAngles(angles);
            msgLidarOut.setDistances(distances);
        }
        lidarOutputMessageHandle.pushSubData(msgLidarOut);
    }

}
