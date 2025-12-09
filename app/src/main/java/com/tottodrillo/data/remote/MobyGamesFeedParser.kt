package com.tottodrillo.data.remote

import com.tottodrillo.domain.model.FeaturedGame
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/**
 * Parser per il feed Atom di MobyGames
 */
object MobyGamesFeedParser {
    private const val MOBYGAMES_FEED_URL = "https://www.mobygames.com/feed/most_researched_games.atom"
    
    /**
     * URL del feed Atom di MobyGames
     */
    fun getFeedUrl(): String = MOBYGAMES_FEED_URL
    
    /**
     * Parsa il feed Atom e restituisce la lista di giochi in evidenza
     */
    fun parseFeed(inputStream: InputStream): List<FeaturedGame> {
        val games = mutableListOf<FeaturedGame>()
        
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = true
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")
            
            var eventType = parser.eventType
            var currentEntry: EntryData? = null
            
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "entry" -> {
                                currentEntry = EntryData()
                            }
                            "title" -> {
                                if (currentEntry != null) {
                                    currentEntry.title = parser.nextText().trim()
                                }
                            }
                            "link" -> {
                                if (currentEntry != null) {
                                    // In Atom, il link può essere in href o nel testo
                                    val href = parser.getAttributeValue(null, "href")
                                    if (href != null && href.isNotBlank()) {
                                        currentEntry.link = href
                                    } else {
                                        // Prova a leggere il testo del link
                                        val linkText = parser.nextText().trim()
                                        if (linkText.isNotBlank() && linkText.startsWith("http")) {
                                            currentEntry.link = linkText
                                        }
                                    }
                                }
                            }
                            "content" -> {
                                if (currentEntry != null) {
                                    val content = parser.nextText()
                                    // Cerca immagini nel contenuto HTML
                                    val imageUrl = extractImageFromContent(content)
                                    if (imageUrl != null) {
                                        currentEntry.imageUrl = imageUrl
                                    }
                                }
                            }
                            "summary" -> {
                                if (currentEntry != null && currentEntry.imageUrl == null) {
                                    val summary = parser.nextText()
                                    // Cerca immagini nel summary HTML
                                    val imageUrl = extractImageFromContent(summary)
                                    if (imageUrl != null) {
                                        currentEntry.imageUrl = imageUrl
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "entry" && currentEntry != null) {
                            // Crea FeaturedGame dall'entry
                            val game = createFeaturedGame(currentEntry)
                            if (game != null) {
                                games.add(game)
                            }
                            currentEntry = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            android.util.Log.e("MobyGamesFeedParser", "Errore nel parsing del feed", e)
        }
        
        return games.take(10) // Massimo 10 giochi
    }
    
    /**
     * Estrae l'URL dell'immagine dal contenuto HTML
     */
    private fun extractImageFromContent(content: String): String? {
        // Cerca tag <img> con src
        val imgPattern = Regex("<img[^>]+src=['\"]([^'\"]+)['\"]", RegexOption.IGNORE_CASE)
        val match = imgPattern.find(content)
        return match?.groupValues?.get(1)
    }
    
    /**
     * Crea un FeaturedGame da un EntryData
     */
    private fun createFeaturedGame(entry: EntryData): FeaturedGame? {
        if (entry.link.isBlank()) {
            return null
        }
        
        // Estrai il titolo dall'URL se non presente nel feed
        val title = if (entry.title.isNotBlank()) {
            entry.title
        } else {
            extractTitleFromUrl(entry.link)
        }
        
        // Estrai la query di ricerca dall'URL
        val searchQuery = extractSearchQueryFromUrl(entry.link)
        
        return FeaturedGame(
            title = title,
            imageUrl = entry.imageUrl,
            gameUrl = entry.link,
            searchQuery = searchQuery
        )
    }
    
    /**
     * Estrae il titolo dall'URL del gioco
     * Es: https://www.mobygames.com/game/251332/spongebob-patty-pursuit-2/ -> "Spongebob Patty Pursuit 2"
     */
    private fun extractTitleFromUrl(url: String): String {
        // Estrai la parte finale dell'URL (dopo l'ultimo /)
        val parts = url.trimEnd('/').split("/")
        val slug = parts.lastOrNull() ?: return "Unknown Game"
        
        // Trasforma lo slug in titolo: "spongebob-patty-pursuit-2" -> "Spongebob Patty Pursuit 2"
        return slug
            .split("-")
            .joinToString(" ") { word ->
                word.lowercase().replaceFirstChar { 
                    if (it.isLowerCase()) it.titlecase() else it.toString() 
                }
            }
    }
    
    /**
     * Estrae la query di ricerca dall'URL (stesso del titolo)
     */
    private fun extractSearchQueryFromUrl(url: String): String {
        return extractTitleFromUrl(url)
    }
    
    /**
     * Dati temporanei per un entry del feed
     */
    private data class EntryData(
        var title: String = "",
        var link: String = "",
        var imageUrl: String? = null
    )
}

