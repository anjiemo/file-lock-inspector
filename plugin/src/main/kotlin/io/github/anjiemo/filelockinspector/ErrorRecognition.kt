package io.github.anjiemo.filelockinspector

object ErrorRecognition {
    private val sharing = Regex("另一个程序正在使用此文件|另一进程正在使用此文件|being used by another process|used by another process|sharing violation", RegexOption.IGNORE_CASE)
    private val path = Regex("(?:[A-Za-z]:[\\\\/])[^\\r\\n<>\\\"|?*]+")
    fun paths(text: String): List<String> {
        if (!sharing.containsMatchIn(text)) return emptyList()
        return text.lineSequence().filter { sharing.containsMatchIn(it) }.flatMap { line ->
            val messageStart = sharing.find(line)!!.range.first
            path.findAll(line.take(messageStart)).map { match ->
                match.value.substringBefore(": ").trim().trimEnd(':', ' ', '\'', ')')
            }
        }.filter { it.length > 3 }.distinct().toList()
    }
}
