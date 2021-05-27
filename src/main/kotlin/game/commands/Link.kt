package game.commands

import arc.util.Time
import bundle.Bundle
import cfg.Globals
import db.Driver
import discord4j.core.`object`.entity.User
import kotlin.random.Random



class
Link(val driver: Driver, val discord: Discord): Command("link") {
    private val random = Random(Time.millis())
    override fun run(args: Array<String>): Enum<*> {
        if(notNum(0, args)) {
            return Generic.NotAInteger
        }

        val id = num(args[0])

        val password = driver.users.get(id, Driver.Users.password)

        if (password == null) {
            send("notFound")
            return Generic.NotFound
        }

        if (password == Driver.Users.noPassword) {
            send("link.noPassword")
            return Result.NoPassword
        }

        val code = code()
        if(Globals.testing) {
            discord.verificationQueue[id] = Discord.CodeData(code, "fake-snowflake")
            Bundle.send("link.code", code)
        } else {
            discord.verificationQueue[id] = Discord.CodeData(code, message!!.author.get().id.asString())
            try {
                send("link.failedDm")
                dm.sendPrivate("link.code", code)
                send("link.failedDm")
            } catch (e: Exception) {
                send("link.failedDm")
                return Result.SendFailed
            }
        }
        send("link.success")
        return Generic.Success
    }

    private fun code(): String {
        return (random.nextInt(9999-1000) + 1000).toString()
    }

    enum class Result {
        NoPassword, SendFailed
    }
}