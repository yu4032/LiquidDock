package com.hellovoid.liquiddock.os4

import org.w3c.dom.Element
import org.w3c.dom.Node
import kotlin.math.abs

/** Bounded static geometry analysis. Unresolved runtime values remain unknown. */
internal class Os4MamlMetadata(root: Element) {
    private data class Term(val value: Double, val widthPower: Int = 0, val heightPower: Int = 0) {
        fun times(other: Term, divide: Boolean = false): Term? {
            if (divide && other.value == 0.0) return null
            val result = if (divide) Term(value / other.value, widthPower - other.widthPower, heightPower - other.heightPower)
                else Term(value * other.value, widthPower + other.widthPower, heightPower + other.heightPower)
            return result.takeIf { it.value.isFinite() && abs(it.widthPower) <= 16 && abs(it.heightPower) <= 16 }
        }
        fun plus(other: Term, subtract: Boolean): Term? = when {
            other.value == 0.0 -> this
            value == 0.0 -> other.copy(value = if (subtract) -other.value else other.value)
            widthPower != other.widthPower || heightPower != other.heightPower -> null
            else -> copy(value = value + if (subtract) -other.value else other.value).takeIf { it.value.isFinite() }
        }
        fun scalar(): Double? = value.takeIf { widthPower == 0 && heightPower == 0 && it.isFinite() }
    }
    private val definitions = mutableMapOf<String, String>()
    private val uncertain = mutableSetOf<String>()
    private val cache = mutableMapOf<String, Term?>()
    private val rootWidth = dimension(root, "w", "width")?.let { positiveLiteral(it) }
        ?.let { Term(it) } ?: Term(1.0, 1, 0)
    private val rootHeight = dimension(root, "h", "height")?.let { positiveLiteral(it) }
        ?.let { Term(it) } ?: Term(1.0, 0, 1)
    private val rootArea = rootWidth.times(rootHeight)!!

    init {
        fun collect(element: Element) {
            val tag = element.localName ?: element.tagName.substringAfter(':')
            val name = element.getAttribute("name")
            if (name.isNotBlank()) {
                if (tag == "VariableCommand" || tag == "Variable" ||
                    (tag == "Var" && (element.hasAttribute("size") || element.getElementsByTagName("VariableAnimation").length > 0))) {
                    uncertain += name
                }
                if (tag == "Var" && element.hasAttribute("expression") &&
                    element.getAttribute("type").let { it.isEmpty() || it == "number" || it == "int" }) {
                    val expression = element.getAttribute("expression")
                    val previous = definitions.put(name, expression)
                    if (previous != null) uncertain += name
                }
            }
            val children = element.childNodes
            for (i in 0 until children.length) if (children.item(i).nodeType == Node.ELEMENT_NODE) collect(children.item(i) as Element)
        }
        collect(root)
    }

    fun areaRatio(element: Element): Float {
        val width = dimension(element, "w", "width") ?: return Float.NaN
        val height = dimension(element, "h", "height") ?: return Float.NaN
        val w = evaluate(width, linkedSetOf()) ?: return Float.NaN
        val h = evaluate(height, linkedSetOf()) ?: return Float.NaN
        if (w.value <= 0 || h.value <= 0) return Float.NaN
        val ratio = w.times(h)?.times(rootArea, divide = true)?.scalar() ?: return Float.NaN
        return ratio.coerceIn(0.0, 1.0).toFloat()
    }

    private fun evaluate(expression: String, resolving: MutableSet<String>): Term? {
        if (expression.isBlank() || expression.length > 1024 || resolving.size >= 24) return null
        return runCatching { Parser(expression, resolving).parse() }.getOrNull()
    }
    private fun variable(name: String, resolving: MutableSet<String>): Term? {
        if (name == "view_width") return rootWidth
        if (name == "view_height") return rootHeight
        if (name in uncertain || !resolving.add(name)) return null
        return try {
            if (cache.containsKey(name)) cache[name]
            else definitions[name]?.let { evaluate(it, resolving) }.also { cache[name] = it }
        } finally { resolving.remove(name) }
    }

    private inner class Parser(private val input: String, private val resolving: MutableSet<String>) {
        private var position = 0
        private var nesting = 0
        fun parse(): Term? { val result = sum(); space(); return result.takeIf { position == input.length } }
        private fun space() { while (position < input.length && input[position].isWhitespace()) position++ }
        private fun take(c: Char): Boolean { space(); if (position < input.length && input[position] == c) { position++; return true }; return false }
        private fun sum(): Term? {
            var value = product() ?: return null
            while (true) {
                val subtract = when { take('+') -> false; take('-') -> true; else -> return value }
                value = value.plus(product() ?: return null, subtract) ?: return null
            }
        }
        private fun product(): Term? {
            var value = atom() ?: return null
            while (true) {
                val divide = when { take('*') -> false; take('/') -> true; else -> return value }
                value = value.times(atom() ?: return null, divide) ?: return null
            }
        }
        private fun atom(): Term? {
            if (++nesting > 48) return null
            return try {
                when {
                    take('+') -> atom()
                    take('-') -> atom()?.let { it.copy(value = -it.value) }
                    take('(') -> sum()?.takeIf { take(')') }
                    take('#') -> {
                        val start = position
                        while (position < input.length && (input[position].isLetterOrDigit() || input[position] == '_' || input[position] == '.')) position++
                        variable(input.substring(start, position), resolving)
                    }
                    else -> {
                        space()
                        val match = number.find(input, position)?.takeIf { it.range.first == position } ?: return null
                        position += match.value.length
                        match.value.toDoubleOrNull()?.takeIf(Double::isFinite)?.let { Term(it) }
                    }
                }
            } finally { nesting-- }
        }
    }
    private companion object {
        val number = Regex("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")
        fun dimension(element: Element, short: String, long: String): String? =
            element.getAttribute(short).ifBlank { element.getAttribute(long) }.takeIf { it.isNotBlank() }
        fun positiveLiteral(value: String): Double? = value.trim().toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
    }
}
