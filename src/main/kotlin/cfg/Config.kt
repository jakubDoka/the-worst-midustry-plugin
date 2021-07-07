package cfg

import mindustry.game.Gamemode

class Config(var data: Data = Data()) {
    class Data(
        val inspectHistorySize: Long = 5,
        var maturity: Long = 1000 * 60 * 60 * 5,
        var minBuildCost: Float = 60f,
        var testPenalty: Long = 1000 * 60 * 15,
        var disabledGameCommands: Set<String> = setOf(),
        var vpnApyKey: String = "",
        var gamemode: Gamemode = Gamemode.survival,
        val doubleTapSensitivity: Long = 300,
    )
}