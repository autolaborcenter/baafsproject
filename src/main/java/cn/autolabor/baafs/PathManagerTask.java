package cn.autolabor.baafs;

import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskParameter;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.core.server.message.MessageSourceType;
import cn.autolabor.message.navigation.Msg2DOdometry;
import cn.autolabor.pathmaneger.PathManager;
import cn.autolabor.util.reflect.TypeNode;
import org.mechdancer.algebra.implement.vector.Vector2D;

import java.io.File;
import java.util.List;

@TaskProperties
public class PathManagerTask extends AbstractTask {

    private final PathManager path;
    @TaskParameter(name = "positionTopic", value = "fusion")
    private String positionTopic;
    @TaskParameter(name = "positionInterval", value = "0.05")
    private double positionInterval;
    @TaskParameter(name = "outputPath", value = "path.txt")
    private String outputPath;
    private boolean recordFlag = false;
    private MessageHandle<Msg2DOdometry> positionHandle;

    @SuppressWarnings("unchecked")
    public PathManagerTask(String... name) {
        super(name);
        path = new PathManager(positionInterval);
        positionHandle = ServerManager.me().getOrCreateMessageHandle(positionTopic, new TypeNode(Msg2DOdometry.class));
    }

    @TaskFunction(name = "start")
    public void start() {
        recordFlag = true;
        positionHandle.addCallback(this, "record", new MessageSourceType[]{});
    }

    @TaskFunction(name = "stop")
    public void stop() {
        recordFlag = false;
        positionHandle.removeCallbackByTask(this);
    }

    @TaskFunction(name = "save")
    public void save() {
        this.stop();
        path.saveTo(new File(outputPath));
    }

    @TaskFunction(name = "record")
    public void record(Msg2DOdometry msg) {
        if (recordFlag) {
            path.record(new Vector2D(msg.getPose().getX(), msg.getPose().getY()));
        }
    }

    @TaskFunction(name = "loadPathFromFile")
    public void loadPath() {
        path.loadFrom(new File(outputPath));
    }

    @TaskFunction(name = "getPath")
    public List<Vector2D> getPath() {
        return path.get();
    }

}
