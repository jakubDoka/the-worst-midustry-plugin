package the_worst_one.game.commands

import the_worst_one.game.Users

class Online(val users: Users) : Command("online") {
    override fun run(args: Array<String>): Enum<*> {
        val sb = StringBuilder()
        for ((_, v) in users) {
            sb
                .append(v.data.name)
                .append(" - ")
                .append(v.data.id)
                .append(" - ")
                .append(v.data.display.name)
                .append("\n")
        }

        alert("online.title", "placeholder", sb.toString())

        return Generic.Success
    }
}