package com.tdcreator.app.navigation

/**
 * Centralized navigation routes. Compose destinations are simple string constants; each
 * screen reads its arguments from the [androidx.navigation.NavBackStackEntry] savedStateHandle.
 */
object Routes {
    const val HOME = "home"
    const val CAPTURE = "capture"
    const val GALLERY = "gallery"
    const val UPLOAD = "upload"
    const val JOBS = "jobs"
    const val JOB_DETAIL = "jobs/{jobId}"
    const val VIEWER = "viewer/{jobId}"
    const val SHARE = "share/{jobId}"
    const val SETTINGS = "settings"

    fun jobDetail(jobId: String) = "jobs/$jobId"
    fun viewer(jobId: String) = "viewer/$jobId"
    fun share(jobId: String) = "share/$jobId"
}
