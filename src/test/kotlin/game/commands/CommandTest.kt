package game.commands

import arc.util.CommandHandler
import cfg.Config
import db.Driver
import db.Ranks
import game.Users
import mindustry_plugin_utils.Logger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class CommandTest {
    private val driver = Driver( testing = true)
    private val ranks = Ranks()
    private val logger = Logger("config/logger.json")
    private val users = Users(driver, logger, ranks, Config(), testing = true)
    private val handler = Handler(users, logger, Command.Kind.Game)
    private val discord = Discord()
    private val config = Config()

    init {
        handler.init(CommandHandler("/"))
        handler.reg(Help.Game(handler))
        handler.reg(Execute(driver))
        handler.reg(SetRank(driver, users, ranks))
        handler.reg(Account(driver, users, discord, config))
        handler.reg(Configure(mapOf()))
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
        c.assert(Execute.Result.Denied, "")
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

        s.assert(SetRank.Result.Denied, "ab", "something")
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
        val s = Account(driver, users, discord, config)
        s.kind = Command.Kind.Game
        s.user = users.test()

        s.user!!.data.rank = ranks.griefer
        s.assert(Account.Result.Denied, "password", "h")
        s.user!!.data.rank = ranks.default

        s.assert(Account.Result.Complain, "password", "9")
        s.assert(Account.Result.Complain, "password", "a")
        s.assert(Account.Result.Complain, "password", "hA")
        s.assert(Account.Result.Complain, "password", "hA9")

        s.assert(Command.Generic.Success, "password", "hA912345")
        s.assert(Account.Result.NoMatch, "password", "d")
        s.assert(Command.Generic.Success, "password", "hA912345")
        s.assert(Command.Generic.Success, "password", "hA912345")

        s.assert(Account.Result.Denied, "password", "hA912345x")
        s.assert(Command.Generic.Success, "password", "hA912345")
        s.assert(Account.Result.Complain, "password", "h")
        s.assert(Account.Result.Denied, "password", "hA912345x")
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
        s.assert(Account.Result.Denied, "login", "", "1")
        s.assert(Account.Result.Denied, "login", "hA912345x", "-4")
        s.assert(Command.Generic.Success, "login", "hA912345x", "1")
        s.user!!.data.id = 1

        s.assert(Account.Result.Premature, "login", "new")
        s.config.data.maturity = 0
        s.assert(Command.Generic.Success, "login", "new")

        s.assert(Account.Result.None, "discord", "", "")
        discord.verificationQueue[1] = Discord.CodeData("1234", "asd")
        s.assert(Account.Result.Denied, "discord", "", "")
        s.assert(Account.Result.CodeDenied, "discord", "hA912345x", "")
        s.assert(Account.Result.None, "discord", "", "")
        discord.verificationQueue[1] = Discord.CodeData("1234", "asd")
        s.assert(Command.Generic.Success, "discord", "hA912345x", "1234")
    }

    @Test
    fun link() {
        val l = Link(driver, discord, true)
        l.kind = Command.Kind.Cmd
        val user = users.test()
        l.assert(Command.Generic.NotAInteger, "h")
        l.assert(Command.Generic.NotFound, "2")
        l.assert(Link.Result.NoPassword, "1")

        val a = Account(driver, users, discord, Config())
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

        c.assert(Configure.Result.Denied, "hell")
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
    }

    @Test
    fun profile() {
        val p = Profile.Game(driver, ranks, users)
        p.user = users.test()

        fun profile(c: Command, doMe: Boolean = true) {
            p.assert(Command.Generic.NotAInteger, "e", "f")
            p.assert(Command.Generic.NotFound, "2", "f")
            p.assert(Command.Generic.Mismatch, "1", "f")
            p.assert(Profile.Result.Personal, "1", "personal")
            p.assert(Profile.Result.Stats, "1", "stats")
            if(doMe) p.assert(Profile.Result.Stats, "me", "stats")
        }

        profile(p)

        val a = Account(driver, users, discord, config)
        a.user = p.user
        a.assert(Command.Generic.Success, "password", "hA912345")
        a.assert(Command.Generic.Success, "password", "hA912345")

        val l = Link(driver, discord, true)
        l.assert(Command.Generic.Success, "1")

        a.assert(Command.Generic.Success, "discord", "hA912345", discord.verificationQueue[1]!!.code)

        val d = Profile.Discord(driver, ranks, users)
        profile(d)

        profile(Profile.Terminal(driver, ranks, users), false)
    }
}