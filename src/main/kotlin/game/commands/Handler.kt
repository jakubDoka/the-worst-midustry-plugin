package game.commands

import arc.util.CommandHandler
import bundle.Bundle
import game.Users
import mindustry.gen.Player
import mindustry_plugin_utils.Logger
import java.lang.RuntimeException


//handler registers game and terminal commands
class Handler(val users: Users, val logger: Logger, private val kind: Command.Kind): HashMap<String, Command>() {
    lateinit var inner: CommandHandler

    fun init(handler: CommandHandler) {
        inner = handler
    }

    // registers any command
    fun reg(command: Command) {
        if (Command.Kind.Game == kind) {
            try {
                inner.register<Player>(command.name, command.args, "") { a, p ->
                    val user = users[p.uuid()]
                    if (user == null) {
                        p.sendMessage("[yellow] Please report that you saw this message. You cannot use command due to the bug in server.")
                        return@register
                    }

                    if (user.paralyzed && command.name != "account" && command.name != "help") {
                        user.send("paralyzed")
                        return@register
                    }

                    command.user = user

                    logger.run { command.run(a) }
                }
            } catch (e: Exception) {
                RuntimeException("failed to register ${command.name}", e).printStackTrace()
            }
        }

        if (Command.Kind.Cmd == kind) {
            try {
                inner.register(command.name, Bundle.translate("${command.name}.args"), Bundle.translate("${command.name}.desc")) {
                    logger.run { command.run(it) }
                }
            } catch (e: Exception) {
                RuntimeException("failed to register ${command.name}", e).printStackTrace()
            }
        }

        command.kind = kind
        put(command.name, command)
    }
}