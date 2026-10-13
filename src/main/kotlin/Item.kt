enum class ItemType {
    HEALING,
    MAGIC,
    KEY,
    TREASURE
}

data class Item(
    val name: String,
    val description: String,
    val value: Int,
    val type: ItemType,
    val healingPower: Int = 0
)