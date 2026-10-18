package dev.sumanth.spd.utils

import java.io.IOException

/** The server answered a download request with an HTTP error status. */
class DownloadHttpException(val code: Int) : IOException("HTTP $code")
