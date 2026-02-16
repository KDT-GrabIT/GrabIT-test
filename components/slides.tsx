"use client"

import { Eye, Hand, Volume2, Wifi, WifiOff, Cpu, Mic, Search, Database, Smartphone, Camera, Activity, AlertTriangle, CheckCircle, ArrowRight, Users, Code, Server, Brain, Zap, Layers } from "lucide-react"
import { TitleSlide, ContentSlide, SectionSlide, ThreeColumnCard, FlowChart, BulletList, ComparisonTable, StatCard } from "./slide-layouts"
import { CodeBlock, SplitCodeSlide } from "./code-block"

/* ─── Slide 1: Title ─── */
function Slide1() {
  return (
    <TitleSlide
      badge="KDT Final Project"
      title="GrabIT: Real-time Shopping Assistant"
      subtitle="On-Device AI for the visually impaired - guiding your hand to the product with sound and touch"
    >
      <div className="flex items-center gap-6 mt-4">
        <div className="flex items-center gap-2 px-4 py-2 rounded-lg bg-primary/10 text-primary">
          <Eye className="w-5 h-5" />
          <span className="text-sm font-medium">Vision AI</span>
        </div>
        <div className="flex items-center gap-2 px-4 py-2 rounded-lg bg-accent-orange/10 text-accent-orange">
          <Volume2 className="w-5 h-5" />
          <span className="text-sm font-medium">Audio Guide</span>
        </div>
        <div className="flex items-center gap-2 px-4 py-2 rounded-lg bg-accent-green/10 text-accent-green">
          <Smartphone className="w-5 h-5" />
          <span className="text-sm font-medium">On-Device</span>
        </div>
      </div>
    </TitleSlide>
  )
}

