package com.hellovoid.liquiddock.os4

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.StringReader
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/** Secure, bounded MAML archive inspection and rewrite helpers. */
internal object Os4MamlArchive {
    const val MAX_ARCHIVE_BYTES = 128L * 1024L * 1024L
    const val MAX_TOTAL_UNCOMPRESSED = 256L * 1024L * 1024L
    const val MAX_MANIFEST_BYTES = 4 * 1024 * 1024
    const val MAX_XML_NODES = 8192
    private const val MAX_ZIP_ENTRIES = 8192

    private val renderedTags = setOf(
        "Group", "Layer", "Image", "Text", "Rectangle", "Circle", "Arc", "Line",
        "Button", "ImageNumber", "Time", "DateTime", "ProgressBar", "Number",
        "List", "LinearLayout", "FrameLayout", "VirtualScreen", "WebView", "Video",
        "BitmapFont", "BitmapText", "Slider", "CheckBox", "RadioButton", "MusicControl",
    )
    private val logicTags = setOf(
        "VariableBinders", "Binders", "Commands", "Triggers", "Variables", "VariableUpdater",
        "Animations", "Animation", "Transition", "Position", "Size", "Alpha", "Rotation",
        "Scale", "Touch", "Command", "IntentCommand", "FolmeCommand", "VariableCommand",
    )

    data class NodeInfo(
        val name: String,
        val tag: String,
        val className: String,
        val hierarchyPath: String,
        val componentType: String,
    ) {
        fun selector(owner: String): String = listOf(
            "M2", owner, name, className, hierarchyPath,
        ).joinToString("\t")

        fun catalog(owner: String): String = listOf(
            "M2", owner, name, className, hierarchyPath, "", componentType,
        ).joinToString("\t")
    }

    data class Inspection(val nodes: List<NodeInfo>, val productId: String)

