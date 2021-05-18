package game.commands

import bundle.Bundle
import discord4j.rest.util.Color
import mindustry_plugin_utils.Templates
import java.lang.StringBuilder
import kotlin.math.sign

abstract class Help(val commands: Handler): Command("help") {
    val pageLen = 7

    class Game(handler: Handler): Help(handler) {
        override fun run(args: Array<String>): Enum<*> {
            val user = user!!
            val page = if(args.isEmpty()) "1" else args[0]
            if (!notNum(0, page)) {
                val arr = Array(commands.inner.commandList.size) {
                    val c = commands.inner.commandList[it]
                    "[orange]${c.text}[gray] - ${user.translateOr("${c.text}.args", c.paramText)} - [white]${user.translateOr("${c.text}.desc", c.description)}"
                }
                user.alert(Templates.page(user.translate("help.help.title"), arr, pageLen, num(page).toInt()))
                return Result.List
            }

            if (commands.containsKey(args[0])) {
                val c = commands[args[0]]!!
                user.alert(user.translate("${c.name}.help.title"), "${c.name}.help.body")
                return Result.Info
            }

            user.send("help.invalid")
            return Result.Invalid
        }
    }

    class Discord(private val discord: game.commands.Discord, handler: Handler): Help(handler) {
        override fun run(args: Array<String>): Enum<*> {
            if (args.isEmpty()) {
                val sb = StringBuilder()
                discord.handler!!.forEach { (k, v) ->
                    sb
                        .append("**").append(k).append("**")
                        .append(" - *").append(v.args).append("* - ")
                        .append(v.description).append("\n")
                }
                send {
                    it
                        .setTitle("Commands")
                        .setDescription(sb.toString())
                        .setColor(Color.CYAN)
                }
                return Generic.Success
            }
            val c = commands[args[0]]
            if (c != null) {
                send {
                    it
                        .setTitle(Bundle.translate("${c.name}.help.title"))
                        .setDescription(Bundle.translateOr("${c.name}.discord.help.body", Bundle.translate("${c.name}.help.body")))
                        .setColor(Color.CYAN)
                }
                return Result.Info
            }
            return Result.Invalid
        }
    }

    enum class Result {
        List, Info, Invalid
    }
}