package the_worst_one.db

import com.beust.klaxon.Klaxon
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jetbrains.exposed.sql.update
import java.net.URL
import the_worst_one.db.Driver.Users
import kotlinx.coroutines.GlobalScope
import org.jetbrains.exposed.sql.transactions.transaction
import the_worst_one.cfg.Globals

// Outlook handles user localization
class Lookout() {
    val input = Channel<Request?>()

    // Spawns an outlook worker
    init {

            Globals.runLoggedGlobalScope {
                while (true) {
                    val inp = input.receive() ?: break
                    val data = localize(inp.ip, inp.locale)
                    transaction {
                        Users.update({ Users.id eq inp.id }) {
                            it[country] = data.country
                            it[locale] = data.locale
                        }
                    }
                }
            }
    }

    // localize tries to get locale by ip address
    fun localize(ip: String, default: String): Data {
        return try {
            val text = URL("http://ipapi.co/$ip/json").readText()
            val resp = Klaxon().parse<Response>(text)!!

            val parts = resp.languages.split(",")
            val locale =
                if(parts.isNotEmpty()) parts[0].replace('-', '_')
                else resp.languages

            Data(resp.country_name, locale)
        } catch (e: Exception) {
            Data(Users.defaultCountry, default)
        }
    }

    class Data(val country: String, val locale: String)
    class Response(val country_name: String = Users.defaultCountry, val languages: String = Users.defaultLocale)
    class Request(val ip: String, val id: Long, val locale: String)
}