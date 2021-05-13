package db

import game.Users
import mindustry_plugin_utils.Logger
import org.junit.jupiter.api.Test

class DriverTest() {
    private val ranks = Ranks()
    private val driver = Driver("config/driver/config.json", ranks,true)
    private val users = Users(driver, Logger("/"), ranks)

    @Test
    fun init() {
        val u = users.test("ip", "name")

        assert(driver.users.search(u.inner).size == 1)
    }
}