import cn.autolabor.core.annotation.InjectMessage;
import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.sensor.MsgLidar;
import cn.autolabor.util.reflect.TypeNode;
import com.faselase.Resource;
import kotlin.Pair;
import kotlin.Suppress;
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
        resource = new Resource((begin, end, list) -> {
            List<Double> distances = new ArrayList<>(list.size());
            List<Double> angles = new ArrayList<>(list.size());
            list.forEach(pair -> {
                distances.add(pair.getFirst());
                angles.add(pair.getSecond());
            });

            MsgLidar msg = new MsgLidar();
            msg.getHeader().setStamp(end);
            msg.setDistances(distances);
            msg.setAngles(angles);
            topicSender.pushSubData(msg);

            return Unit.INSTANCE;
        });
        asyncRun("run");
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
