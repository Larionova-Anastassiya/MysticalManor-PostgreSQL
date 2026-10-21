import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.http.content.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.sql.DriverManager
import java.sql.Connection

@Serializable data class NewPlayer(val name: String, val characterClass: String)
@Serializable data class PlayerView(val id: Long, val name: String, val characterClass: String, val health: Int, val maxHealth: Int, val gold: Int, val experience: Int, val room: String, val inventory: List<String>, val paintings: List<String>, val message: String)
@Serializable data class ActionRequest(val action: String)
@Serializable data class UpdatePlayer(val name: String, val characterClass: String)
@Serializable data class ErrorView(val error: String)

object ManorDatabase {
    private val host = System.getenv("DB_HOST") ?: "localhost"
    private val port = System.getenv("DB_PORT") ?: "5432"
    private val name = System.getenv("DB_NAME") ?: "mystical_manor"
    private val user = System.getenv("DB_USER") ?: "manor"
    private val password = System.getenv("DB_PASSWORD") ?: error("DB_PASSWORD must be configured")
    private fun connect(): Connection = DriverManager.getConnection("jdbc:postgresql://$host:$port/$name", user, password)
    fun initialize() = connect().use { c -> c.createStatement().use { s ->
        s.executeUpdate("""CREATE TABLE IF NOT EXISTS players (id BIGSERIAL PRIMARY KEY, name VARCHAR(60) NOT NULL, character_class VARCHAR(20) NOT NULL, health INT NOT NULL, max_health INT NOT NULL, gold INT NOT NULL, experience INT NOT NULL DEFAULT 0, room VARCHAR(40) NOT NULL DEFAULT 'Grand Hall', message TEXT NOT NULL DEFAULT '')""")
        s.executeUpdate("""CREATE TABLE IF NOT EXISTS inventory (player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE, item VARCHAR(100) NOT NULL, PRIMARY KEY(player_id,item))""")
        s.executeUpdate("""CREATE TABLE IF NOT EXISTS paintings (player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE, title VARCHAR(100) NOT NULL, PRIMARY KEY(player_id,title))""")
    } }
    fun create(req: NewPlayer): PlayerView {
        val name = req.name.trim().take(60)
        require(name.isNotEmpty()) { "Enter a character name" }
        val kind = req.characterClass.lowercase()
        require(kind in setOf("artist", "explorer", "mage")) { "Choose Artist, Explorer or Mage" }
        // Reuse the character classes from the original console game.
        val hero: Character = when(kind) { "artist" -> Artist(name); "explorer" -> Explorer(name); else -> Mage(name) }
        return connect().use { c ->
            c.autoCommit = false
            try {
                val id = c.prepareStatement("INSERT INTO players(name,character_class,health,max_health,gold,experience,room,message) VALUES(?,?,?,?,?,?,?,?) RETURNING id").use { s ->
                    s.setString(1,name);s.setString(2,hero.characterClass);s.setInt(3,hero.health);s.setInt(4,hero.maxHealth);s.setInt(5,hero.gold);s.setInt(6,0);s.setString(7,"Grand Hall");s.setString(8,"The manor doors close behind you. Your story begins.");s.executeQuery().use { r -> r.next();r.getLong(1) }
                }
                if(kind=="artist") insertItem(c,id,"Magic Brush")
                if(kind=="explorer") insertItem(c,id,"Old Compass")
                if(kind=="mage") insertItem(c,id,"Arcane Pendant")
                c.commit(); load(id)!!
            } catch(e: Exception) { c.rollback();throw e }
        }
    }
    fun listPlayers(): List<PlayerView> = connect().use { c ->
        val ids = c.prepareStatement("SELECT id FROM players ORDER BY id").use { statement ->
            statement.executeQuery().use { result ->
                buildList { while (result.next()) add(result.getLong(1)) }
            }
        }
        ids.mapNotNull { load(c, it) }
    }

