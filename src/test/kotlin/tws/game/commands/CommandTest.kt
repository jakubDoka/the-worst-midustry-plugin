package the_worst_one.game.commands

import arc.util.CommandHandler
import the_worst_one.cfg.Config
import the_worst_one.cfg.Globals
import the_worst_one.db.Driver
import the_worst_one.db.Ranks
import the_worst_one.game.Docks
import the_worst_one.game.Users
import the_worst_one.game.Voting
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mindustry.Vars
import mindustry.content.Items
import mindustry.core.ContentLoader
import mindustry_plugin_utils.Logger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File

class CommandTest {
    init { Globals.testing = true }
    private val config = Config(data = Config.Data(vpnApyKey = "e28a8957c617446da4f8c4297efd80ae"))
    private val ranks = Ranks()
    private val driver = Driver(ranks = ranks)
    private val logger = Logger("config/logger.json")
    private val users = Users(driver, ranks, config)
    private val discord = Discord(logger = logger, driver = driver, users = users)
    private val handler = Handler(users, config, Command.Kind.Game, discord)
    private val voting = Voting(users)
    private val docks = Docks(users, "config/docks.json")


    init {
        driver.reload()
        ranks.reload()
        Vars.content = ContentLoader()
        Items().load()

        handler.init(CommandHandler("/"))
        handler.reg(Help.Game(handler))
        handler.reg(Execute(driver))
        handler.reg(SetRank(driver, users, ranks, discord))
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
            "roles" to "k;f;m",
        ), kind = Ranks.Kind.Special)
        ranks["everything"]!!.name = "everything"
    }

    @Test
    fun vpn() {
        assert(!users.vpn.authorize("109.230.35.76"))
        assert(users.vpn.authorize("104.238.96.0"))
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
        c.assert(Command.Generic.Success, "select * from users")
        c.assert(Execute.Result.Failed, "hey the_worst_one.db how are you?")
    }

    @Test
    fun setrank() {
        users.test("asd", "other")

        val s = SetRank(driver, users, ranks, discord)
        s.kind = Command.Kind.Game
        s.user = users.test()

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
        l.assert(Command.Generic.NotFound, "fiction")
        for (k in ranks) println(k)
        l.assert(Command.Generic.Success, "everything")
    }

    @Test
    fun verificationTest() {
        assert(File("config/tests").deleteRecursively())
        val t = VerificationTest(ranks, users, config, "config/tests")
        t.reload()
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
        val v = VoteKick(driver, users, ranks, voting, discord)
        v.user = users.test()
        v.assert(Command.Generic.NotFound, "hello#10")
    }

    @Test
    fun maps() {
        val mm = MapManager(driver)
        mm.kind = Command.Kind.Cmd
        File("config/maps/caldera.msav").delete()
        mm.run(arrayOf("add", "testData/caldera.msav"))

        val m = Maps(config, voting, driver)
        m.user = users.test()

        m.assert(Command.Generic.Success, "list")
        m.assert(Command.Generic.Success, "list", "a")
        m.assert(Command.Generic.Success, "list", "1")

        m.assert(Command.Generic.NotFound, "change", "2")
        m.assert(Maps.Result.NotActive, "change", "1")
        m.assert(Maps.Result.NotActive, "change", "Caldera")
        mm.assert(Command.Generic.Success, "activate", "1")
        m.assert(Command.Generic.Vote, "change", "1")

        m.assert(Command.Generic.Vote, "restart")

        m.assert(Command.Generic.Vote, "end")
    }

    @Test
    fun mapmanager() {
        val m = MapManager(driver)
        m.kind = Command.Kind.Cmd

        m.assert(Command.Generic.NotFound, "add", "something")
        m.assert(MapManager.Result.Invalid, "add", "password.txt")
        m.assert(Command.Generic.Success, "add", "testData/caldera.msav")

        m.assert(Command.Generic.NotEnough, "update", "2")
        m.assert(MapManager.Result.Nonexistent, "update", "2", "testData/caldera.msav")
        m.assert(Command.Generic.Success, "update", "1", "testData/caldera.msav")

        m.assert(Command.Generic.Success, "remove", "1")
        m.assert(MapManager.Result.Nonexistent, "remove", "1")
        m.assert(Command.Generic.Success, "add", "testData/caldera.msav")

        m.assert(Command.Generic.Success, "activate", "2")
        m.assert(Command.Generic.Success, "deactivate", "2")
        m.assert(MapManager.Result.Error, "deactivate", "2")
    }

    @Test
    fun loadout() {
        val l = Loadout(driver, docks, voting, "config/loadout.json")

        l.user = users.test()

        l.assert(Command.Generic.Success, "status")
        l.assert(Command.Generic.NotAInteger, "store", "foo", "blue")

        l.assert(Loadout.Result.Error, "store", "10", "where foo")
        l.assert(Loadout.Result.InvalidItem, "store", "10", "itemName == \"coal\" and itemAmount < 30")
        l.assert(Command.Generic.Success, "store", "10", "coal")
        l.assert(Command.Generic.Success, "store", "10", "where itemName == \"coal\" and itemAmount < 30")

        l.assert(Loadout.Result.Redundant, "load", "0", "coal")
        l.assert(Command.Generic.Success, "load", "10", "coal")
    }

    @Test
    fun mute() {
        val m = Mute(driver, users)

        m.user = users.test()

        m.assert(Command.Generic.Denied, "1", "all")
        m.assert(Command.Generic.NotFound, "2")

        m.user!!.data.rank.control = Ranks.Control.High
        m.assert(Command.Generic.Success, "1")
        m.assert(Command.Generic.Success, "1", "all")

        m.assert(Command.Generic.Success, "1")
        m.assert(Command.Generic.Success, "1", "all")
    }
}