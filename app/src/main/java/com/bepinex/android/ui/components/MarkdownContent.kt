package com.bepinex.android.ui.components

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.intellij.markdown.IElementType
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun MarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val flavour = remember { GFMFlavourDescriptor() }
    val root = remember(markdown) {
        MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
    }

    SelectionContainer(modifier = modifier) {
        Column {
            MarkdownBlocks(
                content = markdown,
                node = root,
                onLinkClick = { url ->
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            )
        }
    }
}

fun plainTextFromMarkdown(markdown: String): String {
    if (markdown.isBlank()) return ""
    return markdown
        .replace(Regex("```[\\s\\S]*?```"), " ")
        .replace(Regex("`([^`]+)`"), "$1")
        .replace(Regex("!\\[[^\\]]*\\]\\([^)]*\\)"), " ")
        .replace(Regex("\\[([^\\]]+)\\]\\([^)]*\\)"), "$1")
        .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "")
        .replace(Regex("^\\s{0,3}>\\s?", RegexOption.MULTILINE), "")
        .replace(Regex("^\\s*[-*+]\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("^\\s*\\d+\\.\\s+", RegexOption.MULTILINE), "")
        .replace(Regex("(\\*\\*|__|\\*|_|~~)"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}

@Composable
private fun MarkdownBlocks(
    content: String,
    node: ASTNode,
    onLinkClick: (String) -> Unit
) {
    node.children.forEach { child ->
        when (child.type) {
            MarkdownElementTypes.PARAGRAPH -> MarkdownParagraph(content, child, onLinkClick)
            MarkdownElementTypes.ATX_1 -> MarkdownHeading(content, child, 1, onLinkClick)
            MarkdownElementTypes.ATX_2 -> MarkdownHeading(content, child, 2, onLinkClick)
            MarkdownElementTypes.ATX_3 -> MarkdownHeading(content, child, 3, onLinkClick)
            MarkdownElementTypes.ATX_4,
            MarkdownElementTypes.ATX_5,
            MarkdownElementTypes.ATX_6 -> MarkdownHeading(content, child, 4, onLinkClick)
            MarkdownElementTypes.SETEXT_1 -> MarkdownHeading(content, child, 1, onLinkClick)
            MarkdownElementTypes.SETEXT_2 -> MarkdownHeading(content, child, 2, onLinkClick)
            MarkdownElementTypes.UNORDERED_LIST -> MarkdownList(content, child, ordered = false, onLinkClick)
            MarkdownElementTypes.ORDERED_LIST -> MarkdownList(content, child, ordered = true, onLinkClick)
            MarkdownElementTypes.BLOCK_QUOTE -> MarkdownQuote(content, child, onLinkClick)
            MarkdownElementTypes.CODE_FENCE,
            MarkdownElementTypes.CODE_BLOCK -> MarkdownCodeBlock(content, child)
            MarkdownElementTypes.IMAGE -> {
                Spacer(Modifier.height(8.dp))
                MarkdownImage(content, child)
                Spacer(Modifier.height(8.dp))
            }
            MarkdownTokenTypes.HORIZONTAL_RULE -> {
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
                Spacer(Modifier.height(8.dp))
            }
            MarkdownElementTypes.HTML_BLOCK -> {
                val html = child.raw(content).trim()
                if (html.isNotBlank()) {
                    Text(
                        text = html,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            GFMElementTypes.TABLE -> MarkdownParagraph(content, child, onLinkClick)
            MarkdownTokenTypes.EOL, MarkdownTokenTypes.WHITE_SPACE -> Unit
            else -> {
                if (child.children.isNotEmpty()) {
                    MarkdownBlocks(content, child, onLinkClick)
                }
            }
        }
    }
}

@Composable
private fun MarkdownHeading(
    content: String,
    node: ASTNode,
    level: Int,
    onLinkClick: (String) -> Unit
) {
    val style = when (level) {
        1 -> MaterialTheme.typography.headlineSmall
        2 -> MaterialTheme.typography.titleLarge
        3 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.titleSmall
    }
    Spacer(Modifier.height(12.dp))
    Text(
        text = inlineAnnotatedString(content, node, onLinkClick),
        style = style.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun MarkdownParagraph(
    content: String,
    node: ASTNode,
    onLinkClick: (String) -> Unit
) {
    val image = node.children.firstOrNull { it.type == MarkdownElementTypes.IMAGE }
    val meaningful = node.children.filterNot {
        it.type == MarkdownTokenTypes.EOL || it.type == MarkdownTokenTypes.WHITE_SPACE
    }
    if (image != null && meaningful.size == 1) {
        Spacer(Modifier.height(8.dp))
        MarkdownImage(content, image)
        Spacer(Modifier.height(8.dp))
        return
    }
    val text = inlineAnnotatedString(content, node, onLinkClick)
    if (text.text.isBlank()) return
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
private fun MarkdownList(
    content: String,
    node: ASTNode,
    ordered: Boolean,
    onLinkClick: (String) -> Unit
) {
    val items = node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        items.forEachIndexed { index, item ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    text = if (ordered) "${index + 1}." else "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.width(20.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    MarkdownBlocks(content, item, onLinkClick)
                }
            }
        }
    }
}

@Composable
private fun MarkdownQuote(
    content: String,
    node: ASTNode,
    onLinkClick: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(12.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .heightIn(min = 16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            MarkdownBlocks(content, node, onLinkClick)
        }
    }
}

@Composable
private fun MarkdownCodeBlock(content: String, node: ASTNode) {
    val code = node.children
        .filter {
            it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT ||
                it.type == MarkdownTokenTypes.CODE_LINE
        }
        .joinToString("") { it.raw(content) }
        .trimEnd()
    if (code.isBlank()) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = code,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                lineHeight = 18.sp
            ),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
private fun MarkdownImage(content: String, node: ASTNode) {
    val url = node.findChild(MarkdownElementTypes.LINK_DESTINATION)?.raw(content)?.trim().orEmpty()
    val alt = node.findChild(MarkdownElementTypes.LINK_TEXT)?.let { textNode ->
        textNode.children
            .filter { it.type == MarkdownTokenTypes.TEXT }
            .joinToString("") { it.raw(content) }
    }.orEmpty()
    if (url.isBlank()) return

    var bitmap by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url) {
        bitmap = withContext(Dispatchers.IO) { loadMarkdownBitmap(url) }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!,
            contentDescription = alt,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp)),
            contentScale = ContentScale.FillWidth
        )
    } else {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Box(modifier = Modifier.size(120.dp))
        }
    }
}

@Composable
private fun inlineAnnotatedString(
    content: String,
    node: ASTNode,
    onLinkClick: (String) -> Unit
): androidx.compose.ui.text.AnnotatedString {
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.surface
    return buildAnnotatedString {
        appendInline(content, node, onLinkClick, linkColor, codeBackground)
    }
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendInline(
    content: String,
    node: ASTNode,
    onLinkClick: (String) -> Unit,
    linkColor: Color,
    codeBackground: Color
) {
    when (node.type) {
        MarkdownTokenTypes.TEXT,
        MarkdownTokenTypes.WHITE_SPACE,
        MarkdownTokenTypes.SINGLE_QUOTE,
        MarkdownTokenTypes.DOUBLE_QUOTE,
        MarkdownTokenTypes.LPAREN,
        MarkdownTokenTypes.RPAREN,
        MarkdownTokenTypes.COLON,
        MarkdownTokenTypes.EXCLAMATION_MARK -> append(node.raw(content))
        MarkdownTokenTypes.EOL -> append('\n')
        MarkdownTokenTypes.HARD_LINE_BREAK -> append('\n')
        MarkdownElementTypes.EMPH -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
            node.children.forEach { appendInline(content, it, onLinkClick, linkColor, codeBackground) }
        }
        MarkdownElementTypes.STRONG -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
            node.children.forEach { appendInline(content, it, onLinkClick, linkColor, codeBackground) }
        }
        GFMElementTypes.STRIKETHROUGH -> withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
            node.children.forEach { appendInline(content, it, onLinkClick, linkColor, codeBackground) }
        }
        MarkdownElementTypes.CODE_SPAN -> {
            val code = node.raw(content).trim('`').trim()
            withStyle(
                SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = codeBackground
                )
            ) {
                append(code)
            }
        }
        MarkdownElementTypes.INLINE_LINK,
        MarkdownElementTypes.FULL_REFERENCE_LINK,
        MarkdownElementTypes.SHORT_REFERENCE_LINK -> {
            val label = node.findChild(MarkdownElementTypes.LINK_TEXT)?.let { textNode ->
                textNode.children
                    .filter {
                        it.type != MarkdownTokenTypes.LBRACKET &&
                            it.type != MarkdownTokenTypes.RBRACKET
                    }
                    .joinToString("") { it.raw(content) }
            }.orEmpty().ifBlank { node.raw(content) }
            val url = node.findChild(MarkdownElementTypes.LINK_DESTINATION)?.raw(content)?.trim().orEmpty()
            if (url.isNotBlank()) {
                withLink(
                    LinkAnnotation.Url(
                        url = url,
                        styles = TextLinkStyles(
                            style = SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline
                            )
                        ),
                        linkInteractionListener = { onLinkClick(url) }
                    )
                ) {
                    append(label.ifBlank { url })
                }
            } else {
                append(label)
            }
        }
        MarkdownElementTypes.AUTOLINK -> {
            val url = node.raw(content).trim('<', '>').trim()
            withLink(
                LinkAnnotation.Url(
                    url = url,
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline
                        )
                    ),
                    linkInteractionListener = { onLinkClick(url) }
                )
            ) { append(url) }
        }
        MarkdownElementTypes.IMAGE -> {
            val alt = node.findChild(MarkdownElementTypes.LINK_TEXT)?.raw(content).orEmpty()
            append(alt.trim('[', ']').ifBlank { " " })
        }
        MarkdownTokenTypes.ATX_HEADER,
        MarkdownTokenTypes.ATX_CONTENT -> node.children.forEach {
            appendInline(content, it, onLinkClick, linkColor, codeBackground)
        }
        MarkdownTokenTypes.LIST_BULLET,
        MarkdownTokenTypes.LIST_NUMBER,
        MarkdownTokenTypes.BLOCK_QUOTE,
        MarkdownTokenTypes.CODE_FENCE_START,
        MarkdownTokenTypes.CODE_FENCE_END,
        MarkdownTokenTypes.LBRACKET,
        MarkdownTokenTypes.RBRACKET,
        MarkdownTokenTypes.LT,
        MarkdownTokenTypes.GT -> Unit
        else -> {
            if (node.children.isEmpty()) {
                val raw = node.raw(content)
                if (raw.isNotBlank() && !raw.startsWith("#")) append(raw)
            } else {
                node.children.forEach { appendInline(content, it, onLinkClick, linkColor, codeBackground) }
            }
        }
    }
}

private fun ASTNode.raw(content: String): String =
    if (startOffset in 0..content.length && endOffset in 0..content.length && endOffset >= startOffset) {
        content.substring(startOffset, endOffset)
    } else {
        ""
    }

private fun ASTNode.findChild(type: IElementType): ASTNode? {
    if (this.type == type) return this
    children.forEach { child ->
        child.findChild(type)?.let { return it }
    }
    return null
}

private fun loadMarkdownBitmap(url: String): ImageBitmap? {
    var conn: HttpURLConnection? = null
    return try {
        conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 8000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "PVZRH-Launcher/1.0")
        }
        if (conn.responseCode !in 200..299) return null
        conn.inputStream.use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
    } catch (_: Exception) {
        null
    } finally {
        conn?.disconnect()
    }
}
