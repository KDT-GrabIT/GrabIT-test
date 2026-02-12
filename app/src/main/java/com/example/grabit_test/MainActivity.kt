package com.example.grabitTest

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.graphics.Bitmap
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.app.AlertDialog
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.ar.core.Config
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Session
import com.google.ar.core.exceptions.NotYetAvailableException
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import android.graphics.RectF
import android.view.Surface
import android.media.Image
import java.nio.ByteOrder
import com.example.grabitTest.databinding.ActivityMainBinding
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.os.Handler
import android.os.Looper
import com.example.grabitTest.data.synonym.SynonymRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val arExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var arSession: Session? = null
    @Volatile private var arRunning = false
    private var displayRotationDegrees = 0

    // 자동 탐색 메시지 (ARCore에는 줌 없음; 거리 > 2000mm 시 "너무 멂" 안내)
    private val scanHandler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private val SCAN_INITIAL_DELAY_MS = 3000L

    // AI 모델
    private var yoloxInterpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var handLandmarker: HandLandmarker? = null

    // LIVE_STREAM: Hands 결과는 콜백으로 옴 → 최신 값만 보관
    private val latestHandsResult = AtomicReference<HandLandmarkerResult?>(null)

    // FPS 측정
    private var frameCount = 0
    private var lastFpsTime = System.currentTimeMillis()

    private var lastYoloxMaxConf = 0f

    /** 클래스 인덱스 → 이름 (assets/classes.txt, 한 줄에 하나, # 무시) */
    private var classLabels: List<String> = emptyList()

    enum class SearchState { SEARCHING, LOCKED }
    private var searchState = SearchState.SEARCHING
    private var frozenBox: OverlayView.DetectionBox? = null
    private var frozenImageWidth = 0
    private var frozenImageHeight = 0
    /** State B에서 계속 추적할 타겟 라벨 (락 시점의 box.label) */
    private var lockedTargetLabel: String = ""

    /** 같은 객체가 연속 N프레임 감지됐을 때 고정 */
    private val LOCK_CONFIRM_FRAMES = 2
    private var pendingLockBox: OverlayView.DetectionBox? = null
    private var pendingLockCount = 0

    /** LOCKED 시 YOLOX 검증+보정: N프레임마다 1회 실행 (3으로 줄여 드리프트 보정 빈도 증가) */
    private val VALIDATION_INTERVAL = 3
    private val VALIDATION_FAIL_LIMIT = 3
    private var validationFailCount = 0
    private var lockedFrameCount = 0

    private lateinit var gyroManager: GyroTrackingManager
    private val opticalFlowTracker = OpticalFlowTracker()

    private var wasOccluded = false  // occlusion → non-occlusion 전환 시 gyro 동기화용

    private val TARGET_CONFIDENCE_THRESHOLD = 0.5f  // 50% 이상 확신 시 고정 (탐지 용이하게 완화)
    private val currentTargetLabel = AtomicReference<String>("")

    // 거리 유도 (LOCKED): ARCore Depth API 실제 거리(mm) 기반
    private var lastGuidanceTime = 0L
    private val DISTANCE_NEAR_MM = 600
    private val DISTANCE_FAR_MM = 2000
    private val GUIDANCE_COOLDOWN_MS = 3000L

    // STT / TTS
    private var sttManager: STTManager? = null
    private var ttsManager: TTSManager? = null
    private var beepPlayer: BeepPlayer? = null
    private var voiceFlowController: VoiceFlowController? = null
    private val searchTimeoutHandler = Handler(Looper.getMainLooper())
    private var searchTimeoutRunnable: Runnable? = null
    @Volatile private var searchTimeoutAborted = false
    private val SEARCH_TIMEOUT_MS = 30_000L
    private val POSITION_ANNOUNCE_INTERVAL_MS = 5000L
    private var positionAnnounceRunnable: Runnable? = null
    /** 음성으로 찾기 요청한 상품 클래스(실제 일치 여부 검사용). null = 음성 플로우 아님 */
    private var voiceSearchTargetLabel: String? = null

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    private val REQUEST_CODE_PERMISSIONS = 10

    // 화면 상태
    enum class ScreenState { FIRST_SCREEN, CAMERA_SCREEN }
    private var screenState = ScreenState.FIRST_SCREEN

    // TTS / STT
    private var tts: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var ttsDetectedPlayed = false   // 객체 탐지 TTS 1회만
    private var ttsGrabPlayed = false      // 손 뻗어 잡으세요 TTS (LOCKED 후)
    private var ttsGrabbedPlayed = false   // 객체 잡았음 TTS 1회만
    private var ttsAskAnotherPlayed = false
    private var waitingForTouchConfirm = false  // (단순화 후 사용 안 함)
    /** STT NO_MATCH/타임아웃 시 "다시 말해주세요" TTS 후 재시도 횟수 (이 횟수만큼 반복) */
    private val STT_MAX_RETRIES = 3
    /** 터치 확인("상품에 닿았나요?") STT 재시도 카운트 */
    private var touchConfirmSttRetryCount = 0
    /** 상품명/맞습니까 대기 시 STT 재시도 카운트 */
    private var voiceFlowSttRetryCount = 0
    /** 확인 대기("맞습니까? 예라고 말해주세요") 단계에서 NO_MATCH 시 TTS 없이 재청취 횟수 */
    private var voiceConfirmSilentRetryCount = 0
    private var handsOverlapFrameCount = 0  // 손-박스 겹침 연속 프레임 수 (잘못된 잡기 판정용)
    private var pinchGrabFrameCount = 0     // 엄지+검지 잡기 판정 연속 프레임

    // 손 위치 안내 TTS (손을 더 뻗어주세요 등)
    private val handGuidanceHandler = Handler(Looper.getMainLooper())
    private var handGuidanceRunnable: Runnable? = null
    private var lastHandGuidanceTimeMs = 0L
    private val HAND_GUIDANCE_INTERVAL_MS = 5000L

    /** LOCKED 시 마지막으로 YOLOX 검증 성공한 시각. 박스를 잃은 뒤 2초 지나면 추론을 매 프레임 켜서 재탐지 */
    private var lastSuccessfulValidationTimeMs = 0L
    private val REACQUIRE_INFERENCE_AFTER_MS = 2000L
    /** near-contact 후 질문(STT) 플로우가 이미 진행 중인지 여부 (중복 트리거 방지) */
    private var touchConfirmInProgress = false
    /** analyzer에서 enterTouchConfirm을 큐에 넣었으면 true. UI에서 실행되기 전 다음 프레임이 또 넣는 것 방지 */
    private var touchConfirmScheduled = false

    /** 볼륨 업 길게 누르기 → 재시작. 짧게 누르면 볼륨만 조정 */
    private val VOLUME_LONG_PRESS_MS = 600L
    private val volumeLongPressHandler = Handler(Looper.getMainLooper())
    private var volumeUpLongPressRunnable: Runnable? = null
    private var volumeUpLongPressFired = false
    /** 키 반복(KEY_DOWN 연속) 시 runnable 중복 등록 방지 */
    private var volumeUpKeyDownScheduled = false

    /** 볼륨 다운 길게 누르기 → TTS 중지 후 바로 음성인식(STT) 시작 */
    private var volumeDownLongPressRunnable: Runnable? = null
    private var volumeDownLongPressFired = false
    private var volumeDownKeyDownScheduled = false

    // ---------- near-contact(touch) 판정 (시각장애인 UX, 현장 튜닝 포인트) ----------
    /** 타겟 박스 확장 비율. 확장된 박스 안에 엄지·검지 중간점이 있으면 touch (넓을수록 잡기 판정 수월) */
    private val TOUCH_BOX_EXPAND_RATIO = 0.22f
    /** touch=true 연속 N프레임이면 touchActive 전환 + TTS 1회 (낮을수록 빨리 잡았다고 인정) */
    private val TOUCH_CONFIRM_FRAMES = 4
    /** touch=false 연속 N프레임이면 touchActive 해제 (즉시 해제 방지) */
    private val RELEASE_HOLD_FRAMES = 10
    /** TTS 재발화 쿨다운(ms). 이 시간 동안은 재트리거 금지 */
    private val TOUCH_TTS_COOLDOWN_MS = 1800L

    private var touchFrameCount = 0
    private var releaseFrameCount = 0
    private var touchActive = false
    private var lastTouchTtsTimeMs = 0L
    /** 디버그: 엄지·검지 중간점 (이미지 픽셀). OverlayView에 전달용 */
    private var lastTouchMidpointPx: Pair<Float, Float>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 하단 메시지 박스가 네비게이션 바와 겹치지 않도록 시스템 하단 inset 반영
        ViewCompat.setOnApplyWindowInsetsListener(binding.messageOverlayBottom) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val bottomPx = if (systemBars.bottom > 0) systemBars.bottom
            else (56 * resources.displayMetrics.density).toInt()
            (v.layoutParams as? ViewGroup.MarginLayoutParams)?.bottomMargin = bottomPx
            v.requestLayout()
            insets
        }
        binding.root.requestApplyInsets()

        loadClassLabels()
        ProductDictionary.load(this)
        loadSynonymFromRemote()
        initYOLOX()
        initMediaPipeHands()
        setupTargetSpinner()
        initGyroTrackingManager()
        initSttTts()

        binding.firstScreen.setOnClickListener { onFirstScreenClicked() }
        binding.resetBtn.setOnClickListener { gyroManager.resetToSearchingFromUI() }
        binding.btnFirstScreen.setOnClickListener { goToFirstScreen() }
        setupProductDrawer()
        binding.micButton.setOnClickListener { onMicButtonClicked() }
        binding.confirmBtn.setOnClickListener { voiceFlowController?.onConfirmClicked() }
        binding.reinputBtn.setOnClickListener { voiceFlowController?.onReinputClicked() }
        binding.retryBtn.setOnClickListener { voiceFlowController?.onRetrySearch() }
        binding.adminTextInputBtn.setOnClickListener { showAdminTextInputDialog() }
        setupPinchZoom()

        if (allPermissionsGranted()) {
            showFirstScreen()
            // TTS는 initTTS 콜백에서 준비되면 playWelcomeTTS() 호출 (중복 호출 방지)
        } else {
            ActivityCompat.requestPermissions(
                this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    /** 앱 시작 시 MongoDB(백엔드)에서 대답/상품 근접단어 로드. API 키 설정 시에만 호출됨. */
    private fun loadSynonymFromRemote() {
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) {
                SynonymRepository.loadFromRemote()
            }
        }
    }

    private fun initTTS() {
        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = java.util.Locale.KOREAN
                runOnUiThread {
                    if (allPermissionsGranted()) playWelcomeTTS()
                }
            }
        }
    }

    private fun initSTT() {
        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        }
    }

    /** 햄버거 메뉴 - 찾을 수 있는 품목 리스트 Drawer (특정 상품만, 모든 상품 옵션 없음) */
    private fun setupProductDrawer() {
        val productList = classLabels
        binding.productListView.adapter = ArrayAdapter(this, R.layout.item_product, R.id.itemProductName, productList)
        binding.productListView.setOnItemClickListener { _, _, position, _ ->
            val label = productList.getOrNull(position) ?: return@setOnItemClickListener
            currentTargetLabel.set(label)
            binding.drawerLayout.closeDrawers()
            if (screenState == ScreenState.FIRST_SCREEN) {
                val displayName = if (ProductDictionary.isLoaded()) ProductDictionary.getDisplayNameKo(label) else label
                speak("$displayName 찾겠습니다.") {
                    runOnUiThread { showCameraScreen() }
                }
            } else {
                binding.systemMessageText.text = "찾는 중: $label"
                binding.statusText.text = "찾는 중: $label"
                transitionToSearching()
            }
        }
        binding.menuButton.setOnClickListener {
            binding.drawerLayout.openDrawer(binding.drawerContent)
        }
    }

    private fun speak(text: String, onDone: (() -> Unit)? = null) {
        ttsManager?.speak(text, TextToSpeech.QUEUE_FLUSH, onDone)
    }

    private fun playWelcomeTTS() {
        speak(VoicePrompts.PROMPT_IDLE_TOUCH)
    }

    private fun showFirstScreen() {
        screenState = ScreenState.FIRST_SCREEN
        binding.firstScreen.visibility = View.VISIBLE
        binding.cameraContainer.visibility = View.GONE
        binding.btnFirstScreen.visibility = View.GONE
        binding.targetRow.visibility = View.GONE
        binding.systemMessageText.text = ""
        binding.statusText.text = ""
        updateSearchTargetLabel()
        stopHandGuidanceTTS()
    }

    /** 우측 하단 '첫 화면' 버튼: 카메라 끄고 첫 화면으로 */
    private fun goToFirstScreen() {
        transitionToSearching()
        stopArSession()
        speak("첫 화면으로 돌아갑니다.") {
            runOnUiThread {
                showFirstScreen()
                playWelcomeTTS()
            }
        }
    }

    private fun showCameraScreen() {
        screenState = ScreenState.CAMERA_SCREEN
        binding.firstScreen.visibility = View.GONE
        binding.cameraContainer.visibility = View.VISIBLE
        binding.btnFirstScreen.visibility = View.VISIBLE
        binding.overlayView.visibility = View.VISIBLE
        updateSearchTargetLabel()
        binding.systemMessageText.text = "찾는 중: ${getTargetLabel()}"
        binding.statusText.text = "찾는 중: ${getTargetLabel()}"
        startArSession()
    }

    private fun onFirstScreenClicked() {
        if (screenState != ScreenState.FIRST_SCREEN) return
        if (!allPermissionsGranted()) {
            Toast.makeText(this, "마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            return
        }
        if (voiceFlowController == null) {
            Toast.makeText(this, "준비 중입니다. 잠시 후 다시 터치해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        // 볼륨 업 길게 누르기 시 마이크 켜고 상품명 입력 받기
        voiceFlowController?.startProductNameInput()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        when (event.keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (volumeUpKeyDownScheduled) return true
                        volumeUpKeyDownScheduled = true
                        volumeUpLongPressFired = false
                        volumeUpLongPressRunnable = Runnable {
                            volumeUpLongPressFired = true
                            runOnUiThread { onFirstScreenClicked() }
                        }
                        volumeLongPressHandler.postDelayed(volumeUpLongPressRunnable!!, VOLUME_LONG_PRESS_MS)
                        return true
                    }
                    KeyEvent.ACTION_UP -> {
                        volumeUpKeyDownScheduled = false
                        volumeLongPressHandler.removeCallbacks(volumeUpLongPressRunnable ?: return true)
                        if (!volumeUpLongPressFired) {
                            (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                                ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                        }
                        return true
                    }
                }
            }
            KeyEvent.KEYCODE_VOLUME_DOWN -> {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (volumeDownKeyDownScheduled) return true
                        volumeDownKeyDownScheduled = true
                        volumeDownLongPressFired = false
                        volumeDownLongPressRunnable = Runnable {
                            volumeDownLongPressFired = true
                            runOnUiThread {
                                ttsManager?.stop()
                                sttManager?.startListening()
                            }
                        }
                        volumeLongPressHandler.postDelayed(volumeDownLongPressRunnable!!, VOLUME_LONG_PRESS_MS)
                        return true
                    }
                    KeyEvent.ACTION_UP -> {
                        volumeDownKeyDownScheduled = false
                        volumeLongPressHandler.removeCallbacks(volumeDownLongPressRunnable ?: return true)
                        if (!volumeDownLongPressFired) {
                            (getSystemService(Context.AUDIO_SERVICE) as? AudioManager)
                                ?.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                        }
                        return true
                    }
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun initSttTts() {
        ttsManager = TTSManager(
            context = this,
            onReady = { },
            onSpeakDone = { },
            onError = { msg ->
                runOnUiThread {
                    Log.e(TAG, "[TTS 에러] $msg")
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
        beepPlayer = BeepPlayer().also { it.init() }

        ttsManager?.init { success ->
            runOnUiThread {
                if (success && beepPlayer != null) {
                    voiceFlowController = VoiceFlowController(
                        ttsManager = ttsManager!!,
                        onStateChanged = { _, _ -> runOnUiThread { updateVoiceFlowButtonVisibility() } },
                        onSystemAnnounce = { msg -> runOnUiThread { binding.systemMessageText.text = msg } },
                        onRequestStartStt = {
                    runOnUiThread { sttManager?.startListening() }
                },
                        onStartSearch = { productName -> runOnUiThread { onStartSearchFromVoiceFlow(productName) } },
                        onProductNameEntered = { productName -> runOnUiThread { setTargetFromSpokenProductName(productName) } }
                    )
                    voiceFlowController?.start()
                    // 첫 화면일 때만 시작 안내 TTS (화면 터치해서 상품찾기 시작해주세요)
                    if (screenState == ScreenState.FIRST_SCREEN) playWelcomeTTS()
                } else {
                    Log.e(TAG, "TTS 초기화 실패")
                }
            }
        }

        sttManager = STTManager(
            context = this,
            onResult = { text ->
                runOnUiThread {
                    binding.userSpeechText.text = text
                    if (waitingForTouchConfirm) {
                        waitingForTouchConfirm = false
                        handleTouchConfirmYesNo(text)
                        return@runOnUiThread
                    }
                    voiceFlowSttRetryCount = 0
                    if (voiceFlowController?.currentState == VoiceFlowController.VoiceFlowState.WAITING_PRODUCT_NAME) {
                        voiceConfirmSilentRetryCount = 0  // 상품명 인식 후 확인 대기 단계 진입 시 리셋
                    }
                    voiceFlowController?.onSttResult(text)
                }
            },
            onError = { msg ->
                runOnUiThread {
                    binding.systemMessageText.text = msg
                    if (waitingForTouchConfirm) {
                        waitingForTouchConfirm = false
                        binding.systemMessageText.text = "손을 뻗어 잡아주세요"
                        binding.statusText.text = "손을 뻗어 잡아주세요"
                        touchActive = false
                        startPositionAnnounce()
                        Toast.makeText(this, "음성 인식 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onErrorWithCode = { msg, errorCode ->
                runOnUiThread {
                    val state = voiceFlowController?.currentState
                    val isNoMatchOrTimeout =
                        errorCode == android.speech.SpeechRecognizer.ERROR_NO_MATCH ||
                            errorCode == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    // 예/아니오 대기 = 터치 확인(상품에 닿았나요?) 또는 상품명/맞습니까 — 공통 처리
                    val isSttWaiting = waitingForTouchConfirm ||
                        state == VoiceFlowController.VoiceFlowState.WAITING_PRODUCT_NAME ||
                        state == VoiceFlowController.VoiceFlowState.WAITING_CONFIRMATION

                    // NO_MATCH / SPEECH_TIMEOUT → 공통 재시도 또는 포기
                    if (isNoMatchOrTimeout && isSttWaiting) {
                        val isTouchConfirm = waitingForTouchConfirm
                        val isVoiceConfirm = state == VoiceFlowController.VoiceFlowState.WAITING_CONFIRMATION
                        val retryCount = when {
                            isTouchConfirm -> touchConfirmSttRetryCount
                            else -> voiceFlowSttRetryCount
                        }
                        // 터치 확인: 처음 두 번 NO_MATCH는 TTS 없이 재시작 (듣는 중이 빨리 꺼지는 기기/엔진 완화)
                        if (isTouchConfirm && retryCount < 2) {
                            touchConfirmSttRetryCount++
                            binding.root.postDelayed({
                                if (!waitingForTouchConfirm) return@postDelayed
                                sttManager?.startListening()
                            }, 400L)
                            return@runOnUiThread
                        }
                        // 확인 대기("맞습니까? 예라고 말해주세요"): 처음 두 번 NO_MATCH는 TTS 없이 재시작
                        if (isVoiceConfirm && voiceConfirmSilentRetryCount < 2) {
                            voiceConfirmSilentRetryCount++
                            binding.root.postDelayed({
                                if (voiceFlowController?.currentState != VoiceFlowController.VoiceFlowState.WAITING_CONFIRMATION) return@postDelayed
                                sttManager?.startListening()
                            }, 400L)
                            return@runOnUiThread
                        }
                        binding.systemMessageText.text = "음성 인식 실패: $msg (코드 $errorCode)"
                        if (!isTouchConfirm) {
                            val lastPartial = sttManager?.getLastPartialText()?.takeIf { it.isNotBlank() }
                            if (!lastPartial.isNullOrBlank()) binding.userSpeechText.text = lastPartial
                        }
                        if (retryCount < STT_MAX_RETRIES) {
                            if (isTouchConfirm) touchConfirmSttRetryCount++ else voiceFlowSttRetryCount++
                            speak("$msg") {
                                runOnUiThread {
                                    speak("다시 말해주세요.") {
                                        runOnUiThread {
                                            binding.root.postDelayed({
                                                if (isTouchConfirm && !waitingForTouchConfirm) return@postDelayed
                                                sttManager?.startListening()
                                            }, 800L)
                                        }
                                    }
                                }
                            }
                        } else {
                            if (isTouchConfirm) {
                                touchConfirmSttRetryCount = 0
                                waitingForTouchConfirm = false
                                binding.statusText.text = "손을 뻗어 잡아주세요"
                                touchActive = false
                                startPositionAnnounce()
                            } else {
                                voiceFlowSttRetryCount = 0
                                voiceConfirmSilentRetryCount = 0
                            }
                            speak("$msg") {
                                if (isTouchConfirm) {
                                    speak("화면을 터치해서 다시 시작해주세요.") {
                                        runOnUiThread {
                                            Toast.makeText(this, "음성 인식 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } else {
                                    speak(VoicePrompts.PROMPT_TOUCH_RESTART) {
                                        voiceFlowController?.start()
                                        showFirstScreen()
                                    }
                                }
                            }
                        }
                        return@runOnUiThread
                    }

                    // RECOGNIZER_BUSY → 상품명/맞습니까 대기 중일 때만 재시도 (터치 확인은 제외)
                    val isRetryable = errorCode == android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                    val isVoiceFlowWaiting = state == VoiceFlowController.VoiceFlowState.WAITING_PRODUCT_NAME ||
                        state == VoiceFlowController.VoiceFlowState.WAITING_CONFIRMATION
                    if (isRetryable && isVoiceFlowWaiting) {
                        binding.systemMessageText.text = "다시 듣는 중..."
                        binding.root.postDelayed({
                            sttManager?.startListening()
                        }, 800L)
                    }
                }
            },
            onListeningChanged = { listening ->
                runOnUiThread {
                    binding.micButton.isEnabled = !listening
                    if (listening) {
                        binding.systemMessageText.text = "듣는 중..."
                        binding.userSpeechText.text = ""
                    } else {
                        if (binding.systemMessageText.text == "듣는 중...") {
                            binding.systemMessageText.text = ""
                        }
                    }
                    updateVoiceFlowButtonVisibility()
                }
            },
            onPartialResult = { text ->
                runOnUiThread {
                    // 내가 말한 메시지: 사용자가 실제 말한 데이터만 실시간 표시 (빈 문자열이면 기존 값 유지)
                    if (text.isNotBlank()) binding.userSpeechText.text = text
                    // [예 말하기 공통] 1. 찾는 상품 확인, 2. 객체 잡았나요 — 둘 다 isShortYesLike 부분 인식 → 바로 통과. 한쪽만 수정 금지.
                    if (text.isNullOrBlank()) return@runOnUiThread
                    if (!isShortYesLike(text)) return@runOnUiThread
                    val state = voiceFlowController?.currentState
                    val isConfirmWaiting = state == VoiceFlowController.VoiceFlowState.WAITING_CONFIRMATION
                    if (isConfirmWaiting || waitingForTouchConfirm) {
                        sttManager?.stopListening()
                        if (waitingForTouchConfirm) {
                            waitingForTouchConfirm = false
                            handleTouchConfirmYesNo(text)
                        } else {
                            voiceFlowController?.onSttResult(text)
                        }
                    }
                }
            },
            onListeningEndedReason = { reason ->
                runOnUiThread { binding.systemMessageText.text = "음성인식 종료: $reason" }
            },
            beepPlayer = beepPlayer
        ).also { it.init() }
    }

    private fun updateVoiceFlowButtonVisibility() {
        val state = voiceFlowController?.currentState ?: return
        when (state) {
            VoiceFlowController.VoiceFlowState.CONFIRM_PRODUCT,
            VoiceFlowController.VoiceFlowState.WAITING_CONFIRMATION -> {
                binding.confirmBtn.visibility = View.VISIBLE
                binding.reinputBtn.visibility = View.VISIBLE
                binding.retryBtn.visibility = View.GONE
            }
            VoiceFlowController.VoiceFlowState.SEARCH_RESULT -> {
                binding.confirmBtn.visibility = View.GONE
                binding.reinputBtn.visibility = View.GONE
                binding.retryBtn.visibility = View.VISIBLE
            }
            VoiceFlowController.VoiceFlowState.SEARCH_FAILED -> {
                binding.confirmBtn.visibility = View.GONE
                binding.reinputBtn.visibility = View.GONE
                binding.retryBtn.visibility = View.VISIBLE
                binding.previewView.visibility = View.GONE
                binding.overlayView.visibility = View.GONE
            }
            VoiceFlowController.VoiceFlowState.WAITING_PRODUCT_NAME,
            VoiceFlowController.VoiceFlowState.APP_START -> {
                binding.confirmBtn.visibility = View.GONE
                binding.reinputBtn.visibility = View.GONE
                binding.retryBtn.visibility = View.GONE
                // preview/overlay는 건드리지 않음 (버튼으로 카메라 켠 뒤 STT 콜백 시 꺼지던 현상 방지)
            }
            else -> {
                binding.confirmBtn.visibility = View.GONE
                binding.reinputBtn.visibility = View.GONE
                binding.retryBtn.visibility = View.GONE
            }
        }
    }

    /** 음성 플로우에서 확인 클릭 시: 첫 화면 숨기고 카메라 켠 뒤 탐지 시작. 입력된 상품만 탐지하도록 타겟을 반드시 특정 클래스로 설정. */
    private fun onStartSearchFromVoiceFlow(productName: String) {
        val targetClass = mapSpokenToClass(productName)
        if (productName.isNotBlank() && targetClass.isBlank()) {
            voiceSearchTargetLabel = null
            val failReason = "인식된 말을 상품 목록에서 찾지 못했어요. '$productName'"
            binding.systemMessageText.text = "상품 매칭 실패: '$productName'(을)를 찾지 못했어요."
            speak(failReason) {
                speak(VoicePrompts.PROMPT_PRODUCT_RECOGNITION_FAILED) { }
            }
            return
        }
        cancelSearchTimeout()
        transitionToSearching()
        voiceSearchTargetLabel = targetClass
        currentTargetLabel.set(targetClass)
        val idx = classLabels.indexOf(targetClass)
        if (idx >= 0) binding.targetSpinner.setSelection(idx)
        binding.startSearchBtn.visibility = View.GONE
        screenState = ScreenState.CAMERA_SCREEN
        binding.firstScreen.visibility = View.GONE
        binding.cameraContainer.visibility = View.VISIBLE
        binding.btnFirstScreen.visibility = View.VISIBLE
        binding.overlayView.visibility = View.VISIBLE
        binding.systemMessageText.text = "찾는 중: ${getTargetLabel()}"
        binding.statusText.text = "찾는 중: ${getTargetLabel()}"
        startArSession()
        startSearchTimeout()
    }

    private fun mapSpokenToClass(spoken: String): String {
        if (spoken.isBlank()) return ""
        SynonymRepository.findClassByProximity(spoken)?.let { return it }
        ProductDictionary.findClassByStt(spoken)?.let { return it }
        val s = spoken.trim().lowercase().replace(" ", "")
        for (label in classLabels) {
            val labelNorm = label.lowercase().replace("_", "")
            if (labelNorm.contains(s) || s.contains(labelNorm.take(3))) return label
        }
        return ""
    }

    /** 상품명 말한 직후 타겟을 해당 상품으로 설정. 목록에 없는 상품이면 타겟을 바꾸지 않음. */
    private fun setTargetFromSpokenProductName(productName: String) {
        val targetClass = mapSpokenToClass(productName)
        if (productName.isNotBlank() && targetClass.isBlank()) return

        currentTargetLabel.set(targetClass)
        val idx = classLabels.indexOf(targetClass)
        if (idx >= 0) binding.targetSpinner.setSelection(idx)
    }

    private fun stopArSession() {
        stopScanning()
        arRunning = false
        arExecutor.execute {} // drain
        try {
            arSession?.close()
            arSession = null
        } catch (_: Exception) {}
    }

    private fun setupPinchZoom() {
        // ARCore 세션에서는 카메라 줌 제어 없음 (무시)
    }

    private var scanRunnable: Runnable? = null

    /** 자동 탐색 메시지: 거리 > 2000mm 시 "너무 멂" 표시 (ARCore에는 줌 없음) */
    private fun startScanning() {
        if (isScanning) return
        if (screenState != ScreenState.CAMERA_SCREEN) return
        if (searchState != SearchState.SEARCHING) return
        if (!hasSpecificTarget()) return
        isScanning = true
    }

    /** 자동 탐색 중단 (물체 발견 시 또는 LOCKED 진입 시) */
    private fun stopScanning() {
        isScanning = false
        scanHandler.removeCallbacksAndMessages(null)
        scanRunnable = null
    }

    /** 관리자: 클래스 드롭다운으로 선택 후 바로 찾기 과정 진행 */
    private fun showAdminTextInputDialog() {
        if (classLabels.isEmpty()) {
            Toast.makeText(this, "클래스 목록을 불러오는 중입니다.", Toast.LENGTH_SHORT).show()
            return
        }
        val spinner = android.widget.Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, classLabels)
            setPadding(32, 24, 32, 24)
        }
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(48, 32, 48, 24)
            addView(spinner)
        }
        AlertDialog.Builder(this)
            .setTitle("클래스 선택")
            .setView(container)
            .setPositiveButton("확인") { _, _ ->
                val selected = spinner.selectedItem?.toString()?.trim() ?: ""
                if (selected.isNotBlank()) onStartSearchFromVoiceFlow(selected)
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /** [예 말하기 공통] 부분 인식에서 "예" 등 짧은 긍정 판별. 1. 찾는 상품 확인, 2. 객체 잡았나요 — 둘 다 이 함수 사용. 항상 동시 적용. */
    private fun isShortYesLike(text: String): Boolean {
        val t = text.trim().lowercase().replace(" ", "")
        if (t.length > 6) return false
        return t.contains("예") || t.contains("네") || t.contains("내") || t.contains("응") ||
            t.contains("맞") || t.contains("그래") || t.contains("좋아") || t == "yes" || t == "y"
    }

    /** TOUCH_CONFIRM: near-contact 후 "상품에 닿았나요?" 질문에 대한 응답 처리 (STT가 "네"→"내"로 인식하는 경우 포함) */
    private fun handleTouchConfirmYesNo(text: String) {
        val t = text.trim().lowercase().replace(" ", "")
        val isYes = t.contains("예") || t.contains("네") || t.contains("내") || t.contains("응") ||
            t.contains("맞") || t.contains("그래") || t.contains("좋아") ||
            t == "yes" || t == "y"
        if (isYes) {
            speak(VoicePrompts.PROMPT_DONE) {
                resetToIdleFromTouch()
            }
        } else {
            speak(VoicePrompts.PROMPT_TOUCH_RESTART) {
                resetToIdleFromTouch()
            }
        }
    }

    /** near-contact 확정 시 TOUCH_CONFIRM 상태로 진입 (중복 트리거 방지) */
    private fun enterTouchConfirm() {
        if (touchConfirmInProgress) return
        touchConfirmInProgress = true
        touchConfirmScheduled = false
        waitingForTouchConfirm = true
        touchConfirmSttRetryCount = 0
        val question = "상품에 닿았나요? 닿았으면 예라고 말해주세요."
        speak(question) {
            runOnUiThread { sttManager?.startListening() }
        }
    }

    /** TOUCH_CONFIRM 종료 후 완전 초기화(S0 IDLE) */
    private fun resetToIdleFromTouch() {
        runOnUiThread {
            touchConfirmInProgress = false
            touchConfirmScheduled = false
            waitingForTouchConfirm = false
            touchActive = false
            touchFrameCount = 0
            releaseFrameCount = 0
            transitionToSearching()
            stopArSession()
            showFirstScreen()
            // 선택: IDLE 안내 TTS
            playWelcomeTTS()
        }
    }

    private fun startSearchTimeout() {
        cancelSearchTimeout()
        searchTimeoutAborted = false
        searchTimeoutRunnable = Runnable {
            if (searchTimeoutAborted) return@Runnable
            if (searchState == SearchState.LOCKED) return@Runnable
            if (voiceFlowController?.currentState == VoiceFlowController.VoiceFlowState.SEARCHING_PRODUCT) {
                runOnUiThread {
                    if (searchTimeoutAborted || searchState == SearchState.LOCKED) return@runOnUiThread
                    voiceSearchTargetLabel = null
                    voiceFlowController?.onSearchComplete(false)
                    binding.previewView.visibility = View.GONE
                    binding.overlayView.visibility = View.GONE
                }
            }
        }
        searchTimeoutHandler.postDelayed(searchTimeoutRunnable!!, SEARCH_TIMEOUT_MS)
    }

    private fun cancelSearchTimeout() {
        searchTimeoutAborted = true
        searchTimeoutRunnable?.let { searchTimeoutHandler.removeCallbacks(it) }
        searchTimeoutRunnable = null
    }

    private fun startPositionAnnounce() {
        stopPositionAnnounce()
        positionAnnounceRunnable = object : Runnable {
            override fun run() {
                if (waitingForTouchConfirm) {
                    searchTimeoutHandler.postDelayed(this, POSITION_ANNOUNCE_INTERVAL_MS)
                    return
                }
                val box = frozenBox ?: return
                val w = frozenImageWidth
                val h = frozenImageHeight
                if (w <= 0 || h <= 0) return
                val displayName = if (ProductDictionary.isLoaded())
                    ProductDictionary.getDisplayNameKo(lockedTargetLabel) else lockedTargetLabel
                voiceFlowController?.announcePosition(displayName, box.rect, w, h)
                searchTimeoutHandler.postDelayed(this, POSITION_ANNOUNCE_INTERVAL_MS)
            }
        }
        searchTimeoutHandler.postDelayed(positionAnnounceRunnable!!, POSITION_ANNOUNCE_INTERVAL_MS)
    }

    private fun stopPositionAnnounce() {
        positionAnnounceRunnable?.let { searchTimeoutHandler.removeCallbacks(it) }
        positionAnnounceRunnable = null
    }

    private fun onMicButtonClicked() {
        if (!allPermissionsGranted()) {
            Toast.makeText(this, "마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            return
        }
        if (sttManager?.isListening() == true) {
            sttManager?.stopListening()
        } else {
            binding.systemMessageText.text = "듣는 중..."
            sttManager?.startListening()
        }
    }

    private fun initGyroTrackingManager() {
        gyroManager = GyroTrackingManager(
            context = this,
            onBoxUpdate = { update: BoxUpdate ->
                runOnUiThread {
                    val box = OverlayView.DetectionBox(
                        label = lockedTargetLabel,
                        confidence = 0.9f,
                        rect = update.rect,
                        topLabels = listOf(lockedTargetLabel to 90),
                        rotationDegrees = update.rotationDegrees
                    )
                    frozenBox = box
                    binding.overlayView.setDetections(listOf(box), frozenImageWidth, frozenImageHeight)
                }
            },
            onTrackingLost = { transitionToSearching(skipTtsDetectedReset = true) }
        )
    }

    private fun initMediaPipeHands() {
        try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("hand_landmarker.task")
                .build()

            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, image ->
                    latestHandsResult.set(result)
                }
                .setErrorListener { e ->
                    Log.e(TAG, "Hands LIVE_STREAM error", e)
                }
                .build()

            handLandmarker = HandLandmarker.createFromOptions(this, options)
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe Hands 초기화 실패", e)
        }
    }

    private fun setupTargetSpinner() {
        val spinnerItems = classLabels
        binding.targetSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, spinnerItems)
        currentTargetLabel.set("")
        binding.targetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, p1: View?, pos: Int, p3: Long) {
                currentTargetLabel.set(spinnerItems.getOrNull(pos) ?: "")
            }
            override fun onNothingSelected(p0: AdapterView<*>?) {}
        }
    }

    private fun getTargetLabel(): String = currentTargetLabel.get().trim()

    /** 화면 상단 "찾는 품목" 라벨 갱신 (실제 앱처럼 찾는 품목 표시) */
    private fun updateSearchTargetLabel() {
        val label = getTargetLabel()
        if (label.isEmpty()) {
            binding.searchTargetLabel.visibility = View.GONE
        } else {
            val displayName = if (ProductDictionary.isLoaded()) ProductDictionary.getDisplayNameKo(label) else label
            binding.searchTargetLabel.text = "찾는 품목: $displayName"
            binding.searchTargetLabel.visibility = View.VISIBLE
        }
    }

    /** 특정 상품이 선택된 경우만 true. 비어 있으면 탐지 시작 안 함. */
    private fun hasSpecificTarget(): Boolean = getTargetLabel().isNotEmpty()

    /** 물체 발견 시 자동 탐색 메시지 중단 */
    private fun processAutoZoom(detections: List<OverlayView.DetectionBox>, imageWidth: Int, imageHeight: Int) {
        val target = getTargetLabel().trim()
        if (target.isBlank()) return
        val confident = detections.any { d ->
            (d.label.equals(target, true) || d.topLabels.any { it.first.equals(target, true) }) &&
                d.confidence >= TARGET_CONFIDENCE_THRESHOLD
        }
        if (confident) stopScanning()
    }

    /**
     * LOCKED: ARCore Depth API로 측정한 실제 거리(mm) 기반 유도.
     * - &lt; 600mm: "손을 뻗으세요" (적정 거리)
     * - 600~2000mm: "더 가까이 오세요"
     * - &gt; 2000mm: "너무 멂 (자동 줌 탐색)" 메시지
     */
    private fun processDistanceGuidanceByDepth(distanceMm: Int?) {
        if (distanceMm == null) return
        val now = System.currentTimeMillis()
        if (now - lastGuidanceTime < GUIDANCE_COOLDOWN_MS) return
        runOnUiThread {
            when {
                distanceMm < DISTANCE_NEAR_MM -> {
                    lastGuidanceTime = now
                    speak("손을 뻗으세요")
                }
                distanceMm in DISTANCE_NEAR_MM..DISTANCE_FAR_MM -> {
                    lastGuidanceTime = now
                    speak("더 가까이 오세요")
                }
                distanceMm > DISTANCE_FAR_MM -> {
                    lastGuidanceTime = now
                    speak("너무 멂. 더 가까이 오세요.")
                }
            }
        }
    }

    /** LOCKED 시 시각 보정: 자이로 예측 위치 또는 이전 박스와 가장 가까운 타겟 반환 (Gyro-Guided Matching)
     * @param minConfidence occlusion 시 더 낮은 기준 사용 (예: 0.15f) */
    private fun findTrackedTarget(
        detections: List<OverlayView.DetectionBox>,
        targetLabel: String,
        prevBox: OverlayView.DetectionBox?,
        minConfidence: Float = TARGET_CONFIDENCE_THRESHOLD * 0.5f
    ): OverlayView.DetectionBox? {
        val target = targetLabel.trim()
        if (target.isBlank()) return null

        val candidates = detections.filter { d ->
            (d.label.trim().equals(target, ignoreCase = true) ||
                d.topLabels.any { it.first.trim().equals(target, ignoreCase = true) }) &&
            d.confidence >= minConfidence
        }.map { d ->
            if (d.label.trim().equals(target, ignoreCase = true)) d
            else {
                val conf = d.topLabels.find { it.first.trim().equals(target, ignoreCase = true) }?.second?.div(100f) ?: d.confidence
                d.copy(label = target, confidence = conf, topLabels = listOf(target to (conf * 100).toInt()))
            }
        }

        if (candidates.isEmpty()) return null
        val predicted = gyroManager.getPredictedRect()
        return when {
            predicted != null -> candidates.minByOrNull { box ->
                val cx = (box.rect.left + box.rect.right) / 2f
                val cy = (box.rect.top + box.rect.bottom) / 2f
                val px = (predicted.left + predicted.right) / 2f
                val py = (predicted.top + predicted.bottom) / 2f
                (cx - px) * (cx - px) + (cy - py) * (cy - py)
            }
            prevBox != null -> candidates.maxByOrNull { iou(it.rect, prevBox.rect) }
            else -> candidates.maxByOrNull { it.confidence }
        }
    }

    /** 타겟에 맞는 detection만 반환. 타겟이 없으면 빈 리스트(탐지 안 함). */
    private fun filterDetectionsByTarget(detections: List<OverlayView.DetectionBox>, targetLabel: String): List<OverlayView.DetectionBox> {
        val target = targetLabel.trim()
        if (target.isBlank()) return emptyList()

        return detections
            .mapNotNull { box ->
                val targetConf = when {
                    box.label.trim().equals(target, ignoreCase = true) -> box.confidence
                    else -> box.topLabels.find { it.first.trim().equals(target, ignoreCase = true) }?.second?.div(100f)
                }
                if (targetConf != null && targetConf >= TARGET_CONFIDENCE_THRESHOLD) {
                    if (box.label.trim().equals(target, ignoreCase = true)) box
                    else box.copy(
                        label = target,
                        confidence = targetConf,
                        topLabels = listOf(target to (targetConf * 100).toInt())
                    )
                } else null
            }
    }

    /** 타겟에 맞는 detection만 반환. 타겟이 없으면 null(탐지/락 안 함). */
    private fun findTargetMatch(detections: List<OverlayView.DetectionBox>, targetLabel: String): Pair<OverlayView.DetectionBox, String>? {
        val target = targetLabel.trim()
        if (target.isBlank()) return null

        return detections
            .mapNotNull { box ->
                val targetConf = when {
                    box.label.trim().equals(target, ignoreCase = true) -> box.confidence
                    else -> box.topLabels.find { it.first.trim().equals(target, ignoreCase = true) }?.second?.div(100f)
                }
                if (targetConf != null && targetConf >= TARGET_CONFIDENCE_THRESHOLD) {
                    val displayBox = if (box.label.trim().equals(target, ignoreCase = true)) box
                    else box.copy(
                        label = target,
                        confidence = targetConf,
                        topLabels = listOf(target to (targetConf * 100).toInt())
                    )
                    Pair(displayBox, box.label)
                } else null
            }
            .maxByOrNull { it.first.confidence }
    }

    /** @param actualPrimaryLabel 탐지된 1순위 라벨(음성 플로우에서 요청 상품과 비교용). null이면 box.label 사용 */
    private fun transitionToLocked(box: OverlayView.DetectionBox, imageWidth: Int, imageHeight: Int, actualPrimaryLabel: String? = null) {
        cancelSearchTimeout()
        runOnUiThread {
            if (searchState == SearchState.LOCKED) return@runOnUiThread
            stopScanning()
            searchState = SearchState.LOCKED
            lockedTargetLabel = box.label
            validationFailCount = 0
            frozenImageWidth = imageWidth
            frozenImageHeight = imageHeight
            ttsGrabPlayed = false
            ttsGrabbedPlayed = false
            ttsAskAnotherPlayed = false
            touchConfirmInProgress = false
            touchConfirmScheduled = false
            waitingForTouchConfirm = false
            handsOverlapFrameCount = 0
            pinchGrabFrameCount = 0
            touchFrameCount = 0
            releaseFrameCount = 0
            touchActive = false
            lastTouchMidpointPx = null
            lastTouchTtsTimeMs = 0L
            latestHandsResult.set(null)  // 재획득 시 이전 손 데이터로 터치 판정하지 않도록 초기화
            binding.resetBtn.visibility = View.GONE
            binding.overlayView.setDetections(listOf(box), imageWidth, imageHeight)
            binding.overlayView.setFrozen(true)
            binding.systemMessageText.text = "객체 탐지됨. 손을 뻗어 잡아주세요."
            binding.statusText.text = "객체 탐지됨. 손을 뻗어 잡아주세요."
            if (!ttsDetectedPlayed) {
                ttsDetectedPlayed = true
                speak("객체를 탐지했습니다. 손을 뻗어 잡아주세요.")
                Toast.makeText(this, "타겟 고정 → 자이로 추적 모드", Toast.LENGTH_SHORT).show()
            }
            binding.yoloxStatus.text = "🔒 고정: ${box.label} (자이로)"
            binding.handsStatus.text = "📐 자이로: ON"
            if (voiceFlowController?.currentState == VoiceFlowController.VoiceFlowState.SEARCHING_PRODUCT) {
                cancelSearchTimeout()
                val requested = voiceSearchTargetLabel
                val actualLabel = actualPrimaryLabel ?: box.label
                val isRequestedProduct = requested.isNullOrBlank() || requested == actualLabel
                if (isRequestedProduct) {
                    voiceSearchTargetLabel = null
                    voiceFlowController?.onSearchComplete(true, actualLabel, box.rect, imageWidth, imageHeight)
                }
            }
            startPositionAnnounce()
        }
    }

    private fun transitionToSearching(skipTtsDetectedReset: Boolean = false) {
        runOnUiThread {
            stopPositionAnnounce()
            searchState = SearchState.SEARCHING
            lockedTargetLabel = ""
            pendingLockBox = null
            pendingLockCount = 0
            validationFailCount = 0
            wasOccluded = false
            if (!skipTtsDetectedReset) ttsDetectedPlayed = false
            ttsGrabPlayed = false
            ttsGrabbedPlayed = false
            ttsAskAnotherPlayed = false
            touchConfirmInProgress = false
            touchConfirmScheduled = false
            waitingForTouchConfirm = false
            handsOverlapFrameCount = 0
            pinchGrabFrameCount = 0
            touchFrameCount = 0
            releaseFrameCount = 0
            touchActive = false
            lastTouchMidpointPx = null
            lastTouchTtsTimeMs = 0L
            lastGuidanceTime = 0L
            latestHandsResult.set(null)  // SEARCHING 진입 시 손 데이터 초기화 (재락 시 새 데이터만 사용)
            opticalFlowTracker.reset()
            gyroManager.stopTracking()
            binding.resetBtn.visibility = View.GONE
            binding.overlayView.setDetections(emptyList(), 0, 0)
            binding.overlayView.setFrozen(false)
            updateSearchTargetLabel()
            binding.systemMessageText.text = "찾는 중: ${getTargetLabel()}"
            binding.statusText.text = "찾는 중: ${getTargetLabel()}"
            stopHandGuidanceTTS()
            if (screenState == ScreenState.CAMERA_SCREEN) startScanning()
        }
    }

    /** assets/classes.txt 로드 (한 줄 = 한 클래스, 0번째 줄 = class 0). # 시작 줄 무시. */
    private fun loadClassLabels() {
        try {
            assets.open("classes.txt").use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    classLabels = reader.readLines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "classes.txt 로드 실패", e)
            classLabels = emptyList()
        }
    }

    private fun getClassLabel(classId: Int): String {
        return classLabels.getOrNull(classId)?.takeIf { it.isNotBlank() } ?: "Object_$classId"
    }

    /** YOLOX 입력: BitmapUtils Letterboxing 사용(비율 유지, 찌그러짐 없음) */
    private fun letterboxBitmap(bitmap: Bitmap, inputSize: Int): Pair<Bitmap, Float> =
        BitmapUtils.createLetterboxBitmap(bitmap, inputSize)

    /** YOLOX preproc: BGR, 0~255 float. NCHW [1,3,H,W] 시 채널 선 출력 */
    private fun bitmapToFloatBuffer(bitmap: Bitmap, size: Int, isNchw: Boolean = false): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * size * size * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        if (isNchw) {
            for (c in 0 until 3) {  // B, G, R
                val shift = when (c) { 0 -> 0; 1 -> 8; else -> 16 }
                for (i in pixels.indices) {
                    buffer.putFloat(((pixels[i] shr shift) and 0xFF).toFloat())
                }
            }
        } else {
            for (p in pixels) {
                buffer.putFloat((p and 0xFF).toFloat())
                buffer.putFloat(((p shr 8) and 0xFF).toFloat())
                buffer.putFloat(((p shr 16) and 0xFF).toFloat())
            }
        }
        buffer.rewind()
        return buffer
    }

    private fun initYOLOX() {
        try {
            val modelFilename = "yolox_nano_49cls_float16.tflite"
            val modelFile = loadModelFile(modelFilename)
            val options = Interpreter.Options()
            try {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
                options.setAllowFp16PrecisionForFp32(true)
                Log.d(TAG, "🚀 GPU 가속 켜짐 (FP16)")
                runOnUiThread { binding.systemMessageText.text = "YOLOX GPU 준비"; binding.statusText.text = "YOLOX GPU 준비" }
            } catch (e: Exception) {
                Log.e(TAG, "❌ GPU 실패 -> CPU 전환", e)
                options.setNumThreads(4)
                gpuDelegate = null
                runOnUiThread { binding.systemMessageText.text = "YOLOX CPU 준비"; binding.statusText.text = "YOLOX CPU 준비" }
            }
            yoloxInterpreter = Interpreter(modelFile, options)
            val inputShape = yoloxInterpreter!!.getInputTensor(0).shape()
            val inputSize = if (inputShape.size >= 3 && inputShape[1] == 3) inputShape[2] else inputShape[1]
        } catch (e: Exception) {
            Log.e(TAG, "YOLOX 초기화 실패", e)
            runOnUiThread { binding.systemMessageText.text = "YOLOX 초기화 실패"; binding.statusText.text = "YOLOX 초기화 실패" }
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        val buffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
        return buffer
    }

    /** ARCore 세션 시작: Session 생성, Config(Depth AUTOMATIC), 프레임 루프 시작 */
    private fun startArSession() {
        if (arSession != null) return
        try {
            val session = Session(this)
            val config = session.config
            config.depthMode = Config.DepthMode.AUTOMATIC
            config.focusMode = Config.FocusMode.AUTO
            if (!session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                Log.w(TAG, "Depth API 미지원 기기")
            }
            session.configure(config)
            arSession = session
            arRunning = true
            scanHandler.postDelayed({ startScanning() }, SCAN_INITIAL_DELAY_MS)
            binding.previewView.post {
                updateDisplayGeometry()
                arExecutor.execute(arFrameLoopRunnable)
            }
        } catch (e: Exception) {
            Log.e(TAG, "ARCore 세션 시작 실패", e)
            runOnUiThread {
                binding.systemMessageText.text = "ARCore 초기화 실패"
                binding.statusText.text = "ARCore 초기화 실패"
            }
        }
    }

    private fun updateDisplayGeometry() {
        val session = arSession ?: return
        val view = binding.previewView
        if (view.width == 0 || view.height == 0) return
        val rotation = windowManager.defaultDisplay?.rotation ?: Surface.ROTATION_0
        displayRotationDegrees = when (rotation) {
            Surface.ROTATION_0 -> 0
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        session.setDisplayGeometry(rotation, view.width, view.height)
    }

    private val arFrameLoopRunnable = Runnable {
        while (arRunning) {
            val session = arSession ?: break
            try {
                val frame = session.update() ?: continue
                if (binding.previewView.width == 0 || binding.previewView.height == 0) {
                    runOnUiThread { updateDisplayGeometry() }
                    continue
                }
                var cameraImage: Image? = null
                try {
                    cameraImage = frame.acquireCameraImage()
                } catch (_: NotYetAvailableException) {
                    continue
                } catch (e: Exception) {
                    Log.e(TAG, "acquireCameraImage 실패", e)
                    continue
                }
                val bitmap = BitmapUtils.yuv420ToBitmap(cameraImage!!)
                cameraImage.close()
                cameraImage = null
                if (bitmap == null) continue
                val w = bitmap.width
                val h = bitmap.height
                val rotatedBitmap = BitmapUtils.rotateBitmap(bitmap, displayRotationDegrees) ?: bitmap
                if (rotatedBitmap != bitmap) bitmap.recycle()
                val imageWidth = rotatedBitmap.width
                val imageHeight = rotatedBitmap.height
                val startTime = System.currentTimeMillis()

                if (searchState == SearchState.LOCKED) {
                    sendFrameToHands(rotatedBitmap, frame.timestamp)
                }

                when (searchState) {
                    SearchState.SEARCHING -> {
                        if (!hasSpecificTarget()) {
                            runOnUiThread { displayResults(emptyList(), 0, imageWidth, imageHeight) }
                        } else {
                            val detections = runYOLOX(rotatedBitmap)
                            processAutoZoom(detections, imageWidth, imageHeight)
                            val matchResult = findTargetMatch(detections, getTargetLabel())
                            if (matchResult != null) {
                                val (matched, actualPrimaryLabel) = matchResult
                                if (matched.confidence >= TARGET_CONFIDENCE_THRESHOLD) {
                                    val prev = pendingLockBox
                                    if (prev != null && prev.label == matched.label &&
                                        RectF.intersects(matched.rect, prev.rect)) {
                                        pendingLockCount++
                                        if (pendingLockCount >= LOCK_CONFIRM_FRAMES) {
                                            pendingLockBox = null
                                            pendingLockCount = 0
                                            runOnUiThread {
                                                transitionToLocked(matched, imageWidth, imageHeight, actualPrimaryLabel)
                                                frozenBox = matched
                                                frozenImageWidth = imageWidth
                                                frozenImageHeight = imageHeight
                                                wasOccluded = false
                                                opticalFlowTracker.reset()
                                                gyroManager.startTracking(matched.rect, imageWidth, imageHeight)
                                                lockedFrameCount = 0
                                                validationFailCount = 0
                                            }
                                            displayResults(filterDetectionsByTarget(detections, getTargetLabel()),
                                                System.currentTimeMillis() - startTime, imageWidth, imageHeight)
                                            drawPreviewAndUpdateFps(rotatedBitmap)
                                            continue
                                        }
                                    } else {
                                        pendingLockBox = matched
                                        pendingLockCount = 1
                                    }
                                }
                            } else {
                                pendingLockBox = null
                                pendingLockCount = 0
                            }
                            runOnUiThread {
                                displayResults(filterDetectionsByTarget(detections, getTargetLabel()),
                                    System.currentTimeMillis() - startTime, imageWidth, imageHeight)
                            }
                        }
                    }
                    SearchState.LOCKED -> {
                        lockedFrameCount++
                        val nowMs = System.currentTimeMillis()
                        val boxLostDurationMs = nowMs - lastSuccessfulValidationTimeMs
                        val shouldReacquire = boxLostDurationMs >= REACQUIRE_INFERENCE_AFTER_MS
                        val shouldValidate = shouldReacquire || (lockedFrameCount % VALIDATION_INTERVAL) == 0
                        var inferMs = System.currentTimeMillis() - startTime
                        val box = frozenBox
                        val handsOverlap = box?.let { isHandOverlappingBox(latestHandsResult.get(), it.rect, imageWidth, imageHeight) } ?: false
                        val handTouch = box?.let { isHandTouchingTargetBox(latestHandsResult.get(), it.rect, imageWidth, imageHeight) } ?: false

                        if (handTouch) {
                            touchFrameCount++
                            releaseFrameCount = 0
                            if (!touchActive && !touchConfirmInProgress && !touchConfirmScheduled && touchFrameCount >= TOUCH_CONFIRM_FRAMES) {
                                touchActive = true
                                val cooldownOk = nowMs - lastTouchTtsTimeMs >= TOUCH_TTS_COOLDOWN_MS
                                if (cooldownOk) {
                                    lastTouchTtsTimeMs = nowMs
                                    touchConfirmScheduled = true
                                    runOnUiThread {
                                        stopPositionAnnounce()
                                        stopHandGuidanceTTS()
                                        enterTouchConfirm()
                                    }
                                }
                            }
                        } else {
                            releaseFrameCount++
                            if (releaseFrameCount >= RELEASE_HOLD_FRAMES) {
                                touchActive = false
                                touchFrameCount = 0
                            } else touchFrameCount = 0
                        }
                        gyroManager.suspendUpdates = handsOverlap

                        if (handsOverlap) {
                            if (shouldReacquire) {
                                val detections = runYOLOX(rotatedBitmap)
                                inferMs = System.currentTimeMillis() - startTime
                                val tracked = findTrackedTarget(detections, lockedTargetLabel, frozenBox, 0.18f)
                                if (tracked != null) {
                                    lastSuccessfulValidationTimeMs = System.currentTimeMillis()
                                    gyroManager.correctPosition(tracked.rect)
                                    runOnUiThread {
                                        frozenBox = tracked.copy(rotationDegrees = 0f)
                                        frozenImageWidth = imageWidth
                                        frozenImageHeight = imageHeight
                                        if (!touchConfirmInProgress && !waitingForTouchConfirm) {
                                            touchActive = false
                                            touchFrameCount = 0
                                            releaseFrameCount = 0
                                            lastTouchTtsTimeMs = 0L
                                        }
                                    }
                                }
                            } else {
                                val handRect = mergedHandRect(latestHandsResult.get(), imageWidth, imageHeight)
                                val flow = opticalFlowTracker.update(rotatedBitmap, handRect, frozenBox?.rect)
                                flow?.let { (dx, dy) ->
                                    val b = frozenBox ?: return@let
                                    val r = b.rect
                                    val newRect = RectF(r.left + dx, r.top + dy, r.right + dx, r.bottom + dy)
                                    gyroManager.correctPosition(newRect)
                                    runOnUiThread { frozenBox = b.copy(rect = newRect) }
                                }
                            }
                        } else {
                            if (wasOccluded) frozenBox?.rect?.let { gyroManager.correctPosition(it) }
                            if (shouldValidate) {
                                val detections = runYOLOX(rotatedBitmap)
                                inferMs = System.currentTimeMillis() - startTime
                                val minConf = if (shouldReacquire) 0.18f else 0.22f
                                val tracked = findTrackedTarget(detections, lockedTargetLabel, frozenBox, minConf)
                                if (tracked != null) {
                                    validationFailCount = 0
                                    lastSuccessfulValidationTimeMs = System.currentTimeMillis()
                                    val cur = frozenBox?.rect ?: tracked.rect
                                    val blend = 0.7f
                                    val blendedRect = RectF(
                                        cur.left * blend + tracked.rect.left * (1f - blend),
                                        cur.top * blend + tracked.rect.top * (1f - blend),
                                        cur.right * blend + tracked.rect.right * (1f - blend),
                                        cur.bottom * blend + tracked.rect.bottom * (1f - blend)
                                    )
                                    gyroManager.correctPosition(blendedRect)
                                    runOnUiThread {
                                        frozenBox = tracked.copy(rect = blendedRect, rotationDegrees = 0f)
                                        frozenImageWidth = imageWidth
                                        frozenImageHeight = imageHeight
                                        if (!touchConfirmInProgress && !waitingForTouchConfirm) {
                                            touchActive = false
                                            touchFrameCount = 0
                                            releaseFrameCount = 0
                                            lastTouchTtsTimeMs = 0L
                                        }
                                    }
                                } else {
                                    validationFailCount++
                                    if (validationFailCount >= VALIDATION_FAIL_LIMIT) {
                                        validationFailCount = 0
                                        runOnUiThread { gyroManager.resetToSearchingFromUI() }
                                    }
                                }
                            }
                        }
                        wasOccluded = handsOverlap

                        var distanceMm: Int? = null
                        val frozen = frozenBox
                        if (frozen != null) {
                            val cx = (frozen.rect.left + frozen.rect.right) / 2f
                            val cy = (frozen.rect.top + frozen.rect.bottom) / 2f
                            distanceMm = getDepthAtCameraPixel(frame, cx.toInt(), cy.toInt(), imageWidth, imageHeight)
                            processDistanceGuidanceByDepth(distanceMm)
                        }
                        val boxes = if (frozen != null) listOf(frozen) else emptyList()
                        runOnUiThread {
                            displayResults(boxes, inferMs, frozenImageWidth, frozenImageHeight)
                        }
                    }
                }
                drawPreviewAndUpdateFps(rotatedBitmap)
            } catch (e: Exception) {
                if (arRunning) Log.e(TAG, "AR 프레임 루프 오류", e)
            }
        }
    }

    /** 회전된 비트맵 좌표 → ARCore 카메라 이미지(IMAGE_PIXELS) 좌표. displayRotationDegrees 기준 */
    private fun rotatedBitmapToCameraPixel(px: Int, py: Int, imageWidth: Int, imageHeight: Int): Pair<Int, Int> {
        return when (displayRotationDegrees) {
            0 -> Pair(px, py)
            90 -> Pair(py, imageWidth - 1 - px)
            180 -> Pair(imageWidth - 1 - px, imageHeight - 1 - py)
            270 -> Pair(imageHeight - 1 - py, px)
            else -> Pair(px, py)
        }
    }

    /** 카메라 이미지 픽셀(회전된 비트맵 좌표)에서 Depth 이미지 좌표로 변환 후 mm 값 반환. 실패 시 null */
    private fun getDepthAtCameraPixel(frame: Frame, centerX: Int, centerY: Int, imageWidth: Int, imageHeight: Int): Int? {
        val (cameraPx, cameraPy) = rotatedBitmapToCameraPixel(centerX, centerY, imageWidth, imageHeight)
        var depthImage: Image? = null
        try {
            depthImage = frame.acquireDepthImage16Bits()
        } catch (_: NotYetAvailableException) {
            return null
        } catch (_: Exception) {
            return null
        }
        try {
            val (depthX, depthY) = cameraToDepthPixel(frame, cameraPx.toFloat(), cameraPy.toFloat(), depthImage!!)
                ?: return null
            if (depthX < 0 || depthY < 0 || depthX >= depthImage.width || depthY >= depthImage.height) return null
            return getMillimetersDepth(depthImage, depthX, depthY)
        } finally {
            depthImage?.close()
        }
    }

    /** ARCore: 카메라 이미지(IMAGE_PIXELS) 픽셀 → Depth 이미지 픽셀. 변환 실패 시 null */
    private fun cameraToDepthPixel(frame: Frame, cameraPx: Float, cameraPy: Float, depthImage: Image): Pair<Int, Int>? {
        val cpuCoords = floatArrayOf(cameraPx, cameraPy)
        val textureCoords = FloatArray(2)
        frame.transformCoordinates2d(
            Coordinates2d.IMAGE_PIXELS,
            cpuCoords,
            Coordinates2d.TEXTURE_NORMALIZED,
            textureCoords
        )
        if (textureCoords[0] < 0 || textureCoords[0] > 1 || textureCoords[1] < 0 || textureCoords[1] > 1) return null
        val depthX = (textureCoords[0] * depthImage.width).toInt().coerceIn(0, depthImage.width - 1)
        val depthY = (textureCoords[1] * depthImage.height).toInt().coerceIn(0, depthImage.height - 1)
        return Pair(depthX, depthY)
    }

    /** Depth 이미지 (x,y) 픽셀의 거리(mm). 16비트 little-endian */
    private fun getMillimetersDepth(depthImage: Image, x: Int, y: Int): Int {
        val plane = depthImage.planes[0]
        val byteIndex = x * plane.pixelStride + y * plane.rowStride
        val buffer = plane.buffer.order(ByteOrder.nativeOrder())
        val depthSample = buffer.getShort(byteIndex)
        return depthSample.toInt() and 0xFFFF
    }

    private fun drawPreviewAndUpdateFps(bitmap: Bitmap) {
        runOnUiThread {
            val holder = (binding.previewView as? android.view.SurfaceView)?.holder ?: return@runOnUiThread
            val canvas = holder.lockCanvas() ?: return@runOnUiThread
            try {
                val vw = canvas.width
                val vh = canvas.height
                val scale = min(vw.toFloat() / bitmap.width, vh.toFloat() / bitmap.height)
                val dw = (bitmap.width * scale).toInt()
                val dh = (bitmap.height * scale).toInt()
                val left = (vw - dw) / 2
                val top = (vh - dh) / 2
                canvas.drawBitmap(bitmap, null, android.graphics.Rect(left, top, left + dw, top + dh), null)
            } finally {
                holder.unlockCanvasAndPost(canvas)
            }
            updateFPS()
        }
    }

    private var firstInferenceLogged = false
    private var firstResolutionLogged = false

    /** 화면이 거의 안 보일 정도로 어두우면 true (손으로 가림 등). threshold 낮을수록 어두운 이미지도 처리 */
    private fun isImageTooDark(bitmap: Bitmap, threshold: Int = 28): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w == 0 || h == 0) return true
        val step = max(1, min(w, h) / 20)
        var sum = 0L
        var count = 0
        for (y in 0 until h step step) {
            for (x in 0 until w step step) {
                val p = bitmap.getPixel(x, y)
                sum += ((p shr 16) and 0xFF) + ((p shr 8) and 0xFF) + (p and 0xFF)
                count++
            }
        }
        if (count == 0) return true
        val avg = (sum / count) / 3
        return avg < threshold
    }

    private fun runYOLOX(bitmap: Bitmap): List<OverlayView.DetectionBox> {
        if (yoloxInterpreter == null) return emptyList()

        try {
            val inputShape = yoloxInterpreter!!.getInputTensor(0).shape()
            val isNchw = inputShape.size >= 4 && inputShape[1] == 3
            val inputSize = when {
                isNchw -> inputShape[2]
                inputShape.size >= 4 -> inputShape[1]
                else -> inputShape.last()
            }
            val expectedBytes = 4 * inputSize * inputSize * 3
            val (preprocBitmap, letterboxScale) = letterboxBitmap(bitmap, inputSize)
            val inputBuffer = bitmapToFloatBuffer(preprocBitmap, inputSize, isNchw)
            if (preprocBitmap != bitmap) preprocBitmap.recycle()
            if (inputBuffer.remaining() != expectedBytes) {
                Log.e(TAG, "YOLOX 입력 버퍼 크기 불일치: ${inputBuffer.remaining()} != $expectedBytes")
                return emptyList()
            }
            val inferStart = System.currentTimeMillis()
            val outputShape = yoloxInterpreter!!.getOutputTensor(0).shape()
            val output = Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
            yoloxInterpreter!!.run(inputBuffer, output)
            val inferMs = System.currentTimeMillis() - inferStart
            if (isImageTooDark(bitmap)) {
                return emptyList()
            }
            val detections = parseYOLOXOutput(
                output[0],
                outputShape[1],
                outputShape[2],
                bitmap.width,
                bitmap.height,
                inputSize,
                letterboxScaleR = letterboxScale
            )
            if (!firstInferenceLogged) firstInferenceLogged = true
            return detections
        } catch (e: Exception) {
            Log.e(TAG, "YOLOX 추론 실패", e)
            return emptyList()
        }
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap, size: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * size * size * 3)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)

        for (pixelValue in pixels) {
            // [수정] FP16 모델은 0~255 값을 0.0~1.0으로 '정규화' 해줘야 합니다!
            // 기존 코드: buffer.putFloat(...) -> 틀림
            // 수정 코드: 255.0f로 나누기

            buffer.putFloat(((pixelValue shr 16) and 0xFF) / 255.0f) // R
            buffer.putFloat(((pixelValue shr 8) and 0xFF) / 255.0f)  // G
            buffer.putFloat((pixelValue and 0xFF) / 255.0f)          // B
        }

        return buffer
    }

    private fun parseYOLOXOutput(
        output: Array<FloatArray>,
        dim1: Int,
        dim2: Int,
        imageWidth: Int,
        imageHeight: Int,
        inputSize: Int,
        letterboxScaleR: Float? = null
    ): List<OverlayView.DetectionBox> {
        val numBoxes: Int
        val boxSize: Int
        val isRowMajor: Boolean

        if (dim1 >= dim2) {
            numBoxes = dim1
            boxSize = dim2
            isRowMajor = true
        } else {
            numBoxes = dim2
            boxSize = dim1
            isRowMajor = false
        }

        val isNormalized = if (isRowMajor && output.isNotEmpty() && output[0].size >= 4) {
            maxOf(output[0][0], output[0][1], output[0][2], output[0][3]) <= 1.5f
        } else if (!isRowMajor && output.size >= 4) {
            maxOf(output[0][0], output[1][0], output[2][0], output[3][0]) <= 1.5f
        } else true

        if (!yoloxShapeLogged) yoloxShapeLogged = true

        val candidates = mutableListOf<OverlayView.DetectionBox>()
        // 4(box)+1(obj)+N(cls): 85(80), 58(53), 55(50), 54(49)
        val hasObjectness = (boxSize == 85 || boxSize == 58 || boxSize == 55 || boxSize == 54)
        val scoreStart = if (hasObjectness) 5 else 4
        val classCount = (boxSize - scoreStart).coerceAtLeast(1)

        val useSingleScoreFormat = (numBoxes <= 100 && boxSize >= 5)
        val minConfidenceToShow = when {
            useSingleScoreFormat -> 0.35f
            hasObjectness -> 0.45f
            else -> 0.78f
        }

        var maxConfidenceSeen = 0f

        for (j in 0 until numBoxes) {
            val v0: Float
            val v1: Float
            val v2: Float
            val v3: Float
            val classScores = FloatArray(classCount)
            var singleScore = 0f

            if (isRowMajor) {
                val row = output[j]
                if (row.size < boxSize) continue
                v0 = row[0]; v1 = row[1]; v2 = row[2]; v3 = row[3]
                if (row.size > 4) singleScore = row[4]
                for (c in 0 until classCount) classScores[c] = row[scoreStart + c]
            } else {
                v0 = output[0][j]; v1 = output[1][j]; v2 = output[2][j]; v3 = output[3][j]
                if (dim1 > 4) singleScore = output[4][j]
                for (c in 0 until classCount) classScores[c] = output[scoreStart + c][j]
            }

            val objScore = singleScore.coerceIn(0f, 1f)
            val topK = 3
            val topClassIds = classScores.indices
                .map { c ->
                    var rs = classScores[c]
                    if (rs > 1f || rs < 0f) rs = 1f / (1f + exp(-rs))
                    c to (objScore * rs).coerceIn(0f, 1f)
                }
                .sortedByDescending { it.second }
                .take(topK)
            val confidence = topClassIds.firstOrNull()?.second ?: 0f
            if (confidence > maxConfidenceSeen) maxConfidenceSeen = confidence
            if (confidence < minConfidenceToShow) continue

            // 50% 이상인 클래스만 표시 (상위 3개 중에서)
            val topLabels = topClassIds
                .filter { (_, conf) -> conf >= 0.5f }
                .map { (cid, conf) ->
                    getClassLabel(cid) to (conf * 100).toInt()
                }

            // 좌표: 정규화 0~1이고 v2>v0, v3>v1 이면 (x1,y1,x2,y2). 아니면 (cx,cy,w,h).
            val left: Float
            val top: Float
            val right: Float
            val bottom: Float
            if (isNormalized && v2 > v0 && v3 > v1) {
                left = (v0 * imageWidth).coerceIn(0f, imageWidth.toFloat())
                top = (v1 * imageHeight).coerceIn(0f, imageHeight.toFloat())
                right = (v2 * imageWidth).coerceIn(0f, imageWidth.toFloat())
                bottom = (v3 * imageHeight).coerceIn(0f, imageHeight.toFloat())
            } else {
                if (letterboxScaleR != null && letterboxScaleR > 0f) {
                    // Letterbox: 모델 출력은 letterbox 캔버스 좌표 → 원본 이미지 좌표 = modelCoord / r
                    val cx = (v0 / letterboxScaleR).coerceIn(0f, imageWidth.toFloat())
                    val cy = (v1 / letterboxScaleR).coerceIn(0f, imageHeight.toFloat())
                    val w = (v2 / letterboxScaleR) / 2f
                    val h = (v3 / letterboxScaleR) / 2f
                    left = max(0f, cx - w)
                    top = max(0f, cy - h)
                    right = min(imageWidth.toFloat(), cx + w)
                    bottom = min(imageHeight.toFloat(), cy + h)
                } else if (isNormalized) {
                    val cx = v0.coerceIn(0f, 1f) * imageWidth
                    val cy = v1.coerceIn(0f, 1f) * imageHeight
                    val w = (v2.coerceIn(0f, 1f) * imageWidth) / 2f
                    val h = (v3.coerceIn(0f, 1f) * imageHeight) / 2f
                    left = max(0f, cx - w)
                    top = max(0f, cy - h)
                    right = min(imageWidth.toFloat(), cx + w)
                    bottom = min(imageHeight.toFloat(), cy + h)
                } else {
                    val cx = v0 / inputSize * imageWidth
                    val cy = v1 / inputSize * imageHeight
                    val w = (v2 / inputSize * imageWidth) / 2f
                    val h = (v3 / inputSize * imageHeight) / 2f
                    left = max(0f, cx - w)
                    top = max(0f, cy - h)
                    right = min(imageWidth.toFloat(), cx + w)
                    bottom = min(imageHeight.toFloat(), cy + h)
                }
            }

            if (right <= left || bottom <= top) continue

            candidates.add(
                OverlayView.DetectionBox(
                    label = topLabels.firstOrNull()?.first ?: "",
                    confidence = confidence,
                    rect = android.graphics.RectF(left, top, right, bottom),
                    topLabels = topLabels
                )
            )
        }

        lastYoloxMaxConf = maxConfidenceSeen
        return nms(candidates, iouThreshold = 0.6f)
    }

    private fun nms(boxes: List<OverlayView.DetectionBox>, iouThreshold: Float): List<OverlayView.DetectionBox> {
        val sorted = boxes.sortedByDescending { it.confidence }
        val picked = mutableListOf<OverlayView.DetectionBox>()
        val used = BooleanArray(sorted.size)

        for (i in sorted.indices) {
            if (used[i]) continue
            picked.add(sorted[i])
            for (j in i + 1 until sorted.size) {
                if (used[j]) continue
                if (iou(sorted[i].rect, sorted[j].rect) > iouThreshold) used[j] = true
            }
        }
        return picked
    }

    /** 손 랜드마크(정규화 0~1) → 이미지 좌표 RectF */
    private fun handLandmarksToRect(landmarks: List<NormalizedLandmark>, imageWidth: Int, imageHeight: Int): RectF {
        if (landmarks.isEmpty()) return RectF(0f, 0f, 0f, 0f)
        val xs = landmarks.map { it.x().coerceIn(0f, 1f) * imageWidth }
        val ys = landmarks.map { it.y().coerceIn(0f, 1f) * imageHeight }
        return RectF(xs.min(), ys.min(), xs.max(), ys.max())
    }

    /** 손 랜드마크들 → 모든 손을 포함하는 단일 RectF (optical flow 마스킹용) */
    private fun mergedHandRect(handsResult: HandLandmarkerResult?, imageWidth: Int, imageHeight: Int): RectF? {
        val landmarks = handsResult?.landmarks() ?: return null
        if (landmarks.isEmpty()) return null
        var union: RectF? = null
        for (hand in landmarks) {
            val r = handLandmarksToRect(hand, imageWidth, imageHeight)
            union = if (union == null) r else {
                val u = RectF(union)
                u.union(r)
                u
            }
        }
        return union
    }

    /** 손이 박스와 겹치면 true (occlusion, optical flow용) */
    private fun isHandOverlappingBox(handsResult: HandLandmarkerResult?, boxRect: RectF, imageWidth: Int, imageHeight: Int): Boolean {
        val landmarks = handsResult?.landmarks() ?: return false
        if (imageWidth <= 0 || imageHeight <= 0) return false
        return landmarks.any { hand ->
            val handRect = handLandmarksToRect(hand, imageWidth, imageHeight)
            iou(handRect, boxRect) > 0.1f
        }
    }

    /**
     * near-contact(닿음) 판정: 엄지·검지 중간점이 확장된 타겟 박스 안에 있으면 true.
     * 시각장애인 UX용 — "손이 제품에 닿았거나 충분히 가까울 때" 안내.
     * 좌표는 기존 landmarkToPoint(정규화 → 이미지 픽셀) 유지.
     */
    private fun isHandTouchingTargetBox(
        handsResult: HandLandmarkerResult?,
        targetBox: RectF,
        imageWidth: Int,
        imageHeight: Int
    ): Boolean {
        val landmarks = handsResult?.landmarks() ?: run {
            lastTouchMidpointPx = null
            return false
        }
        if (imageWidth <= 0 || imageHeight <= 0) {
            lastTouchMidpointPx = null
            return false
        }
        val hand = landmarks.firstOrNull() ?: run {
            lastTouchMidpointPx = null
            return false
        }
        if (hand.size < 9) {
            lastTouchMidpointPx = null
            return false
        }
        val thumb = landmarkToPoint(hand, THUMB_TIP, imageWidth, imageHeight) ?: run {
            lastTouchMidpointPx = null
            return false
        }
        val index = landmarkToPoint(hand, INDEX_TIP, imageWidth, imageHeight) ?: run {
            lastTouchMidpointPx = null
            return false
        }
        val midX = (thumb.first + index.first) / 2f
        val midY = (thumb.second + index.second) / 2f
        lastTouchMidpointPx = Pair(midX, midY)

        val w = targetBox.width().coerceAtLeast(1f)
        val h = targetBox.height().coerceAtLeast(1f)
        val expandW = w * TOUCH_BOX_EXPAND_RATIO
        val expandH = h * TOUCH_BOX_EXPAND_RATIO
        val touchBox = RectF(
            targetBox.left - expandW,
            targetBox.top - expandH,
            targetBox.right + expandW,
            targetBox.bottom + expandH
        )
        return touchBox.contains(midX, midY)
    }

    /** 랜드마크를 이미지 좌표 (x,y) 로 변환 */
    private
    fun landmarkToPoint(hand: List<NormalizedLandmark>, index: Int, imageWidth: Int, imageHeight: Int): Pair<Float, Float>? {
        if (index < 0 || index >= hand.size) return null
        val lm = hand[index]
        return Pair(
            lm.x().coerceIn(0f, 1f) * imageWidth,
            lm.y().coerceIn(0f, 1f) * imageHeight
        )
    }

    /**
     * [미사용] 기존 pinch 잡기 판정. near-contact(isHandTouchingTargetBox)으로 대체됨.
     * 참고용으로 유지.
     */
    @Suppress("unused")
    private fun isPinchGrab(handsResult: HandLandmarkerResult?, boxRect: RectF, imageWidth: Int, imageHeight: Int): Boolean {
        val landmarks = handsResult?.landmarks() ?: return false
        if (imageWidth <= 0 || imageHeight <= 0) return false
        val boxW = max(boxRect.width(), 20f)
        val boxH = max(boxRect.height(), 20f)
        val touchMargin = max(boxW, boxH) * 0.02f
        val touchBox = RectF(
            boxRect.left - touchMargin,
            boxRect.top - touchMargin,
            boxRect.right + touchMargin,
            boxRect.bottom + touchMargin
        )
        for (hand in landmarks) {
            if (hand.size < 21) continue
            val thumb = landmarkToPoint(hand, THUMB_TIP, imageWidth, imageHeight) ?: continue
            val index = landmarkToPoint(hand, INDEX_TIP, imageWidth, imageHeight) ?: continue
            val thumbOnObject = touchBox.contains(thumb.first, thumb.second)
            val indexOnObject = touchBox.contains(index.first, index.second)
            if (!thumbOnObject || !indexOnObject) continue

            val middle = landmarkToPoint(hand, MIDDLE_TIP, imageWidth, imageHeight)
            val ring = landmarkToPoint(hand, RING_TIP, imageWidth, imageHeight)
            val pinky = landmarkToPoint(hand, PINKY_TIP, imageWidth, imageHeight)
            val middleBehind = middle != null && !boxRect.contains(middle.first, middle.second)
            val ringBehind = ring != null && !boxRect.contains(ring.first, ring.second)
            val pinkyBehind = pinky != null && !boxRect.contains(pinky.first, pinky.second)
            val behindCount = listOf(middleBehind, ringBehind, pinkyBehind).count { it }
            if (behindCount >= 2) return true
        }
        return false
    }

    /** LOCKED 상태에서 손 위치에 따른 TTS 안내 문구 (손을 더 뻗어주세요, 앞으로 움직여주세요 등) */
    private fun buildHandPositionGuidance(handsResult: HandLandmarkerResult?, boxRect: RectF, imageWidth: Int, imageHeight: Int): String? {
        val landmarks = handsResult?.landmarks() ?: return null
        if (landmarks.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return null
        val hand = landmarks.first()
        if (hand.size <= WRIST) return null
        val wrist = landmarkToPoint(hand, WRIST, imageWidth, imageHeight) ?: return null
        val boxCenterX = (boxRect.left + boxRect.right) / 2f
        val boxCenterY = (boxRect.top + boxRect.bottom) / 2f
        val dx = (wrist.first - boxCenterX) / imageWidth
        val dy = (wrist.second - boxCenterY) / imageHeight
        val dist = sqrt(dx * dx + dy * dy)

        return when {
            dist > 0.45f -> "손을 더 뻗어주세요. 앞으로 조금 더 움직여주세요."
            dist > 0.3f -> "조금 더 앞으로 다가가 주세요."
            dx > 0.15f -> "오른쪽으로 조금 움직여주세요."
            dx < -0.15f -> "왼쪽으로 조금 움직여주세요."
            dy > 0.12f -> "아래로 조금 움직여주세요."
            dy < -0.12f -> "위로 조금 움직여주세요."
            else -> null
        }
    }

    private fun startHandGuidanceTTS() {
        stopHandGuidanceTTS()
        handGuidanceRunnable = object : Runnable {
            override fun run() {
                if (searchState != SearchState.LOCKED || ttsGrabbedPlayed) return
                val box = frozenBox ?: return
                val w = frozenImageWidth
                val h = frozenImageHeight
                if (w <= 0 || h <= 0) return
                val guidance = buildHandPositionGuidance(latestHandsResult.get(), box.rect, w, h)
                if (!guidance.isNullOrBlank()) {
                    lastHandGuidanceTimeMs = System.currentTimeMillis()
                    speak(guidance)
                }
                handGuidanceHandler.postDelayed(this, HAND_GUIDANCE_INTERVAL_MS)
            }
        }
        handGuidanceHandler.postDelayed(handGuidanceRunnable!!, HAND_GUIDANCE_INTERVAL_MS)
    }

    private fun stopHandGuidanceTTS() {
        handGuidanceRunnable?.let { handGuidanceHandler.removeCallbacks(it) }
        handGuidanceRunnable = null
    }

    /** LIVE_STREAM: 프레임만 넘기고 즉시 return. 결과는 resultListener 콜백으로 옴. */
    private fun sendFrameToHands(bitmap: Bitmap, timestampNs: Long) {
        if (handLandmarker == null) return
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestampMs = timestampNs / 1_000_000
            handLandmarker!!.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e(TAG, "Hands detectAsync 실패", e)
        }
    }

    private fun iou(a: android.graphics.RectF, b: android.graphics.RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interW = max(0f, interRight - interLeft)
        val interH = max(0f, interBottom - interTop)
        val interArea = interW * interH
        val areaA = a.width() * a.height()
        val areaB = b.width() * b.height()
        return interArea / (areaA + areaB - interArea + 1e-6f)
    }

    private fun displayResults(
        detections: List<OverlayView.DetectionBox>,
        inferenceTime: Long,
        imageWidth: Int,
        imageHeight: Int
    ) {
        runOnUiThread {
            binding.overlayView.setDetections(detections, imageWidth, imageHeight)
            binding.overlayView.setHands(
                if (searchState == SearchState.LOCKED) latestHandsResult.get() else null
            )
            if (searchState == SearchState.LOCKED && lastTouchMidpointPx != null) {
                binding.overlayView.setTouchDebugPoint(lastTouchMidpointPx!!.first, lastTouchMidpointPx!!.second)
            } else {
                binding.overlayView.setTouchDebugPoint(null, null)
            }

            when (searchState) {
                    SearchState.SEARCHING -> {
                        val msg = "찾는 중: ${getTargetLabel()}"
                        binding.systemMessageText.text = msg
                        binding.statusText.text = msg
                    }
                    SearchState.LOCKED -> {
                        if (frozenBox != null) {
                            val msg = when {
                                waitingForTouchConfirm && (sttManager?.isListening() == true) -> "듣는 중..."
                                waitingForTouchConfirm -> "상품에 닿았나요? 예라고 말해주세요."
                                touchActive -> "손이 제품에 닿았어요"
                                else -> "손을 뻗어 잡아주세요"
                            }
                            binding.systemMessageText.text = msg
                            binding.statusText.text = msg
                        }
                    }
            }
        }
    }

    private fun updateFPS() {
        frameCount++
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFpsTime >= 1000) {
            frameCount = 0
            lastFpsTime = currentTime
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                showFirstScreen()
                playWelcomeTTS()
            } else {
                Toast.makeText(this, "카메라 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelSearchTimeout()
        stopPositionAnnounce()
        arExecutor.shutdown()
        yoloxInterpreter?.close()
        gpuDelegate?.close()
        gyroManager.stopTracking()
        sttManager?.release()
        ttsManager?.release()
        beepPlayer?.release()
        handLandmarker?.close()
        if (::gyroManager.isInitialized) gyroManager.stopTracking()
    }

    override fun onResume() {
        super.onResume()
        if (arSession != null) {
            arSession?.resume()
        } else if (screenState == ScreenState.CAMERA_SCREEN) {
            startArSession()
        }
    }

    override fun onPause() {
        super.onPause()
        arSession?.pause()
        ttsManager?.stop()
        sttManager?.stopListening()
    }

    override fun onStop() {
        super.onStop()
        // 앱이 백그라운드로 나가면 카메라/음성 인식/타이머를 정리해 백그라운드 동작을 최소화
        cancelSearchTimeout()
        stopPositionAnnounce()
        try {
            stopArSession()
        } catch (_: Exception) {}
        sttManager?.stopListening()
        ttsManager?.stop()
    }

    companion object {
        private const val TAG = "GrabIT_Test"
        private var yoloxShapeLogged = false
        private const val THUMB_TIP = 4
        private const val INDEX_TIP = 8
        private const val MIDDLE_TIP = 12
        private const val RING_TIP = 16
        private const val PINKY_TIP = 20
        private const val WRIST = 0
    }
}