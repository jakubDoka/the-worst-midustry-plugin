package game.commands

import arc.util.CommandHandler
import db.Driver
import db.Ranks
import game.Users
import mindustry_plugin_utils.Logger
import org.junit.jupiter.api.Test

class CommandTest {
    val driver = Driver("", testing = true)
    val ranks = Ranks()
    val users = Users(driver, Logger(""), ranks, testing = true)
    val handler = Handler(CommandHandler("/"), users, Logger(""), Command.Kind.Game)

    init {
        for(i in 0..20) {
            handler.inner.register("command$i", "[command$i]", "command$i", {})
        }

        handler.reg(Help(handler))
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
        c.assert(Execute.Result.Success, "select * from users")

        c.kind = Command.Kind.Game
        c.user = users.test()
        c.assert(Execute.Result.Denied, "")
        c.user!!.data.rank = Ranks()["dev"]!!
        c.assert(Execute.Result.Success, "select * from users")
        c.assert(Execute.Result.Failed, "hey db how are you?")
    }

    @Test
    fun setrank() {
        val target = users.test("asd", "other")

        val s = SetRank(driver, users, ranks)
        s.kind = Command.Kind.Game
        s.user = users.test()

        s.assert(SetRank.Result.Denied, "10", "something")
        s.user!!.data.rank = ranks.admin
        s.assert(SetRank.Result.NotFound, "10", "something")
        s.assert(SetRank.Result.InvalidRank, "1", "something")
        s.assert(SetRank.Result.NotMutable, "1", "admin")
        s.assert(SetRank.Result.Success, "1", "verified")
    }
}