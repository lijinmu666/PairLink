package com.lijinmu.pairlink

import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

class UiState {
    var server by mutableStateOf("")
    var own by mutableStateOf("")
    var peer by mutableStateOf("")
    var script by mutableStateOf("")
    var sharing by mutableStateOf(false)
    var shell by mutableStateOf(false)
    var root by mutableStateOf(false)
    var sendRoot by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var usageAllowed by mutableStateOf(false)
    var connection by mutableStateOf("共享已关闭")
    var dialog by mutableStateOf<UiDialog?>(null)

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
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { TopAppBar(title = "PairLink", largeTitle = "PairLink") },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth(), insideMargin = PaddingValues(20.dp)) {
                    Text(ui.connection, color = MiuixTheme.colorScheme.primary, style = MiuixTheme.textStyles.title2)
                    Spacer(Modifier.height(8.dp))
                    Text("你的手机，与对方保持连接", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            item {
                Section("连接设置") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Input(ui.server, { ui.server = it }, "服务器 HTTPS 根地址", enabled = !ui.sharing)
                        Input(ui.own, { ui.own = it }, "自己的设备 ID", enabled = !ui.sharing)
                        Input(ui.peer, { ui.peer = it }, "对方的设备 ID", enabled = !ui.sharing)
                        Action("保存连接设置", activity::saveSettings, enabled = !ui.sharing)
                        Action("测试服务器连接", activity::testConnection, enabled = !ui.busy, primary = false)
                        Action("查看本机连接与执行记录", activity::showLogs, primary = false)
                        Action("清除记录", activity::clearLogs, primary = false)
                    }
                }
            }
            item {
                Section("共享与授权") {
                    SwitchPreference(title = "允许对方查询此手机", summary = "开启后建立接收通道；暂停后停止共享", checked = ui.sharing, onCheckedChange = activity::changeSharing)
                    SwitchPreference(title = "允许对方执行 Shell", summary = "本机主动确认后开启", checked = ui.shell, onCheckedChange = activity::changeShell)
                    SwitchPreference(title = "允许对方使用 root", summary = "需要本机 root 管理器单独授权", checked = ui.root, onCheckedChange = activity::changeRoot)
                    if (!ui.usageAllowed) {
                        Column(Modifier.padding(16.dp)) {
                            Action("开启应用使用情况访问权限", activity::usagePermission, primary = false)
                        }
                    }
                }
            }
            item {
                Section("查询对方") {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Action("查看在线状态与电量", activity::queryStatus, enabled = !ui.busy)
                        Action("查看最近 1 小时使用的应用", activity::queryRecent, enabled = !ui.busy, primary = false)
                    }
                }
            }
            item {
                Section("远程 Shell") {
                    Column(Modifier.padding(16.dp)) {
                        Input(ui.script, { ui.script = it }, "输入命令，例如 id 或 pwd", singleLine = false)
                    }
                    SwitchPreference(title = "本次请求使用对方的 root 权限", checked = ui.sendRoot, onCheckedChange = { ui.sendRoot = it })
                    Column(Modifier.padding(16.dp)) {
                        Action("执行命令", activity::executeShell, enabled = !ui.busy)
                    }
                }
            }
            item {
                Text("双方都需要主动安装、配置并开启共享。当前为单次命令执行，暂不提供交互式终端或 MCP。", modifier = Modifier.padding(horizontal = 12.dp), color = MiuixTheme.colorScheme.onSurfaceVariantSummary, style = MiuixTheme.textStyles.footnote1)
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
