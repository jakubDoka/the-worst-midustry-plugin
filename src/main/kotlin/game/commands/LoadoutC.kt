package game.commands

import cfg.Globals
import db.Ranks
import game.Docks
import game.Interpreter
import game.Loadout
import game.Voting
import java.lang.Exception
import java.lang.Long.min

class LoadoutC(val loadout: Loadout, val docks: Docks, val voting: Voting): Command("loadout") {
    val store = Voting.Session.Data(1, 5, "store", "loadout", Ranks.Perm.Store)
    val load = Voting.Session.Data(1, 5, "load", "loadout", Ranks.Perm.Load)

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
                    val item = Globals.itemList().find { it.name == args[2] }
                    if (item == null) {
                        send("loadout.invalidItem", Globals.listItems())
                        return Result.InvalidItem
                    }

                    if(!docks.canShip()) {
                        send("loadout.load.noFreeShips")
                        return Result.NoFreeShips
                    }

                    val icon = Globals.itemIcons[item.name]!!
                    amount = min(amount, docks.transportable().toLong())
                    voting.add(Voting.Session(load, user!!, "$icon$amount") {
                        docks.transport(amount) {
                            loadout.items.dec(item, it.toLong())
                            Docks.LoadoutShip(item, it)
                        }
                        loadout.save()
                    })
                }
                "store" -> {
                    val code =
                        if(args[2].startsWith("where ")) {
                            args[2].substring("where ".length)
                        } else {
                            if(Globals.itemList().find { it.name == args[2] } == null) {
                                return Result.InvalidItem
                            }
                            "itemName == \"${args[2]}\""
                        }

                    try {
                        Interpreter.run(code, mapOf("itemName" to "", "itemAmount" to 0L)) // syntax check
                        voting.add(Voting.Session(store, user!!, args[1], args[2]) {
                            loadout.launch(code, amount)
                        })
                    } catch (e: Exception) {
                        send("loadout.store.error", e.message ?: "congrats, even error cannot be displayed")
                        return Result.Error
                    }
                }
                else -> {
                    send("wrongOption", "load store state")
                    return Generic.Mismatch
                }
            }
        } else if (args[0] == "status") {
            alert("loadout.title", "placeholder", loadout.items.format())
        } else {
            send("wrongOption", "load store state")
            return Generic.Mismatch
        }

        return Generic.Success
    }

    enum class Result {
        InvalidItem, Redundant, NoFreeShips, Error
    }
}