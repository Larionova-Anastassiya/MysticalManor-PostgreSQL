class Enemy(
    val name: String,
    var health: Int,
    val minDamage: Int,
    val maxDamage: Int,
    val experienceReward: Int,
    val goldReward: Int
) {

    fun attack(): Int {
        return (minDamage..maxDamage).random()
    }

    fun takeDamage(amount: Int) {
        health = (health - amount).coerceAtLeast(0)
    }

    fun isAlive(): Boolean {
        return health > 0
    }
}