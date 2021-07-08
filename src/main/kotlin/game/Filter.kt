package game

import arc.util.Time
import cfg.Config
import cfg.Globals
import db.Driver
import db.Ranks
import game.u.User
import mindustry.Vars
import mindustry.game.EventType
import mindustry.world.Tile
import java.lang.Integer.max
import java.lang.Integer.min
import mindustry.net.Administration.ActionType;
import mindustry_plugin_utils.Logger
import mindustry.gen.Call
import java.lang.StringBuilder


// handles actions and message filtering
class Filter(val users: Users, val ranks: Ranks, val logger: Logger, val config: Config) {
    var map = LockMap()
    val inspect = Inspect(map, config, users, logger)

    fun init() {
        logger.on(EventType.PlayEvent::class.java) {
            map.resize(Vars.world.width(), Vars.world.height())
            inspect.doubleTaps.clear()
        }

        logger.on(EventType.BlockDestroyEvent::class.java) { map.unlock(it.tile) }

        logger.on(EventType.BlockBuildEndEvent::class.java) { if(it.breaking) map.unlock(it.tile) }

        logger.run(EventType.Trigger.update) {
            users.forEach { _, u ->
                if(u.data.rank.control.spectator() && u.inner.shooting) {
                    u.inner.unit().kill()
                }
            }
        }

        Vars.netServer.admins.addActionFilter {
            if(it.player == null) return@addActionFilter true

            val user = users[it.player.uuid()]!!

            if(user.data.rank == ranks.griefer) {
                user.send("action.griefer")
                return@addActionFilter false
            }

            if(user.data.rank == ranks.paralyzed) {
                user.send("action.paralyzed")
                return@addActionFilter false
            }

            if(it.tile == null) return@addActionFilter true

            if(!map.canInteract(it.tile, user)) {
                user.send("action.interact", user.data.rank.control, Ranks.Control.Normal)
                return@addActionFilter false
            }

            when(it.type) {
                ActionType.breakBlock -> map.unlock(it.tile)
                ActionType.placeBlock -> map.update(it.tile, user)
                else -> {}
            }

            inspect.denote(it.tile, user.data, it.type)

            return@addActionFilter true
        }

        Vars.netServer.admins.addChatFilter { p, message ->
            var s = message
            val u = users[p.uuid()]!!

            if(!u.data.hasPerm(Ranks.Perm.Scream)) {
                s = s.toLowerCase()
            }

            if(!u.data.mutedForAll) users.sendUserMessage(u, message)
            else u.send("mute.mutedForAll")

            return@addChatFilter null
        }
    }

    class LockMap {
        var tiles: Array<Array<LockTile>> = arrayOf()

        fun resize(w: Int, h: Int) {
            tiles = Array(h) { Array(w) { LockTile() } }
        }

        fun canInteract(tile: Tile, user: User): Boolean {
            return tile(tile).lock <= user.data.rank.control.value
        }

        fun update(tile: Tile, user: User) {
            val t = tile(tile)
            t.lock = min(max(user.data.rank.control.value, t.lock), Ranks.Control.Normal.value)
        }

        fun unlock(tile: Tile) {
            tile(tile).lock = Ranks.Control.Minimal.value
        }

        fun tile(tile: Tile): LockTile {
            return tiles[tile.y.toInt()][tile.x.toInt()]
        }
    }

    class LockTile {
        var lock: Int = Ranks.Control.Minimal.value
        val inspectData = mutableListOf<Inspect.Data>()
    }

    class Inspect(val map: LockMap, val config: Config, users: Users, logger: Logger) {
        val doubleTaps = mutableMapOf<Long, TapData>()
        init {
            logger.on(EventType.TapEvent::class.java) {
                val user = users[it.player.uuid()]!!
                val last = doubleTaps[user.data.id]
                if (last == null || (last.tile.x != it.tile.x && last.tile.y != it.tile.y)
                    || Time.millis() - last.timestamp > config.data.doubleTapSensitivity) {
                    doubleTaps[user.data.id] = TapData(it.tile)
                    return@on
                }
                Call.label(it.player.con, summarize(it.tile), 5f, it.tile.worldx(), it.tile.worldy())
            }
        }

        fun denote(tile: Tile, user: Driver.RawUser, action: ActionType) {
            val data = map.tile(tile).inspectData
            if(data.isNotEmpty() && data.last().name == user.idName() && data.last().action == action) return
            data.add(Data(user.idName(), user.rank.postfix, action))
            if(data.size > config.data.inspectHistorySize)
                data.removeAt(0)
        }

        fun summarize(tile: Tile): String {
            val t = map.tile(tile)
            val sb = StringBuilder()

            sb.append("lock: ").append(t.lock).append("\n")

            for(e in t.inspectData)
                sb
                    .append(e.name)
                    .append(" - ")
                    .append(e.rank)
                    .append(" - ")
                    .append(e.action)
                    .append("\n")

            return sb.substring(0, sb.length-1)
        }

        class Data(val name: String, val rank: String, val action: ActionType)
        class TapData(val tile: Tile, val timestamp: Long = Time.millis())
    }
}