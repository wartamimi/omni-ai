package com.omniai.core.domain.tools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

private val toolJson = Json { prettyPrint = true; ignoreUnknownKeys = true }

private fun args(argumentsJson: String): JsonObject = try {
    toolJson.parseToJsonElement(argumentsJson.ifBlank { "{}" }).jsonObject
} catch (e: Exception) {
    throw ToolArgumentException("Arguments must be a JSON object.")
}

private fun JsonObject.string(key: String): String =
    (this[key] as? JsonPrimitive)?.content ?: throw ToolArgumentException("Missing argument '$key'.")

private fun JsonObject.optString(key: String): String? = (this[key] as? JsonPrimitive)?.content

/** Arithmetic with + - * / ^ % and parentheses. A hand-written parser: no script engine, no eval. */
class CalculatorTool : LocalTool {
    override val name = "calculator"
    override val description = "Evaluates an arithmetic expression with + - * / ^ % and parentheses. Returns the numeric result."
    override val parametersSchema = """{"type":"object","properties":{"expression":{"type":"string","description":"e.g. (12.5 * 4) / 3"}},"required":["expression"]}"""

    override suspend fun execute(argumentsJson: String): String {
        val expr = args(argumentsJson).string("expression")
        if (expr.length > 500) throw ToolArgumentException("Expression too long.")
        return ExpressionParser(expr).parse().stripTrailingZeros().toPlainString()
    }
}

internal class ExpressionParser(private val input: String) {
    private var pos = 0
    private val mc = MathContext(34, RoundingMode.HALF_EVEN)

    fun parse(): BigDecimal {
        val v = expression()
        skip()
        if (pos < input.length) throw ToolArgumentException("Unexpected '${input[pos]}' at position $pos.")
        return v
    }

    private fun skip() { while (pos < input.length && input[pos].isWhitespace()) pos++ }

    private fun expression(): BigDecimal {
        var v = term()
        while (true) {
            skip()
            v = when (input.getOrNull(pos)) {
                '+' -> { pos++; v.add(term(), mc) }
                '-' -> { pos++; v.subtract(term(), mc) }
                else -> return v
            }
        }
    }

    private fun term(): BigDecimal {
        var v = power()
        while (true) {
            skip()
            v = when (input.getOrNull(pos)) {
                '*', '×' -> { pos++; v.multiply(power(), mc) }
                '/', '÷' -> { pos++; val d = power(); if (d.signum() == 0) throw ToolArgumentException("Division by zero."); v.divide(d, mc) }
                '%' -> { pos++; val d = power(); if (d.signum() == 0) throw ToolArgumentException("Division by zero."); v.remainder(d, mc) }
                else -> return v
            }
        }
    }

    private fun power(): BigDecimal {
        val base = unary()
        skip()
        if (input.getOrNull(pos) == '^') {
            pos++
            val exp = power()
            val e = exp.toInt()
            if (exp.compareTo(BigDecimal(e)) != 0 || e !in -999..999) throw ToolArgumentException("Exponent must be an integer between -999 and 999.")
            return if (e >= 0) base.pow(e, mc) else BigDecimal.ONE.divide(base.pow(-e, mc), mc)
        }
        return base
    }

    private fun unary(): BigDecimal {
        skip()
        return when (input.getOrNull(pos)) {
            '-' -> { pos++; unary().negate() }
            '+' -> { pos++; unary() }
            else -> primary()
        }
    }

    private fun primary(): BigDecimal {
        skip()
        if (input.getOrNull(pos) == '(') {
            pos++
            val v = expression()
            skip()
            if (input.getOrNull(pos) != ')') throw ToolArgumentException("Missing closing parenthesis.")
            pos++
            return v
        }
        val start = pos
        while (pos < input.length && (input[pos].isDigit() || input[pos] == '.')) pos++
        if (start == pos) throw ToolArgumentException("Expected a number at position $pos.")
        return input.substring(start, pos).toBigDecimalOrNull() ?: throw ToolArgumentException("Invalid number '${input.substring(start, pos)}'.")
    }
}

class DateTimeTool(private val zone: () -> ZoneId = { ZoneId.systemDefault() }) : LocalTool {
    override val name = "current_datetime"
    override val description = "Returns the current date and time on the user's device, optionally in an IANA time zone such as Asia/Brunei."
    override val parametersSchema = """{"type":"object","properties":{"timezone":{"type":"string","description":"Optional IANA zone id"}}}"""

    override suspend fun execute(argumentsJson: String): String {
        val tz = args(argumentsJson).optString("timezone")?.takeIf { it.isNotBlank() }
        val zoneId = try { tz?.let { ZoneId.of(it) } ?: zone() } catch (e: Exception) { throw ToolArgumentException("Unknown time zone '$tz'.") }
        val now = ZonedDateTime.now(zoneId)
        return "${now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)} (${now.dayOfWeek}, zone $zoneId)"
    }
}

