package game

import cfg.Globals
import cfg.Reloadable
import com.beust.klaxon.JsonObject
import com.beust.klaxon.Klaxon
import db.Driver
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.type.Item
import mindustry_plugin_utils.Fs
import mindustry_plugin_utils.Logger
import mindustry_plugin_utils.Messenger
import java.io.File
import java.lang.Long.min
import java.lang.StringBuilder

class Docks(override val configPath: String, val users: Users, logger: Logger) : Displayable, Reloadable {
    private val ships = mutableListOf<Ship>()
    var config = Config()

    val messenger = Messenger("docks")

    init {
        logger.on(EventType.GameOverEvent::class.java) {
            users.send("docks.bail")
            val iter = ships.iterator()
            while(iter.hasNext()) if(iter.next().bail) iter.remove()
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
        ship.initialize(this)
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
                if(current.execute(users)) newShipsCount++
            }
        }

        for(i in 0 until newShipsCount) {
            launch(ReturningShip())
        }
    }

    override fun display(user: Driver.RawUser): String {
        val sb = StringBuilder()
        for(s in ships) {
            s.display(sb)
        }
        return sb.toString()
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

    fun transport(theAmount: Long, distributor: (Int)->Ship) {
        var amount = theAmount
        while(amount != 0L && canShip()) {
            val piece = min(config.shipCapacity.toLong(), amount)
            amount -= piece
            launch(distributor(piece.toInt()))
        }
    }

    interface Ship {
        val bail: Boolean
        var timer: Long
        fun initialize(docks: Docks)
        fun execute(users: Users): Boolean
        fun display(sb: StringBuilder)
    }

    class LoadoutShip(val item: Item, val amount: Int): Ship {
        override var timer: Long = 0
        override val bail: Boolean = true
        override fun initialize(docks: Docks) {
            timer = docks.config.travelTime
        }

        override fun execute(users: Users): Boolean {
            val core = Vars.state.teams.get(Team.sharded).core()
            val icon = Globals.itemIcons[item.name]!!
            if (core == null) {
                users.send("docks.shipLost", amount, icon)
            } else {
                users.send("docks.shipArrived", amount, icon)
                core.items.add(item, amount)
            }

            return true
        }

        override fun display(sb: StringBuilder) {
            val icon = Globals.itemIcons[item.name]!!
            sb
                .append(icon)
                .append(amount)
                .append(" >")
                .append(formatShipTimer(timer))
                .append("> ")
                .append(Globals.coreIcon)
                .append(" ")
        }
    }

    class ReturningShip : Ship {
        override val bail: Boolean = false
        override var timer: Long = 0
        override fun execute(users: Users): Boolean { return false }
        override fun initialize(docks: Docks) {
            timer = docks.config.travelTime
        }

        override fun display(sb: StringBuilder) {
            sb.append("returning ").append(formatShipTimer(timer))
        }
    }

    class BuildingShip() : Ship {
        override var timer: Long = 0
        override val bail: Boolean = false
        override fun execute(users: Users): Boolean { return false }
        override fun initialize(docks: Docks) {
            timer = docks.config.rebuildTime
        }

        override fun display(sb: StringBuilder) {
            sb.append("building new ship ").append(formatShipTimer(timer))
        }

    }

    companion object {
        fun formatShipTimer(l: Long): String {
            return "${l / 60}:${l % 60}"
        }
    }

    class Config(
        val travelTime: Long = 60 * 3,
        val shipCapacity: Int = 5000,
        val shipCount: Int = 3,
        val rebuildTime: Long = 60 * 60,
    )


}