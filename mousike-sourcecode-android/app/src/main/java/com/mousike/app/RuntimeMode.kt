package com.mousike.app
/** Enabled only by an explicit debug launch extra; the regular app always uses the live catalog. */
object RuntimeMode { @Volatile var offlineFixture=false }
