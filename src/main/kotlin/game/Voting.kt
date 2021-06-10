package game

import arc.math.Mathf
import arc.struct.Seq
import bundle.Bundle
import cfg.Globals
import db.Driver
import db.Ranks
import game.u.User
import java.lang.StringBuilder

class Voting(val users: Users): Displayable {
    var queue: Seq<Session> = Seq()

    override fun tick() {
        queue = queue.filter {
            it.duration--
            if (it.duration <= 0) {
                if(it.permissive && it.yes > it.no) {
                    execute(it)
                } else {
                    stop(it)
                }
                false
            } else true
        }
    }

    override fun display(user: Driver.RawUser): String {
        val sb = StringBuilder(" ")
        for((i, s) in queue.withIndex()) {
            sb.append(String.format(
                "%s [green]%d[]:[gray]%d[]:[red]%d[] %ds ([green]/vote y ${i + 1}[])",
                user.translate(s.key("message"), *s.args),
                s.yes,
                required(s),
                s.no,
                s.duration
            )).append("\n")
        }

        return sb.substring(0, sb.length - 1)
    }

    private fun required(session: Session): Int {
        var res = 0
        users.forEach { _, v ->
            if(!v.data.rank.control.spectator()) {
                res += v.data.rank.voteValue
            }
        }
        return Mathf.clamp(res/2, session.data.min, session.data.max)
    }

    fun vote(id: Int, user: Driver.RawUser, yes: Boolean): Boolean {
        val s = queue[id]
        if(s.voted.contains(user.id)) return false
        val req = required(s)
        if(yes) {
            s.yes += user.voteValue
            if(req <= s.yes || user.hasPerm(Ranks.Perm.Skip)) {
                execute(s)
                queue.remove(id)
            }
        } else {
            s.no += user.voteValue
            if(req <= s.no || user.hasPerm(Ranks.Perm.Skip)) {
                stop(s)
                queue.remove(id)
            }
        }
        s.voted.add(user.id)
        return true
    }

    fun add(session: Session): Enum<*> {
        if(Globals.testing) {
            announce(session)
            execute(session)
            stop(session)
            return SessionSpawnResp.Success
        }

        if(queue.find{it.user.data.id == session.user.data.id} != null) {
            session.user.send("voting.already")
            return SessionSpawnResp.Full
        }

        if (session.user.data.hasPerm(Ranks.Perm.Skip)) {
            session.run()
            session.user.send("voting.skip")
            return SessionSpawnResp.Skip
        }

        var resp = SessionSpawnResp.Success

        if(session.user.data.hasPerm(session.data.promoted)) {
            session.duration = 30
            session.permissive = true
            users.send("voting.special", session.user.data.idName(), session.data.promoted)
            resp = SessionSpawnResp.Special
        }

        announce(session)
        queue.add(session)
        vote(queue.size - 1, session.user.data, true)
        session.user.send("voting.success")

        return resp
    }

    fun announce(session: Session) {
        send(session, "message")
    }

    fun execute(session: Session) {
        send(session, "execute")
        if (Globals.testing) return
        session.run()
    }

    fun stop(session: Session) {
        send(session, "stop")
    }

    fun send(session: Session, key: String) {
        if(Globals.testing) {
            Bundle.send(session.key(key), *session.args)
            return
        }
        users.send(session.key(key), *session.args)
        users.send("voting.announcedBy", session.user.inner.name)
    }

    enum class SessionSpawnResp {
        Skip, Special, Full, Success
    }

    class Session(val data: Data, val user: User, vararg val args: Any, val run: () -> Unit) {
        var duration = 60
        var permissive = false
        var yes: Int = 0
        var no: Int = 0
        val voted: HashSet<Long> = HashSet()

        fun key(postfix: String): String {
            return "${data.command}.${data.name}.$postfix"
        }

        class Data(val min: Int, val max: Int, val name: String, val command: String, val promoted: Ranks.Perm = Ranks.Perm.None)
    }
}