import { ShikeDemo } from './shike-demo';
import { SiteControls } from './site-controls';

const version = '2.3.1';
const repositoryUrl = 'https://github.com/McGeeLee/shike';
const releasesUrl = `${repositoryUrl}/releases`;
const releaseUrl = `${repositoryUrl}/releases/download/v${version}/shike-v${version}.apk`;

const softwareSchema = {
  '@context': 'https://schema.org',
  '@type': 'SoftwareApplication',
  name: '食刻 Shike',
  description:
    '通过视觉模型识别食物、估算热量与三大营养素，并在本地保存记录的 Android 应用。',
  applicationCategory: 'HealthApplication',
  operatingSystem: 'Android',
  downloadUrl: releaseUrl,
  softwareVersion: version,
  codeRepository: repositoryUrl,
  author: {
    '@type': 'Person',
    name: 'McGee Lee',
    url: 'https://github.com/McGeeLee',
  },
};

export default function Home() {
  return (
    <main className="projectDetail projectDetailShike">
      <SiteControls />
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(softwareSchema) }}
      />

      <nav id="top" className="projectDetailNav" aria-label="项目页导航">
        <a href={repositoryUrl}>← 项目仓库</a>
        <span>ANDROID / AI NUTRITION TRACKER</span>
      </nav>

      <section className="projectDetailHero" aria-labelledby="project-title">
        <article className="projectDetailHeroCopy">
          <span className="sectionCode">ANDROID / DAILY PRODUCT</span>
          <h1 id="project-title"><span>食刻</span><span>Shike</span></h1>
          <p className="projectDetailKicker">拍一张，记一餐。</p>
          <p className="projectDetailDescription">
            一款从真实记录需求出发的 Android 饮食应用。它把拍照、视觉识别、营养估算和当天记录连成一个足够轻的日常动作。
          </p>
          <ul className="projectDetailTags" aria-label="项目技术与特性">
            <li>视觉模型</li><li>原生 Android</li><li>本地保存</li><li>隐私优先</li>
          </ul>
          <div className="projectDetailActions">
            <a className="actionLink actionLinkPrimary" href={releaseUrl}>
              下载正式版 {version} <span aria-hidden="true">↓</span>
            </a>
            <a className="actionLink actionLinkSecondary" href={repositoryUrl} target="_blank" rel="noreferrer">
              查看源码 <span aria-hidden="true">↗</span>
            </a>
          </div>
        </article>

        <div className="projectDetailVisual" aria-label="食刻应用首页交互预览">
          <ShikeDemo />
        </div>
      </section>

      <section className="projectBrief" aria-labelledby="project-brief-title">
        <header>
          <span className="sectionCode">THE BRIEF</span>
          <h2 id="project-brief-title">把记录这件事，缩短到一次拍照。</h2>
          <p>复杂的营养数据只有进入日常才有意义。食刻没有从功能清单开始，而是从“我怎样才愿意每天记”开始。</p>
        </header>
        <dl className="projectFactGrid">
          <div><dt>FORMAT</dt><dd><strong>Android</strong><span>原生独立应用</span></dd></div>
          <div><dt>RELEASE</dt><dd><strong>{version}</strong><span>正式签名版本</span></dd></div>
          <div><dt>PRIVACY</dt><dd><strong>本地</strong><span>记录留在设备</span></dd></div>
        </dl>
      </section>

      <section className="projectProcess" aria-labelledby="project-process-title">
        <header>
          <span className="sectionCode">HOW IT WORKS</span>
          <h2 id="project-process-title">三步，把一餐留下来。</h2>
          <p>交互尽量少，信息保持够用；模型负责理解照片，应用负责把结果变成可以长期回看的记录。</p>
        </header>
        <ol className="projectStepGrid">
          <li><span>01</span><small>CAPTURE</small><strong>拍下食物</strong><p>从系统相机或照片选择器进入记录流程，并先在设备端压缩图片，减少不必要的传输与等待。</p></li>
          <li><span>02</span><small>UNDERSTAND</small><strong>识别与估算</strong><p>你选择的视觉模型识别餐食内容，给出热量、蛋白质、碳水和脂肪估算。</p></li>
          <li><span>03</span><small>REMEMBER</small><strong>留在当天</strong><p>把结果保存到本地日历式记录中，让趋势来自真实使用，而不是一次演示。</p></li>
        </ol>
      </section>

      <section className="projectClosing" aria-labelledby="project-closing-title">
        <span className="sectionCode">WHAT MATTERS</span>
        <h2 id="project-closing-title">不是更复杂的健康系统，而是一个愿意每天打开的入口。</h2>
        <p>食刻最重要的结果不是一次识别有多炫，而是记录动作足够轻，能够一直留在生活里。</p>
      </section>

      <footer className="projectDetailFooter">
        <a href="#top">食刻<span>.</span><small>回到页面顶部</small></a>
        <a href={releasesUrl} className="projectNext" target="_blank" rel="noreferrer">
          <small>GITHUB / RELEASES</small><strong>下载 v{version}</strong><span aria-hidden="true">→</span>
        </a>
      </footer>
    </main>
  );
}
