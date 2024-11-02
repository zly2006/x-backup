package com.github.zly2006.xbackup

sealed class CrossVersionText {
    data class CombinedText(val texts: List<CrossVersionText>) : CrossVersionText() {
        constructor(vararg texts: CrossVersionText) : this(texts.toList())
    }

    data class LiteralText(val text: String) : CrossVersionText()

    data class ClickableText(val text: CrossVersionText, val command: String, val hover: CrossVersionText? = null) : CrossVersionText()
}
