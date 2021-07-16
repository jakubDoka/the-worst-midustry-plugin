package game.commands

import cfg.Globals
import cfg.Globals.time
import cfg.Reloadable
import com.beust.klaxon.Klaxon
import db.Driver
import db.Ranks
import game.Docks
import game.Users
import game.Voting
import mindustry.content.Blocks
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.world.Tile
import mindustry_plugin_utils.Fs
import java.io.File

class BuildCore(val driver: Driver, val docks: Docks, val voting: Voting, val banned: MutableMap<Tile, String>, override val configPath: String) : Command("buildcore"), Reloadable, Globals.Log {
    override val prefix = "buildcore"
    var config = Config()
    val build = Voting.Session.Data(1, 5, "build", "buildcore", Ranks.Perm.BuildCore)

    init {
        reload()
    }

    override fun run(args: Array<String>): Enum<*> {
        val user = user!!

        val missing = driver.items.findMissing(config.costs)

        if(!docks.canShip()) {
            send("docks.noFreeShip")
            return Generic.NoFreeShips
        }

        if(missing.isNotEmpty()) {
            val sb = StringBuilder()
            for((k, v) in missing) {
                sb
                    .append(Globals.itemIcons[k]!!)
                    .append(v)
                    .append(" ")
            }
            send("buildcore.missing", sb)
            return Result.MissingItems
        }

        var tile = user.inner.tileOn()
        if(tile.build?.block != Blocks.vault || tile.build.team != Team.sharded) {
            send("buildcore.missingVault")
            return Result.MissingVault
        }
        tile = tile.build.tile

        voting.add(Voting.Session(build, user, "${tile.x}:${tile.y}") {
            banned[tile] = "buildcore.reservedTile"
            docks.launch(CoreShip(tile, banned, config.shipTravelTime))
            driver.items.take(config.costs)
        })

        return Generic.Success
    }

    override fun reload() {
        try {
            config = Klaxon().parse<Config>(File(configPath))!!
        } catch (e: Exception) {
            e.printStackTrace()
            Globals.loadFailMessage("buildcore", e)
            Fs.createDefault(configPath, config)
        }
    }

    class CoreShip(val tile: Tile, val tiles: MutableMap<Tile, String>, travelTime: Long): Docks.Ship(travelTime) {
        override fun execute(users: Users) {
            tiles.remove(tile)
            if(tile.build?.block != Blocks.vault || tile.build.team != Team.sharded) {
                users.send("buildcore.fail")
                return
            }

            tile.remove()
            Call.constructFinish(tile, Blocks.coreShard, null, 0, Team.sharded, null)
            users.send("buildcore.success")
        }

        override fun display(sb: StringBuilder) {
            sb
                .append(Globals.coreIcon)
                .append(" >")
                .append(timer.times(1000).time())
                .append("> ")
                .append(tile.x)
                .append(":")
                .append(tile.y)
        }
    }

    class Config(
        val costs: Map<String, Long> = mapOf(
            "copper" to 1L
        ),
        val shipTravelTime: Long = 60 * 3,
    )

    enum class Result {
        MissingVault, MissingItems
    }
}