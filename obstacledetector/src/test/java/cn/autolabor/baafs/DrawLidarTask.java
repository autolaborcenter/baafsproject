package cn.autolabor.baafs;

import cn.autolabor.core.annotation.InjectMessage;
import cn.autolabor.core.annotation.SubscribeMessage;
import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.navigation.Msg2DOdometry;
import cn.autolabor.message.navigation.Msg2DPoint;
import cn.autolabor.message.navigation.Msg2DPose;
import cn.autolabor.message.navigation.Msg2DTwist;
import cn.autolabor.plugin.gazebo.ignitionmsgs.MarkerProtos;
import cn.autolabor.plugin.gazebo.msg.MsgMarker;
import cn.autolabor.plugin.gazebo.task.GazeboMarkerServerTask;

import java.util.List;

@TaskProperties
public class DrawLidarTask extends AbstractTask {

    @InjectMessage(topic = "marker")
    private MessageHandle<byte[]> markerHandle;

    private List<List<Double>> outline;
    private double predictionTime = 1.0;

    public DrawLidarTask(String... name) {
        super(name);
        outline = (List<List<Double>>) ServerManager.me().getConfig("PoseDetectionTask", "outline");
        asyncRunLater("carForShow", 1000L);
    }

    public static void main(String[] args) {
        ServerManager.me().loadConfig("conf/obstacle.conf");
        ServerManager.me().register(new GazeboMarkerServerTask());
        ServerManager.me().register(new DrawLidarTask());

    }

    @TaskFunction
    public void carForShow() {
        MsgMarker marker = new MsgMarker();
        marker.setNamespace("default");
        marker.setId(1L);
        marker.setMaterial("Gazebo/White");
        marker.setAction(MarkerProtos.Marker.Action.ADD_MODIFY);
        marker.setType(MarkerProtos.Marker.Type.LINE_STRIP);
        outline.forEach(i -> {
            marker.addPoint(i.get(0), i.get(1), 0.05);
        });
        markerHandle.pushSubData(marker.toProtoByte());
        System.out.println("push car");
    }

    @SubscribeMessage(topic = "cmdvel_in")
    @TaskFunction
    public void moveCarForShow(Msg2DOdometry msg) {
        Msg2DTwist in = msg.getTwist();
        Msg2DPose predictionPose = new Msg2DPose();
        if (in.getYaw() != 0) {
            double d = in.getX() / in.getYaw();
            double theta = in.getYaw() * predictionTime;
            predictionPose.setX(d * Math.sin(theta));
            predictionPose.setY(d * (1 - Math.cos(theta)));
            predictionPose.setYaw(theta);
        } else {
            predictionPose.setX(in.getX() * predictionTime);
        }

        MsgMarker marker = new MsgMarker();
        marker.setNamespace("default");
        marker.setId(2L);
        marker.setMaterial("Gazebo/Red");
        marker.setAction(MarkerProtos.Marker.Action.ADD_MODIFY);
        marker.setType(MarkerProtos.Marker.Type.LINE_STRIP);
        outline.forEach(i -> {
            double x = i.get(0);
            double y = i.get(1);
            marker.addPoint(predictionPose.getX() + x * Math.cos(predictionPose.getYaw()) - y * Math.sin(predictionPose.getYaw()), predictionPose.getY() + x * Math.sin(predictionPose.getYaw()) + y * Math.cos(predictionPose.getYaw()), 0.06);
        });
        markerHandle.pushSubData(marker.toProtoByte());
        System.out.println("push prediction car");
    }

    @SubscribeMessage(topic = "obstacle_points")
    @TaskFunction
    public void lidarForShow(List<Msg2DPoint> data) {
        MsgMarker marker = new MsgMarker();
        marker.setNamespace("default");
        marker.setId(0L);
        marker.setMaterial("Gazebo/Yellow");
        marker.setAction(MarkerProtos.Marker.Action.ADD_MODIFY);
        marker.setType(MarkerProtos.Marker.Type.POINTS);
        data.forEach(i -> {
            marker.addPoint(i.getX(), i.getY(), 0.05);
        });
        markerHandle.pushSubData(marker.toProtoByte());
        System.out.println("push data : " + data.size());
    }

}
