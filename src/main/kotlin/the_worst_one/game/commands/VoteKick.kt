package the_worst_one.game.commands

import the_worst_one.db.Driver
import the_worst_one.db.Ranks
import the_worst_one.game.Users
import the_worst_one.game.Voting
import mindustry_plugin_utils.Templates

class VoteKick(val driver: Driver, val users: Users, val ranks: Ranks, val voting: Voting, val discord: Discord): Command("votekick") {
    private val kick = Voting.Session.Data(2, 6, "mark", name, Ranks.Perm.VoteKick)

    private val sessions = HashMap<Long, Voting.Session>()

    override fun run(args: Array<String>): Enum<*> {
        if(args[0].contains("#")) {
            args[0] = idFromName(args[0])
        }

        if(notNum(0, args)) {
            return Generic.NotAInteger
        }

        val target = users.withdraw(num(args[0]))
        if(target == null) {
            send("notFound")
            return Generic.NotFound
        }

        for((i, s) in voting.queue.withIndex()) {
            if(s.args.size == 3 && s.args[1] is String && num(idFromName(s.args[1] as String)) == target.id) {
                voting.vote(i, data!!, true)
                send("vote.success", data!!.voteValue)
                return Generic.Vote
            }
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

        val data = data!!
        voting.add(Voting.Session(kick, user!!, data.idName(), target.idName(), desc) {
            discord.logRankChange(data, target, target.rank, ranks.griefer, desc)

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

    fun idFromName(name: String): String {
        return Templates.cleanName(name.substring(name.lastIndexOf("#") + 1))
    }

    enum class Result {
        Admin, Griefer, Redundant, Already
    }
}