class JsonFormatterTool : LocalTool {
    override val name = "json_formatter"
    override val description = "Validates and pretty-prints JSON. Returns the formatted JSON or the parse error."
    override val parametersSchema = """{"type":"object","properties":{"json":{"type":"string"}},"required":["json"]}"""

    override suspend fun execute(argumentsJson: String): String {
        val raw = args(argumentsJson).string("json")
        return try {
            toolJson.encodeToString(kotlinx.serialization.json.JsonElement.serializer(), toolJson.parseToJsonElement(raw))
        } catch (e: Exception) {
            "Invalid JSON: ${e.message?.take(200)}"
        }
    }
}

class TextStatsTool : LocalTool {
    override val name = "text_statistics"
    override val description = "Counts characters, words, lines and sentences in a text."
    override val parametersSchema = """{"type":"object","properties":{"text":{"type":"string"}},"required":["text"]}"""

    override suspend fun execute(argumentsJson: String): String {
        val text = args(argumentsJson).string("text")
        val words = text.split(Regex("\\s+")).count { it.isNotBlank() }
        val sentences = text.split(Regex("[.!?]+")).count { it.isNotBlank() }
        return "characters=${text.length}, words=$words, lines=${text.lines().size}, sentences=$sentences"
    }
}

class UnitConverterTool : LocalTool {
    override val name = "unit_converter"
    override val description = "Converts between units of length, mass, volume, temperature, speed and data size."
    override val parametersSchema = """{"type":"object","properties":{"value":{"type":"number"},"from":{"type":"string","description":"e.g. km, mi, kg, lb, c, f, l, gal, kmh, mph, mb, gb"},"to":{"type":"string"}},"required":["value","from","to"]}"""

    private val factors: Map<String, Pair<String, Double>> = mapOf(
        // length (meters)
        "mm" to ("length" to 0.001), "cm" to ("length" to 0.01), "m" to ("length" to 1.0), "km" to ("length" to 1000.0),
        "in" to ("length" to 0.0254), "ft" to ("length" to 0.3048), "yd" to ("length" to 0.9144), "mi" to ("length" to 1609.344),
        // mass (kg)
        "mg" to ("mass" to 1e-6), "g" to ("mass" to 0.001), "kg" to ("mass" to 1.0), "t" to ("mass" to 1000.0),
        "oz" to ("mass" to 0.028349523125), "lb" to ("mass" to 0.45359237),
        // volume (liters)
        "ml" to ("volume" to 0.001), "l" to ("volume" to 1.0), "gal" to ("volume" to 3.785411784), "qt" to ("volume" to 0.946352946), "cup" to ("volume" to 0.2365882365),
        // speed (m/s)
        "ms" to ("speed" to 1.0), "kmh" to ("speed" to 1 / 3.6), "mph" to ("speed" to 0.44704), "kn" to ("speed" to 0.514444),
        // data (bytes, decimal and binary)
        "b" to ("data" to 1.0), "kb" to ("data" to 1e3), "mb" to ("data" to 1e6), "gb" to ("data" to 1e9), "tb" to ("data" to 1e12),
        "kib" to ("data" to 1024.0), "mib" to ("data" to 1048576.0), "gib" to ("data" to 1073741824.0),
    )

    override suspend fun execute(argumentsJson: String): String {
        val a = args(argumentsJson)
        val value = a.string("value").toDoubleOrNull() ?: throw ToolArgumentException("'value' must be a number.")
        val from = a.string("from").lowercase().replace("/", "").replace(" ", "")
        val to = a.string("to").lowercase().replace("/", "").replace(" ", "")
        val temps = setOf("c", "f", "k")
        if (from in temps && to in temps) {
            val celsius = when (from) { "c" -> value; "f" -> (value - 32) * 5 / 9; else -> value - 273.15 }
            val result = when (to) { "c" -> celsius; "f" -> celsius * 9 / 5 + 32; else -> celsius + 273.15 }
            return "${fmt(value)} ${from.uppercase()} = ${fmt(result)} ${to.uppercase()}"
        }
        val f = factors[from] ?: throw ToolArgumentException("Unknown unit '$from'.")
        val t = factors[to] ?: throw ToolArgumentException("Unknown unit '$to'.")
        if (f.first != t.first) throw ToolArgumentException("Cannot convert ${f.first} to ${t.first}.")
        return "${fmt(value)} $from = ${fmt(value * f.second / t.second)} $to"
    }

    private fun fmt(d: Double) = BigDecimal(d).round(MathContext(10)).stripTrailingZeros().toPlainString()
}

object BuiltInTools {
    fun all(): List<LocalTool> = listOf(CalculatorTool(), DateTimeTool(), JsonFormatterTool(), TextStatsTool(), UnitConverterTool())
}
