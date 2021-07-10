package game

import cfg.Globals
import cfg.Globals.time
import cfg.Reloadable
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import db.Driver
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.type.Item
import mindustry.world.Tile
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Logger
import mindustry_plugin_utils.Messenger
import java.io.File
import java.lang.Long.min
import java.lang.StringBuilder
import kotlin.math.max

class Docks(val users: Users, logger: Logger, override val configPath: String) : Displayable, Reloadable {
    private val ships = mutableListOf<Ship>()
    var config = Config()

    val messenger = Messenger("docks")

    init {
        logger.on(EventType.GameOverEvent::class.java) {
            val prev = ships.size
            val iter = ships.iterator()
            while(iter.hasNext()) if(!iter.next().invincible) iter.remove()
            for(i in 0 until prev - ships.size) launch(BuildingShip(config.rebuildTime))
            if(prev - ships.size != 0) users.send("docks.destroyed")
        }
        reload()
    }

    fun canShip(): Boolean {
        return ships.size < config.shipCount
    }

    fun transportable(): Int {
        return (config.shipCount - ships.size) * config.shipCapacity
    }

    fun launch(ship: Ship) {
        if(Globals.testing) {
            ship.execute(users)
            val sb = StringBuilder()
            ship.display(sb)
            println(sb.toString())
        } else {
            ships.add(ship)
        }
    }

    override fun tick() {
        var newShipsCount = 0
        val it = ships.iterator()
        while (it.hasNext()) {
            val current = it.next()
            current.timer -= 1
            if (current.timer <= 0L) {
                it.remove()
                current.execute(users)
                if(!current.invincible) newShipsCount++
            }
        }

        for(i in 0 until newShipsCount) {
            launch(ReturningShip(config.returnTime))
        }
    }

    override fun display(user: Driver.RawUser): String {
        val sb = StringBuilder()
        for(s in ships) {
            s.display(sb)
            sb.append(Globals.hudDelimiter)
        }
        return sb.substring(0, max(0, sb.length - Globals.hudDelimiter.length))
    }

    override fun reload() {
        try {
            config = Klaxon().parse<Config>(File(configPath))!!
        } catch (e: Exception) {
            e.printStackTrace()
            messenger.log("cannot load the ship config: ${e.message}")

            Fs.createDefault(configPath, Config())
        }
    }

    fun transport(theAmount: Long, shipCapacity: Long, distributor: (Int)->Ship) {
        var amount = theAmount
        while(amount != 0L && canShip()) {
            val piece = min(shipCapacity, amount)
            amount -= piece
            launch(distributor(piece.toInt()))
        }
    }

    class ReturningShip(travelTime: Long): Ship(travelTime, true) {
        override fun display(sb: StringBuilder) {
            sb.append("returning ").append(timer.time())
        }
    }

    class BuildingShip(travelTime: Long): Ship(travelTime, true) {
        override fun display(sb: StringBuilder) {
            sb.append("building new ship ").append(timer.time())
        }
    }

    abstract class Ship(val travelTime: Long, val invincible: Boolean = false, ) {
        var timer = travelTime

        open fun execute(users: Users) {}
        
        abstract fun display(sb: StringBuilder)
    }

    class Config(
        val returnTime: Long = 60 * 3,
        val shipCapacity: Int = 5000,
        val shipCount: Int = 3,
        val rebuildTime: Long = 60 * 60,
    )
}