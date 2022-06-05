package vk.sudipriankape

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
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.lessEq
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.File

object Artists : IntIdTable() {
    val name = text("artist")
}

object Votes : IntIdTable() {
    val phone = varchar("phone", 10)
    val artist = text("artist")
    val timestamp = long("timestamp")
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
            } else if (transaction { Artists.select { Artists.name eq artist }.count() } == 0L) {
                call.respond(HttpStatusCode.NotFound)
                return@post
            } else {
                transaction {
                    Votes.insert {
                        it[Votes.phone] = phone
                        it[Votes.artist] = artist
                        it[timestamp] = System.currentTimeMillis()
                    }
                }
                call.respond(HttpStatusCode.Created)
            }
        }
        get("votes/stats") route@{
            val from = call.request.queryParameters["from"]?.toLong()
            val to = call.request.queryParameters["to"]?.toLong()
            val intervalsN = call.request.queryParameters["intervals"]?.toLong()
            val artists = call.request.queryParameters["artists"]?.split(',')
            val stats =
                transaction {
                    val from =
                        from ?: Votes.slice(Votes.timestamp.min()).selectAll().map { it[Votes.timestamp.min()] }[0]!!
                    val to = to ?: Votes.slice(Votes.timestamp.max()).selectAll().map { it[Votes.timestamp.max()] }[0]!!
                    val intervalsN = minOf(intervalsN ?: 10, (to - from) / 1000).toInt()
                    val intervals = MutableList(intervalsN) { (to - from) / intervalsN }
                    intervals[intervalsN - 1] += (to - from) % intervalsN
                    intervals.add(0)
                    val stats = mutableListOf<Stat>()
                    var st = from
                    var ed = from + intervals[0]
                    var i = 1
                    while (i <= intervalsN) {
                        var condition = Votes.timestamp greaterEq st and (Votes.timestamp lessEq ed)
                        artists?.let { condition = condition and (Votes.artist inList it) }
                        stats.add(Stat(st, ed, Votes.select { condition }.count()))
                        st = ed
                        ed += intervals[i]
                        i += 1
                    }
                    stats
                }
            call.respond(mapOf("data" to stats))
        }
    }
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

@Serializable
data class Stat(val start: Long, val end: Long, val votes: Long)