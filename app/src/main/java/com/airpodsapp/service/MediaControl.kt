package com.airpodsapp.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.SystemClock
import android.util.Log
import android.view.KeyEvent

/**
 * Pauses and resumes whatever is playing.
 *
 * Media keys only reach the app owning the media session, and some players ignore them
 * (YouTube Shorts: session reported as STOPPED while audio plays). So we also hold transient
 * audio focus during the pause: players pause on losing it and resume on getting it back.
 */
class MediaControl(context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()
    private var heldFocus: AudioFocusRequest? = null

    val isPlaying: Boolean get() = audio.isMusicActive

    fun pause() {
        press(KeyEvent.KEYCODE_MEDIA_PAUSE)
        if (heldFocus != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change ->
                // Another app took over for good: nothing left for us to give back.
                if (change == AudioManager.AUDIOFOCUS_LOSS) abandonHeldFocus()
            }
            .build()
        val result = audio.requestAudioFocus(request)
        Log.i(TAG, "Audio focus request result: $result")
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) heldFocus = request
    }

    fun play() {
        abandonHeldFocus()
        press(KeyEvent.KEYCODE_MEDIA_PLAY)
    }

    /**
     * Gives focus back without letting players resume, for when the AirPods disconnect
     * mid-pause: a brief permanent request makes them drop their "resume on gain" state,
     * so audio doesn't restart on the phone speaker.
     */
    fun releaseWithoutResuming() {
        if (heldFocus == null) return
        val permanent = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { }
            .build()
        audio.requestAudioFocus(permanent)
        abandonHeldFocus()
        audio.abandonAudioFocusRequest(permanent)
    }

    private fun abandonHeldFocus() {
        heldFocus?.let(audio::abandonAudioFocusRequest)
        heldFocus = null
    }

    private fun press(keyCode: Int) {
        val now = SystemClock.uptimeMillis()
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        audio.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
    }

    private companion object {
        const val TAG = "MediaControl"
    }
}
