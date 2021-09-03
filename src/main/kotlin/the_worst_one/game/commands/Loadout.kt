package the_worst_one.game.commands

import the_worst_one.cfg.Globals
import the_worst_one.cfg.Globals.time
import the_worst_one.cfg.Reloadable
import com.beust.klaxon.Klaxon
import the_worst_one.db.Driver
import the_worst_one.db.Ranks
import the_worst_one.game.Docks
import the_worst_one.game.Interpreter
import the_worst_one.game.Users
import the_worst_one.game.Voting
import mindustry.Vars
import mindustry.game.Team
import mindustry.type.Item
import mindustry_plugin_utils.Fs
import java.io.File
import java.lang.Exception
import java.lang.Long.min

class Loadout(val driver: Driver, val docks: Docks, val voting: Voting, override var configPath: String): Command("loadout"), Reloadable, Globals.Log {
    override val prefix = "loadout"
    val store = Voting.Session.Data(1, 5, "store", "loadout", Ranks.Perm.Store)
    val load = Voting.Session.Data(1, 5, "load", "loadout", Ranks.Perm.Load)
    var config = Config()

    override fun run(args: Array<String>): Enum<*> {
        if(args.size == 3) {
            if(notNum(1, args)) {
                return Generic.NotAInteger
            }

            var amount = num(args[1])

            if(amount == 0L) {
                send("loadout.redundant")
                return Result.Redundant
            }

            when(args[0]) {
                "load" -> {
                    val item = Globals.item(args[2])
                    if (item == null) {
                        send("loadout.invalidItem", Globals.listItems())
                        return Result.InvalidItem
                    }

                    if(!docks.canShip()) {
                        send("docks.noFreeShips")
                        return Generic.NoFreeShips
                    }

                    val icon = Globals.itemIcons[item.name]!!
                    amount = min(amount, docks.transportable().toLong())
                    voting.add(Voting.Session(load, user!!, "$icon$amount") {
                        docks.transport(amount, config.shipCapacity) {
                            driver.items.dec(item, it.toLong())
                            LoadoutShip(item, it, config.shipTravelTime)
                        }
                    })
                }
                "store" -> {
                    val code =
                        if(args[2].startsWith("where ")) {
                            args[2].substring("where ".length)
                        } else {
                            if(Globals.item(args[2]) == null) {
                                send("loadout.invalidItem", Globals.listItems())
                                send("loadout.suggestWhere")
                                return Result.InvalidItem
                            }
                            "itemName == \"${args[2]}\""
                        }

                    try {
                        Interpreter.run(code, mapOf("itemName" to "", "itemAmount" to 0L)) // syntax check
                        voting.add(Voting.Session(store, user!!, args[1], args[2]) {
                            driver.items.launch(code, amount)
                        })
                    } catch (e: Exception) {
                        send("loadout.store.error", e.message ?: "congrats, even error cannot be displayed")
                        return Result.Error
                    }
                }
                else -> {
                    send("wrongOption", "load store status")
                    return Generic.Mismatch
                }
            }
        } else if (args[0] == "status") {
            alert("loadout.title", "placeholder", driver.items.format())
        } else {
            send("wrongOption", "load store status")
            return Generic.Mismatch
        }

        return Generic.Success
    }

    override fun reload() {
        try {
            config = Klaxon().parse<Config>(File(configPath))!!
        } catch (e: Exception) {
            e.printStackTrace()
            Globals.loadFailMessage("loadout", e)
            Fs.createDefault(configPath, config)
        }
    }

    class Config(
        val shipTravelTime: Long = 60 * 3,
        val shipCapacity: Long = 5000,
    )

    class LoadoutShip(val item: Item, val amount: Int, travelTime: Long): Docks.Ship(travelTime) {
        override fun execute(users: Users) {
            val core = Vars.state.teams.get(Team.sharded).core()
            val icon = Globals.itemIcons[item.name]!!
            if (core == null) {
                users.send("docks.shipLost", amount, icon)
            } else {
                users.send("docks.shipArrived", amount, icon)
                core.items.add(item, amount)
            }
        }

        override fun display(sb: StringBuilder) {
            val icon = Globals.itemIcons[item.name]!!
            sb
                .append(icon)
                .append(amount)
                .append(" >")
                .append(timer.times(1000).time())
                .append("> ")
                .append(Globals.coreIcon)
                .append(" ")
        }
    }

    enum class Result {
        InvalidItem, Redundant, Error
    }


}