package cn.autolabor.baafs;

import cn.autolabor.FilterTwistTask;
import cn.autolabor.ObstacleDetectionTask;
import cn.autolabor.PoseDetectionTask;
import cn.autolabor.core.server.ServerManager;

public class ObstacleDetectionTest {


    public static void main(String[] args) {
        ServerManager.me().loadConfig("conf/obstacle.conf");
        ServerManager.me().register(new ObstacleDetectionTask("ObstacleDetectionTask"));
        ServerManager.me().register(new PoseDetectionTask("PoseDetectionTask"));
        ServerManager.me().register(new FilterTwistTask("FilterTwistTask"));
    }
}
