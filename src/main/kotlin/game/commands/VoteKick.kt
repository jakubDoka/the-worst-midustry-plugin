package game.commands

import db.Driver
import db.Ranks
import game.Users
import game.Voting

class VoteKick(val driver: Driver, val users: Users, val ranks: Ranks, val voting: Voting): Command("votekick") {
    private val kick = Voting.Session.Data(2, 6, "mark", name, Ranks.Perm.VoteKick)

    private val sessions = HashMap<Long, Voting.Session>()

    override fun run(args: Array<String>): Enum<*> {
        if(args[0].contains("#")) {
            args[0] = args[0].substring(args[0].lastIndexOf("#") + 1)
        }

        if(notNum(0, args)) {
            return Generic.NotAInteger
        }

        val target = users.withdraw(num(args[0]))
        if(target == null) {
            send("notFound")
            return Generic.NotFound
        }

        if(target.rank.control.admin()) {
            send("votekick.admin")
            return Result.Admin
        }

        // in case someone kicks griefer, one person is all that's needed for griefer to be
        // temporarily kicked. Griefer will also be informed that any player can kick him
        // without a vote session.
        if(target.rank == ranks.griefer) {
            val online = users[target.uuid]
            if(online != null) {
                online.inner.kick(online.data.translate("votekick.griefer.kickMessage"))
                send("votekick.griefer")
                return Result.Griefer
            }
            send("votekick.redundant")
            return Result.Redundant
        }

        if(data!!.id == target.id) {
            send("votekick.yourself")
        }

        var desc = "unknown reasons"
        if (args.size > 1) {
            desc = args[1]
        }

        voting.add(Voting.Session(kick, user!!, data!!.idName(), target.idName(), desc) {
            target.rank = ranks.griefer
            target.ban()
            val online = users[target.uuid]
            if(online == null)
                target.save(driver.config.multiplier, ranks)
            else
                users.reload(online)
        })
        
        return Generic.Vote
    }

    enum class Result {
        Admin, Griefer, Redundant, Already
    }
}