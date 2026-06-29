package com.karterlauncher.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.ColorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import com.karterlauncher.R
import com.karterlauncher.data.MediaQueueItemUi
import com.karterlauncher.data.NowPlayingUi
import com.karterlauncher.ui.theme.rememberSoftPressInteraction
import com.karterlauncher.ui.theme.softPressScale
import com.karterlauncher.util.formatTrackDuration
import kotlin.math.max

private val PlayerOuterPad = 16.dp
private val PlayerGap = 16.dp
private val PlayerCorner = 24.dp
private val ArtworkCorner = 24.dp
private val PlayerSplitBreakpoint = 600.dp

@Composable
fun MusicPlayerScreen(
    nowPlaying: NowPlayingUi,
    positionMs: Long,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onStartPlayback: () -> Unit,
    onSkipToQueueItem: (Long) -> Unit,
    onRequestNotificationAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(PlayerOuterPad),
    ) {
        val isWide = maxWidth >= PlayerSplitBreakpoint
        if (isWide) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(PlayerGap),
            ) {
                NowPlayingPanel(
                    nowPlaying = nowPlaying,
                    positionMs = positionMs,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onStartPlayback = onStartPlayback,
                    onRequestNotificationAccess = onRequestNotificationAccess,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                UpNextPanel(
                    nowPlaying = nowPlaying,
                    onSkipToQueueItem = onSkipToQueueItem,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(PlayerGap),
            ) {
                NowPlayingPanel(
                    nowPlaying = nowPlaying,
                    positionMs = positionMs,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onStartPlayback = onStartPlayback,
                    onRequestNotificationAccess = onRequestNotificationAccess,
                    modifier = Modifier
                        .weight(1.05f)
                        .fillMaxWidth(),
                )
                UpNextPanel(
                    nowPlaying = nowPlaying,
                    onSkipToQueueItem = onSkipToQueueItem,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun NowPlayingPanel(
    nowPlaying: NowPlayingUi,
    positionMs: Long,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onStartPlayback: () -> Unit,
    onRequestNotificationAccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val placeholderPainter = remember(surfaceVariant) { ColorPainter(surfaceVariant) }
    val scheme = MaterialTheme.colorScheme

    val titleText = when {
        nowPlaying.hasActiveSession && !nowPlaying.title.isNullOrBlank() -> nowPlaying.title
        nowPlaying.hasActiveSession -> stringResource(R.string.music_widget_unknown_track)
        else -> stringResource(R.string.music_player_no_session_title)
    }
    val subtitleText = when {
        !nowPlaying.notificationAccessEnabled ->
            stringResource(R.string.music_widget_enable_listener)
        nowPlaying.hasActiveSession -> {
            nowPlaying.subtitle?.takeIf { it.isNotBlank() }
                ?: nowPlaying.artist?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.music_widget_playing)
        }
        else -> stringResource(R.string.music_widget_nothing_playing)
    }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(PlayerCorner),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val availableHeight = maxHeight
            val availableWidth = maxWidth
            val artworkSize = calculateArtworkSize(
                availableHeight = availableHeight,
                availableWidth = availableWidth,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                NowPlayingArtwork(
                    artworkModel = nowPlaying.artworkModel,
                    placeholderPainter = placeholderPainter,
                    context = context,
                    modifier = Modifier.size(artworkSize),
                )

                NowPlayingMeta(
                    title = titleText,
                    subtitle = subtitleText,
                    notificationAccessMissing = !nowPlaying.notificationAccessEnabled,
                    onRequestNotificationAccess = onRequestNotificationAccess,
                )

                ProgressRow(
                    positionMs = positionMs,
                    durationMs = nowPlaying.durationMs,
                )

                val canControl = nowPlaying.notificationAccessEnabled
                val onPlayPauseOrStart: () -> Unit = {
                    if (nowPlaying.hasActiveSession) {
                        onPlayPause()
                    } else {
                        onStartPlayback()
                    }
                }
                PlayerControls(
                    isPlaying = nowPlaying.isPlaying,
                    canControl = canControl,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPauseOrStart,
                    onNext = onNext,
                )
            }
        }
    }
}

private fun calculateArtworkSize(availableHeight: Dp, availableWidth: Dp): Dp {
    val reserved = ArtworkReservedHeight
    val candidate = availableHeight - reserved
    val widthCapped = if (candidate > availableWidth) availableWidth else candidate
    return widthCapped.coerceIn(ArtworkMinSize, ArtworkMaxSize)
}

private val ArtworkReservedHeight = 250.dp
private val ArtworkMinSize = 140.dp
private val ArtworkMaxSize = 380.dp

@Composable
private fun NowPlayingArtwork(
    artworkModel: Any?,
    placeholderPainter: ColorPainter,
    context: android.content.Context,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(ArtworkCorner))
            .background(scheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (artworkModel != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkModel)
                    .crossfade(true)
                    .size(Size(512, 512))
                    .build(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = placeholderPainter,
                error = placeholderPainter,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}

@Composable
private fun NowPlayingMeta(
    title: String,
    subtitle: String,
    notificationAccessMissing: Boolean,
    onRequestNotificationAccess: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.SemiBold,
            ),
            color = scheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = if (notificationAccessMissing) scheme.primary else scheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = if (notificationAccessMissing) {
                Modifier.clickable(onClick = onRequestNotificationAccess)
            } else {
                Modifier
            },
        )
    }
}

