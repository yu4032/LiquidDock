package com.hellovoid.liquiddock.os4

import java.math.BigDecimal

/** Native ImageFilter glass values that may be independently overridden from stock defaults. */
object Os4NativeGlassOptions {
    data class Field(
        val id: String,
        val label: String,
        val group: String,
        val min: Double,
        val max: Double,
        val default: Double,
    )

    val fields: List<Field> = listOf(
        Field("edge_px", "边缘宽度（px）", "折射与轮廓", 0.1, 128.0, 36.0),
        Field("edge_pow", "边缘衰减", "折射与轮廓", 0.1, 8.0, 2.2),
        Field("thickness_px", "折射厚度（px）", "折射与轮廓", 0.0, 480.0, 100.0),
        Field("reflect_offset_px", "反射偏移（px）", "折射与轮廓", 0.0, 400.0, 200.0),
        Field("ior", "折射率", "折射与轮廓", 1.0, 3.0, 2.0),

        Field("light_x", "光源 X", "光照与反射", -2.0, 2.0, 0.0),
        Field("light_y", "光源 Y", "光照与反射", -2.0, 2.0, 0.0),
        Field("light_z", "光源 Z", "光照与反射", -2.0, 2.0, 1.0),
        Field("light_intensity", "主光强度", "光照与反射", 0.0, 4.0, 1.5),
        Field("light_opposite", "反向补光", "光照与反射", 0.0, 4.0, 1.0),
        Field("light_range", "光照范围（π 倍）", "光照与反射", 0.01, 2.0, 1.4),
        Field("light_edge_pow", "边缘光衰减", "光照与反射", 0.1, 8.0, 1.0),
        Field("reflection_strength", "反射强度", "光照与反射", 0.0, 2.0, 0.6),
        Field("reflection_lighten", "暗部提亮", "光照与反射", 0.0, 2.0, 1.0),
        Field("inner_bottom", "底部内光", "光照与反射", 0.0, 2.0, 0.0),

        Field("glass_r", "玻璃红色", "颜色", 0.0, 1.0, 1.0),
        Field("glass_g", "玻璃绿色", "颜色", 0.0, 1.0, 1.0),
        Field("glass_b", "玻璃蓝色", "颜色", 0.0, 1.0, 1.0),
        Field("glass_a", "玻璃透明度", "颜色", 0.0, 1.0, 1.0),
        Field("alpha", "整体透明度", "颜色", 0.0, 1.0, 1.0),
        Field("unshade", "原色保留", "颜色", 0.0, 1.0, 0.0),
        Field("color_pow", "颜色曲线", "颜色", 0.1, 4.0, 1.0),

        Field("blur_refract", "折射背景模糊", "背景与色调", 0.0, 1000.0, 36.0),
        Field("blur_background", "背景模糊", "背景与色调", 0.0, 1000.0, 500.0),
        Field("darker", "暗部压低", "背景与色调", 0.0, 1.0, 0.12),
        Field("darker_low", "暗部范围起点", "背景与色调", 0.0, 1.0, 0.0),
        Field("darker_high", "暗部范围终点", "背景与色调", 0.0, 1.0, 1.0),
        Field("bg_saturation", "背景饱和度", "背景与色调", 0.0, 3.0, 1.0),
        Field("bg_brightness", "背景亮度", "背景与色调", 0.0, 3.0, 1.0),
        Field("saturation", "玻璃饱和度", "背景与色调", 0.0, 3.0, 1.0),
        Field("brightness", "玻璃亮度", "背景与色调", 0.0, 3.0, 1.0),
        Field("inner_white", "内层提白", "背景与色调", 0.0, 1.0, 0.0),
        Field("inner_mix", "内层混合", "背景与色调", 0.0, 1.0, 0.0),
        Field("luminance_amount", "亮度曲线强度", "背景与色调", 0.0, 1.0, 0.0),
        Field("luminance_c0", "亮度曲线控制点 1", "背景与色调", -8.0, 8.0, 0.0),
        Field("luminance_c1", "亮度曲线控制点 2", "背景与色调", -8.0, 8.0, 0.0),
        Field("luminance_c2", "亮度曲线控制点 3", "背景与色调", -8.0, 8.0, 1.0),
        Field("luminance_c3", "亮度曲线控制点 4", "背景与色调", -8.0, 8.0, 0.0),
        Field("burn", "内层曲线", "背景与色调", 0.1, 8.0, 1.0),
    )

    private val byId = fields.associateBy(Field::id)
    private val decimalPattern = Regex("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?")

    init {
        require(byId.size == fields.size) { "Duplicate native glass field id" }
        require(fields.all { it.min.isFinite() && it.max.isFinite() && it.default in it.min..it.max })
    }

    val groups: List<Pair<String, List<Field>>> = fields.groupBy(Field::group).toList()

    /** Empty string means inherit every native value; malformed or unknown entries return null. */
    fun parse(serialized: String): Map<String, Double>? {
        if (serialized.isEmpty()) return emptyMap()
        val result = linkedMapOf<String, Double>()
        for (entry in serialized.split(';')) {
            if (entry.isEmpty()) return null
            val separator = entry.indexOf(':')
            if (separator <= 0 || separator != entry.lastIndexOf(':')) return null
            val id = entry.substring(0, separator)
            val number = entry.substring(separator + 1)
            val field = byId[id] ?: return null
            if (id in result || !decimalPattern.matches(number)) return null
            val value = number.toDoubleOrNull() ?: return null
            if (!value.isFinite() || value !in field.min..field.max) return null
            result[id] = value
        }
        return result
    }

    fun serialize(overrides: Map<String, Double>): String? {
        if (overrides.any { (id, value) ->
                val field = byId[id]
                field == null || !value.isFinite() || value !in field.min..field.max
            }
        ) return null
        return fields.asSequence()
            .filter { it.id in overrides }
            .joinToString(";") { field -> "${field.id}:${format(overrides.getValue(field.id))}" }
    }

    fun configLines(overrides: Map<String, Double>): List<String>? {
        if (overrides.any { (id, value) ->
                val field = byId[id]
                field == null || !value.isFinite() || value !in field.min..field.max
            }
        ) return null
        return fields.asSequence()
            .filter { it.id in overrides }
            .map { "glass_native_${it.id}=${format(overrides.getValue(it.id))}" }
            .toList()
    }

    fun format(value: Double): String {
        require(value.isFinite())
        val plain = BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
        return if ('.' in plain) plain else "$plain.0"
    }

    fun update(serialized: String, field: Field, value: Double?): String? {
        val values = parse(serialized)?.toMutableMap() ?: return null
        if (value == null) values.remove(field.id) else values[field.id] = value
        return serialize(values)
    }
}
