package org.mechdancer.core

import org.mechdancer.geometry.transformation.Pose2D

sealed class LocalPath {
    class Path(val path: Sequence<Pose2D>) : LocalPath()
    class KeyPose(val pose: Pose2D) : LocalPath()
    object Finish : LocalPath()
    object Failure : LocalPath()
}

interface Planner<T : Any> {
    suspend fun plan(pose: Pose2D): T?
}

interface GlobalPlanner {
    suspend fun plan(pose: Pose2D): LocalPath
}

interface LocalPlanner {
    suspend fun plan(path: LocalPath): LocalPath
}

interface ActionPlanner<T : Any> {
    suspend fun plan(path: LocalPath): T?
}
