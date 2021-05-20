import arc.util.CommandHandler
import cfg.Config
import cfg.Reloadable
import com.beust.klaxon.Klaxon
import db.Driver
import db.Ranks
import game.Filter
import game.Users
import game.commands.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mindustry.mod.Plugin
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Logger
import java.io.File


class Main : Plugin(), Reloadable {
    private val config = Config()

    private val root = "config/mods/worst/"
    override val configPath = root + "config.json"
    private val logger = Logger(root + "logger/config.json")
    private val ranks = Ranks(root + "ranks/config.json")
    private val driver = Driver(root + "databaseDriver/config.json", ranks)
    private val users = Users(driver, logger, ranks, config)


    private val filter = Filter(users, ranks, logger)
    private val reloadable = HashMap(mapOf(
        "main" to this,
        "driver" to driver,
        "ranks" to ranks
    ))

    private val game = Handler(users, logger, Command.Kind.Game)
    private val terminal = Handler(users, logger, Command.Kind.Cmd)

    private val discord = Discord(root + "bot/config.json", logger, driver) {
        it.reg(Help.Discord(it, game))
        it.reg(Execute(driver))
        it.reg(SetRank(driver, users, ranks))
        it.reg(Link(driver, it))
        it.reg(Configure(reloadable))
        it.reg(Profile(driver, ranks, users))
        it.reg(Search(ranks))
        it.reg(RankInfo(ranks, users.quests))

        users.quests.discord = it
        reloadable["bot"] = it
    }

    override fun reload() {
        config.data = try {
            Klaxon().parse(File(configPath))!!
        } catch(e: Exception) {
            Fs.createDefault(configPath, Config.Data())
            Config.Data()
        }
    }



    init {
        reload()


    }

    override fun init() {
        filter.init()
    }

    override fun registerClientCommands(handler: CommandHandler) {
        bulkRemove(handler, "help")

        game.init(handler)

        game.reg(Help.Game(game))
        game.reg(Execute(driver))
        game.reg(SetRank(driver, users, ranks))
        game.reg(Account(driver, users, discord, config, ranks))
        game.reg(Configure(reloadable))
        game.reg(Profile(driver, ranks, users))
        game.reg(Search(ranks))
        game.reg(Minimal.Reload(users))
        game.reg(Look(ranks, users))
        game.reg(RankInfo(ranks, users.quests))
    }

    override fun registerServerCommands(handler: CommandHandler) {
        bulkRemove(handler, "")

        terminal.init(handler)

        terminal.reg(Execute(driver))
        terminal.reg(SetRank(driver, users, ranks))
        terminal.reg(Configure(reloadable))
        terminal.reg(Profile(driver, ranks, users))
        terminal.reg(Search(ranks))
        terminal.reg(RankInfo(ranks, users.quests))
    }

    private fun bulkRemove(handler: CommandHandler, toRemove: String) {
        for(s in toRemove.split(" ")) handler.removeCommand(s)
    }
}

