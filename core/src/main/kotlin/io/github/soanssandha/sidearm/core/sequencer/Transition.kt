package io.github.soanssandha.sidearm.core.sequencer

/** What [reduce] decided: the next state, and the effects for the hook to run in order. */
data class Transition(val state: State, val effects: List<Effect> = emptyList())
