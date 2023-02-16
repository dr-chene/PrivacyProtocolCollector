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
        device.click(930, 1730)


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
    }

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

    companion object {

        private const val LAUNCH_TIMEOUT = 5000L
        private const val TEST_LAUNCH_PACKAGE = "com.xingin.xhs"
    }
}