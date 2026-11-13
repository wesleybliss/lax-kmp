package com.gammagamma.lax.slack

enum class MrkdwnStyle { Bold, Italic, Strike, Code, CodeBlock, Link, Mention, Quote }

data class MrkdwnRun(
    val start: Int,
    val end: Int,
    val style: MrkdwnStyle,
    val target: String = "",
)

data class RenderedMrkdwn(
    val text: String,
    val runs: List<MrkdwnRun>,
)

fun interface NameResolver {
    fun resolveUser(id: String): String?
    fun resolveChannel(id: String): String? = null
}

/**
 * Slack mrkdwn → plain text + style runs.
 * Handles `*bold*`, `_italic_`, `~strike~`, `` `code` ``, ```blocks```,
 * `>` quotes, `<@U>`, `<#C|name>`, `<!here>`, `<url|label>`, and HTML entities.
 */
object Mrkdwn {
    fun render(raw: String, resolver: NameResolver = NameResolver { null }): RenderedMrkdwn {
        val out = StringBuilder()
        val runs = ArrayList<MrkdwnRun>()
        var i = 0
        while (i < raw.length) {
            when {
                raw.startsWith("```", i) -> {
                    val end = raw.indexOf("```", i + 3).let { if (it < 0) raw.length else it }
                    val start = out.length
                    out.append(raw.substring(i + 3, end).trim('\n'))
                    runs += MrkdwnRun(start, out.length, MrkdwnStyle.CodeBlock)
                    i = if (end < raw.length) end + 3 else raw.length
                }
                raw[i] == '`' -> {
                    val end = raw.indexOf('`', i + 1).let { if (it < 0) -1 else it }
                    if (end > i) {
                        val start = out.length
                        out.append(raw, i + 1, end)
                        runs += MrkdwnRun(start, out.length, MrkdwnStyle.Code)
                        i = end + 1
                    } else {
                        out.append(raw[i]); i++
                    }
                }
                raw[i] == '<' -> {
                    val close = raw.indexOf('>', i + 1)
                    if (close < 0 || raw.substring(i, close).contains('\n')) {
                        out.append('<'); i++
                    } else {
                        val body = raw.substring(i + 1, close)
                        val bar = body.indexOf('|')
                        val target = if (bar >= 0) body.substring(0, bar) else body
                        val label = if (bar >= 0) body.substring(bar + 1) else null
                        val start = out.length
                        when {
                            target.startsWith('@') -> {
                                val id = target.drop(1)
                                out.append('@').append(label ?: resolver.resolveUser(id) ?: id)
                                runs += MrkdwnRun(start, out.length, MrkdwnStyle.Mention)
                            }
                            target.startsWith('#') -> {
                                val id = target.drop(1)
                                out.append('#').append(label ?: resolver.resolveChannel(id) ?: id)
                                runs += MrkdwnRun(start, out.length, MrkdwnStyle.Mention)
                            }
                            target.startsWith('!') -> {
                                out.append('@').append(label ?: target.drop(1))
                                runs += MrkdwnRun(start, out.length, MrkdwnStyle.Mention)
                            }
                            else -> {
                                out.append(label ?: unescape(target))
                                runs += MrkdwnRun(start, out.length, MrkdwnStyle.Link, unescape(target))
                            }
                        }
                        i = close + 1
                    }
                }
                raw.startsWith("&lt;", i) -> { out.append('<'); i += 4 }
                raw.startsWith("&gt;", i) -> { out.append('>'); i += 4 }
                raw.startsWith("&amp;", i) -> { out.append('&'); i += 5 }
                raw[i] == '*' || raw[i] == '_' || raw[i] == '~' -> {
                    val mark = raw[i]
                    val end = raw.indexOf(mark, i + 1)
                    if (end > i + 1 && !raw.substring(i + 1, end).contains('\n')) {
                        val style = when (mark) {
                            '*' -> MrkdwnStyle.Bold
                            '_' -> MrkdwnStyle.Italic
                            else -> MrkdwnStyle.Strike
                        }
                        val start = out.length
                        val inner = render(raw.substring(i + 1, end), resolver)
                        out.append(inner.text)
                        runs += inner.runs.map {
                            it.copy(start = it.start + start, end = it.end + start)
                        }
                        runs += MrkdwnRun(start, out.length, style)
                        i = end + 1
                    } else {
                        out.append(mark); i++
                    }
                }
                raw[i] == '>' && (i == 0 || raw[i - 1] == '\n') -> {
                    val start = out.length
                    i++
                    if (i < raw.length && raw[i] == ' ') i++
                    val lineEnd = raw.indexOf('\n', i).let { if (it < 0) raw.length else it }
                    out.append(raw, i, lineEnd)
                    runs += MrkdwnRun(start, out.length, MrkdwnStyle.Quote)
                    i = lineEnd
                }
                else -> {
                    out.append(raw[i]); i++
                }
            }
        }
        return RenderedMrkdwn(out.toString(), runs)
    }

    private fun unescape(s: String): String =
        s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
}
