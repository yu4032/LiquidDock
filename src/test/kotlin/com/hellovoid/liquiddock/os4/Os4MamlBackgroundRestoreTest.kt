package com.hellovoid.liquiddock.os4

import com.hellovoid.liquiddock.WidgetComponentStore
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Os4MamlBackgroundRestoreTest {
    @Test fun turningOffGlassRestoresBackgroundButKeepsExplicitTextHide() {
        val directory = Files.createTempDirectory("os4-maml-restore").toFile()
        try {
            val source = directory.resolve("source.zip")
            val glassOn = directory.resolve("glass-on.zip")
            val glassOff = directory.resolve("glass-off.zip")
            val manifest = """
                <Widget productId="example" w="100" h="100">
                    <Layer>
                        <Rectangle name="background" x="0" y="0" w="100" h="100"/>
                        <Text name="custom_label" x="5" y="5" w="50" h="10" text="hello"/>
                    </Layer>
                </Widget>
            """.trimIndent()
            ZipOutputStream(source.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("manifest.xml"))
                out.write(manifest.toByteArray(Charsets.UTF_8))
                out.closeEntry()
            }
            val owner = "os4:example:widget_2x2"
            val nodes = Os4MamlArchive.inspect(Os4MamlArchive.FileLike(source), owner).nodes
            val background = nodes.single { it.name == "background" }
            val label = nodes.single { it.name == "custom_label" }
            val selected = listOf(background, label)
            val glassOnPaths = selected.map { it.hierarchyPath }.toSet()
            val glassOffPaths = selected.mapNotNull { node ->
                val descriptor = WidgetComponentStore.parseCatalog(node.catalog(owner))!!
                node.hierarchyPath.takeIf { Os4MamlHidePolicy.shouldApply(descriptor, false) }
            }.toSet()

            assertFalse(background.hierarchyPath in glassOffPaths)
            assertTrue(label.hierarchyPath in glassOffPaths)
            assertTrue(Os4MamlArchive.rewrite(
                Os4MamlArchive.FileLike(source), Os4MamlArchive.FileLike(glassOn),
                glassOnPaths, true
            ))
            assertTrue(Os4MamlArchive.rewrite(
                Os4MamlArchive.FileLike(source), Os4MamlArchive.FileLike(glassOff),
                glassOffPaths, false
            ))

            fun readDocument(file: java.io.File) = ZipFile(file).use { zip ->
                zip.getInputStream(zip.getEntry("manifest.xml")).use { input ->
                    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
                }
            }
            val on = readDocument(glassOn)
            val off = readDocument(glassOff)
            assertEquals("0", (on.getElementsByTagName("Rectangle").item(0) as org.w3c.dom.Element)
                .getAttribute("visibility"))
            assertEquals("", (off.getElementsByTagName("Rectangle").item(0) as org.w3c.dom.Element)
                .getAttribute("visibility"))
            assertEquals("0", (off.getElementsByTagName("Text").item(0) as org.w3c.dom.Element)
                .getAttribute("visibility"))
            assertTrue(on.getElementsByTagName("GlassConfig").length > 0)
            assertEquals(0, off.getElementsByTagName("GlassConfig").length)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test fun unifiesExistingStrokeMaterialToClockGlassMaterial() {
        val directory = Files.createTempDirectory("os4-maml-clock-unify").toFile()
        try {
            val source = directory.resolve("source.zip")
            val glassOn = directory.resolve("glass-on.zip")
            val manifest = """
                <Widget productId="weather" w="400" h="200" supportBackgroundBlur="false">
                    <Layer w="#view_width" h="#view_height">
                        <MaterialToken tokenName="Glass_Stroke_Regular" version="30">
                            <BloomStrokeConfig disableBloomStroke="not(#hyper_material_bloom_stroke_enable)||#isInPicker"/>
                            <ColorBlendConfig disableBlend="0"/>
                        </MaterialToken>
                    </Layer>
                </Widget>
            """.trimIndent()
            ZipOutputStream(source.outputStream()).use { out ->
                out.putNextEntry(ZipEntry("manifest.xml"))
                out.write(manifest.toByteArray(Charsets.UTF_8))
                out.closeEntry()
            }

            assertTrue(Os4MamlArchive.rewrite(
                Os4MamlArchive.FileLike(source), Os4MamlArchive.FileLike(glassOn),
                emptySet(), true,
            ))

            val doc = ZipFile(glassOn).use { zip ->
                zip.getInputStream(zip.getEntry("manifest.xml")).use { input ->
                    DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(input)
                }
            }
            assertEquals("false", doc.documentElement.getAttribute("supportBackgroundBlur"))
            val token = doc.getElementsByTagName("MaterialToken").item(0) as org.w3c.dom.Element
            assertEquals("clock_classic_material", token.getAttribute("tokenName"))
            assertEquals("30", token.getAttribute("version"))
            val bloom = doc.getElementsByTagName("BloomStrokeConfig").item(0) as org.w3c.dom.Element
            assertEquals("1", bloom.getAttribute("disableBloomStroke"))
            val blend = doc.getElementsByTagName("ColorBlendConfig").item(0) as org.w3c.dom.Element
            assertEquals("1", blend.getAttribute("disableBlend"))
            val blur = doc.getElementsByTagName("BackgroundBlurConfig").item(0) as org.w3c.dom.Element
            assertEquals("0", blur.getAttribute("blurBgMode"))
            assertEquals("0", blur.getAttribute("disableBlur"))
            assertEquals("true", blur.getAttribute("passWindow"))
            assertEquals("true", blur.getAttribute("rustPassWindow"))
            assertEquals("true", blur.getAttribute("reuseContainer"))
            val glass = doc.getElementsByTagName("GlassConfig").item(0) as org.w3c.dom.Element
            assertEquals("0", glass.getAttribute("disableGlass"))
        } finally {
            directory.deleteRecursively()
        }
    }
}
