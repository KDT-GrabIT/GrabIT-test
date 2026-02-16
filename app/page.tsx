"use client"

import { useState, useEffect, useCallback } from "react"
import { slides } from "@/components/slides"
import { ChevronLeft, ChevronRight, Grid3X3, X } from "lucide-react"

export default function Presentation() {
  const [currentSlide, setCurrentSlide] = useState(0)
  const [showOverview, setShowOverview] = useState(false)
  const [slideKey, setSlideKey] = useState(0)

  const goTo = useCallback((idx: number) => {
    if (idx >= 0 && idx < slides.length) {
      setCurrentSlide(idx)
      setSlideKey((k) => k + 1)
      setShowOverview(false)
    }
  }, [])

  const next = useCallback(() => goTo(currentSlide + 1), [currentSlide, goTo])
  const prev = useCallback(() => goTo(currentSlide - 1), [currentSlide, goTo])

  useEffect(() => {
    function handleKey(e: KeyboardEvent) {
      if (e.key === "ArrowRight" || e.key === " " || e.key === "Enter") {
        e.preventDefault()
        next()
      } else if (e.key === "ArrowLeft" || e.key === "Backspace") {
        e.preventDefault()
        prev()
      } else if (e.key === "Escape") {
        setShowOverview((v) => !v)
      } else if (e.key === "Home") {
        goTo(0)
      } else if (e.key === "End") {
        goTo(slides.length - 1)
      }
    }
    window.addEventListener("keydown", handleKey)
    return () => window.removeEventListener("keydown", handleKey)
  }, [next, prev, goTo])

  const slide = slides[currentSlide]
  const progress = ((currentSlide + 1) / slides.length) * 100

  return (
    <div className="h-screen w-screen flex flex-col bg-background overflow-hidden select-none">
      {/* Progress bar */}
      <div className="h-1 bg-muted w-full shrink-0">
        <div
          className="h-full bg-primary progress-bar"
          style={{ width: `${progress}%` }}
        />
      </div>

      {/* Slide content */}
      <div className="flex-1 min-h-0 relative">
        {showOverview ? (
          <div className="h-full overflow-y-auto p-6">
            <div className="flex items-center justify-between mb-6">
              <h2 className="text-2xl font-bold text-foreground">{'슬라이드 목록'}</h2>
              <button
                onClick={() => setShowOverview(false)}
                className="p-2 rounded-lg hover:bg-muted transition-colors"
                aria-label="목록 닫기"
              >
                <X className="w-5 h-5 text-muted-foreground" />
              </button>
            </div>
            <div className="grid grid-cols-5 gap-4">
              {slides.map((s, i) => (
                <button
                  key={i}
                  onClick={() => goTo(i)}
                  className={`text-left p-3 rounded-lg border transition-all hover:shadow-md ${
                    i === currentSlide
                      ? "border-primary bg-primary/5 shadow-sm"
                      : "border-border bg-card hover:border-primary/40"
                  }`}
                >
                  <div className="text-xs text-muted-foreground mb-1">{s.section}</div>
                  <div className="text-sm font-medium text-foreground leading-tight">{s.title}</div>
                  <div className="text-xs text-muted-foreground mt-1">{'슬라이드'} {i + 1}</div>
                </button>
              ))}
            </div>
          </div>
        ) : (
          <div key={slideKey} className="h-full p-10 slide-enter">
            {slide.component()}
          </div>
        )}
      </div>

      {/* Bottom navigation bar */}
      <div className="shrink-0 border-t border-border bg-background px-6 py-3 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <button
            onClick={prev}
            disabled={currentSlide === 0}
            className="p-2 rounded-lg hover:bg-muted transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            aria-label="이전 슬라이드"
          >
            <ChevronLeft className="w-5 h-5 text-foreground" />
          </button>
          <button
            onClick={next}
            disabled={currentSlide === slides.length - 1}
            className="p-2 rounded-lg hover:bg-muted transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
            aria-label="다음 슬라이드"
          >
            <ChevronRight className="w-5 h-5 text-foreground" />
          </button>
        </div>

        <div className="flex items-center gap-4">
          <span className="text-xs text-muted-foreground font-mono">
            {slide.section}
          </span>
          <span className="text-sm font-medium text-foreground">
            {slide.title}
          </span>
        </div>

        <div className="flex items-center gap-3">
          <button
            onClick={() => setShowOverview((v) => !v)}
            className="p-2 rounded-lg hover:bg-muted transition-colors"
            aria-label="슬라이드 목록 토글"
          >
            <Grid3X3 className="w-5 h-5 text-muted-foreground" />
          </button>
          <span className="text-sm font-mono text-muted-foreground tabular-nums">
            {currentSlide + 1} / {slides.length}
          </span>
        </div>
      </div>
    </div>
  )
}
