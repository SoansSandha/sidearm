package io.github.soanssandha.sidearm.core.config

/**
 * The activity a triple press opens. It mirrors `android.content.ComponentName`, which `:core` can't use, and is
 * stored as [flatten] text, the same as `ComponentName.flattenToString()`.
 */
data class TargetComponent(val packageName: String, val className: String) {
    init {
        require(PACKAGE_NAME.matches(packageName)) { "Invalid package name: '$packageName'" }
        require(CLASS_NAME.matches(className)) { "Invalid class name: '$className'" }
    }

    fun flatten(): String = "$packageName/$className"

    companion object {
        // Android requires an ASCII package name with at least two segments.
        private val PACKAGE_NAME = Regex("""[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+""")

        // Class names are Java identifiers, which may use any Unicode letter or digit.
        private val CLASS_NAME = Regex("""[\p{L}_$][\p{L}\p{N}_$]*(\.[\p{L}_$][\p{L}\p{N}_$]*)*""")

        /**
         * Reads `package/class`, where a class starting with `.` is relative to the package, as in
         * `ComponentName.unflattenFromString()`. Returns null for anything malformed.
         */
        fun parse(flat: String): TargetComponent? {
            val packageName = flat.substringBefore('/', missingDelimiterValue = "")
            val rawClassName = flat.substringAfter('/', missingDelimiterValue = "")
            val className = if (rawClassName.startsWith('.')) packageName + rawClassName else rawClassName
            val valid = PACKAGE_NAME.matches(packageName) && CLASS_NAME.matches(className)
            return if (valid) TargetComponent(packageName, className) else null
        }
    }
}