    fun inspect(zip: FileLike, owner: String): Inspection {
        require(zip.length <= MAX_ARCHIVE_BYTES) { "MAML ZIP exceeds 128 MiB" }
        ZipFile(zip.file).use { archive ->
            var entryCount = 0
            var total = 0L
            var manifest: ZipEntry? = null
            val entries = archive.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                entryCount++
                require(entryCount <= MAX_ZIP_ENTRIES) { "MAML ZIP has too many entries" }
                require(!entry.name.startsWith("/") &&
                    !entry.name.split('/').any { it == ".." || it == "." }) {
                    "Unsafe entry in MAML ZIP"
                }
                require(entry.size >= 0) { "Unknown ZIP entry size" }
                total += entry.size
                require(total <= MAX_TOTAL_UNCOMPRESSED) { "MAML ZIP expands beyond 256 MiB" }
                if (entry.name == "manifest.xml") {
                    require(manifest == null) { "Duplicate manifest.xml" }
                    manifest = entry
                }
            }
            val manifestEntry = requireNotNull(manifest) { "MAML ZIP has no manifest.xml" }
            require(manifestEntry.size in 1..MAX_MANIFEST_BYTES.toLong()) { "Invalid manifest size" }
            val bytes = archive.getInputStream(manifestEntry).use { it.readBounded(MAX_MANIFEST_BYTES) }
            val document = parseManifest(bytes)
            val product = document.documentElement.getAttribute("productId")
                .ifBlank { owner.substringAfter(':').substringBeforeLast(':') }
            require(product.isNotBlank() && isSafeField(product)) { "Missing or invalid MAML product id" }
            val nodes = ArrayList<NodeInfo>()
            val nodeCounter = intArrayOf(0)
            walkRendered(document.documentElement, "os4xml", nodes, nodeCounter)
            require(nodeCounter[0] <= MAX_XML_NODES) { "MAML manifest has too many XML nodes" }
            return Inspection(nodes, product)
        }
    }

    /** Applies exact source-tree paths and optionally adds native glass, always starting at source. */
    fun rewrite(
        source: FileLike,
        output: FileLike,
        selectedPaths: Set<String>,
        addGlass: Boolean,
    ): Boolean {
        require(source.length <= MAX_ARCHIVE_BYTES) { "MAML ZIP exceeds 128 MiB" }
        ZipFile(source.file).use { archive ->
            var total = 0L
            var count = 0
            val allEntries = ArrayList<ZipEntry>()
            var manifest: ZipEntry? = null
            val enumeration = archive.entries()
            while (enumeration.hasMoreElements()) {
                val entry = enumeration.nextElement()
                count++
                require(count <= MAX_ZIP_ENTRIES) { "MAML ZIP has too many entries" }
                require(!entry.name.startsWith("/") &&
                    !entry.name.split('/').any { it == ".." || it == "." }) {
                    "Unsafe entry in MAML ZIP"
                }
                require(entry.size >= 0) { "Unknown ZIP entry size" }
                total += entry.size
                require(total <= MAX_TOTAL_UNCOMPRESSED) { "MAML ZIP expands beyond 256 MiB" }
                allEntries.add(entry)
                if (entry.name == "manifest.xml") {
                    require(manifest == null) { "Duplicate manifest.xml" }
                    manifest = entry
                }
            }
            val manifestEntry = requireNotNull(manifest) { "MAML ZIP has no manifest.xml" }
            require(manifestEntry.size in 1..MAX_MANIFEST_BYTES.toLong()) { "Invalid manifest size" }
            val document = parseManifest(
                archive.getInputStream(manifestEntry).use { it.readBounded(MAX_MANIFEST_BYTES) },
            )
            var changed = applySelections(document.documentElement, selectedPaths)
            if (addGlass) changed = ensureGlass(document) || changed
            if (!changed) return false
            val manifestBytes = serializeManifest(document)

            ZipOutputStream(output.file.outputStream().buffered()).use { out ->
                var copiedUncompressed = manifestBytes.size.toLong()
                for (entry in allEntries) {
                    val replacement = ZipEntry(entry.name).apply {
                        time = entry.time
                        comment = entry.comment
                        extra = entry.extra
                        if (entry.method == ZipEntry.STORED) {
                            size = if (entry.name == "manifest.xml") manifestBytes.size.toLong() else entry.size
                            compressedSize = size
                            crc = if (entry.name == "manifest.xml") crc32(manifestBytes) else entry.crc
                            method = ZipEntry.STORED
                        } else method = ZipEntry.DEFLATED
                    }
                    out.putNextEntry(replacement)
                    if (entry.isDirectory) {
                        // Directory entries carry no payload.
                    } else if (entry.name == "manifest.xml") {
                        out.write(manifestBytes)
                    } else {
                        archive.getInputStream(entry).use { input ->
                            copiedUncompressed += input.copyBoundedTo(out, MAX_TOTAL_UNCOMPRESSED - copiedUncompressed)
                        }
                    }
                    out.closeEntry()
                }
            }
            return true
        }
    }

    fun productIdFromPath(path: String): String? {
        val root = path.substringBeforeLast('/')
        val relative = root.substringAfter("/maml/res/", "")
        if (relative.isEmpty()) return null
        val parts = relative.split('/').filter(String::isNotBlank)
        val candidates = parts.filterNot { it == "0" || it == "1" }
        return candidates.lastOrNull()?.takeIf(::isSafeField)
    }

    fun isRenderedTag(tag: String): Boolean = tag in renderedTags

    private fun parseManifest(bytes: ByteArray): Document {
        val declarationProbe = String(bytes, Charsets.ISO_8859_1).replace("\u0000", "").uppercase()
        require("<!DOCTYPE" !in declarationProbe && "<!ENTITY" !in declarationProbe) {
            "DOCTYPE and entity declarations are not allowed in MAML manifests"
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isXIncludeAware = false
            isExpandEntityReferences = false
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> throw SecurityException("External XML entity rejected") }
        }
        val doc = builder.parse(ByteArrayInputStream(bytes))
        var count = 0
        fun countNodes(node: Node) {
            if (node.nodeType == Node.ELEMENT_NODE || node.nodeType == Node.TEXT_NODE || node.nodeType == Node.COMMENT_NODE) {
                count++
                require(count <= MAX_XML_NODES) { "MAML manifest has too many XML nodes" }
            }
            val children = node.childNodes
            for (i in 0 until children.length) countNodes(children.item(i))
        }
        countNodes(doc)
        return doc
    }

    private fun serializeManifest(document: Document): ByteArray {
        val transformerFactory = TransformerFactory.newInstance().apply {
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalStylesheet", "") }
        }
        val transformer = transformerFactory.newTransformer().apply {
            setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        }
        return ByteArrayOutputStream().use { output ->
            transformer.transform(DOMSource(document), StreamResult(output))
            output.toByteArray().also { require(it.size <= MAX_MANIFEST_BYTES) { "Rewritten manifest exceeds 4 MiB" } }
        }
    }

    private fun walkRendered(parent: Element, path: String, out: MutableList<NodeInfo>, count: IntArray) {
        val children = parent.childNodes
        var elementIndex = 0
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType != Node.ELEMENT_NODE) continue
            count[0]++
            require(count[0] <= MAX_XML_NODES) { "MAML manifest has too many XML nodes" }
            val element = child as Element
            val tag = elementTag(element)
            val childPath = "$path/$elementIndex"
            elementIndex++
            if (tag in logicTags || !isRenderedTag(tag)) continue
            val name = element.getAttribute("name").takeIf(::isSafeField).orEmpty()
            out += NodeInfo(name, tag, "${tag}ScreenElement", childPath, classify(tag))
            walkRendered(element, childPath, out, count)
        }
    }

    private fun applySelections(root: Element, selectedPaths: Set<String>): Boolean {
        var changed = false
        fun visit(parent: Element, path: String) {
            val children = parent.childNodes
            var index = 0
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child.nodeType != Node.ELEMENT_NODE) continue
                val element = child as Element
                val childPath = "$path/$index"
                index++
                val tag = elementTag(element)
                if (tag in logicTags || !isRenderedTag(tag)) continue
                if (childPath in selectedPaths && element.getAttribute("visibility") != "0") {
                    element.setAttribute("visibility", "0")
                    changed = true
                }
                visit(element, childPath)
            }
        }
        visit(root, "os4xml")
        return changed
    }

    private fun ensureGlass(document: Document): Boolean {
        val root = document.documentElement
        val layers = findElements(root, "Layer")
        val allMaterials = layers.flatMap { directChildren(it, "MaterialToken") }
        // Preserve an existing native GlassConfig byte-for-byte instead of replacing an unknown token.
        if (allMaterials.any { directChildren(it, "GlassConfig").isNotEmpty() }) return false
        val material = allMaterials.firstOrNull()
        if (material != null) {
            material.setAttribute("enableBackgroundBlurConfig", "true")
            material.appendChild(document.createElementNS(material.namespaceURI, "GlassConfig").apply {
                setAttribute("token", "'Glass_Common_Medium_Thin_Low'")
                setAttribute("disableGlass", "0")
                setAttribute("rustPassWindow", "true")
            })
            return true
        }

        val layer = document.createElementNS(root.namespaceURI, "Layer").apply {
            setAttribute("frameRate", "-1")
            setAttribute("w", "#view_width")
            setAttribute("h", "#view_height")
            setAttribute("layerType", "bottom")
        }
        val token = document.createElementNS(root.namespaceURI, "MaterialToken").apply {
            setAttribute("tokenName", "Glass_Stroke_Regular")
            setAttribute("version", "30")
            setAttribute("enableBackgroundBlurConfig", "true")
        }
        token.appendChild(document.createElementNS(root.namespaceURI, "GlassConfig").apply {
            setAttribute("token", "'Glass_Common_Medium_Thin_Low'")
            setAttribute("disableGlass", "0")
            setAttribute("rustPassWindow", "true")
        })
        layer.appendChild(token)
        layer.appendChild(document.createElementNS(root.namespaceURI, "ViewOutlineProvider").apply {
            setAttribute("type", "rect")
            setAttribute("left", "0")
            setAttribute("top", "0")
            setAttribute("right", "#view_width")
            setAttribute("bottom", "#view_height")
            setAttribute("radius", "60")
        })
        val moved = ArrayList<Node>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child.nodeType == Node.ELEMENT_NODE && isRenderedTag(elementTag(child as Element))) moved += child
        }
        val first = moved.firstOrNull()
        if (first == null) root.appendChild(layer) else root.insertBefore(layer, first)
        moved.forEach { child -> layer.appendChild(child) }
        return true
    }

    private fun findElements(root: Element, tag: String): List<Element> {
        val found = ArrayList<Element>()
        fun visit(parent: Element) {
            if (elementTag(parent) == tag) found += parent
            val children = parent.childNodes
            for (i in 0 until children.length) {
                val node = children.item(i)
                if (node.nodeType == Node.ELEMENT_NODE) visit(node as Element)
            }
        }
        visit(root)
        return found
    }

    private fun directChildren(parent: Element, tag: String): List<Element> {
        val result = ArrayList<Element>()
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node.nodeType == Node.ELEMENT_NODE && elementTag(node as Element) == tag) result += node
        }
        return result
    }

    private fun elementTag(element: Element): String =
        element.localName ?: element.tagName.substringAfter(':')

    private fun classify(tag: String): String = when (tag) {
        "Image", "ImageNumber" -> "image"
        "Text", "Time", "DateTime", "BitmapText" -> "text"
        "Rectangle", "Circle", "Arc", "Line" -> "background"
        "Group", "Layer", "List", "LinearLayout", "FrameLayout" -> "container"
        "Button", "Slider", "CheckBox", "RadioButton" -> "interactive"
        else -> "other"
    }

    private fun isSafeField(value: String): Boolean =
        value.length <= 256 && value.none { it == '\t' || it == '\n' || it == '\r' }

    private fun InputStream.readBounded(maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            require(total <= maxBytes) { "XML exceeds size limit" }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }

    private fun InputStream.copyBoundedTo(output: OutputStream, maxBytes: Long): Long {
        val buffer = ByteArray(32 * 1024)
        var total = 0L
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            total += n
            require(total <= maxBytes) { "ZIP entry exceeds size limit" }
            output.write(buffer, 0, n)
        }
        return total
    }

    private fun crc32(bytes: ByteArray): Long {
        val crc = java.util.zip.CRC32()
        crc.update(bytes)
        return crc.value
    }

    data class FileLike(val file: java.io.File) {
        val length: Long get() = file.length()
    }
}
