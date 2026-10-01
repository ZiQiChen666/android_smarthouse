package com.example.smarthouse.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.smarthouse.data.ThingModel
import com.example.smarthouse.ui.components.DeviceChip
import com.example.smarthouse.ui.components.GradientHeader
import com.example.smarthouse.ui.components.LineChartCard
import com.example.smarthouse.ui.components.MeasurementCard
import com.example.smarthouse.ui.components.SectionTitle
import com.example.smarthouse.ui.components.SwitchCard
import com.example.smarthouse.ui.components.TargetIpCard
import com.example.smarthouse.ui.components.ThresholdRow
import com.example.smarthouse.ui.theme.AccentAmber
import com.example.smarthouse.ui.theme.AccentRose
import com.example.smarthouse.ui.theme.AccentViolet
import com.example.smarthouse.ui.theme.BrandBlue
import com.example.smarthouse.ui.theme.BrandCyan
import com.example.smarthouse.ui.theme.BrandTeal
import com.example.smarthouse.ui.theme.ErrorRed
import com.example.smarthouse.ui.theme.OnlineGreen

@Composable
fun MainScreen(viewModel: SmartHouseViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.fetchTargetIp() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val statusColor = when (state.connectionState) {
        ConnectionState.Online -> OnlineGreen
        ConnectionState.Error -> ErrorRed
        ConnectionState.Idle -> Color.White
    }
    val statusText = when (state.connectionState) {
        ConnectionState.Online -> "已连接 OneNET"
        ConnectionState.Error -> "连接异常"
        ConnectionState.Idle -> "未连接"
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = padding.calculateBottomPadding())
        ) {
            GradientHeader(
                title = "智慧大棚监控",
                subtitle = state.selectedDevice.displayName,
                statusText = statusText,
                statusColor = statusColor
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 设备选择
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThingModel.devices.forEach { device ->
                            DeviceChip(
                                name = device.displayName,
                                selected = device.deviceId == state.selectedDevice.deviceId,
                                onClick = { viewModel.selectDevice(device) }
                            )
                        }
                        Spacer(Modifier.weight(1f))
                    }
                }

                // 实时测量值
                item {
                    SectionTitle("实时测量", "设备上报的传感器数据")
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MeasurementCard(
                            label = "温度",
                            value = state.valueOf(ThingModel.TEMPERATURE),
                            unit = "℃",
                            icon = Icons.Filled.Thermostat,
                            accent = AccentRose,
                            modifier = Modifier.weight(1f)
                        )
                        MeasurementCard(
                            label = "湿度",
                            value = state.valueOf(ThingModel.HUMIDITY),
                            unit = "%RH",
                            icon = Icons.Filled.WaterDrop,
                            accent = BrandCyan,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MeasurementCard(
                            label = "光照",
                            value = state.valueOf(ThingModel.LIGHT),
                            unit = "lx",
                            icon = Icons.Filled.Lightbulb,
                            accent = AccentAmber,
                            modifier = Modifier.weight(1f)
                        )
                        MeasurementCard(
                            label = "水位",
                            value = state.valueOf(ThingModel.WATERLEVEL),
                            unit = "cm",
                            icon = Icons.Filled.Waves,
                            accent = BrandBlue,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // 实时曲线（每秒采样，自动更新）
                item {
                    SectionTitle("实时曲线", "每秒自动采集，最近 60 秒变化趋势")
                }
                item {
                    LineChartCard(
                        label = "温度",
                        unit = "℃",
                        samples = state.history[ThingModel.TEMPERATURE] ?: emptyList(),
                        accent = AccentRose
                    )
                }
                item {
                    LineChartCard(
                        label = "湿度",
                        unit = "%RH",
                        samples = state.history[ThingModel.HUMIDITY] ?: emptyList(),
                        accent = BrandCyan
                    )
                }
                item {
                    LineChartCard(
                        label = "光照",
                        unit = "lx",
                        samples = state.history[ThingModel.LIGHT] ?: emptyList(),
                        accent = AccentAmber
                    )
                }
                item {
                    LineChartCard(
                        label = "水位",
                        unit = "cm",
                        samples = state.history[ThingModel.WATERLEVEL] ?: emptyList(),
                        accent = BrandBlue
                    )
                }

                // 开关控制（temp1 不支持）
                if (ThingModel.supportsSwitches(state.selectedDevice.deviceId)) {
                    item {
                        SectionTitle("设备控制", "点击开关即可下发指令到 OneNET")
                    }
                    items(ThingModel.switchProps) { prop ->
                        val checked = state.switchStates[prop.identifier] ?: false
                        SwitchCard(
                            label = prop.name,
                            checked = checked,
                            icon = switchIcon(prop.identifier),
                            accent = switchColor(prop.identifier),
                            enabled = !state.isSending,
                            onCheckedChange = { viewModel.sendSwitch(prop.identifier, it) }
                        )
                    }
                }

                // 阈值设置
                item {
                    SectionTitle("阈值设置", "输入数值后点击下发，仅下发该项内容")
                }
                items(ThingModel.thresholdProps) { prop ->
                    ThresholdRow(
                        label = prop.name,
                        unit = prop.unit,
                        enabled = !state.isSending,
                        onSend = { viewModel.sendProperty(prop.identifier, it) }
                    )
                }

                // 网络配置：仅 temp1 拥有目标 IP，读取后提供跳转按钮
                if (ThingModel.hasTargetIp(state.selectedDevice.deviceId)) {
                    item {
                        SectionTitle("网络配置", "从设备读取的目标服务器地址")
                    }
                    item {
                        TargetIpCard(
                            targetIp = state.targetIp,
                            onOpen = {
                                val ip = state.targetIp.trim()
                                if (ip.isBlank()) {
                                    viewModel.showMessage("尚未获取到目标 IP")
                                } else {
                                    val url = normalizeUrl(ip)
                                    try {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                        )
                                    } catch (e: Exception) {
                                        viewModel.showMessage("无法打开链接: $url")
                                    }
                                }
                            },
                            onRefresh = { viewModel.fetchTargetIp() }
                        )
                    }
                }

                // 底部时间
                item {
                    state.lastUpdated?.let {
                        Text(
                            "最后更新: ${formatTime(it)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun UiState.valueOf(id: String): String = values[id] ?: ""

private fun formatTime(ts: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(ts))
}

/**
 * 把设备上报的地址规范化为可打开的 http(s) URL。
 * 支持 "192.168.1.10"、"192.168.1.10:8080"、"http://..." 等。
 */
private fun normalizeUrl(raw: String): String {
    val t = raw.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.startsWith("//") -> "http:$t"
        else -> "http://$t"
    }
}

private fun switchIcon(id: String): ImageVector = when (id) {
    ThingModel.FAN_S -> Icons.Filled.Air
    ThingModel.LIGHT_S -> Icons.Filled.Lightbulb
    ThingModel.PUMP_S -> Icons.Filled.Opacity
    else -> Icons.Filled.Wifi
}

private fun switchColor(id: String): Color = when (id) {
    ThingModel.FAN_S -> BrandCyan
    ThingModel.LIGHT_S -> AccentAmber
    ThingModel.PUMP_S -> BrandBlue
    else -> BrandTeal
}
