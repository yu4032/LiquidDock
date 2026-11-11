package com.hellovoid.liquiddock.os4

import android.content.Context
import android.util.Log
import org.tukaani.xz.XZInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Decode Dart AOT .gnu_debugdata only in the LiquidDock app's background
 * config worker, never on the Launcher startup path. Exact ELF and function
 * checks are optional and follow the native hook safety setting.
 */
internal object Os4GlassSymbols {
    private const val TAG = "LiquidDock-OS4Glass"
    private const val LIB = "lib/arm64-v8a/libapp.so"
    private const val EXPECTED_SIZE = 29_980_112L
    private const val EXPECTED_SHA =
        "93e511989202a70d38b8231c3133167b830ca7609b61ba6ad133898737d0e0ef"
    private const val MAX_XZ = 8 * 1024 * 1024
    private const val MAX_ELF = 16 * 1024 * 1024
    private val names = listOf(
        "BlurUtilities.isGlassMaterialSupported",
        "BlurUtilities.isHyperMaterialSupported",
        "BlurUtilities.isMaterialDisabled",
    )
    private val pipelineNames = listOf(
        "_DockGlassWidgetState._buildLightGlassFilter",
        "_DockGlassWidgetState._buildDarkGlassFilter",
        "ImageFilterCompat.glass",
        "GlassLightConfig.applyTo",
    )
    private val renderGateNames = listOf(
        "BlurUtilities.isBlurSupported",
        "BlurUtilities.isMamlBlurSupported",
        "BlurUtilities.isAnyMaterialActive",
        "BlurUtilities.isWidgetMaterialActive",
        "BlurUtilities.isThreeLayerBlurSupported",
        "BlurUtilities.isFolderBlurSupported",
        "WidgetMaterialUtils.isGlassOs4SupportedSync",
        "GlassPressHighlightWidget.isSupported",
        "_HyperMaterialWidgetState._computeEffective",
    )
    private const val PASS_BLUR_SUPPORTED = "PassBlurBackgroundLight.supported"
    @Volatile private var cached: Pair<Pair<String, Boolean>, String>? = null

    fun fromInstalledLauncher(context: Context, strictFingerprint: Boolean): String? = runCatching {
        val source = context.packageManager.getApplicationInfo("com.miui.home", 0).sourceDir
        val cacheKey = source to strictFingerprint
        cached?.takeIf { pair -> pair.first == cacheKey }?.second?.let { result -> return result }
        val image = ZipFile(source).use { zip ->
            val entry = zip.getEntry(LIB) ?: error("Launcher has no Dart AOT libapp.so")
            if (strictFingerprint) require(entry.size == EXPECTED_SIZE) { "Vendor ELF changed size" }
            require(entry.size in 1L..64L * 1024L * 1024L) { "Vendor ELF size is invalid" }
            zip.getInputStream(entry).use { input -> input.readBytes() }
        }
        require(image.isNotEmpty())
        if (strictFingerprint) {
            require(image.size.toLong() == EXPECTED_SIZE)
            val actual = MessageDigest.getInstance("SHA-256").digest(image)
            val hex = actual.joinToString("") { byte -> "%02x".format(byte) }
            require(hex == EXPECTED_SHA) { "Vendor ELF SHA-256 differs" }
        }
        val result = extract(image) ?: error("XZ mini-ELF symbols missing or ambiguous")
        cached = cacheKey to result
        Log.i(TAG, "Resolved OS4 glass targets: " + result)
        result
    }.onFailure { err -> Log.e(TAG, "OS4 glass target extraction failed", err) }.getOrNull()

