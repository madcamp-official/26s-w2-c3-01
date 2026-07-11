package com.melodybubble.server

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class MelodyBubbleServerApplication

fun main(args: Array<String>) {
    runApplication<MelodyBubbleServerApplication>(*args)
}
