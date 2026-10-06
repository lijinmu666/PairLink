package com.lijinmu.pairlink

import android.os.Build
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.json.JSONObject
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class UiState {
    var server by mutableStateOf("")
    var own by mutableStateOf("")
    var peer by mutableStateOf("")
    var script by mutableStateOf("")
    var targetPackage by mutableStateOf("")
    var shizuku by mutableStateOf(false)
    var sendShizuku by mutableStateOf(false)
    var appControl by mutableStateOf(false)
    var appControlMode by mutableStateOf("device_owner")
    var privilegeStatus by mutableStateOf("")
    var privilegeMode by mutableStateOf("无特权")
    var sharing by mutableStateOf(false)
    var shell by mutableStateOf(false)
    var root by mutableStateOf(false)
    var sendRoot by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var usageAllowed by mutableStateOf(false)
    var notificationAccess by mutableStateOf(false)
    var notificationTitle by mutableStateOf("")
    var notificationContent by mutableStateOf("")
    var sendNotifications by mutableStateOf(false)
    var notifications by mutableStateOf(false)
    var connection by mutableStateOf("共享已关闭")
    var dialog by mutableStateOf<UiDialog?>(null)
    var resultPage by mutableStateOf<ResultPage?>(null)

    fun showQuery(action: String, result: JSONObject, device: String) {
        val page = queryPage(action, result, device)
        busy = false
        dialog = null
        resultPage = page
    }
    fun showRecords(logs: String, device: String) {
        dialog = null
        resultPage = ResultPage(
            "本机记录", device,
            logs.lineSequence().filter { it.isNotBlank() }.map { ResultCard("执行与连接记录", body = it) }.toList(),
            emptyMessage = "暂无记录",
        )
    }
    fun showRestored(results: JSONArray) {
        val cards = (0 until results.length()).map { index ->
            val item = results.optJSONObject(index) ?: JSONObject()
            val pkg = item.optString("package_name")
            ResultCard(
                appName(item), pkg,
                fields = listOf(
                    "结果" to if (item.has("error")) "恢复失败" else if (item.optBoolean("suspended")) "仍被暂停" else "已恢复",
                    "执行方式" to AppControl.label(item.optString("backend")),
                ),
                body = item.optString("error").ifBlank { item.optString("note") },
            )
        }
        busy = false
        dialog = null
        resultPage = ResultPage("本机应用恢复结果", own, cards, emptyMessage = "没有需要恢复的应用")
    }
    fun showResult(body: String) {
        dialog = UiDialog("执行结果", body, "关闭", null, isResult = true)
    }
    fun message(title: String, body: String) {
        dialog = UiDialog(title, body, "知道了", null)
    }
    fun confirm(title: String, body: String, button: String, action: Runnable) {
        dialog = UiDialog(title, body, button, action)
    }
}

data class UiDialog(val title: String, val body: String, val button: String, val action: Runnable?, val isResult: Boolean = false)

object PairLinkUi {
    @JvmStatic fun render(activity: MainActivity, ui: UiState) {
        activity.enableEdgeToEdge()
        activity.setContent {
            val dark = isSystemInDarkTheme()
            MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
                Screen(activity, ui)
            }
        }
    }
}

