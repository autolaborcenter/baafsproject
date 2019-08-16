import cn.autolabor.core.server.ServerManager;
import cn.autolabor.core.server.Setup;
import cn.autolabor.core.server.SystemInfoTask;
import cn.autolabor.module.communication.TCPServiceSupport;
import cn.autolabor.module.communication.UDPMulticastSupport;

public class Main {
    public static void main(String[] args) {
        ServerManager.setSetup(new Setup() {
            @Override
            public void start() {
                ServerManager.me().register(new SystemInfoTask());
                ServerManager.me().register(new UDPMulticastSupport());
                ServerManager.me().register(new TCPServiceSupport());
            }

            @Override
            public void stop() {
            }
        });

        ServerManager.me().register(new FaselaseTask("faselase"));
    }
}
