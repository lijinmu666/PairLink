package com.lijinmu.pairlink

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.IntOffset
import android.os.Build
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.text.style.TextAlign
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
import top.yukonga.miuix.kmp.preference.CheckboxPreference
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class UiState {
    var server by mutableStateOf("")
    var serverVersion by mutableStateOf("未获取，点击刷新")
    var serverVersionLoading by mutableStateOf(false)
    var own by mutableStateOf("")
    var peer by mutableStateOf("")
    var script by mutableStateOf("")
    var appPicker by mutableStateOf<AppPickerPage?>(null)
    var appSearch by mutableStateOf("")
    var selectedPeerApps by mutableStateOf<Set<String>>(emptySet())
    var pauseBlacklistCount by mutableIntStateOf(0)
    var screenshotBlacklistCount by mutableIntStateOf(0)
    var screenshots by mutableStateOf(false)
    var dhizuku by mutableStateOf(false)
    var nightMode by mutableStateOf("system")
    var appControl by mutableStateOf(false)
    var appControlMode by mutableStateOf("none")
    var privilegeStatus by mutableStateOf("")
    var privilegeMode by mutableStateOf("无特权")
    var commandSummary by mutableStateOf(false)
    var summaryMinutes by mutableIntStateOf(60)
    var statusNotification by mutableStateOf(true)
    var sharing by mutableStateOf(false)
    var shell by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var usageSharing by mutableStateOf(false)
    var appListPermissionNeeded by mutableStateOf(false)
    var usageAllowed by mutableStateOf(false)
    var notificationAccess by mutableStateOf(false)
    var notificationTitle by mutableStateOf("")
    var notificationContent by mutableStateOf("")
    var sendNotifications by mutableStateOf(false)
    var notifications by mutableStateOf(false)
    var media by mutableStateOf(false)
    var connection by mutableStateOf("共享已关闭")
    var dialog by mutableStateOf<UiDialog?>(null)
    var resultPage by mutableStateOf<ResultPage?>(null)

    fun showAppPicker(result: JSONObject, server: String, sender: String, device: String, protectedPackage: String) {
        val array = result.optJSONArray("apps") ?: JSONArray()
        val apps = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val pkg = item.optString("package_name")
            if (pkg.length > 255 || !AppControl.PACKAGE.matcher(pkg).matches()) return@mapNotNull null
            PeerApp(pkg, displayAppName(item.optString("app_name"), pkg), pkg == protectedPackage,
                pausedByPairLink = item.optBoolean("paused_by_pairlink"))
        }.distinctBy { it.packageName }.sortedWith(compareBy<PeerApp> { it.name.lowercase(Locale.ROOT) }.thenBy { it.packageName })
        val old = appPicker
        val sameDevice = old != null && !old.blacklist && old.server == server && old.sender == sender && old.device == device
        val selectable = apps.filterNot { it.protectedApp }.map { it.packageName }.toSet()
        val paused = apps.filter { it.pausedByPairLink && !it.protectedApp }.map { it.packageName }.toSet()
        val previouslyPaused = old?.apps?.filter { it.pausedByPairLink }?.map { it.packageName }?.toSet().orEmpty()
        selectedPeerApps = if (sameDevice) (selectedPeerApps + (paused - previouslyPaused)).intersect(selectable) else paused
        if (!sameDevice) appSearch = ""
        appPicker = AppPickerPage(server, sender, device, apps, result.optBoolean("truncated"))
        resultPage = null
        busy = false
        dialog = null
    }
    fun selectedAppRequests(): JSONArray {
        val result = JSONArray()
        appPicker?.apps?.filter { it.packageName in selectedPeerApps && !it.protectedApp }?.forEach {
            result.put(JSONObject().put("package_name", it.packageName).put("app_name", it.name))
        }
        return result
    }
    fun showLocalBlacklist(array: JSONArray, chosen: Set<String>, device: String, protectedPackage: String, screenshot: Boolean) {
        val apps = (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val pkg = item.optString("package_name")
            if (pkg.length > 255 || !AppControl.PACKAGE.matcher(pkg).matches()) return@mapNotNull null
            PeerApp(pkg, displayAppName(item.optString("app_name"), pkg), !screenshot && pkg == protectedPackage, item.optBoolean("installed", true))
        }.distinctBy { it.packageName }.sortedWith(compareBy<PeerApp> { it.name.lowercase(Locale.ROOT) }.thenBy { it.packageName })
        val refreshing = appPicker?.blacklist == true && appPicker?.screenshotBlacklist == screenshot
        appPicker = AppPickerPage("", "", device, apps, false, blacklist = true, screenshotBlacklist = screenshot)
        selectedPeerApps = chosen.intersect(apps.filterNot { it.protectedApp }.map { it.packageName }.toSet())
        if (!refreshing) appSearch = ""
        resultPage = null
        busy = false
        dialog = null
    }
    fun clearAppPicker() {
        appPicker = null
        selectedPeerApps = emptySet()
        appSearch = ""
    }
    fun showPeerAppResults(results: JSONArray, device: String, suspended: Boolean) {
        val cards = (0 until results.length()).map { index ->
            val item = results.getJSONObject(index)
            val value = item.optJSONObject("result") ?: JSONObject()
            val status = item.optString("status")
            ResultCard(appName(item), item.optString("package_name"), fields = listOf(
                "结果" to when (status) {
                    "completed" -> if (value.optBoolean("suspended")) (if (suspended) "已暂停" else "仍被暂停") else "未暂停"
                    "not_executed" -> "未执行"
                    "uncertain" -> "执行状态未确认"
                    else -> "执行失败"
                },
            ) + if (status == "completed") listOf("执行方式" to AppControl.label(value.optString("backend"))) else emptyList(),
                body = item.optString("error").ifBlank { value.optString("note") })
        }
        busy = false
        dialog = null
        resultPage = ResultPage(if (suspended) "应用暂停结果" else "应用恢复结果", device, cards,
            "共 ${cards.size} 个应用", "操作逐个执行\n状态未确认的应用请检查后再操作；返回可继续选择应用")
    }

    fun showQuery(action: String, result: JSONObject, device: String) {
        val page = queryPage(action, result, device)
        busy = false
        dialog = null
        resultPage = page
    }
    fun showRecords(records: JSONArray, device: String, from: Long, to: Long, incomingOnly: Boolean, count: Long) {
        busy = false
        dialog = null
        val cards = (0 until records.length()).mapNotNull { records.optJSONObject(it)?.let(::recordCard) }
        val range = if (incomingOnly) "统计时段：${formatTime(from)} 至 ${formatTime(to)}，共 $count 次对方指令" else "最近 24 小时，共 ${cards.size} 条记录"
        resultPage = ResultPage(
            "连接与执行记录", device, cards, summary = range,
            note = "点击卡片查看详情\n记录仅保存在本机，保留 24 小时、最多 1000 条\n旧版记录没有指令和权限详情" +
                if (incomingOnly && count > cards.size) "\n部分详情已过期、被清除或因数量限制移除" else "",
            emptyMessage = if (incomingOnly) "此时段暂无可查看的记录，或记录已清除／过期" else "暂无记录",
            historyView = true, historyFrom = from, historyTo = to, historyIncoming = incomingOnly, historyCount = count,
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
    fun showScreenshot(result: JSONObject, image: Bitmap, device: String) {
        busy = false
        dialog = null
        appPicker = null
        resultPage = ResultPage("屏幕截图", device, listOf(
            ResultCard("对方当前屏幕", image = image),
            ResultCard("截图信息", fields = listOf(
                "截图时间" to timestamp(result, "captured_at"),
                "前台应用包名" to result.optString("foreground_package"),
                "图片尺寸" to "${result.optInt("width")} × ${result.optInt("height")}",
                "原始尺寸" to "${result.optInt("source_width")} × ${result.optInt("source_height")}",
                "执行方式" to executionIdentity(result.optString("backend"), result.optInt("shizuku_uid", -1)),
            )),
        ), note = "图片经过压缩，服务器查询结果中的图片最多保留 10 分钟")
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
            val systemDark = isSystemInDarkTheme()
            val dark = when (ui.nightMode) {
                "on" -> true
                "off" -> false
                else -> systemDark
            }
            SideEffect {
                activity.enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(0xE6FFFFFF.toInt(), 0x801B1B1B.toInt()) { dark },
                )
            }
            MiuixTheme(colors = if (dark) darkColorScheme() else lightColorScheme()) {
                Screen(activity, ui)
            }
        }
    }
}

