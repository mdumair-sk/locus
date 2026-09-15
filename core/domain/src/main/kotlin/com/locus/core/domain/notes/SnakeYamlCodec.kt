package com.locus.core.domain.notes

import org.yaml.snakeyaml.DumperOptions
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.representer.Representer
import java.util.Date

/**
 * Pure JVM [YamlCodec] backed by SnakeYAML 2.x.
 *
 * Uses [SafeConstructor] for safe deserialization preventing arbitrary code/class execution,
 * and [DumperOptions] configured for block style matching frontmatter conventions (no flow-style maps/lists).
 */
class SnakeYamlCodec : YamlCodec {

    private val yaml: Yaml by lazy {
        val loaderOptions = LoaderOptions()
        val dumperOptions = DumperOptions().apply {
            defaultFlowStyle = DumperOptions.FlowStyle.BLOCK
            isPrettyFlow = false
            splitLines = false
        }
        val representer = Representer(dumperOptions)
        Yaml(SafeConstructor(loaderOptions), representer, dumperOptions, loaderOptions)
    }

    override fun decode(yamlText: String): Map<String, Any?> {
        if (yamlText.isBlank()) return emptyMap()
        val loaded = yaml.load<Any?>(yamlText) ?: return emptyMap()
        if (loaded !is Map<*, *>) {
            throw IllegalArgumentException("Expected YAML mapping but found: ${loaded::class.java.simpleName}")
        }
        val result = linkedMapOf<String, Any?>()
        for ((k, v) in loaded) {
            result[k.toString()] = sanitizeYamlValue(v)
        }
        return result
    }

    override fun encode(fields: Map<String, Any?>): String {
        return yaml.dump(fields)
    }

    private fun sanitizeYamlValue(value: Any?): Any? = when (value) {
        is Date -> value.toInstant().toString()
        is Map<*, *> -> value.entries.associate { (k, v) -> k.toString() to sanitizeYamlValue(v) }
        is List<*> -> value.map { sanitizeYamlValue(it) }
        else -> value
    }
}
