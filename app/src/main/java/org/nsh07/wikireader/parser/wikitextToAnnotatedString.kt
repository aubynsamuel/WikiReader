package org.nsh07.wikireader.parser

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.github.tomtung.latex2unicode.LaTeX2Unicode
import org.nsh07.wikireader.data.langCodeToName
import org.nsh07.wikireader.parser.ReferenceData.refCount
import org.nsh07.wikireader.parser.ReferenceData.refList
import org.nsh07.wikireader.parser.ReferenceData.refListCount
import kotlin.math.min
import kotlin.text.Typography.bullet
import kotlin.text.Typography.nbsp
import kotlin.text.Typography.ndash

private const val MAGIC_SEP = "{{!}}"

/**
 * Converts Wikitext source code into an [AnnotatedString] that can be rendered by [androidx.compose.material3.Text]
 */
fun String.toWikitextAnnotatedString(
    colorScheme: ColorScheme,
    typography: Typography,
    loadPage: (String) -> Unit,
    fontSize: Int,
    newLine: Boolean = true,
    inIndentCode: Boolean = false,
    showRef: (String) -> Unit,
): AnnotatedString {
    val hrChar = '─'
    val input = this
    var i = 0
    var number = 1 // Count for numbered lists

    val twas: String.() -> AnnotatedString = {
        this.toWikitextAnnotatedString(
            colorScheme,
            typography,
            loadPage,
            fontSize,
            showRef = showRef
        )
    }

    val twasNoNewline: String.() -> AnnotatedString = {
        this.toWikitextAnnotatedString(
            colorScheme,
            typography,
            loadPage,
            fontSize,
            newLine = false,
            showRef = showRef
        )
    }

    return buildAnnotatedString {
        while (i < input.length) {
            if (input[i] != '#') number = 1
            when (input[i]) {
                ' ' ->
                    if ((getOrNull(i - 1) == '\n' || i == 0) && !inIndentCode) {
                        val curr = substring(i + 1).substringBefore('\n')
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                color = colorScheme.onSurfaceVariant
                            )
                        ) {
                            append(
                                curr.toWikitextAnnotatedString(
                                    colorScheme,
                                    typography,
                                    loadPage,
                                    fontSize,
                                    inIndentCode = true,
                                    showRef = showRef
                                )
                            )
                        }
                        i += curr.length
                    } else append(input[i])

                '<' -> {
                    val currSubstring = input.substring(i)
                    when {
                        currSubstring.startsWith("<nowiki>") -> {
                            val curr =
                                currSubstring.substringBefore("</nowiki>").substringAfter('>')
                            append(curr)
                            i += 8 + curr.length + 8
                        }

                        currSubstring.startsWith("<ref", ignoreCase = true) -> {
                            // Handle references
                            // When a reference footnote is clicked, the showRef lambda is called
                            val open = currSubstring.substringMatchingParen('<', '>')
                            val refName = open
                                .substringAfter("name=")
                                .removeSuffix(">")
                                .trim(' ', '"', '/')
                            if (open.getOrNull(4) == '>') {
                                // Style 1: Plain reference without named references
                                val curr =
                                    currSubstring.substringBefore("</ref>").substringAfter('>')
                                withLink(
                                    LinkAnnotation.Url(
                                        "",
                                        TextLinkStyles(
                                            SpanStyle(color = colorScheme.primary)
                                        )
                                    ) {
                                        showRef(curr)
                                    }
                                ) {
                                    append("<sup>$refCount </sup>".twas())
                                    refCount++
                                }
                                i += 5 + curr.length + 5
                            } else if (open.endsWith("/>")) {
                                // Style 2: reference that refers to a named reference
                                val refWt = refList[refName] ?: ""
                                if (refListCount[refWt] == null) {
                                    refListCount[refWt] = refCount
                                    refCount++
                                }
                                withLink(
                                    LinkAnnotation.Url(
                                        "",
                                        TextLinkStyles(
                                            SpanStyle(color = colorScheme.primary)
                                        )
                                    ) {
                                        showRef(refWt)
                                    }
                                ) { append("<sup>${refListCount[refWt]} </sup>".twas()) }
                                i += open.length - 1
                            } else {
                                // Style 3: Names reference definition
                                val refWt = refList[refName] ?: ""
                                if (refListCount[refWt] == null) {
                                    refListCount[refWt] = refCount
                                    refCount++
                                }
                                withLink(
                                    LinkAnnotation.Url(
                                        "",
                                        TextLinkStyles(
                                            SpanStyle(color = colorScheme.primary)
                                        )
                                    ) {
                                        showRef(refWt)
                                    }
                                ) { append("<sup>${refListCount[refWt]} </sup>".twas()) }
                                i += currSubstring.substringBefore("</ref>").length + 5
                            }
                        }

                        currSubstring.startsWith("<br", ignoreCase = true) -> {
                            append('\n')
                            i += currSubstring.substringBefore('>').length
                        }

                        currSubstring.startsWith("<hr", ignoreCase = true) -> {
                            append('\n')
                            repeat(5) { append(hrChar) }
                            append('\n')
                            i += currSubstring.substringBefore('>').length
                        }

                        currSubstring.startsWith("<u>") -> {
                            val curr = currSubstring.substringBefore("</u>").substringAfter('>')
                            withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) {
                                append(curr.twasNoNewline())
                            }
                            i += 3 + curr.length + 3
                        }

                        currSubstring.startsWith("<div") -> {
                            val divTag = currSubstring.substringBefore('>')
                            val curr = currSubstring.substringBefore("</div>").substringAfter('>')
                            append(
                                curr.twasNoNewline()
                            )
                            i += divTag.length + 1 + curr.length + 5
                        }

                        currSubstring.startsWith("<section") -> {
                            val curr = currSubstring.substringBefore('>')
                            i += curr.length
                        }

                        currSubstring.startsWith("<noinclude>") -> {
                            val curr =
                                currSubstring.substringBefore("</noinclude>").substringAfter('>')
                            append(curr.twasNoNewline())
                            i += 11 + curr.length + 11
                        }

                        currSubstring.startsWith("<code>") -> {
                            val curr = currSubstring.substringBefore("</code>").substringAfter('>')
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurfaceVariant
                                )
                            ) { append(curr.twasNoNewline()) }
                            i += 6 + curr.length + 6
                        }

                        currSubstring.startsWith("<syntaxhighlight") -> {
                            val curr =
                                currSubstring.substringBefore("</syntaxhighlight>")
                                    .substringAfter('>')
                            withStyle(
                                SpanStyle(
                                    fontFamily = FontFamily.Monospace,
                                    color = colorScheme.onSurfaceVariant
                                )
                            ) { append(curr) }
                            i += currSubstring.substringBefore('>').length + curr.length + 18
                        }

                        currSubstring.startsWith("<sub>") -> {
                            val curr = currSubstring.substringBefore("</sub>").substringAfter('>')
                            withStyle(
                                SpanStyle(
                                    baselineShift = BaselineShift.Subscript,
                                    fontSize = (fontSize - 4).sp
                                )
                            ) { append(curr.twasNoNewline()) }
                            i += 5 + curr.length + 5
                        }

                        currSubstring.startsWith("<var>") -> {
                            val curr = currSubstring.substringBefore("</var>").substringAfter('>')
                            append("''$curr''".twasNoNewline())
                            i += 5 + curr.length + 5
                        }

                        currSubstring.startsWith("<sup>") -> {
                            val curr = currSubstring.substringBefore("</sup>").substringAfter('>')
                            withStyle(
                                SpanStyle(
                                    baselineShift = BaselineShift.Superscript,
                                    fontSize = (fontSize - 4).sp
                                )
                            ) { append(curr.twasNoNewline()) }
                            i += 5 + curr.length + 5
                        }

                        currSubstring.startsWith("<small>") -> {
                            val curr = currSubstring.substringBefore("</small>").substringAfter('>')
                            withStyle(SpanStyle(fontSize = (fontSize - 2).sp)) {
                                append(curr.twasNoNewline())
                            }
                            i += 7 + curr.length + 7
                        }

                        currSubstring.startsWith("<math>") -> {
                            val curr = currSubstring.substringBefore("</math>").substringAfter('>')
                            withStyle(SpanStyle(fontFamily = FontFamily.Serif)) {
                                append(LaTeX2Unicode.convert(curr).replace(' ', nbsp))
                            }
                            i += 6 + curr.length + 6
                        }

                        currSubstring.startsWith("<math display") -> {
                            val curr = currSubstring.substringBefore("</math>").substringAfter('>')
                            append("\t\t")
                            withStyle(SpanStyle(fontFamily = FontFamily.Serif)) {
                                append(LaTeX2Unicode.convert(curr).replace(' ', nbsp))
                            }
                            i += currSubstring.substringBefore('>').length + curr.length + "</math>".length
                        }

                        currSubstring.startsWith("<blockquote") -> {
                            val curr =
                                currSubstring.substringBefore("</blockquote>").substringAfter('>')
                            withStyle(
                                ParagraphStyle(
                                    textIndent = TextIndent(
                                        firstLine = 16.sp,
                                        restLine = 16.sp
                                    )
                                )
                            ) {
                                withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                                    append('\n')
                                    append(curr.twas())
                                    append('\n')
                                }
                            }
                            i += 12 + curr.length + 13
                        }

                        currSubstring.startsWith("<!--") -> {
                            val curr = currSubstring.substringMatchingParen('<', '>')
                            i += curr.length - 1
                        }

                        else -> {
                            append(input[i])
                        }
                    }
                }

                '{' ->
                    if (input.getOrNull(i + 1) == '{') {
                        val currSubstring =
                            substringMatchingParen('{', '}', i).substringBeforeLast("}}")
                        when {
                            currSubstring.startsWith("{{cite", ignoreCase = true) -> {
                                val text = if (currSubstring.startsWith("{{cite book", true)) {
                                    val params = mutableMapOf<String, String>()

                                    // Extract inside of {{Cite book ...}}
                                    val content =
                                        currSubstring.substringAfter('|').trim()

                                    // Split by pipes, then split each param by '='
                                    val parts = content.split("|")
                                    for (part in parts) {
                                        val (key, value) = part.split("=", limit = 2)
                                            .map { it.trim() }.let {
                                                if (it.size == 2) it[0] to it[1] else return@let null
                                            } ?: continue
                                        params[key.lowercase()] = value
                                    }

                                    // Build citation text
                                    val first = params["first"]
                                    val last = params["last"]
                                    val author = listOfNotNull(last, first).joinToString().trim()
                                    if (params["script-title"] != null) params["title"] =
                                        params["script-title"] ?: ""
                                    val title =
                                        if (params["title"] != null)
                                            "''${params["title"]}''" + if (params["edition"] != null) {
                                                " (" + params["edition"]!! + " ed.)"
                                            } else ""
                                        else null

                                    listOfNotNull(
                                        "$author (${
                                            listOfNotNull(
                                                params["date"],
                                                params["year"]
                                            ).joinToString()
                                        })",
                                        title,
                                        params["publisher"],
                                        params["isbn"]?.let { "[[ISBN]] $it" }
                                    )
                                        .filter { it.isNotBlank() }
                                        .joinToString(". ")
                                        .plus(".")
                                        .trim()
                                        .twas()
                                } else if (
                                    currSubstring.startsWith("{{cite web", true) ||
                                    currSubstring.startsWith("{{cite news", true) ||
                                    currSubstring.startsWith("{{cite AV media", true) ||
                                    currSubstring.startsWith("{{cite press release", true)
                                ) {
                                    val params = mutableMapOf<String, String>()

                                    // Extract inside of {{Cite ...}}
                                    val content =
                                        currSubstring.substringAfter('|').trim()

                                    // Split by pipes, then split each param by '='
                                    val parts = content.split("|")
                                    for (part in parts) {
                                        val (key, value) = part.split("=", limit = 2)
                                            .map { it.trim() }.let {
                                                if (it.size == 2) it[0] to it[1] else return@let null
                                            } ?: continue
                                        params[key.lowercase()] = value
                                    }

                                    // Build citation text
                                    val first = params["first"]
                                    val last = params["last"]
                                    val author = listOfNotNull(last, first).joinToString().trim()
                                    if (params["script-title"] != null) params["title"] =
                                        params["script-title"] ?: ""
                                    var title =
                                        if (params["title"] != null)
                                            "''${params["title"]}''" + if (params["edition"] != null) {
                                                " (" + params["edition"]!! + " ed.)"
                                            } else ""
                                        else null
                                    if (params["trans-title"] != null) title += " [${params["trans-title"]}]"

                                    listOfNotNull(
                                        "$author (${
                                            listOfNotNull(
                                                params["date"],
                                                params["year"]
                                            ).joinToString()
                                        })".takeIf { it.trim('(', ')').isNotBlank() },
                                        if (params["url"] == null) title.takeIf {
                                            it?.trim('"')?.isNotBlank() == true
                                        }
                                        else "[${params["url"]} $title]".takeIf {
                                            it.trim('"').isNotBlank()
                                        },
                                        params["website"],
                                        "''${params["work"] ?: ""}''".takeIf {
                                            it.trim('\'').isNotBlank()
                                        },
                                        params["publisher"],
                                        "[${params["archive-url"]} Archived] ${params["archive-date"]}".takeIf { params["archive-url"] != null },
                                        "via ${params["via"]}".takeIf { params["via"] != null }
                                    )
                                        .filter { it.isNotBlank() }
                                        .joinToString(". ")
                                        .plus(".")
                                        .trim()
                                        .twas()
                                } else if (currSubstring.startsWith("{{cite journal", true)) {
                                    val params = mutableMapOf<String, String>()

                                    // Extract inside of {{Cite journal ...}}
                                    val content =
                                        currSubstring.substringAfter('|').trim()

                                    // Split by pipes, then split each param by '='
                                    val parts = content.split("|")
                                    for (part in parts) {
                                        val (key, value) = part.split("=", limit = 2)
                                            .map { it.trim() }.let {
                                                if (it.size == 2) it[0] to it[1] else return@let null
                                            } ?: continue
                                        params[key.lowercase()] = value
                                    }

                                    // Build citation text
                                    val first = params["first"]
                                    val last = params["last"]
                                    val author = listOfNotNull(last, first).joinToString().trim()
                                    if (params["script-title"] != null) params["title"] =
                                        params["script-title"] ?: ""
                                    val title =
                                        if (params["title"] != null)
                                            "''${params["title"]}''" + if (params["edition"] != null) {
                                                " (" + params["edition"]!! + " ed.)"
                                            } else ""
                                        else null
                                    val volume =
                                        "'''${params["volume"]}'''${" (${params["issue"]})".takeIf { params["issue"] != null } ?: ""}${": ${params["pages"]}".takeIf { params["pages"] != null } ?: ""}".takeIf { params["volume"] != null }

                                    listOfNotNull(
                                        "$author (${
                                            listOfNotNull(
                                                params["date"],
                                                params["year"]
                                            ).joinToString()
                                        })",
                                        if (params["url"] == null) title.takeIf {
                                            it?.trim('"')?.isNotBlank() == true
                                        }
                                        else "[${params["url"]} $title]".takeIf {
                                            it.trim('"').isNotBlank()
                                        },
                                        "''${params["journal"]}''".takeIf { params["journal"] != null },
                                        volume,
                                        params["publisher"],
                                        "[[ISSN]] ${params["issn"]}".takeIf { params["issn"] != null },
                                        "[[Bibcode]]:${params["bibcode"]}".takeIf { params["bibcode"] != null },
                                        "[[Doi_(identifier)|doi]]:${params["doi"]}".takeIf { params["doi"] != null },
                                        "[[PMC]]:${params["PMC"]}".takeIf { params["PMC"] != null },
                                        "[[PMID]]:${params["PMID"]}".takeIf { params["PMID"] != null },
                                    )
                                        .filter { it.isNotBlank() }
                                        .joinToString(". ")
                                        .plus(".")
                                        .trim()
                                        .twas()
                                } else AnnotatedString(currSubstring)
                                append(text)
                            }

                            currSubstring.startsWith("{{abbr", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|').substringBefore('|')
                                append(curr.twas())
                            }

                            currSubstring.startsWith("{{TableTBA", ignoreCase = true) -> {
                                append("<small>TBA</small>".twas())
                            }

                            currSubstring.startsWith("{{efn", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                curr.twas()
                            }

                            currSubstring.startsWith("{{convert", ignoreCase = true) ||
                                    currSubstring.startsWith("{{cvt", ignoreCase = true)
                                -> {
                                val curr = currSubstring.substringAfter('|')
                                val currSplit = curr.split('|')
                                var toAdd = ""
                                toAdd += currSplit[0]
                                toAdd += if (currSplit[1] in listOf("-", "to", "and")) {
                                    " " + currSplit[1] + " " + currSplit[2] + " " + currSplit[3]
                                } else {
                                    " " + currSplit[1]
                                }
                                append(toAdd)
                            }

                            currSubstring.startsWith("{{mono", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontFamily = FontFamily.Monospace)) {
                                    append(curr.twas())
                                }
                            }

                            currSubstring.startsWith("{{math", ignoreCase = true) ||
                                    currSubstring.startsWith("{{mvar", ignoreCase = true)
                                -> {
                                val curr = currSubstring.substringAfter('|').removePrefix("1=")
                                withStyle(SpanStyle(fontFamily = FontFamily.Serif)) {
                                    append(curr.replace(' ', nbsp).twas())
                                    append(curr.replace(' ', nbsp).twas())
                                }
                            }

                            currSubstring.startsWith("{{val", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|').substringBefore('|')
                                append(curr.twas())
                            }

                            currSubstring.startsWith("{{var", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                append("''$curr''".twas())
                            }

                            arrayOf("{{small", "{{smaller", "{{petit", "{{hw-small", "{{sma").any {
                                currSubstring.startsWith(it, ignoreCase = true)
                            } -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontSize = (fontSize - 2).sp)) {
                                    append(curr.twas())
                                }
                            }

                            currSubstring.startsWith("{{main", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                val splitList = curr.split('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append("Main article")
                                    if (splitList.size > 1) append("s: ")
                                    else append(": ")
                                    splitList.forEachIndexed { index, it ->
                                        append(
                                            "[[${it.substringBefore(MAGIC_SEP)}|${
                                                it.substringAfter(MAGIC_SEP).replace(
                                                    "#",
                                                    " § "
                                                )
                                            }]]".twas()
                                        )
                                        if (index == splitList.size - 2 && splitList.size > 1) append(
                                            ", and "
                                        )
                                        else if (index < splitList.size - 1) append(", ")
                                    }
                                }
                                append('\n')
                            }

                            currSubstring.startsWith("{{see also", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                val splitList = curr.split('|').filterNot { it.startsWith('#') }
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append("See also: ")
                                    splitList.forEachIndexed { index, it ->
                                        append(
                                            "[[${it.substringBefore(MAGIC_SEP)}|${
                                                it.substringAfter((MAGIC_SEP)).replace("#", " § ")
                                            }]]".twas()
                                        )
                                        if (index == splitList.size - 2 && splitList.size > 1) append(
                                            ", and "
                                        )
                                        else if (index < splitList.size - 1) append(", ")
                                    }
                                }
                            }

                            currSubstring.startsWith("{{date", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                val splitList = curr.split('|')
                                if (splitList.size < 3) {
                                    append(splitList[0])
                                } else {
                                    append(splitList[0])
                                    append(' ')
                                    append(splitList[1])
                                    append(' ')
                                    append(splitList[2])
                                }
                            }

                            currSubstring.startsWith("{{dfn", true) -> {
                                val curr = currSubstring.substringAfter('|')
                                append("'''$curr'''".twas())
                            }

                            currSubstring.startsWith("{{distinguish", ignoreCase = true) -> {
                                val textSpecified =
                                    currSubstring.contains("text=") || currSubstring.contains("text =")
                                val curr =
                                    if (textSpecified) currSubstring.substringAfter('=').trim()
                                    else currSubstring.substringAfter('|')
                                val splitList =
                                    if (textSpecified) listOf(curr)
                                    else curr.split('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append("Not to be confused with ")
                                    if (!textSpecified) splitList.forEachIndexed { index, it ->
                                        append(
                                            "[[${it.substringBefore(MAGIC_SEP)}|${
                                                it.substringAfter(
                                                    MAGIC_SEP
                                                ).replace("#", " § ")
                                            }]]".twas()
                                        )
                                        if (index == splitList.size - 2 && splitList.size > 1) append(
                                            ", or "
                                        )
                                        else if (index < splitList.size - 1) append(", ")
                                    } else append(splitList[0].twas())
                                    append('.')
                                }
                            }

                            currSubstring.startsWith("{{hatnote", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                append("''$curr''".twas())
                            }

                            currSubstring.startsWith("{{IPAc-en", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|').split('|')
                                    .filterNot { it.contains('=') }.joinToString("")
                                append("/${curr.replace(' ', nbsp)}/")
                            }

                            currSubstring.startsWith("{{langx", ignoreCase = true) -> {
                                val lang = langCodeToName(
                                    currSubstring.substringAfter('|').substringBefore('|').trim()
                                )
                                val text =
                                    currSubstring.substringAfter('|').substringAfter('|').split('|')
                                        .filterNot { it.contains('=') }.joinToString()
                                append("[[$lang|$lang]]: ".twas())
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(
                                        text.twas()
                                    )
                                }
                            }

                            currSubstring.startsWith("{{lang|", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(curr.substringAfter('|').substringBefore('|').twas())
                                }
                            }

                            currSubstring.startsWith("{{IPA", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontSize = (fontSize - 2).sp)) {
                                    append("${langCodeToName(curr.substringBefore('|'))}: ")
                                }
                                append(
                                    "[${
                                        curr.substringAfter('|').substringBefore("|")
                                            .replace("|", "").replace(' ', nbsp)
                                    }]"
                                )
                            }

                            currSubstring.startsWith("{{respell", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(curr.replace('|', '-'))
                                }
                            }

                            currSubstring.startsWith("{{BCE", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|').substringBefore('|')
                                append(curr)
                                append(nbsp)
                                append("BCE")
                            }

                            currSubstring.startsWith("{{blockquote", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(
                                    ParagraphStyle(
                                        textIndent = TextIndent(
                                            firstLine = 16.sp,
                                            restLine = 16.sp
                                        )
                                    )
                                ) {
                                    withStyle(SpanStyle(color = colorScheme.onSurfaceVariant)) {
                                        append('\n')
                                        append(
                                            curr.substringAfter("text=")
                                                .substringBefore('=')
                                                .substringBeforeLast('|')
                                                .twas()
                                        )
                                        append('\n')
                                    }
                                }
                            }

                            currSubstring.startsWith("{{further", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                val topic = curr.substringAfter("topic=", "").substringBefore('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append("Further")
                                    if (topic.isNotEmpty()) {
                                        append(" information on $topic")
                                        append(
                                            ": [[${
                                                curr.substringAfter('|').substringBefore('|')
                                                    .substringBefore('#')
                                            }]]\n".twas()
                                        )
                                    } else {
                                        append(" reading")
                                        append(
                                            ": [[${
                                                curr.substringAfter('|').substringBefore('|')
                                                    .substringBefore('#')
                                            }]]\n".twas()
                                        )
                                    }
                                }
                            }

                            currSubstring.startsWith("{{rp", true) -> {
                                val curr = currSubstring.substringAfter('|')
                                append("<sup>:$curr </sup>".twas())
                            }

                            currSubstring.startsWith("{{isbn", true) -> {
                                val curr = currSubstring.substringAfter('|').split('|')
                                    .filterNot { it.contains('=') }.joinToString()
                                append("[[ISBN]] $curr".twas())
                            }

                            currSubstring.startsWith("{{sfrac") -> {
                                val curr = currSubstring.substringAfter('|')
                                val splitList = curr.split('|')
                                when (splitList.size) {
                                    3 -> append("${splitList[0]}<sup>${splitList[1]}</sup>/<sub>${splitList[2]}</sub>".twas())
                                    2 -> append("<sup>${splitList[0]}</sup>/<sub>${splitList[1]}</sub>".twas())
                                    1 -> append("<sup>1</sup>/<sub>${splitList[0]}</sub>".twas())
                                }
                            }

                            currSubstring.startsWith("{{as of", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter("{{")
                                append(curr.substringBefore('|'))
                                append(' ')
                                var date = ""
                                curr.substringAfter('|').split('|').forEach {
                                    if (it.toIntOrNull() != null) {
                                        date += it
                                        date += '/'
                                    }
                                }
                                append(date.trim('/'))
                            }

                            currSubstring.startsWith("{{unichar", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|').substringBefore('|')
                                append("<code>U+$curr</code> ".twas())
                                try {
                                    append(Character.toString(curr.toInt(16)))
                                } catch (_: Exception) {
                                }
                            }

                            currSubstring.startsWith("{{char", ignoreCase = true) -> {
                                append(currSubstring.substringAfter('|').twas())
                            }

                            currSubstring.startsWith("{{Nihongo", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|').split('|')
                                    .filterNot { it.contains('=') }.joinToString("|")
                                append(curr.twas())
                            }

                            currSubstring.startsWith("{{flagg", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|').substringAfter('|')
                                    .substringBefore('|')
                                append(curr.twas())
                            }

                            currSubstring.startsWith("{{noflag", ignoreCase = true) -> {
                                val curr = currSubstring.substringAfter('|')
                                append(curr.twas())
                            }

                            arrayOf("{{nowrap", "{{nobr", "{{nobreak", "{{nwr", "{{nbr").any {
                                currSubstring.startsWith(it, ignoreCase = true)
                            } -> {
                                val curr = currSubstring.substringAfter('|')
                                append(curr.replace(' ', nbsp).twas())
                            }

                            currSubstring.startsWith(
                                "{{spaced en dash",
                                ignoreCase = true
                            ) || currSubstring.startsWith("{{snd", ignoreCase = true)
                                -> {
                                append(nbsp)
                                append(ndash)
                                append(' ')
                            }

                            currSubstring.startsWith("{{empty section", ignoreCase = true) -> {
                                withStyle(
                                    SpanStyle(
                                        fontStyle = FontStyle.Italic,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                ) {
                                    append("This section is empty. You can help by adding to it on Wikipedia.")
                                }
                            }

                            currSubstring.startsWith("{{\"|") -> {
                                val curr = currSubstring.substringAfter('|')
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                                    append(curr.twas())
                                }
                            }

                            currSubstring.startsWith("{{Starbox begin", ignoreCase = true) -> {
                                val templateLength = currSubstring.length
                                i = input.indexOf(
                                    "{{starbox end}}",
                                    i,
                                    ignoreCase = true
                                ) - templateLength + 15
                            }

                            currSubstring.startsWith(
                                "{{lahirmati",
                                true
                            ) -> { // Indonesian template for date of birth and death
                                val curr =
                                    currSubstring.substringAfter('|').substringAfter('|').split('|')
                                if (curr.size <= 3) {
                                    append("lahir " + curr.joinToString("/").twas())
                                } else append(
                                    "${
                                        curr.subList(0, min(curr.size, 3)).joinToString("/").twas()
                                    } $ndash ${curr.subList(3, curr.size).joinToString("/")}"
                                )
                            }

                            else -> {
                                val curr = input.getOrNull(i + 1 + currSubstring.length + 1)
                                if (curr == '\n') i++
                            }
                        }
                        i += currSubstring.length + 1
                    } else append(input[i])

                '&' ->
                    if (input.substring(i, min(i + 10, input.length)).contains(';')) {
                        val curr = input.substring(i, input.indexOf(';', i) + 1)
                        append(AnnotatedString.fromHtml(curr))
                        i += curr.length - 1
                    } else append(input[i])

                '*' ->
                    if ((i == 0 || input.getOrNull(i - 1) == '\n') && newLine) {
                        val bulletCount =
                            input.substring(i).substringBefore(' ').count { it == '*' }
                        val curr = input.substring(i).substringBefore('\n')
                        withStyle(
                            ParagraphStyle(
                                textIndent = TextIndent(restLine = (12 * bulletCount).sp),
                                lineHeight = (24 * (fontSize / 16.0)).toInt().sp,
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            )
                        ) {
                            append("\t\t".repeat(bulletCount - 1))
                            append(bullet)
                            append("\t\t")
                            append(curr.substringAfterLast('*').trim().twas())
                        }
                        i += curr.length
                    } else append(input[i])

                '#' ->
                    if ((i == 0 || input.getOrNull(i - 1) == '\n') && newLine) {
                        val bulletCount =
                            input.substring(i).substringBefore(' ').count { it == '#' }
                        val curr = input.substring(i).substringBefore('\n')
                        withStyle(
                            ParagraphStyle(
                                textIndent = TextIndent(restLine = (27 * bulletCount).sp),
                                lineHeight = (24 * (fontSize / 16.0)).toInt().sp,
                                lineHeightStyle = LineHeightStyle(
                                    alignment = LineHeightStyle.Alignment.Center,
                                    trim = LineHeightStyle.Trim.None
                                )
                            )
                        ) {
                            append("\t\t".repeat(bulletCount - 1))
                            append("$number.")
                            append("\t\t")
                            append(curr.substringAfterLast('#').trim().twas())
                        }
                        i += curr.length
                        number++
                    } else append(input[i])

                '=' ->
                    if (input.getOrNull(i + 1) == '=' && input.getOrNull(i + 2) == '=') {
                        if (input.getOrNull(i + 3) == '=') {
                            if (input.getOrNull(i + 4) == '=') { // h5
                                val curr = input.substring(i + 5).substringBefore("=====")
                                withStyle(typography.titleMedium.toSpanStyle()) {
                                    append(curr.trim().twas())
                                }
                                i += 4 + curr.length + 5
                            } else { // h4
                                val curr = input.substring(i + 4).substringBefore("====")
                                withStyle(typography.titleLarge.toSpanStyle()) {
                                    append("${curr.trim()}\n".twas())
                                }
                                i += 3 + curr.length + 4
                            }
                        } else { // h3
                            val curr = input.substring(i + 3).substringBefore("===")
                            withStyle(typography.headlineSmall.toSpanStyle()) {
                                append("${curr.trim()}\n".twas())
                            }
                            i += 2 + curr.length + 3
                        }
                    } else append(input[i])

                '\'' ->
                    if (input.getOrNull(i + 1) == '\'' && input.getOrNull(i + 2) == '\'') {
                        val subs = input.substring(i + 3)
                        val curr = subs.substring(
                            0,
                            min(
                                subs.length,
                                ("'''(?!')".toRegex().find(subs)?.range?.start ?: subs.length) + 2
                            )
                        )
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(curr.twas())
                        }
                        i += curr.length + 3
                    } else if (input.getOrNull(i + 1) == '\'') {
                        val curr = input.substring(i + 2).substringBefore("''")
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(curr.twas())
                        }
                        i += 1 + curr.length + 2
                    } else append(input[i])

                '[' ->
                    if (input.getOrNull(i + 1) == '[') {
                        val curr = input.substring(i + 2).substringBefore("]]")
                        if (!curr.startsWith("File:", ignoreCase = true)) {
                            withLink(
                                LinkAnnotation.Url(
                                    "",
                                    TextLinkStyles(
                                        SpanStyle(color = colorScheme.primary)
                                    )
                                ) {
                                    loadPage(curr.substringBefore('|').substringBefore('#'))
                                }
                            ) {
                                append(curr.substringAfter('|').trim().twas())
                            }
                            i += 1 + curr.length + 2
                        } else i += substringMatchingParen('[', ']', i).length - 1
                    } else if (input.getOrNull(i + 1) == 'h') {
                        val linkText = input.substringMatchingParen('[', ']', i)
                        withLink(
                            LinkAnnotation.Url(
                                "",
                                TextLinkStyles(
                                    SpanStyle(color = colorScheme.primary)
                                )
                            ) {
                                showRef(linkText.substringBefore(' ').removePrefix("["))
                            }
                        ) {
                            append(
                                (linkText.substringAfter(' ').removeSuffix("]")
                                    .trim() + "\uD83D\uDD17")
                            )
                        }
                        i += linkText.length - 1
                    } else append(input[i])

                else -> append(input[i])
            }
            i++
        }
    }
}

fun String.substringMatchingParen(
    openingParen: Char,
    closingParen: Char,
    startIndex: Int = 0
): String {
    var i = startIndex
    var stack = 0
    while (i < length) {
        if (this[i] == openingParen) stack++
        else if (this[i] == closingParen) stack--

        if (stack == 0) break

        i++
    }

    return if (i < length) this.substring(startIndex, i + 1)
    else this
}

fun String.buildRefList() {
    var i = 0
    while (i < this.length) {
        if (this[i] == '<') {
            if (this.substring(i, min(i + 10, this.length)).startsWith("<ref name")) {
                val open = this.substringMatchingParen('<', '>', i)
                if (!open.endsWith("/>")) {
                    val refWt = this.substring(i).substringBefore("</ref>").substringAfter('>')
                    val refName = open.substringAfter("name=").removeSuffix(">").trim('"', ' ')
                    refList[refName] = refWt
                }
            }
        }
        i++
    }
}

object ReferenceData {
    var refCount = 1
    val refList = mutableMapOf<String, String>()
    val refListCount = mutableMapOf<String, Int>()
}