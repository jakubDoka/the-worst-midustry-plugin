import arc.util.CommandHandler
import db.Driver
import db.Ranks
import game.Users
import game.commands.*
import mindustry.mod.Plugin
import mindustry_plugin_utils.Logger


class Main : Plugin() {

    private val root = "config/mods/worst/"
    private val logger = Logger(root + "logger/config.json")
    private val ranks = Ranks(root + "ranks/config.json")
    private val driver = Driver(root + "databaseDriver/config.json", ranks)
    private val users = Users(driver, logger, ranks)
    private val discord = Discord(root + "bot/config.json")
    private lateinit var game: Handler
    private lateinit var terminal: Handler


    init {
        discord.reg(Execute(driver))
        discord.reg(SetRank(driver, users, ranks))
    }

    override fun registerClientCommands(handler: CommandHandler) {
        bulkRemove(handler, "")

        game = Handler(handler, users, logger, Command.Kind.Game)

        game.reg(Help(game))
        game.reg(Execute(driver))
        game.reg(SetRank(driver, users, ranks))
    }

    override fun registerServerCommands(handler: CommandHandler) {
        bulkRemove(handler, "help")

        terminal = Handler(handler, users, logger, Command.Kind.Cmd)

        terminal.reg(Execute(driver))
        terminal.reg(SetRank(driver, users, ranks))
    }

    private fun bulkRemove(handler: CommandHandler, toRemove: String) {
        for(s in toRemove.split(" ")) handler.removeCommand(s)
    }
}

