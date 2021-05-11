import arc.Events
import arc.util.CommandHandler
import arc.util.Log
import bundle.Bundle
import db.Driver
import db.Ranks
import game.UserStore
import game.commands.Handler
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.game.EventType.BuildSelectEvent
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.mod.Plugin
import mindustry_plugin_utils.Logger


class Main() : Plugin() {

    private val root = "config/mods/worst/"
    private val logger = Logger(root + "logger/config.json")
    private val ranks = Ranks(root + "ranks/config.json")
    private val driver = Driver(root + "databaseDriver/config.json", ranks)
    private val users = UserStore(driver, logger)
    private lateinit var game: Handler
    private lateinit var terminal: Handler


    init {

    }

    override fun registerClientCommands(handler: CommandHandler) {
        handler.register(
            "reply", "<text...>", "A simple ping command that echoes a player's text."
        ) { args: Array<String>, player: Player ->
            player.sendMessage("You said: [accent] " + args[0]);
        }
    }

    override fun registerServerCommands(handler: CommandHandler) {
        game = Handler(handler, Bundle(), users)
    }
}

