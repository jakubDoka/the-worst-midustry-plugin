package game.commands

import arc.util.CommandHandler
import game.User.User

class Help(command: Handler): Command(command) {
    override val name = "help"
    override val args = "[commandName|page]"
    override val kind = Kind.Game

    override fun run(user: User?, args: Array<String>): Result {
        return Result.Success
    }
}