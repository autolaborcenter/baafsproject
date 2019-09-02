package cn.autolabor.baafs;

import cn.autolabor.core.annotation.TaskFunction;
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
    private final MessageHandle<MsgLidar> topicSender;
    private final Resource resource;

    // 打开雷达资源，翻译数据帧并发送
    public FaselaseTask(String topic) {
        //noinspection unchecked
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
            msg.getHeader().setCoordinate("lidar");
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
