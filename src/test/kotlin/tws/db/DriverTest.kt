package the_worst_one.db

import the_worst_one.cfg.Config
import the_worst_one.game.Users
import mindustry_plugin_utils.Logger
import org.junit.jupiter.api.Test

class DriverTest() {
    private val ranks = Ranks()
    private val driver = Driver("config/driver/config.json", ranks)
    private val users = Users(driver, Logger("/"), ranks, Config())

    @Test
    fun init() {
        val u = users.test("ip", "name")

        assert(driver.users.search(u.inner).size == 1)
    }
}