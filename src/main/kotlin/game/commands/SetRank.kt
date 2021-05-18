package game.commands

import db.Driver
import db.Ranks
import game.Users

class SetRank(val driver: Driver, val users: Users, private val ranks: Ranks): Command("setrank") {
    override fun run(args: Array<String>): Enum<*> {
        if(kind == Kind.Game && !user!!.data.rank.admin) {
            send("setrank.denied")
            return Result.Denied
        }

        if (notNum(0, *args)) {
            return Generic.NotAInteger
        }

        val id = num(args[0])

        val other = users.withdraw(id)

        if(other == null) {
            send("notFound")
            return Generic.NotFound
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

        val old = other.rank
        other.rank = rank


        post {
            val target = users[other.uuid]
            if(target != null) {
                users.reload(target)
            } else {
                driver.users.set(id, Driver.Users.rank, other.rank.name)
            }
        }

        send("setrank.success", other.name, old.postfix, rank.postfix)

        return Generic.Success
    }

    enum class Result {
        Denied, InvalidRank, NotMutable
    }
}