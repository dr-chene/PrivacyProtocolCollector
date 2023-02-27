package com.yangchaoorca.privacyprotocolcollector

import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import android.webkit.WebView
import android.widget.ScrollView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.json.JSONObject
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileWriter

@RunWith(AndroidJUnit4::class)
class GetProtocol {

    private lateinit var device: UiDevice

    @Before
    fun setUp() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    }

    /**
     * 10 个一组进行收集，如果收集失败会存储在 halfSave.txt 文件下，后续以半自动的方式收集
     */
    @Test
    fun startAutoCollect() {
        val halfList = mutableListOf<String>()
        val autoCollectResult = fileInit("AutoCollect.txt")
        COLLECT_APP_LIST.splitToSequence("、").map { it.trim() }.forEach { appName ->
            Log.d(TAG, "startAutoCollect: start $appName collect")
            var isOpen = true
            val icon = device.findObject(UiSelector().text(appName))
            if (icon.exists()) {
                // 可能存在桌面图标点击不了的情况
                device.performActionAndWait({
                    device.click(icon.visibleBounds.centerX(), icon.visibleBounds.centerY())
                }, Until.newWindow(), WAIT_FOR_WINDOW_TIMEOUT)

                Thread.sleep(3000)
                val simpleInfo = StringBuilder()
                val scrollView = device.findObject(UiSelector().className(ScrollView::class.java))

                if (scrollView.exists()) {
                    readScrollView(UiScrollable(scrollView.selector), simpleInfo)
                } else {
                    val textView = device.findObject(UiSelector().textContains("《"))
                    if (textView.exists()) {
                        simpleInfo.appendLine(textView.text)
                    } else {
                        Log.e(TAG, "未找到简易协议文本，返回")
                    }
                }

                val privacyInfo = PrivacyInfo(appName, simpleInfo.toString())

                // 多数协议都是以可点击的SpannableString的形式展示在弹窗中的，需要点击进入WebView才能获取到完整的协议
                // 单独放置的文本链接场景
                for (i in 0..1) {
                    // 操作者手动点击文本链接转到 web 页面
                    Log.e(TAG, "半自动化收集开始，appName = $appName, name = ${if (i == 0) "用户协议" else "隐私政策"}")
                    device.waitForWindowUpdate(
                        device.currentPackageName,
                        2 * WAIT_FOR_WINDOW_TIMEOUT
                    )
                    enterDetailWindow(privacyInfo, i)
                }
                if (privacyInfo.isLegal()) {
                    privacyInfo.toJson().let {
                        // 保存协议文本信息
                        autoCollectResult.appendLine(it)
                    Log.d(TAG, "收集的协议信息：$it")
                    }
                } else {
                    Log.d(TAG, "startAutoCollect: ${privacyInfo.toJson()}")
                    halfList.add(appName)
                }
            } else {
                isOpen = false
                Log.d(TAG, "startAutoCollect: not find icon, appName = $appName")
            }
            // 找到并打开应用后点击 home 键回到首页
            if (isOpen) {
                device.pressHome()
            }
        }
        Log.d(TAG, "全自动收集完成")
        autoCollectResult.flush()
        autoCollectResult.close()

        val half = fileInit("halfSave.txt", true)
        halfList.forEach { half.append(it).append('、') }
        half.flush()
        half.close()
    }

    private fun enterDetailWindow(privacyInfo: PrivacyInfo, index: Int) {
        Thread.sleep(2000)
        val sb = StringBuilder()
        val webView = device.findObject(UiSelector().className(WebView::class.java).instance(1))
        if (webView.exists()) {
            readWebView(sb)
        } else {
            val scrollView = device.findObject(UiSelector().className(ScrollView::class.java))
            if (scrollView.exists()) {
                readScrollView(UiScrollable(scrollView.selector), sb)
            } else {
                Log.e(TAG, "进入详情页后没有找到详情 view")
            }
        }

        privacyInfo.put(index, sb.toString())
        device.pressBack()
    }

