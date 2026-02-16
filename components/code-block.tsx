export function CodeBlock({ code, language, filename }: { code: string; language?: string; filename?: string }) {
  return (
    <div className="rounded-lg overflow-hidden border border-border/50 shadow-sm">
      {filename && (
        <div className="flex items-center gap-2 px-4 py-2 bg-code-bg border-b border-border/30">
          <div className="flex gap-1.5">
            <span className="w-3 h-3 rounded-full bg-red-400/80" />
            <span className="w-3 h-3 rounded-full bg-yellow-400/80" />
            <span className="w-3 h-3 rounded-full bg-green-400/80" />
          </div>
          <span className="text-xs font-mono text-muted-foreground ml-2">{filename}</span>
          {language && (
            <span className="ml-auto text-xs text-muted-foreground/60 uppercase tracking-wide">{language}</span>
          )}
        </div>
      )}
      <div className="bg-code-bg p-4 overflow-x-auto code-scroll">
        <pre className="text-sm leading-relaxed font-mono text-code-foreground whitespace-pre">{code}</pre>
      </div>
    </div>
  )
}

export function SplitCodeSlide({
  title,
  subtitle,
  code,
  filename,
  language,
  bullets,
  accentColor = "accent-orange",
}: {
  title: string
  subtitle?: string
  code: string
  filename: string
  language?: string
  bullets: string[]
  accentColor?: string
}) {
  const colorMap: Record<string, string> = {
    "accent-orange": "bg-accent-orange",
    "accent-green": "bg-accent-green",
    "accent-blue": "bg-accent-blue",
    primary: "bg-primary",
  }
  const dotColor = colorMap[accentColor] || "bg-accent-orange"

  return (
    <div className="flex flex-col h-full">
      <div className="mb-6">
        <h2 className="text-3xl font-bold text-foreground text-balance">{title}</h2>
        {subtitle && <p className="text-lg text-muted-foreground mt-2">{subtitle}</p>}
      </div>
      <div className="flex-1 grid grid-cols-2 gap-6 min-h-0">
        <div className="min-h-0 flex flex-col">
          <CodeBlock code={code} filename={filename} language={language} />
        </div>
        <div className="flex flex-col justify-center gap-4">
          {bullets.map((b, i) => (
            <div key={i} className="flex items-start gap-3">
              <span className={`mt-2 w-2 h-2 rounded-full ${dotColor} shrink-0`} />
              <p className="text-base text-foreground leading-relaxed">{b}</p>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
