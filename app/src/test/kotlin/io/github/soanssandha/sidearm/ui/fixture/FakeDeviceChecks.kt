package io.github.soanssandha.sidearm.ui.fixture

import io.github.soanssandha.sidearm.data.BuildCheck
import io.github.soanssandha.sidearm.data.DeviceChecks
import io.github.soanssandha.sidearm.data.DoublePress

class FakeDeviceChecks(var doublePress: DoublePress = DoublePress.CAMERA) : DeviceChecks {
    override fun doublePress(): DoublePress = doublePress

    override val build = BuildCheck(id = "CP3A.261005.005", tested = true)
}
