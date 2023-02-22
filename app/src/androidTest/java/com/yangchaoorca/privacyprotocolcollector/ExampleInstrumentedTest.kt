package com.yangchaoorca.privacyprotocolcollector

import android.content.Context
import android.text.TextUtils
import android.util.Log
import android.webkit.WebView
import android.widget.ScrollView
import android.widget.TextView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileWriter

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    private lateinit var device: UiDevice

    /**
     * 测试真机是否卡顿
     */
    @Test
    fun demo() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        for (i in 0..1000) {
            device.click(device.displayWidth / 2, device.displayHeight / 2)
            Log.d(TAG, "demo: $i")
        }
    }

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
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        jumpToStartPage()

        val storeBaseLocation = ApplicationProvider.getApplicationContext<Context>()
            .getExternalFilesDir(null)?.absolutePath + "/"

        Log.d(TAG, "运行输出基本存储路径 $storeBaseLocation")
        val fileInit: (String) -> FileWriter = {
            FileWriter(File(storeBaseLocation + it).apply {
                if (!exists()) {
                    createNewFile()
                }
            })
        }
        val collectFailed = fileInit("CollectFailed.txt")
        val collectSuccess = fileInit("CollectSuccess.txt")
        val halfCollect = fileInit("HalfCollect.txt")

        val halfAutoCollectList = mutableListOf<Pair<Int, String>>()
        try {
            // 读取 app 名字文件，打开应用
            APP_NAME_TEST.splitToSequence("、").map { it.trim() }.forEachIndexed { index, appName ->
                collectText(
                    index,
                    appName,
                    false,
                    halfAutoCollectList,
                    collectFailed,
                    collectSuccess
                )
            }
            Log.e(TAG, "全自动收集完成，接下来开始半自动化收集")

            // 开始收集需要半自动化收集的应用
            jumpToStartPage()
            halfAutoCollectList.forEach { (index, appName) ->
                halfCollect.appendLine(JSONObject().apply {
                    put("index", index)
                    put("appName", appName)
                }.toString())
                collectText(index, appName, true, null, collectFailed, collectSuccess)
            }
        } catch (e: Exception) {
//            Log.e("runtime_error", e.toString())
            e.printStackTrace()
        } finally {
            // 关闭文件流
            collectFailed.flush()
            collectFailed.close()
            collectSuccess.flush()
            collectSuccess.close()
            halfCollect.flush()
            halfCollect.close()
        }

        Log.d(TAG, "程序运行结束")
    }

    private fun jumpToStartPage() {
        // 回到桌面，滑动到我们预先放置测试 app 的页面
        device.pressHome()
        device.pressHome() // 防止不在桌面的情况

        for (i in 0 until APP_SWIPE) {
            swipeToLeft()
        }
    }

    private fun swipeToLeft() {
        val width = device.displayWidth
        val height = device.displayHeight
        device.swipe(width / 4 * 3, height / 2, width / 4, height / 2, 5)
    }

    private fun collectText(
        index: Int,
        appName: String,
        isHalfAuto: Boolean,
        halfList: MutableList<Pair<Int, String>>?,
        failed: FileWriter,
        success: FileWriter
    ) {
        if (!TextUtils.isEmpty(appName)) {
            Log.d(TAG, "开始收集：appName = $appName")
            var isOpen = true
            val icon = device.findObject(By.text(appName))
            if (icon != null) {
                // 可能存在桌面图标点击不了的情况
                device.performActionAndWait({
                    device.click(icon.visibleBounds.centerX(), icon.visibleBounds.centerY())
                }, Until.newWindow(), WAIT_FOR_WINDOW_TIMEOUT)

                Thread.sleep(WAIT_FOR_WINDOW_TIMEOUT)
                // 所有APP在首次打开时，都必须通过弹窗等显著方式向用户展示隐私协议内容
                // 因此启动应用后直接读取即可
                if (!readText(appName, success, isHalfAuto)) {
                    Log.e(TAG, "协议文本收集失败，appName = $appName, index = $index")
                    failed.appendLine("协议文本收集失败，appName = $appName, index = $index")
                    // 失败的原因多数是因为代码无法普遍适配开发者非常规的实现，将这些 app 收集用于后续半自动化收集
                    halfList?.add(index to appName)
                }

                Log.d(TAG, "收集结束，回到桌面")
            } else {
                isOpen = false
                // 收集进入失败的应用名，后续进行手动分析
                Log.e(TAG, "未找到应用图标，appName = $appName, index = $index")
                failed.appendLine("未找到应用图标，appName = $appName, index = $index")
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

    private fun clickTextLink(privacyInfo: PrivacyInfo): Boolean {
        var clicked = 0
        // 假设单独存放的文本链接是以书名号包围的，否则归为非常规实现

        // text 实现
        device.findObjects(By.textStartsWith("《")).forEachIndexed { index, uiObject2 ->
            if (uiObject2 != null && uiObject2.isClickable) {
                clicked++
                uiObject2.click()
                enterDetailWindow(privacyInfo, index)
            }
        }
        if (clicked <= 0) {
            // desc 实现
            device.findObjects(By.descStartsWith("《")).forEachIndexed { index, uiObject2 ->
                if (uiObject2 != null && uiObject2.isClickable) {
                    clicked++
                    uiObject2.click()
                    enterDetailWindow(privacyInfo, index)
                }
            }
        }
        return clicked > 0
    }

    private fun readText(
        appName: String,
        out: FileWriter,
        isHalfAuto: Boolean
    ): Boolean {
//        // 某些应用首次进入会申请权限，直接拒绝
//        val rejectButton = device.findObject(UiSelector().text("拒接"))
//        if (rejectButton.exists() && rejectButton.isClickable) {
//            rejectButton.click()
//        }
        // 用户协议、隐私政策、青少年xxx
        var textView = device.findObject(By.clazz(ScrollView::class.java))
        if (textView == null) {
            textView =
                device.findObject(By.textContains("《").clazz(TextView::class.java))
        }
        if (textView == null) {
            return false
        }
        val simpleInfo = StringBuilder()
        readTextDeeply(textView, simpleInfo)

        val privacyInfo = PrivacyInfo(appName, simpleInfo.toString())
        if (isHalfAuto) {
            Log.d(TAG, "半自动化收集开始，appName = $appName")
            for (i in 0..1) {
                // 操作者手动点击文本链接转到 web 页面
                device.waitForWindowUpdate(
                    device.currentPackageName,
                    2 * WAIT_FOR_WINDOW_TIMEOUT
                )
                enterDetailWindow(privacyInfo, i)
            }
        } else {
            // 多数协议都是以可点击的SpannableString的形式展示在弹窗中的，需要点击进入WebView才能获取到完整的协议
            // 单独放置的文本链接场景
            if (!clickTextLink(privacyInfo)) {
                // 标准的 ScrollView + 单 Textview + 内嵌 web 链接场景
                device.pressDPadDown() // 获取文本焦点
                // 市面上 app 以书名号 + 用户协议、隐私政策为主流
                for (i in 0..1) {
                    if (device.performActionAndWait({
                            device.pressDPadDown()
                            device.pressEnter()
                        }, Until.newWindow(), WAIT_FOR_WINDOW_TIMEOUT)) {
                        enterDetailWindow(privacyInfo, i)
                    } else {
                        break
                    }
                }
            }
        }

        return if (privacyInfo.isLegal()) {
            privacyInfo.toJson().let {
                // 保存协议文本信息
                out.appendLine(it)
//                    Log.d(TAG, "收集的协议信息：$it")
            }
            true
        } else {
            false
        }
    }

    private fun enterDetailWindow(privacyInfo: PrivacyInfo, index: Int) {
        Thread.sleep(3000)
        val webViews = device.findObjects(By.clazz(WebView::class.java))
        val detailView = if (webViews.size > 1) {
            webViews[1]
        } else {
            device.findObject(By.clazz(ScrollView::class.java))
        }

        if (detailView != null) {
            val sb = StringBuilder()
            readTextDeeply(detailView, sb)

            privacyInfo.put(index, sb.toString())
        } else {
            Log.e("runtime_error", "进入详情页后没有找到详情 view")
        }
        device.pressBack()
    }

    /**
     * 递归读取 WebView 协议文本
     */
    private fun readTextDeeply(view: UiObject2, sb: StringBuilder) {
//        Log.d(TAG, "当前 UiObject childCount = ${view.childCount}")
        view.children.forEach {
            if (!it.text.isNullOrEmpty()) {
                sb.append(it.text).append('\n')
            } else if (!it.contentDescription.isNullOrEmpty()) {
                sb.append(it.contentDescription).append('\n')
            }
            readTextDeeply(it, sb)
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
        private var userAgreement: String = ""
        private var privacyPolicy: String = ""

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

        fun isLegal(): Boolean {
            return userAgreement.isNotEmpty() && privacyPolicy.isNotEmpty()
        }
    }
}