package com.jinkeen.vtm.store.action

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Bundle
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.rxLifeScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.chad.library.adapter.base.listener.OnItemClickListener
import com.jinkeen.base.action.BaseAppCompatActivity
import com.jinkeen.base.service.BASE_URL
import com.jinkeen.base.util.*
import com.jinkeen.vtm.store.R
import com.jinkeen.vtm.store.adapter.AppsAdapter
import com.jinkeen.vtm.store.databinding.ActivityHomeLayoutBinding
import com.jinkeen.vtm.store.entity.AppInfoEntity
import com.jinkeen.vtm.store.service.NetworkApi
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rxhttp.RxHttp
import rxhttp.RxHttpPlugins
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class HomeActivity : BaseAppCompatActivity() {

    @SuppressLint("MissingSuperCall")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState, R.layout.activity_home_layout)
        BASE_URL = "https://test.jinkeen.com/publicServer/"
        this.loadingApps()
    }

    override fun getScreenWidth(): Float = 1080.0f

    override fun setupThemeStyle(s: ThemeStyle) {
        s.isNeedBaseActionbar = false
    }

    override fun initCustomToolbar(): Toolbar? {
        super.initCustomToolbar()
        return getLayoutBinding<ActivityHomeLayoutBinding>()?.included?.toolbar
    }

    private lateinit var layoutBinding: ActivityHomeLayoutBinding
    private lateinit var adapter: AppsAdapter
    private var command = Command.DOWNLOAD

    override fun setupViews(binding: ViewDataBinding?) {
        if (binding is ActivityHomeLayoutBinding) {
            layoutBinding = binding
            binding.recyclerview.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
            binding.recyclerview.addItemDecoration(object : RecyclerView.ItemDecoration() {
                override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
                    val position = parent.getChildAdapterPosition(view)
                    if (position > 0) outRect.top = 1.0f.dpToPx().toInt()
                }
            })
            adapter = AppsAdapter()
            adapter.setOnItemClickListener(itemClickListener)
            binding.recyclerview.adapter = adapter
            binding.download.throttleFirst {
                if (command == Command.OPEN) this.startActivity(packageManager.getLaunchIntentForPackage(binding.download.getTag(R.id.download_tag).toString()))
                else this.excelDownloadApk()
            }
        }
    }

    private fun loadingApps() {
        rxLifeScope.launch({
            val apps = NetworkApi.getApps().also {
                it.forEachIndexed { index, appInfoEntity -> appInfoEntity.isChecked = (index == 0) }
            }
            adapter.setNewInstance(apps)
            if (apps.isNotEmpty()) showAppDetails(apps[0])
        }, {
            showToast("获取应用列表异常")
        })
    }

    private val itemClickListener = OnItemClickListener { _, _, position ->
        this.showAppDetails(adapter.getItem(position))
    }

    private fun showAppDetails(aInfo: AppInfoEntity) {
        layoutBinding.logo.load(aInfo.logo)
        layoutBinding.aInfo = aInfo
        layoutBinding.executePendingBindings()
        this.readLocalApps(aInfo)
    }

    private val localApps = arrayListOf<App>()

    private fun readLocalApps(aInfo: AppInfoEntity) {
        layoutBinding.download.visibility = View.INVISIBLE
        layoutBinding.initContainer.visibility = View.VISIBLE
        fun compareApp() {
            // 1，在本地没有找到目标app，按钮为‘下载’
            // 2，目标版本大于本地版本，按钮为‘更新’
            // 3，目标版本等于或小于本地版本，按钮为‘打开’
            var localApp: App? = null
            localApps.forEach {
                if (aInfo.name == it.name) {
                    localApp = it
                }
            }
            localApp?.let {
                command = if (aInfo.verCode > it.vCode) Command.UPGRADE else Command.OPEN
                layoutBinding.download.setText(if (command == Command.UPGRADE) R.string.label_upgrade.string() else R.string.label_open.string())
                layoutBinding.download.setTag(R.id.download_tag, it.packname)
            } ?: kotlin.run {
                layoutBinding.download.setText(R.string.label_download.string())
                command = Command.DOWNLOAD
            }
            layoutBinding.initContainer.visibility = View.INVISIBLE
            layoutBinding.download.visibility = View.VISIBLE
        }
        if (localApps.isEmpty()) CoroutineScope(Dispatchers.IO).launch {
            try {
                val process = Runtime.getRuntime().exec("pm list package -3")
                val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String? = bufferedReader.readLine()
                while (line != null) {
                    val packInfo = packageManager.getPackageInfo(line.replace("package:", ""), PackageManager.GET_GIDS)
                    localApps.add(App(packInfo.applicationInfo.loadLabel(packageManager), packInfo.versionCode, packInfo.packageName))
                    line = bufferedReader.readLine()
                }
                withContext(Dispatchers.Main) { compareApp() }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else compareApp()
    }

    private data class App(val name: CharSequence, var vCode: Int, val packname: String)

    private fun excelDownloadApk() {
        layoutBinding.download.visibility = View.INVISIBLE
        layoutBinding.downloadContainer.visibility = View.VISIBLE
        val destFilePath = "${getExternalFilesDir("apks")?.absolutePath}${File.separator}${layoutBinding.aInfo?.name}.apk"
        File(destFilePath).apply { if (exists()) delete() }
        RxHttp.get(layoutBinding.aInfo?.downUrl)
            .asAppendDownload(destFilePath, AndroidSchedulers.mainThread()) {
                layoutBinding.downloadProgress.progress = it.progress
                layoutBinding.downloadProgressValue.text = R.string.fm_download_progress.string(it.progress)
            }.timeout(15, TimeUnit.MINUTES)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                layoutBinding.downloadProgressValue.text = R.string.label_install.string()
                installApk(it)
            }, {
                e(it, "下载异常")
                File(destFilePath).apply { if (exists()) delete() }
                layoutBinding.downloadContainer.visibility = View.INVISIBLE
                layoutBinding.download.visibility = View.VISIBLE
            })
    }

    private fun installApk(apkPath: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                /*
                 * 1.3 pm install命令
                    安装应用

                    pm install [options] <PATH>
                    其中[options]参数：

                    -r: 覆盖安装已存在Apk，并保持原有数据；
                    -d: 运行安装低版本Apk;
                    -t: 运行安装测试Apk
                    -i : 指定Apk的安装器；
                    -s: 安装apk到共享快存储，比如sdcard;
                    -f: 安装apk到内部系统内存；
                    -l: 安装过程，持有转发锁
                    -g: 准许Apk manifest中的所有权限；
                 */
                val cmd = if (command == Command.UPGRADE) "pm install -r $apkPath" else "pm install -f $apkPath"
                val process = Runtime.getRuntime().exec(cmd)
                val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
                bufferedReader.readLine()?.let { line ->
                    withContext(Dispatchers.Main) {
                        layoutBinding.downloadContainer.visibility = View.INVISIBLE
                        layoutBinding.download.visibility = View.VISIBLE
                        if (line.lowercase() == "success") {
                            command = Command.OPEN
                            layoutBinding.download.setText(R.string.label_open.string())
                            packageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_ACTIVITIES)?.let { packageInfo ->
                                layoutBinding.download.setTag(R.id.download_tag, packageInfo.packageName)
                                localApps.forEach { app ->  if (app.packname == packageInfo.packageName) app.vCode = packageInfo.versionCode }
                            }
                        } else {
                            showToast(R.string.fm_install_failure.string(line))
                            File(apkPath).apply { if (exists()) delete() }
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private enum class Command { DOWNLOAD, OPEN, UPGRADE }

    override fun onDestroy() {
        super.onDestroy()
        RxHttpPlugins.cancelAll()
    }
}