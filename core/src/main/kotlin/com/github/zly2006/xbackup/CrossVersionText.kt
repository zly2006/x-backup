package com.github.zly2006.xbackup

sealed class CrossVersionText {
    class CombinedText(val texts: List<CrossVersionText>) : CrossVersionText() {
        constructor(vararg texts: CrossVersionText) : this(texts.toList())

        override fun toString(): String {
            return "CombinedText(texts=$texts)"
        }
    }

    class LiteralText(val text: String) : CrossVersionText() {
        override fun toString(): String {
            return "LiteralText(text='$text')"
        }
    }

    class ClickableText(val text: CrossVersionText, val command: String, val hover: CrossVersionText? = null) : CrossVersionText() {
        override fun toString(): String {
            return "ClickableText(text='$text', command='$command', hover=$hover)"
        }
    }
}
