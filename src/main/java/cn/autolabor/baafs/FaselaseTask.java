package cn.autolabor.baafs;

import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskParameter;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.sensor.MsgLidar;
import cn.autolabor.util.reflect.TypeNode;
import com.faselase.Resource;
import kotlin.Unit;

import java.util.ArrayList;
import java.util.List;

/**
 * 砝石雷达转发任务
 */
@TaskProperties(unique = false)
public class FaselaseTask extends AbstractTask {

    @TaskParameter(name = "topic", value = "scan")
    private String topic;

    @TaskParameter(name = "frameId", value = "lidar")
    private String frameId;

    private final MessageHandle<MsgLidar> topicSender;
    private final Resource resource;

    @SuppressWarnings("unchecked")
    public FaselaseTask(String... name) {
        super(name);
        topicSender = ServerManager.me().getOrCreateMessageHandle(topic, new TypeNode(MsgLidar.class));
        resource = new Resource(list -> {
            // 拆分
            List<Double> distances = new ArrayList<>(list.size());
            List<Double> angles = new ArrayList<>(list.size());
            list.forEach(pair -> {
                distances.add(pair.getData().getDistance());
                angles.add(pair.getData().getAngle());
            });
            // 发送
            MsgLidar msg = new MsgLidar();
            msg.getHeader().setCoordinate(frameId);
            msg.setDistances(distances);
            msg.setAngles(angles);
            topicSender.pushSubData(msg);

            return Unit.INSTANCE;
        });
        asyncRun("run");
    }

    public String name() {
        return resource.getInfo();
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
