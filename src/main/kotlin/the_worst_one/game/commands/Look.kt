package the_worst_one.game.commands

import the_worst_one.db.Ranks
import the_worst_one.game.Users

class Look(val ranks: Ranks, val users: Users): Command("look") {
    override fun run(args: Array<String>): Enum<*> {
        return when(args[0]) {
            "rank" -> {
                val user = user!!
                val name = args[1]
                if (!user.data.specials.contains(name) && user.data.rank.name != name) {
                    send("look.rank.denied")
                    Generic.Denied
                } else if (!ranks.containsKey(name)){
                    send("look.rank.notFound")
                    Generic.NotFound
                } else {
                    user.mount?.kill()
                    user.data.display = ranks[name]!!
                    users.reload(user)
                    send("look.rank.success")
                    Generic.Success
                }
            }
            else -> {
                send("wrongOption", "rank")
                return Generic.Mismatch
            }
        }
    }

    enum class Result {
        Denied, All
    }
}