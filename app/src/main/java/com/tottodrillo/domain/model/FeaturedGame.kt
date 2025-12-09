package com.tottodrillo.domain.model

/**
 * Modello per un gioco in evidenza da MobyGames
 */
data class FeaturedGame(
    val title: String, // Titolo del gioco (es. "Spongebob Patty Pursuit 2")
    val imageUrl: String?, // URL dell'immagine dal feed
    val gameUrl: String, // URL completo del gioco (es. "https://www.mobygames.com/game/251332/spongebob-patty-pursuit-2/")
    val searchQuery: String // Query di ricerca da usare (es. "Spongebob Patty Pursuit 2")
)

