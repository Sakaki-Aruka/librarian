package online.aruka.librarian.command

// --isbn が1件も指定されなかった場合、非対話環境（パイプ/リダイレクト）に限り
// 標準入力から1行1ISBNとして読み込む。対話端末では入力待ちで固まらないよう何もしない。
internal fun resolveIsbnInput(explicit: List<String>): List<String> {
    if (explicit.isNotEmpty()) return explicit
    if (System.console() != null) return emptyList()

    return generateSequence(::readLine)
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .toList()
}
