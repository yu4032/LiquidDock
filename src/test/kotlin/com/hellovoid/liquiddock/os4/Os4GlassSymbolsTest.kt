package com.hellovoid.liquiddock.os4

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Os4GlassSymbolsTest {
    private val target = "0x9000:88,0x9400:140,0x9800:88"
    private val names = listOf(
        "BlurUtilities.isGlassMaterialSupported",
        "BlurUtilities.isHyperMaterialSupported",
        "BlurUtilities.isMaterialDisabled",
    )

    private fun buffer(bytes: ByteArray) = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

    private fun header(bytes: ByteArray, sections: Int, shstr: Int): ByteBuffer {
        val b=buffer(bytes)
        b.put(0,0x7f); b.put(1,'E'.code.toByte()); b.put(2,'L'.code.toByte())
        b.put(3,'F'.code.toByte()); b.put(4,2); b.put(5,1)
        b.putLong(40,128); b.putShort(58,64); b.putShort(60,sections.toShort())
        b.putShort(62,shstr.toShort())
        return b
    }
    private fun section(b: ByteBuffer, i: Int, name: Int, type: Int,
                        offset: Long, size: Long, link: Int=0, entry: Long=0) {
        val p=128+i*64
        b.putInt(p,name); b.putInt(p+4,type)
        b.putLong(p+24,offset); b.putLong(p+32,size)
        b.putInt(p+40,link); b.putLong(p+56,entry)
    }

    private fun miniElf(): ByteArray {
        val out=ByteArray(2048)
        val b=header(out,4,3)
        val shnames="\u0000.symtab\u0000.strtab\u0000.shstrtab\u0000".toByteArray()
        shnames.copyInto(out,960)
        val stringTable=ByteArrayOutputStream().apply { write(0) }
        val offsets=names.map { n ->
            val at=stringTable.size()
            stringTable.write(n.toByteArray())
            stringTable.write(0)
            at
        }
        val strings=stringTable.toByteArray()
        strings.copyInto(out,768)
        section(b,1,1,2,512,4L*24L,2,24)
        section(b,2,9,3,768,strings.size.toLong())
        section(b,3,17,3,960,shnames.size.toLong())
        for(i in 1..3) {
            val base=512+i*24
            b.putInt(base,offsets[i-1]); b.put(base+4,2)
            b.putShort(base+6,1)
            b.putLong(base+8,0x9000L+(i-1)*0x400)
            b.putLong(base+16,if(i==2)140 else 88)
        }
        return out
    }

    @Test fun resolvesThreeDartAotFunctionsFromMiniElf() {
        assertEquals(target,Os4GlassSymbols.fromMiniElf(miniElf()))
    }

    @Test fun resolvesXzCompressedGnuDebugdata() {
        val compressed=ByteArrayOutputStream()
        XZOutputStream(compressed,LZMA2Options()).use { it.write(miniElf()) }
        val packed=compressed.toByteArray()
        val outer=ByteArray(1024+packed.size)
        val b=header(outer,3,2)
        val strings="\u0000.gnu_debugdata\u0000.shstrtab\u0000".toByteArray()
        strings.copyInto(outer,768)
        packed.copyInto(outer,1024)
        section(b,1,1,1,1024,packed.size.toLong())
        section(b,2,16,3,768,strings.size.toLong())
        assertEquals(target,Os4GlassSymbols.extract(outer))
    }

    @Test fun rejectsAmbiguousAndMissingSymbols() {
        val duplicate=miniElf()
        val b=buffer(duplicate)
        duplicate.copyInto(duplicate,512+4*24,512+24,512+48)
        b.putLong(512+4*24+8,0xb000)
        b.putLong(128+64+32,5L*24L)
        assertNull(Os4GlassSymbols.fromMiniElf(duplicate))
        val missing=miniElf()
        buffer(missing).putInt(512+3*24,0)
        assertNull(Os4GlassSymbols.fromMiniElf(missing))
    }
}
