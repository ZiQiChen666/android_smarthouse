package com.example.smarthouse.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.smarthouse.data.DeviceConfig
import com.example.smarthouse.data.OneNetClient
import com.example.smarthouse.data.PropertyType
import com.example.smarthouse.data.ThingModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/** 单条采样点，用于绘制曲线 */
data class Sample(
    val timestamp: Long,
    val value: Double
)

data class UiState(
    val selectedDevice: DeviceConfig = ThingModel.devices.first(),
    val values: Map<String, String> = emptyMap(),
    val isLoading: Boolean = false,
    val connectionState: ConnectionState = ConnectionState.Idle,
    val message: String? = null,
    val lastUpdated: Long? = null,
    /** 从 temp1 读取到的目标 IP，用于跳转网页 */
    val targetIp: String = "",
    /** 各测量值的历史曲线：identifier -> 采样点列表 */
    val history: Map<String, List<Sample>> = emptyMap(),
    /** 开关的本地显示状态（仅用于界面展示，不代表云端真实值） */
    val switchStates: Map<String, Boolean> = emptyMap()
)

enum class ConnectionState { Idle, Online, Error }

class SmartHouseViewModel(
    private val client: OneNetClient = OneNetClient()
) : ViewModel() {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /**
     * 轮询锁：只用于轮询请求之间互斥。用 tryLock 非阻塞获取，
     * 拿不到就跳过本轮，绝不阻塞其他操作。
     */
    private val pollLock = Mutex()

    private var pollJob: Job? = null

    /** 轮询连续失败次数，用于区分"偶发失败"和"真正掉线" */
    private var pollFailStreak = 0

    companion object {
        /** 轮询间隔：1 秒 */
        private const val POLL_INTERVAL_MS = 1000L
        /** 每条曲线最多保留的采样点数 */
        private const val MAX_SAMPLES = 60
        /** 连续失败多少次才判定为"连接异常" */
        private const val OFFLINE_THRESHOLD = 3
    }

    init {
        startPolling()
    }

    fun selectDevice(device: DeviceConfig) {
        pollFailStreak = 0
        _state.update {
            it.copy(
                selectedDevice = device,
                values = emptyMap(),
                history = emptyMap(),
                switchStates = emptyMap(),
                lastUpdated = null,
                connectionState = ConnectionState.Idle
            )
        }
    }

    /** 启动 1 秒轮询（自动刷新） */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                refresh()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** 查询全部属性 (GET)。非阻塞拿锁，避免与下发互相等待 */
    private suspend fun refresh() {
        val device = _state.value.selectedDevice
        // 已有轮询在途则跳过本轮，不排队等待
        if (!pollLock.tryLock()) return
        try {
            val result = withContext(Dispatchers.IO) {
                client.queryProperties(ThingModel.PRODUCT_ID, device.deviceId, device.deviceKey)
            }
            // 设备可能在请求期间被切换，丢弃过期结果
            if (device.deviceId != _state.value.selectedDevice.deviceId) return

            pollFailStreak = 0
            val now = System.currentTimeMillis()

            // 只解析测量值（温度/湿度/光照/水位），其余阈值一律忽略
            val measured = ThingModel.measuredProps
                .mapNotNull { prop ->
                    result[prop.identifier]?.let { prop.identifier to it }
                }
                .toMap()

            // temp1 的目标 IP 单独读取，用于跳转网页
            val newTargetIp = if (ThingModel.hasTargetIp(device.deviceId)) {
                result[ThingModel.TARGET_IP] ?: _state.value.targetIp
            } else _state.value.targetIp

            _state.update { old ->
                old.copy(
                    values = measured,
                    targetIp = newTargetIp,
                    isLoading = false,
                    connectionState = ConnectionState.Online,
                    lastUpdated = now,
                    history = appendHistory(old.history, measured, now)
                    // 注意：不在此处清空 message，避免把下发的提示冲掉
                )
            }
        } catch (e: Exception) {
            // 单次失败不立刻判定掉线：1s 高频轮询下偶发失败属正常现象。
            // 只有连续失败达到阈值才显示"连接异常"，从而不会出现"一下发就断连"的假象。
            pollFailStreak++
            if (pollFailStreak >= OFFLINE_THRESHOLD) {
                _state.update {
                    it.copy(isLoading = false, connectionState = ConnectionState.Error)
                }
            }
        } finally {
            pollLock.unlock()
        }
    }

    /** 把本次结果中的测量值追加到历史曲线 */
    private fun appendHistory(
        old: Map<String, List<Sample>>,
        result: Map<String, String>,
        now: Long
    ): Map<String, List<Sample>> {
        val mutable = old.toMutableMap()
        ThingModel.measuredProps.forEach { prop ->
            val num = result[prop.identifier]?.toDoubleOrNull() ?: return@forEach
            val list = (mutable[prop.identifier] ?: emptyList()) + Sample(now, num)
            mutable[prop.identifier] = if (list.size > MAX_SAMPLES) list.takeLast(MAX_SAMPLES) else list
        }
        return mutable
    }

    /**
     * 单独从 temp1 获取目标 IP 并保存到变量（不切换当前设备显示）。
     */
    fun fetchTargetIp() {
        val device = ThingModel.devices.firstOrNull { ThingModel.hasTargetIp(it.deviceId) } ?: return
        viewModelScope.launch {
            if (!pollLock.tryLock()) return@launch
            try {
                val result = withContext(Dispatchers.IO) {
                    client.queryProperties(ThingModel.PRODUCT_ID, device.deviceId, device.deviceKey)
                }
                result[ThingModel.TARGET_IP]?.takeIf { it.isNotBlank() }?.let { ip ->
                    _state.update { it.copy(targetIp = ip) }
                }
            } catch (_: Exception) {
                // 静默失败，不影响主流程
            } finally {
                pollLock.unlock()
            }
        }
    }

    /** 下发开关指令 (POST)。temp1 不支持开关 */
    fun sendSwitch(identifier: String, on: Boolean) {
        if (!ThingModel.supportsSwitches(_state.value.selectedDevice.deviceId)) {
            _state.update { it.copy(message = "该设备不支持开关控制") }
            return
        }
        // 开关状态仅本地记录用于界面显示（不解析云端返回值）
        _state.update { it.copy(switchStates = it.switchStates + (identifier to on)) }
        val value = if (on) 1.0 else 0.0
        sendParams(mapOf(identifier to value))
    }

    /** 下发阈值/数值/文本 (POST)。返回是否成功进入下发流程 */
    fun sendProperty(identifier: String, raw: String): Boolean {
        val def = ThingModel.find(identifier)
        val value: Any = when (def?.type) {
            PropertyType.NUMBER -> raw.toDoubleOrNull()
                ?: run {
                    _state.update { it.copy(message = "请输入有效数值") }
                    return false
                }
            else -> raw
        }
        sendParams(mapOf(identifier to value))
        return true
    }

    private fun sendParams(params: Map<String, Any>) {
        val device = _state.value.selectedDevice
        // 直接下发，不等待、不阻塞界面
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    client.setProperties(ThingModel.PRODUCT_ID, device.deviceId, device.deviceKey, params)
                }
            } catch (_: Exception) {
                // 只负责下发，不处理结果状态
            }
        }
    }

    fun clearMessage() {
        _state.update { it.copy(message = null) }
    }

    fun showMessage(msg: String) {
        _state.update { it.copy(message = msg) }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }
}
