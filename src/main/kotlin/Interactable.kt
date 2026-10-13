interface Interactable {

    val interactionName: String

    fun interact(player: Character)
}

class TreasureChest(
    override val interactionName: String,
    private val treasure: Item
) : Interactable {

    private var opened: Boolean = false

    override fun interact(player: Character) {

        if (opened) {
            println("$interactionName is already empty.")
            return
        }

        println("You open $interactionName.")
        println("Inside you find: ${treasure.name}!")

        player.addItem(treasure)

        opened = true
    }
}