@Composable
private fun Screen(activity: MainActivity, ui: UiState) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("概览", "查询", "操作", "设置")
    val listStates = listOf(
        rememberLazyListState(),
        rememberLazyListState(),
        rememberLazyListState(),
        rememberLazyListState(),
    )
    val page = ui.resultPage
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(page) { if (page != null) keyboard?.hide() }
    BackHandler(enabled = page != null) { ui.resultPage = null }
    BackHandler(enabled = page == null && selectedTab != 0 && ui.dialog == null) { selectedTab = 0 }
    if (page != null) {
        key(page) { ResultScreen(page, onBack = { ui.resultPage = null }) }
        return
    }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = "PairLink",
                largeTitle = "PairLink",
                bottomContent = {
                    TabRow(
                        tabs = tabs,
                        selectedTabIndex = selectedTab,
                        onTabSelected = { selectedTab = it; keyboard?.hide() },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        minWidth = 56.dp,
                    )
                },
            )
        },
    ) { padding ->
        key(selectedTab) {
            LazyColumn(
                state = listStates[selectedTab],
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (selectedTab) {
                    0 -> {
                        item {
                            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
                                Text(ui.connection, color = MiuixTheme.colorScheme.primary, style = MiuixTheme.textStyles.title2)
                                Spacer(Modifier.height(8.dp))
                                Text("你的手机，与对方保持连接", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                        item {
                            Section("本机信息") {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    OverviewField("设备型号", listOf(Build.BRAND.trim(), Build.MODEL.trim()).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "未知" })
                                    OverviewField("安卓版本", Build.VERSION.RELEASE)
                                }
                            }
                        }
                        item {
                            Section("设备 ID") {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    OverviewField("本机 ID", ui.own.ifBlank { "未设置" })
                                    OverviewField("对方 ID", ui.peer.ifBlank { "未设置" })
                                }
                            }
                        }
                        item {
                            Section("共享与权限") {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    OverviewField("允许对方查询此手机", if (ui.sharing) "已开启" else "未开启")
                                    OverviewField("权限模式", ui.privilegeMode)
                                }
                            }
                        }
                    }
                    1 -> {
                        item {
                            Text("对方设备：" + ui.peer.ifBlank { "尚未配置，请前往设置页面" }, modifier = Modifier.padding(horizontal = 12.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        item {
                            Section("查询对方") {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Action("查看在线状态与电量", activity::queryStatus, enabled = !ui.busy)
                                    Action("查看过去 24 小时应用使用时长", activity::queryRecent, enabled = !ui.busy, primary = false)
                                    Action("查看已安装的用户应用", activity::queryInstalledApps, enabled = !ui.busy, primary = false)
                                    Action("查看对方当前通知", activity::queryNotifications, enabled = !ui.busy, primary = false)
                    }
                }
            }
                    }
                    2 -> {
                        item {
                            Text("对方设备：" + ui.peer.ifBlank { "尚未配置，请前往设置页面" }, modifier = Modifier.padding(horizontal = 12.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        item {
                            Section("暂停与恢复应用") {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Input(ui.targetPackage, { ui.targetPackage = it }, "对方设备中的应用包名，例如 com.example.kskblzdjd")
                                    Action("暂停", activity::suspendPeerApp, enabled = !ui.busy)
                                    Action("恢复", activity::resumePeerApp, enabled = !ui.busy, primary = false)
                    }
                }
            }
            item {
                Section("发送系统通知") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Input(ui.notificationTitle, { ui.notificationTitle = it }, "标题")
                        Input(ui.notificationContent, { ui.notificationContent = it }, "内容", singleLine = false)
                        Action("发送", activity::sendNotification, enabled = !ui.busy)
                    }
                }
            }
            item {
                Section("远程 Shell") {
                    Column(Modifier.padding(16.dp)) {
                        Input(ui.script, { ui.script = it }, "输入命令，例如 whoami", singleLine = false)
                    }
                    SwitchPreference(title = "使用对方的 root 权限", checked = ui.sendRoot, onCheckedChange = { ui.sendRoot = it; if (it) ui.sendShizuku = false })
                    SwitchPreference(title = "使用对方的 Shizuku / Sui", checked = ui.sendShizuku, onCheckedChange = { ui.sendShizuku = it; if (it) ui.sendRoot = false })
                    Column(Modifier.padding(16.dp)) {
                        Action("执行命令", activity::executeShell, enabled = !ui.busy)
                    }
                }
            }
                    }
                    3 -> {
                        item {
                            Section("连接设置") {
                                SwitchPreference(title = "允许对方查询此设备", summary = "开启后建立接收通道；暂停后停止共享", checked = ui.sharing, onCheckedChange = activity::changeSharing)
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Input(ui.server, { ui.server = it }, "服务器 HTTPS 根地址", enabled = !ui.sharing)
                                    Input(ui.own, { ui.own = it }, "自己的设备 ID", enabled = !ui.sharing)
                                    Input(ui.peer, { ui.peer = it }, "对方的设备 ID", enabled = !ui.sharing)
                                    Action("保存连接设置", activity::saveSettings, enabled = !ui.sharing)
                                    Action("测试服务器连接", activity::testConnection, enabled = !ui.busy, primary = false)
                                    Action("查看记录", activity::showLogs, primary = false)
                                    Action("清除记录", activity::clearLogs, primary = false)
                    }
                }
            }
                        item {
                            Section("执行与通知授权") {
                                SwitchPreference(title = "允许对方执行 Shell", summary = "本机主动确认后开启", checked = ui.shell, onCheckedChange = activity::changeShell)
                                SwitchPreference(title = "允许使用 Shizuku / Sui", summary = "本机确认并授权后，用于 Shell 和应用暂停", checked = ui.shizuku, onCheckedChange = activity::changeShizuku)
                                SwitchPreference(title = "允许对方使用 root", summary = "需要本机 root 管理器单独授权", checked = ui.root, onCheckedChange = activity::changeRoot)
                                SwitchPreference(title = "允许对方发送普通通知", summary = "接收方确认后才允许发送", checked = ui.sendNotifications, onCheckedChange = activity::changeSendNotifications)
                                SwitchPreference(title = "允许对方查看通知", summary = if (ui.notificationAccess) "仅在查询时读取当前通知" else "需要授予系统通知访问权限", checked = ui.notifications, onCheckedChange = activity::changeNotifications)
                                if (!ui.notificationAccess) {
                                    Column(Modifier.padding(16.dp)) {
                                        Action("开启通知访问权限", activity::notificationPermission, primary = false)
                                    }
                                }
                                if (!ui.usageAllowed) {
                                    Column(Modifier.padding(16.dp)) {
                                        Action("开启应用使用情况访问权限", activity::usagePermission, primary = false)
                                    }
                    }
                }
            }
            item {
                Section("本机应用暂停授权") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(ui.privilegeStatus)
                        val modes = listOf("device_owner", "shizuku", "root")
                        OverlayDropdownPreference(
                            title = "应用暂停执行方式",
                            summary = "本机接受暂停请求时使用",
                            items = listOf("设备所有者", "Shizuku / Sui", "root"),
                            selectedIndex = modes.indexOf(ui.appControlMode).coerceAtLeast(0),
                            onSelectedIndexChange = { index -> activity.selectAppControlMode(modes[index]) },
                        )
                        Action("查看设备所有者激活说明", activity::ownerInstructions, primary = false)
                    }
                    SwitchPreference(title = "允许对方暂停和恢复应用", summary = "默认关闭，执行方式由本机选择", checked = ui.appControl, onCheckedChange = activity::changeAppControl)
                }
            }
            item {
                Section("本机紧急恢复") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("共享关闭后也可以恢复本机由 PairLink 暂停的应用。", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        Action("恢复本机由 PairLink 暂停的应用", activity::restoreLocalApps, enabled = !ui.busy, primary = false)
                    }
                }
            }
                    }
                }
            }
        }
        val dialog = ui.dialog
        val waiting = dialog?.isResult == true && ui.busy
        OverlayDialog(
            show = dialog != null,
            title = if (waiting) "正在执行" else dialog?.title,
            onDismissRequest = { if (!waiting) ui.dialog = null },
        ) {
            if (dialog != null) {
                if (waiting) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Spacer(Modifier.height(16.dp))
                }
                key(dialog) {
                    SelectionContainer {
                        Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                            Text(dialog.body, fontFamily = if (dialog.isResult) FontFamily.Monospace else null)
                        }
                    }
                }
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (dialog.action != null) TextButton("取消", { ui.dialog = null }, Modifier.weight(1f))
                    TextButton(dialog.button, {
                        ui.dialog = null
                        dialog.action?.run()
                    }, Modifier.weight(1f), enabled = !waiting, colors = ButtonDefaults.textButtonColorsPrimary())
                }
            }
        }
    }
}