    fun extract(vendor: ByteArray): String? {
        val outer = Elf64(vendor)
        val debug = outer.sections().firstOrNull { section ->
            outer.sectionName(section) == ".gnu_debugdata"
        } ?: return null
        if (debug.size !in 1L..MAX_XZ.toLong()) return null
        val compressed = outer.sectionBytes(debug) ?: return null
        val mini = XZInputStream(ByteArrayInputStream(compressed)).use { xz ->
            val out = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val count = xz.read(chunk)
                if (count < 0) break
                require(out.size() <= MAX_ELF - count) { "Mini-ELF too large" }
                out.write(chunk, 0, count)
            }
            out.toByteArray()
        }
        return fromMiniElf(mini)
    }

    /** Isolated parser can be exercised against synthetic ELF in unit tests. */
    fun fromMiniElf(data: ByteArray): String? {
        val elf = Elf64(data)
        val sections = elf.sections()
        val found = names.map { requested ->
            val hits = mutableSetOf<Pair<Long, Long>>()
            for (table in sections) {
                if (table.type != 2L || table.link !in sections.indices ||
                    table.entrySize != 24L) continue
                val strings = sections[table.link]
                if (strings.type != 3L || table.size / 24L > 200_000L) continue
                for (i in 0 until (table.size / 24L).toInt()) {
                    val pos = table.offset + i * 24L
                    val strOff = elf.u32(pos)
                    val type = elf.u8(pos + 4) and 15
                    val shndx = elf.u16(pos + 6)
                    val vaddr = elf.u64(pos + 8)
                    val size = elf.u64(pos + 16)
                    if (strOff == 0L || (type != 0 && type != 2) ||
                        shndx == 0 || vaddr < 0x4000L || vaddr % 4L != 0L ||
                        size !in 16L..2048L) continue
                    if (elf.equalsString(strings, strOff, requested))
                        hits.add(vaddr to size)
                }
            }
            hits.singleOrNull() ?: return null
        }
        if (found.map { pair -> pair.first }.distinct().size != 3) return null
        fun format(pairs: List<Pair<Long, Long>>) = pairs.joinToString(",") { pair ->
            "0x" + pair.first.toString(16) + ":" + pair.second
        }
        val gate = format(found)
        val probes = mutableListOf<Pair<Long, Long>>()
        var pipelineComplete = true
        for (requested in pipelineNames) {
            val hits = mutableSetOf<Pair<Long, Long>>()
            for (table in sections) {
                if (table.type != 2L || table.link !in sections.indices ||
                    table.entrySize != 24L) continue
                val strings = sections[table.link]
                if (strings.type != 3L || table.size / 24L > 200_000L) continue
                for (i in 0 until (table.size / 24L).toInt()) {
                    val pos = table.offset + i * 24L
                    val strOff = elf.u32(pos)
                    val kind = elf.u8(pos + 4) and 15
                    val section = elf.u16(pos + 6)
                    val value = elf.u64(pos + 8)
                    val size = elf.u64(pos + 16)
                    if (strOff == 0L || (kind != 0 && kind != 2) || section == 0 ||
                        value < 0x4000 || value % 4 != 0L || size !in 16L..2048L) continue
                    if (elf.equalsString(strings,strOff,requested)) hits.add(value to size)
                }
            }
            val hit = hits.singleOrNull()
            if (hit == null) {
                pipelineComplete = false
                break
            }
            probes.add(hit)
        }
        val pipelineTargets = pipelineComplete &&
            (found + probes).map { pair -> pair.first }.distinct().size == 7
        val pipeline = if (pipelineTargets) format(probes) else ""
        val passBlurHits = mutableSetOf<Pair<Long, Long>>()
        for (table in sections) {
            if (table.type != 2L || table.link !in sections.indices ||
                table.entrySize != 24L) continue
            val strings = sections[table.link]
            if (strings.type != 3L || table.size / 24L > 200_000L) continue
            for (i in 0 until (table.size / 24L).toInt()) {
                val pos = table.offset + i * 24L
                val strOff = elf.u32(pos)
                val type = elf.u8(pos + 4) and 15
                val shndx = elf.u16(pos + 6)
                val vaddr = elf.u64(pos + 8)
                val size = elf.u64(pos + 16)
                if (strOff == 0L || (type != 0 && type != 2) || shndx == 0 ||
                    vaddr < 0x4000L || vaddr % 4L != 0L || size !in 16L..2048L) continue
                if (elf.equalsString(strings, strOff, PASS_BLUR_SUPPORTED))
                    passBlurHits.add(vaddr to size)
            }
        }
        val passBlur = passBlurHits.singleOrNull()
        val passBlurTarget = passBlur?.takeIf { target ->
            found.none { pair -> pair.first == target.first } &&
                probes.none { pair -> pair.first == target.first }
        }?.let { target -> format(listOf(target)) } ?: ""
        val result = gate + "|" + pipeline + "|" + passBlurTarget
        val renderGates = mutableListOf<Pair<Long, Long>>()
        var renderGatesComplete = true
        for (requested in renderGateNames) {
            val hits = mutableSetOf<Pair<Long, Long>>()
            for (table in sections) {
                if (table.type != 2L || table.link !in sections.indices ||
                    table.entrySize != 24L) continue
                val strings = sections[table.link]
                if (strings.type != 3L || table.size / 24L > 200_000L) continue
                for (i in 0 until (table.size / 24L).toInt()) {
                    val pos = table.offset + i * 24L
                    val strOff = elf.u32(pos)
                    val type = elf.u8(pos + 4) and 15
                    val shndx = elf.u16(pos + 6)
                    val vaddr = elf.u64(pos + 8)
                    val size = elf.u64(pos + 16)
                    if (strOff == 0L || (type != 0 && type != 2) || shndx == 0 ||
                        vaddr < 0x4000L || vaddr % 4L != 0L || size !in 16L..2048L) continue
                    if (elf.equalsString(strings, strOff, requested)) hits.add(vaddr to size)
                }
            }
            val hit = hits.singleOrNull()
            if (hit == null) {
                renderGatesComplete = false
                break
            }
            renderGates.add(hit)
        }
        val allTargets = found + probes + listOfNotNull(passBlur) + renderGates
        if (!renderGatesComplete || renderGates.size != renderGateNames.size ||
            allTargets.map { pair -> pair.first }.distinct().size != allTargets.size)
            return result + "||"
        return result + "|" + format(renderGates)
    }

    private class Elf64(private val data: ByteArray) {
        data class Section(
            val name: Long, val type: Long, val offset: Long, val size: Long,
            val link: Int, val entrySize: Long,
        )
        private val b = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)
        init {
            require(data.size >= 64 && data[0] == 0x7f.toByte()
                && data[1] == 69.toByte() && data[2] == 76.toByte()
                && data[3] == 70.toByte() && data[4] == 2.toByte()
                && data[5] == 1.toByte()) { "Invalid ELF64 header" }
        }
        private fun range(pos: Long, len: Long) {
            require(pos >= 0 && len >= 0 && pos <= data.size.toLong()
                && len <= data.size.toLong() - pos) { "Out-of-bounds ELF field" }
        }
        fun u8(pos: Long): Int { range(pos,1); return data[pos.toInt()].toInt() and 255 }
        fun u16(pos: Long): Int { range(pos,2); return b.getShort(pos.toInt()).toInt() and 65535 }
        fun u32(pos: Long): Long { range(pos,4); return b.getInt(pos.toInt()).toLong() and 0xffffffffL }
        fun u64(pos: Long): Long { range(pos,8); return b.getLong(pos.toInt()) }
        private lateinit var table: List<Section>
        fun sections(): List<Section> {
            if (::table.isInitialized) return table
            val base = u64(40)
            val stride = u16(58)
            val count = u16(60)
            require(count in 1..4096 && stride == 64)
            range(base, count.toLong() * 64L)
            table = (0 until count).map { i ->
                val p = base + i * 64L
                Section(u32(p),u32(p+4),u64(p+24),u64(p+32),
                    u32(p+40).toInt(),u64(p+56))
            }
            return table
        }
        fun sectionBytes(s: Section): ByteArray? {
            if (s.offset < 0 || s.size < 0 || s.offset > data.size.toLong()
                || s.size > data.size.toLong() - s.offset) return null
            return data.copyOfRange(s.offset.toInt(),(s.offset+s.size).toInt())
        }
        fun equalsString(s: Section, offset: Long, target: String): Boolean {
            if (offset < 0 || offset >= s.size || s.offset < 0 ||
                s.offset > data.size || s.size > data.size - s.offset ||
                target.length.toLong() >= s.size - offset) return false
            val start = s.offset + offset
            for (i in target.indices)
                if (data[(start+i).toInt()].toInt() != target[i].code) return false
            return data[(start+target.length).toInt()] == 0.toByte()
        }
        fun sectionName(s: Section): String? {
            val strings = sections().getOrNull(u16(62)) ?: return null
            if (strings.type != 3L || s.name >= strings.size ||
                strings.offset < 0 || strings.offset > data.size ||
                strings.size > data.size-strings.offset) return null
            val start = strings.offset + s.name
            var end = start
            val limit = minOf(strings.offset+strings.size,data.size.toLong())
            while(end < limit && end-start < 128L && data[end.toInt()] != 0.toByte()) end++
            if(end >= limit || end-start >= 128) return null
            return String(data,start.toInt(),(end-start).toInt(),Charsets.US_ASCII)
        }
    }
}
