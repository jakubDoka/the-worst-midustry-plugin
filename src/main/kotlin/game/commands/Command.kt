package game.commands

import arc.Core
import bundle.Bundle
import discord4j.core.`object`.entity.Message
import game.u.User
import java.lang.Long.parseLong

// Command is a base class for all commands and implements some common utility
abstract class Command(val name: String) {
    val args = Bundle().get("$name.args")
    var kind: Kind = Kind.Game

    var user: User? = null
    var message: Message? = null

    // main executor of command
    abstract fun run(args: Array<String>): Enum<*>

    // short hand for posting to main thread, this is used in bot commands
    fun post(r: () -> Unit) {
        Core.app.post(r)
    }

    // checks if string is valid number and notifies user if not
    fun isNum(s: String, argument: Int): Boolean {
        return try {
            parseLong(s)
            true
        } catch (e: Exception) {
            send("notANumber", argument)
            false
        }
    }

    // parses a number
    fun num(s: String): Long {
        return parseLong(s)
    }

    // used for texting purposes
    fun assert(result: Enum<*>, vararg args: String) {
        val res = run(Array(args.size) { args[it] })
        if (res != result) {
            println("$res != $result")
            assert(false)
        }
    }

    // generic send method base dof current command kind
    fun send(key: String, vararg args: Any) {
        when(kind) {
            Kind.Discord -> message?.send(key, *args)
            Kind.Game -> user?.send(key, *args)
            Kind.Cmd -> Bundle.send(key, *args)
        }
    }

    // executes block based of current kind
    fun fork(cmd: () -> Unit = {}, game: () -> Unit = {}, discord: () -> Unit = {}) {
        when(kind) {
            Kind.Discord -> discord.invoke()
            Kind.Game -> game.invoke()
            Kind.Cmd -> cmd.invoke()
        }
    }

    enum class Kind {
        Cmd, Game, Discord
    }
}