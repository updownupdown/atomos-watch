// A small JSON reader for the baked data, so the core stays plain Kotlin with no libraries.
// Objects read as maps, arrays as lists, numbers as doubles.

package app.buildatomos.core

object Json {
    fun parse(text: String): Any? = Reader(text).run { val v = value(); space(); v }

    private class Reader(val s: String) {
        var i = 0
        fun space() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            space()
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> { i += 4; true }
                'f' -> { i += 5; false }
                'n' -> { i += 4; null }
                else -> if (c == '-' || c.isDigit()) num() else error("bad JSON at $i")
            }
        }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            space()
            if (s[i] == '}') { i++; return m }
            while (true) {
                space()
                val k = str()
                space(); i++ // :
                m[k] = value()
                space()
                if (s[i++] == '}') return m
            }
        }
        fun arr(): List<Any?> {
            val l = ArrayList<Any?>()
            i++
            space()
            if (s[i] == ']') { i++; return l }
            while (true) {
                l += value()
                space()
                if (s[i++] == ']') return l
            }
        }
        fun str(): String {
            val b = StringBuilder()
            i++
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> when (val e = s[i++]) {
                        'n' -> b.append('\n'); 't' -> b.append('\t'); 'r' -> b.append('\r'); 'b' -> b.append('\b'); 'f' -> b.append('\u000c')
                        'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                        else -> b.append(e)
                    }
                    else -> b.append(c)
                }
            }
        }
        fun num(): Double {
            val st = i
            while (i < s.length && (s[i].isDigit() || s[i] in "+-.eE")) i++
            return s.substring(st, i).toDouble()
        }
    }
}
