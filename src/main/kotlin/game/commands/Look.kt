package game.commands

import bundle.Bundle
import db.Ranks
import game.Users

class Look(val ranks: Ranks, val users: Users): Command("look") {
    override fun run(args: Array<String>): Enum<*> {
        return when(args[0]) {
            "rank" -> {
                if (!user!!.data.specials.contains(args[1]) && user!!.data.rank.name != args[1]) {
                    send("look.rank.denied")
                    Result.Denied
                } else if (!ranks.containsKey(args[1])){
                    send("look.rank.notFound")
                    Generic.NotFound
                } else {
                    user!!.data.display = ranks[args[1]]!!
                    users.reload(user!!)
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