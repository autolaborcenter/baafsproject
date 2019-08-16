package cn.autolabor.baafs;

import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.util.reflect.TypeNode;
import com.marvelmind.Resource;
import kotlin.Unit;

/**
 * 超声波定位转发任务
 */
@TaskProperties
public class MarvelmindTask extends AbstractTask {
    private final MessageHandle<Msg2DPointStamped> topicSender;
    private final Resource resource;

    // 打开超声资源，翻译数据帧并发送
    public MarvelmindTask(String topic) {
        //noinspection unchecked
        topicSender = ServerManager.me().getOrCreateMessageHandle(topic, new TypeNode(Msg2DPointStamped.class));
        resource = new Resource((stamp, x, y) -> {
            topicSender.pushSubData(new Msg2DPointStamped(stamp, x, y));
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
