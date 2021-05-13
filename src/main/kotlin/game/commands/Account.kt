package game.commands

import arc.util.Time
import cfg.Config
import db.Driver
import db.Driver.Users.password
import game.Users
import org.apache.commons.codec.digest.DigestUtils
import util.time
import java.util.regex.Pattern

// Account is game only
class Account(val driver: Driver, val users: Users, val discord: Discord, val config: Config): Command("account") {
    private val confirmQueue = HashMap<Long, String>()

    private val containsNumber = Pattern.compile(".*\\d.*")
    private val containsUpper = Pattern.compile(".*[A-Z].*")
    private val containsLower = Pattern.compile(".*[a-z].*")

    override fun run(args: Array<String>): Enum<*> {
        val user = user!!
        val id = user.data.id
        if(user.idSpectator() && !user.paralyzed) {
            send("account.denied")
            return Result.Denied
        }

        val password = if(user.paralyzed) "" else driver.users[id].password
        return when(args[0]) {
            "password" -> {
                if(user.paralyzed) {
                    send("account.paralyzed")
                    return Result.Paralyzed
                }
                val previous = confirmQueue[id]
                if(previous != null) {
                    confirmQueue.remove(id)
                    if(previous == args[1]) {
                        driver.users[id].password = hash(previous, id)
                        send("account.password.success")
                        Generic.Success
                    } else {
                        if(password != Driver.Users.noPassword) {
                            val complaint = check(args[1])
                            if(complaint == "") {
                                driver.users[id].password = hash(args[1], id)
                                send("account.password.success")
                                Generic.Success
                            } else {
                                send("account.password.$complaint")
                                Result.Complain
                            }
                        } else {
                            send("account.password.noMatch")
                            Result.NoMatch
                        }
                    }
                } else {
                    val complaint = check(args[1])
                    if(complaint == "") {
                        if(password != Driver.Users.noPassword && hash(args[1], id) != password) {
                            send("account.password.denied")
                            Result.Denied
                        } else {
                            send("account.password.confirm")
                            confirmQueue[id] = args[1]
                            Generic.Success
                        }
                    } else {
                        send("account.password.$complaint")
                        Result.Complain
                    }
                }
            }
            "name" -> {
                if(user.paralyzed) {
                    send("account.paralyzed")
                    return Result.Paralyzed
                }
                driver.users[id].name = args[1]
                users.reload(user)
                send("account.name.success")
                Generic.Success
            }
            "discord" -> {
                if(user.paralyzed) {
                    send("account.paralyzed")
                    Result.Paralyzed
                } else if(ensure(args, 3)) {
                    Generic.NotEnough
                } else {
                    val data = discord.verificationQueue[id]
                    if (data == null) {
                        send("account.discord.none")
                        Result.None
                    } else if(password != hash(args[1], id)) {
                        send("account.discord.password")
                        Result.Denied
                    } else if (data.code != args[2]) {
                        discord.verificationQueue.remove(id)
                        send("account.discord.code")
                        Result.CodeDenied
                    } else {
                        driver.users[0].discord = data.id
                        send("account.discord.success")
                        Generic.Success
                    }
                }
            }
            "login" -> {
                if(args[1] == "new") {
                    if (!user.paralyzed) {
                        val liveTime = Time.millis()- driver.users[id].bornDate
                        if (liveTime < config.maturity) {
                            send("account.login.premature", liveTime.time())
                            return Result.Premature
                        }
                        user.inner.name = driver.users[id].name
                    }
                    send("account.login.created")
                    val new = driver.users.new(user.inner)

                    driver.login(new.id, user.inner)
                    driver.logout(user.data)
                    users.reload(user)
                    Generic.Success

                } else if(ensure(args, 3)) {
                    Generic.NotEnough
                } else if(notNum(args[2], 2)) {
                    Generic.NotAInteger
                } else {
                    val tid = num(args[2])
                    if(!driver.users.exists(tid) || hash(args[1], tid) != driver.users[tid].password) {
                        send("account.login.denied")
                        Result.Denied
                    } else {
                        send("account.login.success")
                        if(!user.paralyzed) {
                            user.inner.name = driver.users[id].name
                        }
                        driver.login(tid, user.inner)
                        driver.logout(user.data)
                        users.reload(user)
                        Generic.Success
                    }
                }
            }
            else -> {
                send("wrongOption", "name password")
                Generic.Mismatch
            }
        }
    }

    private fun check(s: String): String {
        if(!containsLower.matcher(s).matches()) {
            return "lowercase"
        }
        if(!containsUpper.matcher(s).matches()) {
            return "uppercase"
        }
        if(!containsNumber.matcher(s).matches()) {
            return "number"
        }
        if(s.length < 8) {
            return "short"
        }

        return ""
    }

    private fun hash(password: String, id: Long): String {
        return DigestUtils.sha256Hex(password + id)
    }

    enum class Result {
        NoMatch, Complain, Denied, CodeDenied, Paralyzed, None, Premature
    }
}