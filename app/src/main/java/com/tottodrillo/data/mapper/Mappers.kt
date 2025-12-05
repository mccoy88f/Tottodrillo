package com.tottodrillo.data.mapper

import com.tottodrillo.data.model.PlatformData
import com.tottodrillo.data.model.RomEntry
import com.tottodrillo.domain.model.DownloadLink
import com.tottodrillo.domain.model.PlatformInfo
import com.tottodrillo.domain.model.RegionInfo
import com.tottodrillo.domain.model.Rom

/**
 * Mapper per convertire RomEntry (API) in Rom (Domain)
 */
fun RomEntry.toDomain(sourceId: String? = null): Rom {
    // Usa boxart_urls se disponibile, altrimenti usa boxartUrl come lista
    val coverUrls = this.boxartUrls?.takeIf { it.isNotEmpty() } 
        ?: this.boxartUrl?.let { listOf(it) } 
        ?: emptyList()
    
    return Rom(
        slug = this.slug,
        id = this.romId,
        title = this.title,
        platform = PlatformInfo(
            code = this.platform,
            displayName = getPlatformDisplayName(this.platform)
        ),
        coverUrl = coverUrls.firstOrNull(), // Prima immagine come principale
        coverUrls = coverUrls, // Tutte le immagini per il carosello
        regions = this.regions.map { RegionInfo.fromCode(it) },
        downloadLinks = this.links.map { link ->
            DownloadLink(
                name = link.name,
                type = link.type,
                format = link.format,
                url = link.url,
                size = link.sizeStr,
                sourceId = sourceId
            )
        },
        sourceId = sourceId
    )
}

/**
 * Mapper per Platform
 */
fun Map.Entry<String, PlatformData>.toDomain(): PlatformInfo {
    return PlatformInfo(
        code = this.key,
        displayName = this.value.name,
        manufacturer = this.value.brand
    )
}

/**
 * Mapper per Region
 */
fun Map.Entry<String, String>.toRegionInfo(): RegionInfo =
    RegionInfo.fromCode(this.key)

/**
 * Helper per ottenere il display name della piattaforma
 */
private fun getPlatformDisplayName(code: String): String = when (code.uppercase()) {
    "NES" -> "Nintendo Entertainment System"
    "SNES" -> "Super Nintendo"
    "N64" -> "Nintendo 64"
    "GC" -> "GameCube"
    "WII" -> "Nintendo Wii"
    "WIIU" -> "Wii U"
    "SWITCH" -> "Nintendo Switch"
    "GB" -> "Game Boy"
    "GBC" -> "Game Boy Color"
    "GBA" -> "Game Boy Advance"
    "NDS" -> "Nintendo DS"
    "3DS" -> "Nintendo 3DS"
    "PS1" -> "PlayStation"
    "PS2" -> "PlayStation 2"
    "PS3" -> "PlayStation 3"
    "PS4" -> "PlayStation 4"
    "PSP" -> "PlayStation Portable"
    "PSVITA" -> "PlayStation Vita"
    "SMS" -> "Sega Master System"
    "SMD" -> "Sega Mega Drive / Genesis"
    "SATURN" -> "Sega Saturn"
    "DC" -> "Dreamcast"
    "GG" -> "Game Gear"
    "XBOX" -> "Xbox"
    "XBOX360" -> "Xbox 360"
    "XBOXONE" -> "Xbox One"
    "ATARI2600" -> "Atari 2600"
    "ATARI5200" -> "Atari 5200"
    "ATARI7800" -> "Atari 7800"
    "LYNX" -> "Atari Lynx"
    "JAGUAR" -> "Atari Jaguar"
    "NEOGEO" -> "Neo Geo"
    "NGPC" -> "Neo Geo Pocket Color"
    "PCE" -> "PC Engine / TurboGrafx-16"
    "WONDERSWAN" -> "WonderSwan"
    "WONDERSWANCOLOR" -> "WonderSwan Color"
    "3DO" -> "3DO"
    "ARCADE" -> "Arcade"
    else -> code.uppercase()
}
