sealed class GameEvent {

    data class FoundItem(
        val item: Item
    ) : GameEvent()

    data class EnemyEncounter(
        val enemy: Enemy
    ) : GameEvent()

    data class PaintingDiscovered(
        val painting: Painting
    ) : GameEvent()

    data object NothingHappened : GameEvent()
}