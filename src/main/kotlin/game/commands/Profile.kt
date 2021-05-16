package game.commands

import db.Driver
import db.Ranks

class Profile(val driver: Driver, val ranks: Ranks): Command("profile") {
    override fun run(args: Array<String>): Enum<*> {
        val id = if(args[0] == "me" && kind == Kind.Game) {
            user!!.data.id
        } else {
            if (notNum(args[0], 0)) {
                return Generic.NotAInteger
            }
            val id = num(args[0])
            if(!driver.users.exists(id)) {
                send("notFound")
                return Generic.NotFound
            }
            id
        }

        when(args[1]) {
            "personal" -> {
                val personal = Driver.Personal(id, ranks)
                return Result.Personal
            }
            "stats" -> {
                val stats = Driver.Stats(id)
                send("profile.personal",
                    stats.won,
                    stats.played,
                    stats.commands,
                    stats.messages,
                    stats.deaths,
                    stats.killed,
                    stats.build,
                    stats.destroyed,
                )
            }
        }

        return Generic.Success
    }

    enum class Result {
        Personal
    }

}