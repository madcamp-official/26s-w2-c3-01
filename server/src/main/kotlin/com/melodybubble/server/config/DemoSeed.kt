package com.melodybubble.server.config

import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class DemoSeed(private val jdbc: JdbcTemplate) : ApplicationRunner {
    override fun run(args: ApplicationArguments) {
        if ((jdbc.queryForObject("select count(*) from users", Int::class.java) ?: 0) > 0) return
        val encoder = BCryptPasswordEncoder()
        val people = listOf(
            Triple("demo@melody.local", "데모", "#6750A4"), Triple("mina@melody.local", "민아", "#D1648C"), Triple("jun@melody.local", "준", "#40897A")
        )
        people.forEach { (email, name, color) ->
            val id = UUID.randomUUID()
            jdbc.update("insert into users(id,email,password_hash,display_name,profile_color) values (?,?,?,?,?)", id, email, encoder.encode("demo1234"), name, color)
            jdbc.update("insert into user_privacy_settings(user_id) values (?)", id)
        }
        jdbc.update("insert into lounges(id,title,description,theme) values (?,?,?,?)", UUID.randomUUID(), "캠퍼스 나이트", "지금 듣는 곡을 가볍게 나누는 라운지", "NIGHT")
    }
}
