package com.yangchaoorca.privacyprotocolcollector

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.text.TextUtils
import android.util.Log
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Test
    fun collectPrivacyProtocolText() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // 回到桌面，滑动到我们预先放置测试 app 的页面
        device.pressHome()
        device.pressHome() // 防止不在桌面的情况
        val width = device.displayWidth
        val height = device.displayHeight
        // 局部函数，向右滑
        val swipeToLeft = {
            device.swipe(width / 4 * 3, height / 2, width / 4, height / 2, 5)
        }
        for (i in 0 until APP_SWIPE) {
            swipeToLeft()
        }

        val storeBaseLocation = ApplicationProvider.getApplicationContext<Context>()
            .getExternalFilesDir(null)?.absolutePath + "/"
        val time = LocalDateTime.now().toString()

        val screenshotPath = storeBaseLocation + "screenshot" + File.separator
        Log.d(TAG, "运行输出基本存储路径 $storeBaseLocation")
        val cantEnter = FileWriter(File(storeBaseLocation + "cant_enter_app.txt").apply {
            if (!exists()) {
                createNewFile()
            }
        })
        // 读取 app 名字文件，打开应用
        APP_NAME.splitToSequence("、").map { it.trim() }.forEachIndexed { index, appName ->
            if (!TextUtils.isEmpty(appName)) {
                var isOpen = true
                try {
                    val icon = device.findObject(UiSelector().text(appName))
                    if (icon.exists() && icon.isClickable) {
                        icon.clickAndWaitForNewWindow()
                        // 所有APP在首次打开时，都必须通过弹窗等显著方式向用户展示隐私协议内容
                        // 因此启动应用后直接读取即可
                        readText(device, appName, "$screenshotPath${appName}_$time.jpg")
                    } else {
                        isOpen = false
                        // 收集进入失败的应用名，后续进行手动分析
                        Log.d(TAG, "未找到应用图标，appName = $appName, index = $index")
                        cantEnter.appendLine("appName = $appName, index = $index")
                    }
                } catch (e: Exception) {
                    Log.d("runtime_error", e.message ?: "")
                }

                // 找到并打开应用后点击 home 键回到首页
                if (isOpen) {
                    device.pressHome()
                }
                // 收集完当前页面应用的隐私协议，滑到下一个页面
                if ((index + 1) % APP_PAGE_COUNT == 0) {
                    swipeToLeft()
                }
            }
        }
        // 关闭文件流
        cantEnter.flush()
        cantEnter.close()
    }

    private fun readText(device: UiDevice, appName: String, filePath: String): Boolean {
        // 某些应用首次进入会申请权限，直接拒绝
        val rejectButton = device.findObject(UiSelector().text("拒接"))
        if (rejectButton.exists() && rejectButton.isClickable) {
            rejectButton.click()
        }
        // 用户协议、隐私政策、青少年xxx
        val textView =
            device.findObject(UiSelector().textContains("协议").className(TextView::class.java))
        if (textView.exists()) {
            val privacyInfo = PrivacyInfo(appName, textView.text)
            // TODO: 收集额外的协议信息，例如协议文本中可点击的文本信息
            // 多数协议都是以可点击的SpannableString的形式展示在弹窗中的，需要点击进入WebView才能获取到完整的协议

            // TODO: 保存协议文本信息
            return true
        } else {
            Log.d(TAG, "$appName 获取隐私协议失败，详情见截图，$filePath")
            // TODO: 某些应用（抖音）进入应用后弹出隐私协议较慢，需要处理
            // 保存截图，用于后续分析
            device.takeScreenshot(File(filePath))
            return false
        }
    }

    companion object {

        private const val TAG = "runtime_log"

        // 按home键回到首页，滑动到存放测试app页所需的次数
        private const val APP_SWIPE = 3

        // 每页存放的测试 app 总数，依靠该数量换页。相应的，app 名字也需要以该数字为界
        private const val APP_PAGE_COUNT = 3
    }

    private data class PrivacyInfo(val name: String, val text: String) {
        val extraDataMap = mutableMapOf<String, String>()

        fun put(key: String, value: String) {
            extraDataMap[key] = value
        }
    }

//        val context = ApplicationProvider.getApplicationContext<Context>()

//        val launcherPackage: String = getLauncherPackageName(context) ?: device.launcherPackageName
//        assertNotNull(launcherPackage)
//        device.wait(
//            Until.hasObject(By.pkg(launcherPackage).depth(0)),
//            LAUNCH_TIMEOUT
//        )

//        getThirdPackagesName(context).forEach {
//            println(it)
////            val intent = getAppOpenIntentByPackageName(context, it)
////            assertNotNull(intent)
////            context.startActivity(intent)
//
//            device.executeShellCommand()
//
//            device.wait(
//                Until.hasObject(By.pkg(it).depth(0)),
//                LAUNCH_TIMEOUT
//            )
//
////            val textView = device.findObject(UiSelector().textContains("协议").className("android.widget.TextView"))
////            println(textView.text)
//        }

    // 获取安装的三方app或服务的包名
    private fun getThirdPackagesName(context: Context) =
        context.packageManager.getInstalledPackages(0)
            .filter {
                (it.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0
            }.map {
                it.packageName
            }

    private fun getLauncherPackageName(context: Context): String? {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo =
            context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName
    }

    private fun getAppOpenIntentByPackageName(context: Context, packageName: String): Intent? {
        // MainActivity完整名
        var mainAct: String? = null
        // 根据包名寻找MainActivity
        val pkgMag = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN)
        intent.addCategory(Intent.CATEGORY_LAUNCHER)
        intent.flags = Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED or Intent.FLAG_ACTIVITY_NEW_TASK
        val list = pkgMag.queryIntentActivities(intent, PackageManager.GET_ACTIVITIES)
        for (i in list.indices) {
            val info = list[i]
            if (info.activityInfo.packageName == packageName) {
                mainAct = info.activityInfo.name
                break
            }
        }
        if (TextUtils.isEmpty(mainAct)) {
            return null
        }
        intent.component = ComponentName(packageName, mainAct!!)
        return intent
    }
}