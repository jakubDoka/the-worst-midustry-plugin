package cfg

class Config(var data: Data = Data()) {
    class Data(
        var maturity: Long = 1000 * 60 * 60 * 5,
        var minBuildCost: Float = 60f,
    )
}