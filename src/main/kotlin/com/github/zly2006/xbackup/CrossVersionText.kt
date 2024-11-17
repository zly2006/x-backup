package com.github.zly2006.xbackup

sealed class CrossVersionText {
    data class CombinedText(val texts: List<CrossVersionText>) : CrossVersionText() {
        constructor(vararg texts: CrossVersionText) : this(texts.toList())
    }

    data class LiteralText(val text: String) : CrossVersionText()

    data class ClickableText(val text: CrossVersionText, val command: String, val hover: CrossVersionText? = null) : CrossVersionText()

    data class TranslatableText(val key: String, val args: List<CrossVersionText>) : CrossVersionText() {
        constructor(key: String, vararg args: CrossVersionText) : this(key, args.toList())

        constructor(key: String, vararg args: String) : this(key, args.map { LiteralText(it) })

        constructor(key: String) : this(key, listOf())
    }
}
