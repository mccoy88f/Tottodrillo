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
        regions = this.regions.map { regionName -> 
            // Converti il nome della regione in codice standardizzato
            val regionCode = normalizeRegionNameToCode(regionName, sourceId)
            RegionInfo.fromCode(regionCode)
        },
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
 * Normalizza il nome di una regione in un codice standardizzato
 * Gestisce i casi in cui le sorgenti restituiscono nomi invece di codici (es. Vimm's Lair)
 */
private fun normalizeRegionNameToCode(regionName: String, sourceId: String?): String {
    // Se è già un codice standardizzato (2-3 lettere maiuscole), restituiscilo
    if (regionName.matches(Regex("^[A-Z]{2,3}$"))) {
        return regionName.uppercase()
    }
    
    // Normalizza il nome per il matching (rimuovi spazi, converti in minuscolo)
    val normalized = regionName.trim().lowercase()
    
    // Mapping da nomi comuni a codici standardizzati
    return when (normalized) {
        "united states", "usa", "us", "u.s.", "u.s.a.", "america" -> "US"
        "europe", "eu", "european union", "e.u." -> "EU"
        "japan", "jp", "japanese" -> "JP"
        "korea", "kr", "south korea", "south korean" -> "KR"
        "china", "cn", "chinese", "prc" -> "CN"
        "australia", "au", "australian" -> "AU"
        "brazil", "br", "brasil", "brazilian" -> "BR"
        "united kingdom", "uk", "u.k.", "britain", "great britain", "england" -> "UK"
        "france", "fr", "french" -> "FR"
        "germany", "de", "deutschland", "german" -> "DE"
        "italy", "it", "italia", "italian" -> "IT"
        "spain", "es", "españa", "spanish" -> "ES"
        "netherlands", "nl", "holland", "dutch" -> "NL"
        "sweden", "se", "swedish" -> "SE"
        "norway", "no", "norwegian" -> "NO"
        "denmark", "dk", "danish" -> "DK"
        "finland", "fi", "finnish" -> "FI"
        "worldwide", "world", "ww", "global", "international" -> "WW"
        "canada", "ca", "canadian" -> "CA"
        "mexico", "mx", "mexican" -> "MX"
        "argentina", "ar", "argentine" -> "AR"
        "chile", "cl", "chilean" -> "CL"
        "colombia", "co", "colombian" -> "CO"
        "peru", "pe", "peruvian" -> "PE"
        "portugal", "pt", "portuguese" -> "PT"
        "greece", "gr", "greek" -> "GR"
        "poland", "pl", "polish" -> "PL"
        "russia", "ru", "russian" -> "RU"
        "south africa", "za", "south african" -> "ZA"
        "new zealand", "nz", "new zealand" -> "NZ"
        "hong kong", "hk", "hong kong" -> "HK"
        "taiwan", "tw", "taiwanese" -> "TW"
        "singapore", "sg", "singaporean" -> "SG"
        "thailand", "th", "thai" -> "TH"
        "philippines", "ph", "filipino" -> "PH"
        "indonesia", "id", "indonesian" -> "ID"
        "malaysia", "my", "malaysian" -> "MY"
        "vietnam", "vn", "vietnamese" -> "VN"
        "india", "in", "indian" -> "IN"
        "turkey", "tr", "turkish" -> "TR"
        "israel", "il", "israeli" -> "IL"
        "egypt", "eg", "egyptian" -> "EG"
        "saudi arabia", "sa", "saudi" -> "SA"
        "uae", "united arab emirates", "ae" -> "AE"
        else -> {
            // Se non riconosciuto, prova a estrarre un codice dalle prime lettere
            // o usa il nome originale come codice
            if (normalized.length >= 2) {
                normalized.take(2).uppercase()
            } else {
                regionName.uppercase()
            }
        }
    }
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
