"use client"

import { Eye, Hand, Volume2, Wifi, WifiOff, Cpu, Mic, Search, Database, Smartphone, Camera, Activity, AlertTriangle, CheckCircle, ArrowRight, Users, Code, Server, Brain, Zap, Layers } from "lucide-react"
import { TitleSlide, ContentSlide, SectionSlide, ThreeColumnCard, FlowChart, BulletList, ComparisonTable, StatCard } from "./slide-layouts"
import { CodeBlock, SplitCodeSlide } from "./code-block"

/* ─── Slide 1: 타이틀 ─── */
function Slide1() {
  return (
    <TitleSlide
      badge="KDT 파이널 프로젝트"
      title="GrabIT: 실시간 쇼핑 보조 어플리케이션"
      subtitle="온디바이스 AI로 시각장애인의 손을 상품까지 안내하는 음성 & 촉각 가이드"
    >
      <div className="flex items-center gap-6 mt-4">
        <div className="flex items-center gap-2 px-4 py-2 rounded-lg bg-primary/10 text-primary">
          <Eye className="w-5 h-5" />
          <span className="text-sm font-medium">Vision AI</span>
        </div>
        <div className="flex items-center gap-2 px-4 py-2 rounded-lg bg-accent-orange/10 text-accent-orange">
          <Volume2 className="w-5 h-5" />
          <span className="text-sm font-medium">{'음성 가이드'}</span>
        </div>
        <div className="flex items-center gap-2 px-4 py-2 rounded-lg bg-accent-green/10 text-accent-green">
          <Smartphone className="w-5 h-5" />
          <span className="text-sm font-medium">{'온디바이스'}</span>
        </div>
      </div>
    </TitleSlide>
  )
}

