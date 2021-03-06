package com.jetbrains.packagesearch.intellij.plugin.fus

import com.intellij.openapi.project.Project

interface PackageSearchEventsLoggerProvider {

    fun logEvent(
        project: Project,
        groupId: String,
        event: String,
        @Suppress("SameParameterValue") version: String,
        extras: Array<out Pair<String, Any>>
    )
}
