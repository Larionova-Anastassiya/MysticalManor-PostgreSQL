import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.random.Random

@Serializable data class PaintingInfo(val id: Long, val title: String, val description: String, val difficulty: Int, val rewardGold: Int, val rewardXp: Int)
@Serializable data class StartPuzzle(val playerId: Long, val paintingId: Long)
@Serializable data class RuneGuess(val runes: List<String>)
@Serializable data class PuzzleView(val id: Long, val playerId: Long, val paintingId: Long, val paintingTitle: String, val status: String, val moves: Int, val maxMoves: Int, val lastExact: Int, val lastPresent: Int, val guesses: List<String>, val createdAt: String)

object CursedPaintingDB {
    private val symbols = listOf("SUN", "MOON", "STAR", "KEY")
    private fun connect(): Connection = DriverManager.getConnection(
        "jdbc:postgresql://${System.getenv("DB_HOST") ?: "localhost"}:${System.getenv("DB_PORT") ?: "5432"}/${System.getenv("DB_NAME") ?: "mystical_manor"}",
        System.getenv("DB_USER") ?: "manor",
        System.getenv("DB_PASSWORD") ?: error("DB_PASSWORD must be configured")
    )

    fun initialize() = connect().use { c ->
        c.createStatement().use { s ->
            s.executeUpdate("""CREATE TABLE IF NOT EXISTS painting_catalog (
                id BIGSERIAL PRIMARY KEY, title VARCHAR(100) NOT NULL UNIQUE,
                description TEXT NOT NULL, difficulty INT NOT NULL CHECK (difficulty BETWEEN 1 AND 10),
                reward_gold INT NOT NULL CHECK (reward_gold >= 0), reward_xp INT NOT NULL CHECK (reward_xp >= 0))""")
            s.executeUpdate("""CREATE TABLE IF NOT EXISTS puzzle_attempts (
                id BIGSERIAL PRIMARY KEY,
                player_id BIGINT NOT NULL REFERENCES players(id) ON DELETE CASCADE,
                painting_id BIGINT NOT NULL REFERENCES painting_catalog(id) ON DELETE RESTRICT,
                secret VARCHAR(80) NOT NULL,
                status VARCHAR(12) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','WON','LOST')),
                moves INT NOT NULL DEFAULT 0, max_moves INT NOT NULL DEFAULT 6,
                last_exact INT NOT NULL DEFAULT 0, last_present INT NOT NULL DEFAULT 0,
                guesses TEXT NOT NULL DEFAULT '',
                created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)""")
            s.executeUpdate("""INSERT INTO painting_catalog(title,description,difficulty,reward_gold,reward_xp) VALUES
                ('The Starry Night','A constellation is locked behind four ancient runes.',3,12,20),
                ('Hidden Portrait','The portrait watches every symbol you choose.',5,20,30)
                ON CONFLICT(title) DO NOTHING""")
        }
    }

    fun paintings(): List<PaintingInfo> = connect().use { c ->
        c.prepareStatement("SELECT id,title,description,difficulty,reward_gold,reward_xp FROM painting_catalog ORDER BY id").use { s ->
            s.executeQuery().use { r -> buildList { while(r.next()) add(PaintingInfo(r.getLong(1),r.getString(2),r.getString(3),r.getInt(4),r.getInt(5),r.getInt(6))) } }
        }
    }
    fun painting(id: Long): PaintingInfo? = paintings().firstOrNull { it.id == id }

    private fun load(c: Connection, id: Long): PuzzleView? = c.prepareStatement("""SELECT a.id,a.player_id,a.painting_id,p.title,a.status,a.moves,a.max_moves,a.last_exact,a.last_present,a.guesses,a.created_at
        FROM puzzle_attempts a JOIN painting_catalog p ON p.id=a.painting_id WHERE a.id=?""").use { s ->
        s.setLong(1,id)
        s.executeQuery().use { r -> if (!r.next()) null else PuzzleView(
            r.getLong(1),r.getLong(2),r.getLong(3),r.getString(4),r.getString(5),r.getInt(6),r.getInt(7),r.getInt(8),r.getInt(9),
            r.getString(10).lines().filter { it.isNotBlank() },r.getString(11)
        ) }
    }
    fun attempt(id: Long): PuzzleView? = connect().use { load(it,id) }
    fun attempts(playerId: Long?, paintingId: Long?): List<PuzzleView> = connect().use { c ->
        val conditions = mutableListOf<String>()
        if (playerId != null) conditions += "player_id=?"
        if (paintingId != null) conditions += "painting_id=?"
        val sql = "SELECT id FROM puzzle_attempts" + (if(conditions.isEmpty()) "" else " WHERE ${conditions.joinToString(" AND ")}") + " ORDER BY id DESC LIMIT 100"
        c.prepareStatement(sql).use { s ->
            var pos=1
            if(playerId!=null) s.setLong(pos++,playerId)
            if(paintingId!=null) s.setLong(pos,paintingId)
            val ids=s.executeQuery().use { r -> buildList { while(r.next()) add(r.getLong(1)) } }
            ids.mapNotNull { load(c,it) }
        }
    }

