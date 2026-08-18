package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.database.Favorite
import com.example.database.WatchHistory
import com.example.model.IPTVChannel
import com.example.model.IPTVSeries
import com.example.ui.IPTVUiState
import com.example.ui.IPTVViewModel
import com.example.ui.Screen
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.Normalizer

@Composable
fun MainDashboard(viewModel: IPTVViewModel) {
    val channels by viewModel.channels.collectAsState()
    val seriesList by viewModel.seriesList.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val selectedLayoutMode by viewModel.deviceLayoutMode.collectAsState()

    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val isTv = when (selectedLayoutMode) {
        "TV" -> true
        "MOBILE" -> false
        else -> com.example.util.DeviceUtil.isTv(context)
    }
    var globalSearchQuery by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MatteBlack)
                .testTag("main_dashboard_container"),
            state = androidx.compose.foundation.lazy.rememberLazyListState()
        ) {
            item {
                // Welcome and active list row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.example.R.drawable.ic_logo),
                            contentDescription = "Logo",
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            if (isTv) {
                                Text(
                                    text = "Conta Ativa:",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = viewModel.activeAccount.collectAsState().value?.username ?: "Nenhuma",
                                    color = NeonGreen,
                                    fontFamily = com.example.ui.theme.RussoOne,
                                    fontSize = 16.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Exp: " + viewModel.accountExpiration.collectAsState().value,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.sp
                                )
                            } else {
                                Text(
                                    text = "BEM-VINDO AO",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Unlock Player",
                                    color = NeonGreen,
                                    fontFamily = com.example.ui.theme.RussoOne,
                                    fontSize = 20.sp,
                                    letterSpacing = 1.5.sp
                                )
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IconButton(
                            onClick = { viewModel.navigateTo(Screen.SETTINGS) },
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Charcoal)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "Configurações",
                                tint = Color.White
                            )
                        }
                    }
                }
            }

            item {
                // Global Search Bar
                var isSearchEditable by remember { mutableStateOf(false) }
                val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
                OutlinedTextField(
                    value = globalSearchQuery,
                    onValueChange = { globalSearchQuery = it },
                    readOnly = if (isTv) !isSearchEditable else false,
                    placeholder = { Text("Pesquisar filmes, séries e canais...", color = Color.Gray) },
                    leadingIcon = { Icon(imageVector = Icons.Rounded.Search, contentDescription = "Search", tint = NeonGreen) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonGreen,
                        unfocusedBorderColor = Charcoal,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = NeonGreen,
                        focusedContainerColor = Charcoal,
                        unfocusedContainerColor = Charcoal
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .then(if (isTv) Modifier.focusRequester(focusRequester).onFocusChanged { if (!it.isFocused) isSearchEditable = false }.clickable { isSearchEditable = true; focusRequester.requestFocus() } else Modifier)
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            if (globalSearchQuery.isNotEmpty()) {
                item {
                    SearchResultsContent(globalSearchQuery, channels, seriesList, viewModel)
                }
            } else {
                item {
                    // Cinematic Rotating Banner
                    RotatingBanner(channels, seriesList, viewModel, isTv)
                    Spacer(modifier = Modifier.height(24.dp))
                }

                if (watchHistory.isNotEmpty()) {
                    item {
                        DashboardSectionHeader("Continue Assistindo") { viewModel.navigateTo(Screen.HISTORY) }
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(items = watchHistory, key = { it.streamId }) { hist ->
                                ContinueWatchingCard(historyItem = hist, modifier = Modifier.width(200.dp).animateItem()) {
                                    val ch = channels.find { it.id == hist.streamId }
                                        ?: IPTVChannel(
                                            id = hist.streamId, 
                                            name = hist.name, 
                                            url = hist.streamUrl, 
                                            logo = hist.logoUrl, 
                                            type = hist.type,
                                            seriesId = hist.seriesId,
                                            seasonNumber = hist.seasonNumber,
                                            episodeNumber = hist.episodeNumber
                                        )
                                    viewModel.selectChannel(ch)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                if (favorites.isNotEmpty()) {
                    item {
                        DashboardSectionHeader("Meus Favoritos") { /* Navigate to favorites if created */ }
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(favorites) { fav ->
                                FavoriteGridCard(fav = fav) {
                                    if (fav.type == "SERIES") {
                                        val ser = seriesList.find { it.id == fav.streamId }
                                            ?: IPTVSeries(id = fav.streamId, name = fav.name, cover = fav.logoUrl, categoryId = fav.categoryId)
                                        viewModel.selectSeries(ser)
                                        viewModel.navigateTo(Screen.SERIES)
                                    } else {
                                        val ch = channels.find { it.id == fav.streamId }
                                            ?: IPTVChannel(id = fav.streamId, name = fav.name, url = fav.streamUrl, logo = fav.logoUrl, type = fav.type)
                                        viewModel.selectChannel(ch)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                val dashboardItemCount = if (isTv) 12 else 8

                // Canais em Alta
                val liveChannels = channels.asSequence().filter { it.type == "LIVE" }.take(dashboardItemCount).toList()
                if (liveChannels.isNotEmpty()) {
                    item {
                        DashboardSectionHeader("Canais em Alta") { viewModel.navigateTo(Screen.LIVE_TV) }
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(liveChannels) { ch ->
                                LiveSpotlightCard(
                                    channel = ch,
                                    modifier = Modifier.width(if (isTv) 210.dp else 160.dp).animateItem()
                                ) { viewModel.selectChannel(ch) }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Top Filmes
                val movies = channels.asSequence().filter { it.type == "MOVIE" }
                    .sortedByDescending { featuredPriority(it.name, it.categoryName, it.rating) }
                    .take(dashboardItemCount).toList()
                if (movies.isNotEmpty()) {
                    item {
                        DashboardSectionHeader("Top Filmes") { viewModel.navigateTo(Screen.MOVIES) }
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(movies) { mv ->
                                MovieSpotlightCard(
                                    movie = mv,
                                    modifier = Modifier.width(if (isTv) 170.dp else 140.dp).animateItem()
                                ) { viewModel.selectChannel(mv) }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }

                // Top Séries
                val spotlightSeries = seriesList.asSequence()
                    .sortedByDescending { featuredPriority(it.name, it.categoryName, it.rating) }
                    .take(dashboardItemCount).toList()
                if (spotlightSeries.isNotEmpty()) {
                    item {
                        DashboardSectionHeader("Top Séries") { viewModel.navigateTo(Screen.SERIES) }
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(spotlightSeries) { ser ->
                                SeriesSpotlightCard(
                                    series = ser,
                                    modifier = Modifier.width(if (isTv) 170.dp else 140.dp).animateItem()
                                ) {
                                    viewModel.selectSeries(ser)
                                    viewModel.navigateTo(Screen.SERIES)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            }
        }
        NeonLoadingOverlay(uiState is IPTVUiState.Loading)
    }
}

@Composable
fun DashboardSectionHeader(title: String, onMoreClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        IconButton(onClick = onMoreClick, modifier = Modifier.size(24.dp)) {
            Icon(imageVector = Icons.Rounded.ArrowForward, contentDescription = "Mais", tint = NeonGreen)
        }
    }
}

@Composable
fun RotatingBanner(
    channels: List<IPTVChannel>,
    series: List<IPTVSeries>,
    viewModel: IPTVViewModel,
    isTv: Boolean = false
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    
    val banners = remember(channels, series) {
        val list = mutableListOf<Any>()
        channels.filter { it.type == "MOVIE" && it.logo.isNotEmpty() }.take(2).let { list.addAll(it) }
        series.filter { it.cover.isNotEmpty() }.take(2).let { list.addAll(it) }
        channels.filter { it.type == "LIVE" && it.logo.isNotEmpty() }.take(1).let { list.addAll(it) }
        list
    }

    if (banners.isEmpty()) return

    LaunchedEffect(banners.size) {
        while (true) {
            delay(5000)
            currentIndex = (currentIndex + 1) % banners.size
        }
    }

    val currentItem = banners.getOrNull(currentIndex) ?: return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTv) 300.dp else 220.dp)
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, NeonGreenGlow, RoundedCornerShape(16.dp))
            .clickable {
                if (currentItem is IPTVChannel) viewModel.selectChannel(currentItem)
                else if (currentItem is IPTVSeries) {
                    viewModel.selectSeries(currentItem)
                    viewModel.navigateTo(Screen.SERIES)
                }
            }
    ) {
        val imageUrl = if (currentItem is IPTVChannel) currentItem.logo else (currentItem as IPTVSeries).cover
        val title = if (currentItem is IPTVChannel) currentItem.name else (currentItem as IPTVSeries).name
        val subtitle = if (currentItem is IPTVChannel) "Em Destaque" else "Série Recomendada"

        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
        )
        
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Text(
                text = subtitle,
                color = NeonGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(NeonGreenDim, RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Black,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun StreamingShortcutsRow(viewModel: IPTVViewModel) {
    val shortcuts = listOf(
        Pair("Netflix", Color(0xFFE50914)),
        Pair("Prime", Color(0xFF00A8E1)),
        Pair("Disney+", Color(0xFF113CCF)),
        Pair("Globoplay", Color(0xFFFF0055)),
        Pair("HBO Max", Color(0xFF5B00C5)),
        Pair("Apple TV", Color(0xFF555555)),
        Pair("Paramount", Color(0xFF0064FF))
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        shortcuts.forEach { (name, color) ->
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Brush.linearGradient(listOf(Charcoal, color.copy(alpha = 0.3f))))
                    .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .clickable {
                        viewModel.setPlatformFilter(name)
                        viewModel.navigateTo(Screen.MOVIES)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name,
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp
                )
            }
        }
    }
}

@Composable
fun SearchResultsContent(q: String, channels: List<IPTVChannel>, seriesList: List<IPTVSeries>, viewModel: IPTVViewModel) {
    val query = q.trim().lowercase()
    val filteredLive = channels.filter { it.type == "LIVE" && it.name.lowercase().contains(query) }
    val filteredMovies = channels.filter { it.type == "MOVIE" && it.name.lowercase().contains(query) }
    val filteredSeries = seriesList.filter { it.name.lowercase().contains(query) }

    if (filteredLive.isEmpty() && filteredMovies.isEmpty() && filteredSeries.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Nenhum resultado encontrado para '$q'", color = Color.Gray, fontSize = 14.sp)
        }
        return
    }

    if (filteredLive.isNotEmpty()) {
        DashboardSectionHeader("Canais Encontrados") { }
        LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filteredLive) { ch ->
                LiveSpotlightCard(ch, modifier = Modifier.width(160.dp)) { viewModel.selectChannel(ch) }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (filteredMovies.isNotEmpty()) {
        DashboardSectionHeader("Filmes Encontrados") { }
        LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filteredMovies) { mv ->
                MovieSpotlightCard(mv, modifier = Modifier.width(140.dp)) { viewModel.selectChannel(mv) }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (filteredSeries.isNotEmpty()) {
        DashboardSectionHeader("Séries Encontradas") { }
        LazyRow(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(filteredSeries) { ser ->
                SeriesSpotlightCard(ser, modifier = Modifier.width(140.dp)) {
                    viewModel.selectSeries(ser)
                    viewModel.navigateTo(Screen.SERIES)
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ContinueWatchingCard(historyItem: WatchHistory, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Charcoal)
            .clickable { onClick() }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                AsyncImage(
                    model = historyItem.logoUrl,
                    contentDescription = historyItem.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)))))
                Icon(
                    imageVector = Icons.Rounded.PlayCircle,
                    contentDescription = "Play",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.align(Alignment.Center).size(36.dp)
                )
                
                // Progress Bar
                if (historyItem.durationMs > 0) {
                    val progress = (historyItem.positionMs.toFloat() / historyItem.durationMs.toFloat()).coerceIn(0f, 1f)
                    Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(4.dp).background(Color.DarkGray)) {
                        Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(progress).background(NeonGreen))
                    }
                }
            }
            Text(
                text = historyItem.name,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun FavoriteGridCard(fav: Favorite, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(110.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Charcoal)
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AsyncImage(
                model = fav.logoUrl,
                contentDescription = fav.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )
            Text(
                text = fav.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun LiveSpotlightCard(channel: IPTVChannel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Charcoal),
        shape = RoundedCornerShape(12.dp),
        border = if (isFocused) androidx.compose.foundation.BorderStroke(2.dp, NeonGreen) else null
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MatteBlack),
                contentAlignment = Alignment.Center
            ) {
                if (channel.logo.isNotEmpty()) {
                    AsyncImage(
                        model = channel.logo,
                        contentDescription = channel.name,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(12.dp)
                    )
                } else {
                    Icon(imageVector = Icons.Rounded.Tv, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(32.dp))
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .background(Color.Red, RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Text(text = "AO VIVO", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Black)
                }
            }
            Text(
                text = channel.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                modifier = Modifier.padding(8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MovieSpotlightCard(movie: IPTVChannel, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Charcoal),
        shape = RoundedCornerShape(12.dp),
        border = if (isFocused) androidx.compose.foundation.BorderStroke(2.dp, NeonGreen) else null
    ) {
        Column {
            AsyncImage(
                model = movie.logo,
                contentDescription = movie.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
            )
            Text(
                text = movie.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SeriesSpotlightCard(series: IPTVSeries, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var isFocused by remember { mutableStateOf(false) }
    Card(
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .focusable()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Charcoal),
        shape = RoundedCornerShape(12.dp),
        border = if (isFocused) androidx.compose.foundation.BorderStroke(2.dp, NeonGreen) else null
    ) {
        Column {
            AsyncImage(
                model = series.cover,
                contentDescription = series.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)
            )
            Text(
                text = series.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(6.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val diacriticsRegex = "\\p{Mn}+".toRegex()
private val ratingRegex = "\\d+(?:[.,]\\d+)?".toRegex()

private fun String.normalizedForSearch(): String = Normalizer
    .normalize(this, Normalizer.Form.NFD)
    .replace(diacriticsRegex, "")
    .lowercase()

private fun IPTVChannel.isSportsContent(): Boolean {
    val searchable = "$categoryName $name".normalizedForSearch()
    val sportsKeywords = listOf(
        "jogo", "jogos", "partida", "esporte", "sport", "futebol", "premiere",
        "espn", "sportv", "combate", "ufc", "nba", "nfl", "champions",
        "libertadores", "brasileirao", "campeonato"
    )
    return sportsKeywords.any(searchable::contains)
}

private fun IPTVChannel.sportsPriority(): Double {
    val searchable = "$categoryName $name".normalizedForSearch()
    val categoryBoost = when {
        "jogos do dia" in searchable -> 1_000.0
        "jogo" in searchable || "partida" in searchable -> 800.0
        "futebol" in searchable || "champions" in searchable || "libertadores" in searchable -> 600.0
        else -> 300.0
    }
    return categoryBoost + parseRating(rating)
}

private fun featuredPriority(name: String, categoryName: String, rating: String): Double {
    val searchable = "$categoryName $name".normalizedForSearch()
    val categoryBoost = when {
        "top filme" in searchable || "top serie" in searchable -> 1_000.0
        "mais assistid" in searchable || "popular" in searchable -> 800.0
        "destaque" in searchable || "lancamento" in searchable || "recomendad" in searchable -> 600.0
        else -> 0.0
    }
    return categoryBoost + parseRating(rating)
}

private fun parseRating(value: String): Double = ratingRegex
    .find(value)
    ?.value
    ?.replace(',', '.')
    ?.toDoubleOrNull()
    ?: 0.0
