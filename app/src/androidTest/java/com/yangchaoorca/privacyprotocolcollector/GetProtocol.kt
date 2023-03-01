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
                    var textView = device.findObject(UiSelector().textContains("《"))
                    if (textView.exists()) {
                        simpleInfo.appendLine(textView.text)
                    } else {
                        textView = device.findObject(UiSelector().textContains("“"))
                    }
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
                        WAIT_FOR_WINDOW_TIMEOUT
                    )
                    enterDetailWindow(privacyInfo, i)
                }
                if (privacyInfo.isLegal()) {
                    privacyInfo.toJson().let {
                        // 保存协议文本信息
                        autoCollectResult.appendLine(it)
                        autoCollectResult.flush()
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
        Thread.sleep(WAIT_FOR_WEB_LOAD)
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
        private const val COLLECT_APP_LIST = ""
        private const val TAG = "get_protocol"
        private const val WAIT_FOR_WINDOW_TIMEOUT = 5500L
        private const val WAIT_FOR_WEB_LOAD = 3000L

        private const val LIST_SAVE = """
            百万麒麟、不为、集溜、泉眼、衔目、云赏文化、圆点5G、Uplay钢琴、自动巴巴、艺播萝美育
            新四平、指尖剪辑视频编辑、iBaby Care、诺云直播、鹊华视频、微视中国、微商视频水印、艺术融媒体、牛Biu段子
            视频剪辑编辑、去水印王、高手、视界观、指尖录屏、闪视频
            音妙剪辑、播鱼、捧音、VitOS Lite、元音符、音频剪辑乐软件、UMIDIGI、美梦睡眠
            右转、Bayge、吴歌、友鼓助手、友鼓轻松学、音频剪辑神器、音频提取器编辑器、乐知海音乐
            智能音频提取器、阿贝路音乐、美得理电鼓玩家、昆曲迷、音频编辑提取器、友乐谱、评剧迷、2496、轻松睡眠、云赏HIFI、音频剪辑lab
            掘金策、钱景私人理财、东航财富、金赢掌、智汇谷、福克斯基金、执行家、薛掌柜、紫金天风期货通
            壹佰金、久阳润泉、贵文智投
            智语良投、国信期货通、浦领基金、赢基金、WGM、华西股票开户、掌上云期
            方正中期财讯通、恒泰期货开户长赢版、新华基金、淘好基金、广发期货开户投资软件、潜龙点金、乐选股、五矿证券掌上盈、湘财期权
            建仓宝、股扒扒、DCE财讯通、光大期货财讯通、融合视讯、国盛研究、U财经、经选基金
            淘牛邦、宝城期货开户交易、西南开户宝、上海证券股票开户、国联安基金、问路石、申万菱信基金、东吴开户、中原红
            湘财证券股票开户、萝卜股票、意才基金、利得基金、三立博易大师、财华财经pro、天相财富
            山羊拼团商家、之交商家版、我做东、会承网、i生态、点了码、重惠通、爱淘源、团咚咚、爱豆仓、票马网、吉吉拍
            大大买钢、登瑞坊、趣比比、满聚优选、云拼购、枞川味道、瑶光生态、贝多享、山羊拼团、21GO、e团油、酒赞
            寻根农业、久加久云商、趣拼吧、惠拼购、春辉包装、袋鼠拼客、易乎社区、柠条
            爱多多商城、蚂蚁换呗、云待商城、箱易通、推客佣金联盟、农极客、阁物、夏邑同城、有趣
            团购宝、爱茶网、物格买菜、叻叻猪、迅播、蜀信e惠生活商户、拿货、聚米团G、饷
            火听调音器、铃声秀、泊声、草根音乐、迷糊音乐播放器、中国好声音、音乐提取助手、川剧迷、节拍器专家、调音器大师、八戒机器人、55Y音乐社区、我爱弹琴、钢琴巴士、iReal Pro
            库客音乐、扬琴调音器、宝耳、ASMR、趣弹UP、中唱音乐、粤剧迷、节奏节拍器
            闪光短视频、嗨曲刷刷、MANA、我是演员、微商视频制作、Z视频、Wiseed、三农头条、悦看、一键去水印下载、艾睿热成像、悦享、视听海南、央博、ELLEone、影音视频播放器、IPC360、无障碍影视
            扬帆、趣刷刷、Ozbil、快点投屏、视频剪辑器、人民视频、福抖直播
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
                put("userAgreement", if (privacyPolicy == userAgreement) "" else userAgreement)
                put("privacyPolicy", privacyPolicy)
            }.toString()
        }

        fun isLegal(): Boolean {
            return privacyPolicy.isNotEmpty()
        }
    }
}