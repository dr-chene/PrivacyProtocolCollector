package com.yangchaoorca.privacyprotocolcollector

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.text.TextUtils
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiSelector
import org.junit.Test
import org.junit.runner.RunWith

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

        // 读取 app 名字文件，打开应用
        APP_NAME.splitToSequence("、").map { it.trim() }.forEachIndexed { index, appName ->
            if (!TextUtils.isEmpty(appName)) {
                var isOpen = true
                try {
                    device.findObject(UiSelector().text(appName)).click()
                } catch (e: Exception) {
                    println("找不到应用，${e.message}")
                    isOpen = false
                }

                // TODO: 获取隐私协议，包括进应用弹和进应用不弹，以及部分需要点击隐私协议里的文本进行二次跳转
                // 首次进入应用就弹隐私协议的情况
                val textView = device.findObject(
                    UiSelector().textContains("协议").className("android.widget.TextView")
                )
                try {
                    println(textView.text)
                } catch (e: Exception) {
                    println("进入应用不弹隐私协议，需要手动获取弹出，${e.message}")
                }

                // 找到并打开应用后点击 home 键回到首页
                if (isOpen) {
                    device.pressHome()
                }
                // 收集完当前页面应用的隐私协议，滑到下一个页面
                if (index != 0 && index % (APP_PAGE_COUNT - 1) == 0) {
                    swipeToLeft()
                }
            }
        }
    }

    companion object {

        // 按home键回到首页，滑动到存放测试app页所需的次数
        private const val APP_SWIPE = 1
        // 每页存放的测试 app 总数，依靠该数量换页。相应的，app 名字也需要以该数字为界
        private const val APP_PAGE_COUNT = 2
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