class Room(
    val name: String,
    val description: String,
    val objects: MutableList<Interactable> = mutableListOf()
) {

    var visited: Boolean = false

    fun describe() {
        println()
        println("================================")
        println(name.uppercase())
        println("================================")
        println(description)

        if (objects.isNotEmpty()) {
            println()
            println("Interesting objects:")

            objects.forEachIndexed { index, interactable ->
                println("${index + 1}. ${interactable.interactionName}")
            }
        }
    }
}