@Composable
private fun Screen(activity: MainActivity, ui: UiState) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    val listStates = listOf(
        rememberLazyListState(),
        rememberLazyListState(),
        rememberLazyListState(),
        rememberLazyListState(),
    )
    val page = ui.resultPage
    val picker = ui.appPicker
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(page) {
        if (page?.historyView == true) selectedTab = 1
        if (page != null) keyboard?.hide()
    }
    BackHandler(enabled = page != null && !ui.busy && ui.dialog == null) { ui.resultPage = page?.parent }
    BackHandler(enabled = page == null && picker != null && !ui.busy && ui.dialog == null) { ui.appPicker = null }
    BackHandler(enabled = page == null && picker == null && selectedTab != 0 && ui.dialog == null && !ui.busy) { selectedTab = 0 }
    BackHandler(enabled = ui.busy) {}
    // The root Scaffold keeps Miuix's popup host stable across destination animations.
    // Child Scaffolds retain their own bars and insets; dialogs render in this root host.
    val destination = UiDestination(page, if (page == null) picker else null)
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { _ ->
        Box(Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = destination,
                contentKey = { it.route },
                modifier = Modifier.fillMaxSize().clipToBounds(),
                transitionSpec = { pageMotion(targetState.depth >= initialState.depth) },
                label = "页面跳转",
            ) { shown ->
                val result = shown.page
                val appPicker = shown.picker
                when {
                    result != null -> ResultScreen(activity, ui, result, onBack = {
                        if (!ui.busy && ui.resultPage === result) ui.resultPage = result.parent
                    })
                    appPicker != null -> AppPickerScreen(activity, ui, appPicker)
                    else -> MainScreen(activity, ui, selectedTab, { selectedTab = it; keyboard?.hide() }, listStates)
                }
            }
            UiDialogs(ui)
        }
    }
}

