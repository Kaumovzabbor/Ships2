package com.friends.ships.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class PlayerInfo(
    val id: Int,
    val name: String,
    val ready: Boolean = false,
    val alive: Boolean = true,
    val bot: Boolean = false,
)

/**
 * Bitta umumiy xabar turi (har qator = bitta JSON).
 * t: JOIN, WELCOME, REJECT, LOBBY, ADDBOT, START, PLACE, READY, FLEET, BATTLE,
 *    FIRE, RESULT, LEFT, REMATCH, SETTINGS, DISCONNECTED (faqat mahalliy)
 */
@Serializable
data class Msg(
    val t: String,
    val id: Int = -1,
    val name: String = "",
    val players: List<PlayerInfo> = emptyList(),
    val fleet: List<List<Int>> = emptyList(),
    val target: Int = -1,
    val cell: Int = -1,
    val res: String = "",
    val sunk: List<Int> = emptyList(),
    val turn: Int = -1,
    val winner: Int = -1,
    val text: String = "",
    val secs: Int = 0,
    val around: List<Int> = emptyList(),
    // host sozlamalari (LOBBY/WELCOME/PLACE/BATTLE xabarlarida, SETTINGS da host yuboradi)
    val fleetType: String = "CLASSIC", // CLASSIC | RUSSIAN
    val noTouch: Boolean = false,
    val extra: Boolean = true,
    val timer: Int = 30, // 0 = o'chiq
)

val json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
