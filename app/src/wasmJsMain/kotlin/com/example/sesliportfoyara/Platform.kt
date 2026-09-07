package com.example.sesliportfoyara

@JsFun("() => Date.now()")
external fun jsDateNow(): Double

actual fun getCurrentTimeMillis(): Long = jsDateNow().toLong()