private data class UiDestination(val page: ResultPage?, val picker: AppPickerPage?) {
    val depth: Int get() = page?.let { generateSequence(it) { parent -> parent.parent }.count() } ?: if (picker != null) 1 else 0
    val route: String get() = when {
        page != null -> "result:$depth:${page.title}:${page.device}"
        picker != null -> "picker:${picker.blacklist}:${picker.screenshotBlacklist}:${picker.device}"
        else -> "main"
    }
}

// Soft spring settling with a short fade, shared by navigation and disclosure.
private fun pageMotion(forward: Boolean): ContentTransform {
    val direction = if (forward) 1 else -1
    return ((slideInHorizontally(
        animationSpec = spring<IntOffset>(dampingRatio = 0.92f, stiffness = Spring.StiffnessMediumLow),
        initialOffsetX = { direction * it / 5 },
    ) + fadeIn(tween(220))) togetherWith
        (slideOutHorizontally(
            animationSpec = spring<IntOffset>(dampingRatio = 0.92f, stiffness = Spring.StiffnessMediumLow),
            targetOffsetX = { -direction * it / 8 },
        ) + fadeOut(tween(150))))
}

@Composable
private fun Reveal(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(animationSpec = spring(dampingRatio = 0.92f, stiffness = Spring.StiffnessMediumLow), expandFrom = Alignment.Top) + fadeIn(tween(180)),
        exit = shrinkVertically(animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow), shrinkTowards = Alignment.Top) + fadeOut(tween(120)),
        label = "选项展开收起",
    ) { content() }
}

