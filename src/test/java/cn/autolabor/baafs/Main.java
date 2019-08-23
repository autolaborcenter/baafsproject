package cn.autolabor.baafs;

import cn.autolabor.core.server.ServerManager;

public class Main {
    public static void main(String[] args) {
        ServerManager.me().register(new FaselaseTask("faselase"));
    }
}
