package cn.autolabor.baafs;

import cn.autolabor.core.annotation.InjectMessage;
import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskParameter;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.sensor.MsgLidar;
import cn.autolabor.util.Strings;
import com.faselase.Resource;
import kotlin.Unit;
import org.mechdancer.common.extension.RangeKt;

import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.PI;
import static kotlin.ranges.RangesKt.rangeTo;

/**
 * 砝石雷达转发任务
 */
@TaskProperties(unique = false)
public class FaselaseTask extends AbstractTask {

    private final Resource resource;
    @TaskParameter(name = "topic", value = "scan")
    private String topic;
    @TaskParameter(name = "frameId", value = "lidar")
    private String frameId;
    @TaskParameter(name = "comName", value = "")
    private String comName;
    @InjectMessage(topic = "${topic}")
    private MessageHandle<MsgLidar> topicSender;
    private long time = System.currentTimeMillis();

    public FaselaseTask(String... name) {
        super(name);
        resource = new Resource(Strings.isBlank(comName) ? null : comName, list -> {
            long now = System.currentTimeMillis();
            if (now - time < 100) return Unit.INSTANCE;
            time = now;
            // 拆分
            List<Double> distances = new ArrayList<>(list.size());
            List<Double> angles = new ArrayList<>(list.size());
            list.forEach(pair -> {
                distances.add(pair.getData().getDistance());
                angles.add(RangeKt.adjust(rangeTo(-PI, +PI), pair.getData().getAngle()));
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
        if (resource != null) resource.close();
    }
}
