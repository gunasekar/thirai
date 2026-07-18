package com.thirai.ui

import com.journeyapps.barcodescanner.CaptureActivity

/**
 * The ZXing scanner screen, locked to portrait. The default capture activity
 * follows the sensor and often opens sideways; this subclass is declared
 * `screenOrientation="portrait"` in the manifest so the QR scan stays upright.
 */
class PortraitCaptureActivity : CaptureActivity()
