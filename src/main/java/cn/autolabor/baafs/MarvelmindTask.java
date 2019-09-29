package cn.autolabor.baafs;

import cn.autolabor.core.annotation.InjectMessage;
import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskParameter;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.navigation.Msg2DOdometry;
import com.marvelmind.Resource;
import kotlin.Unit;

/**
 * 超声波定位转发任务
 */
@TaskProperties
public class MarvelmindTask extends AbstractTask {

    @TaskParameter(name = "topic", value = "scan")
    private String topic;

    @TaskParameter(name = "frameId", value = "tag")
    private String frameId;

    @InjectMessage(topic = "${topic}")
    private MessageHandle<Msg2DOdometry> topicSender;
    private final Resource resource;

    // 打开超声资源，翻译数据帧并发送
    @SuppressWarnings("unchecked")
    public MarvelmindTask(String... name) {
        super(name);
        resource = new Resource(null, (stamp, x, y) -> {
            Msg2DOdometry temp = new Msg2DOdometry();
            temp.getHeader().setStamp(stamp);
            temp.getHeader().setCoordinate(frameId);
            temp.getPose().setX(x);
            temp.getPose().setY(y);
            topicSender.pushSubData(temp);
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