    fun start(req: StartPuzzle): PuzzleView = connect().use { c ->
        c.autoCommit=false
        try {
            require(req.playerId>0 && req.paintingId>0) { "IDs must be positive" }
            val room=c.prepareStatement("SELECT room FROM players WHERE id=? FOR UPDATE").use { s ->
                s.setLong(1,req.playerId);s.executeQuery().use { r -> if(r.next()) r.getString(1) else null }
            } ?: throw NoSuchElementException("Player not found")
            require(room=="Gallery") { "Enter the Gallery before starting a puzzle" }
            val title=c.prepareStatement("SELECT title FROM painting_catalog WHERE id=?").use { s ->
                s.setLong(1,req.paintingId);s.executeQuery().use { r -> if(r.next()) r.getString(1) else null }
            } ?: throw NoSuchElementException("Painting not found")
            val unlocked=c.prepareStatement("SELECT 1 FROM paintings WHERE player_id=? AND title=?").use { s ->
                s.setLong(1,req.playerId);s.setString(2,title);s.executeQuery().use { it.next() }
            }
            require(unlocked) { "Discover this painting in the Gallery first" }
            val secret=(1..3).map { symbols.random() }.joinToString(",")
            val id=c.prepareStatement("INSERT INTO puzzle_attempts(player_id,painting_id,secret) VALUES(?,?,?) RETURNING id").use { s ->
                s.setLong(1,req.playerId);s.setLong(2,req.paintingId);s.setString(3,secret)
                s.executeQuery().use { r -> r.next();r.getLong(1) }
            }
            c.commit()
            load(c,id)!!
        } catch(e:Exception) { c.rollback();throw e }
    }

    fun guess(id: Long, request: RuneGuess): PuzzleView? = connect().use { c ->
        require(request.runes.size==3 && request.runes.all { it in symbols }) { "Submit exactly three runes: SUN, MOON, STAR or KEY" }
        c.autoCommit=false
        try {
            val row=c.prepareStatement("SELECT player_id,painting_id,secret,status,moves,max_moves,guesses FROM puzzle_attempts WHERE id=? FOR UPDATE").use { s ->
                s.setLong(1,id);s.executeQuery().use { r -> if(r.next()) listOf(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getInt(5),r.getInt(6),r.getString(7)) else null }
            } ?: return@use null
            require(row[3]=="ACTIVE") { "This attempt is already finished" }
            val secret=(row[2] as String).split(",")
            val exact=secret.indices.count { secret[it]==request.runes[it] }
            val remainingSecret=secret.indices.filter { secret[it]!=request.runes[it] }.map { secret[it] }.toMutableList()
            var present=0
            for(i in request.runes.indices) if(secret[i]!=request.runes[i] && remainingSecret.remove(request.runes[i])) present++
            val moves=(row[4] as Int)+1
            val status=if(exact==3) "WON" else if(moves >= (row[5] as Int)) "LOST" else "ACTIVE"
            val history=(row[6] as String)+request.runes.joinToString("-")+" | exact=$exact present=$present\n"
            c.prepareStatement("UPDATE puzzle_attempts SET status=?,moves=?,last_exact=?,last_present=?,guesses=? WHERE id=?").use { s ->
                s.setString(1,status);s.setInt(2,moves);s.setInt(3,exact);s.setInt(4,present);s.setString(5,history);s.setLong(6,id);s.executeUpdate()
            }
            if(status=="WON") {
                val reward=c.prepareStatement("SELECT reward_gold,reward_xp FROM painting_catalog WHERE id=?").use { s ->
                    s.setLong(1,row[1] as Long);s.executeQuery().use { r -> r.next();Pair(r.getInt(1),r.getInt(2)) }
                }
                c.prepareStatement("UPDATE players SET gold=gold+?,experience=experience+?,message=? WHERE id=?").use { s ->
                    s.setInt(1,reward.first);s.setInt(2,reward.second);s.setString(3,"The cursed painting is restored! +${reward.first} gold, +${reward.second} XP")
                    s.setLong(4,row[0] as Long);s.executeUpdate()
                }
                c.prepareStatement("INSERT INTO inventory(player_id,item) VALUES(?,?) ON CONFLICT DO NOTHING").use { s ->
                    s.setLong(1,row[0] as Long);s.setString(2,"Enchanted Rune");s.executeUpdate()
                }
            }
            c.commit()
            load(c,id)
        } catch(e:Exception) { c.rollback();throw e }
    }
    fun delete(id: Long): Boolean = connect().use { c ->
        c.prepareStatement("DELETE FROM puzzle_attempts WHERE id=?").use { s -> s.setLong(1,id);s.executeUpdate()>0 }
    }
}