@Composable
private fun OverviewField(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
        SelectionContainer { Text(value) }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.padding(start = 12.dp), color = MiuixTheme.colorScheme.primary, style = MiuixTheme.textStyles.footnote1)
        Card(Modifier.fillMaxWidth()) { Column(content = content) }
    }
}

@Composable
private fun Input(value: String, change: (String) -> Unit, label: String, enabled: Boolean = true, singleLine: Boolean = true) {
    TextField(value = value, onValueChange = change, modifier = Modifier.fillMaxWidth(), label = label, useLabelAsPlaceholder = true, singleLine = singleLine, enabled = enabled)
}

@Composable
private fun Action(title: String, action: () -> Unit, enabled: Boolean = true, primary: Boolean = true) {
    TextButton(title, action, Modifier.fillMaxWidth(), enabled = enabled, colors = if (primary) ButtonDefaults.textButtonColorsPrimary() else ButtonDefaults.textButtonColors())
}

data class ResultCard(
    val title: String,
    val subtitle: String = "",
    val fields: List<Pair<String, String>> = emptyList(),
    val body: String = "",
)

data class ResultPage(
    val title: String,
    val device: String,
    val cards: List<ResultCard>,
    val summary: String = "",
    val note: String = "",
    val emptyMessage: String = "暂无结果",
)

