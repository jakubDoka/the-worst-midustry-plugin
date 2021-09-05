package the_worst_one

import arc.util.CommandHandler
import the_worst_one.cfg.Config
import the_worst_one.cfg.Globals
import the_worst_one.cfg.Reloadable
import com.beust.klaxon.Klaxon
import the_worst_one.db.Driver
import the_worst_one.db.Ranks
import the_worst_one.game.*
import the_worst_one.game.commands.*
import mindustry.game.EventType
import mindustry.mod.Plugin
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Logger
import java.io.File

class Main : Plugin(), Reloadable {
    override var configPath = Globals.root + "config.json"

    private val config = Config()
    private val logger = Globals.logger
    private val ranks = Ranks(Globals.root + "ranks/config.json")
    private val driver = Driver(Globals.root + "databaseDriver/config.json", ranks)
    private val users = Users(driver, logger, ranks, config)
    private val filter = Filter(users, ranks, logger, config)
    private val voting = Voting(users)
    private val pets = Pets(users, logger, Globals.root + "pets/config.json")
    private val docks = Docks(users, logger, Globals.root + "docks/config.json")
    private val verificationTest = VerificationTest(ranks, users, config, Globals.root + "tests")
    private val loadout = Loadout(driver, docks, voting, Globals.root + "loadout/config.json")
    private val buildcore = BuildCore(driver, docks, voting, filter.banned, Globals.root + "buildcore/config.json")
    private val boost = Boost(driver, voting, logger, Globals.root + "boost/config.json")
    private val hud = Hud(users, arrayOf(voting, docks, boost), logger)

    private val reloadable = mutableMapOf(
        "main" to this,
        "driver" to driver,
        "ranks" to ranks,
        "pets" to pets,
        "docks" to docks,
        "test" to verificationTest,
        "loadout" to loadout,
        "buildcore" to buildcore,
        "boost" to boost,
    )

    private val discord = Discord(Globals.root + "bot/config.json", logger, driver, users) {
        it.reg(Help.Discord(it, game))
        it.reg(Execute(driver))
        it.reg(SetRank(driver, users, ranks, it))
        it.reg(Link(driver, it))
        it.reg(Configure(reloadable))
        it.reg(Profile(driver, ranks, users))
        it.reg(Search(ranks))
        it.reg(RankInfo(ranks, users.quests))
        it.reg(MapManager(driver))
        it.reg(Maps(config, voting, driver))
        it.reg(Online(users))
    }
    private val game: Handler = Handler(users, logger, config, Command.Kind.Game, discord)
    private val terminal = Handler(users, logger, config, Command.Kind.Cmd, discord)

    override fun reload() {
        config.data = try {
            val cfg = Klaxon().parse<Config.Data>(File(configPath))!!
            for((k, v) in reloadable) {
                val path = config.data.configPaths[k]
                if (path != null) {
                    v.configPath = path
                }
                if(k == "main") continue
                v.reload()
            }
            cfg
        } catch(e: Exception) {
            e.printStackTrace()
            Fs.createDefault(configPath, Config.Data())
            for((k, v) in reloadable) {
                if(k == "main") continue
                v.reload()
            }
            Config.Data()
        }
    }

    init {
        logger.on(EventType.PlayerChatEvent::class.java) { e ->
            discord.with("chat") {
                if(e.message.startsWith("/")) return@with
                val user = users[e.player.uuid()]!!
                Globals.run { it.restChannel.createMessage(Globals.discordMessage(user.data.idName(), e.message)).block() }
            }
        }

        logger.on(EventType.ServerLoadEvent::class.java) {
            reload()
        }
    }

    override fun init() {
        reloadable["bot"] = discord
        filter.init()
    }

    override fun registerClientCommands(handler: CommandHandler) {
        bulkRemove(handler, "help votekick vote")

        game.init(handler)

        game.reg(Help.Game(game))
        game.reg(Execute(driver))
        game.reg(SetRank(driver, users, ranks, discord))
        game.reg(Account(driver, users, discord, config, ranks))
        game.reg(Configure(reloadable))
        game.reg(Profile(driver, ranks, users))
        game.reg(Search(ranks))
        game.reg(Minimal.Reload(users))
        game.reg(Look(ranks, users))
        game.reg(RankInfo(ranks, users.quests))
        game.reg(Vote(voting))
        game.reg(verificationTest)
        game.reg(VoteKick(driver, users, ranks, voting, discord))
        game.reg(Spawn())
        game.reg(Maps(config, voting, driver))
        game.reg(MapManager(driver))
        game.reg(loadout)
        game.reg(Mute(driver, users))
        game.reg(buildcore)
        game.reg(boost)
    }

    override fun registerServerCommands(handler: CommandHandler) {
        bulkRemove(handler, "")

        terminal.init(handler)

        terminal.reg(Execute(driver))
        terminal.reg(SetRank(driver, users, ranks, discord))
        terminal.reg(Configure(reloadable))
        terminal.reg(Profile(driver, ranks, users))
        terminal.reg(Search(ranks))
        terminal.reg(RankInfo(ranks, users.quests))
        terminal.reg(MapManager(driver))
        terminal.reg(Maps(config, voting, driver))
        terminal.reg(boost)
    }

    private fun bulkRemove(handler: CommandHandler, toRemove: String) {
        for(s in toRemove.split(" ")) handler.removeCommand(s)
    }
}
