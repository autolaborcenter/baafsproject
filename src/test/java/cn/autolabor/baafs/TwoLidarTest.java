package cn.autolabor.baafs;

import cn.autolabor.ObstacleDetectionTask;
import cn.autolabor.core.annotation.InjectMessage;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.core.server.message.MessageHandle;
import cn.autolabor.message.sensor.MsgLidar;

@TaskProperties
public class TwoLidarTest extends AbstractTask {

    @InjectMessage(topic = "lidar_show")
    private MessageHandle<MsgLidar> lidarMessageHandle;

    public TwoLidarTest(String... name) {
        super(name);
    }

//    @SubscribeMessage(topic = "obstacle_points")
//    @TaskFunction
//    public void lidarForShow(List<Msg2DPoint> data) {
//        System.out.println(data);
//        List<Double> angles = new ArrayList<>();
//        List<Double> distances = new ArrayList<>();
//        data.forEach(i -> {
//            angles.add(Math.atan2(i.getY(), i.getX()));
//            distances.add(Math.sqrt(i.getY() * i.getY() + i.getX() + i.getX()));
//        });
//        lidarMessageHandle.pushSubData(new MsgLidar("baseLink", angles, distances));
//    }
//
//    @SubscribeMessage(topic = "scan_back_out")
//    @TaskFunction
//    public void print(MsgLidar data) {
////        System.out.println(data.getAngles());
//    }

    public static void main(String[] args) {
        ServerManager.me().loadConfig("conf/obstacle.conf");
        ServerManager.me().register(new FaselaseTask("FaselaseTaskFront"));
        ServerManager.me().register(new LaserFilterTask("LaserFilterFront"));
        ServerManager.me().register(new FaselaseTask("FaselaseTaskBack"));
        ServerManager.me().register(new LaserFilterTask("LaserFilterBack"));
        ServerManager.me().register(new ObstacleDetectionTask("ObstacleDetectionTask"));
        ServerManager.me().register(new TwoLidarTest());
//        ServerManager.me().dump();
    }
}
