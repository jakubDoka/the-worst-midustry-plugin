package the_worst_one.db

import arc.Core
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import the_worst_one.cfg.Globals
import the_worst_one.game.Pets
import the_worst_one.game.Users
import the_worst_one.game.u.User
import java.util.*
import kotlin.reflect.full.declaredMemberProperties
import mindustry.gen.Call

abstract class Quest(val name: String) {
    companion object {
        const val complete = "complete"
    }

    fun points(amount: Long, user: Driver.RawUser): String {
        return if(amount > 0) {
            user.translate("quest.notEnough", amount)
        } else {
            complete
        }
    }

    fun long(l: Any): Long? {
        return when(l){
            is Long -> l
            is Double -> l.toLong()
            is Int -> l.toLong()
            else -> null
        }
    }

    abstract fun check(user: Driver.RawUser, value: Any): String

    class Stat(name: String): Quest(name) {
        override fun check(user: Driver.RawUser, value: Any): String {
            val v = long(value) ?: return user.translate("quest.error")
            return try {
                points(v - user.stats::class.declaredMemberProperties.find { it.name == name }!!.getter.call(user.stats) as Long, user)
            } catch (e: Exception) {
                return user.translate("quest.internalError", e.message ?: "no message")
            }
        }
    }

    class Quests(val ranks: Ranks, val driver: Driver): HashMap<String, Quest>() {
        lateinit var pets: Pets
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
                override fun check(user: Driver.RawUser, value: Any): String {
                    val v = long(value) ?: return user.translate("quest.error")
                    return points(v - user.age, user)
                }
            })

            reg(object: Quest("ranks") {
                override fun check(user: Driver.RawUser, value: Any): String {
                    if(value !is String) return user.translate("quest.error")
                    val shouldHave = value.split(" ")
                    val missing = StringBuilder()
                    for(s in shouldHave) {
                        if (!user.specials.contains(s)) missing.append(s).append(" ")
                    }

                    return if(missing.isNotEmpty()) {
                        return user.translate("quest.missing", missing.toString())
                    } else {
                        complete
                    }
                }
            })

            reg(object: Quest("rankCount") {
                override fun check(user: Driver.RawUser, value: Any): String {
                    val v = long(value) ?: return user.translate("quest.error")
                    return points(v - user.specials.size, user)
                }
            })

            reg(object: Quest("rankTotalValue") {
                override fun check(user: Driver.RawUser, value: Any): String {
                    val v = long(value) ?: return user.translate("quest.error")
                    return points(v - user.rankValue(ranks), user)
                }
            })

            reg(object: Quest("points") {
                override fun check(user: Driver.RawUser, value: Any): String {
                    val v = long(value) ?: return user.translate("quest.error")
                    return points(v - user.points(ranks, driver.config.multiplier), user)
                }
            })

            reg(object: Quest("pointPlace") {
                override fun check(user: Driver.RawUser, value: Any): String {
                    val v = long(value) ?: return user.translate("quest.error")

                    val amount = transaction {
                        Driver.Users.select { Driver.Users.points greater user.points(ranks, driver.config.multiplier) }.count()
                    }

                    return if(amount >= v) {
                        user.translate("quest.lowOnLadder", amount, v)
                    } else {
                        complete
                    }
                }
            })


                Globals.runLoggedGlobalScope {
                    while(true) {
                        val user = input.receive() ?: break
                        var someRanksLost = false
                        outer@ for((k, v) in ranks) {
                            if (v.kind != Ranks.Kind.Special) continue
                            val contains = user.data.specials.contains(k)
                            if(contains && v.permanent) continue

                            for ((n, a) in v.quest) {
                                val quest = get(n) ?: continue
                                val message = quest.check(user.data, a)
                                if (message != complete) {
                                    someRanksLost = true
                                    if (contains) if (Globals.testing) {
                                        user.data.specials.remove(k)
                                    } else Core.app.post {
                                        user.data.specials.remove(k)
                                        user.send("quest.lost", v.postfix)
                                    }
                                    continue@outer
                                }
                            }

                            if(user.data.specials.contains(k)) continue

                            if (Globals.testing) {
                                user.data.specials.add(k)
                            } else Core.app.post {
                                user.data.specials.add(k)
                                user.send("quest.obtained", v.postfix)
                            }
                        }

                        if(!Globals.testing) Core.app.post {
                            pets.populate(user)
                            if(someRanksLost && !user.data.specials.contains(user.data.display.name)) {
                                user.data.display = user.data.rank
                                driver.users.set(user.data.id, Driver.Users.display, user.data.display.name)
                                user.initData()
                            }
                        }
                    }
                }
        }
    }
}