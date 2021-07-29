package the_worst_one.game

import java.lang.RuntimeException
import java.util.regex.Pattern

object Interpreter {
    val matchSpace = Pattern.compile("\\s+")
    val matchString = Pattern.compile("\\\".*\\\"")
    val matchInt = Pattern.compile("\\d+")
    val matchFloat = Pattern.compile("\\d+\\.\\d+")

    fun run(code: String, constants: Map<String, Any> = mapOf()): Any {
        val tokens = toTokens(code, constants)

        return evaluate(tokens).value
    }

    fun evaluate(tokens: MutableList<Token>): Token {
        val l = tokens.pop() ?: throw RuntimeException("expected token")

        if(l.value is Par) throw RuntimeException("unexpected ')'")

        val op = tokens.pop() ?: return l
        val right = tokens.lookup(0)?.value ?: throw RuntimeException("expected operand, found nothing")

        var nextOp = tokens.lookup(1)

        val r =
            if(right == Par.Left) {
                tokens.pop()!!
                val res = evaluate(tokens)
                nextOp = tokens.lookup(0)
                res
            } else if(nextOp != null && nextOp.value != Par.Right && nextOp.precedence() > op.precedence()) {
                evaluate(tokens)
            } else {
                val r = tokens.pop()!!
                if(nextOp != null && nextOp.value == Par.Right) {
                    tokens.pop()!!
                }
                r
            }

        val result = doOperation(op, r, l)
        if (nextOp != null && nextOp.value != Par.Right) {
            tokens.pop()!!
            return doOperation(nextOp, result, evaluate(tokens))
        }
        return result
    }

    fun doOperation(op: Token, l: Token, r: Token): Token {
        if (l.value::class != r.value::class)
            throw Error(op,"type mismatch (${l.value::class.simpleName} != ${r.value::class.simpleName})")

        return if(op.value == Cmp.Eq) {
            Token(l.idx, r.value == l.value)
        } else if(op.value == Cmp.Neq) {
            Token(l.idx, r.value != l.value)
        } else if(op.value is Op) {
            if(l.value !is Boolean || r.value !is Boolean) throw Error(op, "operation supported only for booleans")
            Token(l.idx, when(op.value) {
                Op.And -> r.value && l.value
                Op.Or -> r.value || l.value
            })
        } else if(l.value is String) {
            throw Error(op, "unsupported operator for string")
        } else {
            Token(l.idx, when(l.value) {
                is Double -> compareNumbers<Double>(op, l.value, r.value)
                is Long -> compareNumbers<Double>(op, l.value, r.value)
                else -> throw Error(l, "unknown type")
            })
        }
    }

    fun <T: Comparable<T>> compareNumbers(op: Token, fa: Any, fb: Any): Boolean {
        @Suppress("UNCHECKED_CAST") val a = fa as T
        @Suppress("UNCHECKED_CAST") val b = fb as T
        val cmp = a.compareTo(b)
        return when(op.value) {
            Cmp.Gr -> cmp < 0
            Cmp.Lo -> cmp > 0
            Cmp.GrEq -> cmp <= 0
            Cmp.LoEq -> cmp >= 0
            else -> throw Error(op, "unsupported operand for numeric type")
        }
    }

    fun toTokens(code: String, constants: Map<String, Any>): MutableList<Token> {
        val parts = matchSpace.split(code)
        val tokens = mutableListOf<Token>()
        var depth = 0
        for ((i, p) in parts.withIndex()) {
            tokens.add(Token(i, when(p) {
                "(" -> {
                    depth++
                    Par.Left
                }
                ")" -> {
                    depth--
                    Par.Right
                }

                ">" -> Cmp.Gr
                ">=" -> Cmp.GrEq
                "<" -> Cmp.Lo
                "<=" -> Cmp.LoEq
                "==" -> Cmp.Eq
                "!=" -> Cmp.Neq

                "and" -> Op.And
                "or" -> Op.Or

                "true" -> true
                "false" -> false

                else -> {
                    if(matchString.matcher(p).matches()) {
                        p.substring(1, p.length-1)
                    } else if(matchInt.matcher(p).matches()) {
                        p.toLong()
                    } else if(matchFloat.matcher(p).matches()) {
                        p.toDouble()
                    } else if(constants.containsKey(p)) {
                        constants[p]!!
                    } else {
                        throw RuntimeException("unrecognized token at position $i ($p)")
                    }
                }
            }))
        }

        for(i in 0 until depth) {
            tokens.add(Token(tokens.size, Par.Right))
        }

        tokens.reverse()

        return tokens
    }

    fun <T> MutableList<T>.pop(): T? {
        return if(size > 0) removeAt(size - 1) else null
    }

    fun <T> MutableList<T>.lookup(idx: Int): T? {
        return if(size > idx) this[size - 1 - idx] else null
    }

    fun Token.precedence(): Int {
        return when(value){
            is Op -> 0
            is Cmp -> 10
            else -> throw Error(this,"operator or comparator expected")
        }
    }

    class Token(val idx: Int, val value: Any)

    enum class Op {
        And,
        Or
    }

    enum class Cmp {
        Gr,
        GrEq,
        Neq,
        Eq,
        LoEq,
        Lo,
    }

    enum class Par {
        Left, Right
    }

    class Error(token: Token, message: String): RuntimeException("Error at token number ${token.idx} (${token.value}): $message")
}