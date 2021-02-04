package cn.autolabor.amcl

import cn.autolabor.locator.Mixer
import org.mechdancer.ClampMatcher
import org.mechdancer.algebra.function.vector.*
import org.mechdancer.algebra.implement.vector.Vector2D
import org.mechdancer.algebra.implement.vector.Vector3D
import org.mechdancer.algebra.implement.vector.vector2DOfZero
import org.mechdancer.common.Odometry
import org.mechdancer.common.Stamped
import org.mechdancer.common.toPose
import org.mechdancer.common.toTransformation
import org.mechdancer.geometry.angle.Angle
import org.mechdancer.geometry.angle.adjust
import org.mechdancer.geometry.transformation.Transformation
import kotlin.math.*
import kotlin.random.Random

class AMCLFilter(
    private val initWaitNumber: Int,
    minCount: Int,
    maxCount: Int,
    private val tagPosition: Vector2D,
    private val dThresh: Double = 0.1,
    private val aThresh: Double = 20 * PI / 180,
    private val alpha1: Double = 0.5, // 角速度对角度速影响
    private val alpha2: Double = 0.5, // 线速度对角速度影响
    private val alpha3: Double = 0.03, // 线速度对线速度影响
    private val alpha4: Double = 0.01, // 角速度对线速度影响
    private val weightSigma: Double = 0.1
) : Mixer<Stamped<Odometry>, Stamped<Vector2D>, Stamped<Odometry>> {
    private val matcher = ClampMatcher<Stamped<Odometry>, Stamped<Vector2D>>(true)

    val pf = PFInfo(minSamples = minCount, maxSamples = maxCount)

    private var initFlag = false
    private var initCount = 0
    private var initPosition = vector2DOfZero()
    private var initCov = vector2DOfZero()

    private var lastUpdateOdom = Odometry.pose()
    private var odom2baselinkTrans =
        Stamped(-1L, Odometry.pose().toTransformation())
    private var map2odomTrans =
        Stamped(-1L, Odometry.pose().toTransformation())

    private fun Double.adjust() = Angle(this).adjust().value
    private fun Vector3D.toTrans() = Transformation.fromPose(Vector2D(this.x, this.y), Angle(this.z))
    private fun Transformation.toPoseVec(): Vector3D = this.toPose().let { Vector3D(it.p.x, it.p.y, it.d.value) }

    override fun measureMaster(item: Stamped<Odometry>): Stamped<Odometry>? {
        val (t, pose) = item
        matcher.add1(item)
        synchronized(pf) {
            odom2baselinkTrans = Stamped(t, pose.toTransformation())
            if (initFlag && needUpdate(item.data)) {
                updateAction(item.data) // 所有粒子按照里程计推演
                lastUpdateOdom = item.data
            }
        }
        return get(item)
    }

    override fun measureHelper(item: Stamped<Vector2D>) {
        matcher.add2(item)
        synchronized(pf) {
            if (!initFlag && initParticles(item.data))
                initFlag = true
            if (initFlag) {
                updateWeight(pf, item) // 更新权重
                if (needResample()) {
                    updateResample(pf) // 对粒子进行重采样
                    updateTrans(pf)
                }
            }
        }
    }

    override fun get(item: Stamped<Odometry>): Stamped<Odometry>? {
        return Stamped(item.time, (map2odomTrans.data * item.data.toTransformation()).toPose())
    }

    private fun initRandSample(mean: Vector2D, std: Vector2D) =
        Vector3D(x = randomGaussian(mean.x, std.x),
                 y = randomGaussian(mean.y, std.y),
                 z = Random.nextDouble(-PI, PI))

    private fun initSample(pf: PFInfo, positionMean: Vector2D, cov: Vector2D, tagPosition: Vector2D): Unit {
        pf.set.kdTree.clear()
        val std = Vector2D(sqrt(cov.x), sqrt(cov.y))
        val tagPositionInverse = Transformation.fromPose(-tagPosition, Angle(0.0))
        repeat(pf.maxSamples) {
            val sample =
                (initRandSample(positionMean, std).toTrans() * tagPositionInverse)
                    .toPoseVec() to 1.0 / pf.maxSamples
            pf.set.samples.add(sample)
            pf.set.kdTree.insert(sample.particle, sample.weight)
        }
        clusterStats(pf)
        pf.wSlow = 0.0
        pf.wFast = 0.0
        pf.converged = false
        pf.set.converged = false
    }

    private fun initParticles(item: Vector2D) =
        (initCount >= initWaitNumber).also {
            if (it)
                initSample(pf, initPosition, initCov + Vector2D(1.0, 1.0), tagPosition)
            else {
                initPosition = (initPosition * initCount + item) / (initCount + 1)
                initCov = (initCov * initCount + initPosition * initPosition - initPosition) / (initCount + 1)
                ++initCount
            }
        }

    // 重采样
    private var resampleCount = 0

    private fun needResample() =
        (++resampleCount == 2).also { if (it) resampleCount = 0 }

    private fun needUpdate(odom: Odometry): Boolean {
        val (p, d) = odom.minusState(lastUpdateOdom)
        return p.norm() > dThresh || abs(d.adjust().value) > aThresh
    }

    // 每个粒子按照运动模型演变
    private fun updateAction(odom: Odometry) {
        val diff = odom.minusState(lastUpdateOdom)
        val deltaTrans = diff.p.norm(2)
        val deltaRot1 = if (deltaTrans < 0.01) 0.0 else atan2(diff.p.y, diff.p.x)
        val deltaRot2 = (diff.d.value - deltaRot1).adjust()
        val deltaRot1Noise = min(abs(deltaRot1), abs((deltaRot1 - PI).adjust()))
        val deltaRot2Noise = min(abs(deltaRot2), abs((deltaRot2 - PI).adjust()))
        pf.set.samples = pf.set.samples.map { (v, w) ->
            val deltaRot1Hat =
                (deltaRot1 - randomGaussian(
                        sqrt(alpha1 * deltaRot1Noise * deltaRot1Noise
                             + alpha2 * deltaTrans * deltaTrans)
                )).adjust()
            val deltaTransHat =
                deltaTrans - randomGaussian(
                        sqrt(alpha3 * deltaTrans * deltaTrans
                             + alpha4 * deltaRot1Noise * deltaRot1Noise
                             + alpha4 * deltaRot2Noise * deltaRot2Noise)
                )
            val deltaRot2Hat =
                (deltaRot2 - randomGaussian(
                        sqrt(alpha1 * deltaRot2Noise * deltaRot2Noise
                             + alpha2 * deltaTrans * deltaTrans)
                )).adjust()

            Vector3D(x = v.x + deltaTransHat * cos(v.z + deltaRot1Hat),
                     y = v.y + deltaTransHat * sin(v.z + deltaRot1Hat),
                     z = v.z + deltaRot1Hat + deltaRot2Hat) to w
        }.toMutableList()
    }

    // 根据观测修改每个粒子权重
    private fun updateWeight(pf: PFInfo, position: Stamped<Vector2D>): Unit {
        // println("updateWeight")
        val tagPosition = Transformation.fromPose(tagPosition, Angle(0.0))
        val p1 = 1 / sqrt(2 * PI) / weightSigma
        val p2 = -1.0 / 2 / (weightSigma * weightSigma)
        var totalWeight = 0.0
        for (sample in pf.set.samples) {
            val diff = (sample.particle.toTrans() * tagPosition).toPose().p - position.data
            val p = p1 * exp((diff.x * diff.x + diff.y * diff.y) * p2)
            sample.weight *= p
            totalWeight += sample.weight
        }
        for (sample in pf.set.samples)
            sample.weight /= totalWeight
        val wAvg = totalWeight / pf.set.samples.size
        pf.wSlow = if (pf.wSlow == 0.0) wAvg else pf.wSlow + pf.alphaSlow * (wAvg - pf.wSlow)
        pf.wFast = if (pf.wFast == 0.0) wAvg else pf.wFast + pf.alphaFast * (wAvg - pf.wFast)
        // println("wfast / wslow : ${pf.wFast / pf.wSlow}")
    }

    // 对粒子进行重采样
    private fun updateResample(pf: PFInfo) {
        val tmpSet = PFSampleSet(pf.maxSamples)
        val c = mutableListOf(0.0).apply {
            pf.set.samples.forEach { this.add(this.last() + it.weight) }
        }
        val wDiff = max(1.0 - pf.wFast / pf.wSlow, 0.0)
        val pdf = Pdf(pf.set.mean, pf.set.cov)
        var total = 0.0
        for (i in 0 until pf.maxSamples) {
            val pose =
                if (Random.nextDouble() > wDiff)
                    Random.nextDouble()
                        .let { r -> c.indexOfLast { it <= r } }
                        .let { pf.set.samples[it].particle }
                else gaussianSample(pdf)
            tmpSet.samples.add(pose to 1.0)
            tmpSet.kdTree.insert(pose, 1.0)
            total += 1.0
            if (tmpSet.samples.size > resampleLimit(pf, tmpSet.kdTree.leavesCount))
                break
        }

        if (wDiff > 0.0) {
            pf.wSlow = 0.0
            pf.wFast = 0.0
        }

        for (it in tmpSet.samples)
            it.weight /= total

        pf.set = tmpSet
        clusterStats(pf)
        updateConverged(pf)
    }

    // 判断粒子是否收敛
    private fun updateConverged(pf: PFInfo): Unit {
        var meanX = .0
        var meanY = .0
        for (sample in pf.set.samples) {
            meanX += sample.particle.x
            meanY += sample.particle.y
        }
        meanX /= pf.set.samples.size
        meanY /= pf.set.samples.size

        for (sample in pf.set.samples)
            if (sample.particle.x - meanX > pf.distThreshold
                || sample.particle.y - meanY > pf.distThreshold
            ) {
                pf.set.converged = false
                pf.converged = false
                return
            }
        pf.set.converged = true
        pf.converged = true
    }

    private fun updateTrans(pf: PFInfo) {
        pf.set.clusters
            .maxByOrNull { (_, cluster) -> cluster.weight }
            ?.takeIf { it.value.weight > 0 }
            ?.apply {
                map2odomTrans = Stamped(
                        System.currentTimeMillis(),
                        this.value.mean.toTrans() * lastUpdateOdom.toTransformation().inverse())
            }
    }

    // 重采样实际粒子数限制
    private fun resampleLimit(pf: PFInfo, k: Int): Int {
        if (k <= 1) return pf.maxSamples
        val a = 1
        val b = 2.0 / (9.0 * (k - 1))
        val c = sqrt(b) * pf.popZ
        val x = a - b + c
        val n = ceil((k - 1) / (2 * pf.popErr) * x * x * x)
        return when {
            n < pf.minSamples -> pf.minSamples
            n > pf.maxSamples -> pf.maxSamples
            else              -> n.toInt()
        }
    }
}
