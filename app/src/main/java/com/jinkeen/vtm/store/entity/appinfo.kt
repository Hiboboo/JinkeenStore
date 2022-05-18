package com.jinkeen.vtm.store.entity

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AppInfoEntity(
    val appInfoId: Int,
    val name: String,
    val verDesc: String,
    val verName: String,
    val verCode: Int,
    val logo: String,
    val downUrl: String,
    val updateTimeStr: String,
    val createTimeStr: String,
    var isChecked: Boolean = false
): Parcelable