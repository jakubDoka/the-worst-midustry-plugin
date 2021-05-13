package game.commands

import db.Driver
import db.Ranks
import game.Users
import java.lang.Long.parseLong

class SetRank(val driver: Driver, val users: Users, val ranks: Ranks): Command("setrank") {
    override fun run(args: Array<String>): Enum<*> {
        if(kind == Kind.Game && !user!!.data.rank.admin) {
            send("setrank.denied")
            return Result.Denied
        }

        val exists = driver.userExists(args[0])
        if(!exists) {
            send("setrank.notFound")
            return Result.NotFound
        }

        val rank = ranks[args[1]]
        if(rank == null || rank.kind != Ranks.Kind.Normal) {
            send( "setrank.invalidRank", ranks.enumerate(Ranks.Kind.Normal))
            return Result.InvalidRank
        }

        if (!rank.control.mutable() && kind != Kind.Cmd && user!!.data.rank.control != Ranks.Control.Absolute) {
            send( "setrank.notMutable")
            return Result.NotMutable
        }

        driver.setRank(parseLong(args[0]), rank)

        val target = users.find(args[0])
        if(target != null) {
            users.reload(target)
        }

        send("setrank.success", )
        return Result.Success
    }

    enum class Result {
        Denied, NotFound, InvalidRank, Success, NotMutable
    }
}