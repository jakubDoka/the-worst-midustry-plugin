package db

import arc.util.Time
import db.Driver.Progress
import db.Driver.Users
import game.u.User
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

    open class Stat(name: String, private val col: Column<Long>, private val index: ID = Users): Quest(name) {
        private val table = index as Table
        override fun check(user: User, value: Any): String {
            if(value !is Long) return error
            return points(value - transaction { table.slice(col).select{index.idColumn eq user.data.id}.first()[col] }, user)
        }

        class Prog(name: String, col: Column<Long>): Stat(name, col, Progress)
    }

    class Quests(val ranks: Ranks): HashMap<String, Quest>() {
        fun reg(q: Quest) {
            put(q.name, q)
        }

        fun ranks(id: Long): List<String> {
            return transaction { Users.slice(Users.specials).select{Users.id eq id}.first()[Users.specials].split(" ") }
        }

        init {
            reg(Stat.Prog("built", Progress.build))
            reg(Stat.Prog("destroyed", Progress.build))
            reg(Stat.Prog("killed", Progress.build))
            reg(Stat.Prog("deaths", Progress.build))
            reg(Stat.Prog("played", Progress.build))
            reg(Stat.Prog("won", Progress.build))
            reg(Stat.Prog("message", Progress.build))
            reg(Stat.Prog("commands", Progress.build))

            reg(object: Quest("age") {
                override fun check(user: User, value: Any): String {
                    if(value !is Long) return error
                    return try {
                        points(value - user.data.stats.javaClass.getField(name).getLong(user.data.stats), user)
                    } catch(e: Exception) {
                        return user.translate("quest.internalError")
                    }
                }
            })

            reg(object: Quest("ranks") {
                override fun check(user: User, value: Any): String {
                    if(value !is String) return error
                    val shouldHave = value.split(" ")
                    val obtained = ranks(user.data.id).toSet()
                    val missing = StringBuilder()
                    for(s in shouldHave) {
                        if (!obtained.contains(s)) missing.append(s).append(" ")
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
                    return points(value - ranks(user.data.id).size, user)
                }
            })

            reg(object: Quest("rankTotalValue") {
                override fun check(user: User, value: Any): String {
                    if(value !is Long) return error
                    var total = 0
                    for(r in ranks(user.data.id)) {
                        total += ranks.getOrDefault(r, ranks.default).value
                    }
                    return points(value - total)
                }
            })
        }
    }

    interface ID {
        val idColumn: Column<EntityID<Long>>
    }
}