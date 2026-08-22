'use client';

import { useEffect, useState } from 'react';

const latestReleaseApi = 'https://api.github.com/repos/McGeeLee/shike/releases/latest';

const fallbackRelease = {
  version: '2.3.1',
  downloadUrl: 'https://github.com/McGeeLee/shike/releases/download/v2.3.1/shike-v2.3.1.apk',
  size: 2_655_510,
};

type Release = typeof fallbackRelease;

type GitHubRelease = {
  tag_name?: unknown;
  assets?: Array<{
    name?: string;
    browser_download_url?: string;
    size?: number;
  }>;
};

type LatestShikeReleaseProps = {
  variant?: 'action' | 'card';
};

function formatSize(bytes: number) {
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export function LatestShikeRelease({ variant = 'action' }: LatestShikeReleaseProps) {
  const [release, setRelease] = useState<Release>(fallbackRelease);

  useEffect(() => {
    const controller = new AbortController();

    fetch(latestReleaseApi, {
      headers: { Accept: 'application/vnd.github+json' },
      signal: controller.signal,
    })
      .then((response) => {
        if (!response.ok) throw new Error('GitHub latest release request failed');
        return response.json() as Promise<GitHubRelease>;
      })
      .then((data) => {
        const asset = Array.isArray(data.assets)
          ? data.assets.find((item) => item.name?.toLowerCase().endsWith('.apk'))
          : undefined;
        const version = typeof data.tag_name === 'string'
          ? data.tag_name.replace(/^v/i, '')
          : undefined;

        if (asset?.browser_download_url && version) {
          setRelease({
            version,
            downloadUrl: asset.browser_download_url,
            size: typeof asset.size === 'number' ? asset.size : fallbackRelease.size,
          });
        }
      })
      .catch((error) => {
        if (error instanceof Error && error.name === 'AbortError') return;
      });

    return () => controller.abort();
  }, []);

  if (variant === 'card') {
    return (
      <a
        className="apkDownload"
        href={release.downloadUrl}
        aria-label={`下载食刻 v${release.version} APK`}
      >
        <span className="apkDownloadIcon" aria-hidden="true">↓</span>
        <span>
          <strong>下载 Android 正式版 {release.version}</strong>
          <small>签名 APK · {formatSize(release.size)}</small>
        </span>
      </a>
    );
  }

  return (
    <a
      className="actionLink actionLinkPrimary"
      href={release.downloadUrl}
      aria-label={`下载食刻 v${release.version} APK`}
    >
      下载正式版 {release.version} <span aria-hidden="true">↓</span>
    </a>
  );
}
