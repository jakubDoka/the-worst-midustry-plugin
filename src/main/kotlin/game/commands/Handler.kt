package game.commands

import arc.util.CommandHandler
import game.Users
import mindustry.gen.Player
import mindustry_plugin_utils.Logger


//handler registers game and terminal commands
class Handler(val inner: CommandHandler, val users: Users, val logger: Logger, private val kind: Command.Kind): HashMap<String, Command>() {

    // registers any command
    fun reg(command: Command) {
        if (Command.Kind.Game == kind) {
            inner.register<Player>(command.name, command.args, "") {a, p ->
                val user = users[p.uuid()]
                if(user == null) {
                    p.sendMessage("[yellow] Please report that you saw this message. You cannot use command due to the bug in server.")
                    return@register
                }

                command.user = user

                logger.run { command.run(a) }
            }
        }

        if (Command.Kind.Cmd == kind) {
            inner.register(command.name,  "${command.name}.args", "${command.name}.desc"){
                logger.run { command.run(it) }
            }
        }

        command.kind = kind
        put(command.name, command)
    }
}