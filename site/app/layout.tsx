import type { Metadata } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: '食刻 Shike — AI 饮食记录 Android 应用',
  description:
    '食刻 Shike 是一款 Android 饮食记录应用，通过视觉模型识别食物、估算热量与三大营养素，并将记录保存在本地。',
  applicationName: '食刻 Shike',
  keywords: ['食刻', 'Shike', 'AI 饮食记录', '热量记录', 'Android', '视觉模型'],
  authors: [{ name: 'McGee Lee', url: 'https://github.com/McGeeLee' }],
  creator: 'McGee Lee',
  icons: { icon: '/icon.png', shortcut: '/icon.png', apple: '/icon.png' },
  openGraph: {
    title: '食刻 Shike — 拍一张，记一餐',
    description: '用视觉模型识别食物、估算热量与营养素的原生 Android 饮食记录应用。',
    type: 'website',
    locale: 'zh_CN',
    images: [
      {
        url: 'https://raw.githubusercontent.com/McGeeLee/shike/main/site/public/og.png',
        width: 1734,
        height: 907,
        alt: '食刻 Shike — 拍一张，记一餐',
      },
    ],
  },
  twitter: {
    card: 'summary_large_image',
    title: '食刻 Shike — 拍一张，记一餐',
    description: '用视觉模型识别食物、估算热量与营养素的原生 Android 饮食记录应用。',
    images: ['https://raw.githubusercontent.com/McGeeLee/shike/main/site/public/og.png'],
  },
  robots: { index: true, follow: true },
};

const themeScript = `(() => {
  try {
    const saved = localStorage.getItem('shike-theme');
    const theme = saved === 'light' || saved === 'dark'
      ? saved
      : matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
    document.documentElement.dataset.theme = theme;
    document.documentElement.style.colorScheme = theme;
    document.querySelector('meta[name="theme-color"]')?.setAttribute(
      'content', theme === 'dark' ? '#141713' : '#f7f0e4'
    );
  } catch (_) {}
})();`;

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="zh-CN" suppressHydrationWarning>
      <head>
        <meta name="theme-color" content="#f7f0e4" />
        <script dangerouslySetInnerHTML={{ __html: themeScript }} />
      </head>
      <body>{children}</body>
    </html>
  );
}