@Composable
private fun ProgressRow(
    positionMs: Long,
    durationMs: Long,
) {
    val scheme = MaterialTheme.colorScheme
    val safeDuration = durationMs.coerceAtLeast(0L)
    val safePosition = positionMs.coerceAtLeast(0L)
    val progress = if (safeDuration > 0L) {
        (safePosition.toFloat() / safeDuration.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val positionText = formatTrackDuration(safePosition)
    val durationText = formatTrackDuration(safeDuration)
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = scheme.primary,
            trackColor = scheme.surfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = positionText,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = durationText,
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    canControl: Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val prevDesc = stringResource(R.string.music_widget_prev)
    val playPauseDesc = stringResource(R.string.music_widget_play_pause)
    val nextDesc = stringResource(R.string.music_widget_next)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundControl(
            icon = Icons.Filled.SkipPrevious,
            contentDescription = prevDesc,
            size = 56.dp,
            onClick = onPrevious,
            enabled = canControl,
        )
        RoundControl(
            icon = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = playPauseDesc,
            size = 76.dp,
            onClick = onPlayPause,
            enabled = canControl,
            primary = true,
        )
        RoundControl(
            icon = Icons.Filled.SkipNext,
            contentDescription = nextDesc,
            size = 56.dp,
            onClick = onNext,
            enabled = canControl,
        )
    }
}

@Composable
private fun RoundControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: Dp,
    onClick: () -> Unit,
    enabled: Boolean,
    primary: Boolean = false,
) {
    val interaction = rememberSoftPressInteraction()
    val scheme = MaterialTheme.colorScheme
    val container = if (primary) scheme.primary else scheme.surfaceVariant
    val content = if (primary) scheme.onPrimary else scheme.onSurface
    Box(
        modifier = Modifier
            .size(size)
            .softPressScale(interaction)
            .clip(CircleShape)
            .background(if (enabled) container else container.copy(alpha = 0.5f))
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (enabled) content else content.copy(alpha = 0.6f),
            modifier = Modifier.size(size * 0.55f),
        )
    }
}

@Composable
private fun UpNextPanel(
    nowPlaying: NowPlayingUi,
    onSkipToQueueItem: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(PlayerCorner),
        colors = CardDefaults.cardColors(containerColor = scheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
        ) {
            Text(
                text = stringResource(R.string.music_player_section_up_next),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = scheme.primary,
            )
            Spacer(Modifier.height(8.dp))

            val showQueue = nowPlaying.notificationAccessEnabled &&
                nowPlaying.hasActiveSession &&
                nowPlaying.upNext.isNotEmpty()

            if (!showQueue) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = when {
                            !nowPlaying.notificationAccessEnabled ->
                                stringResource(R.string.music_player_notification_required)
                            !nowPlaying.hasActiveSession ->
                                stringResource(R.string.music_widget_nothing_playing)
                            else -> stringResource(R.string.music_player_empty_queue)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = nowPlaying.upNext,
                        key = { "${it.queueId}-${it.title}" },
                    ) { track ->
                        UpNextRow(
                            track = track,
                            onClick = { onSkipToQueueItem(track.queueId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun UpNextRow(
    track: MediaQueueItemUi,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }
    val scheme = MaterialTheme.colorScheme
    val surfaceVariant = scheme.surfaceVariant
    val placeholderPainter = remember(surfaceVariant) { ColorPainter(surfaceVariant) }
    val playQueuedDesc = stringResource(R.string.music_widget_play_queued_track)
    val canPlay = track.queueId != android.media.session.MediaSession.QueueItem.UNKNOWN_ID.toLong()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.55f))
            .semantics {
                contentDescription = if (canPlay) {
                    "$playQueuedDesc. ${track.title}"
                } else {
                    track.title
                }
            }
            .clickable(
                enabled = canPlay,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(scheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            if (track.artwork != null) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(track.artwork)
                        .crossfade(true)
                        .size(Size(128, 128))
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    placeholder = placeholderPainter,
                    error = placeholderPainter,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (canPlay) scheme.onSurface else scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (track.subtitle != null) {
                Text(
                    text = track.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
