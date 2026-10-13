import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

class Game {

    private lateinit var player: Character

    private val achievements: MutableSet<Achievement> = mutableSetOf()

    private val discoveredPaintings: MutableList<Painting> = mutableListOf()

    private val rooms: MutableMap<String, Room> = mutableMapOf()

    private var enemiesDefeated: Int = 0

    private var paintingsEntered: Int = 0

    private var roomsVisited: Int = 0

    private var running: Boolean = true


    suspend fun start() {

        showTitle()

        createPlayer()

        setupWorld()

        giveStartingItem()

        println()
        println("A distant clock strikes somewhere inside the manor...")
        println("The doors behind you suddenly lock.")
        println()
        println("There must be a way out.")

        while (running && player.isAlive()) {
            showMainMenu()
        }

        if (!player.isAlive()) {
            println()
            println("The darkness of the manor surrounds you...")
            println("GAME OVER")
        }

        println()
        println("Thank you for playing MYSTICAL MANOR.")
    }


    private fun showTitle() {

        println("======================================")
        println("=          MYSTICAL MANOR            =")
        println("=          Console Adventure         =")
        println("======================================")

        println()
        println("Welcome, traveler.")
        println()
        println("You wake up inside a mysterious manor...")
    }


    private fun createPlayer() {

        println()
        print("Enter your name: ")

        val name: String = readln().ifBlank { "Traveler" }

        println()
        println("Welcome, $name.")
        println()
        println("Choose your character:")
        println()
        println("1. Artist")
        println("2. Explorer")
        println("3. Mage")


        while (true) {

            print("\n> ")

            when (readln()) {

                "1" -> {
                    player = Artist(name)
                    break
                }

                "2" -> {
                    player = Explorer(name)
                    break
                }

                "3" -> {
                    player = Mage(name)
                    break
                }

                else -> {
                    println(
                        "Please choose 1, 2, or 3."
                    )
                }
            }
        }


        println()
        val article =
            if (player.characterClass.first().lowercaseChar() in listOf('a', 'e', 'i', 'o', 'u')) {
                "an"
            } else {
                "a"
            }

        println("You are now $article ${player.characterClass}.")
        println("Health: ${player.health}")
        println("Gold: ${player.gold}")
        println(
            "Special ability: ${player.specialAbilityDescription}"
        )
    }


    private fun setupWorld() {

        val starryNight = Painting(
            title = "The Starry Night",
            worldDescription =
                "A dreamlike village lies beneath a swirling sky of living stars.",
            examinationText =
                "The stars move slowly across the canvas. " +
                        "For a moment, you feel a cold wind coming from inside the painting."
        )

        val enchantedGarden = Painting(
            title = "The Enchanted Garden",
            worldDescription =
                "An endless moonlit garden filled with impossible flowers.",
            examinationText =
                "Silver flowers turn toward you as if they can see beyond the canvas."
        )

        val forgottenKingdom = Painting(
            title = "The Forgotten Kingdom",
            worldDescription =
                "Ruined towers rise from a kingdom lost beneath crimson clouds.",
            examinationText =
                "A tiny golden light flickers inside one of the painted castle windows."
        )


        val gallery = Room(
            name = "Gallery",
            description =
                "Tall paintings cover the walls. " +
                        "Most are silent, but one canvas seems strangely alive.",
            objects = mutableListOf(starryNight)
        )


        val moonCrystal = Item(
            name = "Moon Crystal",
            description = "A valuable crystal glowing with pale blue light.",
            value = 80,
            type = ItemType.TREASURE
        )

        val chest = TreasureChest(
            interactionName = "Ancient Treasure Chest",
            treasure = moonCrystal
        )


        val library = Room(
            name = "Library",
            description =
                "Dusty books rise from floor to ceiling. " +
                        "An old chest rests beneath a broken window.",
            objects = mutableListOf(chest)
        )


        val secretGallery = Room(
            name = "Secret Gallery",
            description =
                "A hidden corridor leads to two paintings untouched by time.",
            objects = mutableListOf(
                enchantedGarden,
                forgottenKingdom
            )
        )


        rooms["gallery"] = gallery
        rooms["library"] = library
        rooms["secret"] = secretGallery
    }


