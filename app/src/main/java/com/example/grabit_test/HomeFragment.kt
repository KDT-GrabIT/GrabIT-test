package com.example.grabitTest

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.grabitTest.data.synonym.SynonymRepository
import com.example.grabitTest.databinding.FragmentHomeBinding
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedViewModel by activityViewModels()

    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    @Volatile private var currentZoomRatio = 1f

    private val scanHandler = Handler(Looper.getMainLooper())
    private var isScanning = false
    private var isZoomedIn = false
    private val SCAN_INITIAL_DELAY_MS = 3000L
    private val SCAN_ZOOM_IN_MS = 800L
    private val SCAN_ZOOM_OUT_MS = 1500L

    private var yoloxInterpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var handLandmarker: HandLandmarker? = null
    private val latestHandsResult = AtomicReference<HandLandmarkerResult?>(null)

    private var classLabels: List<String> = emptyList()

    enum class SearchState { IDLE, SEARCHING, LOCKED }
    private var searchState = SearchState.IDLE
    private var frozenBox: OverlayView.DetectionBox? = null
    private var frozenImageWidth = 0
    private var frozenImageHeight = 0
    private var lockedTargetLabel: String = ""

    private val LOCK_CONFIRM_FRAMES = 2
    private var pendingLockBox: OverlayView.DetectionBox? = null
    private var pendingLockCount = 0

    private val VALIDATION_INTERVAL = 3
    private val VALIDATION_FAIL_LIMIT = 3
    private var validationFailCount = 0
    private var lockedFrameCount = 0

    private lateinit var gyroManager: GyroTrackingManager
    private val opticalFlowTracker = OpticalFlowTracker()
    private var wasOccluded = false

    private val TARGET_CONFIDENCE_THRESHOLD = 0.5f
    private val currentTargetLabel = AtomicReference<String>("")

    private var lastDirectionGuidanceTime = 0L
    private var lastDistanceGuidanceTime = 0L
    private var lastActionGuidanceTime = 0L
    private val DIRECTION_GUIDANCE_COOLDOWN_MS = 4000L   // 왼쪽/오른쪽 (4초)
    private val DISTANCE_GUIDANCE_COOLDOWN_MS = 7000L    // "앞으로 오세요" (7초)
    private val ACTION_GUIDANCE_COOLDOWN_MS = 10000L    // "손을 뻗어 확인해보세요" (10초)
    private val SAFE_ZONE_LEFT = 0.3f   // centerXNorm < 0.3 → Left Zone
    private val SAFE_ZONE_RIGHT = 0.7f  // centerXNorm > 0.7 → Right Zone (Safe: 0.3..0.7)
    private var lastZoomDecayTime = 0L
    private val TARGET_BOX_RATIO = 0.12f  // 폴백: 거리 계산 실패 시 사용
    private val CLOSE_DISTANCE_STABILITY_FRAMES = 15    // 500mm 이하 연속 15프레임(~0.5초) 시에만 "손을 뻗어" 허용
    private var closeDistanceFrameCount = 0
    private val ZOOM_DECAY_INTERVAL_MS = 80L
    private val FOCAL_LENGTH_FACTOR = 1.1f   // pinhole: FocalLength = imageHeight * this
    private val DISTANCE_NEAR_MM = 500f       // 50cm 이하 → "손을 뻗어 확인해보세요"
    private val DISTANCE_ZOOM_OUT_MM = 700f   // 700mm 미만 → 1.0x 빠르게 복귀
    private val DISTANCE_ZOOM_IN_MM = 1500f    // 1500mm 이상 → 줌 인
    private val MAX_ZOOM_SCAN = 2.5f           // 거리 기반 줌 최대 2.5x
    private val ZOOM_LERP_SPEED_OUT = 0.5f
    private val ZOOM_LERP_SPEED_IN = 0.04f
    private val ZOOM_LERP_SPEED_OUT_FAST = 0.7f  // 700mm 미만 시 더 빠른 복귀

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
    private var voiceSearchTargetLabel: String? = null

    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )
    private val REQUEST_CODE_PERMISSIONS = 10

    private var ttsDetectedPlayed = false
    private var ttsGrabPlayed = false
    private var ttsGrabbedPlayed = false
    private var ttsAskAnotherPlayed = false
    private var waitingForTouchConfirm = false
    private val STT_MAX_RETRIES = 3
    private var touchConfirmSttRetryCount = 0
    private var voiceFlowSttRetryCount = 0
    private var voiceConfirmSilentRetryCount = 0
    private var handsOverlapFrameCount = 0
    private var pinchGrabFrameCount = 0

    private val handGuidanceHandler = Handler(Looper.getMainLooper())
    private var handGuidanceRunnable: Runnable? = null
    private var lastHandGuidanceTimeMs = 0L
    private val HAND_GUIDANCE_INTERVAL_MS = 5000L

    private var lastSuccessfulValidationTimeMs = 0L
    private val REACQUIRE_INFERENCE_AFTER_MS = 2000L
    private var touchConfirmInProgress = false
    private var touchConfirmScheduled = false

    private val TOUCH_BOX_EXPAND_RATIO = 0.22f
    private val TOUCH_CONFIRM_FRAMES = 4
    private val RELEASE_HOLD_FRAMES = 10
    private val TOUCH_TTS_COOLDOWN_MS = 1800L

    private var touchFrameCount = 0
    private var releaseFrameCount = 0
    private var touchActive = false
    private var lastTouchTtsTimeMs = 0L
    private var lastTouchMidpointPx: Pair<Float, Float>? = null

    private var scanRunnable: Runnable? = null
    private var firstInferenceLogged = false
    private var firstResolutionLogged = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadClassLabels()
        ProductDictionary.load(requireContext())
        loadSynonymFromRemote()
        initYOLOX()
        initMediaPipeHands()
        initGyroTrackingManager()
        initSttTts()

        binding.voiceInputButton.setOnClickListener { onVoiceInputClicked() }
        binding.confirmBtn.setOnClickListener { voiceFlowController?.onConfirmClicked() }
        binding.reinputBtn.setOnClickListener { voiceFlowController?.onReinputClicked() }
        binding.retryBtn.setOnClickListener { voiceFlowController?.onRetrySearch() }
        setupPinchZoom()

        sharedViewModel.adminSelectedTarget.observe(viewLifecycleOwner) { target ->
            target?.let {
                sharedViewModel.consumeAdminSelectedTarget()
                startDetectionFromAdmin(it)
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
            voiceFlowController?.start()
        } else {
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted() && _binding != null) {
            startCamera()
        }
    }

    override fun onPause() {
        super.onPause()
        resetGlobalState()
    }

    override fun onStop() {
        super.onStop()
        cancelSearchTimeout()
        stopPositionAnnounce()
        try { stopCamera() } catch (_: Exception) {}
        sttManager?.stopListening()
        ttsManager?.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        cameraExecutor.shutdown()
        yoloxInterpreter?.close()
        gpuDelegate?.close()
        gyroManager.stopTracking()
        sttManager?.release()
        ttsManager?.release()
        beepPlayer?.release()
        handLandmarker?.close()
        _binding = null
    }

    private fun loadSynonymFromRemote() {
        CoroutineScope(Dispatchers.Main).launch {
            withContext(Dispatchers.IO) { SynonymRepository.loadFromRemote() }
        }
    }

    private fun onVoiceInputClicked() {
        if (!allPermissionsGranted()) {
            Toast.makeText(requireContext(), "마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
            requestPermissions(REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            return
        }
        if (voiceFlowController == null) {
            Toast.makeText(requireContext(), "준비 중입니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
            return
        }
        ttsManager?.stop()
        sttManager?.cancelListening()
        voiceFlowController?.startProductNameInput()
    }

    private fun startDetectionFromAdmin(targetLabel: String) {
        currentTargetLabel.set(targetLabel)
        val displayName = if (ProductDictionary.isLoaded()) ProductDictionary.getDisplayNameKo(targetLabel) else targetLabel
        requireActivity().runOnUiThread {
            binding.systemMessageText.text = "찾는 중: $displayName"
            startScanningDirect(targetLabel)
        }
    }

    /** @param urgent true면 TTS 재생 중이어도 즉시 송출. false면 재생 중이면 무시(Drop). onDone은 마지막에 두어 trailing lambda 사용 가능. */
    private fun speak(text: String, urgent: Boolean = true, onDone: (() -> Unit)? = null) {
        if (!urgent && (ttsManager?.isSpeaking() == true)) return
        ttsManager?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, onDone)
    }

    private fun updateVoiceButtonVisibility() {
        val listening = sttManager?.isListening() == true
        val hasTarget = getTargetLabel().isNotEmpty()
        val inSearchMode = hasTarget && (searchState == SearchState.SEARCHING || searchState == SearchState.LOCKED)
        val showButton = !listening && !inSearchMode
        binding.voiceButtonArea.visibility = if (showButton) View.VISIBLE else View.GONE
    }

    private fun resetGlobalState() {
        camera?.cameraControl?.setZoomRatio(1.0f)
        currentZoomRatio = 1.0f
        searchState = SearchState.IDLE
        currentTargetLabel.set("")
        ttsManager?.stop()
        sttManager?.cancelListening()
        isScanning = false
        isZoomedIn = false
        cancelSearchTimeout()
        stopPositionAnnounce()
        stopScanning()
        stopHandGuidanceTTS()
        scanHandler.removeCallbacksAndMessages(null)
        voiceSearchTargetLabel = null
        ttsDetectedPlayed = false
        ttsGrabPlayed = false
        ttsGrabbedPlayed = false
        ttsAskAnotherPlayed = false
        waitingForTouchConfirm = false
        touchConfirmInProgress = false
        touchConfirmScheduled = false
        touchActive = false
        wasOccluded = false
        pendingLockBox = null
        pendingLockCount = 0
        validationFailCount = 0
        closeDistanceFrameCount = 0
        lastDirectionGuidanceTime = 0L
        lastDistanceGuidanceTime = 0L
        lastActionGuidanceTime = 0L
        lockedTargetLabel = ""
        frozenBox = null
        latestHandsResult.set(null)
        opticalFlowTracker.reset()
        if (::gyroManager.isInitialized) {
            try { gyroManager.stopTracking() } catch (_: Exception) {}
        }
        voiceFlowController?.start()
        _binding?.let { b ->
            b.overlayView.setDetections(emptyList(), 0, 0)
            b.overlayView.setFrozen(false)
            b.searchTargetLabel.visibility = View.GONE
            b.systemMessageText.text = ""
            b.userSpeechText.text = ""
            b.voiceFlowButtons.visibility = View.GONE
            updateVoiceButtonVisibility()
        }
    }

    private fun startScanningDirect(targetLabel: String) {
        currentTargetLabel.set(targetLabel)
        transitionToSearching()
        startCamera()
        startSearchTimeout()
        updateVoiceButtonVisibility()
    }

    private fun initSttTts() {
        ttsManager = TTSManager(
            context = requireContext(),
            onReady = { },
            onSpeakDone = { },
            onError = { msg ->
                requireActivity().runOnUiThread {
                    Log.e(TAG, "[TTS 에러] $msg")
                    Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                }
            }
        )
        beepPlayer = BeepPlayer().also { it.init() }

        ttsManager?.init { success ->
            requireActivity().runOnUiThread {
                if (success && beepPlayer != null) {
                    voiceFlowController = VoiceFlowController(
                        ttsManager = ttsManager!!,
                        onStateChanged = { _, _ -> requireActivity().runOnUiThread { updateVoiceFlowButtons(); updateVoiceButtonVisibility() } },
                        onSystemAnnounce = { msg -> requireActivity().runOnUiThread { binding.systemMessageText.text = msg } },
                        onRequestStartStt = { requireActivity().runOnUiThread { sttManager?.startListening() } },
                        onStartSearch = { productName -> requireActivity().runOnUiThread { onStartSearchFromVoiceFlow(productName) } },
                        onProductNameEntered = { productName -> requireActivity().runOnUiThread { setTargetFromSpokenProductName(productName) } }
                    )
                    voiceFlowController?.start()
                }
            }
        }

        sttManager = STTManager(
            context = requireContext(),
            onResult = { text ->
                requireActivity().runOnUiThread {
                    binding.userSpeechText.text = text
                    if (waitingForTouchConfirm) {
                        waitingForTouchConfirm = false
                        handleTouchConfirmYesNo(text)
                        return@runOnUiThread
                    }
                    voiceFlowSttRetryCount = 0
                    if (voiceFlowController?.currentState == VoiceFlowController.VoiceFlowState.WAITING_PRODUCT_NAME) {
                        voiceConfirmSilentRetryCount = 0
                    }
                    voiceFlowController?.onSttResult(text)
                }
            },
            onError = { msg ->
                requireActivity().runOnUiThread {
                    binding.systemMessageText.text = msg
                    if (waitingForTouchConfirm) {
                        waitingForTouchConfirm = false
                        binding.systemMessageText.text = "손을 뻗어 잡아주세요"
                        touchActive = false
                        startPositionAnnounce()
                        Toast.makeText(requireContext(), "음성 인식 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onErrorWithCode = { msg, errorCode ->
                requireActivity().runOnUiThread {
                    val state = voiceFlowController?.currentState
                    val isNoMatchOrTimeout =
                        errorCode == android.speech.SpeechRecognizer.ERROR_NO_MATCH ||
                            errorCode == android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT
                    val isSttWaiting = waitingForTouchConfirm ||
                        state == VoiceFlowController.VoiceFlowState.WAITING_PRODUCT_NAME ||
                        state == VoiceFlowController.VoiceFlowState.WAITING_CONFIRMATION

                    if (isNoMatchOrTimeout && isSttWaiting) {
                        val isTouchConfirm = waitingForTouchConfirm
                        val isVoiceConfirm = state == VoiceFlowController.VoiceFlowState.WAITING_CONFIRMATION
                        val retryCount = when {
                            isTouchConfirm -> touchConfirmSttRetryCount
                            else -> voiceFlowSttRetryCount
                        }
                        if (isTouchConfirm && retryCount < 2) {
                            touchConfirmSttRetryCount++
                            view?.postDelayed({ if (waitingForTouchConfirm) sttManager?.startListening() }, 400L)
                            return@runOnUiThread
                        }
                        if (isVoiceConfirm && voiceConfirmSilentRetryCount < 2) {
                            voiceConfirmSilentRetryCount++
                            view?.postDelayed({
                                if (voiceFlowController?.currentState != VoiceFlowController.VoiceFlowState.WAITING_CONFIRMATION) return@postDelayed
                                sttManager?.startListening()
                            }, 400L)
                            return@runOnUiThread
                        }
                        binding.systemMessageText.text = "음성 인식 실패: $msg (코드 $errorCode)"
                        if (retryCount < STT_MAX_RETRIES) {
                            if (isTouchConfirm) touchConfirmSttRetryCount++ else voiceFlowSttRetryCount++
                            speak("$msg") {
                                requireActivity().runOnUiThread {
                                    speak("다시 말해주세요.") {
                                        requireActivity().runOnUiThread {
                                            view?.postDelayed({ sttManager?.startListening() }, 800L)
                                        }
                                    }
                                }
                            }
                        } else {
                            if (isTouchConfirm) {
                                touchConfirmSttRetryCount = 0
                                waitingForTouchConfirm = false
                                binding.systemMessageText.text = "손을 뻗어 잡아주세요"
                                touchActive = false
                                startPositionAnnounce()
                            } else {
                                voiceFlowSttRetryCount = 0
                                voiceConfirmSilentRetryCount = 0
                            }
                            speak("$msg") {
                                if (isTouchConfirm) {
                                    speak("음성 입력 버튼을 눌러 다시 시작해주세요.") {}
                                } else {
                                    speak(VoicePrompts.PROMPT_TOUCH_RESTART) {
                                        voiceFlowController?.start()
                                        transitionToSearching()
                                        currentTargetLabel.set("")
                                        updateSearchTargetLabel()
                                        updateVoiceButtonVisibility()
                                    }
                                }
                            }
                        }
                        return@runOnUiThread
                    }

                    val isRetryable = errorCode == android.speech.SpeechRecognizer.ERROR_RECOGNIZER_BUSY
                    val isVoiceFlowWaiting = state == VoiceFlowController.VoiceFlowState.WAITING_PRODUCT_NAME ||
                        state == VoiceFlowController.VoiceFlowState.WAITING_CONFIRMATION
                    if (isRetryable && isVoiceFlowWaiting) {
                        binding.systemMessageText.text = "다시 듣는 중..."
                        view?.postDelayed({ sttManager?.startListening() }, 800L)
                    }
                }
            },
            onListeningChanged = { listening ->
                requireActivity().runOnUiThread {
                    if (listening) {
                        binding.systemMessageText.text = "듣는 중..."
                        binding.userSpeechText.text = ""
                    } else if (binding.systemMessageText.text == "듣는 중...") {
                        binding.systemMessageText.text = ""
                    }
                    updateVoiceButtonVisibility()
                }
            },
            onPartialResult = { text ->
                requireActivity().runOnUiThread {
                    if (text.isNotBlank()) binding.userSpeechText.text = text
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
            onListeningEndedReason = { },
            beepPlayer = beepPlayer
        ).also { it.init() }
    }

    private fun updateVoiceFlowButtons() {
        val state = voiceFlowController?.currentState ?: return
        when (state) {
            VoiceFlowController.VoiceFlowState.CONFIRM_PRODUCT,
            VoiceFlowController.VoiceFlowState.WAITING_CONFIRMATION -> {
                binding.voiceFlowButtons.visibility = View.VISIBLE
                binding.confirmBtn.visibility = View.VISIBLE
                binding.reinputBtn.visibility = View.VISIBLE
                binding.retryBtn.visibility = View.GONE
            }
            VoiceFlowController.VoiceFlowState.SEARCH_RESULT -> {
                binding.voiceFlowButtons.visibility = View.VISIBLE
                binding.confirmBtn.visibility = View.GONE
                binding.reinputBtn.visibility = View.GONE
                binding.retryBtn.visibility = View.VISIBLE
            }
            VoiceFlowController.VoiceFlowState.SEARCH_FAILED -> {
                binding.voiceFlowButtons.visibility = View.VISIBLE
                binding.confirmBtn.visibility = View.GONE
                binding.reinputBtn.visibility = View.GONE
                binding.retryBtn.visibility = View.VISIBLE
            }
            else -> {
                binding.voiceFlowButtons.visibility = View.GONE
            }
        }
    }

    private fun onStartSearchFromVoiceFlow(productName: String) {
        val targetClass = mapSpokenToClass(productName)
        if (productName.isNotBlank() && targetClass.isBlank()) {
            voiceSearchTargetLabel = null
            val failReason = "인식된 말을 상품 목록에서 찾지 못했어요. '$productName'"
            binding.systemMessageText.text = "상품 매칭 실패: '$productName'(을)를 찾지 못했어요."
            speak(failReason) { speak(VoicePrompts.PROMPT_PRODUCT_RECOGNITION_FAILED) { } }
            return
        }
        cancelSearchTimeout()
        transitionToSearching()
        voiceSearchTargetLabel = targetClass
        currentTargetLabel.set(targetClass)
        binding.systemMessageText.text = "찾는 중: ${getTargetLabel()}"
        startCamera()
        startSearchTimeout()
        updateVoiceButtonVisibility()
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

    private fun setTargetFromSpokenProductName(productName: String) {
        val targetClass = mapSpokenToClass(productName)
        if (productName.isNotBlank() && targetClass.isBlank()) return
        currentTargetLabel.set(targetClass)
    }

    private fun isShortYesLike(text: String): Boolean {
        val t = text.trim().lowercase().replace(" ", "")
        if (t.length > 6) return false
        return t.contains("예") || t.contains("네") || t.contains("내") || t.contains("응") ||
            t.contains("맞") || t.contains("그래") || t.contains("좋아") || t == "yes" || t == "y"
    }

    private fun handleTouchConfirmYesNo(text: String) {
        val t = text.trim().lowercase().replace(" ", "")
        val isYes = t.contains("예") || t.contains("네") || t.contains("내") || t.contains("응") ||
            t.contains("맞") || t.contains("그래") || t.contains("좋아") || t == "yes" || t == "y"
        val isExplicitNo = t.contains("아니") || t.contains("아직") || t.contains("없어") || t == "no" || t == "n"
        when {
            isYes -> speak(VoicePrompts.PROMPT_DONE) { resetToIdleFromTouch() }
            isExplicitNo -> resetTouchConfirmAndRetrack()
            else -> speak(VoicePrompts.PROMPT_TOUCH_RESTART) { resetToIdleFromTouch() }
        }
    }

    /** '손 닿았나요?'에 부정 답변 시: TOUCH_CONFIRM 해제, 재추적로 돌아감 */
    private fun resetTouchConfirmAndRetrack() {
        requireActivity().runOnUiThread {
            sttManager?.cancelListening()
            touchConfirmInProgress = false
            touchConfirmScheduled = false
            waitingForTouchConfirm = false
            touchActive = false
            touchFrameCount = 0
            releaseFrameCount = 0
            speak("다시 위치를 확인합니다.") {
                requireActivity().runOnUiThread {
                    startPositionAnnounce()
                    binding.systemMessageText.text = "손을 뻗어 잡아주세요"
                }
            }
        }
    }

    private fun enterTouchConfirm() {
        if (touchConfirmInProgress) return
        touchConfirmInProgress = true
        touchConfirmScheduled = false
        waitingForTouchConfirm = true
        touchConfirmSttRetryCount = 0
        speak("상품에 닿았나요? 닿았으면 예라고 말해주세요.") {
            requireActivity().runOnUiThread { sttManager?.startListening() }
        }
    }

    private fun resetToIdleFromTouch() {
        requireActivity().runOnUiThread {
            touchConfirmInProgress = false
            touchConfirmScheduled = false
            waitingForTouchConfirm = false
            touchActive = false
            touchFrameCount = 0
            releaseFrameCount = 0
            transitionToSearching()
            stopCamera()
            currentTargetLabel.set("")
            updateSearchTargetLabel()
            updateVoiceButtonVisibility()
            speak(VoicePrompts.PROMPT_IDLE_TOUCH) {}
        }
    }

    private fun startSearchTimeout() {
        cancelSearchTimeout()
        searchTimeoutAborted = false
        searchTimeoutRunnable = Runnable {
            if (searchTimeoutAborted || searchState == SearchState.LOCKED) return@Runnable
            if (voiceFlowController?.currentState == VoiceFlowController.VoiceFlowState.SEARCHING_PRODUCT) {
                requireActivity().runOnUiThread {
                    if (searchTimeoutAborted || searchState == SearchState.LOCKED) return@runOnUiThread
                    voiceSearchTargetLabel = null
                    voiceFlowController?.onSearchComplete(false)
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

    private fun initGyroTrackingManager() {
        gyroManager = GyroTrackingManager(
            context = requireContext(),
            onBoxUpdate = { update ->
                requireActivity().runOnUiThread {
                    val box = OverlayView.DetectionBox(
                        label = lockedTargetLabel,
                        confidence = 0.9f,
                        rect = update.rect,
                        topLabels = listOf(lockedTargetLabel to 90),
                        rotationDegrees = update.rotationDegrees
                    )
                    frozenBox = box
                    _binding?.overlayView?.setDetections(listOf(box), frozenImageWidth, frozenImageHeight)
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
                .setResultListener { result, _ -> latestHandsResult.set(result) }
                .setErrorListener { e -> Log.e(TAG, "Hands LIVE_STREAM error", e) }
                .build()
            handLandmarker = HandLandmarker.createFromOptions(requireContext(), options)
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe Hands 초기화 실패", e)
        }
    }

    private fun getTargetLabel(): String = currentTargetLabel.get().trim()

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

    private fun hasSpecificTarget(): Boolean = getTargetLabel().isNotEmpty()

    private fun stopCamera() {
        stopScanning()
        try {
            ProcessCameraProvider.getInstance(requireContext()).get().unbindAll()
            camera = null
        } catch (_: Exception) {}
    }

    private fun setupPinchZoom() {
        val detector = ScaleGestureDetector(requireContext(), object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cam = camera ?: return false
                val newZoom = (currentZoomRatio * detector.scaleFactor).coerceIn(1f, 10f)
                currentZoomRatio = newZoom
                cam.cameraControl.setZoomRatio(newZoom)
                return true
            }
        })
        binding.previewView.setOnTouchListener { _, event ->
            detector.onTouchEvent(event)
            false
        }
    }

    private fun startScanning() {
        if (isScanning || searchState != SearchState.SEARCHING || !hasSpecificTarget() || camera == null) return
        isScanning = true
        isZoomedIn = false
        scanRunnable = object : Runnable {
            override fun run() {
                if (!isScanning || searchState != SearchState.SEARCHING) return
                val c = camera ?: run { isScanning = false; return }
                isZoomedIn = !isZoomedIn
                val ratio = if (isZoomedIn) 2.5f else 1.0f
                c.cameraControl.setZoomRatio(ratio)
                currentZoomRatio = ratio
                val delay = if (isZoomedIn) SCAN_ZOOM_IN_MS else SCAN_ZOOM_OUT_MS
                scanHandler.postDelayed(this, delay)
            }
        }
        scanHandler.postDelayed(scanRunnable!!, SCAN_ZOOM_OUT_MS)
    }

    private fun stopScanning() {
        isScanning = false
        scanHandler.removeCallbacksAndMessages(null)
        scanRunnable = null
    }

    /** Pinhole: Distance_mm = (FocalLength_px * RealObjectWidth_mm) / BoundingBoxWidth_px, FocalLength = imageHeight * 1.1 */
    private fun computeDistanceMm(boxWidthPx: Float, imageHeight: Int, label: String): Float {
        if (boxWidthPx <= 0f || imageHeight <= 0) return Float.MAX_VALUE
        val focalLengthPx = imageHeight * FOCAL_LENGTH_FACTOR
        val physicalWidthMm = ProductDictionary.getPhysicalWidthMm(label)
        return (focalLengthPx * physicalWidthMm) / boxWidthPx
    }

    /** 화면 구역: Left < 0.3, Right > 0.7, Safe 0.3..0.7 (centerXNorm = centerX / imageWidth) */
    private fun isInSafeZone(centerXNorm: Float): Boolean =
        centerXNorm in SAFE_ZONE_LEFT..SAFE_ZONE_RIGHT

    private fun setZoomTo1x() {
        val cam = camera ?: return
        if (kotlin.math.abs(currentZoomRatio - 1.0f) >= 0.01f) {
            currentZoomRatio = 1.0f
            try { cam.cameraControl.setZoomRatio(1.0f) } catch (_: Exception) {}
        }
    }

    private fun processAutoZoom(detections: List<OverlayView.DetectionBox>, imageWidth: Int, imageHeight: Int) {
        val target = getTargetLabel().trim()
        if (target.isBlank()) return
        val match = detections.filter { d ->
            (d.label.equals(target, true) || d.topLabels.any { it.first.equals(target, true) }) &&
                d.confidence >= TARGET_CONFIDENCE_THRESHOLD
        }.maxByOrNull { it.confidence } ?: return
        val boxCenterX = (match.rect.left + match.rect.right) / 2f
        val centerXNorm = if (imageWidth > 0) boxCenterX / imageWidth else 0.5f
        if (!isInSafeZone(centerXNorm)) {
            requireActivity().runOnUiThread {
                setZoomTo1x()
            }
            return
        }
        val boxWidthPx = match.rect.width()
        val distanceMm = computeDistanceMm(boxWidthPx, imageHeight, match.label)
        val cam = camera ?: return
        val shouldZoomOutFast = distanceMm < DISTANCE_ZOOM_OUT_MM
        val shouldZoomOutSlow = distanceMm in DISTANCE_ZOOM_OUT_MM..DISTANCE_ZOOM_IN_MM
        val shouldZoomIn = distanceMm >= DISTANCE_ZOOM_IN_MM
        val now = System.currentTimeMillis()
        requireActivity().runOnUiThread {
            when {
                shouldZoomOutFast -> {
                    stopScanning()
                    val speed = ZOOM_LERP_SPEED_OUT_FAST
                    val newZoom = (currentZoomRatio + (1.0f - currentZoomRatio) * speed).coerceIn(1.0f, MAX_ZOOM_SCAN)
                    if (kotlin.math.abs(newZoom - currentZoomRatio) >= 0.01f) {
                        currentZoomRatio = newZoom
                        try { cam.cameraControl.setZoomRatio(newZoom) } catch (_: Exception) {}
                    }
                }
                shouldZoomOutSlow && currentZoomRatio > 1.0f -> {
                    val newZoom = (currentZoomRatio + (1.0f - currentZoomRatio) * ZOOM_LERP_SPEED_OUT)
                        .coerceIn(1.0f, MAX_ZOOM_SCAN)
                    if (kotlin.math.abs(newZoom - currentZoomRatio) >= 0.01f) {
                        currentZoomRatio = newZoom
                        try { cam.cameraControl.setZoomRatio(newZoom) } catch (_: Exception) {}
                    }
                }
                shouldZoomIn -> {
                    val newZoom = min(MAX_ZOOM_SCAN, currentZoomRatio + ZOOM_LERP_SPEED_IN)
                    if (kotlin.math.abs(newZoom - currentZoomRatio) >= 0.01f) {
                        currentZoomRatio = newZoom
                        try { cam.cameraControl.setZoomRatio(newZoom) } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    /** 방향 정렬 우선: Left/Right Zone에서는 접근 안내·줌 금지. Safe Zone에서만 거리 안내. 쿨다운·안정화 적용. */
    private fun processDistanceGuidance(box: OverlayView.DetectionBox, imageWidth: Int, imageHeight: Int) {
        val cam = camera ?: return
        val totalArea = imageWidth * imageHeight
        if (totalArea <= 0 || imageWidth <= 0) return
        val boxCenterX = (box.rect.left + box.rect.right) / 2f
        val centerXNorm = boxCenterX / imageWidth
        val boxWidthPx = box.rect.width()
        val distanceMm = computeDistanceMm(boxWidthPx, imageHeight, box.label)
        val now = System.currentTimeMillis()
        requireActivity().runOnUiThread {
            when {
                centerXNorm < SAFE_ZONE_LEFT -> {
                    closeDistanceFrameCount = 0
                    setZoomTo1x()
                    if (now - lastDirectionGuidanceTime >= DIRECTION_GUIDANCE_COOLDOWN_MS) {
                        lastDirectionGuidanceTime = now
                        speak("왼쪽으로 조금 더 돌리세요", false)
                    }
                }
                centerXNorm > SAFE_ZONE_RIGHT -> {
                    closeDistanceFrameCount = 0
                    setZoomTo1x()
                    if (now - lastDirectionGuidanceTime >= DIRECTION_GUIDANCE_COOLDOWN_MS) {
                        lastDirectionGuidanceTime = now
                        speak("오른쪽으로 조금 더 돌리세요", false)
                    }
                }
                else -> {
                    val boxRatio = (box.rect.width() * box.rect.height()) / totalArea
                    val zoom = currentZoomRatio
                    val newZoom = max(1.0f, currentZoomRatio * 0.95f)
                    if (currentZoomRatio > 1.0f && now - lastZoomDecayTime >= ZOOM_DECAY_INTERVAL_MS) {
                        lastZoomDecayTime = now
                        cam.cameraControl.setZoomRatio(newZoom)
                        currentZoomRatio = newZoom
                    }
                    if (distanceMm <= DISTANCE_NEAR_MM) {
                        closeDistanceFrameCount++
                        if (closeDistanceFrameCount >= CLOSE_DISTANCE_STABILITY_FRAMES &&
                            now - lastActionGuidanceTime >= ACTION_GUIDANCE_COOLDOWN_MS) {
                            lastActionGuidanceTime = now
                            closeDistanceFrameCount = 0
                            speak("손을 뻗어 확인해보세요", false)
                        }
                    } else {
                        closeDistanceFrameCount = 0
                        if (now - lastDistanceGuidanceTime >= DISTANCE_GUIDANCE_COOLDOWN_MS &&
                            zoom <= 1.2f && boxRatio < TARGET_BOX_RATIO) {
                            lastDistanceGuidanceTime = now
                            speak("조금 더 앞으로 오세요", false)
                        }
                    }
                }
            }
        }
    }

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

    private fun filterDetectionsByTarget(detections: List<OverlayView.DetectionBox>, targetLabel: String): List<OverlayView.DetectionBox> {
        val target = targetLabel.trim()
        if (target.isBlank()) return emptyList()
        return detections.mapNotNull { box ->
            val targetConf = when {
                box.label.trim().equals(target, ignoreCase = true) -> box.confidence
                else -> box.topLabels.find { it.first.trim().equals(target, ignoreCase = true) }?.second?.div(100f)
            }
            if (targetConf != null && targetConf >= TARGET_CONFIDENCE_THRESHOLD) {
                if (box.label.trim().equals(target, ignoreCase = true)) box
                else box.copy(label = target, confidence = targetConf, topLabels = listOf(target to (targetConf * 100).toInt()))
            } else null
        }
    }

    private fun findTargetMatch(detections: List<OverlayView.DetectionBox>, targetLabel: String): Pair<OverlayView.DetectionBox, String>? {
        val target = targetLabel.trim()
        if (target.isBlank()) return null
        return detections.mapNotNull { box ->
            val targetConf = when {
                box.label.trim().equals(target, ignoreCase = true) -> box.confidence
                else -> box.topLabels.find { it.first.trim().equals(target, ignoreCase = true) }?.second?.div(100f)
            }
            if (targetConf != null && targetConf >= TARGET_CONFIDENCE_THRESHOLD) {
                val displayBox = if (box.label.trim().equals(target, ignoreCase = true)) box
                else box.copy(label = target, confidence = targetConf, topLabels = listOf(target to (targetConf * 100).toInt()))
                Pair(displayBox, box.label)
            } else null
        }.maxByOrNull { it.first.confidence }
    }

    private fun transitionToLocked(box: OverlayView.DetectionBox, imageWidth: Int, imageHeight: Int, actualPrimaryLabel: String? = null) {
        cancelSearchTimeout()
        requireActivity().runOnUiThread {
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
            closeDistanceFrameCount = 0
            lastTouchMidpointPx = null
            lastTouchTtsTimeMs = 0L
            latestHandsResult.set(null)
            binding.overlayView.setDetections(listOf(box), imageWidth, imageHeight)
            binding.overlayView.setFrozen(true)
            binding.systemMessageText.text = "객체 탐지됨. 손을 뻗어 잡아주세요."
            if (!ttsDetectedPlayed) {
                ttsDetectedPlayed = true
                speak("객체를 탐지했습니다. 손을 뻗어 잡아주세요.")
            }
            if (voiceFlowController?.currentState == VoiceFlowController.VoiceFlowState.SEARCHING_PRODUCT) {
                val requested = voiceSearchTargetLabel
                val actualLabel = actualPrimaryLabel ?: box.label
                val isRequestedProduct = requested.isNullOrBlank() || requested == actualLabel
                if (isRequestedProduct) {
                    voiceSearchTargetLabel = null
                    voiceFlowController?.onSearchComplete(true, actualLabel, box.rect, imageWidth, imageHeight)
                }
            }
            startPositionAnnounce()
            updateVoiceButtonVisibility()
        }
    }

    private fun transitionToSearching(skipTtsDetectedReset: Boolean = false) {
        requireActivity().runOnUiThread {
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
            lastDirectionGuidanceTime = 0L
            lastDistanceGuidanceTime = 0L
            lastActionGuidanceTime = 0L
            closeDistanceFrameCount = 0
            lastZoomDecayTime = 0L
            latestHandsResult.set(null)
            opticalFlowTracker.reset()
            gyroManager.stopTracking()
            binding.overlayView.setDetections(emptyList(), 0, 0)
            binding.overlayView.setFrozen(false)
            updateSearchTargetLabel()
            binding.systemMessageText.text = "찾는 중: ${getTargetLabel()}"
            stopHandGuidanceTTS()
            startScanning()
            updateVoiceButtonVisibility()
        }
    }

    private fun loadClassLabels() {
        try {
            requireContext().assets.open("classes.txt").use { stream ->
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

    private fun getClassLabel(classId: Int): String =
        classLabels.getOrNull(classId)?.takeIf { it.isNotBlank() } ?: "Object_$classId"

    private fun letterboxBitmap(bitmap: Bitmap, inputSize: Int): Pair<Bitmap, Float> =
        BitmapUtils.createLetterboxBitmap(bitmap, inputSize)

    private fun bitmapToFloatBuffer(bitmap: Bitmap, size: Int, isNchw: Boolean = false): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(4 * size * size * 3)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        if (isNchw) {
            for (c in 0 until 3) {
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
            val modelFile = loadModelFile("yolox_nano_49cls_float16.tflite")
            val options = Interpreter.Options()
            try {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
                options.setAllowFp16PrecisionForFp32(true)
                requireActivity().runOnUiThread { binding.systemMessageText.text = "YOLOX GPU 준비" }
            } catch (e: Exception) {
                Log.e(TAG, "GPU 실패 -> CPU 전환", e)
                options.setNumThreads(4)
                gpuDelegate = null
                requireActivity().runOnUiThread { binding.systemMessageText.text = "YOLOX CPU 준비" }
            }
            yoloxInterpreter = Interpreter(modelFile, options)
        } catch (e: Exception) {
            Log.e(TAG, "YOLOX 초기화 실패", e)
            requireActivity().runOnUiThread { binding.systemMessageText.text = "YOLOX 초기화 실패" }
        }
    }

    private fun loadModelFile(modelName: String): MappedByteBuffer {
        val fileDescriptor = requireContext().assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
    }

    private fun startCamera() {
        if (_binding == null) return
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1280, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder()
                .setResolutionSelector(resolutionSelector)
                .build()
                .also { it.setSurfaceProvider(binding.previewView.surfaceProvider) }

            val imageAnalyzer = ImageAnalysis.Builder()
                .setResolutionSelector(resolutionSelector)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(cameraExecutor, ImageAnalyzer()) }

            try {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                )
                currentZoomRatio = 1f
                camera?.cameraControl?.setZoomRatio(1.0f)
                scanHandler.postDelayed({ startScanning() }, SCAN_INITIAL_DELAY_MS)
            } catch (e: Exception) {
                Log.e(TAG, "카메라 바인딩 실패", e)
            }
        }, ContextCompat.getMainExecutor(requireContext()))
    }

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
        return if (count == 0) true else (sum / count) / 3 < threshold
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
            if (inputBuffer.remaining() != expectedBytes) return emptyList()
            val outputShape = yoloxInterpreter!!.getOutputTensor(0).shape()
            val output = Array(outputShape[0]) { Array(outputShape[1]) { FloatArray(outputShape[2]) } }
            yoloxInterpreter!!.run(inputBuffer, output)
            if (isImageTooDark(bitmap)) return emptyList()
            return parseYOLOXOutput(
                output[0], outputShape[1], outputShape[2],
                bitmap.width, bitmap.height, inputSize, letterboxScaleR = letterboxScale
            )
        } catch (e: Exception) {
            Log.e(TAG, "YOLOX 추론 실패", e)
            return emptyList()
        }
    }

    private fun parseYOLOXOutput(
        output: Array<FloatArray>,
        dim1: Int, dim2: Int,
        imageWidth: Int, imageHeight: Int,
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

        val hasObjectness = (boxSize == 85 || boxSize == 58 || boxSize == 55 || boxSize == 54)
        val scoreStart = if (hasObjectness) 5 else 4
        val classCount = (boxSize - scoreStart).coerceAtLeast(1)
        val useSingleScoreFormat = (numBoxes <= 100 && boxSize >= 5)
        val minConfidenceToShow = when {
            useSingleScoreFormat -> 0.35f
            hasObjectness -> 0.45f
            else -> 0.78f
        }

        val candidates = mutableListOf<OverlayView.DetectionBox>()
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
            val topClassIds = classScores.indices
                .map { c ->
                    var rs = classScores[c]
                    if (rs > 1f || rs < 0f) rs = 1f / (1f + exp(-rs))
                    c to (objScore * rs).coerceIn(0f, 1f)
                }
                .sortedByDescending { it.second }
                .take(3)
            val confidence = topClassIds.firstOrNull()?.second ?: 0f
            if (confidence < minConfidenceToShow) continue

            val topLabels = topClassIds
                .filter { (_, conf) -> conf >= 0.5f }
                .map { (cid, conf) -> getClassLabel(cid) to (conf * 100).toInt() }

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
                    rect = RectF(left, top, right, bottom),
                    topLabels = topLabels
                )
            )
        }
        return nms(candidates, 0.6f)
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

    private fun handLandmarksToRect(landmarks: List<NormalizedLandmark>, imageWidth: Int, imageHeight: Int): RectF {
        if (landmarks.isEmpty()) return RectF(0f, 0f, 0f, 0f)
        val xs = landmarks.map { it.x().coerceIn(0f, 1f) * imageWidth }
        val ys = landmarks.map { it.y().coerceIn(0f, 1f) * imageHeight }
        return RectF(xs.min(), ys.min(), xs.max(), ys.max())
    }

    private fun mergedHandRect(handsResult: HandLandmarkerResult?, imageWidth: Int, imageHeight: Int): RectF? {
        val landmarks = handsResult?.landmarks() ?: return null
        if (landmarks.isEmpty()) return null
        var union: RectF? = null
        for (hand in landmarks) {
            val r = handLandmarksToRect(hand, imageWidth, imageHeight)
            union = if (union == null) r else RectF(union).apply { union(r) }
        }
        return union
    }

    private fun isHandOverlappingBox(handsResult: HandLandmarkerResult?, boxRect: RectF, imageWidth: Int, imageHeight: Int): Boolean {
        val landmarks = handsResult?.landmarks() ?: return false
        if (imageWidth <= 0 || imageHeight <= 0) return false
        return landmarks.any { hand ->
            val handRect = handLandmarksToRect(hand, imageWidth, imageHeight)
            iou(handRect, boxRect) > 0.1f
        }
    }

    private fun isHandTouchingTargetBox(handsResult: HandLandmarkerResult?, targetBox: RectF, imageWidth: Int, imageHeight: Int): Boolean {
        val landmarks = handsResult?.landmarks() ?: run { lastTouchMidpointPx = null; return false }
        if (imageWidth <= 0 || imageHeight <= 0) { lastTouchMidpointPx = null; return false }
        val hand = landmarks.firstOrNull() ?: run { lastTouchMidpointPx = null; return false }
        if (hand.size < 9) { lastTouchMidpointPx = null; return false }
        val thumb = landmarkToPoint(hand, 4, imageWidth, imageHeight) ?: run { lastTouchMidpointPx = null; return false }
        val index = landmarkToPoint(hand, 8, imageWidth, imageHeight) ?: run { lastTouchMidpointPx = null; return false }
        val midX = (thumb.first + index.first) / 2f
        val midY = (thumb.second + index.second) / 2f
        lastTouchMidpointPx = Pair(midX, midY)
        val w = targetBox.width().coerceAtLeast(1f)
        val h = targetBox.height().coerceAtLeast(1f)
        val touchBox = RectF(
            targetBox.left - w * TOUCH_BOX_EXPAND_RATIO,
            targetBox.top - h * TOUCH_BOX_EXPAND_RATIO,
            targetBox.right + w * TOUCH_BOX_EXPAND_RATIO,
            targetBox.bottom + h * TOUCH_BOX_EXPAND_RATIO
        )
        return touchBox.contains(midX, midY)
    }

    private fun landmarkToPoint(hand: List<NormalizedLandmark>, index: Int, imageWidth: Int, imageHeight: Int): Pair<Float, Float>? {
        if (index < 0 || index >= hand.size) return null
        val lm = hand[index]
        return Pair(lm.x().coerceIn(0f, 1f) * imageWidth, lm.y().coerceIn(0f, 1f) * imageHeight)
    }

    private fun buildHandPositionGuidance(handsResult: HandLandmarkerResult?, boxRect: RectF, imageWidth: Int, imageHeight: Int): String? {
        val landmarks = handsResult?.landmarks() ?: return null
        if (landmarks.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return null
        val hand = landmarks.first()
        if (hand.size <= 0) return null
        val wrist = landmarkToPoint(hand, 0, imageWidth, imageHeight) ?: return null
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
                    speak(guidance, false)
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

    private fun sendFrameToHands(bitmap: Bitmap, timestampNs: Long) {
        handLandmarker ?: return
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            handLandmarker!!.detectAsync(mpImage, timestampNs / 1_000_000)
        } catch (e: Exception) {
            Log.e(TAG, "Hands detectAsync 실패", e)
        }
    }

    private fun iou(a: RectF, b: RectF): Float {
        val interLeft = max(a.left, b.left)
        val interTop = max(a.top, b.top)
        val interRight = min(a.right, b.right)
        val interBottom = min(a.bottom, b.bottom)
        val interW = max(0f, interRight - interLeft)
        val interH = max(0f, interBottom - interTop)
        val interArea = interW * interH
        return interArea / (a.width() * a.height() + b.width() * b.height() - interArea + 1e-6f)
    }

    private fun displayResults(detections: List<OverlayView.DetectionBox>, inferenceTime: Long, imageWidth: Int, imageHeight: Int) {
        requireActivity().runOnUiThread {
            _binding?.overlayView?.setDetections(detections, imageWidth, imageHeight)
            _binding?.overlayView?.setHands(if (searchState == SearchState.LOCKED) latestHandsResult.get() else null)
            if (searchState == SearchState.LOCKED && lastTouchMidpointPx != null) {
                _binding?.overlayView?.setTouchDebugPoint(lastTouchMidpointPx!!.first, lastTouchMidpointPx!!.second)
            } else {
                _binding?.overlayView?.setTouchDebugPoint(null, null)
            }
            when (searchState) {
                SearchState.IDLE -> binding.systemMessageText.text = ""
                SearchState.SEARCHING -> binding.systemMessageText.text = "찾는 중: ${getTargetLabel()}"
                SearchState.LOCKED -> {
                    if (frozenBox != null) {
                        binding.systemMessageText.text = when {
                            waitingForTouchConfirm && (sttManager?.isListening() == true) -> "듣는 중..."
                            waitingForTouchConfirm -> "상품에 닿았나요? 예라고 말해주세요."
                            touchActive -> "손이 제품에 닿았어요"
                            else -> "손을 뻗어 잡아주세요"
                        }
                    }
                }
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireContext(), it) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && allPermissionsGranted()) {
            startCamera()
            voiceFlowController?.start()
        } else if (requestCode == REQUEST_CODE_PERMISSIONS) {
            Toast.makeText(requireContext(), "카메라 및 마이크 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
        }
    }

    private inner class ImageAnalyzer : ImageAnalysis.Analyzer {
        @androidx.camera.core.ExperimentalGetImage
        override fun analyze(imageProxy: ImageProxy) {
            val startTime = System.currentTimeMillis()
            var bitmap = imageProxy.toBitmap()
            if (bitmap == null) {
                imageProxy.close()
                return
            }
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            if (rotationDegrees != 0) {
                bitmap = BitmapUtils.rotateBitmap(bitmap!!, rotationDegrees) ?: bitmap!!
            }
            val w = bitmap!!.width
            val h = bitmap.height
            if (!firstResolutionLogged) {
                firstResolutionLogged = true
                Log.d(TAG, "Camera resolution (ImageAnalysis): ${w}x${h}, rotation=$rotationDegrees")
            }
            var inferMs = System.currentTimeMillis() - startTime

            if (searchState == SearchState.LOCKED) {
                sendFrameToHands(bitmap, imageProxy.imageInfo.timestamp)
            }

            when (searchState) {
                SearchState.IDLE -> {
                    displayResults(emptyList(), inferMs, w, h)
                }
                SearchState.SEARCHING -> {
                    if (!hasSpecificTarget()) {
                        displayResults(emptyList(), inferMs, w, h)
                    } else {
                        val detections = runYOLOX(bitmap)
                        processAutoZoom(detections, w, h)
                        val matchResult = findTargetMatch(detections, getTargetLabel())
                        if (matchResult != null) {
                            val (matched, actualPrimaryLabel) = matchResult
                            if (matched.confidence >= TARGET_CONFIDENCE_THRESHOLD) {
                                val prev = pendingLockBox
                                if (prev != null && prev.label == matched.label && RectF.intersects(matched.rect, prev.rect)) {
                                    pendingLockCount++
                                    if (pendingLockCount >= LOCK_CONFIRM_FRAMES) {
                                        pendingLockBox = null
                                        pendingLockCount = 0
                                        transitionToLocked(matched, w, h, actualPrimaryLabel)
                                        frozenBox = matched
                                        frozenImageWidth = w
                                        frozenImageHeight = h
                                        wasOccluded = false
                                        opticalFlowTracker.reset()
                                        gyroManager.startTracking(matched.rect, w, h)
                                        lockedFrameCount = 0
                                        validationFailCount = 0
                                        imageProxy.close()
                                        return
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
                        displayResults(filterDetectionsByTarget(detections, getTargetLabel()), inferMs, w, h)
                    }
                }
                SearchState.LOCKED -> {
                    lockedFrameCount++
                    val nowMs = System.currentTimeMillis()
                    val boxLostDurationMs = nowMs - lastSuccessfulValidationTimeMs
                    val shouldReacquire = boxLostDurationMs >= REACQUIRE_INFERENCE_AFTER_MS
                    val shouldValidate = shouldReacquire || (lockedFrameCount % VALIDATION_INTERVAL) == 0

                    val handsOverlap = frozenBox?.let { isHandOverlappingBox(latestHandsResult.get(), it.rect, w, h) } ?: false
                    val handTouch = frozenBox?.let { isHandTouchingTargetBox(latestHandsResult.get(), it.rect, w, h) } ?: false

                    if (handTouch) {
                        touchFrameCount++
                        releaseFrameCount = 0
                        if (!touchActive && !touchConfirmInProgress && !touchConfirmScheduled && touchFrameCount >= TOUCH_CONFIRM_FRAMES) {
                            touchActive = true
                            val nowMs = System.currentTimeMillis()
                            if (nowMs - lastTouchTtsTimeMs >= TOUCH_TTS_COOLDOWN_MS) {
                                lastTouchTtsTimeMs = nowMs
                                touchConfirmScheduled = true
                                requireActivity().runOnUiThread {
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
                        } else {
                            touchFrameCount = 0
                        }
                    }

                    gyroManager.suspendUpdates = handsOverlap

                    if (handsOverlap) {
                        if (shouldReacquire) {
                            val detections = runYOLOX(bitmap)
                            inferMs = System.currentTimeMillis() - startTime
                            val tracked = findTrackedTarget(detections, lockedTargetLabel, frozenBox, 0.18f)
                            if (tracked != null) {
                                lastSuccessfulValidationTimeMs = System.currentTimeMillis()
                                gyroManager.correctPosition(tracked.rect)
                                frozenBox = tracked.copy(rotationDegrees = 0f)
                                frozenImageWidth = w
                                frozenImageHeight = h
                                if (!touchConfirmInProgress && !waitingForTouchConfirm) {
                                    touchActive = false
                                    touchFrameCount = 0
                                    releaseFrameCount = 0
                                    lastTouchTtsTimeMs = 0L
                                }
                            }
                        } else {
                            val handRect = mergedHandRect(latestHandsResult.get(), w, h)
                            val flow = opticalFlowTracker.update(bitmap, handRect, frozenBox?.rect)
                            flow?.let { (dx, dy) ->
                                val box = frozenBox ?: return@let
                                val r = box.rect
                                val newRect = RectF(r.left + dx, r.top + dy, r.right + dx, r.bottom + dy)
                                gyroManager.correctPosition(newRect)
                                frozenBox = box.copy(rect = newRect)
                            }
                        }
                    } else {
                        if (wasOccluded) {
                            frozenBox?.rect?.let { gyroManager.correctPosition(it) }
                        }
                        if (shouldValidate) {
                            val detections = runYOLOX(bitmap)
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
                                frozenBox = tracked.copy(rect = blendedRect, rotationDegrees = 0f)
                                frozenImageWidth = w
                                frozenImageHeight = h
                                if (!touchConfirmInProgress && !waitingForTouchConfirm) {
                                    touchActive = false
                                    touchFrameCount = 0
                                    releaseFrameCount = 0
                                    lastTouchTtsTimeMs = 0L
                                }
                            } else {
                                validationFailCount++
                                if (validationFailCount >= VALIDATION_FAIL_LIMIT) {
                                    validationFailCount = 0
                                    gyroManager.resetToSearchingFromUI()
                                }
                            }
                        }
                    }
                    wasOccluded = handsOverlap

                    frozenBox?.let { processDistanceGuidance(it, frozenImageWidth, frozenImageHeight) }
                    val boxes = if (frozenBox != null) listOf(frozenBox!!) else emptyList()
                    displayResults(boxes, inferMs, frozenImageWidth, frozenImageHeight)
                }
            }
            imageProxy.close()
        }

        @androidx.camera.core.ExperimentalGetImage
        private fun ImageProxy.toBitmap(): Bitmap? {
            val image = this.image ?: return null
            return BitmapUtils.yuv420ToBitmap(image)
        }
    }

    companion object {
        private const val TAG = "GrabIT_Home"
    }
}
