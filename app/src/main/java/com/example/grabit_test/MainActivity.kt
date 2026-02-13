package com.example.grabitTest

import android.content.Context
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.grabitTest.databinding.ActivityMainBinding

/**
 * GrabIT 메인 액티비티.
 * 상단바(Toolbar), 하단 네비게이션 바(BottomNavigationView), 프래그먼트 네비게이션만 담당.
 * 볼륨 키: 짧게 누름 = 볼륨 조절, 0.5초 이상 길게 누름 = 앱 기능(음성 인식 등).
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var audioManager: AudioManager
    private lateinit var sharedViewModel: SharedViewModel
    private val keyHandler = Handler(Looper.getMainLooper())
    private val LONG_PRESS_TIMEOUT = 500L

    private val longPressRunnable = Runnable {
        isLongPressTriggered = true
        (window.decorView as? View)?.performHapticFeedback(
            android.view.HapticFeedbackConstants.LONG_PRESS
        )
        sharedViewModel.triggerVolumeLongPress()
    }
    private var isLongPressTriggered = false
    private var currentLongPressedKeyCode = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        sharedViewModel = ViewModelProvider(this)[SharedViewModel::class.java]

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.toolbar.title = "GrabIT"
        binding.bottomNavigation.setupWithNavController(navController)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val keyCode = event.keyCode

        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            keyCode == KeyEvent.KEYCODE_VOLUME_DOWN ||
            keyCode == KeyEvent.KEYCODE_HEADSETHOOK ||
            keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {

            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        isLongPressTriggered = false
                        currentLongPressedKeyCode = keyCode
                        keyHandler.postDelayed(longPressRunnable, LONG_PRESS_TIMEOUT)
                    }
                    return true
                }
                KeyEvent.ACTION_UP -> {
                    keyHandler.removeCallbacks(longPressRunnable)
                    if (!isLongPressTriggered) {
                        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                        } else if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
                            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                        }
                    }
                    currentLongPressedKeyCode = -1
                    return true
                }
                else -> return true
            }
        }

        return super.dispatchKeyEvent(event)
    }
}
