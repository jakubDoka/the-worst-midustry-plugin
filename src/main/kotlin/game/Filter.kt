package game

import arc.math.Mathf
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


// handles actions and message filtering
class Filter(val users: Users, val ranks: Ranks, val logger: Logger) {
    var map = LockMap()

    fun init() {
        logger.on(EventType.PlayEvent::class.java) { map.resize(Vars.world.width(), Vars.world.height()) }

        logger.on(EventType.BlockDestroyEvent::class.java) { map.unlock(it.tile) }

        logger.on(EventType.BlockBuildEndEvent::class.java) { map.unlock(it.tile) }

        logger.run(EventType.Trigger.update) {
            users.forEach { _, u ->
                if(u.idSpectator() && u.inner.shooting) {
                    u.inner.unit().kill()
                }
            }
        }

        Vars.netServer.admins.addActionFilter {
            val user = users[it.player.uuid()]!!

            if(user.data.rank == ranks.griefer) {
                user.send("action.griefer")
                return@addActionFilter false
            }

            if(user.data.rank == Ranks.paralyzed) {
                user.send("action.paralyzed")
                return@addActionFilter false
            }

            if(!map.canInteract(it.tile, user)) {
                user.send("action.interact")
                return@addActionFilter false
            }

            when(it.type) {
                ActionType.breakBlock -> map.unlock(it.tile)
                ActionType.placeBlock -> map.update(it.tile, user)
                else -> {}
            }

            return@addActionFilter true
        }

        Vars.netServer.admins.addChatFilter { p, s ->

            return@addChatFilter s
        }
    }

    class LockMap {
        var tiles: Array<Array<LockTile>> = arrayOf()

        fun resize(w: Int, h: Int) {
            tiles = Array(h) { Array(w) { LockTile() } }
        }

        fun canInteract(tile: Tile, user: User): Boolean {
            return tiles[tile.y.toInt()][tile.x.toInt()].lock >= user.data.rank.control.value
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