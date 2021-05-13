package game.commands

import db.Driver
import db.Ranks
import game.Users
import java.lang.Long.parseLong

class SetRank(val driver: Driver, val users: Users, private val ranks: Ranks): Command("setrank") {
    override fun run(args: Array<String>): Enum<*> {
        if(kind == Kind.Game && !user!!.data.rank.admin) {
            send("setrank.denied")
            return Result.Denied
        }

        if (notNum(args[0], 0)) {
            return Generic.NotAInteger
        }

        val id = num(args[0])

        val exists = driver.users.exists(id)
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

        val old = driver.users[id].rank

        driver.users[id].rank = rank

        post {
            val target = users.find(id)
            if(target != null) {
                users.reload(target)
            }
        }

        send("setrank.success", driver.users[id].name, old.postfix, rank.postfix)

        return Generic.Success
    }

    enum class Result {
        Denied, NotFound, InvalidRank, NotMutable
    }
}