package game.commands

import arc.util.CommandHandler
import bundle.Bundle
import game.User.User
import game.UserStore
import mindustry.gen.Player

class Handler(val inner: CommandHandler, val bundle: Bundle, val users: UserStore) {
    val commands = HashMap<String, Command>()

    fun reg(command: Command) {
        val addTerminal = {
            inner.register(command.name,  args(command), desc(command)){
                command.run(null, it)
            }
        }

        val addGame = {
            inner.register<Player>(command.name, command.args, "") {a, p ->
                val user = users.users[p.uuid()]
                if(user == null) {
                    p.sendMessage("[yellow] Please report that you saw this message. You cannot use command due to the bug in server.")
                    return@register
                }
                command.run(user, a)
            }
        }

        when (command.kind) {
            Command.Kind.Game -> addGame.invoke()
            Command.Kind.Terminal -> addTerminal.invoke()
            Command.Kind.Both -> {
                addGame.invoke()
                addTerminal.invoke()
            }
        }

        commands[command.name] = command
    }

    fun args(command: Command): String {
        return bundle.get(command.name + "-args")
    }

    fun desc(command: Command): String {
        return bundle.get(command.name + "-desc")
    }
}