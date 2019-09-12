package cn.autolabor

import cn.autolabor.BehaviorTree.Result.*

/**
 * 行为树
 */
sealed class BehaviorTree {
    /** 执行结果 */
    enum class Result { Success, Failure, Running }

    /** 执行行为 */
    abstract operator fun invoke(): Result

    /** 叶子：可执行的行为 */
    sealed class Behavior : BehaviorTree() {
        /** 等待指定时间 */
        class Waiting(private val time: Long)
            : Behavior() {
            init {
                require(time > 0)
            }

            private var ending = 0L
            override fun invoke(): Result {
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
        class Action(private val behavior: () -> Result)
            : Behavior() {
            override fun invoke() = behavior()
        }
    }

    /** 枝干：选择逻辑 */
    sealed class Logic : BehaviorTree() {
        // 子树
        protected abstract val children: List<BehaviorTree>

        /** 循环执行各子树直到失败 */
        class Loop(override val children: List<BehaviorTree>)
            : Logic() {
            private var start = 0
            override fun invoke(): Result =
                when (children[start]()) {
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

        /** 执行到有一项返回成功 */
        class First(override val children: List<BehaviorTree>)
            : Logic() {
            private var start = 0
            override fun invoke(): Result {
                for (i in start until children.size) {
                    when (children[i]()) {
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

        /** 执行到有一项返回失败 */
        class Sequence(override val children: List<BehaviorTree>)
            : Logic() {
            private var start = 0
            override fun invoke(): Result {
                for (i in start until children.size) {
                    when (children[i]()) {
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