@Composable
private fun MainScreen(activity: MainActivity, ui: UiState, selectedTab: Int, selectTab: (Int) -> Unit, listStates: List<LazyListState>) {
    val tabs = listOf("概览", "查询", "操作", "设置")
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
                        onTabSelected = selectTab,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        minWidth = 56.dp,
                    )
                },
            )
        },
    ) { padding ->
        AnimatedContent(
            targetState = selectedTab,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { pageMotion(targetState > initialState) },
            label = "主页切换",
        ) { shownTab ->
            LazyColumn(
                state = listStates[shownTab],
                modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                when (shownTab) {
                    0 -> {
                        item {
                            Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
                                Text(ui.connection, color = MiuixTheme.colorScheme.primary, style = MiuixTheme.textStyles.title2)
                                Spacer(Modifier.height(8.dp))
                                Text("ฅ^-﹃-^ฅ", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                        item {
                            Card(Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    OverviewField("设备型号", listOf(Build.BRAND.trim(), Build.MODEL.trim()).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "未知" })
                                    OverviewField("安卓版本", Build.VERSION.RELEASE)
                                    val version = remember(activity) {
                                        runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }
                                            .getOrNull().orEmpty().ifBlank { "未知" }
                                    }
                                    Column(
                                        modifier = Modifier.fillMaxWidth().clickable(onClick = activity::tapVersion),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text("客户端版本号", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
                                        Text(version)
                                    }
                                    Column(
                                        modifier = Modifier.fillMaxWidth().clickable(enabled = !ui.busy, onClick = activity::refreshServerVersion),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text("服务端版本号", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
                                        Text(if (ui.serverVersionLoading) "正在刷新……" else ui.serverVersion, color = MiuixTheme.colorScheme.primary)
                                    }
                                    OverviewField("本机 ID", ui.own.ifBlank { "未设置" })
                                    OverviewField("对方 ID", ui.peer.ifBlank { "未设置" })
                                }
                            }
                        }
                        item {
                            Section("权限") {
                                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                    OverviewField("允许查询", if (ui.sharing) "已开启" else "未开启")
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
                                    Action("查看当前状态", activity::queryStatus, enabled = !ui.busy)
                                    Action("查看应用使用情况", activity::queryRecent, enabled = !ui.busy, primary = false)
                                    Action("查看应用列表", activity::queryInstalledApps, enabled = !ui.busy, primary = false)
                                    Action("查看通知列表", activity::queryNotifications, enabled = !ui.busy, primary = false)
                                    Action("查看媒体播放", activity::queryMedia, enabled = !ui.busy, primary = false)
                                    Action("查看屏幕截图", activity::queryScreenshot, enabled = !ui.busy, primary = false)
                    }
                }
            }
                        item {
                            Section("本机记录") {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Action("查看连接与执行记录", activity::showLogs, enabled = !ui.busy, primary = false)
                                    Action("清除记录", activity::clearLogs, enabled = !ui.busy, primary = false)
                                }
                            }
                        }
                    }
                    2 -> {
                        item {
                            Text("对方设备：" + ui.peer.ifBlank { "尚未配置，请前往设置页面" }, modifier = Modifier.padding(horizontal = 12.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                        }
                        item {
                            Section("暂停和恢复应用") {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("从对方的用户应用列表中搜索并勾选应用，再暂停或恢复")
                                    Action("选择对方应用", activity::openAppPicker, enabled = !ui.busy)
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
                Section("执行 Shell 指令") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Input(ui.script, { ui.script = it }, "输入 Shell 命令，例如 whoami", singleLine = false)
                        Text("执行权限跟随对方的特权工作模式；无或 Dhizuku 使用普通应用权限")
                        Action("执行", activity::executeShell, enabled = !ui.busy)
                    }
                }
            }
                    }
                    3 -> {
                        item {
                            Section("客户端偏好") {
                                val modes = listOf("system", "on", "off")
                                OverlayDropdownPreference(
                                    title = "夜间模式",
                                    items = listOf("默认", "开启", "关闭"),
                                    selectedIndex = modes.indexOf(ui.nightMode).coerceAtLeast(0),
                                    onSelectedIndexChange = { index -> activity.selectNightMode(modes[index]) },
                                )
                                SwitchPreference(title = "后台状态通知", summary = "直接开关静默连接通知；关闭后仍尝试保持连接，但失去前台保活，更容易被系统回收", checked = ui.statusNotification, onCheckedChange = activity::changeStatusNotification)
                                SwitchPreference(title = "指令定时通知", summary = "汇总对方向本机发起的指令；点按通知查看详情", checked = ui.commandSummary, onCheckedChange = activity::changeCommandSummary)
                                Reveal(ui.commandSummary) {
                                    val intervals = listOf(15, 30, 60, 120, 360, 1440)
                                    OverlayDropdownPreference(
                                        title = "通知间隔",
                                        summary = "省电机制可能延后发送；通知显示实际统计时段",
                                        items = intervals.map { SummaryWindow.label(it) },
                                        selectedIndex = intervals.indexOf(ui.summaryMinutes).coerceAtLeast(0),
                                        onSelectedIndexChange = { index -> activity.selectSummaryInterval(intervals[index]) },
                                    )
                                }
                            }
                        }
                        item {
                            Section("连接设置") {
                                SwitchPreference(title = "开启共享连接", summary = "连接后默认允许查询应用列表和状态；关闭后停止共享", checked = ui.sharing, onCheckedChange = activity::changeSharing)
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Input(ui.server, { ui.server = it }, "服务器 HTTPS 根地址", enabled = !ui.sharing)
                                    Input(ui.own, { ui.own = it }, "自己的设备 ID", enabled = !ui.sharing)
                                    Input(ui.peer, { ui.peer = it }, "对方的设备 ID", enabled = !ui.sharing)
                                    Action("保存连接设置", activity::saveSettings, enabled = !ui.sharing)
                                    Action("测试服务器连接", activity::testConnection, enabled = !ui.busy, primary = false)
                    }
                }
            }
                        item {
                            Section("权限") {
                                val modes = listOf("none", "dhizuku", "shizuku", "root")
                                OverlayDropdownPreference(
                                    title = "特权工作模式",
                                    summary = "暂停应用和 Shell 跟随所选模式；无或 Dhizuku 的 Shell 使用普通应用权限",
                                    items = listOf("无", "Dhizuku", "Shizuku / Sui", "root"),
                                    selectedIndex = modes.indexOf(ui.appControlMode).coerceAtLeast(0),
                                    onSelectedIndexChange = { index -> activity.selectAppControlMode(modes[index]) },
                                )
                                SwitchPreference(title = "允许对方查看应用使用情况", summary = "共享过去 24 小时的应用使用记录和使用时长", checked = ui.usageSharing, onCheckedChange = activity::changeUsageSharing)
                                Reveal(!ui.usageAllowed) {
                                    Column(Modifier.padding(16.dp)) {
                                        Action("开启应用使用情况访问权限", activity::usagePermission, primary = false)
                                    }
                                }
                                Text(
                                    "恢复应用需使用暂停时的权限方式；Shizuku / Sui 还需保持原来的启动身份",
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    style = MiuixTheme.textStyles.footnote1,
                                )
                                SwitchPreference(title = "允许对方查看通知列表", summary = if (ui.notificationAccess) "查询时读取当前通知" else "需要授予系统通知访问权限", checked = ui.notifications, onCheckedChange = activity::changeNotifications)
                                SwitchPreference(title = "允许对方查看媒体播放", summary = if (ui.notificationAccess) "查询系统媒体面板的曲目和播放状态" else "需要授予系统通知访问权限", checked = ui.media, onCheckedChange = activity::changeMedia)
                                SwitchPreference(title = "允许对方发送系统通知", summary = "发送普通通知", checked = ui.sendNotifications, onCheckedChange = activity::changeSendNotifications)
                                SwitchPreference(title = "允许对方屏幕截图", summary = "需 root 或 Shizuku / Sui，息屏或锁屏时拒绝", checked = ui.screenshots, onCheckedChange = activity::changeScreenshots)
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("截图黑名单已选择 ${ui.screenshotBlacklistCount} 个应用")
                                    Action("管理截图黑名单", activity::openScreenshotBlacklist, enabled = !ui.busy, primary = false)
                                }
                                SwitchPreference(title = "允许对方暂停应用", summary = "不能暂停 PairLink 本身和系统应用", checked = ui.appControl, onCheckedChange = activity::changeAppControl)
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text("已保护 ${ui.pauseBlacklistCount} 个本机应用")
                                    Action("管理暂停黑名单", activity::openPauseBlacklist, enabled = !ui.busy, primary = false)
                                    Action("恢复已暂停的应用", activity::restoreLocalApps, enabled = !ui.busy, primary = false)
                                }
                                SwitchPreference(title = "允许对方执行 Shell 指令", summary = "执行权限跟随本机特权工作模式", checked = ui.shell, onCheckedChange = activity::changeShell)
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Text(ui.privilegeStatus)
                                    Reveal(ui.appListPermissionNeeded) {
                                        Action("授予应用列表访问权限", activity::requestAppListPermission, primary = false)
                                    }
                                }
                            }
                        }

                    }
                }
            }
        }
    }
}

