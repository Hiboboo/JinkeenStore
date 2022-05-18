package com.jinkeen.vtm.store

import android.app.Application
import com.jinkeen.base.action.BaseApplication
import okhttp3.OkHttpClient
import rxhttp.RxHttpPlugins
import java.util.concurrent.TimeUnit

class JKApplication : BaseApplication() {

    override fun onCreate() {
        super.onCreate()

        RxHttpPlugins.init(getDefaultOKHttpClient()).setDebug(true)
    }

    override fun getApplication(): Application = this

    /**
     * 初始化全局的唯一请求连接对象。并忽略了对`host`的验证
     * - 连接超时：1min
     * - 完成调用超时：30s
     * - 读取超时：1min
     * - 写超时：2min
     *
     * @return 返回一个新的[OkHttpClient]对象
     */
    private fun getDefaultOKHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(10, TimeUnit.MINUTES)
        .writeTimeout(10, TimeUnit.MINUTES)
        .hostnameVerifier { _, _ -> true }
        .build()
}