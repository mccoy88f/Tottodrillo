package com.crocdb.friends.data.mapper

import com.crocdb.friends.data.model.Platform
import com.crocdb.friends.data.model.Region
import com.crocdb.friends.data.model.RomEntry
import com.crocdb.friends.domain.model.DownloadLink
import com.crocdb.friends.domain.model.PlatformInfo
import com.crocdb.friends.domain.model.RegionInfo
import com.crocdb.friends.domain.model.Rom

/**
 * Mapper per convertire RomEntry (API) in Rom (Domain)
 */
fun RomEntry.toDomain(): Rom {
    return Rom(
        slug = this.slug,
        id = this.romId,
        title = this.title,
        platform = PlatformInfo(
            code = this.platform,
            displayName = getPlatformDisplayName(this.platform)
        ),
        coverUrl = this.boxartUrl,
        regions = this.regions.map { RegionInfo.fromCode(it) },
        downloadLinks = this.links.map { link ->
            DownloadLink(
                name = link.name,
                type = link.type,
                format = link.format,
                url = link.url,
                size = link.size
            )
        }
    )
}

/**
 * Mapper per Platform
 */
fun Platform.toDomain(): PlatformInfo {
    return PlatformInfo(
        code = this.code,
        displayName = this.name,
        manufacturer = this.manufacturer
    )
}

/**
 * Mapper per Region
 */
fun Region.toDomain(): RegionInfo {
    return RegionInfo.fromCode(this.code)
}

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