fun Route.cursedPaintingRoutes() {
    fun validId(value: String?): Long? = value?.toLongOrNull()?.takeIf { it>0 }
    get("/api/paintings") { call.respond(CursedPaintingDB.paintings()) }
    get("/api/paintings/{id}") {
        val id=validId(call.parameters["id"])
        if(id==null) { call.respond(HttpStatusCode.BadRequest,ErrorView("Invalid painting ID"));return@get }
        val item=CursedPaintingDB.painting(id)
        if(item==null) call.respond(HttpStatusCode.NotFound,ErrorView("Painting not found")) else call.respond(item)
    }
    get("/api/paintings/{id}/attempts") {
        val id=validId(call.parameters["id"])
        if(id==null) { call.respond(HttpStatusCode.BadRequest,ErrorView("Invalid painting ID"));return@get }
        if(CursedPaintingDB.painting(id)==null) { call.respond(HttpStatusCode.NotFound,ErrorView("Painting not found"));return@get }
        call.respond(CursedPaintingDB.attempts(null,id))
    }
    get("/api/players/{id}/attempts") {
        val id=validId(call.parameters["id"])
        if(id==null) { call.respond(HttpStatusCode.BadRequest,ErrorView("Invalid player ID"));return@get }
        if(ManorDatabase.load(id)==null) { call.respond(HttpStatusCode.NotFound,ErrorView("Player not found"));return@get }
        call.respond(CursedPaintingDB.attempts(id,null))
    }
    get("/api/attempts") { call.respond(CursedPaintingDB.attempts(null,null)) }
    get("/api/attempts/{id}") {
        val id=validId(call.parameters["id"])
        if(id==null) { call.respond(HttpStatusCode.BadRequest,ErrorView("Invalid attempt ID"));return@get }
        val item=CursedPaintingDB.attempt(id)
        if(item==null) call.respond(HttpStatusCode.NotFound,ErrorView("Attempt not found")) else call.respond(item)
    }
    post("/api/attempts") {
        try { call.respond(HttpStatusCode.Created,CursedPaintingDB.start(call.receive<StartPuzzle>())) }
        catch(e:NoSuchElementException) { call.respond(HttpStatusCode.NotFound,ErrorView(e.message?:"Not found")) }
        catch(e:IllegalArgumentException) { call.respond(HttpStatusCode.BadRequest,ErrorView(e.message?:"Invalid request")) }
    }
    patch("/api/attempts/{id}") {
        val id=validId(call.parameters["id"])
        if(id==null) { call.respond(HttpStatusCode.BadRequest,ErrorView("Invalid attempt ID"));return@patch }
        try {
            val item=CursedPaintingDB.guess(id,call.receive<RuneGuess>())
            if(item==null) call.respond(HttpStatusCode.NotFound,ErrorView("Attempt not found")) else call.respond(item)
        } catch(e:IllegalArgumentException) { call.respond(HttpStatusCode.BadRequest,ErrorView(e.message?:"Invalid runes")) }
    }
    delete("/api/attempts/{id}") {
        val id=validId(call.parameters["id"])
        if(id==null) { call.respond(HttpStatusCode.BadRequest,ErrorView("Invalid attempt ID"));return@delete }
        if(CursedPaintingDB.delete(id)) call.respond(HttpStatusCode.NoContent)
        else call.respond(HttpStatusCode.NotFound,ErrorView("Attempt not found"))
    }
}
