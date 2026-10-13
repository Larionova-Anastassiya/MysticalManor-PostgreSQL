abstract class Character(
    val name: String,
    val characterClass: String,
    var health: Int,
    val maxHealth: Int,
    var gold: Int,
    var experience: Int
) {

    val inventory: MutableList<Item> = mutableListOf()

    abstract val specialAbilityDescription: String

    abstract fun useSpecialAbility()

    open fun attackDamage(): Int {
        return (10..18).random()
    }

    fun addItem(item: Item) {
        inventory.add(item)
        println("Added to inventory: ${item.name}")
    }

    fun heal(amount: Int) {
        val oldHealth = health
        health = (health + amount).coerceAtMost(maxHealth)

        println("Recovered ${health - oldHealth} HP.")
        println("Current health: $health/$maxHealth")
    }

    fun takeDamage(amount: Int) {
        health = (health - amount).coerceAtLeast(0)
    }

    fun isAlive(): Boolean {
        return health > 0
    }
}


class Artist(name: String) : Character(
    name = name,
    characterClass = "Artist",
    health = 100,
    maxHealth = 100,
    gold = 20,
    experience = 0
) {

    override val specialAbilityDescription: String =
        "Reveal hidden paintings and use the Magic Brush."

    override fun useSpecialAbility() {
        println("$name raises the Magic Brush.")
        println("Hidden magical details begin to glow...")
    }

    override fun attackDamage(): Int {
        return (12..20).random()
    }
}


class Explorer(name: String) : Character(
    name = name,
    characterClass = "Explorer",
    health = 120,
    maxHealth = 120,
    gold = 25,
    experience = 0
) {

    override val specialAbilityDescription: String =
        "Discover hidden treasures and secret paths."

    override fun useSpecialAbility() {
        println("$name carefully examines the surroundings.")
        println("The Explorer notices details others would miss...")
    }

    override fun attackDamage(): Int {
        return (10..18).random()
    }
}


class Mage(name: String) : Character(
    name = name,
    characterClass = "Mage",
    health = 90,
    maxHealth = 90,
    gold = 30,
    experience = 0
) {

    override val specialAbilityDescription: String =
        "Channel arcane energy for powerful attacks."

    override fun useSpecialAbility() {
        println("$name channels mysterious arcane energy.")
        println("The air around you begins to shimmer...")
    }

    override fun attackDamage(): Int {
        return (15..23).random()
    }
}