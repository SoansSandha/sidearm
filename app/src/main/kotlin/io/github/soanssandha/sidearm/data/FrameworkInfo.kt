package io.github.soanssandha.sidearm.data

/** The Xposed framework hosting Sidearm, such as Vector 2.2 with API 102. */
data class FrameworkInfo(val name: String, val version: String, val apiVersion: Int) {
    /** The version without the build details some frameworks add: Vector reports "2.2 (3080) 88f8e1fa-…". */
    val shortVersion: String get() = version.trim().substringBefore(' ')
}
