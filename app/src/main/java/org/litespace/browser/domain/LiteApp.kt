package org.litespace.browser.domain

import java.util.UUID

data class LiteApp(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",
    val icon: String = "",
    val group: String = "",
    val favorite: Boolean = false,
    val javascript: Boolean = true,
    val desktop: Boolean = false,
    val userAgent: String = "",
    val css: String = "",
    val script: String = "",
    val dark: Boolean = false,
    val fontSize: Int = 100,
    val zoom: Boolean = true,
    val thirdPartyCookies: Boolean = false,
    val blockTrackers: Boolean = true,
    val clearOnExit: Boolean = false,
    val httpsOnly: Boolean = true,
    val location: Boolean = false,
    val camera: Boolean = false,
    val microphone: Boolean = false,
    val popups: Boolean = false,
    val fullscreen: Boolean = false,
    val navigation: Boolean = true,
    val gestures: Boolean = true,
    val lock: Boolean = false,
    val downloadNotifications: Boolean = true,
    val profile: String = "",
    val accent: Long = 0xFF526849,
    val updatedAt: Long = System.currentTimeMillis()
) : java.io.Serializable