    private fun giveStartingItem() {

        val startingItem: Item = when (player) {

            is Artist -> Item(
                name = "Magic Brush",
                description = "A mysterious brush capable of revealing hidden magic.",
                value = 100,
                type = ItemType.MAGIC
            )

            is Explorer -> Item(
                name = "Explorer's Compass",
                description = "A strange compass that reacts to hidden treasures.",
                value = 90,
                type = ItemType.MAGIC
            )

            is Mage -> Item(
                name = "Arcane Crystal",
                description = "A crystal containing concentrated magical energy.",
                value = 120,
                type = ItemType.MAGIC
            )

            else -> Item(
                name = "Old Lantern",
                description = "A simple lantern.",
                value = 10,
                type = ItemType.MAGIC
            )
        }

        player.inventory.add(startingItem)

        player.inventory.add(
            Item(
                name = "Healing Potion",
                description = "Restores 30 health.",
                value = 25,
                type = ItemType.HEALING,
                healingPower = 30
            )
        )
    }


    private suspend fun showMainMenu() {

        println()
        println("================================")
        println("          GRAND HALL")
        println("================================")
        println()
        println("${player.name} — ${player.characterClass}")
        println("Health: ${player.health}/${player.maxHealth}")
        println("Gold: ${player.gold}")

        val itemNames: List<String> =
            player.inventory.map { item -> item.name }

        println("Inventory: $itemNames")

        println()
        println("What would you like to do?")
        println()
        println("1. Explore room")
        println("2. View inventory")
        println("3. View discovered paintings")
        println("4. View player statistics")
        println("5. Use special ability")
        println("0. Exit")

        print("\n> ")

        when (readln()) {

            "1" -> chooseRoom()

            "2" -> showInventory()

            "3" -> showPaintings()

            "4" -> showStatistics()

            "5" -> player.useSpecialAbility()

            "0" -> {
                println("You decide to leave the mystery for another day...")
                running = false
            }

            else -> println("Unknown command.")
        }
    }


    private suspend fun chooseRoom() {

        println()
        println("--- EXPLORE THE MANOR ---")
        println()
        println("1. Gallery")
        println("2. Library")

        if (roomsVisited >= 2 || player is Artist) {
            println("3. Secret Gallery")
        }

        println("0. Return")

        print("\n> ")

        val roomKey = when (readln()) {

            "1" -> "gallery"

            "2" -> "library"

            "3" -> {
                if (roomsVisited >= 2 || player is Artist) {
                    "secret"
                } else {
                    println("You cannot find such a room.")
                    return
                }
            }

            "0" -> return

            else -> {
                println("Invalid room.")
                return
            }
        }

        val room: Room = rooms[roomKey] ?: return

        exploreRoom(room)
    }


    private suspend fun exploreRoom(room: Room) {

        println()
        println("You enter the ${room.name}...")

        if (!room.visited) {
            room.visited = true
            roomsVisited++
        }

        room.describe()

        if (room.objects.isEmpty()) {
            handleEvent(GameEvent.NothingHappened)
            return
        }

        println()
        println("Choose an object to investigate.")
        println("0. Return to the Grand Hall")

        print("\n> ")

        val choice: Int = readln().toIntOrNull() ?: return

        if (choice == 0) {
            return
        }

        val selectedObject =
            room.objects.getOrNull(choice - 1)

        if (selectedObject == null) {
            println("There is nothing there.")
            return
        }

        // Interface polymorphism:
        selectedObject.interact(player)

        when (selectedObject) {

            is Painting ->
                discoverPainting(selectedObject)

            is TreasureChest -> {
                unlockAchievement(
                    Achievement(
                        "Treasure Hunter",
                        "Open an ancient treasure chest."
                    )
                )
            }
        }
    }


    private suspend fun discoverPainting(painting: Painting) {

        if (!painting.discovered) {

            painting.discovered = true

            discoveredPaintings.add(painting)

            handleEvent(
                GameEvent.PaintingDiscovered(painting)
            )
        }

        while (true) {

            println()
            println("Something strange is happening...")
            println()
            println("1. Enter the painting")
            println("2. Examine the painting")
            println("3. Return to the hall")

            print("\n> ")

            when (readln()) {

                "1" -> {
                    enterPainting(painting)
                    return
                }

                "2" -> painting.examine()

                "3" -> return

                else -> println("Invalid choice.")
            }
        }
    }


