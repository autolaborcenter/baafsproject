package cn.autolabor.baafs;

import cn.autolabor.core.annotation.InjectMessage;
import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskParameter;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.navigation.Msg2DPoint;
import cn.autolabor.message.navigation.Msg2DPose;
import cn.autolabor.message.navigation.MsgPolygon;

import java.util.ArrayList;
import java.util.List;

import static cn.autolabor.baafs.GeometricUtil.detectCollision;

@TaskProperties
public class PoseDetectionTask extends AbstractTask {

    @TaskParameter(name = "outline", value = "[[1.0,1.0],[1.0,-1.0],[-1.0,-1.0],[-1.0,1.0]]")
    List<List<Double>> outline;
    @TaskParameter(name = "baseLinkFrame", value = "baseLink")
    private String baseLinkFrame;

    @InjectMessage(topic = "obstacles")
    private MessageHandle<List<MsgPolygon>> obstaclesHandle;

    public PoseDetectionTask(String... name) {
        super(name);
    }

    @TaskFunction
    public List<Msg2DPose> filterPoses(List<Msg2DPose> poses) {
        List<MsgPolygon> obstacles = obstaclesHandle.getFirstData();
        List<Msg2DPose> out = new ArrayList<>();
        if (obstacles != null) {
            poses.forEach(p -> {
                if (!detectCollision(obstacles, transform(p))) {
                    out.add(p);
                }
            });
        }
        return out;
    }

    private MsgPolygon transform(Msg2DPose pose) {
        List<Msg2DPoint> out = new ArrayList<>();
        for (List<Double> doubles : outline) {
            double x = doubles.get(0);
            double y = doubles.get(1);
            double trans_x = pose.getX() + x * Math.cos(pose.getYaw()) - y * Math.sin(pose.getYaw());
            double trans_y = pose.getY() + x * Math.sin(pose.getYaw()) + y * Math.cos(pose.getYaw());
            out.add(new Msg2DPoint(trans_x, trans_y));
        }
        return new MsgPolygon(baseLinkFrame, out);
    }


}
