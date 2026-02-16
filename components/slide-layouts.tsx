import { type ReactNode } from "react"

export function TitleSlide({
  title,
  subtitle,
  badge,
  children,
}: {
  title: string
  subtitle?: string
  badge?: string
  children?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center h-full text-center">
      {badge && (
        <span className="inline-block px-4 py-1.5 rounded-full text-sm font-medium bg-primary/10 text-primary mb-6 slide-fade-in stagger-1">
          {badge}
        </span>
      )}
      <h1 className="text-5xl font-bold text-foreground leading-tight text-balance slide-fade-in stagger-2 max-w-4xl">
        {title}
      </h1>
      {subtitle && (
        <p className="text-xl text-muted-foreground mt-4 max-w-2xl text-balance slide-fade-in stagger-3">
          {subtitle}
        </p>
      )}
      {children && <div className="mt-8 slide-fade-in stagger-4">{children}</div>}
    </div>
  )
}

export function SectionSlide({ part, title, color = "primary" }: { part: string; title: string; color?: string }) {
  const bgMap: Record<string, string> = {
    primary: "bg-primary",
    orange: "bg-accent-orange",
    green: "bg-accent-green",
    blue: "bg-accent-blue",
  }
  return (
    <div className={`flex flex-col items-center justify-center h-full ${bgMap[color] || "bg-primary"} -m-10 p-10`}>
      <span className="text-lg font-mono text-primary-foreground/60 tracking-widest uppercase mb-4">{part}</span>
      <h2 className="text-5xl font-bold text-primary-foreground text-balance text-center">{title}</h2>
    </div>
  )
}

export function ContentSlide({
  title,
  subtitle,
  children,
}: {
  title: string
  subtitle?: string
  children: ReactNode
}) {
  return (
    <div className="flex flex-col h-full">
      <div className="mb-6">
        <h2 className="text-3xl font-bold text-foreground text-balance">{title}</h2>
        {subtitle && <p className="text-lg text-muted-foreground mt-2 text-pretty">{subtitle}</p>}
      </div>
      <div className="flex-1 min-h-0">{children}</div>
    </div>
  )
}

export function ThreeColumnCard({
  items,
}: {
  items: { icon: ReactNode; title: string; description: string; color?: string }[]
}) {
  const borderColors: Record<string, string> = {
    blue: "border-t-primary",
    orange: "border-t-accent-orange",
    green: "border-t-accent-green",
  }
  return (
    <div className="grid grid-cols-3 gap-6 h-full items-start">
      {items.map((item, i) => (
        <div
          key={i}
          className={`bg-card rounded-xl p-6 border border-border ${borderColors[item.color || "blue"] || "border-t-primary"} border-t-4 slide-fade-in stagger-${i + 1}`}
        >
          <div className="mb-4">{item.icon}</div>
          <h3 className="text-xl font-semibold text-card-foreground mb-2">{item.title}</h3>
          <p className="text-sm text-muted-foreground leading-relaxed">{item.description}</p>
        </div>
      ))}
    </div>
  )
}

export function FlowChart({ steps }: { steps: { label: string; sub?: string; color?: string }[] }) {
  return (
    <div className="flex items-center justify-center gap-2 flex-wrap">
      {steps.map((step, i) => {
        const bgMap: Record<string, string> = {
          blue: "bg-primary text-primary-foreground",
          orange: "bg-accent-orange text-primary-foreground",
          green: "bg-accent-green text-primary-foreground",
          muted: "bg-muted text-foreground",
        }
        const bg = bgMap[step.color || "muted"] || "bg-muted text-foreground"
        return (
          <div key={i} className="flex items-center gap-2">
            <div className={`${bg} px-4 py-3 rounded-lg text-center min-w-[100px]`}>
              <div className="text-sm font-semibold">{step.label}</div>
              {step.sub && <div className="text-xs opacity-80 mt-0.5">{step.sub}</div>}
            </div>
            {i < steps.length - 1 && (
              <svg width="24" height="24" viewBox="0 0 24 24" fill="none" className="text-muted-foreground shrink-0">
                <path d="M5 12h14m0 0l-4-4m4 4l-4 4" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" />
              </svg>
            )}
          </div>
        )
      })}
    </div>
  )
}

export function BulletList({ items, color = "accent-orange" }: { items: string[]; color?: string }) {
  const dotMap: Record<string, string> = {
    "accent-orange": "bg-accent-orange",
    "accent-green": "bg-accent-green",
    "accent-blue": "bg-accent-blue",
    primary: "bg-primary",
  }
  const dot = dotMap[color] || "bg-accent-orange"
  return (
    <div className="flex flex-col gap-3">
      {items.map((item, i) => (
        <div key={i} className="flex items-start gap-3 slide-fade-in" style={{ animationDelay: `${(i + 1) * 0.1}s`, opacity: 0 }}>
          <span className={`mt-2 w-2 h-2 rounded-full ${dot} shrink-0`} />
          <p className="text-base text-foreground leading-relaxed">{item}</p>
        </div>
      ))}
    </div>
  )
}

export function ComparisonTable({
  headers,
  rows,
}: {
  headers: string[]
  rows: string[][]
}) {
  return (
    <div className="overflow-hidden rounded-lg border border-border">
      <table className="w-full">
        <thead>
          <tr className="bg-primary text-primary-foreground">
            {headers.map((h, i) => (
              <th key={i} className="px-6 py-3 text-left text-sm font-semibold">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row, ri) => (
            <tr key={ri} className={ri % 2 === 0 ? "bg-background" : "bg-card"}>
              {row.map((cell, ci) => (
                <td key={ci} className="px-6 py-3 text-sm text-foreground border-t border-border">
                  {cell}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

export function StatCard({ value, label, color = "primary" }: { value: string; label: string; color?: string }) {
  const colorMap: Record<string, string> = {
    primary: "text-primary",
    orange: "text-accent-orange",
    green: "text-accent-green",
  }
  return (
    <div className="bg-card rounded-xl p-6 border border-border text-center">
      <div className={`text-4xl font-bold ${colorMap[color] || "text-primary"}`}>{value}</div>
      <div className="text-sm text-muted-foreground mt-2">{label}</div>
    </div>
  )
}
