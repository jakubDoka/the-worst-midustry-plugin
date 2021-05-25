package db

import org.junit.jupiter.api.Test

class OutlookTest {
    val outlook = Lookout()

    @Test
    fun get() {
        println(outlook.localize("169.172.45.3", "").locale)
    }
}