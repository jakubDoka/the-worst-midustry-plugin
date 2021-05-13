package game.commands

import mindustry_plugin_utils.Templates

// game only command
class Help(private val commands: Handler): Command("help") {
    private val pageLen = 7
    override fun run(args: Array<String>): Enum<*> {
        val user = user!!
        if (isNum(args[0], 0)) {
            val arr = Array(commands.inner.commandList.size) {
                val c = commands.inner.commandList[it]
                "[orange]${c.text}[gray]-${user.translateOr("${c.text}.args", c.paramText)}-[white]${user.translateOr("${c.text}.desc", c.description)}"
            }
            user.alert(Templates.page(user.translate("help.help.title"), arr, pageLen, num(args[0]).toInt()))
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

    enum class Result {
        List, Info, Invalid
    }
}