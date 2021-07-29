package the_worst_one.game.commands

import the_worst_one.db.Ranks
import mindustry_plugin_utils.Templates

abstract class Help(val commands: Handler) : Command("help", Ranks.Control.None) {
    val pageLen = 7

    class Game(handler: Handler) : Help(handler) {
        override fun run(args: Array<String>): Enum<*> {
            val user = user!!
            val page = if (args.isEmpty()) "1" else args[0]
            if (!notNum(0, page)) {
                val arr = Array(commands.inner.commandList.size) {
                    val c = commands.inner.commandList[it]
                    "[orange]${c.text}[gray] - ${user.data.translateOr("${c.text}.args", c.paramText)} - " +
                            "[white]${user.data.translateOr("${c.text}.desc", c.description)}"
                }
                user.alert(Templates.page(user.data.translate("help.help.title"), arr, pageLen, num(page).toInt()))
                return Result.List
            }

            if (commands.containsKey(args[0])) {
                val c = commands[args[0]]!!
                user.alert(user.data.translate("${c.name}.help.title"), "${c.name}.help.body")
                return Result.Info
            }

            user.send("help.invalid")
            return Result.Invalid
        }
    }

    class Discord(private val discord: the_worst_one.game.commands.Discord, handler: Handler) : Help(handler) {
        override fun run(args: Array<String>): Enum<*> {
            if (args.isEmpty()) {
                val sb = StringBuilder()
                discord.handler!!.forEach { (k, v) ->
                    sb
                        .append("**")
                        .append(discord.config.prefix)
                        .append(k)
                        .append("**")
                    if (v.args.replace("\\s*", "") != "")
                        sb
                            .append(" - *")
                            .append(v.args)
                            .append("* - ")
                    else
                        sb.append(" - ")

                    sb
                        .append(v.description)
                        .append("\n")
                }
                alert("help.discord.title", "placeholder", sb.toString())
                return Generic.Success
            }
            val c = commands[args[0]]
            if (c != null) {
                alert("${c.name}.help.title", "${c.name}.help.body")
                return Result.Info
            }
            send("help.invalid")
            return Result.Invalid
        }
    }


    enum class Result {
        List, Info, Invalid
    }
}