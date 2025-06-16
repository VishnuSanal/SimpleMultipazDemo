package org.multipaz.simpledemo

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform