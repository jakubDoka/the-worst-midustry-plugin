package game.commands

import arc.util.CommandHandler
import db.Driver
import db.Ranks
import game.Users
import mindustry_plugin_utils.Logger
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll

class CommandTest {
    val driver = Driver("", testing = true)
    val ranks = Ranks()
    val users = Users(driver, Logger(""), ranks, testing = true)
    val handler = Handler(CommandHandler("/"), users, Logger(""), Command.Kind.Game)
    val discord = Discord()

    init {
        handler.reg(Help(handler))
        handler.reg(Execute(driver))
        handler.reg(SetRank(driver, users, ranks))
        handler.reg(Account(driver, users, discord))
    }

    @Test
    fun help() {
        val h = Help(handler)
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
        val target = users.test("asd", "other")

        val s = SetRank(driver, users, ranks)
        s.kind = Command.Kind.Game
        s.user = users.test()

        s.assert(SetRank.Result.Denied, "ab", "something")
        s.user!!.data.rank = ranks.admin
        s.assert(Command.Generic.NotAInteger, "ab", "something")
        s.assert(SetRank.Result.NotFound, "10", "something")
        s.assert(SetRank.Result.InvalidRank, "1", "something")
        s.assert(SetRank.Result.NotMutable, "1", "admin")
        s.assert(Command.Generic.Success, "1", "verified")

       assert(driver.users[1].rank.name == "verified") { driver.users[1].rank.name }
    }

    @Test
    fun account() {
        val s = Account(driver, users, discord)
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

        s.assert(Command.Generic.Success, "login", "new")

        s.assert(Account.Result.None, "discord", "", "")
        discord.verificationQueue[1] = Discord.CodeData("1234", "asd")
        s.assert(Account.Result.Denied, "discord", "", "")
        s.assert(Account.Result.CodeDenied, "discord", "hA912345x", "")
        s.assert(Account.Result.None, "discord", "", "")
        discord.verificationQueue[1] = Discord.CodeData("1234", "asd")
        s.assert(Command.Generic.Success, "discord", "hA912345x", "1234")
    }
}