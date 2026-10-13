class Painting(
    val title: String,
    val worldDescription: String,
    val examinationText: String
) : Interactable {

    var discovered: Boolean = false
    var completed: Boolean = false

    override val interactionName: String
        get() = title

    override fun interact(player: Character) {
        println()
        println("${player.name} approaches \"$title\".")
        println("The surface of the painting seems to move.")
    }

    fun examine() {
        println()
        println("--- EXAMINING \"$title\" ---")
        println(examinationText)
    }
}