    fun update(id: Long, request: UpdatePlayer): PlayerView? {
        val playerName = request.name.trim()
        require(playerName.isNotEmpty() && playerName.length <= 60) { "Name must be 1 to 60 characters" }
        val kind = request.characterClass.lowercase()
        require(kind in setOf("artist", "explorer", "mage")) { "Choose Artist, Explorer or Mage" }
        return connect().use { c ->
            c.autoCommit = false
            try {
                // Preserve progression; when class changes, update class-specific maximum HP and starting item.
                val existing = load(c, id) ?: return@use null
                val newHero: Character = when (kind) {
                    "artist" -> Artist(playerName)
                    "explorer" -> Explorer(playerName)
                    else -> Mage(playerName)
                }
                c.prepareStatement("UPDATE players SET name=?, character_class=?, max_health=?, health=LEAST(health, ?) WHERE id=?").use { st ->
                    st.setString(1, playerName)
                    st.setString(2, newHero.characterClass)
                    st.setInt(3, newHero.maxHealth)
                    st.setInt(4, newHero.maxHealth)
                    st.setLong(5, id)
                    st.executeUpdate()
                }
                if (existing.characterClass != newHero.characterClass) {
                    c.prepareStatement("DELETE FROM inventory WHERE player_id=? AND item IN ('Magic Brush', 'Old Compass', 'Arcane Pendant')").use { st ->
                        st.setLong(1, id)
                        st.executeUpdate()
                    }
                    val starter = when (kind) {
                        "artist" -> "Magic Brush"
                        "explorer" -> "Old Compass"
                        else -> "Arcane Pendant"
                    }
                    insertItem(c, id, starter)
                }
                c.commit()
                load(id)
            } catch (e: Exception) {
                c.rollback()
                throw e
            }
        }
    }

    fun delete(id: Long): Boolean = connect().use { c ->
        c.prepareStatement("DELETE FROM players WHERE id=?").use { st ->
            st.setLong(1, id)
            st.executeUpdate() > 0
        }
    }

    private fun insertItem(c: Connection,id: Long,item: String) = c.prepareStatement("INSERT INTO inventory(player_id,item) VALUES(?,?) ON CONFLICT DO NOTHING").use { s ->s.setLong(1,id);s.setString(2,item);s.executeUpdate();Unit }
    private fun insertPainting(c: Connection,id: Long,title: String) = c.prepareStatement("INSERT INTO paintings(player_id,title) VALUES(?,?) ON CONFLICT DO NOTHING").use { s ->s.setLong(1,id);s.setString(2,title);s.executeUpdate();Unit }
    fun load(id: Long): PlayerView? = connect().use { c -> load(c,id) }
    private fun load(c: Connection,id: Long): PlayerView? {
        val base = c.prepareStatement("SELECT name,character_class,health,max_health,gold,experience,room,message FROM players WHERE id=?").use { s -> s.setLong(1,id);s.executeQuery().use { r -> if(r.next()) listOf(r.getString(1),r.getString(2),r.getInt(3),r.getInt(4),r.getInt(5),r.getInt(6),r.getString(7),r.getString(8)) else null } } ?: return null
        fun list(table:String,column:String):List<String> = c.prepareStatement("SELECT $column FROM $table WHERE player_id=? ORDER BY $column").use { s -> s.setLong(1,id);s.executeQuery().use { r -> buildList { while(r.next()) add(r.getString(1)) } } }
        return PlayerView(id,base[0] as String,base[1] as String,base[2] as Int,base[3] as Int,base[4] as Int,base[5] as Int,base[6] as String,list("inventory","item"),list("paintings","title"),base[7] as String)
    }
    fun act(id:Long, action:String):PlayerView? = connect().use { c ->
        c.autoCommit=false
        try {
            // Lock the player row to prevent concurrent actions from losing updates.
            val row=c.prepareStatement("SELECT room,character_class,gold,experience FROM players WHERE id=? FOR UPDATE").use { s ->s.setLong(1,id);s.executeQuery().use { r -> if(r.next()) listOf(r.getString(1),r.getString(2),r.getInt(3),r.getInt(4)) else null } } ?: return@use null
            var room=row[0] as String; val kind=row[1] as String;var gold=row[2] as Int;var xp=row[3] as Int;var message=""
            when(action) {
                "gallery" -> { room="Gallery";message="You enter the Gallery. The Starry Night shimmers behind its frame." }
                "library" -> { room="Library";message="Ancient books whisper as you enter the Library." }
                "hall" -> { room="Grand Hall";message="You return to the Grand Hall. The clock is still ticking." }
                "explore" -> {
                    when(room) {
                        "Gallery" -> {insertPainting(c,id,"The Starry Night");xp+=5;message="You discovered The Starry Night! Its painted stars begin to move. (+5 XP)"}
                        "Library" -> {insertItem(c,id,"Silver Key");xp+=5;message="You found a Silver Key between the pages of a dusty book! (+5 XP)"}
                        else -> {gold+=3;message="You find three forgotten gold coins beneath the grand staircase. (+3 gold)"}
                    }
                }
                "ability" -> {
                    message=when(kind) {
                        "Artist" -> { if(room=="Gallery") {insertPainting(c,id,"Hidden Portrait");"Your Magic Brush reveals a Hidden Portrait behind the wall!"} else "Your Magic Brush glows. Perhaps the Gallery hides something." }
                        "Explorer" -> {gold+=5;"Your keen eyes uncover a hidden cache of five gold coins!"}
                        else -> {xp+=8;"You channel arcane energy through the manor. (+8 XP)"}
                    }
                }
                else -> throw IllegalArgumentException("Unknown action")
            }
            c.prepareStatement("UPDATE players SET room=?,gold=?,experience=?,message=? WHERE id=?").use { s ->s.setString(1,room);s.setInt(2,gold);s.setInt(3,xp);s.setString(4,message);s.setLong(5,id);s.executeUpdate() }
            c.commit();load(id)
        } catch(e:Exception) {c.rollback();throw e}
    }
}