    private suspend fun enterPainting(painting: Painting) = coroutineScope {

        paintingsEntered++

        println()
        println("Entering \"${painting.title}\"...")

        delay(700)

        println("Reality begins to distort...")

        delay(700)

        println("Loading painting world...")

        val atmosphereJob = launch {
            delay(400)
            println("You hear a distant whisper from beyond the canvas...")
        }

        atmosphereJob.join()

        delay(500)

        println()
        println("================================")
        println(painting.title.uppercase())
        println("================================")
        println(painting.worldDescription)

        if (paintingsEntered == 1) {

            unlockAchievement(
                Achievement(
                    "First Painting",
                    "Enter your first magical painting."
                )
            )
        }

        if (!painting.completed) {

            val guardian = Enemy(
                name = "Shadow Guardian",
                health = 40,
                minDamage = 6,
                maxDamage = 12,
                experienceReward = 50,
                goldReward = 20
            )

            handleEvent(
                GameEvent.EnemyEncounter(guardian)
            )

            val won = battle(guardian)

            if (won) {
                painting.completed = true

                player.inventory.add(
                    Item(
                        name = "Golden Key",
                        description =
                            "A mysterious key recovered from the painting world.",
                        value = 50,
                        type = ItemType.KEY
                    )
                )

                handleEvent(
                    GameEvent.FoundItem(
                        player.inventory.last()
                    )
                )
            }

        } else {
            println()
            println("The world is peaceful now.")
            println("The Shadow Guardian has already been defeated.")
        }
    }


    private fun battle(enemy: Enemy): Boolean {

        println()
        println("A ${enemy.name} appears!")

        while (enemy.isAlive() && player.isAlive()) {

            println()
            println("${enemy.name} HP: ${enemy.health}")
            println("Your HP: ${player.health}/${player.maxHealth}")
            println()
            println("Choose action:")
            println()
            println("1. Attack")
            println("2. Use healing item")
            println("3. Use special ability")
            println("4. Escape")

            print("\n> ")

            when (readln()) {

                "1" -> {

                    val damage = player.attackDamage()

                    enemy.takeDamage(damage)

                    println()
                    println("You attack the ${enemy.name}!")
                    println("Damage: $damage")
                    println("${enemy.name} HP: ${enemy.health}")
                }


                "2" -> {

                    val usedItem = useHealingItem()

                    if (!usedItem) {
                        continue
                    }
                }


                "3" -> {

                    player.useSpecialAbility()

                    val bonusDamage: Int = when (player) {

                        is Artist -> (15..22).random()

                        is Explorer -> (12..18).random()

                        is Mage -> (20..28).random()

                        else -> 10
                    }

                    enemy.takeDamage(bonusDamage)

                    println(
                        "Special ability deals $bonusDamage damage!"
                    )
                }


                "4" -> {

                    println(
                        "You escape through the unstable portal!"
                    )

                    return false
                }


                else -> {
                    println("Invalid action.")
                    continue
                }
            }


            if (enemy.isAlive()) {

                val enemyDamage = enemy.attack()

                player.takeDamage(enemyDamage)

                println()
                println(
                    "${enemy.name} attacks you for $enemyDamage damage!"
                )
            }
        }


        if (!player.isAlive()) {
            return false
        }


        println()
        println("${enemy.name} defeated!")

        player.experience += enemy.experienceReward
        player.gold += enemy.goldReward

        enemiesDefeated++

        println(
            "You received +${enemy.experienceReward} XP"
        )

        println(
            "You received +${enemy.goldReward} Gold"
        )


        unlockAchievement(
            Achievement(
                "Monster Hunter",
                "Defeat your first enemy."
            )
        )


        return true
    }


    private fun useHealingItem(): Boolean {

        // FILTER requirement
        val healingItems: List<Item> =
            player.inventory.filter { item ->
                item.type == ItemType.HEALING
            }

        if (healingItems.isEmpty()) {

            println("You have no healing items.")

            return false
        }

        println()
        println("Healing items:")

        healingItems.forEachIndexed { index, item ->

            println(
                "${index + 1}. ${item.name} (+${item.healingPower} HP)"
            )
        }

        println("0. Cancel")

        print("\n> ")

        val choice =
            readln().toIntOrNull() ?: return false

        if (choice == 0) {
            return false
        }

        val item =
            healingItems.getOrNull(choice - 1)
                ?: return false

        player.heal(item.healingPower)

        player.inventory.remove(item)

        return true
    }