/* ─── Slide 2: UX 영상 ─── */
function Slide2() {
  return (
    <ContentSlide title="사용자 경험 데모" subtitle="1분 영상: 안대를 착용한 사용자가 편의점에서 음료를 찾는 과정">
      <div className="flex items-center justify-center h-full">
        <div className="w-full max-w-3xl aspect-video bg-code-bg rounded-xl flex flex-col items-center justify-center gap-4 border border-border">
          <div className="w-16 h-16 rounded-full bg-accent-orange/20 flex items-center justify-center">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" className="text-accent-orange ml-1">
              <path d="M5 3l14 9-14 9V3z" fill="currentColor" />
            </svg>
          </div>
          <p className="text-muted-foreground text-sm">{'영상 플레이스홀더 - UX 데모'}</p>
          <p className="text-muted-foreground/60 text-xs">{'발표 시 재생해주세요'}</p>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 3: 배경 & 문제 ─── */
function Slide3() {
  return (
    <ContentSlide title="배경 & 문제점" subtitle="기존 솔루션이 장보기에서 부족한 이유">
      <div className="grid grid-cols-2 gap-8 h-full items-center">
        <div className="flex flex-col gap-4">
          <div className="bg-card rounded-xl p-6 border border-border border-l-4 border-l-accent-orange">
            <h3 className="text-lg font-semibold text-foreground mb-2">{'접근성의 빈틈'}</h3>
            <p className="text-sm text-muted-foreground leading-relaxed">
              {'Sullivan+ 같은 기존 OCR 앱은 텍스트를 읽을 수 있지만, 사용자의 손을 진열대 위 상품의 물리적 위치까지 안내하지는 못합니다.'}
            </p>
          </div>
          <div className="bg-card rounded-xl p-6 border border-border">
            <h3 className="text-lg font-semibold text-foreground mb-2">{'현실적 영향'}</h3>
            <p className="text-sm text-muted-foreground leading-relaxed">
              {'전 세계 2억 5,200만 명의 시각장애인은 단순한 텍스트 읽기가 아니라, 독립적으로 쇼핑하기 위한 능동적 안내가 필요합니다.'}
            </p>
          </div>
        </div>
        <div className="flex flex-col gap-4">
          <div className="bg-accent-orange/5 rounded-xl p-6 border border-accent-orange/20">
            <p className="text-xl font-bold text-accent-orange mb-2">{'"뭐라고 써있는지는 알겠는데, 어디에 있는 거야?"'}</p>
            <p className="text-sm text-muted-foreground">
              {'핵심 문제: 탐지만 하고 위치를 알려주지 않는 것은 불완전한 보조입니다.'}
            </p>
          </div>
          <div className="flex gap-4">
            <StatCard value="2.52억" label="시각장애인 수 (WHO)" color="primary" />
            <StatCard value="0개" label="손 안내 기능이 있는 앱" color="orange" />
          </div>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 4: 핵심 솔루션 ─── */
function Slide4() {
  return (
    <ContentSlide title="핵심 솔루션: 3가지 기둥" subtitle="탐지와 물리적 안내 사이의 격차를 GrabIT이 어떻게 해소하는가">
      <ThreeColumnCard
        items={[
          {
            icon: <Brain className="w-10 h-10 text-primary" />,
            title: "Vision AI",
            description: "YOLOX-Nano가 온디바이스에서 49개 상품 클래스를 실시간 탐지합니다. MediaPipe가 동시에 사용자의 손 위치를 추적합니다.",
            color: "blue",
          },
          {
            icon: <Volume2 className="w-10 h-10 text-accent-orange" />,
            title: "음성 가이드",
            description: "사인파 비프음(440-880Hz)으로 방향을 안내합니다. TTS가 상품명, 거리, '왼쪽', '오른쪽' 등의 위치 정보를 음성으로 알려줍니다.",
            color: "orange",
          },
          {
            icon: <WifiOff className="w-10 h-10 text-accent-green" />,
            title: "온디바이스 처리",
            description: "네트워크 지연 없음. 모든 AI 추론은 TFLite/LiteRT를 통해 로컬에서 실행됩니다. 서버는 상품 검색 시 유의어 조회에만 사용됩니다.",
            color: "green",
          },
        ]}
      />
    </ContentSlide>
  )
}

/* ─── Slide 5: 목차 ─── */
function Slide5() {
  const parts = [
    { num: "01", title: "팀 소개", color: "bg-primary" },
    { num: "02", title: "아키텍처", color: "bg-primary" },
    { num: "03", title: "시나리오 & 데모", color: "bg-accent-orange" },
    { num: "04", title: "기술 심층 분석", color: "bg-accent-orange" },
    { num: "05", title: "트러블슈팅", color: "bg-accent-green" },
    { num: "06", title: "한계점", color: "bg-accent-green" },
    { num: "07", title: "결론 & 로드맵", color: "bg-primary" },
  ]
  return (
    <ContentSlide title="목차">
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

/* ─── Slide 6: 팀 소개 ─── */
function Slide6() {
  const members = [
    { role: "PM / Android 핵심 개발", name: "팀원 1", skills: ["Kotlin", "CameraX", "TFLite", "Coroutines"] },
    { role: "AI / 백엔드", name: "팀원 2", skills: ["YOLOX", "Python", "Node.js", "MongoDB"] },
    { role: "UI / VUI 설계", name: "팀원 3", skills: ["Figma", "TTS/STT", "접근성", "UX 리서치"] },
  ]
  return (
    <ContentSlide title="팀 소개" subtitle="3명의 팀원, 상호 보완적 전문성">
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

/* ─── Slide 7: 시스템 아키텍처 ─── */
function Slide7() {
  return (
    <ContentSlide title="시스템 아키텍처" subtitle="최소한의 서버 의존성을 가진 온디바이스 AI 파이프라인">
      <div className="flex flex-col items-center gap-6 mt-2">
        <div className="flex items-center gap-3 flex-wrap justify-center">
          <div className="bg-primary text-primary-foreground px-5 py-3 rounded-lg text-sm font-semibold flex items-center gap-2">
            <Camera className="w-4 h-4" /> CameraX 입력
          </div>
          <ArrowRight className="w-5 h-5 text-muted-foreground" />
          <div className="bg-accent-orange text-primary-foreground px-5 py-3 rounded-lg text-sm font-semibold flex items-center gap-2">
            <Brain className="w-4 h-4" /> YOLOX + MediaPipe
          </div>
          <ArrowRight className="w-5 h-5 text-muted-foreground" />
          <div className="bg-accent-green text-primary-foreground px-5 py-3 rounded-lg text-sm font-semibold flex items-center gap-2">
            <Volume2 className="w-4 h-4" /> AudioTrack 출력
          </div>
        </div>
        <div className="text-xs text-muted-foreground font-mono tracking-wide">{'모두 온디바이스 (지연 시간 제로)'}</div>
        <div className="w-px h-8 bg-border" />
        <div className="flex items-center gap-3">
          <div className="bg-muted text-foreground px-5 py-3 rounded-lg text-sm font-semibold flex items-center gap-2 border border-border">
            <Server className="w-4 h-4" /> {'유의어 API'}
          </div>
          <span className="text-xs text-muted-foreground">{'(Node.js + MongoDB, 검색 전용)'}</span>
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
            <h4 className="text-sm font-semibold text-accent-green mb-2">{'서버'}</h4>
            <p className="text-xs text-muted-foreground">Node.js, MongoDB, Python (e5-small 임베딩)</p>
          </div>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 8: 기술 스택 ─── */
function Slide8() {
  return (
    <ContentSlide title="기술 스택" subtitle="온디바이스 성능과 접근성에 최적화">
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
            {["YOLOX-Nano (FP16 TFLite)", "MediaPipe Hand Landmarker", "OpenCV Lucas-Kanade", "Roboflow + MobileSAM3", "49개 상품 클래스"].map((t, i) => (
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
            <h3 className="text-lg font-semibold">{'서버'}</h3>
          </div>
          <div className="flex flex-col gap-2">
            {["Node.js + Express", "MongoDB Atlas", "Python (e5-small 임베딩)", "벡터 검색 (유의어)", "REST API (인증 없음)"].map((t, i) => (
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

/* ─── Slide 9: 사용자 여정 ─── */
function Slide9() {
  return (
    <ContentSlide title="사용자 여정" subtitle="앱 시작부터 상품을 손에 쥐기까지">
      <div className="flex flex-col items-center gap-8 mt-4">
        <FlowChart
          steps={[
            { label: "앱 시작", sub: "터치 / 볼륨 업", color: "blue" },
            { label: "음성 입력", sub: "STT: '콜라'", color: "blue" },
            { label: "확인", sub: "TTS + STT", color: "orange" },
            { label: "탐색", sub: "YOLOX 스캔", color: "orange" },
            { label: "안내", sub: "비프 + TTS", color: "green" },
            { label: "파지", sub: "손 감지", color: "green" },
          ]}
        />
        <div className="grid grid-cols-3 gap-6 w-full max-w-3xl mt-4">
          <div className="bg-card rounded-lg p-4 border border-border text-center">
            <div className="text-2xl font-bold text-primary">{'1-2단계'}</div>
            <div className="text-xs text-muted-foreground mt-1">{'음성 인터랙션'}</div>
            <div className="text-xs text-muted-foreground">VoiceFlowController 상태 머신</div>
          </div>
          <div className="bg-card rounded-lg p-4 border border-border text-center">
            <div className="text-2xl font-bold text-accent-orange">{'3-4단계'}</div>
            <div className="text-xs text-muted-foreground mt-1">AI 탐지</div>
            <div className="text-xs text-muted-foreground">YOLOX + 자이로 트래킹</div>
          </div>
          <div className="bg-card rounded-lg p-4 border border-border text-center">
            <div className="text-2xl font-bold text-accent-green">{'5-6단계'}</div>
            <div className="text-xs text-muted-foreground mt-1">{'물리적 안내'}</div>
            <div className="text-xs text-muted-foreground">{'비프음 + MediaPipe Hands'}</div>
          </div>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 10: 데모 영상 ─── */
function Slide10() {
  return (
    <ContentSlide title="데모 영상" subtitle="GrabIT 실제 작동 영상">
      <div className="flex items-center justify-center h-full">
        <div className="w-full max-w-3xl aspect-video bg-code-bg rounded-xl flex flex-col items-center justify-center gap-4 border border-border">
          <div className="w-16 h-16 rounded-full bg-primary/20 flex items-center justify-center">
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" className="text-primary ml-1">
              <path d="M5 3l14 9-14 9V3z" fill="currentColor" />
            </svg>
          </div>
          <p className="text-muted-foreground text-sm">{'데모 영상 플레이스홀더'}</p>
          <p className="text-muted-foreground/60 text-xs">{'상품 탐지 및 손 안내 전체 과정 워크스루'}</p>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 11: 라이브 데모 ─── */
function Slide11() {
  return (
    <ContentSlide title="라이브 데모" subtitle="폰 미러링 및 QR 코드를 통한 청중 체험">
      <div className="flex items-center justify-center h-full gap-8">
        <div className="bg-card rounded-xl p-8 border border-border flex flex-col items-center gap-4 w-64">
          <Smartphone className="w-16 h-16 text-primary" />
          <h3 className="text-lg font-semibold text-foreground">{'폰 미러링'}</h3>
          <p className="text-sm text-muted-foreground text-center">{'scrcpy 또는 Vysor로 프로젝터에 연결'}</p>
        </div>
        <div className="bg-card rounded-xl p-8 border border-border flex flex-col items-center gap-4 w-64">
          <div className="w-32 h-32 bg-muted rounded-lg flex items-center justify-center border border-border">
            <span className="text-muted-foreground text-xs">QR Code</span>
          </div>
          <h3 className="text-lg font-semibold text-foreground">APK 다운로드</h3>
          <p className="text-sm text-muted-foreground text-center">{'스캔하여 GrabIT을 기기에 설치하세요'}</p>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 12: VUI & 햅틱 피드백 ─── */
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

// 상태 전환
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
      title="VUI & 햅틱 피드백"
      subtitle="상태 머신(State Machine)이 전체 음성 인터랙션을 제어합니다"
      code={code}
      filename="VoiceFlowController.kt"
      language="Kotlin"
      accentColor="accent-orange"
      bullets={[
        "7개 상태 FSM: APP_START -> WAITING_PRODUCT_NAME -> CONFIRM -> SEARCHING -> RESULT/FAILED",
        "TTS가 먼저 말하고, 이후 STT가 듣기 시작 - 순차 실행, 겹침 없음",
        "볼륨 업 길게 누르기: 어디서든 음성 입력으로 바로 진입",
        "볼륨 다운 길게 누르기: 현재 TTS를 건너뛰고 즉시 STT 시작",
        "STT 종료 후 4초 '숨쉬기 시간'으로 자동 안내 TTS 억제",
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
      title="BeepPlayer: 사인파 생성"
      subtitle="AudioTrack을 이용한 지연 없는 음성 피드백"
      code={code}
      filename="BeepPlayer.kt"
      language="Kotlin"
      accentColor="accent-green"
      bullets={[
        "880Hz 원시 PCM 16비트 사인파 생성 (거리에 따라 440-880Hz 가변)",
        "MODE_STATIC: 전체 파형을 버퍼에 미리 로드하여 즉시 재생",
        "MediaPlayer 오버헤드 없음 - AudioTrack API 직접 사용으로 5ms 미만 지연",
        "reloadStaticData()로 재할당 없이 반복 재생 가능",
        "Handler.postDelayed로 깔끔한 정지 + 콜백 순서 보장",
      ]}
    />
  )
}

/* ─── Slide 14: YOLOX 객체 탐지 ─── */
function Slide14() {
  return (
    <ContentSlide title="YOLOX 객체 탐지" subtitle="모바일 추론에 최적화된 Nano 모델">
      <div className="grid grid-cols-2 gap-6 h-full">
        <div className="flex flex-col gap-4">
          <div className="bg-card rounded-xl p-5 border border-border">
            <h3 className="text-lg font-semibold text-foreground mb-3 flex items-center gap-2">
              <Brain className="w-5 h-5 text-accent-orange" /> {'모델 파이프라인'}
            </h3>
            <div className="flex flex-col gap-2">
              <FlowChart steps={[
                { label: "수집", sub: "5000+장", color: "muted" },
                { label: "라벨링", sub: "Roboflow", color: "muted" },
                { label: "자동 라벨링", sub: "MobileSAM3", color: "orange" },
                { label: "학습", sub: "YOLOX-Nano", color: "blue" },
              ]} />
            </div>
          </div>
          <div className="bg-card rounded-xl p-5 border border-border">
            <h3 className="text-lg font-semibold text-foreground mb-3">{'학습 세부 사항'}</h3>
            <BulletList items={[
              "YOLOX-Nano 백본 (경량, 모바일 우선 설계)",
              "TFLite 배포를 위한 Float16 양자화",
              "49개 상품 클래스 (음료, 과자, 생활용품)",
              "Roboflow로 어노테이션 + 증강 파이프라인 구축",
              "MobileSAM3로 반자동 마스크 생성",
            ]} color="accent-orange" />
          </div>
        </div>
        <div className="flex flex-col gap-4">
          <div className="grid grid-cols-2 gap-4">
            <StatCard value="49" label="상품 클래스" color="orange" />
            <StatCard value="FP16" label="양자화" color="primary" />
            <StatCard value="16MB" label="모델 크기" color="green" />
            <StatCard value="~30ms" label="추론 시간" color="orange" />
          </div>
          <div className="bg-accent-orange/5 rounded-xl p-5 border border-accent-orange/20 flex-1">
            <h3 className="text-lg font-semibold text-accent-orange mb-2">{'왜 YOLOX-Nano인가?'}</h3>
            <p className="text-sm text-muted-foreground leading-relaxed">
              {'앵커 프리 설계로 하이퍼파라미터 튜닝이 불필요합니다. 디커플드 헤드는 작은 객체 탐지 성능을 향상시킵니다. Nano 변형은 모바일 GPU에서 최소한의 정확도 손실로 실시간 추론을 달성합니다.'}
            </p>
          </div>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 15: LiteRT & 최적화 ─── */
function Slide15() {
  return (
    <ContentSlide title="LiteRT & 모델 최적화" subtitle="PyTorch에서 온디바이스 TFLite 배포까지">
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
            <div className="text-xs text-muted-foreground">{'중간 단계'}</div>
          </div>
          <div className="flex flex-col items-center gap-1">
            <ArrowRight className="w-6 h-6 text-accent-orange" />
            <span className="text-xs text-accent-orange font-mono">FP16</span>
          </div>
          <div className="bg-accent-green/10 px-6 py-4 rounded-lg border border-accent-green/30 text-center">
            <div className="text-sm font-mono text-accent-green">.tflite</div>
            <div className="text-2xl font-bold text-accent-green">16MB</div>
            <div className="text-xs text-muted-foreground">{'온디바이스'}</div>
          </div>
        </div>
        <div className="grid grid-cols-2 gap-6">
          <div className="bg-card rounded-xl p-5 border border-border">
            <h3 className="text-lg font-semibold text-foreground mb-3">FP16 양자화</h3>
            <BulletList items={[
              "모델 크기 약 12배 감소 (200MB -> 16MB)",
              "정확도 손실 최소 (mAP 1% 미만 하락)",
              "하드웨어 가속을 위한 GPU Delegate 호환",
              "FlatBuffers 직렬화로 제로 카피 로딩",
            ]} color="accent-green" />
          </div>
          <div className="bg-card rounded-xl p-5 border border-border">
            <h3 className="text-lg font-semibold text-foreground mb-3">{'LiteRT 런타임'}</h3>
            <BulletList items={[
              "TensorFlow Lite에서 LiteRT로 업그레이드 (Google 신규 브랜드)",
              "모바일 GPU 병렬 추론을 위한 GPU Delegate",
              "ByteBuffer 입출력의 Interpreter API",
              "코루틴 통합: Dispatchers.Default에서 추론 실행",
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
    <ContentSlide title="CameraX + MediaPipe 파이프라인" subtitle="이중 AI 모델: YOLOX가 상품을, MediaPipe가 손을 탐지">
      <div className="flex flex-col items-center gap-6 mt-2">
        <div className="flex items-center gap-3 flex-wrap justify-center">
          <div className="bg-primary/10 text-primary px-4 py-3 rounded-lg text-sm font-semibold flex items-center gap-2">
            <Camera className="w-4 h-4" /> {'CameraX 프레임'}
          </div>
          <ArrowRight className="w-5 h-5 text-muted-foreground" />
          <div className="flex flex-col gap-2">
            <div className="bg-accent-orange/10 text-accent-orange px-4 py-2 rounded-lg text-xs font-semibold">{'YOLOX: 상품 바운딩 박스'}</div>
            <div className="bg-accent-green/10 text-accent-green px-4 py-2 rounded-lg text-xs font-semibold">{'MediaPipe: 손 랜드마크'}</div>
          </div>
          <ArrowRight className="w-5 h-5 text-muted-foreground" />
          <div className="bg-primary text-primary-foreground px-4 py-3 rounded-lg text-sm font-semibold">
            {'좌표 매핑'}
          </div>
          <ArrowRight className="w-5 h-5 text-muted-foreground" />
          <div className="bg-accent-green text-primary-foreground px-4 py-3 rounded-lg text-sm font-semibold flex items-center gap-2">
            <Hand className="w-4 h-4" /> {'터치 감지'}
          </div>
        </div>
        <div className="grid grid-cols-2 gap-6 w-full mt-2">
          <div className="bg-card rounded-xl p-5 border border-border">
            <h3 className="text-lg font-semibold text-foreground mb-3">{'YOLOX 추론 경로'}</h3>
            <BulletList items={[
              "ImageProxy -> RGBA Bitmap -> 416x416 리사이즈",
              "ByteBuffer 정규화 (0-1 범위)",
              "백그라운드 스레드에서 TFLite Interpreter.run() 실행",
              "NMS + 신뢰도 필터링 (임계값: 0.5)",
              "출력: classId, confidence, 바운딩 박스 (x,y,w,h)",
            ]} color="accent-orange" />
          </div>
          <div className="bg-card rounded-xl p-5 border border-border">
            <h3 className="text-lg font-semibold text-foreground mb-3">{'MediaPipe 손 경로'}</h3>
            <BulletList items={[
              "CameraX 프레임 -> BitmapImageBuilder -> MPImage",
              "HandLandmarker.detect()로 손당 21개 랜드마크 반환",
              "검지 끝(INDEX_FINGER_TIP, 랜드마크 #8) 추출",
              "정규화 좌표를 이미지 픽셀 공간으로 변환",
              "겹침 검사: 검지 끝이 확장된 상품 BBox 안에 있는지 확인",
            ]} color="accent-green" />
          </div>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 17: 자이로 트래킹 ─── */
function Slide17() {
  const code = `// GyroTrackingManager.kt - 핵심 로직
private fun processRotationVector(event: SensorEvent) {
  SensorManager.getRotationMatrixFromVector(
    currentRotationMatrix, rv
  )
  SensorManager.getOrientation(adjustedMatrix, orientation)

  var deltaYaw   = orientation[0] - initialOrientation[0]
  var deltaPitch  = orientation[1] - initialOrientation[1]

  // 카메라가 오른쪽으로 회전 -> 객체는 화면에서 왼쪽으로 이동
  val rotationShiftX = -deltaYaw * pixelsPerRadianX
                       * SENSITIVITY_FACTOR
  val rotationShiftY = deltaPitch * pixelsPerRadianY
                       * SENSITIVITY_FACTOR

  // 지수 이동 평균으로 스무딩
  val newLeft = currentSmoothedRect.left +
    (targetX - currentSmoothedRect.left) * SMOOTHING_ALPHA
}`
  return (
    <SplitCodeSlide
      title="자이로 안정화"
      subtitle="회전 센서를 활용하여 바운딩 박스를 월드 공간에 고정"
      code={code}
      filename="GyroTrackingManager.kt"
      language="Kotlin"
      accentColor="primary"
      bullets={[
        "회전 벡터 센서 -> 초기 방향으로부터의 deltaYaw/deltaPitch 계산",
        "카메라 움직임 역보정: 카메라가 오른쪽 패닝 = 박스는 왼쪽으로 이동",
        "알려진 FOV(수평 79도)를 이용한 픽셀/라디안 변환",
        "지터 억제를 위한 지수 스무딩 (alpha=0.25)",
        "센서 판독값 안정화를 위한 500ms 웜업 기간",
        "범위 이탈 감지: 15프레임 연속 이탈 시 트래킹 손실 판정",
      ]}
    />
  )
}

/* ─── Slide 18: 오클루전 처리 ─── */
function Slide18() {
  const code = `// OpticalFlowTracker.kt
fun update(
  bitmap: Bitmap,
  excludeHandRect: RectF?,
  excludeBoxRect: RectF?
): Pair<Float, Float>? {
  // 속도를 위해 320x240으로 다운스케일
  val smallBitmap = Bitmap.createScaledBitmap(
    bitmap, processWidth, processHeight, true
  )
  // 손 & 타겟 영역 마스킹
  val mask = createExcludeMask(pw, ph,
    handRect, boxRect, scaleX, scaleY)

  // Lucas-Kanade 옵티컬 플로우
  Video.calcOpticalFlowPyrLK(
    prevGray, gray, prevPts, nextPts, status, err
  )

  // IQR 이상치 제거 + 중앙값 플로우
  val (dxList, dyList) = filterOutliersIqr(validDx, validDy)
  return Pair(median(dxList)*scaleX, median(dyList)*scaleY)
}`
  return (
    <SplitCodeSlide
      title="오클루전 처리 (Optical Flow)"
      subtitle="손이 상품을 가릴 때, 배경 움직임을 대신 추적"
      code={code}
      filename="OpticalFlowTracker.kt"
      language="Kotlin"
      accentColor="accent-orange"
      bullets={[
        "손이 상품 바운딩 박스와 겹칠 때 트리거 (오클루전 이벤트)",
        "손 + 타겟 영역을 마스킹하여 배경 특징점만 추적",
        "goodFeaturesToTrack: 품질 임계값 0.1로 80개 코너 포인트 검출",
        "Lucas-Kanade 피라미드: 프레임 간 특징점 변위 추적",
        "IQR 기반 이상치 제거 (k=1.5)로 강건한 움직임 추정",
        "중앙값 dx/dy를 바운딩 박스에 적용하여 고정 유지",
      ]}
    />
  )
}

/* ─── Slide 19: 코루틴 ─── */
function Slide19() {
  const code = `// HomeFragment.kt - 메인 스레드 외부에서의 추론
cameraExecutor = Executors.newSingleThreadExecutor()

// 백그라운드 실행자에서 CameraX ImageAnalysis
ImageAnalysis.Builder()
  .setResolutionSelector(resolutionSelector)
  .setBackpressureStrategy(STRATEGY_KEEP_ONLY_LATEST)
  .build().also { analysis ->
    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
      // YOLOX 추론 (무거운 연산)
      val results = runYoloxInference(imageProxy)

      // UI 업데이트를 위해 Main으로 전환
      withContext(Dispatchers.Main) {
        overlayView.setDetections(results, w, h)
      }
    }
  }

// Room DB 쿼리 (IO 스레드)
lifecycleScope.launch(Dispatchers.IO) {
  val repo = SearchHistoryRepository(db)
  repo.insert(SearchHistoryItem(...))
}`
  return (
    <SplitCodeSlide
      title="코루틴 & 비동기 아키텍처"
      subtitle="이중 AI 모델을 실행하면서 UI 스레드를 자유롭게 유지"
      code={code}
      filename="HomeFragment.kt"
      language="Kotlin"
      accentColor="accent-green"
      bullets={[
        "CameraX ImageAnalysis용 SingleThreadExecutor - 직렬 프레임 처리",
        "STRATEGY_KEEP_ONLY_LATEST: 추론이 느리면 프레임 드롭, 백프레셔 없음",
        "CPU 집약적 YOLOX 추론을 위한 Dispatchers.Default",
        "Room 데이터베이스 및 네트워크 호출을 위한 Dispatchers.IO",
        "UI 업데이트(오버레이, 버튼, TTS 트리거)를 위한 Dispatchers.Main",
        "lifecycleScope로 Fragment 파괴 시 코루틴 자동 취소 보장",
      ]}
    />
  )
}

/* ─── Slide 20: 유의어 API ─── */
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

// 서버: Node.js + MongoDB + e5-small
// 사용자가 "콜라" -> 벡터 검색으로
// 가장 가까운 매칭: "coca_cola" (classId)
//
// 플로우:
// STT("콜라") -> SynonymRepository
//   .findClassByProximity("콜라")
//   -> "coca_cola" (클래스 레이블)
//   -> YOLOX 타겟 필터`
  return (
    <SplitCodeSlide
      title="검색 & 유의어 시스템"
      subtitle="벡터 유사도를 활용하여 자연어를 YOLOX 클래스 ID로 매핑"
      code={code}
      filename="SynonymApi.kt"
      language="Kotlin"
      accentColor="primary"
      bullets={[
        "사용자가 자연어로 발화 (예: '콜라', '코카콜라', '사이다')",
        "SynonymRepository가 앱 시작 시 근접 단어 매핑을 로드",
        "서버에서 e5-small 임베딩으로 의미 벡터 검색 수행",
        "MongoDB에 임베딩과 함께 상품-유의어 매핑 저장",
        "폴백: 오프라인 매칭을 위한 로컬 ProductDictionary.json",
        "결과: 자연어 발화 -> YOLOX 클래스 레이블로 타겟 탐지",
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
      title="로컬 데이터: Room DB + FTS"
      subtitle="최근 및 자주 검색한 상품에 즉시 접근"
      code={code}
      filename="SearchHistoryDao.kt"
      language="Kotlin"
      accentColor="accent-green"
      bullets={[
        "SearchHistoryItem 엔티티의 Room DB (쿼리, classLabel, 타임스탬프, 출처)",
        "최근 검색: 타임스탬프 기준 정렬, LIMIT 100",
        "자주 검색: classLabel별 GROUP BY, COUNT(*)로 인기도 순위",
        "Flow<List>로 UI에 반응형 업데이트 제공 (Compose/LiveData 브릿지)",
        "원터치 재검색: 히스토리 항목 선택 시 음성 입력 과정을 완전 생략",
        "데이터는 로컬에 저장 - 히스토리 기능에 서버 의존성 없음",
      ]}
    />
  )
}

/* ─── Slide 22: 이슈 1 - 모델 호환성 ─── */
function Slide22() {
  return (
    <ContentSlide title="트러블슈팅 #1: 모델 호환성" subtitle="TensorFlow Lite 16KB 페이지 크기 오류">
      <div className="grid grid-cols-2 gap-6 h-full items-start mt-2">
        <div className="flex flex-col gap-4">
          <div className="bg-accent-orange/5 rounded-xl p-5 border border-accent-orange/20">
            <div className="flex items-center gap-2 mb-3">
              <AlertTriangle className="w-5 h-5 text-accent-orange" />
              <h3 className="text-lg font-semibold text-accent-orange">{'문제'}</h3>
            </div>
            <p className="text-sm text-muted-foreground leading-relaxed">
              {'TFLite 모델이 Android 15 기기에서 FlatBuffers 페이지 정렬 오류로 크래시되었습니다. Android 15에서 도입된 16KB 페이지 크기가 기존 TFLite 런타임과 호환되지 않았습니다.'}
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
              <h3 className="text-lg font-semibold text-accent-green">{'해결'}</h3>
            </div>
            <BulletList items={[
              "TensorFlow Lite에서 Google의 새로운 LiteRT 런타임으로 업그레이드",
              "LiteRT가 16KB 페이지 정렬을 내부적으로 처리",
              "FlatBuffers 직렬화를 최신 스펙으로 업데이트",
              "Android 14 및 15 기기에서 검증 완료",
            ]} color="accent-green" />
          </div>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 23: 이슈 2 - 거리 추정 ─── */
function Slide23() {
  const code = `// HomeFragment.kt - 핀홀 카메라 모델
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

  // 거리 = 초점거리 * 실제너비 / 픽셀너비
  val rawDistanceMm =
    (focalLengthPx * physicalWidthMm) / realPixelWidth
  return rawDistanceMm * DISTANCE_CALIBRATION_FACTOR
}`
  return (
    <SplitCodeSlide
      title="트러블슈팅 #2: 거리 추정"
      subtitle="ARCore가 근거리에서 실패 - 핀홀 카메라 모델로 해결"
      code={code}
      filename="HomeFragment.kt"
      language="Kotlin"
      accentColor="accent-orange"
      bullets={[
        "ARCore 깊이 API가 50cm 미만 거리에서 실패 (구조광 방식의 한계)",
        "해결책: 고전적인 핀홀 카메라 모델 공식 적용",
        "거리 = 초점거리 x 실제 너비 / 픽셀 너비",
        "ProductDictionary가 클래스별 실제 치수(mm) 제공",
        "FOCAL_LENGTH_FACTOR (1.1) 기기 FOV별 보정",
        "DISTANCE_CALIBRATION_FACTOR (1.5) 2D 투영 오차 보정",
      ]}
    />
  )
}

/* ─── Slide 24: 한계점 ─── */
function Slide24() {
  return (
    <ContentSlide title="기술적 한계점" subtitle="현재의 허들과 솔직한 평가">
      <div className="grid grid-cols-3 gap-6 mt-2">
        <div className="bg-card rounded-xl p-5 border border-border border-t-4 border-t-accent-orange">
          <AlertTriangle className="w-8 h-8 text-accent-orange mb-3" />
          <h3 className="text-lg font-semibold text-foreground mb-2">{'발열 & 배터리'}</h3>
          <p className="text-sm text-muted-foreground leading-relaxed">
            {'2개의 AI 모델(YOLOX + MediaPipe)과 CameraX를 동시에 실행하면 상당한 발열과 배터리 소모가 발생합니다. 지속 사용은 약 15-20분으로 제한됩니다.'}
          </p>
        </div>
        <div className="bg-card rounded-xl p-5 border border-border border-t-4 border-t-accent-orange">
          <Activity className="w-8 h-8 text-accent-orange mb-3" />
          <h3 className="text-lg font-semibold text-foreground mb-2">{'깊이 정확도'}</h3>
          <p className="text-sm text-muted-foreground leading-relaxed">
            {'2D 카메라로는 측면 각도에서 깊이를 정확히 추정할 수 없습니다. 핀홀 모델은 정면 뷰를 가정하며, 비스듬한 각도에서는 상당한 거리 오차가 발생합니다.'}
          </p>
        </div>
        <div className="bg-card rounded-xl p-5 border border-border border-t-4 border-t-accent-orange">
          <Hand className="w-8 h-8 text-accent-orange mb-3" />
          <h3 className="text-lg font-semibold text-foreground mb-2">{'터치 판정'}</h3>
          <p className="text-sm text-muted-foreground leading-relaxed">
            {'3D 깊이 센서 없이는 손과 상품의 물리적 접촉 판단이 2D 바운딩 박스 겹침에 의존하며, 실제 공간 교차점이 아닙니다.'}
          </p>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── Slide 25: 차별점 & 로드맵 ─── */
function Slide25() {
  return (
    <ContentSlide title="차별점 & 향후 로드맵">
      <div className="flex flex-col gap-6 mt-2">
        <ComparisonTable
          headers={["기능", "GrabIT", "Sullivan+"]}
          rows={[
            ["안내 방식", "능동적 (손 안내)", "수동적 (텍스트 읽기)"],
            ["처리 방식", "온디바이스 (지연 없음)", "서버 기반 (지연 발생)"],
            ["비용", "무료 & 오픈소스", "구독 모델"],
            ["손 감지", "MediaPipe 실시간", "미지원"],
            ["오프라인 모드", "전체 기능 사용 가능", "인터넷 필요"],
          ]}
        />
        <div className="grid grid-cols-3 gap-4">
          <div className="bg-primary/5 rounded-xl p-5 border border-primary/20">
            <Layers className="w-6 h-6 text-primary mb-2" />
            <h3 className="text-base font-semibold text-foreground mb-2">{'하드웨어'}</h3>
            <p className="text-sm text-muted-foreground">{'핸즈프리 쇼핑 경험을 위한 스마트 글래스 통합'}</p>
          </div>
          <div className="bg-accent-orange/5 rounded-xl p-5 border border-accent-orange/20">
            <Zap className="w-6 h-6 text-accent-orange mb-2" />
            <h3 className="text-base font-semibold text-foreground mb-2">{'소프트웨어'}</h3>
            <p className="text-sm text-muted-foreground">{'정밀 3D 깊이 추정을 위한 고급 ARCore + LiDAR'}</p>
          </div>
          <div className="bg-accent-green/5 rounded-xl p-5 border border-accent-green/20">
            <Search className="w-6 h-6 text-accent-green mb-2" />
            <h3 className="text-base font-semibold text-foreground mb-2">{'기능'}</h3>
            <p className="text-sm text-muted-foreground">{'상품 파지 후 영양 성분표 읽기를 위한 OCR'}</p>
          </div>
        </div>
      </div>
    </ContentSlide>
  )
}

/* ─── 전체 슬라이드 내보내기 ─── */
export const slides: { component: () => React.ReactNode; title: string; section: string }[] = [
  { component: Slide1, title: "타이틀", section: "인트로" },
  { component: Slide2, title: "UX 데모", section: "인트로" },
  { component: Slide3, title: "배경 & 문제점", section: "인트로" },
  { component: Slide4, title: "핵심 솔루션", section: "인트로" },
  { component: Slide5, title: "목차", section: "인트로" },
  { component: Slide6, title: "팀 소개", section: "Part 1" },
  { component: Slide7, title: "아키텍처", section: "Part 2" },
  { component: Slide8, title: "기술 스택", section: "Part 2" },
  { component: Slide9, title: "사용자 여정", section: "Part 3" },
  { component: Slide10, title: "데모 영상", section: "Part 3" },
  { component: Slide11, title: "라이브 데모", section: "Part 3" },
  { component: Slide12, title: "VUI & 햅틱", section: "Part 4" },
  { component: Slide13, title: "BeepPlayer", section: "Part 4" },
  { component: Slide14, title: "YOLOX 탐지", section: "Part 4" },
  { component: Slide15, title: "LiteRT & 최적화", section: "Part 4" },
  { component: Slide16, title: "CameraX + MediaPipe", section: "Part 4" },
  { component: Slide17, title: "자이로 안정화", section: "Part 4" },
  { component: Slide18, title: "옵티컬 플로우", section: "Part 4" },
  { component: Slide19, title: "코루틴", section: "Part 4" },
  { component: Slide20, title: "유의어 검색", section: "Part 4" },
  { component: Slide21, title: "Room DB & FTS", section: "Part 4" },
  { component: Slide22, title: "이슈: 모델 호환", section: "Part 5" },
  { component: Slide23, title: "이슈: 거리 추정", section: "Part 5" },
  { component: Slide24, title: "한계점", section: "Part 6" },
  { component: Slide25, title: "결론 & 로드맵", section: "Part 7" },
]
