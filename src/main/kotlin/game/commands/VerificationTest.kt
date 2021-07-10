package game.commands

import arc.util.Time
import cfg.Config
import cfg.Globals
import cfg.Globals.time
import cfg.Reloadable
import com.beust.klaxon.Klaxon
import db.Ranks
import game.Users
import mindustry_plugin_utils.Fs

import java.io.File
import java.util.*
import kotlin.collections.HashMap

class VerificationTest(val ranks: Ranks, val users: Users, val config: Config, override val configPath: String): Command("test"), Reloadable, Globals.Log {
    override val prefix = "test"
    val questions = HashMap<String, Map<String, List<String>>>()

    val sessions = HashMap<Long, Session>()
    val penalties = HashMap<Long, Long>()

    init {
        reload()
    }

    override fun run(args: Array<String>): Enum<*> {
        val data = data!!
        val penalty = config.data.testPenalty - Time.millis() + penalties.getOrDefault(data.id, 0)
        if(penalty > 0) {
            send("test.denied", penalty.time())
            return Generic.Denied
        }

        val session = sessions[data.id]
        return if(session == null) {
            val test = questions[data.bundle.locale] ?: questions["default"]
            if(test == null) {
                send("test.unavailable")
                return Result.Unavailable
            }
            sessions[data.id] = Session(this, data.id, test)
            Result.Initiated
        } else {
            if(args.isEmpty()) {
                session.ask()
                return Result.Repeat
            }
            if(notNum(0, args)) {
                return Generic.NotAInteger
            }
            session.answer(num(args[0]))
        }
    }

    override fun reload() {
        val f = File(configPath)
        if (f.exists()) {
            f.walk().maxDepth(1).filter { it.isFile }.forEach {
                questions[it.nameWithoutExtension] = Klaxon().parse<Map<String, List<String>>>(it)!!
            }
            if (questions.isNotEmpty()) return
        }

        println("test:: No tests found. Creating a new dummy test that should be removed as fast as possible.")

        Fs.createDefault(
            "$configPath/default.json", mapOf(
                "Nice question?" to listOf(
                    "#Nice answer",
                    "Wrong answer",
                    "something else"
                ),
                "Other nice question?" to listOf(
                    "#Nice answer",
                    "Wrong answer",
                    "something else",
                    "Some more"
                )
            )
        )
    }

    class Session(val test: VerificationTest, val id: Long, questions: Map<String, List<String>>) {
        var correct: Int = 0
        var current: Int = 0
        val queue = questions.toList().shuffled(Random(Time.millis()))

        init {
            ask()
        }

        fun answer(id: Long): Enum<*> {
            val question = queue[current]
            if(id < 1 || id > question.second.size) {
                test.send("test.outOfBounds")
                return Result.OutOfBounds
            }

            var res: Enum<*> = Result.Incorrect

            if(question.second[id.toInt() - 1].startsWith("#")) {
                correct++
                res = Result.Correct
            }

            current++

            if(current >= queue.size) {
                res = eval()
            } else {
                ask()
            }

            return res
        }

        fun ask() {
            val question = queue[current]
            val sb = StringBuilder("[white]${question.first}[]\n")
            for((i, o) in question.second.withIndex()) {
                sb
                    .append("\t$i)")
                    .append(if(o.startsWith("#")) o.substring(1) else o)
                    .append("\n")
            }
            test.sendPlain(sb.toString())
        }

        fun eval(): Enum<*> {
            test.sessions.remove(id)
            if(correct == queue.size) {
                test.data!!.rank = test.ranks.verified
                test.users.reload(test.user!!)
                test.send("test.completed")
                return Generic.Success
            }
            test.send("test.failed", correct, queue.size, test.config.data.testPenalty.time())
            test.penalties[test.data!!.id] = Time.millis()
            return Result.Fail
        }
    }

    enum class Result {
        OutOfBounds, Unavailable, Correct, Incorrect, Fail, Initiated, Repeat
    }
}