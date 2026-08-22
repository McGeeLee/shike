"use client";

import { useEffect, useRef, useState } from "react";

export function SiteHeader() {
  const [elevated, setElevated] = useState(false);
  const [showReturn, setShowReturn] = useState(false);
  const [hidden, setHidden] = useState(false);
  const elevatedRef = useRef(false);
  const showReturnRef = useRef(false);
  const hiddenRef = useRef(false);
  const lastScrollYRef = useRef(0);
  const scrollDistanceRef = useRef(0);
  const scrollDirectionRef = useRef<"up" | "down" | null>(null);
  const headerHasFocusRef = useRef(false);

  useEffect(() => {
    let frame = 0;

    const updateHeader = () => {
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        const scrollY = Math.max(window.scrollY, 0);
        const nextElevated = scrollY > 28;
        if (nextElevated !== elevatedRef.current) {
          elevatedRef.current = nextElevated;
          setElevated(nextElevated);
        }

        const nextShowReturn = scrollY > 420;
        if (nextShowReturn !== showReturnRef.current) {
          showReturnRef.current = nextShowReturn;
          setShowReturn(nextShowReturn);
        }

        const delta = scrollY - lastScrollYRef.current;

        if (scrollY <= 120 || headerHasFocusRef.current) {
          scrollDistanceRef.current = 0;
          scrollDirectionRef.current = null;
          if (hiddenRef.current) {
            hiddenRef.current = false;
            setHidden(false);
          }
        } else if (delta !== 0) {
          const nextDirection = delta > 0 ? "down" : "up";
          if (nextDirection !== scrollDirectionRef.current) {
            scrollDirectionRef.current = nextDirection;
            scrollDistanceRef.current = Math.abs(delta);
          } else {
            scrollDistanceRef.current += Math.abs(delta);
          }

          if (scrollDistanceRef.current >= 8) {
            const nextHidden = nextDirection === "down";
            scrollDistanceRef.current = 0;
            if (nextHidden !== hiddenRef.current) {
              hiddenRef.current = nextHidden;
              setHidden(nextHidden);
            }
          }
        }

        lastScrollYRef.current = scrollY;
      });
    };

    if (window.location.hash) {
      window.history.replaceState(null, "", `${window.location.pathname}${window.location.search}`);
    }

    lastScrollYRef.current = Math.max(window.scrollY, 0);
    updateHeader();
    window.addEventListener("scroll", updateHeader, { passive: true });
    window.addEventListener("pageshow", updateHeader);
    window.addEventListener("popstate", updateHeader);

    return () => {
      cancelAnimationFrame(frame);
      window.removeEventListener("scroll", updateHeader);
      window.removeEventListener("pageshow", updateHeader);
      window.removeEventListener("popstate", updateHeader);
    };
  }, []);

  const scrollToTop = () => {
    const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    window.scrollTo({ top: 0, left: 0, behavior: reducedMotion ? "auto" : "smooth" });
  };

  const toggleTheme = () => {
    const root = document.documentElement;
    const nextTheme = root.dataset.theme === "dark" ? "light" : "dark";
    root.dataset.theme = nextTheme;
    root.style.colorScheme = nextTheme;
    localStorage.setItem("mcgeelee-theme", nextTheme);
    document.querySelector<HTMLMetaElement>('meta[name="theme-color"]')?.setAttribute(
      "content",
      nextTheme === "dark" ? "#141713" : "#f7f0e4",
    );
  };

  const showFocusedHeader = () => {
    headerHasFocusRef.current = true;
    scrollDistanceRef.current = 0;
    if (hiddenRef.current) {
      hiddenRef.current = false;
      setHidden(false);
    }
  };

  return (
    <div className="header-slot">
      <header
        className={`site-header shell${elevated ? " is-elevated" : ""}${hidden ? " is-hidden" : ""}`}
        onFocusCapture={showFocusedHeader}
        onBlurCapture={(event) => {
          if (!event.currentTarget.contains(event.relatedTarget)) {
            headerHasFocusRef.current = false;
          }
        }}
      >
        <button className="wordmark" type="button" onClick={scrollToTop} aria-label="回到页面顶部">
          McGeeLee<span aria-hidden="true">.</span>
        </button>
        <div className="header-actions">
          <button
            className="theme-toggle"
            type="button"
            onClick={toggleTheme}
            aria-label="切换日间或夜间模式"
            title="切换日间或夜间模式"
          >
            <span className="theme-toggle-moon" aria-hidden="true">☾</span>
            <span className="theme-toggle-sun" aria-hidden="true">☼</span>
          </button>
          <a
            className="header-github"
            href="https://github.com/McGeeLee"
            target="_blank"
            rel="noreferrer"
            aria-label="前往 McGee Lee 的 GitHub（新窗口）"
          >
            GitHub <span aria-hidden="true">↗</span>
          </a>
        </div>
      </header>
      <button
        className={`back-to-top${showReturn ? " is-visible" : ""}`}
        type="button"
        onClick={scrollToTop}
        aria-label="回到页面顶部"
        title="回到顶部"
      >
        <span aria-hidden="true">↑</span>
      </button>
    </div>
  );
}
