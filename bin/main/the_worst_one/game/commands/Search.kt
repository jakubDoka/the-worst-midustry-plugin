package the_worst_one.game.commands

import the_worst_one.db.Driver
import the_worst_one.db.Ranks
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import the_worst_one.cfg.Globals
import kotlin.math.min

class Search(val ranks: Ranks): Command("search") {
    override fun run(args: Array<String>): Enum<*> {
        val sb = StringBuilder()


            Globals.runLoggedGlobalScope(false) {
                when (args[0]) {
                    "simple" -> {
                        val u = args[1].split(" ")
                        val offset = if (u.size > 1 && !notNum(2, u[1], async = true)) num(u[1]) else 0
                        var limit = if (u.size > 3 && !notNum(3, u[2], async = true)) num(u[2]) else 20

                        when (kind) {
                            Kind.Game -> limit = min(limit, 30)
                            Kind.Discord -> limit = min(limit, 20)
                            else -> {}
                        }

                        transaction {
                            Driver.Users.select { Driver.Users.name.lowerCase() like "${u[0].toLowerCase()}%" }.limit(limit.toInt(), offset = offset).forEach {
                                sb.append(string(it[Driver.Users.id], it[Driver.Users.name], ranks[it[Driver.Users.rank]])).append("\n")
                            }
                        }
                        Result.Simple
                    }
                    "complex" -> try {
                        transaction {
                            exec("select id, name, rank from users where ${args[1]}") {
                                var c = 0
                                while (it.next()) {
                                    when (kind) {
                                        Kind.Game -> if(c > 30) break
                                        Kind.Discord -> if(c > 20) break
                                        else -> {}
                                    }
                                    sb.append(string(it.getString(1), it.getString(2), ranks[it.getString(3)])).append("\n")
                                    c++
                                }
                            }
                        }
                        Result.Complex
                    } catch (e: Exception) {
                        post {
                            send("search.error", e.message ?: "error does not even have message, that fucked up it is")
                        }
                        Result.Error
                        return@runLoggedGlobalScope
                    }
                    else -> {
                        post {
                            send("wrongOption", "complex simple")
                        }
                        Generic.Mismatch
                        return@runLoggedGlobalScope
                    }
                }

                if(sb.isEmpty()) {
                    sb.append(bundle.translate("search.empty"))
                }

                post {
                    send("search.result", sb.toString())
                }
            }

        return Generic.Success
    }


    fun string(id: Any, name: Any, rank: Ranks.Rank?): String {
        return when(kind) {
            Kind.Cmd -> "$id - $name - ${rank?.name}"
            Kind.Game -> "[gray]id: [white]$id [gray]name: [white]$name [gray]rank: ${rank?.postfix ?: "[red]<doom>"}"
            Kind.Discord -> "**id:** $id **name:** $name **rank:** ${rank?.name ?: "~~doom~~"}"
        }
    }

    enum class Result {
        Simple, Complex, Error
    }
}