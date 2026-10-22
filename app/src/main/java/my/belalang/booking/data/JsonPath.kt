package my.belalang.booking.data

import org.json.JSONArray
import org.json.JSONObject

/** Penyelesai laluan JSON ringkas: `a.b[0].c` (awalan `$` dibenarkan). */
object JsonPath {
    fun parseRoot(text: String): Any? {
        val t = text.trimStart('\uFEFF').trim()
        return try {
            when {
                t.startsWith("[") -> JSONArray(t)
                t.startsWith("{") -> JSONObject(t)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** Petunjuk ringkas bila respons bukan JSON: panjang dan aksara pertama sahaja (bukan isi). */
    fun nonJsonHint(text: String): String {
        val t = text.trimStart('\uFEFF').trim()
        if (t.isEmpty()) return "respons kosong"
        val first = t.first()
        val kind = when {
            first == '<' -> "nampak seperti HTML/XML"
            first == '{' || first == '[' -> "bermula seperti JSON tetapi tidak sah/terpotong"
            else -> "teks biasa"
        }
        return "$kind, ${t.length} aksara, aksara pertama '${if (first.isISOControl()) '?' else first}'"
    }

    fun tokens(path: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        for (ch in path) {
            if (ch == '.' || ch == '[' || ch == ']') {
                if (sb.isNotEmpty()) {
                    out.add(sb.toString())
                    sb.clear()
                }
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    fun resolve(root: Any?, path: String?): Any? {
        if (path.isNullOrBlank() || path == "$") return root
        var cur: Any? = root
        for (tok in tokens(path.removePrefix("$"))) {
            val node = cur
            cur = when (node) {
                is JSONObject -> if (node.has(tok)) node.get(tok) else return null
                is JSONArray -> {
                    val i = tok.toIntOrNull() ?: return null
                    if (i in 0 until node.length()) node.get(i) else return null
                }
                else -> return null
            }
        }
        return if (cur === JSONObject.NULL) null else cur
    }

    fun asString(v: Any?): String? = when (v) {
        null -> null
        is String -> v
        else -> v.toString()
    }
}
