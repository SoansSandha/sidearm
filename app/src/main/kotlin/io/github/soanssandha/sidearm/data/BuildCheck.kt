package io.github.soanssandha.sidearm.data

/** The running Android build, by its short ID (such as CP3A.261005.005), and whether its hook targets were checked. */
data class BuildCheck(val id: String, val tested: Boolean)
