package db

import arc.Core
import arc.util.Time
import db.Driver.Progress
import db.Driver.Users
import game.u.User
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

abstract class Quest(val name: String) {
    companion object {
        const val error = "error in rank file"
        const val complete = "c"
    }

    fun points(amount: Long, user: User): String {
        return if(amount > 0) {
            user.translate("quest.notEnough", amount)
        } else {
            complete
        }
    }

    abstract fun check(user: User, value: Any): String

    open class Stat(name: String): Quest(name) {
        override fun check(user: User, value: Any): String {
            if(value !is Long) return error
            return try {
                points(value - user.data.stats.javaClass.getField(name).getLong(user.data.stats), user)
            } catch (e: Exception) {
                return user.translate("quest.internalError")
            }
        }
    }

    class Quests(val ranks: Ranks): HashMap<String, Quest>() {
        val input = Channel<User?>()

        fun reg(q: Quest) {
            put(q.name, q)
        }

        init {
            reg(Stat("built"))
            reg(Stat("destroyed"))
            reg(Stat("killed"))
            reg(Stat("deaths"))
            reg(Stat("played"))
            reg(Stat("wins"))
            reg(Stat("messages"))
            reg(Stat("commands"))
            reg(Stat("playTime"))
            reg(Stat("silence"))

            reg(object: Quest("age") {
                override fun check(user: User, value: Any): String {
                    if(value !is Long) return error
                    return points(value - user.data.age, user)
                }
            })

            reg(object: Quest("ranks") {
                override fun check(user: User, value: Any): String {
                    if(value !is String) return error
                    val shouldHave = value.split(" ")
                    val missing = StringBuilder()
                    for(s in shouldHave) {
                        if (!user.data.specials.contains(s)) missing.append(s).append(" ")
                    }

                    return if(missing.isNotEmpty()) {
                        return user.translate("quest.missing", missing.toString())
                    } else {
                        complete
                    }
                }
            })

            reg(object: Quest("rankCount") {
                override fun check(user: User, value: Any): String {
                    if(value !is Long) return error
                    return points(value - user.data.specials.size, user)
                }
            })

            reg(object: Quest("rankTotalValue") {
                override fun check(user: User, value: Any): String {
                    if(value !is Long) return error
                    var total = 0
                    for(r in user.data.specials) {
                        total += ranks[r]?.value ?: 0
                    }
                    return points(value - total, user)
                }
            })

            runBlocking {
                GlobalScope.launch {
                    while(true) {
                        val user = input.receive() ?: break

                        outer@ for((k, v) in ranks) {
                            if(v.kind != Ranks.Kind.Special || user.data.specials.contains(k)) continue

                            for((q, a) in v.quest) {
                                val res = get(q)?.check(user, a)
                                if(res != complete) {
                                    continue@outer
                                }
                            }

                            Core.app.post {
                                user.data.specials.add(k)
                                user.send("quests.obtained", v.postfix)
                            }
                        }
                    }
                }
            }
        }
    }
}