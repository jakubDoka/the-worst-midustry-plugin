package game.commands

import db.Driver
import game.Users

class Minimal {
    class Reload(val users: Users): Command("reload") {
        override fun run(args: Array<String>): Enum<*> {
            users.reload(user!!)
            send("reload.done")
            return Generic.Success
        }
    }
}