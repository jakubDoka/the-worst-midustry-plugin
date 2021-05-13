package util

object Txt {

}

fun Long.time(): String {
    val sec = this / 1000
    val min = sec / 60
    val hour = min / 60
    val days = hour / 24
    return String.format("%d:%02d:%02d:%02d", days % 365, hour % 24, min % 60, sec % 60)
}