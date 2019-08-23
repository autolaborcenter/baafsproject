package cn.autolabor.baafs;

import cn.autolabor.core.annotation.SubscribeMessage;
import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskParameter;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.message.navigation.Msg2DPoint;
import cn.autolabor.message.navigation.MsgPolygon;
import cn.autolabor.module.networkhub.RemoteHub;
import cn.autolabor.module.networkhub.remote.modules.multicast.MulticastBroadcaster;
import cn.autolabor.util.autobuf.ByteBuilder;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;

@TaskProperties
public class PaintTask extends AbstractTask {
    private final MulticastBroadcaster broadcaster;   // 组播广播装置

    @TaskParameter(name = "slicerSize", value = "5000")
    private int slicerSize;

    public PaintTask(String... name) {
        super(name);
        broadcaster = RemoteHub.ME.setAndGet(new MulticastBroadcaster(slicerSize));
    }

    public static void main(String[] args) {
        FaselaseTask lidarTask = new FaselaseTask("faselase");
        System.out.println(lidarTask.name());
        ServerManager.me().register(lidarTask);
        ServerManager.me().register(new ObstacleDetectionTask());
        ServerManager.me().register(new PaintTask());
    }

    //    // | topic | 单帧 | twoDouble |
    @TaskFunction
    @SubscribeMessage(topic = "obstacle_points")
    public void paintLaser(List<Msg2DPoint> points) {
        ByteBuilder bb = new ByteBuilder();
        bb.putStringWithTag("lidar_show").putByte((byte) 0).putByte((byte) 3);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            stream.write(bb.toBytes());
            DataOutputStream s = new DataOutputStream(stream);
            points.forEach(p -> {

                        try {
                            s.writeDouble(p.getX());
                            s.writeDouble(p.getY());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        broadcaster.broadcast((byte) 6, stream.toByteArray());
        System.out.println("PrintLidar!");
    }

    // | topic | 单帧 | twoDouble |
    @TaskFunction
    @SubscribeMessage(topic = "obstacles")
    public void paintObstacle(List<MsgPolygon> obstacles) {
        ByteBuilder bb = new ByteBuilder();
        bb.putStringWithTag("obstacle_show").putByte((byte) 0).putByte((byte) 3);
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        try {
            stream.write(bb.toBytes());
            DataOutputStream s = new DataOutputStream(stream);
            obstacles
                    .stream()
                    .filter(o -> o.getPoints().size() > 1)
                    .forEach(obstacle -> {
                        obstacle
                                .getPoints()
                                .forEach(point -> {
                                    try {
                                        s.writeDouble(point.getX());
                                        s.writeDouble(point.getY());
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                });
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
        broadcaster.broadcast((byte) 6, stream.toByteArray());
        System.out.println("PrintObstacles!");
    }
}
