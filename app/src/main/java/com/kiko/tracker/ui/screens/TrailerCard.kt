package com.kiko.tracker.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.kiko.tracker.ui.theme.KikoColors
import com.kiko.tracker.ui.theme.kikoCircleShape
import com.kiko.tracker.ui.theme.kikoCorner

// Fixed-width variant of ForumVideo for horizontal rows (detail page "Trailers"): same thumbnail
// fallback, border, play button and tap-to-open, plus MAL's label for the PV under the thumbnail.
@Composable fun TrailerCard(videoId: String, title: String, c: KikoColors) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val thumbs = remember(videoId) {
        listOf("https://img.youtube.com/vi/$videoId/maxresdefault.jpg", "https://img.youtube.com/vi/$videoId/hqdefault.jpg")
    }
    var thumbIndex by remember(videoId) { mutableStateOf(0) }
    val shape = RoundedCornerShape(kikoCorner(8.dp))
    Column(Modifier.width(240.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(shape).background(Color.Black)
                .border(1.dp, c.primary.copy(alpha = .5f), shape)
                .clickable {
                    val url = "https://www.youtube.com/watch?v=$videoId"
                    if (runCatching { uriHandler.openUri(url) }.isFailure) {
                        android.widget.Toast.makeText(context, "Couldn't open video link", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
        ) {
            AsyncImage(
                model = thumbs[thumbIndex], contentDescription = "$title thumbnail",
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                onError = { if (thumbIndex < thumbs.lastIndex) thumbIndex++ },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.align(Alignment.Center).size(48.dp).clip(kikoCircleShape()).background(Color.Black.copy(alpha = .6f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Default.PlayArrow, "Play $title on YouTube", tint = Color.White, modifier = Modifier.size(32.dp)) }
        }
        Text(
            title, color = c.ink, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
    }
}