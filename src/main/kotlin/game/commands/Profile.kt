package game.commands

import bundle.Bundle
import cfg.Globals.time
import db.Driver
import db.Ranks
import discord4j.rest.util.Color
import game.Users

import mindustry_plugin_utils.Fs.jsonToString
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction


class Profile(val driver: Driver, val ranks: Ranks, val users: Users): Command("profile") {
    override fun run(args: Array<String>): Enum<*> {
        val u = if (args[0] == "me") {
            data
        } else {
            if (notNum(0, args)) {
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
                alert(
                    "profile.personal.title",
                    "profile.personal.body",
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
                alert(
                    "profile.stats.title",
                    "profile.stats.body",
                    s.points(driver.config.multiplier) + u.rankValue(ranks),
                    s.built,
                    s.destroyed,
                    s.killed,
                    s.deaths,
                    s.played,
                    s.wins,
                    s.messages,
                    s.commands,
                    s.playTime.time(),
                    s.silence.time()
                )
                return Result.Stats
            }
        }

        send("wrongOption", "personal stats")
        return Generic.Mismatch
    }

    enum class Result {
        Personal, Stats
    }

}