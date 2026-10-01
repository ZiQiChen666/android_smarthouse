package com.example.smarthouse.data

/**
 * 物模型属性定义
 */
data class PropertyDef(
    val identifier: String,
    val name: String,
    val type: PropertyType,
    val unit: String = "",
    /** 该属性是否为"设置值/阈值"，需要通过下发指令写入 */
    val isWritable: Boolean = true
)

enum class PropertyType {
    NUMBER,
    BOOLEAN,
    TEXT
}

/**
 * 属性分组，用于界面分区显示
 */
data class PropertyGroup(
    val title: String,
    val subtitle: String,
    val properties: List<PropertyDef>
)

/**
 * 单条属性的实时值
 */
data class PropertyValue(
    val identifier: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis()
)

object ThingModel {

    const val PRODUCT_ID = "GxSOl8476i"

    // ---------------- 设备配置 ----------------
    val devices = listOf(
        DeviceConfig(
            deviceId = "temp",
            deviceKey = "cVBBUkhPNTZkekRCRDdxTnM0WjRwcDZHd0xRbEZVY3A=",
            displayName = "环境节点 A"
        ),
        DeviceConfig(
            deviceId = "temp1",
            deviceKey = "b0pwcXZWY0xwZERjZlRqbjN2VkpCdTdnNXBXMHJyR1g=",
            displayName = "环境节点 B"
        )
    )

    // ---------------- 物模型属性 ----------------
    const val TEMPERATURE = "temperature"
    const val TEMPERATURE_MAX = "temperatureMax"
    const val TEMPERATURE_MIN = "temperatureMin"
    const val HUMIDITY = "humidity"
    const val HUMIDITY_MAX = "humidityMax"
    const val HUMIDITY_MIN = "humidityMin"
    const val LIGHT = "light"
    const val LIGHT_MAX = "lightMax"
    const val LIGHT_MIN = "lightMin"
    const val WATERLEVEL = "waterlevel"
    const val WATERLEVEL_MAX = "waterlevelMax"
    const val WATERLEVEL_MIN = "waterlevelMin"
    const val FAN_S = "fanS"
    const val LIGHT_S = "lightS"
    const val PUMP_S = "pumpS"
    const val TARGET_IP = "targetip"

    /** 开关类属性（0/1），需要下发指令 */
    val switchProps = listOf(
        PropertyDef(FAN_S, "风扇开关", PropertyType.BOOLEAN),
        PropertyDef(LIGHT_S, "灯光开关", PropertyType.BOOLEAN),
        PropertyDef(PUMP_S, "水泵开关", PropertyType.BOOLEAN)
    )

    /** 实时测量值（只读展示） */
    val measuredProps = listOf(
        PropertyDef(TEMPERATURE, "温度", PropertyType.NUMBER, "℃"),
        PropertyDef(HUMIDITY, "湿度", PropertyType.NUMBER, "%RH"),
        PropertyDef(LIGHT, "光照", PropertyType.NUMBER, "lx"),
        PropertyDef(WATERLEVEL, "水位", PropertyType.NUMBER, "cm")
    )

    /** 阈值类属性（可编辑下发） */
    val thresholdProps = listOf(
        PropertyDef(TEMPERATURE_MAX, "温度上限", PropertyType.NUMBER, "℃"),
        PropertyDef(TEMPERATURE_MIN, "温度下限", PropertyType.NUMBER, "℃"),
        PropertyDef(HUMIDITY_MAX, "湿度上限", PropertyType.NUMBER, "%RH"),
        PropertyDef(HUMIDITY_MIN, "湿度下限", PropertyType.NUMBER, "%RH"),
        PropertyDef(LIGHT_MAX, "光照上限", PropertyType.NUMBER, "lx"),
        PropertyDef(LIGHT_MIN, "光照下限", PropertyType.NUMBER, "lx"),
        PropertyDef(WATERLEVEL_MAX, "水位上限", PropertyType.NUMBER, "cm"),
        PropertyDef(WATERLEVEL_MIN, "水位下限", PropertyType.NUMBER, "cm")
    )

    /** 网络配置 - 仅 temp1 拥有，用于读取后跳转网页 */
    val networkProps = listOf(
        PropertyDef(TARGET_IP, "目标 IP", PropertyType.TEXT, isWritable = false)
    )

    val allProperties: List<PropertyDef> =
        switchProps + measuredProps + thresholdProps + networkProps

    fun find(id: String): PropertyDef? = allProperties.firstOrNull { it.identifier == id }

    /** 该设备是否支持开关下发（temp1 不支持） */
    fun supportsSwitches(deviceId: String): Boolean = deviceId == "temp"

    /** 该设备是否拥有目标 IP 属性（仅 temp1） */
    fun hasTargetIp(deviceId: String): Boolean = deviceId == "temp1"

    /** 该设备可下发的阈值属性 */
    fun thresholdPropsFor(deviceId: String): List<PropertyDef> = thresholdProps
}

data class DeviceConfig(
    val deviceId: String,
    val deviceKey: String,
    val displayName: String
)
