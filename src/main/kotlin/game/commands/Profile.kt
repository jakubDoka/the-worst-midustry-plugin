package game.commands

import bundle.Bundle
import db.Driver
import db.Ranks
import discord4j.rest.util.Color
import game.Users
import mindustry_plugin_utils.Templates.time
import mindustry_plugin_utils.Fs.jsonToString
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction


abstract class Profile(val driver: Driver, val ranks: Ranks, val users: Users): Command("profile") {
    class Game(driver: Driver, ranks: Ranks, users: Users) : Profile(driver, ranks, users) {
        override fun run(args: Array<String>): Enum<*> {
            val u = if (args[0] == "me") {
                user!!.data
            } else {
                if (notNum(args[0], 0)) {
                    return Generic.NotAInteger
                }
                users.withdraw(num(args[0]))
            }

            if (u == null) {
                send("notFound")
                return Generic.NotFound
            }

            when (args[1]) {
                "personal" -> {
                    user!!.alert(
                        title = user!!.translate("profile.personal.title"),
                        bundleKey = "profile.personal.body",
                        u.id,
                        u.name,
                        u.age.time(),
                        u.rank.postfix,
                        u.specials.size,
                        u.country,
                    )
                    return Result.Personal
                }
                "stats" -> {
                    val s = u.stats
                    user!!.alert(
                        title = user!!.translate("profile.stats.title"),
                        bundleKey = "profile.stats.body",
                        s.points(),
                        s.built,
                        s.destroyed,
                        s.killed,
                        s.deaths,
                        s.played,
                        s.wins,
                        s.messages,
                        s.commands,
                        s.playTime,
                        s.silence.time()
                    )
                    return Result.Stats
                }
            }

            send("wrongOption", "personal stats")
            return Generic.Mismatch
        }
    }

    class Terminal(driver: Driver, ranks: Ranks, users: Users) : Profile(driver, ranks, users) {
        override fun run(args: Array<String>): Enum<*> {
            if (notNum(args[0], 0)) {
                return Generic.NotAInteger
            }
            val u = users.withdraw(num(args[0]))
            if (u == null) {
                send("notFound")
                return Generic.NotFound
            }

            when (args[1]) {
                "personal" -> {
                    println(u.jsonToString())
                    return Result.Personal
                }
                "stats" -> {
                    println(u.stats.jsonToString())
                    return Result.Stats
                }
            }

            send("wrongOption", "personal stats")
            return Generic.Mismatch
        }
    }

    class Discord(driver: Driver, ranks: Ranks, users: Users) : Profile(driver, ranks, users) {
        override fun run(args: Array<String>): Enum<*> {
            val u = if (args[0] == "me") {
                transaction {
                    val id = message!!.author.get().id.asString()
                    val query = Driver.Users.slice(Driver.Users.id).select { Driver.Users.discord eq id }
                    if (query.empty()) null else driver.users.load(query.first()[Driver.Users.id].value)
                }
            } else {
                if (notNum(args[0], 0)) {
                    return Generic.NotAInteger
                }
                users.withdraw(num(args[0]))
            }

            if (u == null) {
                send("notFound")
                return Generic.NotFound
            }

            when (args[1]) {
                "personal" -> {

                    send {
                        it.setTitle(Bundle.translate("profile.personal.title"))
                        it.setDescription(
                            Bundle.translate(
                                "profile.personal.discord.body",
                                u.id,
                                u.name,
                                u.age.time(),
                                u.rank.postfix,
                                u.specials.size,
                                u.country,
                            )
                        )
                        it.setColor(Color.CYAN)
                    }

                    return Result.Personal
                }
                "stats" -> {
                    val s = u.stats
                    send {
                        it.setTitle(Bundle.translate("profile.stats.title"))
                        it.setDescription(
                            Bundle.translate(
                                "profile.stats.discord.body",
                                s.points(),
                                s.built,
                                s.destroyed,
                                s.killed,
                                s.deaths,
                                s.played,
                                s.wins,
                                s.messages,
                                s.commands,
                                s.playTime,
                                s.silence.time()
                            )
                        )
                        it.setColor(Color.CYAN)
                    }

                    return Result.Stats
                }
            }

            send("wrongOption", "personal stats")
            return Generic.Mismatch
        }
    }

    enum class Result {
        Personal, Stats
    }

}