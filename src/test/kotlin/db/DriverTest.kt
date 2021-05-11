package db

import arc.net.Connection
import mindustry.gen.Player
import mindustry.net.Net
import mindustry.net.NetConnection
import org.junit.jupiter.api.Test

class DriverTest() {
    private val driver = Driver("config/driver/config.json", Ranks(),true)

    @Test
    fun init() {
        val player = Player.create()

        player.con = object: NetConnection("127.0.0.1") {
            override fun send(p0: Any?, p1: Net.SendMode?) {
                TODO("Not yet implemented")
            }

            override fun close() {
                TODO("Not yet implemented")
            }
        }

        driver.newUser(player)

        assert(driver.searchUsers(player).size == 1)
    }
}