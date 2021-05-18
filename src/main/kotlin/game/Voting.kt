package game

class Voting {
    abstract class Session(val key: String, vararg val args: String) {
        abstract fun run()


    }
}