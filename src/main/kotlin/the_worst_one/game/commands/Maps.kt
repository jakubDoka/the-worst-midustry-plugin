package the_worst_one.game.commands

import arc.Events
import the_worst_one.cfg.Config
import the_worst_one.cfg.Globals
import the_worst_one.db.Driver
import the_worst_one.db.Ranks
import the_worst_one.game.Voting
import mindustry.Vars.*
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.maps.Map
import mindustry.net.WorldReloader
import mindustry_plugin_utils.Templates


class Maps(val config: Config, private val voting: Voting, val driver: Driver): Command("maps") {

    private val change = Voting.Session.Data(1, 7, "change", "maps", Ranks.Perm.Maps)
    private val restart = Voting.Session.Data(1, 7, "restart", "maps", Ranks.Perm.Maps)
    private val end = Voting.Session.Data(1, 7, "end", "maps", Ranks.Perm.Maps)

    override fun run(args: Array<String>): Enum<*> {
        if("change restart end".contains(args[0]) && kind != Kind.Game) {
            send("maps.notSupported")
            return Generic.NotSupported
        }

        when(args[0]) {
            "list" -> {
                val mapData = driver.maps.loadMapList()
                val maps = Array(mapData.size){
                    val d = mapData[it]
                    "[gray]${d.id} - [white]${d.name} - ${if(d.active) "[green]enabled" else "[gray]disabled"}"
                }
                val page = if(args.size < 2 || notNum(1, args)) 1 else num(args[1])

                alertPlain(Templates.page(
                    bundle.translate("maps.list.title"),
                    maps,
                    10,
                    page.toInt(),
                ))

                return Generic.Success
            }
            "change" -> {
                val mapData = driver.maps.loadMapDataContains(args[1])
                    ?: if(notNum(1, args)) return Generic.NotAInteger
                    else driver.maps.loadMapData(num(args[1])) ?: run {
                        send("maps.notFound")
                        return Generic.NotFound
                    }

                val map = if(Globals.testing) {
                    if(!Globals.mapSiActive(mapData.fileName)) {
                        send("maps.notActive")
                        return Result.NotActive
                    }
                    null
                } else maps.customMaps().find { it.name() == mapData.name } ?: run {
                    send("maps.notActive")
                    return Result.NotActive
                }

                voting.add(Voting.Session(change, user!!, mapData.name) {
                    changeMap(map!!) // whe testing the call will not happen
                })

                return Generic.Vote
            }
            "restart" -> {
                voting.add(Voting.Session(restart, user!!){
                    changeMap(state.map)
                })
                return Generic.Vote
            }
            "end" -> {
                voting.add(Voting.Session(end, user!!){
                    Events.fire(EventType.GameOverEvent(Team.crux))
                })
                return Generic.Vote
            }
            else -> {
                send("wrongOption", "change restart end list")
                return Generic.Mismatch
            }
        }
    }

    private fun changeMap(map: Map) {
        val reload = WorldReloader()

        reload.begin()

        world.loadMap(map, map.applyRules(config.data.gamemode))

        state.rules = state.map.applyRules(config.data.gamemode)
        logic.play()

        reload.end()
    }

    enum class Result {
        NotActive
    }
}