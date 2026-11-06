package cc.juanma.mcm3d.ui

import android.content.res.Resources
import cc.juanma.mcm3d.R
import cc.juanma.mcm3d.core.route.Instruction
import cc.juanma.mcm3d.core.route.TurnDirection

/** Title and detail of a guidance instruction, shared by the outing screen and the notification. */
fun instructionText(resources: Resources, instruction: Instruction): Pair<String, String?> = when (instruction) {
  is Instruction.GoToStart ->
    resources.getString(R.string.guide_go_to_start) to resources.getString(R.string.guide_at_distance, formatDistance(instruction.distanceM))
  is Instruction.Rejoin ->
    resources.getString(R.string.guide_rejoin) to resources.getString(R.string.guide_at_distance, formatDistance(instruction.distanceM))
  is Instruction.TurnAhead ->
    resources.getString(turnText(instruction.direction)) to resources.getString(R.string.guide_in, formatDistance(instruction.distanceM))
  is Instruction.Continue ->
    resources.getString(R.string.guide_continue) to resources.getString(R.string.guide_for_distance, formatDistance(instruction.distanceM))
  is Instruction.Arrived -> resources.getString(R.string.guide_arrived) to null
}

private fun turnText(direction: TurnDirection): Int = when (direction) {
  TurnDirection.SLIGHT_LEFT -> R.string.guide_slight_left
  TurnDirection.LEFT -> R.string.guide_left
  TurnDirection.SHARP_LEFT -> R.string.guide_sharp_left
  TurnDirection.SLIGHT_RIGHT -> R.string.guide_slight_right
  TurnDirection.RIGHT -> R.string.guide_right
  TurnDirection.SHARP_RIGHT -> R.string.guide_sharp_right
  TurnDirection.U_TURN -> R.string.guide_u_turn
}