    private fun showInventory() {

        println()
        println("================================")
        println("            INVENTORY")
        println("================================")

        if (player.inventory.isEmpty()) {

            println("Your inventory is empty.")

            return
        }


        player.inventory.forEachIndexed { index, item ->

            println()
            println("${index + 1}. ${item.name}")
            println("   ${item.description}")
            println("   Type: ${item.type}")
            println("   Value: ${item.value} gold")
        }


        // MAP
        val itemNames: List<String> =
            player.inventory.map { it.name }


        // FILTER
        val healingItems: List<Item> =
            player.inventory.filter {
                it.type == ItemType.HEALING
            }


        // REDUCE
        val totalValue: Int =
            if (player.inventory.isEmpty()) {
                0
            } else {
                player.inventory
                    .map { it.value }
                    .reduce { total, value ->
                        total + value
                    }
            }


        println()
        println("Item names: $itemNames")
        println("Healing items: ${healingItems.size}")
        println("Total inventory value: $totalValue gold")
    }


    private fun showPaintings() {

        println()
        println("================================")
        println("       DISCOVERED PAINTINGS")
        println("================================")


        if (discoveredPaintings.isEmpty()) {

            println("You have not discovered any paintings yet.")

            return
        }


        discoveredPaintings.forEachIndexed { index, painting ->

            val status =
                if (painting.completed) {
                    "Guardian defeated"
                } else {
                    "Unresolved"
                }

            println(
                "${index + 1}. ${painting.title} — $status"
            )
        }
    }


    private fun showStatistics() {

        println()
        println("================================")
        println("        PLAYER STATISTICS")
        println("================================")

        println("Name: ${player.name}")
        println("Class: ${player.characterClass}")
        println("Health: ${player.health}/${player.maxHealth}")
        println("Gold: ${player.gold}")
        println("Experience: ${player.experience}")
        println("Rooms visited: $roomsVisited")
        println("Paintings discovered: ${discoveredPaintings.size}")
        println("Paintings entered: $paintingsEntered")
        println("Enemies defeated: $enemiesDefeated")
        println("Achievements: ${achievements.size}")


        calculateInventoryValue { value ->

            println(
                "Inventory value: $value gold"
            )
        }


        println()
        println("--- ACHIEVEMENTS ---")


        if (achievements.isEmpty()) {

            println("No achievements yet.")

        } else {

            achievements.forEach {
                println("• ${it.name} — ${it.description}")
            }
        }
    }


    // Higher-order function:
    // receives another function as a parameter.
    private fun calculateInventoryValue(
        displayResult: (Int) -> Unit
    ) {

        val total: Int =
            if (player.inventory.isEmpty()) {

                0

            } else {

                player.inventory
                    .map { item -> item.value }
                    .reduce { sum, value ->
                        sum + value
                    }
            }


        // Lambda/function passed to this method is executed here.
        displayResult(total)
    }


    private fun handleEvent(event: GameEvent) {

        when (event) {

            is GameEvent.FoundItem -> {

                println()
                println("✨ ITEM FOUND")
                println(event.item.name)
                println(event.item.description)
            }


            is GameEvent.EnemyEncounter -> {

                println()
                println("⚔ ENEMY ENCOUNTER")
                println("${event.enemy.name} blocks your path!")
            }


            is GameEvent.PaintingDiscovered -> {

                println()
                println("✨ PAINTING DISCOVERED")
                println("\"${event.painting.title}\"")
            }


            GameEvent.NothingHappened -> {

                println(
                    "The room is silent. Nothing happens."
                )
            }
        }
    }


    private fun unlockAchievement(
        achievement: Achievement
    ) {

        // Set prevents duplicate achievements.
        val added: Boolean =
            achievements.add(achievement)


        if (added) {

            println()
            println("✨ ACHIEVEMENT UNLOCKED")
            println(achievement.name)
            println(achievement.description)
        }
    }
}