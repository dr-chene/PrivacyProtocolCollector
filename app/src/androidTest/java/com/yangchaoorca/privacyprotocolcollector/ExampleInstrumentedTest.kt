package com.yangchaoorca.privacyprotocolcollector

import android.content.Context
import android.text.TextUtils
import android.util.Log
import android.webkit.WebView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import org.json.JSONObject
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

    /**
     * 进入隐私协议 web 页面是通过 pressDPadDown + pressEnter 的方法实现的
     * 在进入 app 前请清除后台防止当前弹窗已获得焦点的情况
     *
     * 获取 web 文本时借助了 talkback 的功能
     * 网上文章说只要开启过 talkback，直到下次重启时均能成功获取 web 文本，但真机实测关闭 talkback 一段时间后就会失效
     * 因此建议测试前开始一次 talkback，或直接开启 talkback 运行程序
     */
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
        val cantEnter = FileWriter(File(storeBaseLocation + "CantEnter.txt").apply {
            if (!exists()) {
                createNewFile()
            }
        })
        val collectResult = FileWriter(File(storeBaseLocation + "CollectResult.txt").apply {
            if (!exists()) {
                createNewFile()
            }
        })
        try {
            // 读取 app 名字文件，打开应用
            APP_NAME.splitToSequence("、").map { it.trim() }.forEachIndexed { index, appName ->
                if (!TextUtils.isEmpty(appName)) {
                    Log.d(TAG, "开始收集：appName = $appName")
                    var isOpen = true
                    val icon = device.findObject(UiSelector().text(appName))
                    if (icon.exists() && icon.isClickable) {
                        icon.clickAndWaitForNewWindow()
                        // 所有APP在首次打开时，都必须通过弹窗等显著方式向用户展示隐私协议内容
                        // 因此启动应用后直接读取即可
                        readText(
                            device,
                            appName,
                            "$screenshotPath${appName}_$time.jpg",
                            collectResult
                        )
                    } else {
                        isOpen = false
                        // 收集进入失败的应用名，后续进行手动分析
                        Log.d(TAG, "未找到应用图标，appName = $appName, index = $index")
                        cantEnter.appendLine("appName = $appName, index = $index")
                    }

                    Log.d(TAG, "收集结束，回到桌面")
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
        } catch (e: Exception) {
            Log.d("runtime_error", e.message ?: "")
        } finally {
            // 关闭文件流
            cantEnter.flush()
            cantEnter.close()
            collectResult.flush()
            collectResult.close()
        }
    }

    private fun readText(
        device: UiDevice,
        appName: String,
        filePath: String,
        out: FileWriter
    ): Boolean {
        // 某些应用首次进入会申请权限，直接拒绝
        val rejectButton = device.findObject(UiSelector().text("拒接"))
        if (rejectButton.exists() && rejectButton.isClickable) {
            rejectButton.click()
        }
        // 用户协议、隐私政策、青少年xxx
        val textView =
            device.findObject(UiSelector().textContains("《").className(TextView::class.java))
        if (textView.exists()) {
            val privacyInfo = PrivacyInfo(appName, textView.text)
            // 多数协议都是以可点击的SpannableString的形式展示在弹窗中的，需要点击进入WebView才能获取到完整的协议
            device.pressDPadCenter() // 获取文本焦点
            // 市面上 app 以书名号 + 用户协议、隐私政策为主流
            // 为了简便，本程序也主要处理这种情况
            for (i in 0..1) {
                if (device.performActionAndWait({
                        device.pressDPadDown()
                        device.pressEnter()
                    }, Until.newWindow(), WAIT_FOR_WINDOW_TIMEOUT)) {
                    val webView =
                        device.findObject(UiSelector().className(WebView::class.java).instance(1))
                    if (webView.exists()) {
                        val sb = StringBuilder()
                        readTextDeeply(webView, sb)

                        privacyInfo.put(i, sb.toString())
                    } else {
                        Log.d("runtime_error", "进入详情页后没有找到web")
                    }
                    device.pressBack()
                } else {
                    break
                }
            }
            privacyInfo.toJson().let {
                // 保存协议文本信息
                out.appendLine(it)
//                Log.d(TAG, "收集的协议信息：$it")
            }
            return true
        } else {
            Log.d(TAG, "$appName 获取隐私协议失败，详情见截图，$filePath")
            // TODO: 某些应用（抖音）进入应用后弹出隐私协议较慢，需要处理。目前未处理，不收集大厂 app
            // 保存截图，用于后续分析
            device.takeScreenshot(File(filePath))
            return false
        }
    }

    /**
     * 递归读取 WebView 协议文本
     */
    private fun readTextDeeply(view: UiObject, sb: StringBuilder) {
//        Log.d(TAG, "当前 UiObject childCount = ${view.childCount}")
        for (i in 0 until view.childCount) {
            val subView = view.getChild(UiSelector().index(i))
            if (subView.exists()) {
                if (subView.text.isNotEmpty()) {
                    sb.append(subView.text).append('\n')
                }
                readTextDeeply(subView, sb)
            }
        }
    }

    companion object {

        private const val TAG = "runtime_log"

        // 按home键回到首页，滑动到存放测试app页所需的次数
        private const val APP_SWIPE = 3

        // 每页存放的测试 app 总数，依靠该数量换页。相应的，app 名字也需要以该数字为界
        private const val APP_PAGE_COUNT = 30

        private const val WAIT_FOR_WINDOW_TIMEOUT = 5500L
    }

    private class PrivacyInfo(val name: String, val text: String) {
        private lateinit var userAgreement: String
        private lateinit var privacyPolicy: String

        fun put(index: Int, value: String) {
            when (index) {
                0 -> userAgreement = value
                1 -> privacyPolicy = value
            }
        }

        fun toJson(): String {
            return JSONObject().apply {
                put("appName", name)
                put("info", text)
                put("userAgreement", userAgreement)
                put("privacyPolicy", privacyPolicy)
            }.toString()
        }
    }
}