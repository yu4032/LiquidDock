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
        val summary: String,
    )

    val fields: List<Field> = listOf(
        Field("edge_px", "边缘宽度（px）", "折射与轮廓", 0.1, 64.0, 36.0, "玻璃四周立体折射带的像素宽度"),
        Field("edge_pow", "边缘衰减", "折射与轮廓", 0.1, 5.0, 2.2, "折射效果从中心向边缘过渡的陡峭程度"),
        Field("thickness_px", "折射厚度（px）", "折射与轮廓", 0.0, 200.0, 100.0, "模拟玻璃厚度，影响边缘图像被推挤拉伸的幅度"),
        Field("reflect_offset_px", "反射偏移（px）", "折射与轮廓", 0.0, 300.0, 200.0, "边缘内部反射采样相对于边界的偏移距离"),
        Field("ior", "折射率", "折射与轮廓", 1.0, 5.0, 2.0, "物理折射率；1.0 基本无弯曲，越大折射扭曲越明显"),

        Field("light_x", "光源 X", "光照与反射", -1.5, 1.5, 0.0, "虚拟光源方向的水平分量"),
        Field("light_y", "光源 Y", "光照与反射", -1.5, 1.5, 0.0, "虚拟光源方向的垂直分量"),
        Field("light_z", "光源 Z", "光照与反射", -1.5, 1.5, 1.0, "虚拟光源方向的深度分量（正值从正前方照射）"),
        Field("light_intensity", "主光强度", "光照与反射", 0.0, 3.0, 1.5, "正对光源一侧的边缘高光强度"),
        Field("light_opposite", "反向补光", "光照与反射", 0.0, 3.0, 1.0, "背向光源一侧的边缘补光强度"),
        Field("light_range", "光照范围（π 倍）", "光照与反射", 0.05, 2.0, 1.4, "边缘高光沿轮廓覆盖的角度范围"),
        Field("light_edge_pow", "边缘光衰减", "光照与反射", 0.1, 5.0, 1.0, "高光沿边缘衰减的锐利程度"),
        Field("reflection_strength", "反射强度", "光照与反射", 0.0, 2.0, 0.6, "玻璃表面的环境反射强弱"),
        Field("reflection_lighten", "暗部提亮", "光照与反射", 0.0, 2.0, 1.0, "反射暗区的提亮补偿"),
        Field("inner_bottom", "底部内光", "光照与反射", 0.0, 2.0, 0.0, "玻璃底部的额外内发光强度"),

        Field("glass_r", "玻璃红色", "颜色", 0.0, 1.0, 1.0, "玻璃染色 Tint 的红色通道"),
        Field("glass_g", "玻璃绿色", "颜色", 0.0, 1.0, 1.0, "玻璃染色 Tint 的绿色通道"),
        Field("glass_b", "玻璃蓝色", "颜色", 0.0, 1.0, 1.0, "玻璃染色 Tint 的蓝色通道"),
        Field("glass_a", "玻璃透明度", "颜色", 0.0, 1.0, 1.0, "玻璃染色 Tint 的混合权重"),
        Field("alpha", "整体透明度", "颜色", 0.0, 1.0, 1.0, "整块玻璃滤镜最终的可见不透明度"),
        Field("unshade", "原色保留", "颜色", 0.0, 1.0, 0.0, "越高越保留未着色与未打光前的原始底色"),
        Field("color_pow", "颜色曲线", "颜色", 0.1, 3.0, 1.0, "颜色混合时的幂次非线性曲线"),

        Field("blur_refract", "折射背景模糊", "背景与色调", 0.0, 120.0, 36.0, "实际控制玻璃背景与折射采样层的模糊半径；设为 0 可看清背后细节"),
        Field("blur_background", "背景模糊", "背景与色调", 0.0, 600.0, 500.0, "次级远景背景层的散焦模糊半径（对主体清晰度影响较弱）"),
        Field("darker", "暗部压低", "背景与色调", 0.0, 1.0, 0.12, "对暗部区域的压暗强度，用于增强层次"),
        Field("darker_low", "暗部范围起点", "背景与色调", 0.0, 1.0, 0.0, "暗部压低生效的亮度下限"),
        Field("darker_high", "暗部范围终点", "背景与色调", 0.0, 1.0, 1.0, "暗部压低生效的亮度上限"),
        Field("bg_saturation", "背景饱和度", "背景与色调", 0.0, 2.0, 1.0, "混合前玻璃背后采样背景的色彩饱和度"),
        Field("bg_brightness", "背景亮度", "背景与色调", 0.0, 2.0, 1.0, "混合前玻璃背后采样背景的亮度"),
        Field("saturation", "玻璃饱和度", "背景与色调", 0.0, 2.0, 1.0, "最终输出玻璃画面的色彩饱和度"),
        Field("brightness", "玻璃亮度", "背景与色调", 0.0, 2.0, 1.0, "最终输出玻璃画面的整体亮度"),
        Field("inner_white", "内层提白", "背景与色调", 0.0, 1.0, 0.0, "玻璃内部叠加白色雾面的强度"),
        Field("inner_mix", "内层混合", "背景与色调", 0.0, 1.0, 0.0, "内层混合效果的权重"),
        Field("luminance_amount", "亮度曲线强度", "背景与色调", 0.0, 1.0, 0.0, "应用 4 点亮度重映射曲线的混合强度"),
        Field("luminance_c0", "亮度曲线控制点 1", "背景与色调", -2.0, 2.0, 0.0, "亮度多项式重映射曲线第 1 控制点"),
        Field("luminance_c1", "亮度曲线控制点 2", "背景与色调", -2.0, 2.0, 0.0, "亮度多项式重映射曲线第 2 控制点"),
        Field("luminance_c2", "亮度曲线控制点 3", "背景与色调", -2.0, 2.0, 1.0, "亮度多项式重映射曲线第 3 控制点"),
        Field("luminance_c3", "亮度曲线控制点 4", "背景与色调", -2.0, 2.0, 0.0, "亮度多项式重映射曲线第 4 控制点"),
        Field("burn", "内层曲线", "背景与色调", 0.1, 4.0, 1.0, "内层明暗对比的幂次曲线"),
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

    fun configLines(
        overrides: Map<String, Double>,
        keyPrefix: String = "glass_native_",
    ): List<String>? {
        if (overrides.any { (id, value) ->
                val field = byId[id]
                field == null || !value.isFinite() || value !in field.min..field.max
            }
        ) return null
        return fields.asSequence()
            .filter { it.id in overrides }
            .map { "${keyPrefix}${it.id}=${format(overrides.getValue(it.id))}" }
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
