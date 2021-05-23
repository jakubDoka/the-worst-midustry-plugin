package game.commands

import arc.util.CommandHandler
import cfg.Config
import cfg.Globals
import db.Driver
import db.Ranks
import game.Users
import game.Voting
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mindustry_plugin_utils.Logger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File

class CommandTest {
    init { Globals.testing = true }
    private val config = Config()
    private val driver = Driver()
    private val ranks = Ranks()
    private val logger = Logger("config/logger.json")
    private val users = Users(driver, logger, ranks, Config(), )
    private val handler = Handler(users, logger, config, Command.Kind.Game)
    private val discord = Discord(logger = logger, driver = driver)
    private val voting = Voting(users)


    init {
        handler.init(CommandHandler("/"))
        handler.reg(Help.Game(handler))
        handler.reg(Execute(driver))
        handler.reg(SetRank(driver, users, ranks))
        handler.reg(Account(driver, users, discord, config, ranks))
        handler.reg(Configure(mapOf()))

        ranks["builder"] = Ranks.Rank(quest = mapOf("built" to 1000L), kind = Ranks.Kind.Special)
        ranks["builder"]!!.name = "builder"

        ranks["everything"] = Ranks.Rank(quest = mapOf(
            "built" to 1000,
            "destroyed" to 10,
            "killed" to 100,
            "deaths" to 10,
            "played" to 100,
            "wins" to 10,
            "messages" to 30,
            "commands" to 50,
            "playTime" to 40,
            "silence" to 80,
            "age" to 30,
            "ranks" to "a b c",
            "rankCount" to 10,
            "rankTotalValue" to 20,
            "points" to 40,
            "roles" to "k f m",
        ), kind = Ranks.Kind.Special)
        ranks["everything"]!!.name = "everything"
    }

    @Test
    fun ranks() {
        val u = users.test()

        u.data.stats.built = 1000
        users.reload(u)
        runBlocking { delay(1000) }
        var found = false
        users.forEach {
            if (it.value.data.specials.contains("builder")) found = true
        }
        assert(found)
    }

    @AfterEach
    fun cleanup() {
        driver.drop()
        println("=======================================================")
    }

    @Test
    fun help() {
        val h = Help.Game(handler)
        h.kind = Command.Kind.Game
        h.user = users.test()

        h.assert(Help.Result.List, "1")
        h.assert(Help.Result.Info, "help")
        h.assert(Help.Result.Invalid, "something")
    }

    @Test
    fun execute() {
        val c = Execute(driver)
        c.kind = Command.Kind.Cmd
        c.assert(Execute.Result.Failed, "duck")
        c.assert(Command.Generic.Success, "select * from users")

        c.kind = Command.Kind.Game
        c.user = users.test()
        c.assert(Command.Generic.Denied, "")
        c.user!!.data.rank = Ranks()["dev"]!!
        c.assert(Command.Generic.Success, "select * from users")
        c.assert(Execute.Result.Failed, "hey db how are you?")
    }

    @Test
    fun setrank() {
        users.test("asd", "other")

        val s = SetRank(driver, users, ranks)
        s.kind = Command.Kind.Game
        s.user = users.test()

        s.assert(Command.Generic.Denied, "ab", "something")
        s.user!!.data.rank = ranks.admin
        s.assert(Command.Generic.NotAInteger, "ab", "something")
        s.assert(Command.Generic.NotFound, "10", "something")
        s.assert(SetRank.Result.InvalidRank, "1", "something")
        s.assert(SetRank.Result.NotMutable, "1", "admin")
        s.assert(Command.Generic.Success, "1", "verified")

        assert(driver.users.load(1).rank.name == "verified") { driver.users.load(1).rank.name }
    }

    @Test
    fun account() {
        val s = Account(driver, users, discord, config, ranks)
        s.kind = Command.Kind.Game
        s.user = users.test()

        s.user!!.data.rank = ranks.griefer
        s.assert(Command.Generic.Denied, "password", "h")
        s.user!!.data.rank = ranks.default

        s.assert(Account.Result.Complain, "password", "9")
        s.assert(Account.Result.Complain, "password", "a")
        s.assert(Account.Result.Complain, "password", "hA")
        s.assert(Account.Result.Complain, "password", "hA9")

        s.assert(Command.Generic.Success, "password", "hA912345")
        s.assert(Account.Result.NoMatch, "password", "d")
        s.assert(Command.Generic.Success, "password", "hA912345")
        s.assert(Command.Generic.Success, "password", "hA912345")

        s.assert(Command.Generic.Denied, "password", "hA912345x")
        s.assert(Command.Generic.Success, "password", "hA912345")
        s.assert(Account.Result.Complain, "password", "h")
        s.assert(Command.Generic.Denied, "password", "hA912345x")
        s.assert(Command.Generic.Success, "password", "hA912345")
        s.assert(Command.Generic.Success, "password", "hA912345x")

        s.assert(Command.Generic.Success, "name", "hon")
        val dat = driver.users.load(s.user!!.data.id)
        assert(dat.name == "hon")

        s.user!!.data.id = -1
        s.assert(Account.Result.Paralyzed, "password", "")
        s.assert(Account.Result.Paralyzed, "discord", "")
        s.assert(Account.Result.Paralyzed, "name", "")

        s.assert(Command.Generic.NotEnough, "login", "")
        s.assert(Command.Generic.NotAInteger, "login", "", "")
        s.assert(Command.Generic.Denied, "login", "", "1")
        s.assert(Command.Generic.Denied, "login", "hA912345x", "-4")
        s.assert(Command.Generic.Success, "login", "hA912345x", "1")
        s.user!!.data.id = 1

        s.assert(Account.Result.Premature, "login", "new")
        s.config.data.maturity = 0
        s.assert(Command.Generic.Success, "login", "new")

        s.assert(Account.Result.None, "discord", "", "")
        discord.verificationQueue[1] = Discord.CodeData("1234", "asd")
        s.assert(Command.Generic.Denied, "discord", "", "")
        s.assert(Account.Result.CodeDenied, "discord", "hA912345x", "")
        s.assert(Account.Result.None, "discord", "", "")
        discord.verificationQueue[1] = Discord.CodeData("1234", "asd")
        s.assert(Command.Generic.Success, "discord", "hA912345x", "1234")
    }