/* ─── Slide 2: UX Video Placeholder ─── */
function Slide2() {
  return (
    <ContentSlide title="User Experience Demo" subtitle="1-minute video: a blindfolded user finding a drink in a convenience store">
      <div className="flex items-center justify-center h-full">
        <div className="w-full max-w-3xl aspect-video bg-code-bg rounded-xl flex flex-col items-center justify-center gap-4 border border-border">
          <div className="w-16 h-16 rounded-full bg-accent-orange/20 flex items-center justify-center">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" className="text-accent-orange ml-1">
              <path d="M5 3l14 9-14 9V3z" fill="currentColor" />
            </svg>
          </div>
          <p className="text-muted-foreground text-sm">Video Placeholder - UX Demo</p>
          <p className="text-muted-foreground/60 text-xs">Tap to play during presentation</p>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 3: Background & Problem ─── */
function Slide3() {
  return (
    <ContentSlide title="Background & Problem" subtitle="Why existing solutions fall short for grocery shopping">
      <div className="grid grid-cols-2 gap-8 h-full items-center">
        <div className="flex flex-col gap-4">
          <div className="bg-card rounded-xl p-6 border border-border border-l-4 border-l-accent-orange">
            <h3 className="text-lg font-semibold text-foreground mb-2">The Gap in Accessibility</h3>
            <p className="text-sm text-muted-foreground leading-relaxed">
              Existing OCR apps like Sullivan+ can read text, but they cannot guide the user&apos;s hand to the physical location of a product on a shelf.
            </p>
          </div>
          <div className="bg-card rounded-xl p-6 border border-border">
            <h3 className="text-lg font-semibold text-foreground mb-2">Real-world Impact</h3>
            <p className="text-sm text-muted-foreground leading-relaxed">
              252 million visually impaired people worldwide need active guidance, not just passive text reading, to shop independently.
            </p>
          </div>
        </div>
        <div className="flex flex-col gap-4">
          <div className="bg-accent-orange/5 rounded-xl p-6 border border-accent-orange/20">
            <p className="text-xl font-bold text-accent-orange mb-2">{'"'}I know what it says, but where is it?{'"'}</p>
            <p className="text-sm text-muted-foreground">
              The core problem: detection without localization is incomplete assistance.
            </p>
          </div>
          <div className="flex gap-4">
            <StatCard value="252M" label="Visually Impaired (WHO)" color="primary" />
            <StatCard value="0" label="Apps with Hand Guidance" color="orange" />
          </div>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 4: Core Solution ─── */
function Slide4() {
  return (
    <ContentSlide title="Core Solution: 3 Pillars" subtitle="How GrabIT bridges the gap between detection and physical guidance">
      <ThreeColumnCard
        items={[
          {
            icon: <Brain className="w-10 h-10 text-primary" />,
            title: "Vision AI",
            description: "YOLOX-Nano runs on-device to detect 49 product classes in real-time. MediaPipe tracks the user's hand position simultaneously.",
            color: "blue",
          },
          {
            icon: <Volume2 className="w-10 h-10 text-accent-orange" />,
            title: "Audio Guide",
            description: "Sine-wave beeps (440-880Hz) guide direction. TTS announces product name, distance, and positional cues like 'left' or 'right'.",
            color: "orange",
          },
          {
            icon: <WifiOff className="w-10 h-10 text-accent-green" />,
            title: "On-Device Processing",
            description: "Zero network latency. All AI inference runs locally via TFLite/LiteRT. Server is used only for synonym search during product lookup.",
            color: "green",
          },
        ]}
      />
    </ContentSlide>
  )
}

/* ─── Slide 5: Table of Contents ─── */
function Slide5() {
  const parts = [
    { num: "01", title: "Team", color: "bg-primary" },
    { num: "02", title: "Architecture", color: "bg-primary" },
    { num: "03", title: "Scenario & Demo", color: "bg-accent-orange" },
    { num: "04", title: "Tech Deep Dive", color: "bg-accent-orange" },
    { num: "05", title: "Troubleshooting", color: "bg-accent-green" },
    { num: "06", title: "Limitations", color: "bg-accent-green" },
    { num: "07", title: "Conclusion & Roadmap", color: "bg-primary" },
  ]
  return (
    <ContentSlide title="Table of Contents">
      <div className="grid grid-cols-2 gap-4 max-w-3xl mx-auto mt-4">
        {parts.map((p, i) => (
          <div key={i} className="flex items-center gap-4 p-4 bg-card rounded-lg border border-border slide-fade-in" style={{ animationDelay: `${(i + 1) * 0.08}s`, opacity: 0 }}>
            <span className={`${p.color} text-primary-foreground text-sm font-bold w-10 h-10 rounded-lg flex items-center justify-center`}>
              {p.num}
            </span>
            <span className="text-lg font-medium text-foreground">{p.title}</span>
          </div>
        ))}
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 6: Team ─── */
function Slide6() {
  const members = [
    { role: "PM / Android Core", name: "Member 1", skills: ["Kotlin", "CameraX", "TFLite", "Coroutines"] },
    { role: "AI / Backend", name: "Member 2", skills: ["YOLOX", "Python", "Node.js", "MongoDB"] },
    { role: "UI / VUI Design", name: "Member 3", skills: ["Figma", "TTS/STT", "Accessibility", "UX Research"] },
  ]
  return (
    <ContentSlide title="Team" subtitle="3 members, complementary expertise">
      <div className="grid grid-cols-3 gap-6 mt-4">
        {members.map((m, i) => (
          <div key={i} className="bg-card rounded-xl p-6 border border-border flex flex-col items-center text-center slide-fade-in" style={{ animationDelay: `${(i + 1) * 0.15}s`, opacity: 0 }}>
            <div className="w-16 h-16 rounded-full bg-primary/10 flex items-center justify-center mb-4">
              <Users className="w-8 h-8 text-primary" />
            </div>
            <h3 className="text-lg font-semibold text-foreground">{m.name}</h3>
            <p className="text-sm text-accent-orange font-medium mb-3">{m.role}</p>
            <div className="flex flex-wrap gap-1.5 justify-center">
              {m.skills.map((s, j) => (
                <span key={j} className="px-2 py-0.5 rounded-full text-xs bg-muted text-muted-foreground">{s}</span>
              ))}
            </div>
          </div>
        ))}
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 7: System Architecture ─── */
function Slide7() {
  return (
    <ContentSlide title="System Architecture" subtitle="On-device AI pipeline with minimal server dependency">
      <div className="flex flex-col items-center gap-6 mt-2">
        <div className="flex items-center gap-3 flex-wrap justify-center">
          <div className="bg-primary text-primary-foreground px-5 py-3 rounded-lg text-sm font-semibold flex items-center gap-2">
            <Camera className="w-4 h-4" /> CameraX Input
          </div>
          <ArrowRight className="w-5 h-5 text-muted-foreground" />
          <div className="bg-accent-orange text-primary-foreground px-5 py-3 rounded-lg text-sm font-semibold flex items-center gap-2">
            <Brain className="w-4 h-4" /> YOLOX + MediaPipe
          </div>
          <ArrowRight className="w-5 h-5 text-muted-foreground" />
          <div className="bg-accent-green text-primary-foreground px-5 py-3 rounded-lg text-sm font-semibold flex items-center gap-2">
            <Volume2 className="w-4 h-4" /> AudioTrack Output
          </div>
        </div>
        <div className="text-xs text-muted-foreground font-mono tracking-wide">ALL ON-DEVICE (ZERO LATENCY)</div>
        <div className="w-px h-8 bg-border" />
        <div className="flex items-center gap-3">
          <div className="bg-muted text-foreground px-5 py-3 rounded-lg text-sm font-semibold flex items-center gap-2 border border-border">
            <Server className="w-4 h-4" /> Synonym API
          </div>
          <span className="text-xs text-muted-foreground">(Node.js + MongoDB, search only)</span>
        </div>
        <div className="grid grid-cols-3 gap-4 mt-4 w-full max-w-3xl">
          <div className="bg-card rounded-lg p-4 border border-border">
            <h4 className="text-sm font-semibold text-primary mb-2">Android</h4>
            <p className="text-xs text-muted-foreground">Kotlin, Coroutines, Room DB, CameraX</p>
          </div>
          <div className="bg-card rounded-lg p-4 border border-border">
            <h4 className="text-sm font-semibold text-accent-orange mb-2">AI</h4>
            <p className="text-xs text-muted-foreground">YOLOX-Nano (TFLite), MediaPipe Hands, OpenCV</p>
          </div>
          <div className="bg-card rounded-lg p-4 border border-border">
            <h4 className="text-sm font-semibold text-accent-green mb-2">Server</h4>
            <p className="text-xs text-muted-foreground">Node.js, MongoDB, Python (e5-small embedding)</p>
          </div>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 8: Tech Stack ─── */
function Slide8() {
  return (
    <ContentSlide title="Tech Stack" subtitle="Optimized for on-device performance and accessibility">
      <div className="grid grid-cols-3 gap-6 mt-2">
        <div className="bg-card rounded-xl p-6 border border-border border-t-4 border-t-primary">
          <div className="flex items-center gap-2 mb-4">
            <Smartphone className="w-6 h-6 text-primary" />
            <h3 className="text-lg font-semibold">Android</h3>
          </div>
          <div className="flex flex-col gap-2">
            {["Kotlin 1.9+", "Coroutines (Dispatchers.Default)", "Room DB + FTS4", "CameraX (ImageAnalysis)", "MediaPipe Tasks Vision"].map((t, i) => (
              <div key={i} className="flex items-center gap-2">
                <CheckCircle className="w-3.5 h-3.5 text-primary shrink-0" />
                <span className="text-sm text-foreground">{t}</span>
              </div>
            ))}
          </div>
        </div>
        <div className="bg-card rounded-xl p-6 border border-border border-t-4 border-t-accent-orange">
          <div className="flex items-center gap-2 mb-4">
            <Brain className="w-6 h-6 text-accent-orange" />
            <h3 className="text-lg font-semibold">AI / ML</h3>
          </div>
          <div className="flex flex-col gap-2">
            {["YOLOX-Nano (FP16 TFLite)", "MediaPipe Hand Landmarker", "OpenCV Lucas-Kanade", "Roboflow + MobileSAM3", "49 Product Classes"].map((t, i) => (
              <div key={i} className="flex items-center gap-2">
                <CheckCircle className="w-3.5 h-3.5 text-accent-orange shrink-0" />
                <span className="text-sm text-foreground">{t}</span>
              </div>
            ))}
          </div>
        </div>
        <div className="bg-card rounded-xl p-6 border border-border border-t-4 border-t-accent-green">
          <div className="flex items-center gap-2 mb-4">
            <Server className="w-6 h-6 text-accent-green" />
            <h3 className="text-lg font-semibold">Server</h3>
          </div>
          <div className="flex flex-col gap-2">
            {["Node.js + Express", "MongoDB Atlas", "Python (e5-small embedding)", "Vector Search (Synonyms)", "REST API (No Auth)"].map((t, i) => (
              <div key={i} className="flex items-center gap-2">
                <CheckCircle className="w-3.5 h-3.5 text-accent-green shrink-0" />
                <span className="text-sm text-foreground">{t}</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 9: User Journey ─── */
function Slide9() {
  return (
    <ContentSlide title="User Journey" subtitle="From wake-up to product in hand">
      <div className="flex flex-col items-center gap-8 mt-4">
        <FlowChart
          steps={[
            { label: "Wake Up", sub: "Touch / Vol Up", color: "blue" },
            { label: "Voice Input", sub: "STT: 'Coke'", color: "blue" },
            { label: "Confirm", sub: "TTS + STT", color: "orange" },
            { label: "Search", sub: "YOLOX Scan", color: "orange" },
            { label: "Guide", sub: "Beep + TTS", color: "green" },
            { label: "Grasp", sub: "Hand Detection", color: "green" },
          ]}
        />
        <div className="grid grid-cols-3 gap-6 w-full max-w-3xl mt-4">
          <div className="bg-card rounded-lg p-4 border border-border text-center">
            <div className="text-2xl font-bold text-primary">Step 1-2</div>
            <div className="text-xs text-muted-foreground mt-1">Voice Interaction</div>
            <div className="text-xs text-muted-foreground">VoiceFlowController State Machine</div>
          </div>
          <div className="bg-card rounded-lg p-4 border border-border text-center">
            <div className="text-2xl font-bold text-accent-orange">Step 3-4</div>
            <div className="text-xs text-muted-foreground mt-1">AI Detection</div>
            <div className="text-xs text-muted-foreground">YOLOX + Gyro Tracking</div>
          </div>
          <div className="bg-card rounded-lg p-4 border border-border text-center">
            <div className="text-2xl font-bold text-accent-green">Step 5-6</div>
            <div className="text-xs text-muted-foreground mt-1">Physical Guidance</div>
            <div className="text-xs text-muted-foreground">Beep + MediaPipe Hands</div>
          </div>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 10: Demo Video ─── */
function Slide10() {
  return (
    <ContentSlide title="Demo Video" subtitle="Watch GrabIT in action">
      <div className="flex items-center justify-center h-full">
        <div className="w-full max-w-3xl aspect-video bg-code-bg rounded-xl flex flex-col items-center justify-center gap-4 border border-border">
          <div className="w-16 h-16 rounded-full bg-primary/20 flex items-center justify-center">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" className="text-primary ml-1">
              <path d="M5 3l14 9-14 9V3z" fill="currentColor" />
            </svg>
          </div>
          <p className="text-muted-foreground text-sm">Demo Video Placeholder</p>
          <p className="text-muted-foreground/60 text-xs">Full walkthrough of product detection and hand guidance</p>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 11: Live Demo ─── */
function Slide11() {
  return (
    <ContentSlide title="Live Demo" subtitle="Phone mirroring and QR code for audience testing">
      <div className="flex items-center justify-center h-full gap-8">
        <div className="bg-card rounded-xl p-8 border border-border flex flex-col items-center gap-4 w-64">
          <Smartphone className="w-16 h-16 text-primary" />
          <h3 className="text-lg font-semibold text-foreground">Phone Mirroring</h3>
          <p className="text-sm text-muted-foreground text-center">scrcpy or Vysor connected to projector</p>
        </div>
        <div className="bg-card rounded-xl p-8 border border-border flex flex-col items-center gap-4 w-64">
          <div className="w-32 h-32 bg-muted rounded-lg flex items-center justify-center border border-border">
            <span className="text-muted-foreground text-xs">QR Code</span>
          </div>
          <h3 className="text-lg font-semibold text-foreground">Download APK</h3>
          <p className="text-sm text-muted-foreground text-center">Scan to install GrabIT on your device</p>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 12: VUI & Haptics ─── */
function Slide12() {
  const code = `enum class VoiceFlowState {
  APP_START,
  WAITING_PRODUCT_NAME,
  CONFIRM_PRODUCT,
  WAITING_CONFIRMATION,
  SEARCHING_PRODUCT,
  SEARCH_RESULT,
  SEARCH_FAILED
}

// State transition
private fun transitionTo(state: VoiceFlowState) {
  currentState = state
  isNextStepVoiceInput = when (state) {
    WAITING_PRODUCT_NAME, CONFIRM_PRODUCT -> true
    else -> false
  }
  onStateChanged(state, stateLabel)
}`
  return (
    <SplitCodeSlide
      title="VUI & Haptic Feedback"
      subtitle="State Machine drives the entire voice interaction"
      code={code}
      filename="VoiceFlowController.kt"
      language="Kotlin"
      accentColor="accent-orange"
      bullets={[
        "7-state FSM: APP_START -> WAITING_PRODUCT_NAME -> CONFIRM -> SEARCHING -> RESULT/FAILED",
        "TTS speaks, then STT listens - sequential, no overlap",
        "Volume Up long-press: skip to voice input anywhere",
        "Volume Down long-press: skip current TTS and start STT immediately",
        "4-second 'breathing room' after STT ends to suppress auto-guidance TTS",
      ]}
    />
  )
}

/* ─── Slide 13: BeepPlayer ─── */
function Slide13() {
  const code = `class BeepPlayer {
  private const val SAMPLE_RATE = 44100
  private const val BEEP_FREQ_HZ = 880
  private const val AMPLITUDE = 0.35

  fun init(): Boolean {
    val numSamples = (SAMPLE_RATE * BEEP_DURATION_MS / 1000.0).toInt()
    val buffer = ShortArray(numSamples)
    val angularFreq = 2.0 * PI * BEEP_FREQ_HZ / SAMPLE_RATE

    for (i in 0 until numSamples) {
      buffer[i] = (AMPLITUDE * sin(angularFreq * i)
                   * Short.MAX_VALUE).toInt().toShort()
    }

    audioTrack = AudioTrack.Builder()
      .setAudioFormat(AudioFormat.Builder()
        .setEncoding(ENCODING_PCM_16BIT)
        .setSampleRate(SAMPLE_RATE)
        .setChannelMask(CHANNEL_OUT_MONO).build())
      .setTransferMode(AudioTrack.MODE_STATIC)
      .build()
    audioTrack?.write(buffer, 0, buffer.size)
  }
}`
  return (
    <SplitCodeSlide
      title="BeepPlayer: Sine Wave Generation"
      subtitle="Zero-latency audio feedback using AudioTrack"
      code={code}
      filename="BeepPlayer.kt"
      language="Kotlin"
      accentColor="accent-green"
      bullets={[
        "Generates raw PCM 16-bit sine wave at 880Hz (configurable 440-880Hz for distance feedback)",
        "MODE_STATIC: entire waveform pre-loaded into buffer for instant playback",
        "No MediaPlayer overhead - direct AudioTrack API for < 5ms latency",
        "reloadStaticData() allows repeated playback without re-allocation",
        "Handler.postDelayed ensures clean stop + callback sequencing",
      ]}
    />
  )
}

/* ─── Slide 14: YOLOX Object Detection ─── */
function Slide14() {
  return (
    <ContentSlide title="YOLOX Object Detection" subtitle="Nano model optimized for mobile inference">
      <div className="grid grid-cols-2 gap-6 h-full">
        <div className="flex flex-col gap-4">
          <div className="bg-card rounded-xl p-5 border border-border">
            <h3 className="text-lg font-semibold text-foreground mb-3 flex items-center gap-2">
              <Brain className="w-5 h-5 text-accent-orange" /> Model Pipeline
            </h3>
            <div className="flex flex-col gap-2">
              <FlowChart steps={[
                { label: "Collect", sub: "5000+ images", color: "muted" },
                { label: "Label", sub: "Roboflow", color: "muted" },
                { label: "Auto-label", sub: "MobileSAM3", color: "orange" },
                { label: "Train", sub: "YOLOX-Nano", color: "blue" },
              ]} />
            </div>
          </div>
          <div className="bg-card rounded-xl p-5 border border-border">
            <h3 className="text-lg font-semibold text-foreground mb-3">Training Details</h3>
            <BulletList items={[
              "YOLOX-Nano backbone (lightweight, mobile-first)",
              "Float16 quantization for TFLite deployment",
              "49 product classes (drinks, snacks, daily necessities)",
              "Roboflow for annotation + augmentation pipeline",
              "MobileSAM3 for semi-automatic mask generation",
            ]} color="accent-orange" />
          </div>
        </div>
        <div className="flex flex-col gap-4">
          <div className="grid grid-cols-2 gap-4">
            <StatCard value="49" label="Product Classes" color="orange" />
            <StatCard value="FP16" label="Quantization" color="primary" />
            <StatCard value="16MB" label="Model Size" color="green" />
            <StatCard value="~30ms" label="Inference Time" color="orange" />
          </div>
          <div className="bg-accent-orange/5 rounded-xl p-5 border border-accent-orange/20 flex-1">
            <h3 className="text-lg font-semibold text-accent-orange mb-2">Why YOLOX-Nano?</h3>
            <p className="text-sm text-muted-foreground leading-relaxed">
              Anchor-free design eliminates hyperparameter tuning. Decoupled head improves small object detection. Nano variant achieves real-time inference on mobile GPUs with minimal accuracy trade-off.
            </p>
          </div>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 15: LiteRT & Optimization ─── */
function Slide15() {
  return (
    <ContentSlide title="LiteRT & Model Optimization" subtitle="From PyTorch to on-device TFLite deployment">
      <div className="flex flex-col gap-6 mt-2">
        <div className="flex items-center justify-center gap-4">
          <div className="bg-card px-6 py-4 rounded-lg border border-border text-center">
            <div className="text-sm font-mono text-muted-foreground">.pth</div>
            <div className="text-2xl font-bold text-foreground">200MB</div>
            <div className="text-xs text-muted-foreground">PyTorch</div>
          </div>
          <div className="flex flex-col items-center gap-1">
            <ArrowRight className="w-6 h-6 text-accent-orange" />
            <span className="text-xs text-accent-orange font-mono">ONNX</span>
          </div>
          <div className="bg-card px-6 py-4 rounded-lg border border-border text-center">
            <div className="text-sm font-mono text-muted-foreground">.onnx</div>
            <div className="text-2xl font-bold text-foreground">~100MB</div>
            <div className="text-xs text-muted-foreground">Intermediate</div>
          </div>
          <div className="flex flex-col items-center gap-1">
            <ArrowRight className="w-6 h-6 text-accent-orange" />
            <span className="text-xs text-accent-orange font-mono">FP16</span>
          </div>
          <div className="bg-accent-green/10 px-6 py-4 rounded-lg border border-accent-green/30 text-center">
            <div className="text-sm font-mono text-accent-green">.tflite</div>
            <div className="text-2xl font-bold text-accent-green">16MB</div>
            <div className="text-xs text-muted-foreground">On-Device</div>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-6">
          <div className="bg-card rounded-xl p-5 border border-border">
            <h3 className="text-lg font-semibold text-foreground mb-3">FP16 Quantization</h3>
            <BulletList items={[
              "Reduces model size by ~12x (200MB -> 16MB)",
              "Minimal accuracy loss (< 1% mAP drop)",
              "GPU delegate compatible for hardware acceleration",
              "FlatBuffers serialization for zero-copy loading",
            ]} color="accent-green" />
          </div>
          <div className="bg-card rounded-xl p-5 border border-border">
            <h3 className="text-lg font-semibold text-foreground mb-3">LiteRT Runtime</h3>
            <BulletList items={[
              "Upgraded from TensorFlow Lite to LiteRT (Google's new brand)",
              "GPU Delegate for parallel inference on mobile GPU",
              "Interpreter API with ByteBuffer input/output",
              "Coroutines integration: inference on Dispatchers.Default",
            ]} color="primary" />
          </div>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 16: CameraX & MediaPipe ─── */
function Slide16() {
  return (
    <ContentSlide title="CameraX + MediaPipe Pipeline" subtitle="Dual AI model: YOLOX detects products, MediaPipe detects hands">
      <div className="flex flex-col items-center gap-6 mt-2">
        <div className="flex items-center gap-3 flex-wrap justify-center">
          <div className="bg-primary/10 text-primary px-4 py-3 rounded-lg text-sm font-semibold flex items-center gap-2">
            <Camera className="w-4 h-4" /> CameraX Frame
          </div>
          <ArrowRight className="w-5 h-5 text-muted-foreground" />
          <div className="flex flex-col gap-2">
            <div className="bg-accent-orange/10 text-accent-orange px-4 py-2 rounded-lg text-xs font-semibold">YOLOX: Product BBox</div>
            <div className="bg-accent-green/10 text-accent-green px-4 py-2 rounded-lg text-xs font-semibold">MediaPipe: Hand Landmarks</div>
          </div>
          <ArrowRight className="w-5 h-5 text-muted-foreground" />
          <div className="bg-primary text-primary-foreground px-4 py-3 rounded-lg text-sm font-semibold">
            Coordinate Mapping
          </div>
          <ArrowRight className="w-5 h-5 text-muted-foreground" />
          <div className="bg-accent-green text-primary-foreground px-4 py-3 rounded-lg text-sm font-semibold flex items-center gap-2">
            <Hand className="w-4 h-4" /> Touch Detection
          </div>
        </div>
        <div className="grid grid-cols-2 gap-6 w-full mt-2">
          <div className="bg-card rounded-xl p-5 border border-border">
            <h3 className="text-lg font-semibold text-foreground mb-3">YOLOX Inference Path</h3>
            <BulletList items={[
              "ImageProxy -> RGBA Bitmap -> resize to 416x416",
              "ByteBuffer normalization (0-1 range)",
              "TFLite Interpreter.run() on background thread",
              "NMS + confidence filtering (threshold: 0.5)",
              "Output: classId, confidence, bounding box (x,y,w,h)",
            ]} color="accent-orange" />
          </div>
          <div className="bg-card rounded-xl p-5 border border-border">
            <h3 className="text-lg font-semibold text-foreground mb-3">MediaPipe Hand Path</h3>
            <BulletList items={[
              "CameraX frame -> BitmapImageBuilder -> MPImage",
              "HandLandmarker.detect() returns 21 landmarks per hand",
              "Extract fingertip (INDEX_FINGER_TIP, landmark #8)",
              "Map normalized coords to image pixel space",
              "Overlap check: fingertip inside expanded product BBox",
            ]} color="accent-green" />
          </div>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 17: Gyro Tracking ─── */
function Slide17() {
  const code = `// GyroTrackingManager.kt - Core Logic
private fun processRotationVector(event: SensorEvent) {
  SensorManager.getRotationMatrixFromVector(
    currentRotationMatrix, rv
  )
  SensorManager.getOrientation(adjustedMatrix, orientation)

  var deltaYaw   = orientation[0] - initialOrientation[0]
  var deltaPitch  = orientation[1] - initialOrientation[1]

  // Camera rotates right -> object moves LEFT on screen
  val rotationShiftX = -deltaYaw * pixelsPerRadianX
                       * SENSITIVITY_FACTOR
  val rotationShiftY = deltaPitch * pixelsPerRadianY
                       * SENSITIVITY_FACTOR

  // Smooth with exponential moving average
  val newLeft = currentSmoothedRect.left +
    (targetX - currentSmoothedRect.left) * SMOOTHING_ALPHA
}`
  return (
    <SplitCodeSlide
      title="Gyro Stabilization"
      subtitle="Lock bounding box in world-space using rotation sensors"
      code={code}
      filename="GyroTrackingManager.kt"
      language="Kotlin"
      accentColor="primary"
      bullets={[
        "Rotation Vector sensor -> delta yaw/pitch from initial orientation",
        "Reverse camera motion: camera pans right = box shifts left",
        "Pixels-per-radian conversion using known FOV (79deg horizontal)",
        "Exponential smoothing (alpha=0.25) to suppress jitter",
        "500ms warmup period to stabilize sensor readings",
        "Out-of-bounds detection: 15 consecutive frames before tracking loss",
      ]}
    />
  )
}

/* ─── Slide 18: Occlusion Handling ─── */
function Slide18() {
  const code = `// OpticalFlowTracker.kt
fun update(
  bitmap: Bitmap,
  excludeHandRect: RectF?,
  excludeBoxRect: RectF?
): Pair<Float, Float>? {
  // Downscale to 320x240 for speed
  val smallBitmap = Bitmap.createScaledBitmap(
    bitmap, processWidth, processHeight, true
  )
  // Mask out hand & target regions
  val mask = createExcludeMask(pw, ph,
    handRect, boxRect, scaleX, scaleY)

  // Lucas-Kanade Optical Flow
  Video.calcOpticalFlowPyrLK(
    prevGray, gray, prevPts, nextPts, status, err
  )

  // IQR outlier removal + median flow
  val (dxList, dyList) = filterOutliersIqr(validDx, validDy)
  return Pair(median(dxList)*scaleX, median(dyList)*scaleY)
}`
  return (
    <SplitCodeSlide
      title="Occlusion Handling (Optical Flow)"
      subtitle="When the hand covers the product, track background motion instead"
      code={code}
      filename="OpticalFlowTracker.kt"
      language="Kotlin"
      accentColor="accent-orange"
      bullets={[
        "Triggered when hand overlaps product bounding box (occlusion event)",
        "Masks out hand + target regions to track only background features",
        "goodFeaturesToTrack: 80 corner points with quality threshold 0.1",
        "Lucas-Kanade pyramid: tracks feature displacement frame-to-frame",
        "IQR-based outlier removal (k=1.5) for robust motion estimation",
        "Median dx/dy applied to shift bounding box, maintaining lock",
      ]}
    />
  )
}

/* ─── Slide 19: Coroutines ─── */
function Slide19() {
  const code = `// HomeFragment.kt - Off-main-thread inference
cameraExecutor = Executors.newSingleThreadExecutor()

// CameraX ImageAnalysis on background executor
ImageAnalysis.Builder()
  .setResolutionSelector(resolutionSelector)
  .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
  .build().also { analysis ->
    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
      // YOLOX inference (heavy computation)
      val results = runYoloxInference(imageProxy)

      // Switch to Main for UI update
      withContext(Dispatchers.Main) {
        overlayView.setDetections(results, w, h)
      }
    }
  }

// Room DB query (IO thread)
lifecycleScope.launch(Dispatchers.IO) {
  val repo = SearchHistoryRepository(db)
  repo.insert(SearchHistoryItem(...))
}`
  return (
    <SplitCodeSlide
      title="Coroutines & Async Architecture"
      subtitle="Keeping the UI thread free while running dual AI models"
      code={code}
      filename="HomeFragment.kt"
      language="Kotlin"
      accentColor="accent-green"
      bullets={[
        "SingleThreadExecutor for CameraX ImageAnalysis - serial frame processing",
        "STRATEGY_KEEP_ONLY_LATEST: drop frames if inference is slow, no backpressure",
        "Dispatchers.Default for CPU-intensive YOLOX inference",
        "Dispatchers.IO for Room database and network calls",
        "Dispatchers.Main for UI updates (overlay, buttons, TTS triggers)",
        "lifecycleScope ensures coroutines are cancelled on Fragment destroy",
      ]}
    />
  )
}

/* ─── Slide 20: Synonym API ─── */
function Slide20() {
  const code = `// SynonymApi.kt (Retrofit)
interface SynonymApi {
  @GET("synonyms/answers")
  suspend fun getAnswerProximityWords()
    : AnswerProximityResponse

  @GET("synonyms/products")
  suspend fun getProductProximityWords()
    : ProductProximityResponse
}

// Server: Node.js + MongoDB + e5-small
// User says "Coke" -> vector search finds
// closest match: "coca_cola" (classId)
//
// Flow:
// STT("콜라") -> SynonymRepository
//   .findClassByProximity("콜라")
//   -> "coca_cola" (class label)
//   -> YOLOX target filter`
  return (
    <SplitCodeSlide
      title="Search & Synonym System"
      subtitle="Mapping spoken language to YOLOX class IDs via vector similarity"
      code={code}
      filename="SynonymApi.kt"
      language="Kotlin"
      accentColor="primary"
      bullets={[
        "User speaks natural language (e.g., 'Coke', 'cola', 'coca-cola')",
        "SynonymRepository loads proximity word mappings at app startup",
        "Server uses e5-small embeddings for semantic vector search",
        "MongoDB stores product-to-synonym mappings with embeddings",
        "Fallback: local ProductDictionary.json for offline matching",
        "Result: spoken word -> YOLOX class label for targeted detection",
      ]}
    />
  )
}

/* ─── Slide 21: Room & FTS ─── */
function Slide21() {
  const code = `// SearchHistoryDao.kt
@Dao
interface SearchHistoryDao {

  @Insert
  suspend fun insert(item: SearchHistoryItem)

  @Query("""
    SELECT * FROM search_history
    ORDER BY searchedAt DESC
    LIMIT 100
  """)
  fun getAllOrderedByRecent()
    : Flow<List<SearchHistoryItem>>

  @Query("""
    SELECT classLabel,
           COUNT(*) as searchCount
    FROM search_history
    GROUP BY classLabel
    ORDER BY searchCount DESC
    LIMIT 50
  """)
  fun getFrequentClassLabels()
    : Flow<List<FrequentSearchItem>>
}`
  return (
    <SplitCodeSlide
      title="Local Data: Room DB + FTS"
      subtitle="Instant access to recent and frequently searched products"
      code={code}
      filename="SearchHistoryDao.kt"
      language="Kotlin"
      accentColor="accent-green"
      bullets={[
        "Room DB with SearchHistoryItem entity (query, classLabel, timestamp, source)",
        "Recent searches: ordered by timestamp, LIMIT 100",
        "Frequent searches: GROUP BY classLabel, COUNT(*) for popularity ranking",
        "Flow<List> provides reactive updates to UI (Compose/LiveData bridge)",
        "One-tap re-search: selecting a history item skips voice input entirely",
        "Data stored locally - no server dependency for history features",
      ]}
    />
  )
}

/* ─── Slide 22: Issue 1 - Model Compatibility ─── */
function Slide22() {
  return (
    <ContentSlide title="Troubleshooting #1: Model Compatibility" subtitle="TensorFlow Lite 16KB page size error">
      <div className="grid grid-cols-2 gap-6 h-full items-start mt-2">
        <div className="flex flex-col gap-4">
          <div className="bg-accent-orange/5 rounded-xl p-5 border border-accent-orange/20">
            <div className="flex items-center gap-2 mb-3">
              <AlertTriangle className="w-5 h-5 text-accent-orange" />
              <h3 className="text-lg font-semibold text-accent-orange">Problem</h3>
            </div>
            <p className="text-sm text-muted-foreground leading-relaxed">
              TFLite model crashed on Android 15 devices with a FlatBuffers page alignment error. The 16KB page size introduced in Android 15 was incompatible with older TFLite runtime.
            </p>
            <div className="mt-3 bg-code-bg rounded-lg p-3">
              <code className="text-xs text-code-foreground font-mono">
                java.lang.IllegalArgumentException: Model buffer is not aligned to 16KB page boundary
              </code>
            </div>
          </div>
        </div>
        <div className="flex flex-col gap-4">
          <div className="bg-accent-green/5 rounded-xl p-5 border border-accent-green/20">
            <div className="flex items-center gap-2 mb-3">
              <CheckCircle className="w-5 h-5 text-accent-green" />
              <h3 className="text-lg font-semibold text-accent-green">Solution</h3>
            </div>
            <BulletList items={[
              "Upgraded from TensorFlow Lite to Google's new LiteRT runtime",
              "LiteRT handles 16KB page alignment internally",
              "Updated FlatBuffers serialization to latest spec",
              "Verified on Android 14 and 15 devices",
            ]} color="accent-green" />
          </div>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 23: Issue 2 - Distance Estimation ─── */
function Slide23() {
  const code = `// HomeFragment.kt - Pinhole Camera Model
private fun computeDistanceMm(
  boxWidthPx: Float,
  imageHeight: Int,
  label: String,
  zoomRatio: Float = 1f
): Float {
  if (boxWidthPx <= 0f || imageHeight <= 0)
    return Float.MAX_VALUE

  val zoom = zoomRatio.coerceAtLeast(0.1f)
  val realPixelWidth = boxWidthPx / zoom
  val focalLengthPx = imageHeight * FOCAL_LENGTH_FACTOR
  val physicalWidthMm =
    ProductDictionary.getPhysicalWidthMm(label)

  // Distance = Focal * RealWidth / PixelWidth
  val rawDistanceMm =
    (focalLengthPx * physicalWidthMm) / realPixelWidth
  return rawDistanceMm * DISTANCE_CALIBRATION_FACTOR
}`
  return (
    <SplitCodeSlide
      title="Troubleshooting #2: Distance Estimation"
      subtitle="ARCore failed at close range - solved with Pinhole Camera Model"
      code={code}
      filename="HomeFragment.kt"
      language="Kotlin"
      accentColor="accent-orange"
      bullets={[
        "ARCore depth API failed at < 50cm range (too close for structured light)",
        "Solution: classic Pinhole Camera Model formula",
        "Distance = FocalLength x PhysicalWidth / PixelWidth",
        "ProductDictionary provides real-world dimensions (mm) per class",
        "FOCAL_LENGTH_FACTOR (1.1) calibrated per device FOV",
        "DISTANCE_CALIBRATION_FACTOR (1.5) compensates for 2D projection error",
      ]}
    />
  )
}

/* ─── Slide 24: Limitations ─── */
function Slide24() {
  return (
    <ContentSlide title="Technical Limitations" subtitle="Current hurdles and honest assessment">
      <div className="grid grid-cols-3 gap-6 mt-2">
        <div className="bg-card rounded-xl p-5 border border-border border-t-4 border-t-accent-orange">
          <AlertTriangle className="w-8 h-8 text-accent-orange mb-3" />
          <h3 className="text-lg font-semibold text-foreground mb-2">Heat & Battery</h3>
          <p className="text-sm text-muted-foreground leading-relaxed">
            Running 2 AI models (YOLOX + MediaPipe) plus CameraX simultaneously causes significant heat generation and battery drain. Sustained use limited to ~15-20 minutes.
          </p>
        </div>
        <div className="bg-card rounded-xl p-5 border border-border border-t-4 border-t-accent-orange">
          <Activity className="w-8 h-8 text-accent-orange mb-3" />
          <h3 className="text-lg font-semibold text-foreground mb-2">Depth Accuracy</h3>
          <p className="text-sm text-muted-foreground leading-relaxed">
            2D camera cannot accurately estimate depth from side angles. Pinhole model assumes frontal view - oblique angles introduce significant distance errors.
          </p>
        </div>
        <div className="bg-card rounded-xl p-5 border border-border border-t-4 border-t-accent-orange">
          <Hand className="w-8 h-8 text-accent-orange mb-3" />
          <h3 className="text-lg font-semibold text-foreground mb-2">Touch Judgment</h3>
          <p className="text-sm text-muted-foreground leading-relaxed">
            Without 3D depth sensing, determining physical contact between hand and product relies on 2D bounding box overlap - not true spatial intersection.
          </p>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 25: Differentiation & Roadmap ─── */
function Slide25() {
  return (
    <ContentSlide title="Differentiation & Future Roadmap">
      <div className="flex flex-col gap-6 mt-2">
        <ComparisonTable
          headers={["Feature", "GrabIT", "Sullivan+"]}
          rows={[
            ["Guidance Mode", "Active (hand guidance)", "Passive (text reading)"],
            ["Processing", "On-device (zero latency)", "Server-based (latency)"],
            ["Cost", "Free & Open Source", "Subscription model"],
            ["Hand Detection", "MediaPipe real-time", "Not available"],
            ["Offline Mode", "Full functionality", "Requires internet"],
          ]}
        />
        <div className="grid grid-cols-3 gap-4">
          <div className="bg-primary/5 rounded-xl p-5 border border-primary/20">
            <Layers className="w-6 h-6 text-primary mb-2" />
            <h3 className="text-base font-semibold text-foreground mb-2">Hardware</h3>
            <p className="text-sm text-muted-foreground">Smart Glasses integration for hands-free shopping experience</p>
          </div>
          <div className="bg-accent-orange/5 rounded-xl p-5 border border-accent-orange/20">
            <Zap className="w-6 h-6 text-accent-orange mb-2" />
            <h3 className="text-base font-semibold text-foreground mb-2">Software</h3>
            <p className="text-sm text-muted-foreground">Advanced ARCore with LiDAR for precise 3D depth estimation</p>
          </div>
          <div className="bg-accent-green/5 rounded-xl p-5 border border-accent-green/20">
            <Search className="w-6 h-6 text-accent-green mb-2" />
            <h3 className="text-base font-semibold text-foreground mb-2">Feature</h3>
            <p className="text-sm text-muted-foreground">OCR for reading nutrition facts after grasping the product</p>
          </div>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Export all slides ─── */
export const slides: { component: () => React.ReactNode; title: string; section: string }[] = [
  { component: Slide1, title: "Title", section: "Intro" },
  { component: Slide2, title: "UX Demo", section: "Intro" },
  { component: Slide3, title: "Background & Problem", section: "Intro" },
  { component: Slide4, title: "Core Solution", section: "Intro" },
  { component: Slide5, title: "Table of Contents", section: "Intro" },
  { component: Slide6, title: "Team", section: "Part 1" },
  { component: Slide7, title: "Architecture", section: "Part 2" },
  { component: Slide8, title: "Tech Stack", section: "Part 2" },
  { component: Slide9, title: "User Journey", section: "Part 3" },
  { component: Slide10, title: "Demo Video", section: "Part 3" },
  { component: Slide11, title: "Live Demo", section: "Part 3" },
  { component: Slide12, title: "VUI & Haptics", section: "Part 4" },
  { component: Slide13, title: "BeepPlayer", section: "Part 4" },
  { component: Slide14, title: "YOLOX Detection", section: "Part 4" },
  { component: Slide15, title: "LiteRT & Optimization", section: "Part 4" },
  { component: Slide16, title: "CameraX + MediaPipe", section: "Part 4" },
  { component: Slide17, title: "Gyro Stabilization", section: "Part 4" },
  { component: Slide18, title: "Optical Flow", section: "Part 4" },
  { component: Slide19, title: "Coroutines", section: "Part 4" },
  { component: Slide20, title: "Synonym Search", section: "Part 4" },
  { component: Slide21, title: "Room DB & FTS", section: "Part 4" },
  { component: Slide22, title: "Issue: Model Compat", section: "Part 5" },
  { component: Slide23, title: "Issue: Distance", section: "Part 5" },
  { component: Slide24, title: "Limitations", section: "Part 6" },
  { component: Slide25, title: "Conclusion & Roadmap", section: "Part 7" },
]