//    @Test
//    fun readWebViewTest() {
//        readWebView()
//    }

    private fun readWebView(sb: StringBuilder) {
        val accessInfo = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow

        val webAccessInfo = findAccessInfo(accessInfo)
//        Log.d(TAG, "readWebView: $webAccessInfo")
        if (webAccessInfo != null) {
            readWebViewText(webAccessInfo.getChild(0), sb)
//            Log.d(TAG, "readWebView: $sb")
        } else {
            Log.e(TAG, "readWebView: not find")
        }
    }

    private fun readWebViewText(root: AccessibilityNodeInfo, sb: StringBuilder) {
        for (i in 0 until root.childCount) {
            val node = root.getChild(i)
            if (!node.text.isNullOrBlank()) {
//                Log.d(TAG, "readText: $node.text")
                sb.appendLine(node.text)
            }
            readText(node, sb)
        }
    }

    @Test
    fun readScrollViewTest() {
        val scrollView = device.findObject(UiSelector().className(ScrollView::class.java))
        if (scrollView.exists()) {
            readScrollView(UiScrollable(scrollView.selector), StringBuilder())
        }
    }

    private fun readScrollView(
        view: UiScrollable,
        sb: StringBuilder,
        times: Int = view.maxSearchSwipes
    ) {
        val set = hashSetOf<String>()
        // 可能存在 scrollview 超 31页的情况，这种时候就只能靠人为修正了
        for (i in 0..times) {
            if (readTextWithSet(view, sb, set) > 0) {
                view.swipeUp(100)
                Thread.sleep(1500)
            } else {
                // 无新增文本表示已经阅读完成了，提前结束循环
                break
            }
        }
    }

    private fun readTextWithSet(view: UiObject, sb: StringBuilder, set: HashSet<String>): Int {
        var res = 0
        for (i in 0 until view.childCount) {
            val subView = view.getChild(UiSelector().index(i))
            if (subView.exists()) {
                var text = subView.text
                if (text.isNullOrBlank()) {
                    text = subView.contentDescription
                }
                if (!text.isNullOrBlank() && set.add(text)) {
                    sb.appendLine(text)
                    res++
                }
//                Log.d(TAG, "readTextWithSet: $text")
                res += readTextWithSet(subView, sb, set)
            }
        }
        return res
    }

    /**
     * 递归找到和 className 相同的节点
     */
    private fun findAccessInfo(
        root: AccessibilityNodeInfo,
        className: String = WebView::class.java.name
    ): AccessibilityNodeInfo? {
        for (i in 0 until root.childCount) {
            val n = root.getChild(i)
            if (n.className.equals(className)) {
                return n
            }
            val r = findAccessInfo(n, className)
            if (r != null) {
                return r
            }
        }
        return null
    }

    private fun readText(
        root: AccessibilityNodeInfo,
        sb: StringBuilder,
        set: HashSet<CharSequence>? = null
    ): Int {
        var read = 0
        for (i in 0 until root.childCount) {
            val node = root.getChild(i)
            var text = node.text
            if (text.isNullOrBlank()) {
                text = node.contentDescription
            }
            if (!text.isNullOrBlank() && set?.add(text) != false) {
//                Log.d(TAG, "readText: $text")
                sb.appendLine(text)
                read++
            }
            read += readText(node, sb)
        }
        return read
    }

    private fun fileInit(fileName: String, isAppend: Boolean = false): FileWriter {
        val storeBaseLocation = ApplicationProvider.getApplicationContext<Context>()
            .getExternalFilesDir(null)?.absolutePath + "/"

        Log.d(TAG, "运行输出基本存储路径 $storeBaseLocation")
        return FileWriter(File(storeBaseLocation + fileName).apply {
            if (!exists()) {
                createNewFile()
            }
        }, isAppend)
    }

    companion object {
        private const val COLLECT_APP_LIST = "衔目、艺播萝美育"
        private const val TAG = "get_protocol"
        private const val WAIT_FOR_WINDOW_TIMEOUT = 5500L

        private const val LIST_SAVE = """
            百万麒麟、不为、集溜、泉眼、衔目、云赏文化、圆点5G、Uplay钢琴、自动巴巴、艺播萝美育
            
        """
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