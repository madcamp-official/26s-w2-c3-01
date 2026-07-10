package com.melodybubble.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class MelodyBubbleServerApplication

fun main(args: Array<String>) {
    runApplication<MelodyBubbleServerApplication>(*args)
}
