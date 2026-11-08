package com.airpodsapp.service

import android.content.Context
import android.media.AudioManager
import android.os.SystemClock
import android.view.KeyEvent

/** Pauses/resumes whatever app currently owns the media session, via media key events. */
class MediaControl(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)

    val isPlaying: Boolean get() = audio.isMusicActive

    fun pause() = press(KeyEvent.KEYCODE_MEDIA_PAUSE)

    fun play() = press(KeyEvent.KEYCODE_MEDIA_PLAY)

    private fun press(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }
}
