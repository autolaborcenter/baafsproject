package cn.autolabor.baafs;

import cn.autolabor.core.server.ServerManager;

public class ParticleFilterTest {
    public static void main(String[] args) {
        System.out.println("HELLO WORLD");
        ServerManager.me().register(
            new ParticleFilterTask(128, "abs", "odom", "fusion"));
        System.out.println("HELLO WORLD");
    }
}
