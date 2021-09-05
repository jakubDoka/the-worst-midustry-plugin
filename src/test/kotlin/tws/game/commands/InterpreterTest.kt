package the_worst_one.game.commands

import the_worst_one.Main
import org.junit.jupiter.api.Test
import the_worst_one.game.Interpreter
import mindustry.mod.Mod

class InterpreterTest {
    @Test
    fun tokenize() {
        assert(Interpreter.run("a > 3 or a <= 10 or \"something\" != b", mapOf("a" to 10L, "b" to "hell")) as Boolean)
        assert(Interpreter.run("10 > 3") as Boolean)
        assert(Interpreter.run("10 >= 10") as Boolean)
        assert(Interpreter.run("10 == 10") as Boolean)
        assert(Interpreter.run("10 != 3") as Boolean)
        assert(Interpreter.run("10 <= 10") as Boolean)
        assert(Interpreter.run("3 < 10") as Boolean)
        assert(Interpreter.run("10 > 3 and 10 >= 10 and 10 == 10 and 10 != 3 and 10 <= 10 and 3 < 10") as Boolean)
        assert(!(Interpreter.run("true or true and false") as Boolean))
        assert(Interpreter.run("true or ( true and false )") as Boolean)
    }
    
    @Test
    fun test() {
        assert(Main::class.java.getDeclaredConstructor().newInstance() is Mod)
    }
}