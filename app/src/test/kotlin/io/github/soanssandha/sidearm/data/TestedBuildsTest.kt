package io.github.soanssandha.sidearm.data

import kotlin.test.Test
import kotlin.test.assertTrue

class TestedBuildsTest {

    /** brand/product/device:release/id/incremental:type/tags, the whole of `Build.FINGERPRINT`. */
    private val fullFingerprint = Regex("""[^/:]+/[^/:]+/[^/:]+:[^/:]+/[^/:]+/[^/:]+:[^/:]+/[^/:]+""")

    @Test
    fun `every entry is a full fingerprint, with the build type and tags the phone reports`() {
        TestedBuilds.FINGERPRINTS.forEach { fingerprint ->
            assertTrue(fullFingerprint.matches(fingerprint), "Not a full fingerprint: $fingerprint")
        }
    }
}
