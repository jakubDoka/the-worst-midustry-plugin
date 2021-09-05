package the_worst_one.game

import arc.Core
import the_worst_one.cfg.Config
import com.beust.klaxon.Klaxon
import the_worst_one.game.u.User
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import the_worst_one.cfg.Globals
import java.net.URL

class VPN(val config: Config, users: Users) {
    val input = Channel<User?>()

    init {
            Globals.runLoggedGlobalScope {
                while (true) {
                    val inp = input.receive() ?: break
                    if(!inp.data.banned() && authorize(inp.inner.con.address)) {
                        inp.data.ban()
                        Core.app.post {
                            users.reload(inp)
                            inp.send("vpn.detected")
                        }
                    }
                }
            }
    }

    fun authorize(ip: String): Boolean {
        return try {
            val text = URL("https://vpnapi.io/api/$ip?key=${config.data.vpnApyKey}").readText()
            val resp = Klaxon().parse<Response>(text)!!

            resp.security.vpn || resp.security.proxy || resp.security.tor
        } catch (e: Exception) {
            false
        }
    }

    class Response(val security: Security) {
        class Security(
            val vpn: Boolean,
            val proxy: Boolean,
            val tor: Boolean
        )
    }
}