    @Test
    fun link() {
        val l = Link(driver, discord)
        l.kind = Command.Kind.Discord

        val user = users.test()
        l.assert(Command.Generic.NotAInteger, "h")
        l.assert(Command.Generic.NotFound, "2")
        l.assert(Link.Result.NoPassword, "1")

        val a = Account(driver, users, discord, config, ranks)
        a.user = user
        a.run(arrayOf("password", "hA912345x"))
        a.run(arrayOf("password", "hA912345x"))

        l.assert(Command.Generic.Success, "1")
    }

    @Test
    fun configure() {
        val c = Configure(mapOf(
            "bot" to discord,
            "ranks" to ranks,
            "driver" to driver,
        ))
        c.user = users.test()

        c.assert(Command.Generic.Denied, "hell")
        c.user!!.data.rank = ranks["dev"]!!
        c.assert(Configure.Result.Unknown, "hell")
        c.assert(Configure.Result.View, "bot", "view")
        c.assert(Configure.Result.Reload, "bot", "reload")
        c.assert(Configure.Result.Count, "bot", "something")
        c.assert(Configure.Result.Result, "bot", "remove", "prefix")
        c.assert(Configure.Result.Result, "bot", "insert", "str", "prefix", "?")
        c.assert(Configure.Result.Result, "bot", "insert", "p", "prefix", "?")
        c.assert(Configure.Result.Result, "bot", "h", "str", "prefix", "!")
        c.assert(Configure.Result.Result, "bot", "remove", "str", "hello.there", "!")
        c.assert(Configure.Result.Reload, "ranks", "reload")
    }

    @Test
    fun profile() {
        val p = Profile(driver, ranks, users)
        p.user = users.test()
        p.kind = Command.Kind.Game

        fun profile(p: Command, doMe: Boolean = true) {
            p.assert(Command.Generic.NotAInteger, "e", "f")
            p.assert(Command.Generic.NotFound, "2", "f")
            p.assert(Command.Generic.Mismatch, "1", "f")
            p.assert(Profile.Result.Personal, "1", "personal")
            p.assert(Profile.Result.Stats, "1", "stats")
            if(doMe) p.assert(Profile.Result.Stats, "me", "stats")
        }

        profile(p)

        val a = Account(driver, users, discord, config, ranks)
        a.user = p.user
        a.assert(Command.Generic.Success, "password", "hA912345")
        a.assert(Command.Generic.Success, "password", "hA912345")

        val l = Link(driver, discord)
        l.kind = Command.Kind.Discord
        l.assert(Command.Generic.Success, "1")

        a.assert(Command.Generic.Success, "discord", "hA912345", discord.verificationQueue[1]!!.code)

        p.kind = Command.Kind.Discord
        profile(p)

        p.kind = Command.Kind.Cmd
        profile(Profile(driver, ranks, users), false)
    }

    @Test
    fun search() {
        val s = Search(ranks)
        s.user = users.test()
        users.test(name = "hl")
        users.test(name = "cld")
        users.test(name = "flm")
        users.test(name = "hlk")
        s.run(arrayOf("simple", "h 1 1"))
        s.run(arrayOf("simple", "h"))
        s.run(arrayOf("simple", "h 1"))
        s.run(arrayOf("complex", "name = 'cld'"))

        runBlocking { delay(500) }
    }

    @Test
    fun look() {
        val l = Look(ranks, users)
        l.user = users.test()
        l.user!!.data.specials.add("fiction")
        l.user!!.data.specials.add("builder")

        l.assert(Command.Generic.Denied, "rank", "dev")
        l.assert(Command.Generic.NotFound, "rank", "fiction")
        l.assert(Command.Generic.Success, "rank", "builder")
    }

    @Test
    fun rankInfo() {
        val l = RankInfo(ranks, users.quests)
        l.user = users.test()

        l.assert(RankInfo.Result.All, "all")
        l.assert(Command.Generic.NotFound, "rank", "fiction")
        l.assert(Command.Generic.Success, "rank", "everything")
    }

    @Test
    fun verificationTest() {
        assert(File("config/tests").deleteRecursively())
        val t = VerificationTest("config/tests", ranks, users, config)
        t.user = users.test()

        t.assert(VerificationTest.Result.Unavailable)
        t.reload()
        t.assert(VerificationTest.Result.Initiated)
        t.assert(VerificationTest.Result.Repeat)
        t.assert(Command.Generic.NotAInteger, "d")
        t.assert(VerificationTest.Result.OutOfBounds, "5")
        t.assert(VerificationTest.Result.OutOfBounds, "0")
        t.assert(VerificationTest.Result.Incorrect, "2")
        t.assert(VerificationTest.Result.Fail, "2")
        t.assert(Command.Generic.Denied)
        t.penalties.remove(1)
        t.assert(VerificationTest.Result.Initiated)
        t.assert(VerificationTest.Result.Correct, "1")
        t.assert(Command.Generic.Success, "1")
    }

    @Test
    fun votekick() {
        val v = VoteKick(driver, users, ranks, voting)
        v.user = users.test()
        v.assert(Command.Generic.NotFound, "hello#10")
    }
}