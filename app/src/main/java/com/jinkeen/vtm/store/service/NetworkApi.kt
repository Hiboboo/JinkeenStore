package com.jinkeen.vtm.store.service

import com.jinkeen.vtm.store.entity.AppInfoEntity
import rxhttp.RxHttp
import rxhttp.toResponse

object NetworkApi {

    suspend fun getApps(): MutableList<AppInfoEntity> = RxHttp.get("vtmAppInfo/selectVtmAppInfos")
        .toResponse<MutableList<AppInfoEntity>>()
        .await()
}