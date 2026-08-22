import { ShikeDemo } from './shike-demo';
import { SiteHeader } from './site-controls';

const pageUrl = 'https://shike.mcgeelee.com/';
const portfolioUrl = 'https://mcgeelee.com/';
const repositoryUrl = 'https://github.com/McGeeLee/shike';
const releaseUrl =
  'https://raw.githubusercontent.com/McGeeLee/mcgeelee-portfolio/main/public/downloads/shike-v1.0.0.apk';

const structuredData = {
  '@context': 'https://schema.org',
  '@type': 'SoftwareApplication',
  '@id': `${pageUrl}#app`,
  name: '食刻 Shike',
  url: pageUrl,
  description: '通过视觉模型识别食物、估算热量与三大营养素，并在本地保存记录的 Android 应用。',
  applicationCategory: 'HealthApplication',
  operatingSystem: 'Android',
  downloadUrl: releaseUrl,
  codeRepository: repositoryUrl,
  author: {
    '@type': 'Person',
    name: 'McGee Lee',
    url: portfolioUrl,
  },
};

export default function Home() {
  return (
    <main className="projectDetail projectDetail-shike">
      <SiteHeader />
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{
          __html: JSON.stringify(structuredData).replace(/</g, '\\u003c'),
        }}
      />

      <nav className="projectDetailNav" aria-label="项目页导航">
        <a href={portfolioUrl}>← 返回所有作品</a>
        <span>WORK 01 / DAILY PRODUCT</span>
      </nav>

      <section className="projectDetailHero" aria-labelledby="project-title">
        <article className="projectDetailHeroCopy">
          <span className="sectionCode">WORK 01 / DAILY PRODUCT</span>
          <h1 id="project-title"><span>食刻</span><span>Shike</span></h1>
          <p className="projectDetailKicker">拍张照片，记下这一餐。</p>
          <p className="projectDetailDescription">
            一款从真实记录需求出发的 Android 饮食应用。它把拍照、视觉识别、营养估算和当天记录连成一个足够轻的日常动作。
          </p>
          <ul className="projectDetailTags" aria-label="项目技术与特性">
            <li>视觉模型</li><li>Android App</li><li>本地保存</li><li>图片压缩</li>
          </ul>
          <div className="projectDetailActions">
            <a className="actionLink actionLinkPrimary" href={releaseUrl} download="shike-v1.0.0.apk">
              下载正式版 1.0.0 <span aria-hidden="true">↓</span>
            </a>
            <a className="actionLink actionLinkSecondary" href={repositoryUrl} target="_blank" rel="noreferrer">
              查看源码 <span aria-hidden="true">↗</span>
            </a>
          </div>
        </article>

        <div className="projectDetailVisual">
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
          <div><dt>FORMAT</dt><dd><strong>Android</strong><span>独立安装应用</span></dd></div>
          <div><dt>RELEASE</dt><dd><strong>1.0.0</strong><span>正式签名版本</span></dd></div>
          <div><dt>REAL USE</dt><dd><strong>半年</strong><span>持续放进日常使用</span></dd></div>
        </dl>
      </section>

      <section className="projectProcess" aria-labelledby="project-process-title">
        <header>
          <span className="sectionCode">HOW IT WORKS</span>
          <h2 id="project-process-title">三步，把一餐留下来。</h2>
          <p>交互尽量少，信息保持够用；模型负责理解照片，应用负责把结果变成可以长期回看的记录。</p>
        </header>
        <ol className="projectStepGrid">
          <li><span>01</span><small>CAPTURE</small><strong>拍下食物</strong><p>从相机进入记录流程，并先压缩图片，减少不必要的传输与等待。</p></li>
          <li><span>02</span><small>UNDERSTAND</small><strong>识别与估算</strong><p>视觉模型识别餐食内容，给出热量、蛋白质、碳水和脂肪估算。</p></li>
          <li><span>03</span><small>REMEMBER</small><strong>留在当天</strong><p>把结果保存到本地日历式记录中，让趋势来自真实使用而不是一次演示。</p></li>
        </ol>
      </section>

      <section className="projectClosing" aria-labelledby="project-closing-title">
        <span className="sectionCode">WHAT MATTERS</span>
        <h2 id="project-closing-title">不是更复杂的健康系统，而是一个愿意每天打开的入口。</h2>
        <p>食刻最重要的结果不是一次识别有多炫，而是记录动作足够轻，能在半年之后仍然留在生活里。</p>
      </section>

      <footer className="projectDetailFooter">
        <a href={portfolioUrl}>McGeeLee<span>.</span> <small>返回作品集</small></a>
        <a className="projectNext" href="https://mcgeelee.com/work/zako/">
          <small>NEXT / WORK 02</small>
          <strong>Zako</strong>
          <span aria-hidden="true">→</span>
        </a>
      </footer>
    </main>
  );
}
