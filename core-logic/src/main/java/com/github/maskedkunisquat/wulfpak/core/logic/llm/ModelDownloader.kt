package com.github.maskedkunisquat.wulfpak.core.logic.llm

fun interface ModelDownloader {
    fun enqueue(modelFile: String, url: String, sha256: String?)
}