private fun appName(item: JSONObject): String =
    item.optString("app_name").ifBlank { item.optString("package_name").ifBlank { "未知应用" } }

private fun timestamp(item: JSONObject, key: String): String {
    if (!item.has(key) || item.isNull(key) || item.optLong(key) <= 0) return "未知"
    return SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(item.optLong(key)))
}

private fun duration(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    val hours = seconds / 3600
    val minutes = seconds % 3600 / 60
    return buildString {
        if (hours > 0) append("${hours}小时 ")
        if (minutes > 0 || hours > 0) append("${minutes}分钟 ")
        append("${seconds % 60}秒")
    }
}

private fun queryPage(action: String, result: JSONObject, device: String): ResultPage {
    if (action == "get_status") {
        val current = result.optJSONObject("current_app") ?: JSONObject()
        val battery = result.optInt("battery_percent", -1)
        val uptime = result.optLong("uptime_ms", -1)
        val cards = listOf(
            ResultCard("设备信息", fields = listOf(
                "型号" to result.optString("device_model").ifBlank { "未知" },
                "安卓版本" to result.optString("android_version").ifBlank { "未知" },
                "API" to if (result.has("android_sdk")) result.optInt("android_sdk").toString() else "未知",
                "已开机" to if (uptime >= 0) duration(uptime) else "未知",
            )),
            ResultCard("电量", fields = listOf(
                "剩余电量" to if (battery >= 0) "${battery}%" else "未知",
                "充电状态" to if (result.optBoolean("charging")) "正在充电" else "未充电",
            )),
            ResultCard("当前屏幕应用", appName(current), fields = listOf(
                "包名" to current.optString("package_name").ifBlank { "未知" },
            )),
            ResultCard("执行授权", fields = listOf(
                "Shell" to if (result.optBoolean("shell_enabled")) "开启" else "关闭",
                "root" to if (result.optBoolean("root_enabled")) "开启" else "关闭",
            )),
        )
        return ResultPage("设备状态", device, cards, "查询时间：" + timestamp(result, "queried_at"))
    }
    if (action == "get_notifications") {
        val list = result.optJSONArray("notifications") ?: JSONArray()
        val cards = (0 until list.length()).map { index ->
            val item = list.optJSONObject(index) ?: JSONObject()
            ResultCard(
                appName(item), item.optString("package_name"),
                fields = listOf(
                    "标题" to item.optString("title").ifBlank { "无标题" },
                    "发布时间" to timestamp(item, "posted_at"),
                ) + buildList {
                    if (item.optBoolean("ongoing")) add("类型" to "常驻通知")
                    if (item.optBoolean("group_summary")) add("分组" to "分组摘要")
                    if (item.optBoolean("content_truncated")) add("内容" to "正文过长，已截断")
                },
                body = item.optString("text").ifBlank { "此通知没有可读取的正文" },
            )
        }
        return ResultPage(
            "当前通知", device, cards,
            "显示 ${list.length()} / ${result.optInt("total_count", list.length())} 条 · " + timestamp(result, "queried_at"),
            (if (result.optBoolean("truncated")) "通知或正文过多，部分内容未完整展示。\n" else "") +
                "仅显示系统允许读取的内容；自定义通知或敏感通知可能没有正文。",
            "当前没有通知",
        )
    }
    val recent = action == "get_recent_apps"
    val apps = result.optJSONArray("apps") ?: JSONArray()
    val cards = (0 until apps.length()).map { index ->
        val item = apps.optJSONObject(index) ?: JSONObject()
        ResultCard(
            appName(item), item.optString("package_name"),
            fields = if (recent) listOf(
                "使用时长" to if (item.has("usage_duration_ms") && !item.isNull("usage_duration_ms") && item.optBoolean("duration_known", true))
                    duration(item.optLong("usage_duration_ms")) else "未知",
                "最近打开" to timestamp(item, "last_opened_at"),
            ) else emptyList(),
        )
    }
    return ResultPage(
        if (recent) "过去 ${result.optInt("hours", 24)} 小时使用情况" else "已安装的用户应用",
        device, cards,
        "共 ${apps.length()} 个应用 · " + timestamp(result, "queried_at"),
        listOfNotNull(
            if (recent) "按前台使用时长排序；系统记录不完整时，时长可能未知或存在误差。" else null,
            if (result.optBoolean("truncated")) "列表过长，仅显示部分应用。" else null,
        ).joinToString("\n"),
        if (recent) "此时间段内暂无应用使用记录" else "没有可显示的用户应用",
    )
}

