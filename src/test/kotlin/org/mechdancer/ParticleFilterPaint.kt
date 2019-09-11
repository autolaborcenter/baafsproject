package org.mechdancer

import cn.autolabor.pm1.sdk.PM1
import org.mechdancer.modules.LocatorModule

fun main() {
    // 定位模块
    LocatorModule().use {
        // launch pm1
        PM1.initialize()
        PM1.locked = false
        PM1.setCommandEnabled(false)
        // launch marvelmind
        it.marvelmindBlockTask()
    }
}
