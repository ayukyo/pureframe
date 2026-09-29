package com.pureframe.player.di

import com.pureframe.player.cast.CastController
import com.pureframe.player.cast.ContentUrlProvider
import com.pureframe.player.cast.DlnaCastController
import com.pureframe.player.cast.DlnaContentUrlProvider
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 投屏模块 DI 绑定
 *
 * 接口化架构的关键点：上层注入 CastController / ContentUrlProvider 接口，
 * 新增协议时在此追加 @Binds 即可，上层零改动。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CastModule {

    @Binds
    @Singleton
    abstract fun bindContentUrlProvider(impl: DlnaContentUrlProvider): ContentUrlProvider

    @Binds
    @Singleton
    abstract fun bindDlnaCastController(impl: DlnaCastController): CastController
}
