package cn.autolabor.baafs;

import cn.autolabor.core.annotation.SubscribeMessage;
import cn.autolabor.core.annotation.TaskFunction;
import cn.autolabor.core.annotation.TaskProperties;
import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.executor.AbstractTask;
import cn.autolabor.message.navigation.Msg2DOdometry;

@TaskProperties
public class PrintTask extends AbstractTask {

    public static void main(String[] args) {
        ServerManager.me().register(new PrintTask());
    }

    @SubscribeMessage(topic = "cmdvel")
    @TaskFunction
    public void print(Msg2DOdometry odometry) {
        System.out.println(odometry);
    }

}
