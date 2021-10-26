package the_worst_one.game.commands

import the_worst_one.game.Users

class Minimal {
    class Reload(val users: Users): Command("reload") {
        override fun run(args: Array<String>): Enum<*> {
            users.reload(user!!)
            send("reload.done")
            return Generic.Success
        }
    }
}