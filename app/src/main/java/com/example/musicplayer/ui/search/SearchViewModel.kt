package com.example.musicplayer.ui.search

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicplayer.MusicPlayerApplication
import com.example.musicplayer.scraper.DownloadWorker
import com.example.musicplayer.scraper.SearchEngine
import com.example.musicplayer.scraper.SearchResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel de la pantalla de búsqueda en YouTube.
 *
 * Orquesta la búsqueda con [SearchEngine] (NewPipeExtractor) y expone el
 * resultado como un [StateFlow] tipado para la UI.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    sealed interface SearchUiState {
        data object Idle : SearchUiState
        data object Loading : SearchUiState
        data class Results(val query: String, val results: List<SearchResult>) : SearchUiState
        data class NoResults(val query: String) : SearchUiState
        data object Error : SearchUiState
    }

    private val searchEngine = SearchEngine()

    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    /** Busca en YouTube la consulta indicada. */
    fun search(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            _uiState.value = SearchUiState.Loading
            _uiState.value = runCatching { searchEngine.search(query) }
                .fold(
                    onSuccess = { results ->
                        if (results.isEmpty()) {
                            SearchUiState.NoResults(query)
                        } else {
                            SearchUiState.Results(query, results)
                        }
                    },
                    onFailure = { SearchUiState.Error },
                )
        }
    }

    /** Descarga un resultado en segundo plano (WorkManager). */
    fun download(result: SearchResult) {
        DownloadWorker.start(getApplication(), result.url)
    }
}