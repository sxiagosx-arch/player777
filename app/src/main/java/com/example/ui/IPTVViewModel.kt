package com.example.ui

import android.app.Application
import android.util.Log
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.database.BlockedItem
import com.example.database.Favorite
import com.example.database.PlaylistAccount
import com.example.database.WatchHistory
import com.example.model.IPTVCategory
import com.example.model.IPTVChannel
import com.example.model.IPTVSeason
import com.example.model.IPTVSeries
import com.example.network.IPTVRepository
import com.example.ui.player.PlaybackBufferConfig
import com.example.ui.player.VideoQualityMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class Screen {
    SPLASH,
    LOGIN,
    HOME,
    LIVE_TV,
    MOVIES,
    SERIES,
    PLATFORMS,
    FAVORITES,
    HISTORY,
    PARENTAL_CONTROL,
    SETTINGS,
    ABOUT,
    DEVICE_SELECTION
}

sealed interface IPTVUiState {
    object Idle : IPTVUiState
    object Loading : IPTVUiState
    object Success : IPTVUiState
    data class Error(val message: String) : IPTVUiState
}

class IPTVViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = IPTVRepository(application)
    private val context = application.applicationContext

    // UI State & Flow Exposing
    private val _uiState = MutableStateFlow<IPTVUiState>(IPTVUiState.Idle)
    val uiState: StateFlow<IPTVUiState> = _uiState.asStateFlow()

    private val _deviceLayoutMode = MutableStateFlow("MOBILE")
    val deviceLayoutMode: StateFlow<String> = _deviceLayoutMode.asStateFlow()

    private val _currentScreen = MutableStateFlow(Screen.SPLASH)
    val currentScreen: StateFlow<Screen> = _currentScreen.asStateFlow()

    private val _isLoadingApp = MutableStateFlow(true)
    val isLoadingApp: StateFlow<Boolean> = _isLoadingApp.asStateFlow()

    private val _channels = MutableStateFlow<List<IPTVChannel>>(emptyList())
    val channels: StateFlow<List<IPTVChannel>> = _channels.asStateFlow()

    private val _categories = MutableStateFlow<List<IPTVCategory>>(emptyList())
    val categories: StateFlow<List<IPTVCategory>> = _categories.asStateFlow()

    private val _allCategories = MutableStateFlow<List<IPTVCategory>>(emptyList())
    val allCategories: StateFlow<List<IPTVCategory>> = _allCategories.asStateFlow()

    private val _seriesList = MutableStateFlow<List<IPTVSeries>>(emptyList())
    val seriesList: StateFlow<List<IPTVSeries>> = _seriesList.asStateFlow()

    // Dashboard-specific pre-calculated content
    private val _topMovies = MutableStateFlow<List<IPTVChannel>>(emptyList())
    val topMovies: StateFlow<List<IPTVChannel>> = _topMovies.asStateFlow()

    private val _topSeries = MutableStateFlow<List<IPTVSeries>>(emptyList())
    val topSeries: StateFlow<List<IPTVSeries>> = _topSeries.asStateFlow()

    private val _liveSpotlight = MutableStateFlow<List<IPTVChannel>>(emptyList())
    val liveSpotlight: StateFlow<List<IPTVChannel>> = _liveSpotlight.asStateFlow()

    private val _banners = MutableStateFlow<List<Any>>(emptyList())
    val banners: StateFlow<List<Any>> = _banners.asStateFlow()

    private val _selectedChannel = MutableStateFlow<IPTVChannel?>(null)
    val selectedChannel: StateFlow<IPTVChannel?> = _selectedChannel.asStateFlow()

    private val _selectedSeries = MutableStateFlow<IPTVSeries?>(null)
    val selectedSeries: StateFlow<IPTVSeries?> = _selectedSeries.asStateFlow()

    private val _seriesSeasons = MutableStateFlow<List<IPTVSeason>>(emptyList())
    val seriesSeasons: StateFlow<List<IPTVSeason>> = _seriesSeasons.asStateFlow()
    private val _currentEPG = MutableStateFlow<List<com.example.model.EPGProgram>>(emptyList())
    val currentEPG: StateFlow<List<com.example.model.EPGProgram>> = _currentEPG.asStateFlow()

    private val _isDrawerOpen = MutableStateFlow(false)
    val isDrawerOpen: StateFlow<Boolean> = _isDrawerOpen.asStateFlow()

    val accounts: StateFlow<List<PlaylistAccount>> = repository.accountsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val activeAccount: StateFlow<PlaylistAccount?> = repository.activeAccountFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _favorites = MutableStateFlow<List<Favorite>>(emptyList())
    val favorites: StateFlow<List<Favorite>> = _favorites.asStateFlow()

    private val _watchHistory = MutableStateFlow<List<WatchHistory>>(emptyList())
    val watchHistory: StateFlow<List<WatchHistory>> = _watchHistory.asStateFlow()

    private val _blockedItems = MutableStateFlow<List<BlockedItem>>(emptyList())
    val blockedItems: StateFlow<List<BlockedItem>> = _blockedItems.asStateFlow()

    // Agora vai pegar do servidor!
    private val _accountExpiration = MutableStateFlow("Desconhecido")
    val accountExpiration: StateFlow<String> = _accountExpiration.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedPlatformFilter = MutableStateFlow<String?>(null)
    val selectedPlatformFilter: StateFlow<String?> = _selectedPlatformFilter.asStateFlow()
    fun setPlatformFilter(filter: String?) { _selectedPlatformFilter.value = filter }

    private val backStack = mutableListOf<Screen>()

    private val _blockAdult = MutableStateFlow(false)
    val blockAdult: StateFlow<Boolean> = _blockAdult.asStateFlow()

    private val _hardwareDecoding = MutableStateFlow(true)
    val hardwareDecoding: StateFlow<Boolean> = _hardwareDecoding.asStateFlow()

    private val _videoQualityMode = MutableStateFlow(VideoQualityMode.AUTO)
    val videoQualityMode: StateFlow<VideoQualityMode> = _videoQualityMode.asStateFlow()

    private val _bufferSize = MutableStateFlow("Médio (Padrão)")
    val bufferSize: StateFlow<String> = _bufferSize.asStateFlow()

    private val _bufferMaxSeconds = MutableStateFlow(50)
    val bufferMaxSeconds: StateFlow<Int> = _bufferMaxSeconds.asStateFlow()

    private val _bufferStartSeconds = MutableStateFlow(1)
    val bufferStartSeconds: StateFlow<Int> = _bufferStartSeconds.asStateFlow()

    fun setBlockAdult(block: Boolean) {
        _blockAdult.value = block
        viewModelScope.launch {
            repository.setSetting("blockAdult", block.toString())
            refreshContents()
        }
    }

    fun setHardwareDecoding(enabled: Boolean) {
        _hardwareDecoding.value = enabled
        viewModelScope.launch { repository.setSetting("hardwareDecoding", enabled.toString()) }
    }

    fun setVideoQualityMode(mode: VideoQualityMode) {
        _videoQualityMode.value = mode
        viewModelScope.launch { repository.setSetting("videoQualityMode", mode.name) }
    }

    fun setBufferSize(size: String) {
        _bufferSize.value = size
        val maxSeconds = when (size) {
            "Grande (Reduz engasgos)" -> 90
            "Pequeno (Troca rápida)" -> 10
            else -> 50
        }
        setBufferMaxSeconds(maxSeconds)
        viewModelScope.launch { repository.setSetting("bufferSize", size) }
    }

    fun setBufferMaxSeconds(seconds: Int) {
        val value = seconds.coerceIn(
            PlaybackBufferConfig.MIN_MAX_SECONDS,
            PlaybackBufferConfig.MAX_MAX_SECONDS
        )
        _bufferMaxSeconds.value = value
        viewModelScope.launch { repository.setSetting("bufferMaxSeconds", value.toString()) }
    }

    fun setBufferStartSeconds(seconds: Int) {
        val value = seconds.coerceIn(
            PlaybackBufferConfig.MIN_START_SECONDS,
            PlaybackBufferConfig.MAX_START_SECONDS
        )
        _bufferStartSeconds.value = value
        viewModelScope.launch { repository.setSetting("bufferStartSeconds", value.toString()) }
    }

    init {
        viewModelScope.launch {
            _deviceLayoutMode.value = repository.getSetting("deviceLayoutMode", "UNSET")
            _blockAdult.value = repository.getSetting("blockAdult", "false").toBoolean()
            _hardwareDecoding.value = repository.getSetting("hardwareDecoding", "true").toBoolean()
            _videoQualityMode.value = VideoQualityMode.fromStorage(
                repository.getSetting("videoQualityMode", VideoQualityMode.AUTO.name)
            )
            _bufferSize.value = repository.getSetting("bufferSize", "Médio (Padrão)")
            val legacyMaxSeconds = when (_bufferSize.value) {
                "Grande (Reduz engasgos)" -> 90
                "Pequeno (Troca rápida)" -> 10
                else -> 50
            }
            _bufferMaxSeconds.value = repository
                .getSetting("bufferMaxSeconds", legacyMaxSeconds.toString())
                .toIntOrNull()
                ?.coerceIn(PlaybackBufferConfig.MIN_MAX_SECONDS, PlaybackBufferConfig.MAX_MAX_SECONDS)
                ?: legacyMaxSeconds
            _bufferStartSeconds.value = repository
                .getSetting("bufferStartSeconds", "1")
                .toIntOrNull()
                ?.coerceIn(PlaybackBufferConfig.MIN_START_SECONDS, PlaybackBufferConfig.MAX_START_SECONDS)
                ?: 1
        }

        viewModelScope.launch {
            try {
                val storedMode = repository.getSetting("deviceLayoutMode", "UNSET")
                _deviceLayoutMode.value = if (storedMode == "UNSET") "MOBILE" else storedMode

                val active = repository.getActiveAccount()
                
                if (storedMode == "UNSET") {
                    _currentScreen.value = Screen.DEVICE_SELECTION
                } else if (active != null) {
                    val hasCache = repository.loadActivePlaylist(forceRefresh = false)
                    if (hasCache) {
                        refreshContents()
                        _currentScreen.value = Screen.HOME
                        _uiState.value = IPTVUiState.Success
                    }
                } else {
                    _currentScreen.value = Screen.LOGIN
                }
                
                _isLoadingApp.value = false

                // 2. VALIDAÇÃO EM SEGUNDO PLANO
                if (active != null) {
                    viewModelScope.launch {
                        val isServerActive = verificarStatusServidor()
                        if (isServerActive) {
                            repository.loadActivePlaylist(forceRefresh = false)
                            refreshContents()
                        } else {
                            _uiState.value = IPTVUiState.Error("Assinatura Pausada. Regularize para voltar.")
                            _currentScreen.value = Screen.LOGIN
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _currentScreen.value = Screen.LOGIN
            }
        }

        // Syncs
        viewModelScope.launch {
            activeAccount.collectLatest { acc ->
                if (acc != null) repository.getFavoritesFlow(acc.id).collect { _favorites.value = it }
            }
        }
        viewModelScope.launch {
            activeAccount.collectLatest { acc ->
                if (acc != null) repository.getWatchHistoryFlow(acc.id).collect { _watchHistory.value = it }
            }
        }
        viewModelScope.launch {
            activeAccount.collectLatest { acc ->
                if (acc != null) {
                    repository.getBlockedItemsFlow(acc.id).collect { list ->
                        _blockedItems.value = list
                        refreshContents()
                    }
                }
            }
        }
    }

    // NOVA FUNÇÃO: PERGUNTA PARA O SEU SITE SE A LISTA DELE AINDA ESTÁ ATIVA
    private suspend fun verificarStatusServidor(): Boolean {
        return withContext(Dispatchers.IO) {
            var connection: HttpURLConnection? = null
            try {
                val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "UNKNOWN_ID"
                val deviceId = androidId.uppercase().take(8)
                val apiUrl = "https://lojavip.net/api/verificar_mac/$deviceId"

                val url = URL(apiUrl)
                connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                connection.readTimeout = 3000

                val responseCode = connection.responseCode
                val response = (if (responseCode in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    .orEmpty()

                // A temporary 4xx/5xx or an empty response must not erase a cached
                // playlist. Only an explicit inactive status is a subscription decision.
                if (responseCode !in 200..299 || response.isBlank()) return@withContext true

                if (response.isNotBlank()) {
                    val json = JSONObject(response)
                    val status = json.optString("status").trim().lowercase()

                    if (status in setOf("sucesso", "success", "ativo", "active")) {
                        // Puxa a validade direto do seu servidor
                        _accountExpiration.value = json.optString("vencimento", "N/A")
                        return@withContext true
                    }

                    if (status in setOf("erro", "error", "bloqueado", "blocked", "pausado", "paused", "expirado", "expired")) {
                        return@withContext false
                    }
                }
                // Preserve access for new/unknown response formats until the endpoint
                // returns a documented inactive status.
                return@withContext true
            } catch (e: Exception) {
                // Se der erro de internet, permite passar (já que a lista tá salva)
                return@withContext true
            } finally {
                connection?.disconnect()
            }
        }
    }

    fun navigateTo(screen: Screen) {
        if (_currentScreen.value != screen) {
            backStack.add(_currentScreen.value)
            _currentScreen.value = screen
        }
        _isDrawerOpen.value = false
    }

    fun navigateBack() {
        if (backStack.isNotEmpty()) {
            _currentScreen.value = backStack.removeAt(backStack.size - 1)
        }
    }

    fun toggleDrawer() {
        _isDrawerOpen.value = !_isDrawerOpen.value
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectPlatformFilter(platform: String?) {
        _selectedPlatformFilter.value = if (_selectedPlatformFilter.value == platform) null else platform
    }

    private suspend fun refreshContents() {
        try {
            val block = _blockAdult.value
            val allChannels = repository.getChannels().toList()
            val allCategories = repository.getCategories().toList()
            val allSeries = repository.getSeries().toList()
            val hiddenCategoryIds = _blockedItems.value
                .asSequence()
                .filter { it.type == "HIDDEN_CATEGORY" || it.type == "CATEGORY" }
                .map { it.blockId }
                .toSet()

            val (filteredCats, filteredChans, filteredSers) = withContext(Dispatchers.Default) {
                var cats = allCategories
                var chans = allChannels
                var sers = allSeries

                if (block) {
                    val adultWords = listOf("adult", "+18", "18+", "xxx", "porn", "sex", "erótico", "erotico", "privé", "prive")
                    val categoryNames = allCategories.associate { it.id to it.name }
                    fun isAdult(value: String): Boolean = adultWords.any { word -> value.contains(word, ignoreCase = true) }

                    cats = cats.filter { !isAdult(it.name) }
                    chans = chans.filter { ch ->
                        !isAdult(ch.name) && !isAdult(categoryNames[ch.categoryId].orEmpty())
                    }
                    sers = sers.filter { series ->
                        !isAdult(series.name) && !isAdult(categoryNames[series.categoryId].orEmpty())
                    }
                }
                Triple(cats, chans, sers)
            }

            _allCategories.value = filteredCats
            _categories.value = filteredCats.filterNot { it.id in hiddenCategoryIds }
            val finalChannels = filteredChans.filterNot { it.categoryId in hiddenCategoryIds }
            val finalSeries = filteredSers.filterNot { it.categoryId in hiddenCategoryIds }

            _channels.value = finalChannels
            _seriesList.value = finalSeries

            // Pre-calculate Dashboard Content on Background
            withContext(Dispatchers.Default) {
                val dashboardItemCount = 8
                
                val live = finalChannels.asSequence()
                    .filter { it.type == "LIVE" }
                    .take(dashboardItemCount)
                    .toList()
                
                val movies = finalChannels.asSequence()
                    .filter { it.type == "MOVIE" }
                    .sortedByDescending { featuredPriority(it.name, it.categoryName, it.rating) }
                    .take(dashboardItemCount)
                    .toList()
                
                val spotlightSeries = finalSeries.asSequence()
                    .sortedByDescending { featuredPriority(it.name, it.categoryName, it.rating) }
                    .take(dashboardItemCount)
                    .toList()

                val bannerList = mutableListOf<Any>()
                finalChannels.filter { it.type == "MOVIE" && it.logo.isNotEmpty() }.take(2).let { bannerList.addAll(it) }
                finalSeries.filter { it.cover.isNotEmpty() }.take(2).let { bannerList.addAll(it) }
                finalChannels.filter { it.type == "LIVE" && it.logo.isNotEmpty() }.take(1).let { bannerList.addAll(it) }

                _liveSpotlight.value = live
                _topMovies.value = movies
                _topSeries.value = spotlightSeries
                _banners.value = bannerList
            }
        } catch (error: Throwable) {
            Log.e("IPTVViewModel", "Não foi possível atualizar o catálogo", error)
        }
    }

    private val diacriticsRegex = "\\p{Mn}+".toRegex()
    private val ratingRegex = "\\d+(?:[.,]\\d+)?".toRegex()

    private fun String.normalizedForSearch(): String = java.text.Normalizer
        .normalize(this, java.text.Normalizer.Form.NFD)
        .replace(diacriticsRegex, "")
        .lowercase()

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

    fun addAccount(account: PlaylistAccount) {
        viewModelScope.launch {
            _uiState.value = IPTVUiState.Loading
            repository.saveAccount(account.copy(isActive = true))
            val loaded = repository.loadActivePlaylist()
            if (loaded) {
                // A validade já vem pela verificação, não precisa puxar do repository
                refreshContents()
                _currentScreen.value = Screen.HOME
                _uiState.value = IPTVUiState.Success
            } else {
                _uiState.value = IPTVUiState.Error("Falha ao carregar a lista IPTV. Verifique a URL ou credenciais.")
            }
        }
    }

    fun selectAccount(accountId: Int) {
        viewModelScope.launch {
            _uiState.value = IPTVUiState.Loading
            repository.selectAccount(accountId)
            val loaded = repository.loadActivePlaylist()
            if (loaded) {
                refreshContents()
                _currentScreen.value = Screen.HOME
                _uiState.value = IPTVUiState.Success
            } else {
                _uiState.value = IPTVUiState.Error("Falha ao carregar a conta selecionada.")
            }
        }
    }

    fun deleteAccount(accountId: Int) {
        viewModelScope.launch {
            repository.deleteAccount(accountId)
            val active = repository.getActiveAccount()
            if (active == null) {
                _currentScreen.value = Screen.LOGIN
            }
        }
    }

    fun tryDemo() {
        viewModelScope.launch {
            _uiState.value = IPTVUiState.Loading
            val demoAcc = PlaylistAccount(name = "Lista de Teste Unlock", type = "DEMO", isActive = true)
            repository.saveAccount(demoAcc)
            val loaded = repository.loadActivePlaylist()
            if (loaded) {
                refreshContents()
                _currentScreen.value = Screen.HOME
                _uiState.value = IPTVUiState.Success
            } else {
                _uiState.value = IPTVUiState.Error("Falha ao carregar lista de demonstração.")
            }
        }
    }

    fun showError(message: String) { _uiState.value = IPTVUiState.Error(message) }
    fun clearError() { _uiState.value = IPTVUiState.Idle }

    fun selectChannel(channel: IPTVChannel?) {
        _currentEPG.value = emptyList()
        if (channel != null) {
            if (channel.type == "LIVE") {
                viewModelScope.launch { _currentEPG.value = repository.fetchEPG(channel.id) }
            } else if (channel.type == "SERIES" && channel.seriesId.isNotEmpty()) {
                viewModelScope.launch { _seriesSeasons.value = repository.fetchSeriesSeasonsAndEpisodes(channel.seriesId) }
            }
        }
        _selectedChannel.value = channel
    }

    fun selectSeries(series: IPTVSeries?) {
        _selectedSeries.value = series
        if (series != null) {
            viewModelScope.launch { _seriesSeasons.value = repository.fetchSeriesSeasonsAndEpisodes(series.id) }
        } else {
            _seriesSeasons.value = emptyList()
        }
    }

    fun saveWatchProgress(channel: IPTVChannel, currentPos: Long, totalDuration: Long) {
        viewModelScope.launch { repository.saveWatchProgress(channel, currentPos, totalDuration) }
    }

    fun toggleFavorite(channel: IPTVChannel) { viewModelScope.launch { repository.toggleFavorite(channel) } }
    fun toggleFavoriteSeries(series: IPTVSeries) { viewModelScope.launch { repository.toggleFavoriteSeries(series) } }
    fun toggleCategoryBlock(categoryId: String) { viewModelScope.launch { repository.toggleCategoryBlock(categoryId) } }
    fun toggleCategoryHidden(categoryId: String) { viewModelScope.launch { repository.toggleCategoryHidden(categoryId) } }

    fun isCategoryBlocked(categoryId: String, categoryName: String): Boolean {
        if (_blockAdult.value) {
            val adultWords = listOf("adult", "+18", "18+", "xxx", "porn", "sex", "erótico", "erotico", "privé", "prive")
            val isAdult = adultWords.any { word -> categoryName.contains(word, ignoreCase = true) }
            if (isAdult) return true
        }
        return _blockedItems.value.any { it.blockId == categoryId && it.type == "CATEGORY" }
    }

    fun isCategoryHidden(categoryId: String): Boolean {
        return _blockedItems.value.any { it.blockId == categoryId && it.type == "HIDDEN_CATEGORY" }
    }

    fun setParentalPin(pin: String, callback: () -> Unit) {
        viewModelScope.launch { repository.setParentalPin(pin); callback() }
    }

    fun setDeviceLayoutMode(mode: String) {
        _deviceLayoutMode.value = mode
        viewModelScope.launch {
            repository.setSetting("deviceLayoutMode", mode)
            if (_currentScreen.value == Screen.DEVICE_SELECTION) {
                val active = repository.getActiveAccount()
                if (active != null) {
                    _currentScreen.value = Screen.HOME
                } else {
                    _currentScreen.value = Screen.LOGIN
                }
            }
        }
    }

    fun checkParentalPin(inputPin: String, onSuccess: () -> Unit, onFailure: () -> Unit) {
        viewModelScope.launch {
            val pin = repository.getParentalPin()
            if (pin == inputPin || (pin == null && inputPin == "0000")) onSuccess() else onFailure()
        }
    }

    fun isParentalPinSet(callback: (Boolean) -> Unit) {
        viewModelScope.launch { callback(repository.getParentalPin() != null) }
    }

    fun getAdjacentChannels(channel: IPTVChannel): List<IPTVChannel> {
        if (channel.type == "LIVE") return _channels.value.filter { it.categoryId == channel.categoryId && it.type == "LIVE" }
        else if (channel.type == "SERIES") return _seriesSeasons.value.flatMap { it.episodes }
        return emptyList()
    }
}
