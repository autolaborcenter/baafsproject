package cn.autolabor.baafs;

import cn.autolabor.core.annotation.FilterTask;
import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskParameter;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.message.navigation.Msg2DPoint;
import cn.autolabor.message.navigation.Msg2DPose;
import cn.autolabor.message.navigation.MsgPolygon;

import java.util.ArrayList;
import java.util.List;

@TaskProperties
public class PoseDetectionTask extends AbstractTask {

    @TaskParameter(name = "outline", value = "[[1.0,1.0],[1.0,-1.0],[-1.0,-1.0],[-1.0,1.0]]")
    List<List<Double>> outline;
    @TaskParameter(name = "baseLinkFrame", value = "baseLink")
    private String baseLinkFrame;
    private ObstacleDetectionTask obstacleDetectionTask;

    public PoseDetectionTask(String... name) {
        super(name);
    }

    public static void main(String[] args) {
        ServerManager.me().register(new PoseDetectionTask());
    }

    @FilterTask
    @TaskFunction
    public void searchObstacleDetectionTask(AbstractTask task) {
        if (task instanceof ObstacleDetectionTask) {
            obstacleDetectionTask = (ObstacleDetectionTask) task;
        }
    }

    @TaskFunction
    public List<Msg2DPose> filterPoses(List<Msg2DPose> poses) {
        List<Msg2DPose> out = new ArrayList<>();
        if (obstacleDetectionTask != null) {
            poses.forEach(p -> {
                if (!obstacleDetectionTask.detectCollision(transform(p))) {
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
