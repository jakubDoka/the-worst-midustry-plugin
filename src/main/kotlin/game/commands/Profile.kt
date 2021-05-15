package game.commands

class Profile: Command("profile") {
    override fun run(args: Array<String>): Enum<*> {
        val id = if(args[0] == "me" && kind == Kind.Game) {
            user!!.data.id
        } else {
            if (notNum(args[0], 0)) {
                return Generic.NotAInteger
            }
            num(args[0])
        }

        when(args[1]) {
            "personal" -> {

            }
        }

        return Generic.Success
    }

}