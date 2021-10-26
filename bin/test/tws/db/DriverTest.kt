package the_worst_one.db

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import the_worst_one.cfg.Config
import the_worst_one.game.Users
import mindustry_plugin_utils.Logger
import org.junit.jupiter.api.Test
import the_worst_one.cfg.Globals

class DriverTest() {

    @Test
    fun init() {
        runBlocking {
            Globals.runLoggedGlobalScope {
                throw Exception()
            }

            delay(10000)
        }
    }
}