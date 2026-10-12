package com.shibill.app.data.backup

import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * 最小 xlsx 读取器：仅用 JDK 标准库（ZipInputStream + DOM）读取第一个工作表。
 *
 * 替代方案说明（见交付说明"分歧裁定"第 4 条）：
 * 方案 §2 首选 Apache POI-Android，但 POI 5.x 在 Android 上依赖大量 java.awt/xmlbeans 类，
 * 集成脆弱且显著增大包体；§8.3 允许"POI 集成困难时降级"。
 * 本实现零第三方依赖、离线可用，完整满足 M4"微信 xlsx 导入"要求。
 *
 * 支持：sharedStrings / inlineStr / str / 数值单元格；按单元格 r 属性（如 "B3"）定位列。
 */
object XlsxReader {

    /** 读取第一个工作表为二维字符串表（含表头行） */
    fun readFirstSheet(input: InputStream): List<List<String>> {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(input.readBytes())).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                val name = entry.name
                if (name == "xl/sharedStrings.xml" ||
                    (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml") && !entries.containsKey(SHEET_KEY))
                ) {
                    val key = if (name == "xl/sharedStrings.xml") name else SHEET_KEY
                    entries[key] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val sheetBytes = entries[SHEET_KEY]
            ?: throw IllegalArgumentException("无法读取 xlsx 工作表，请确认文件为微信导出的 xlsx 账单")
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) }.orEmpty()
        return parseSheet(sheetBytes, sharedStrings)
    }

    private const val SHEET_KEY = "xl/worksheets/sheet1.xml"

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val document = parseXml(bytes)
        val nodes = document.getElementsByTagNameNS("*", "si")
        val result = mutableListOf<String>()
        for (i in 0 until nodes.length) {
            val texts = mutableListOf<String>()
            collectText(nodes.item(i), texts)
            result.add(texts.joinToString(""))
        }
        return result
    }

    private fun collectText(node: Node, out: MutableList<String>) {
        if (node is Element && node.localName == "t") {
            out.add(node.textContent ?: "")
            return
        }
        val children = node.childNodes
        for (i in 0 until children.length) {
            collectText(children.item(i), out)
        }
    }

    private fun parseSheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val document = parseXml(bytes)
        val rowNodes = document.getElementsByTagNameNS("*", "row")
        val result = mutableListOf<List<String>>()
        for (i in 0 until rowNodes.length) {
            val rowNode = rowNodes.item(i)
            val cellNodes = rowNode.childNodes
            val row = mutableListOf<String>()
            for (j in 0 until cellNodes.length) {
                val cell = cellNodes.item(j)
                if (cell !is Element || cell.localName != "c") continue
                val columnIndex = columnIndex(cell.getAttribute("r"))
                if (columnIndex >= 0) {
                    while (row.size < columnIndex) row.add("")
                    row.add(cellValue(cell, sharedStrings))
                } else {
                    row.add(cellValue(cell, sharedStrings))
                }
            }
            result.add(row)
        }
        return result
    }

    /** "B3" → 1；无引用返回 -1 */
    private fun columnIndex(reference: String): Int {
        var value = 0
        var found = false
        for (ch in reference) {
            if (ch.isLetter()) {
                value = value * 26 + (ch.uppercaseChar() - 'A' + 1)
                found = true
            } else {
                break
            }
        }
        return if (found) value - 1 else -1
    }

    private fun cellValue(cell: Element, sharedStrings: List<String>): String {
        val type = cell.getAttribute("t")
        var valueNode: Element? = null
        var inlineNode: Element? = null
        val children = cell.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element) {
                when (child.localName) {
                    "v" -> valueNode = child
                    "is" -> inlineNode = child
                }
            }
        }
        return when (type) {
            "s" -> {
                val index = valueNode?.textContent?.trim()?.toIntOrNull()
                if (index != null) sharedStrings.getOrNull(index).orEmpty() else ""
            }

            "inlineStr" -> {
                val texts = mutableListOf<String>()
                inlineNode?.let { collectText(it, texts) }
                texts.joinToString("")
            }

            "str" -> valueNode?.textContent ?: ""

            else -> valueNode?.textContent ?: ""
        }
    }

    private fun parseXml(bytes: ByteArray): Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // 防御 XXE：禁用外部实体（部分平台实现不支持该 feature，失败时忽略）
            runCatching {
                setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            }
            runCatching {
                setFeature("http://xml.org/sax/features/external-general-entities", false)
            }
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }
}