@Composable
private fun UiDialogs(ui: UiState) {
        val dialog = ui.dialog
        val waiting = dialog?.isResult == true && ui.busy
        OverlayDialog(
            show = dialog != null,
            title = if (waiting) "正在执行" else dialog?.title,
            onDismissRequest = { if (!waiting) ui.dialog = null },
        ) {
            if (dialog != null) {
                Reveal(waiting) {
                    Column {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(16.dp))
                    }
                }
                key(dialog) {
                    SelectionContainer {
                        Column(Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                            Text(dialog.body, modifier = Modifier.fillMaxWidth(), textAlign = if (dialog.isResult && !waiting) TextAlign.Start else TextAlign.Center,
                                fontFamily = if (dialog.isResult) FontFamily.Monospace else null)
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
    val detailFields: List<Pair<String, String>>? = null,
    val detailBody: String = "",
    val image: Bitmap? = null,
)

data class ResultPage(
    val title: String,
    val device: String,
    val cards: List<ResultCard>,
    val summary: String = "",
    val note: String = "",
    val emptyMessage: String = "暂无结果",
    val parent: ResultPage? = null,
    val historyView: Boolean = false,
    val historyFrom: Long = 0,
    val historyTo: Long = 0,
    val historyIncoming: Boolean = false,
    val historyCount: Long = -1,
)

private fun formatTime(time: Long): String =
    if (time <= 0) "未知" else SimpleDateFormat("MM-dd HH:mm:ss", Locale.CHINA).format(Date(time))

private fun commandName(action: String): String = when (action) {
    "shell" -> "执行 Shell 指令"
    "get_screenshot" -> "屏幕截图"
    "get_status" -> "查询设备状态"
    "get_installed_apps" -> "查询应用列表"
    "get_recent_apps" -> "查询应用使用情况"
    "get_notifications" -> "查询通知列表"
    "get_media" -> "查询媒体播放"
    "send_notification" -> "发送系统通知"
    "set_app_suspended" -> "暂停／恢复应用"
    else -> action.ifBlank { "未知指令" }
}

private fun recordStatus(item: JSONObject): String {
    val result = item.optJSONObject("result") ?: JSONObject()
    return when (item.optString("status")) {
        "completed" -> when {
            result.optBoolean("cancelled") -> "已取消"
            result.optBoolean("timed_out") -> "执行超时"
            result.has("exit_code") && result.optInt("exit_code") != 0 -> "执行结束（非零退出码）"
            else -> "已完成"
        }
        "failed" -> "失败／被拒绝"
        "offline" -> "对方离线，未执行"
        "uncertain" -> "执行情况未确认"
        "running" -> "处理中／结果尚未确认"
        "pending" -> "等待处理／结果尚未确认"
        else -> "结果未确认"
    }
}

private fun requestedShell(params: JSONObject): String = when {
    params.optBoolean("root") && params.optBoolean("shizuku") -> "root 与 Shizuku 同时请求（无效）"
    params.optBoolean("root") -> "root"
    params.optBoolean("shizuku") -> "Shizuku / Sui"
    else -> "普通应用权限"
}

private fun privilegeModeName(mode: String): String = when (mode) {
    "none" -> "无"
    "dhizuku" -> "Dhizuku"
    "shizuku" -> "Shizuku / Sui"
    "root" -> "root"
    else -> "未知"
}

private fun executionIdentity(backend: String, uid: Int): String = when (backend) {
    "app" -> "普通应用权限"
    "root" -> "root"
    "dhizuku" -> "Dhizuku"
    "shizuku" -> "Shizuku / Sui" + when (uid) {
        0 -> " · root 身份（UID 0）"
        2000 -> " · Shell 身份（UID 2000）"
        in 1..Int.MAX_VALUE -> "（UID $uid）"
        else -> "（身份未确认）"
    }
    else -> "执行权限未确认"
}

private fun recordCard(item: JSONObject): ResultCard {
    val kind = item.optString("kind")
    if (kind == "event" || kind == "legacy") {
        val text = item.optString("message")
        val time = if (kind == "legacy") "旧版记录，时间见正文" else timestamp(item, "at")
        return ResultCard(if (kind == "legacy") "旧版记录" else "连接与运行记录", time,
            body = text.take(140) + if (text.length > 140) "…" else "",
            detailFields = listOf("记录时间" to time), detailBody = text)
    }
    val action = item.optString("action")
    val params = item.optJSONObject("params") ?: JSONObject()
    val result = item.optJSONObject("result") ?: JSONObject()
    val direction = if (kind == "incoming") "对方 → 本机" else "本机 → 对方"
    val title = if (action == "set_app_suspended") {
        if (params.optBoolean("suspended")) "暂停应用" else "恢复应用"
    } else commandName(action)
    val details = mutableListOf(
        "记录时间" to timestamp(item, "at"),
        "指令方向" to direction,
        "发送方 ID" to item.optString("sender_id"),
        "接收方 ID" to item.optString("target_id"),
        "服务器" to item.optString("server"),
        "指令 ID" to item.optString("command_id").ifBlank { "尚未取得" },
        "处理结果" to recordStatus(item),
    )
    if (action == "shell") {
        details += "Shell 指令" to params.optString("command").ifBlank { "未记录" }
        details += "权限规则" to if (item.optString("shell_policy") == "receiver_work_mode") "跟随接收手机的特权工作模式" else "旧版请求：" + requestedShell(params)
        // Do not mistake the request flags for the receiving device's execution report.
        val identity = if (result.has("backend")) executionIdentity(result.optString("backend"), result.optInt("shizuku_uid", -1))
            else "未执行或实际权限尚未确认"
        details += "执行方式（对方返回）" to identity
        if (result.has("work_mode")) details += "执行时的特权工作模式" to privilegeModeName(result.optString("work_mode"))
        if (item.has("actual_backend") && !result.has("backend"))
            details += "尝试使用的执行通道" to executionIdentity(item.optString("actual_backend"), item.optInt("execution_uid", -1))
        details += "超时设置" to "${params.optInt("timeout_seconds", 20)} 秒"
        if (result.has("exit_code")) details += "退出码" to result.optInt("exit_code").toString()
        if (result.has("timed_out")) details += "是否超时" to if (result.optBoolean("timed_out")) "是" else "否"
        if (result.has("cancelled")) details += "是否取消" to if (result.optBoolean("cancelled")) "是" else "否"
    } else {
        if (item.has("actual_backend")) details += "执行方式" to executionIdentity(item.optString("actual_backend"), item.optInt("execution_uid", -1))
        if (action == "get_screenshot" && result.has("width")) {
            details += "截图前台应用" to result.optString("foreground_package")
            details += "图片尺寸" to "${result.optInt("width")} × ${result.optInt("height")}"
            details += "截图时间" to timestamp(result, "captured_at")
            details += "图片记录" to "本机记录不保存截图图片"
        }
        if (params.has("package_name")) details += "应用包名" to params.optString("package_name")
        if (result.has("app_name")) details += "应用名" to appName(result)
        if (result.has("suspended")) details += "当前暂停状态" to if (result.optBoolean("suspended")) "已暂停" else "未暂停"
        if (params.has("hours")) details += "查询范围" to "${params.optInt("hours")} 小时"
        if (params.has("title")) details += "通知标题" to params.optString("title")
        if (params.has("content")) details += "通知内容" to params.optString("content")
    }
    if (item.has("started_at")) details += "开始处理时间" to timestamp(item, "started_at")
    if (item.has("finished_at") && item.optString("status") != "pending") details += "处理结束时间" to timestamp(item, "finished_at")
    if (item.has("delivery")) details += "结果回传" to item.optString("delivery")
    val body = buildString {
        if (item.optString("error").isNotBlank()) append("失败原因：\n${item.optString("error")}\n\n")
        if (item.optString("delivery_error").isNotBlank()) append("回传失败：\n${item.optString("delivery_error")}\n\n")
        if (result.has("stdout")) append("标准输出：\n${result.optString("stdout").ifBlank { "（空）" }}\n\n")
        if (result.has("stderr")) append("错误输出：\n${result.optString("stderr").ifBlank { "（空）" }}\n\n")
        if (result.optString("note").isNotBlank()) append(result.optString("note") + "\n")
        if (result.optBoolean("history_truncated") || result.optBoolean("truncated")) append("部分输出已截断")
    }.trim()
    return ResultCard(title, timestamp(item, "at"), listOf("方向" to direction, "结果" to recordStatus(item)),
        body = if (action == "shell") params.optString("command").take(100) else "点按查看详情",
        detailFields = details, detailBody = body)
}

private fun displayAppName(name: String, packageName: String): String =
    name.trim().takeUnless { it.isEmpty() || it == packageName.trim() } ?: "棍母"

private fun appName(item: JSONObject): String =
    displayAppName(item.optString("app_name"), item.optString("package_name"))

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
        val screenKnown = result.has("screen_off") && !result.isNull("screen_off")
        val screenOff = screenKnown && result.optBoolean("screen_off")
        val battery = result.optInt("battery_percent", -1)
        val uptime = result.optLong("uptime_ms", -1)
        val cards = listOf(
            ResultCard("设备信息", fields = listOf(
                "型号" to result.optString("device_model").ifBlank { "未知" },
                "安卓版本" to result.optString("android_version").ifBlank { "未知" },
                "API" to if (result.has("android_sdk")) result.optInt("android_sdk").toString() else "未知",
                "已开机" to if (uptime >= 0) duration(uptime) else "未知",
                "是否息屏" to if (!screenKnown) "未知" else if (screenOff) "是" else "否",
            )),
            ResultCard("电量信息", fields = listOf(
                "剩余电量" to if (battery >= 0) "${battery}%" else "未知",
                "充电状态" to if (result.optBoolean("charging")) "正在充电" else "未充电",
            )),
            if (screenOff || current.optBoolean("skipped")) {
                ResultCard("当前屏幕应用", body = if (screenOff) "息屏中，未查询前台应用"
                    else current.optString("skip_reason").ifBlank { "未查询前台应用" })
            } else {
                ResultCard("当前屏幕应用", appName(current), fields = listOf(
                    "包名" to current.optString("package_name").ifBlank { "未知" },
                ))
            },
            ResultCard("执行授权", fields = listOf(
                "Shell" to if (result.optBoolean("shell_enabled")) "开启" else "关闭",
                "特权工作模式" to if (result.has("work_mode")) privilegeModeName(result.optString("work_mode")) else "旧版未提供",
                "Shell 执行方式" to if (result.has("shell_backend")) executionIdentity(result.optString("shell_backend"), result.optInt("shizuku_uid", -1)) else "旧版未提供",
            )),
        )
        return ResultPage("设备状态", device, cards, "查询时间：" + timestamp(result, "queried_at"))
    }
    if (action == "get_media") {
        val sessions = result.optJSONArray("sessions") ?: JSONArray()
        val cards = (0 until sessions.length()).map { index ->
            val item = sessions.optJSONObject(index) ?: JSONObject()
            ResultCard(
                item.optString("title").ifBlank { "未提供曲目名称" },
                appName(item),
                fields = listOf(
                    "应用包名" to item.optString("package_name"),
                    "播放状态" to item.optString("state").ifBlank { "未知" },
                    "作者 / 歌手" to item.optString("artist").ifBlank { "未提供" },
                    "专辑" to item.optString("album").ifBlank { "未提供" },
                    "播放进度" to if (item.isNull("position_ms") || !item.has("position_ms")) "未知" else duration(item.optLong("position_ms")),
                    "总时长" to if (item.isNull("duration_ms") || !item.has("duration_ms")) "未知" else duration(item.optLong("duration_ms")),
                ),
            )
        }
        return ResultPage(
            "媒体播放", device, cards,
            "共 ${cards.size} 个媒体会话 · " + timestamp(result, "queried_at"),
            "这是查询时的播放快照，进度可能为估算值；仅显示应用向系统提供的信息，可能包含视频" +
                (if (result.optBoolean("truncated")) "\n媒体会话较多，仅显示部分结果" else "") +
                (if (result.optInt("unavailable_count") > 0) "\n部分媒体会话暂时无法读取" else ""),
            if (result.optInt("unavailable_count") > 0) "媒体会话暂时无法读取，请稍后重试" else "当前没有可读取的媒体会话",
        )
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
            (if (result.optBoolean("truncated")) "通知或正文过多，部分内容未完整展示\n" else "") +
                "仅显示系统允许读取的内容；自定义通知或敏感通知可能没有正文",
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
            if (recent) "按前台使用时长排序；系统记录不完整时，时长可能未知或存在误差" else null,
            if (result.optBoolean("truncated")) "列表过长，仅显示部分应用" else null,
        ).joinToString("\n"),
        if (recent) "此时间段内暂无应用使用记录" else "没有可显示的用户应用",
    )
}

@Composable
private fun ResultScreen(activity: MainActivity, ui: UiState, page: ResultPage, onBack: () -> Unit) {
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
            if (page.historyView) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        TextButton("刷新记录", activity::refreshRecords, Modifier.weight(1f), enabled = !ui.busy)
                        TextButton("清除记录", activity::clearLogs, Modifier.weight(1f), enabled = !ui.busy)
                    }
                }
            }
            if (page.cards.isEmpty()) {
                item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) { Text(page.emptyMessage) } }
            }
            itemsIndexed(page.cards, key = { index, card -> "${card.title}:${card.subtitle}:$index" }) { _, card ->
                val clickable = if (card.detailFields != null) Modifier.clickable(enabled = !ui.busy) {
                    ui.resultPage = ResultPage("记录详情", page.device,
                        listOf(card.copy(fields = card.detailFields, body = card.detailBody, detailFields = null, detailBody = "")),
                        parent = page)
                } else Modifier
                Card(Modifier.animateItem(
                    fadeInSpec = tween(180),
                    placementSpec = spring(dampingRatio = 0.92f, stiffness = Spring.StiffnessMediumLow),
                    fadeOutSpec = tween(120),
                ).fillMaxWidth().then(clickable), insideMargin = PaddingValues(20.dp)) {
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
                            card.image?.let { image ->
                                Image(bitmap = image.asImageBitmap(), contentDescription = "对方屏幕截图",
                                    modifier = Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height),
                                    contentScale = ContentScale.Fit)
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


data class PeerApp(val packageName: String, val name: String, val protectedApp: Boolean, val installed: Boolean = true, val pausedByPairLink: Boolean = false)
data class AppPickerPage(val server: String, val sender: String, val device: String, val apps: List<PeerApp>, val truncated: Boolean, val blacklist: Boolean = false, val screenshotBlacklist: Boolean = false)

@Composable
private fun AppPickerScreen(activity: MainActivity, ui: UiState, page: AppPickerPage) {
    val search = ui.appSearch.trim()
    val visible = page.apps.filter { search.isEmpty() || it.name.contains(search, ignoreCase = true) || it.packageName.contains(search, ignoreCase = true) }
    val keyboard = LocalSoftwareKeyboardController.current
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = if (page.screenshotBlacklist) "截图黑名单" else if (page.blacklist) "应用暂停黑名单" else "选择对方应用",
                largeTitle = if (page.screenshotBlacklist) "截图黑名单" else if (page.blacklist) "应用暂停黑名单" else "选择对方应用",
                navigationIcon = {
                    IconButton(onClick = { if (!ui.busy) { keyboard?.hide(); ui.appPicker = null } }, minWidth = 48.dp, minHeight = 48.dp) {
                        Icon(BackArrow, contentDescription = "返回", tint = MiuixTheme.colorScheme.onSurface)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text((if (page.blacklist) "本机设备：" else "对方设备：") + page.device, color = MiuixTheme.colorScheme.primary)
                if (page.blacklist) Text(if (page.screenshotBlacklist) "勾选后保存\n前台应用在黑名单中时拒绝对方截图，可选择用户应用和系统应用" else "勾选后保存，阻止对方通过应用暂停功能暂停这些应用\n不会自动恢复已经暂停的应用", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Input(ui.appSearch, { ui.appSearch = it }, "搜索应用名或包名", enabled = !ui.busy)
                Text("共 ${page.apps.size} 个应用 · 显示 ${visible.size} 个 · 已选 ${ui.selectedPeerApps.size} 个",
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton("刷新列表", { if (page.screenshotBlacklist) activity.openScreenshotBlacklist() else if (page.blacklist) activity.openPauseBlacklist() else activity.openAppPicker() }, Modifier.weight(1f), enabled = !ui.busy)
                    TextButton("清空选择", { ui.selectedPeerApps = emptySet() }, Modifier.weight(1f), enabled = !ui.busy && ui.selectedPeerApps.isNotEmpty())
                }
                if (page.truncated) Text("应用列表过长，仅显示服务器返回的部分应用", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (visible.isEmpty()) {
                    item { Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
                        Text(if (page.apps.isEmpty()) "没有可显示的用户应用" else "没有匹配的应用")
                    } }
                }
                items(visible, key = { it.packageName }) { app ->
                    Card(Modifier.animateItem(
                        fadeInSpec = tween(180),
                        placementSpec = spring(dampingRatio = 0.92f, stiffness = Spring.StiffnessMediumLow),
                        fadeOutSpec = tween(120),
                    ).fillMaxWidth()) {
                        CheckboxPreference(
                            title = app.name,
                            summary = app.packageName + (if (app.protectedApp) "\nPairLink 本身已受保护" else "") + (if (app.pausedByPairLink) "\n由 PairLink 暂停" else "") + (if (!app.installed) "\n未安装，保留原黑名单记录" else ""),
                            checked = app.packageName in ui.selectedPeerApps,
                            onCheckedChange = { checked ->
                                ui.selectedPeerApps = if (checked) ui.selectedPeerApps + app.packageName else ui.selectedPeerApps - app.packageName
                            },
                            enabled = !ui.busy && !app.protectedApp,
                        )
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (page.blacklist) {
                    TextButton("保存黑名单", { keyboard?.hide(); if (page.screenshotBlacklist) activity.saveScreenshotBlacklist() else activity.savePauseBlacklist() }, Modifier.weight(1f),
                        enabled = !ui.busy, colors = ButtonDefaults.textButtonColorsPrimary())
                } else {
                    TextButton("暂停所选", { keyboard?.hide(); activity.suspendPeerApp() }, Modifier.weight(1f),
                        enabled = !ui.busy && ui.selectedPeerApps.isNotEmpty(), colors = ButtonDefaults.textButtonColorsPrimary())
                    TextButton("恢复所选", { keyboard?.hide(); activity.resumePeerApp() }, Modifier.weight(1f),
                        enabled = !ui.busy && ui.selectedPeerApps.isNotEmpty())
                }
            }
        }
    }
}
