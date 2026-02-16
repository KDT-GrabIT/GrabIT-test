import type { Metadata, Viewport } from "next"
import { Inter, JetBrains_Mono } from "next/font/google"
import "./globals.css"

const inter = Inter({ subsets: ["latin"], variable: "--font-inter" })
const jetbrainsMono = JetBrains_Mono({ subsets: ["latin"], variable: "--font-jetbrains" })

export const metadata: Metadata = {
  title: "GrabIT - 시각장애인을 위한 실시간 쇼핑 보조 어플리케이션",
  description: "온디바이스 AI 상품 탐지 및 음성/촉각 안내 시스템. 프로젝트 발표.",
}

export const viewport: Viewport = {
  themeColor: "#1E3A5F",
}

export default function RootLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <html lang="ko" className={`${inter.variable} ${jetbrainsMono.variable}`}>
      <body className="font-sans bg-background text-foreground antialiased">{children}</body>
    </html>
  )
}
