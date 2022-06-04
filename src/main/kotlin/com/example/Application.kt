package com.example

import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object Artists : IntIdTable() {
    val name = text("artist")
}

object Votes : IntIdTable() {
    val phone = varchar("phone", 10)
    val artist = text("artist")
}

fun main() {
    Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1", driver = "org.h2.Driver")
    transaction {
        SchemaUtils.create(Artists)
        SchemaUtils.create(Votes)
        File("artists.txt").forEachLine { artist ->
            Artists.insert { it[name] = artist }
        }
    }
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") {
        configureRouting()
        install(ContentNegotiation) {
            json()
        }
    }.start(wait = true)
}

fun Application.configureRouting() {
    routing {
        get("/votes") {
            val stats = transaction {
                val artists = Artists.selectAll().map { it[Artists.name] }.toList()
                artists.map { artist ->
                    val votes = Votes.select(Votes.artist eq artist).count()
                    ArtistStat(artist, votes)
                }.toList()
            }
            call.respond(mapOf("data" to stats))
        }
        post("/votes") {
            val (phone, artist) = call.receive<Vote>()
            if (!isPhoneValid(phone)) {
                call.respond(HttpStatusCode.BadRequest)
            } else if (transaction { Artists.select { Artists.name eq artist }.count() } != 0L) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            } else {
                transaction {
                    Votes.insert {
                        it[Votes.phone] = phone
                        it[Votes.artist] = artist
                    }
                }
                call.respond(HttpStatusCode.Created)
            }
        }
    }
    routing {}
}

fun isPhoneValid(phone: String): Boolean {
    if (phone.length != 10) return false
    if (phone[0] != '9') return false
    if (phone.any { char -> !char.isDigit() }) return false
    return true
}

@Serializable
data class Vote(val phone: String, val artist: String)

@Serializable
data class ArtistStat(val name: String, val votes: Long)