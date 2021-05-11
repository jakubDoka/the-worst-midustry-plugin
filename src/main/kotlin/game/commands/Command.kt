package game.commands

import arc.util.CommandHandler
import game.User.User

abstract class Command(val game: Handler) {
    abstract val name: String
    abstract val args: String
    abstract val kind: Kind

    abstract fun run(user: User?, args: Array<String>): Result

    fun key(result: Result): String {
        return "$name-$result"
    }

    enum class Kind {
        Terminal, Game, Both
    }
}