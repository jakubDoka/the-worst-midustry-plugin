package game.User

import db.Driver
import db.Ranks
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry_plugin_utils.Templates

class User(val inner: Player, val data: Driver.RawUser) {
    val paralyzed: Boolean get() = -1L == data.id

    fun alert(title: String, key: String, vararg arguments: Any, color: String = "orange") {
        Call.infoMessage(inner.con, Templates.info(title, translate(key, arguments), color))
    }

    fun translate(key: String, vararg arguments: Any): String {
        return String.format(data.bundle.get(key), arguments)
    }

    fun idSpectator(): Boolean {
        return data.rank.control == Ranks.Control.None
    }
}