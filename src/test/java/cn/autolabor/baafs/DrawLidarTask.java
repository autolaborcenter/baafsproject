package cn.autolabor.baafs;

import cn.autolabor.core.annotation.InjectMessage;
import cn.autolabor.core.annotation.SubscribeMessage;
import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.navigation.Msg2DPoint;
import cn.autolabor.plugin.gazebo.ignitionmsgs.MarkerProtos;
import cn.autolabor.plugin.gazebo.msg.MsgMarker;
import cn.autolabor.plugin.gazebo.task.GazeboMarkerServerTask;

import java.util.List;

@TaskProperties
public class DrawLidarTask extends AbstractTask {

    @InjectMessage(topic = "marker")
    private MessageHandle<byte[]> markerHandle;

    public DrawLidarTask(String... name) {
        super(name);
    }

    public static void main(String[] args) {
        ServerManager.me().register(new GazeboMarkerServerTask());
        ServerManager.me().register(new DrawLidarTask());
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
