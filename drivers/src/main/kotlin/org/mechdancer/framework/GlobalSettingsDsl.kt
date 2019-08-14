package org.mechdancer.framework

import cn.autolabor.core.server.ServerManager
import cn.autolabor.core.server.Setup
import cn.autolabor.core.server.SystemInfoTask
import cn.autolabor.module.communication.TCPServiceSupport
import cn.autolabor.module.communication.UDPMulticastSupport
import java.util.*

class GlobalSettingsDsl {
    var id = UUID.randomUUID().toString()
    private var before = {
        ServerManager.me().register(SystemInfoTask())
        ServerManager.me().register(UDPMulticastSupport())
        ServerManager.me().register(TCPServiceSupport())

        // ServerManager.me().register(new UDPMulticastBroadcaster());
        // ServerManager.me().register(new UDPMulticastReceiver());
        // ServerManager.me().register(new TCPDialogServer());
        // TCPDialogClient.startYell();
        Unit
    }
    private var after = {
        Unit
    }

    fun before(block: () -> Unit) {
        before = block
    }

    fun after(block: () -> Unit) {
        after = block
    }

    internal fun build() {
        ServerManager.setIdentification(id)
        ServerManager.setSetup(object : Setup {
            override fun start() = before()
            override fun stop() = after()
        })
    }
}


