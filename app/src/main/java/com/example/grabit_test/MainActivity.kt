package com.example.grabitTest

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.example.grabitTest.databinding.ActivityMainBinding

/**
 * GrabIT 메인 액티비티.
 * 상단바(Toolbar), 하단 네비게이션 바(BottomNavigationView), 프래그먼트 네비게이션만 담당.
 * CameraX, YOLOX, STT/TTS 등 핵심 로직은 HomeFragment에 이관됨.
 * 탭 전환 시 상태 초기화는 HomeFragment.onPause()에서 처리.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController

        binding.toolbar.title = "GrabIT"
        binding.bottomNavigation.setupWithNavController(navController)
    }
}