@Composable
private fun ResultScreen(page: ResultPage, onBack: () -> Unit) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = page.title,
                largeTitle = page.title,
                navigationIcon = {
                    IconButton(onClick = onBack, minWidth = 48.dp, minHeight = 48.dp) {
                        Icon(BackArrow, contentDescription = "返回", tint = MiuixTheme.colorScheme.onSurface)
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("设备：" + page.device, color = MiuixTheme.colorScheme.primary)
                    if (page.summary.isNotBlank()) Text(page.summary, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            if (page.cards.isEmpty()) {
                item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) { Text(page.emptyMessage) } }
            }
            items(page.cards) { card ->
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(card.title, style = MiuixTheme.textStyles.title2, color = MiuixTheme.colorScheme.primary)
                            if (card.subtitle.isNotBlank()) Text(card.subtitle, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            card.fields.forEach { (label, value) ->
                                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                    Text(label, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                                    Text(value)
                                }
                            }
                            if (card.body.isNotBlank()) Text(card.body)
                        }
                    }
                }
            }
            if (page.note.isNotBlank()) {
                item { Text(page.note, Modifier.padding(horizontal = 8.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
            }
        }
    }
}

private val BackArrow: ImageVector by lazy {
    ImageVector.Builder(
        name = "BackArrow",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
        autoMirror = true,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(19f, 12f)
            lineTo(5f, 12f)
            moveTo(12f, 5f)
            lineTo(5f, 12f)
            lineTo(12f, 19f)
        }
    }.build()
}
