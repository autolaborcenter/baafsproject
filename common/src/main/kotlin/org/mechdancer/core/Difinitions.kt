package org.mechdancer.core

import org.mechdancer.common.Odometry

sealed class LocalPath {
    class Path(val path: Sequence<Odometry>) : LocalPath()
    class KeyPose(val pose: Odometry) : LocalPath()
    object Finish : LocalPath()
    object Failure : LocalPath()
}

interface Planner<T : Any> {
    suspend fun plan(pose: Odometry): T?
}

interface GlobalPlanner {
    suspend fun plan(pose: Odometry): LocalPath
}

interface LocalPlanner {
    suspend fun plan(path: LocalPath): LocalPath
}

interface ActionPlanner<T : Any> {
    suspend fun plan(path: LocalPath): T?
}
