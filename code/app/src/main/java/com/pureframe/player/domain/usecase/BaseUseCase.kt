package com.pureframe.player.domain.usecase

import kotlinx.coroutines.flow.Flow

/**
 * UseCase 基类
 * 
 * 定义 UseCase 的基本结构，遵循 Clean Architecture 原则：
 * - 每个 UseCase 只负责一个业务操作
 * - UseCase 不直接依赖 UI 或数据层实现细节
 * - 通过 Repository 接口访问数据
 */

/**
 * 无参数的 Flow UseCase
 */
abstract class FlowUseCase<out T> {
    abstract operator fun invoke(): Flow<T>
}

/**
 * 带参数的 Flow UseCase
 */
abstract class ParamFlowUseCase<in P, out T> {
    abstract operator fun invoke(params: P): Flow<T>
}

/**
 * 无参数的 suspend UseCase（返回结果）
 */
abstract class SuspendUseCase<out T> {
    abstract suspend operator fun invoke(): T
}

/**
 * 带参数的 suspend UseCase（返回结果）
 */
abstract class ParamSuspendUseCase<in P, out T> {
    abstract suspend operator fun invoke(params: P): T
}

/**
 * 带参数的 suspend UseCase（无返回值）
 */
abstract class ParamSuspendActionUseCase<in P> {
    abstract suspend operator fun invoke(params: P)
}

/**
 * 无参数的 suspend UseCase（无返回值）
 */
abstract class SuspendActionUseCase {
    abstract suspend operator fun invoke()
}