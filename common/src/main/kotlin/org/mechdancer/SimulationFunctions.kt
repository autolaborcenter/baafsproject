package org.mechdancer

import org.mechdancer.simulation.prefabs.OneStepTransferRandomDrivingBuilderDSL.Companion.oneStepTransferRandomDriving

/** 构造新随机行驶驱动器 */
fun newRandomDriving() =
    oneStepTransferRandomDriving {
        vx(0.1) {
            row(-1, .99, .00)
            row(.00, -1, .02)
            row(.00, .01, -1)
        }
        w(0.5) {
            row(-1, .01, .01)
            row(.01, -1, .01)
            row(.01, .01, -1)
        }
    }

