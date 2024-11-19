package com.github.zly2006.xbackup

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.slf4j.LoggerFactory
import java.io.InputStream

object I18n {
    private val log = LoggerFactory.getLogger("X-Backup/I18n")!!
    const val DEFAULT_LANGUAGE = "en_us"
    private val langMap = mutableMapOf<String, String>()
    @ExperimentalSerializationApi
    fun setLanguage(lang: String) {
        try {
            val lang = getLanguageFile(lang).use {
                Json.decodeFromStream<Map<String, String>>(it)
            }
            val english = getLanguageFile(DEFAULT_LANGUAGE).use {
                Json.decodeFromStream<Map<String, String>>(it)
            }
            langMap.clear()
            langMap.putAll(english)
            langMap.putAll(lang)
        } catch (e: Exception) {
            log.error("Error loading language", e)
            try {
                val english = getLanguageFile(DEFAULT_LANGUAGE).use {
                    Json.decodeFromStream<Map<String, String>>(it)
                }
                langMap.clear()
                langMap.putAll(english)
            } catch (e1: Exception) {
                throw IllegalStateException("Error loading default language", e1).apply {
                    addSuppressed(e)
                }
            }
        }
    }

    private fun getLanguageFile(lang: String): InputStream {
        return I18n.javaClass.classLoader.getResourceAsStream("assets/x-backup/lang/$lang.json")
            ?: throw IllegalArgumentException("Language file not found: $lang")
    }
}
