package cn.autolabor

import cn.autolabor.BehaviorTree.Result.*

/**
 * 行为树
 */
sealed class BehaviorTree<T> {
    /** 执行结果 */
    enum class Result { Success, Failure, Running }

    /** 执行行为 */
    abstract operator fun invoke(info: T): Result

    /** 叶子：可执行的行为 */
    abstract class Behavior<T> : BehaviorTree<T>() {
        /** 等待指定时间 */
        class Waiting<T>(private val time: Long)
            : Behavior<T>() {
            init {
                require(time > 0)
            }

            private var ending = 0L
            override fun invoke(info: T): Result {
                val now = System.currentTimeMillis()
                return when {
                    ending == 0L -> {
                        ending = now + time
                        Running
                    }
                    now < ending ->
                        Running
                    else         -> {
                        ending = 0L
                        Success
                    }
                }
            }
        }

        /** 执行特定动作 */
        class Action<T>(private val behavior: (T) -> Result)
            : Behavior<T>() {
            override fun invoke(info: T) = behavior(info)
        }
    }

    /** 枝干：选择逻辑 */
    sealed class Logic<T> : BehaviorTree<T>() {
        // 子树
        protected abstract val children: List<BehaviorTree<T>>

        /**
         * 循环执行各子树直到失败
         * 每个子树返回时都返回 [Running]
         */
        class LoopEach<T>(vararg children: BehaviorTree<T>)
            : Logic<T>() {
            override val children = children.toList()
            private var start = 0
            override fun invoke(info: T): Result =
                when (children[start](info)) {
                    Success -> {
                        ++start
                        Running
                    }
                    Failure -> {
                        start = 0
                        Failure
                    }
                    Running -> Running
                }
        }

        /**
         * 执行到有一项返回成功
         * 一个子树失败立即尝试下一个子树
         * `我不生产 [Running]，我只是 [Running] 的搬运工`
         */
        class First<T>(vararg children: BehaviorTree<T>)
            : Logic<T>() {
            override val children = children.toList()
            private var start = 0
            override fun invoke(info: T): Result {
                for (i in start until children.size) {
                    when (children[i](info)) {
                        Success -> {
                            start = 0
                            return Success
                        }
                        Failure -> Unit
                        Running -> return Running
                    }
                }
                return Failure
            }
        }

        /**
         * 执行到有一项返回成功
         * 一个子树成功立即尝试下一个子树
         * `我不生产 [Running]，我只是 [Running] 的搬运工`
         */
        class Sequence<T>(vararg children: BehaviorTree<T>)
            : Logic<T>() {
            override val children = children.toList()
            private var start = 0
            override fun invoke(info: T): Result {
                for (i in start until children.size) {
                    when (children[i](info)) {
                        Success -> Unit
                        Failure -> {
                            start = 0
                            return Failure
                        }
                        Running -> return Running
                    }
                }
                return Success
            }
        }
    }
}
