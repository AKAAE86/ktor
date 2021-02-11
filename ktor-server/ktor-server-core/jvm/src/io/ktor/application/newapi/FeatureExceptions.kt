/*
 * Copyright 2014-2020 JetBrains s.r.o and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package io.ktor.application.newapi

public class FeatureNotInstalledException(private val featureName: String): Exception() {
    override val message: String?
        get() = "Feature $featureName is not installed but required"
}
