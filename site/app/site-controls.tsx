'use client';

import { useEffect, useState } from 'react';

export function SiteControls() {
  const [elevated, setElevated] = useState(false);
  const [showTop, setShowTop] = useState(false);

  useEffect(() => {
    const onScroll = () => {
      setElevated(window.scrollY > 24);
      setShowTop(window.scrollY > 560);
    };
    onScroll();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => window.removeEventListener('scroll', onScroll);
  }, []);

  const toggleTheme = () => {
    const root = document.documentElement;
    const nextTheme = root.dataset.theme === 'dark' ? 'light' : 'dark';
    root.dataset.theme = nextTheme;
    root.style.colorScheme = nextTheme;
    localStorage.setItem('shike-theme', nextTheme);
    document
      .querySelector('meta[name="theme-color"]')
      ?.setAttribute('content', nextTheme === 'dark' ? '#141713' : '#f7f0e4');
  };

  return (
    <>
      <header className={`siteHeader${elevated ? ' isElevated' : ''}`}>
        <button
          className="wordmark"
          type="button"
          onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
          aria-label="回到页面顶部"
        >
          食刻<span aria-hidden="true">.</span>
        </button>
        <div className="headerActions">
          <button
            className="themeToggle"
            type="button"
            onClick={toggleTheme}
            aria-label="切换日间或夜间模式"
            title="切换日间或夜间模式"
          >
            <span className="themeMoon" aria-hidden="true">☾</span>
            <span className="themeSun" aria-hidden="true">☀</span>
          </button>
          <a
            className="headerGithub"
            href="https://github.com/McGeeLee/shike"
            target="_blank"
            rel="noreferrer"
            aria-label="前往食刻 GitHub 仓库（新窗口）"
          >
            GitHub <span aria-hidden="true">↗</span>
          </a>
        </div>
      </header>
      <button
        className={`backToTop${showTop ? ' isVisible' : ''}`}
        type="button"
        onClick={() => window.scrollTo({ top: 0, behavior: 'smooth' })}
        aria-label="回到页面顶部"
        title="回到顶部"
      >
        <span aria-hidden="true">↑</span>
      </button>
    </>
  );
}