fun main() {
    ManorDatabase.initialize()
    val port=(System.getenv("PORT")?:"8080").toInt()
    embeddedServer(Netty,host="0.0.0.0",port=port) {
        install(ContentNegotiation) { json(Json { prettyPrint=true }) }
        routing {
            get("/api/health") { call.respond(mapOf("status" to "ok")) }
            get("/api/players") {
                call.respond(ManorDatabase.listPlayers())
            }
            post("/api/players") {
                try { call.respond(HttpStatusCode.Created,ManorDatabase.create(call.receive<NewPlayer>())) }
                catch(e:IllegalArgumentException) {call.respond(HttpStatusCode.BadRequest,ErrorView(e.message?:"Invalid input"))}
            }
            get("/api/players/{id}") {
                val id=call.parameters["id"]?.toLongOrNull()
                val player=id?.let {ManorDatabase.load(it)}
                if(player==null) call.respond(HttpStatusCode.NotFound,ErrorView("Player not found")) else call.respond(player)
            }
            put("/api/players/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null || id <= 0) {
                    call.respond(HttpStatusCode.BadRequest, ErrorView("Invalid player ID"))
                    return@put
                }
                try {
                    val updated = ManorDatabase.update(id, call.receive<UpdatePlayer>())
                    if (updated == null) call.respond(HttpStatusCode.NotFound, ErrorView("Player not found"))
                    else call.respond(updated)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, ErrorView(e.message ?: "Invalid input"))
                }
            }
            delete("/api/players/{id}") {
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null || id <= 0) {
                    call.respond(HttpStatusCode.BadRequest, ErrorView("Invalid player ID"))
                    return@delete
                }
                if (ManorDatabase.delete(id)) call.respond(HttpStatusCode.NoContent)
                else call.respond(HttpStatusCode.NotFound, ErrorView("Player not found"))
            }
            post("/api/players/{id}/actions") {
                val id=call.parameters["id"]?.toLongOrNull()
                if(id==null) {call.respond(HttpStatusCode.BadRequest,ErrorView("Invalid player ID"));return@post}
                try { val player=ManorDatabase.act(id,call.receive<ActionRequest>().action);if(player==null)call.respond(HttpStatusCode.NotFound,ErrorView("Player not found")) else call.respond(player) }
                catch(e:IllegalArgumentException) {call.respond(HttpStatusCode.BadRequest,ErrorView(e.message?:"Invalid action"))}
            }
            get("/api/players/{id}/inventory") { val p=call.parameters["id"]?.toLongOrNull()?.let(ManorDatabase::load);if(p==null)call.respond(HttpStatusCode.NotFound,ErrorView("Not found")) else call.respond(p.inventory) }
            get("/api/players/{id}/paintings") { val p=call.parameters["id"]?.toLongOrNull()?.let(ManorDatabase::load);if(p==null)call.respond(HttpStatusCode.NotFound,ErrorView("Not found")) else call.respond(p.paintings) }
            staticResources("/", "web", index="index.html")
        }
    }.start(wait=true)
}
