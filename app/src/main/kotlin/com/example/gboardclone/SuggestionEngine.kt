package com.example.gboardclone

/**
 * Lightweight suggestion engine: built-in dictionary + learned user words,
 * edit-distance autocorrect and simple bigram next-word prediction.
 * Not a full language model, but gives a Gboard-like predictive strip.
 */
object SuggestionEngine {

    private val BUILTIN = (
        "the be to of and a in that have i it for not on with he as you do at this but his by from they " +
        "we say her she or an will my one all would there their what so up out if about who get which go me " +
        "when make can like time no just him know take people into year your good some could them see other " +
        "than then now look only come its over think also back after use two how our work first well way even " +
        "new want because any these give day most us is are was were been being am has had did does done " +
        "hello hi hey thanks thank please yes no maybe sure okay ok cool nice great good morning evening night " +
        "love like hate need want help what when where who why how much many really anyway anyway today tomorrow " +
        "here there home time money food water friend family work play game phone message call text email " +
        "send receive open close start stop read write learn understand forget remember name place thing world " +
        "life happy sad angry tired busy free ready fine wrong right true false big small long short old young " +
        "hot cold fast slow early late open shut light dark clean dirty sweet sour hard easy rich poor safe " +
        "apple banana cat dog book pen car bus train ship bike road city country state house room door window " +
        "sun moon star sky cloud rain snow wind fire ice tree flower grass water sea lake river mountain hill"
        ).split(" ").toSet()

    private val words = BUILTIN.toMutableSet()
    private val learned = mutableSetOf<String>()
    private val bigrams = mutableMapOf<String, MutableList<String>>()
    private var lastWord: String? = null

    fun loadLearned(learned: Set<String>) {
        words.addAll(learned)
        this.learned.addAll(learned)
    }

    fun getLearned(): Set<String> = learned.toSet()

    fun resetContext() {
        lastWord = null
    }

    /** Called when a word is finished (e.g. space typed). */
    fun learn(word: String) {
        val w = word.lowercase().trim()
        if (w.length >= 2 && w.all { it.isLetter() }) {
            words.add(w)
            learned.add(w)
            lastWord?.let { prev ->
                val list = bigrams.getOrPut(prev) { mutableListOf() }
                if (list.lastOrNull() != w) list.add(w)
                while (list.size > 6) list.removeAt(0)
            }
            lastWord = w
        }
    }

    fun suggestions(currentWord: String): List<String> {
        val cw = currentWord.lowercase()
        val out = mutableListOf<String>()

        lastWord?.let { prev ->
            bigrams[prev]?.let { out.addAll(it.take(2)) }
        }

        if (cw.length >= 3 && !words.contains(cw)) {
            correct(cw)?.let { if (it !in out) out.add(it) }
        }

        if (out.size < 3) {
            for (f in listOf("the", "i", "you", "and", "to", "a")) {
                if (f !in out) out.add(f)
                if (out.size == 3) break
            }
        }
        return out.take(3)
    }

    private fun correct(w: String): String? {
        var best: String? = null
        var bestD = Int.MAX_VALUE
        for (dict in words) {
            if (kotlin.math.abs(dict.length - w.length) > 2) continue
            val d = editDistance(w, dict)
            if (d in 1..2 && d < bestD) {
                bestD = d
                best = dict
            }
        }
        return best
    }

    private fun editDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i - 1][j] + 1, dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
            }
        }
        return dp[a.length][b.length]
    }
}
