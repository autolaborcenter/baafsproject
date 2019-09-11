package cn.autolabor.baafs;

import cn.autolabor.core.server.ServerManager;

public class ParticleFilterTest {
    public static void main(String[] args) {
        ServerManager.me().register(new ParticleFilterTask());
        System.out.println("HELLO WORLD");
    }
}
