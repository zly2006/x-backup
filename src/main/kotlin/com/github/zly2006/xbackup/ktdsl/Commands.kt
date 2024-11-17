package com.github.zly2006.xbackup.ktdsl

import com.mojang.brigadier.Command
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder

@Target(AnnotationTarget.CLASS, AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
@DslMarker
annotation class CommandBuilder

@CommandBuilder
open class RootBuilderScope<S>(
    var builders: MutableList<ArgumentBuilder<S, *>>? = mutableListOf()
) {
    @CommandBuilder
    inline fun literal(name: String) = LiteralArgumentBuilder.literal<S>(name)!!
        .also(requireNotNull(builders) { "Command already built" }::add)

    @CommandBuilder
    inline fun literal(name: String, function: BuilderScope<S>.() -> Unit) = literal(name).apply {
        then(function)
    }
}

@CommandBuilder
class BuilderScope<S>: RootBuilderScope<S>(mutableListOf()) {
    var executes: Command<S>? = null
    @CommandBuilder
    fun executes(function: Command<S>) {
        executes = function
    }
    @CommandBuilder
    inline fun <T> argument(name: String, type: ArgumentType<T>) = RequiredArgumentBuilder.argument<S, T>(name, type)!!
        .also(requireNotNull(builders) { "Command already built" }::add)

    @CommandBuilder
    inline fun argument(name: String, type: ArgumentType<*>, function: BuilderScope<S>.() -> Unit) = argument(name, type).apply {
        then(function)
    }

    @CommandBuilder
    inline fun optional(builder: ArgumentBuilder<S, *>, function: BuilderScope<S>.() -> Unit) {
        builder.then(function)
        this.function()
        requireNotNull(builders) { "Command already built" }.add(builder)
    }
}

@CommandBuilder
inline fun <S> ArgumentBuilder<S, *>.then(function: BuilderScope<S>.() -> Unit) = apply {
    val scope = BuilderScope<S>()
    function(scope)
    scope.builders!!.forEach(this::then)
    scope.builders = null
    if (scope.executes != null) {
        this.executes(scope.executes)
    }
}

@CommandBuilder
inline fun <S> CommandDispatcher<S>.register(function: RootBuilderScope<S>.() -> Unit) {
    val scope = BuilderScope<S>()
    function(scope)
    scope.builders!!.forEach {
        register(it as LiteralArgumentBuilder<S>)
    }
    scope.builders = null
}

inline operator fun <S> ArgumentBuilder<S, *>.invoke(function: BuilderScope<S>.() -> Unit) = then(function)
