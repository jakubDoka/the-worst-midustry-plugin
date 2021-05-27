package game

import arc.math.Mathf
import cfg.Globals
import db.Ranks
import game.u.User
import mindustry.Vars
import mindustry.game.EventType
import mindustry.net.Administration
import mindustry.world.Tile
import java.lang.Integer.max
import java.lang.Integer.min
import mindustry.net.Administration.ActionType;
import mindustry_plugin_utils.Logger
import mindustry_plugin_utils.Templates
import mindustry.gen.Call


// handles actions and message filtering
class Filter(val users: Users, val ranks: Ranks, val logger: Logger) {
    var map = LockMap()

    fun init() {
        logger.on(EventType.PlayEvent::class.java) { map.resize(Vars.world.width(), Vars.world.height()) }

        logger.on(EventType.BlockDestroyEvent::class.java) { map.unlock(it.tile) }

        logger.on(EventType.BlockBuildEndEvent::class.java) { map.unlock(it.tile) }

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

            return@addActionFilter true
        }

        Vars.netServer.admins.addChatFilter { p, message ->
            var s = message
            val u = users[p.uuid()]!!

            if(!u.data.hasPerm(Ranks.Perm.Scream)) {
                s = s.toLowerCase()
            }

            Call.sendMessage(Globals.message(u.data.idName(), u.data.colorMessage(s)))

            return@addChatFilter null
        }
    }

    class LockMap {
        var tiles: Array<Array<LockTile>> = arrayOf()

        fun resize(w: Int, h: Int) {
            tiles = Array(h) { Array(w) { LockTile() } }
        }

        fun canInteract(tile: Tile, user: User): Boolean {
            return tiles[tile.y.toInt()][tile.x.toInt()].lock <= user.data.rank.control.value
        }

        fun update(tile: Tile, user: User) {
            val t = tiles[tile.y.toInt()][tile.x.toInt()]
            t.lock = min(max(user.data.rank.control.value, t.lock), Ranks.Control.Normal.value)
        }

        fun unlock(tile: Tile) {
            tiles[tile.y.toInt()][tile.x.toInt()].lock = Ranks.Control.Minimal.value
        }
    }

    class LockTile {
        var lock: Int = Ranks.Control.Minimal.value
    }
}