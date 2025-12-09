package com.tottodrillo.domain.manager

/**
 * Mapper per convertire piattaforme IGDB in piattaforme Tottodrillo
 * Mappa i nomi/ID delle piattaforme IGDB ai codici piattaforma usati in Tottodrillo
 */
object IgdbPlatformMapper {
    /**
     * Mappa un nome piattaforma IGDB al codice piattaforma Tottodrillo
     * @param igdbPlatformName Nome della piattaforma da IGDB
     * @return Codice piattaforma Tottodrillo o null se non trovato
     */
    fun mapIgdbToTottodrillo(igdbPlatformName: String): String? {
        val normalized = igdbPlatformName.lowercase().trim()
        
        return when {
            // Nintendo
            normalized.contains("nintendo switch") || normalized == "switch" -> "nsw"
            normalized.contains("nintendo 3ds") || normalized == "3ds" -> "3ds"
            normalized.contains("nintendo ds") || normalized == "ds" -> "nds"
            normalized.contains("game boy advance") || normalized == "gba" -> "gba"
            normalized.contains("game boy color") || normalized == "gbc" -> "gbc"
            normalized.contains("game boy") || normalized == "gb" -> "gb"
            normalized.contains("gamecube") || normalized == "gc" -> "gc"
            normalized.contains("wii u") -> "wiiu"
            normalized.contains("wii") -> "wii"
            normalized.contains("nintendo 64") || normalized == "n64" -> "n64"
            normalized.contains("super nintendo") || normalized.contains("snes") -> "snes"
            normalized.contains("nintendo entertainment system") || normalized.contains("nes") -> "nes"
            normalized.contains("game & watch") -> "gaw"
            
            // Sony
            normalized.contains("playstation 5") || normalized == "ps5" -> "ps5"
            normalized.contains("playstation 4") || normalized == "ps4" -> "ps4"
            normalized.contains("playstation 3") || normalized == "ps3" -> "ps3"
            normalized.contains("playstation 2") || normalized == "ps2" -> "ps2"
            normalized.contains("playstation") || normalized == "psx" || normalized == "ps1" -> "psx"
            normalized.contains("playstation portable") || normalized == "psp" -> "psp"
            normalized.contains("playstation vita") || normalized == "vita" -> "vita"
            
            // Microsoft
            normalized.contains("xbox series") || normalized.contains("xbox sx") -> "xbsx"
            normalized.contains("xbox one") -> "xbone"
            normalized.contains("xbox 360") -> "x360"
            normalized.contains("xbox") -> "xb"
            
            // Sega
            normalized.contains("dreamcast") -> "dc"
            normalized.contains("saturn") -> "ss"
            normalized.contains("genesis") || normalized.contains("mega drive") -> "md"
            normalized.contains("master system") -> "sms"
            normalized.contains("game gear") -> "gg"
            
            // Atari
            normalized.contains("atari 2600") -> "a26"
            normalized.contains("atari 7800") -> "a78"
            normalized.contains("atari lynx") -> "lynx"
            normalized.contains("jaguar") -> "jag"
            
            // Neo Geo
            normalized.contains("neo geo") -> "ng"
            normalized.contains("neo geo pocket") -> "ngp"
            
            // PC Engine / TurboGrafx
            normalized.contains("pc engine") || normalized.contains("turbografx") -> "pce"
            
            // 3DO
            normalized.contains("3do") -> "3do"
            
            // Amiga
            normalized.contains("amiga") -> "amiga"
            
            // Commodore
            normalized.contains("commodore 64") || normalized == "c64" -> "c64"
            
            // MSX
            normalized.contains("msx") -> "msx"
            
            // Apple
            normalized.contains("apple ii") -> "apple2"
            
            // Altri
            normalized.contains("wonderswan") -> "ws"
            normalized.contains("game.com") -> "gamecom"
            
            else -> "other" // fallback per piattaforme non mappate
        }
    }
    
    /**
     * Mappa un codice piattaforma Tottodrillo a possibili nomi IGDB per la ricerca
     * @param tottodrilloCode Codice piattaforma Tottodrillo
     * @return Lista di possibili nomi piattaforma IGDB
     */
    fun mapTottodrilloToIgdbSearchTerms(tottodrilloCode: String): List<String> {
        return when (tottodrilloCode.lowercase()) {
            "nsw" -> listOf("Nintendo Switch", "Switch")
            "3ds" -> listOf("Nintendo 3DS", "3DS")
            "nds" -> listOf("Nintendo DS", "DS")
            "gba" -> listOf("Game Boy Advance", "GBA")
            "gbc" -> listOf("Game Boy Color", "GBC")
            "gb" -> listOf("Game Boy", "GB")
            "gc" -> listOf("GameCube", "Gamecube")
            "wiiu" -> listOf("Wii U", "WiiU")
            "wii" -> listOf("Wii")
            "n64" -> listOf("Nintendo 64", "N64")
            "snes" -> listOf("Super Nintendo", "SNES", "Super NES")
            "nes" -> listOf("Nintendo Entertainment System", "NES")
            "ps5" -> listOf("PlayStation 5", "PS5")
            "ps4" -> listOf("PlayStation 4", "PS4")
            "ps3" -> listOf("PlayStation 3", "PS3")
            "ps2" -> listOf("PlayStation 2", "PS2")
            "psx" -> listOf("PlayStation", "PSX", "PS1")
            "psp" -> listOf("PlayStation Portable", "PSP")
            "vita" -> listOf("PlayStation Vita", "Vita", "PS Vita")
            "xbsx" -> listOf("Xbox Series X", "Xbox Series S", "Xbox Series")
            "xbone" -> listOf("Xbox One")
            "x360" -> listOf("Xbox 360")
            "xb" -> listOf("Xbox")
            "dc" -> listOf("Dreamcast")
            "ss" -> listOf("Saturn", "Sega Saturn")
            "md" -> listOf("Genesis", "Mega Drive", "Sega Genesis", "Sega Mega Drive")
            "sms" -> listOf("Master System", "Sega Master System")
            "gg" -> listOf("Game Gear", "Sega Game Gear")
            "a26" -> listOf("Atari 2600")
            "a78" -> listOf("Atari 7800")
            "lynx" -> listOf("Atari Lynx")
            "jag" -> listOf("Jaguar", "Atari Jaguar")
            "ng" -> listOf("Neo Geo", "NeoGeo")
            "ngp" -> listOf("Neo Geo Pocket", "NeoGeo Pocket")
            "pce" -> listOf("PC Engine", "TurboGrafx-16", "TurboGrafx")
            "3do" -> listOf("3DO", "3DO Interactive Multiplayer")
            "amiga" -> listOf("Amiga")
            "c64" -> listOf("Commodore 64", "C64")
            "msx" -> listOf("MSX")
            "apple2" -> listOf("Apple II", "Apple IIe")
            "ws" -> listOf("WonderSwan", "WonderSwan Color")
            "gamecom" -> listOf("Game.com")
            else -> emptyList()
        }
    }
}

