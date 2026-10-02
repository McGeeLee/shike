# 食刻安全性与逻辑审查

修复状态：本报告记录修复前的问题与证据。9 项代码问题及 6 个依赖包的公告命中已修复并完成回归验证，详见[修复说明](https://github.com/McGeeLee/shike/blob/main/docs/security-fixes-2026-10-02.md)。

审查日期：2026-10-02（Asia/Shanghai）。审查提交：`cab076f83d39b3b190c037fe183eac899c060369`；通过 `git ls-remote origin HEAD` 确认初次审查时与 GitHub HEAD 一致。初次审查仅新增报告及依赖公告原始结果；下文行号、复现结果和验证范围均对应修复前的代码。

确认 9 处代码问题：1 项 P1、7 项 P2、1 项 P3；另有 6 个网站依赖包名命中安全公告。P1 表示应优先修复的严重功能问题，P2 表示应修复的安全或可靠性问题，P3 表示较低频边界问题。这是修复优先级，不等同于安全公告的 CVSS 等级。

| 编号 | 优先级 | 问题 | 主要影响 |
| --- | --- | --- | --- |
| F1 | P1 | Android 7.0–8.1 图片尺寸解码结果被误判 | 任何照片都无法进入识别流程 |
| F2 | P2 | 营养数值没有上限，计算结果未检查有限性 | 异常模型响应可生成持久化崩溃记录 |
| F3 | P2 | 旧数据迁移失败仍标记完成 | 旧数据不再自动导入，缺少重试机会 |
| F4 | P2 | 迁移后旧明文 API Key 未清除 | 加密存储之外仍残留明文副本 |
| F5 | P2 | 相机原图缓存没有清理 | 删除记录后原照片仍存在，缓存持续增长 |
| F6 | P2 | 获取模型期间切换服务商继承 loading | 新服务商的获取/测试按钮一直禁用 |
| F7 | P2 | 取消下载后立即重试共享文件与状态 | 旧任务可删除新任务下载成功的 APK |
| F8 | P2 | 重发旧版本无条件设置 latest | 官网回退旧版、正常升级受阻 |
| F9 | P3 | 超过 30 个食物项目被静默截断 | 总热量和营养数据漏记 |

**F1 · Android 7.0–8.1 正常照片全部读取失败**

位置：[ImageProcessor.kt:66](https://github.com/McGeeLee/shike/blob/cab076f83d39b3b190c037fe183eac899c060369/android/app/src/main/java/com/gee/eatapp/image/ImageProcessor.kt#L66)。

`inJustDecodeBounds=true` 时，`BitmapFactory.decodeStream()` 成功也返回 `null`，仅填写尺寸字段。当前代码用 `use { decodeStream(...) } ?: throw` 判断读取是否成功，所以只要进入 `decodeLegacy()` 就一定抛出“无法读取这张图片”。项目 `minSdk=24`，API 24–27 均使用这条路径。此返回值行为由 [Android 官方 API 文档](https://developer.android.com/reference/android/graphics/BitmapFactory.Options#inJustDecodeBounds)确认。

复现：在 Android 7.0、7.1、8.0 或 8.1 上选择正常 JPEG 或拍照，准备阶段始终失败。验证方式为源码控制流和官方 API 语义，本轮未连接设备执行。

建议：先单独判断 `openInputStream()` 是否为空，调用尺寸解码，再判断 `outWidth/outHeight`；不要把返回的空 Bitmap 当成失败。补 API 24/27 上的图片准备测试。

**F2 · 超大有限营养值可形成反复崩溃的记录**

入口：[FoodAnalysisClient.kt:562](https://github.com/McGeeLee/shike/blob/cab076f83d39b3b190c037fe183eac899c060369/android/app/src/main/java/com/gee/eatapp/network/FoodAnalysisClient.kt#L562)；计算：[AppModels.kt:301](https://github.com/McGeeLee/shike/blob/cab076f83d39b3b190c037fe183eac899c060369/android/app/src/main/java/com/gee/eatapp/data/AppModels.kt#L301)；崩溃点：[ShikeApp.kt:798](https://github.com/McGeeLee/shike/blob/cab076f83d39b3b190c037fe183eac899c060369/android/app/src/main/java/com/gee/eatapp/ui/ShikeApp.kt#L798)。

`safeNumber()` 和 `safeNutritionValue()` 只排除非有限值、负值，不限制数值上限。模型或自定义接口返回单项 `protein_g=1e308` 时，它是有限 Double，因此被接受，也可序列化保存。读取记录仍保留该数值。蛋白质热量乘以 4 后成为 `Infinity`，占比计算变成 `Infinity / Infinity = NaN`，界面计算百分比时 `roundToInt()` 抛异常。

复现输入示例：

```json
{"is_food":true,"foods":[{"name":"测试食物","portion":"一份","calories":200,"protein_g":1e308,"carbs_g":20,"fat_g":8}],"total_calories":200,"confidence":"high","notes":""}
```

验证：编译执行未修改的实际 `AppModels.kt`，并用原样提取的响应归一化函数确认该数值被接受。计算输出：

```text
proteinCalories=Infinity; totalCalories=Infinity; share=NaN
UI failure=java.lang.IllegalArgumentException: Cannot round NaN value.
```

影响前提是服务商返回异常或恶意数据，用户保存结果；本轮未直接调用付费模型或写入真实用户记录。当天记录页以及包含该数据的统计可能反复崩溃。

建议：在响应、存储读写入口限制合理数值范围；对求和、乘法和占比结果检查有限性，界面遇到异常值应有降级处理。热量的 Int 求和也需防止溢出。

**F3 · 迁移失败后永久停止自动迁移**

位置：[ShikeRepository.kt:120](https://github.com/McGeeLee/shike/blob/cab076f83d39b3b190c037fe183eac899c060369/android/app/src/main/java/com/gee/eatapp/data/ShikeRepository.kt#L120)。

`importLegacyData()` 丢弃 `runCatching` 的结果，无论 JSON 解析、Keystore 写入或记录持久化是否失败，随后都设置 `legacy_webview_migrated_v1=true`。下一次启动不再读取旧数据。旧数据通常仍在 WebView 存储中，但正常应用流程无法再次导入。

确定性复现步骤：先传入 `importLegacyData("{invalid")`，再传入正常导出 JSON；第二次在完成标记检查处直接返回 0。实际升级时，存储空间不足等错误也会进入这条分支。此项依据源码确定，未在设备上注入存储故障。

建议：全部数据确认持久化成功后才设置完成标记；保留失败原因和重试入口，并保证部分成功后的重试不会重复导入。

**F4 · 迁移后仍保留旧明文密钥**

位置：[LegacyDataMigrator.kt:48](https://github.com/McGeeLee/shike/blob/cab076f83d39b3b190c037fe183eac899c060369/android/app/src/main/java/com/gee/eatapp/data/LegacyDataMigrator.kt#L48)，回调完成后仅在第 24 行销毁 WebView。

迁移脚本读取旧 `localStorage['eat-keys']`，将密钥写入 Keystore 加密存储，但没有移除旧键。历史版本的前端确实把密钥以明文 JSON 保存于该键。销毁 WebView 对象不会清除持久化 Web Storage；[WebView.destroy 文档](https://developer.android.com/reference/android/webkit/WebView#destroy())描述的是实例销毁。

复现步骤：旧版本保存测试密钥后升级，再读取 `https://localhost` 同源的 localStorage，旧 `eat-keys` 仍存在。本轮通过历史源码和当前迁移路径确认，未执行设备升级。

风险边界：需要攻击者已经能读取应用私有数据，例如设备被攻破或应用上下文被利用；没有证据说明普通其他 App 可直接读取。问题在于升级用户没有获得承诺的完整静态加密保护。

建议：安全存储成功并确认持久化后清除旧密钥键；失败时保留恢复源。不要在确认前清除，也不要为清理密钥而提前删除尚未导入的旧日志。

**F5 · 相机原图在记录删除后仍残留**

位置：[ShikeApp.kt:157](https://github.com/McGeeLee/shike/blob/cab076f83d39b3b190c037fe183eac899c060369/android/app/src/main/java/com/gee/eatapp/ui/ShikeApp.kt#L157)；创建原图：[ShikeApp.kt:1743](https://github.com/McGeeLee/shike/blob/cab076f83d39b3b190c037fe183eac899c060369/android/app/src/main/java/com/gee/eatapp/ui/ShikeApp.kt#L1743)。

每次拍照创建 `cache/images/meal_*.jpg`，回调丢弃 URI 后没有清理文件。图片压缩、取消保存和删除记录都不删除这个源文件；删除记录仅清除另一个归档 JPEG。与 README 的“不保留相机原始大图”承诺不一致。

复现步骤：拍照保存、删除记录并结束撤销，检查调试版本的 `cache/images`，原图仍存在；取消拍摄也可能留下临时文件。依据是文件创建与全仓清理路径审查，未执行设备复现。系统可能回收缓存，但 [Android 官方文档](https://developer.android.com/training/data-storage/app-specific)明确要求应用管理自身缓存。

建议：处理结束或取消后清理本应用创建的相机文件，并清理因进程中断而遗留的捕获文件。仅删除本应用生成的捕获 URI，不删除 Photo Picker 选择的用户原照片。

**F6 · 请求模型列表时切换服务商导致按钮卡住**

位置：[ShikeViewModel.kt:292](https://github.com/McGeeLee/shike/blob/cab076f83d39b3b190c037fe183eac899c060369/android/app/src/main/java/com/gee/eatapp/ui/ShikeViewModel.kt#L292)；旧回调过滤：[ShikeViewModel.kt:457](https://github.com/McGeeLee/shike/blob/cab076f83d39b3b190c037fe183eac899c060369/android/app/src/main/java/com/gee/eatapp/ui/ShikeViewModel.kt#L457)。

获取模型把 `isLoading` 置为 true；服务商下拉框在加载期间仍可操作。`selectProvider()` 复制旧 draft，没有重置 `isLoading`。原请求完成时，因 provider 不同直接返回，也不重置。新服务商的“自动获取模型”和“测试连接”均一直禁用，关闭后重新打开设置才恢复。

复现：服务商 A 点击获取模型，在请求完成前切换为 B，等待 A 返回。用源码原样提取的 `selectProvider/discoverModels/updateDraft` 方法、真实 `AppModels.kt` 和可控异步客户端桩运行，输出：

```text
After switch: provider=claude, isLoading=true
After old response: provider=claude, isLoading=true, status=IDLE
```

建议：为模型发现保存 Job；切换服务商或关闭设置时取消并重置加载状态。回调校验请求 ID、配置或凭据版本，避免关闭后重开以及 A→B→A 时旧请求覆盖新状态。

**F7 · 取消更新后立即重试，旧任务会删除新 APK**

位置：[AppUpdateClient.kt:93](https://github.com/McGeeLee/shike/blob/cab076f83d39b3b190c037fe183eac899c060369/android/app/src/main/java/com/gee/eatapp/update/AppUpdateClient.kt#L93)；取消入口：[ShikeViewModel.kt:428](https://github.com/McGeeLee/shike/blob/cab076f83d39b3b190c037fe183eac899c060369/android/app/src/main/java/com/gee/eatapp/ui/ShikeViewModel.kt#L428)。

取消只调用 `cancel()`，马上解除下载状态；阻塞的 `input.read()` 可能稍后才返回。同一版本的两个任务共享 `.part` 和最终 APK 路径。新任务成功后，旧任务因取消进入 `catch`，删除新任务的目标文件。旧任务的状态回调与 `finally` 还可能覆盖新任务状态并清空新 Job 引用。

验证：编译运行未修改的完整 `AppUpdateClient.kt`，用 Android API 与 HTTPS 输入流测试桩控制“首任务阻塞→取消→第二任务完成→首任务退出”的顺序，实际输出：

```text
second completed: returned file exists=true, bytes=27
first cancellation cleanup finished: second result exists=false
```

该测试证明文件竞态，不代表真实 APK 签名绕过；PackageManager 在测试中使用桩，正常代码仍有签名验证。

建议：取消后等待旧任务结束再允许重试；临时文件使用任务独立路径，清理和界面状态更新必须绑定所属任务，旧任务不能清理新任务文件或状态。

**F8 · 重发旧标签会覆盖 latest，并造成版本编码倒挂**

位置：[release.yml:140](https://github.com/McGeeLee/shike/blob/cab076f83d39b3b190c037fe183eac899c060369/.github/workflows/release.yml#L140)；版本编码生成：[release.yml:48](https://github.com/McGeeLee/shike/blob/cab076f83d39b3b190c037fe183eac899c060369/.github/workflows/release.yml#L48)。

工作流允许手动重发已有旧标签，用当前 `run_number + 6` 生成新的 `versionCode`，覆盖既有 APK，且无条件执行 `--latest`。[GitHub CLI 文档](https://cli.github.com/manual/gh_release_edit)确认此参数显式设置 latest。按标签分组的 concurrency 也允许两个不同版本同时发布。

复现流程：先发布 `v2.4.0`，再手动发布 `v2.3.1`；旧版本会成为 latest，且旧代码的 `versionCode` 可能高于已发布的 `v2.4.0`。网站下载链接跟随 latest。App 只查询 `/releases/latest`，按 `versionName` 比较，可能错过真正的新版本；手动安装旧版高编码 APK 后，先前新版 APK 的编码较低，正常安装升级会失败。两个版本并发时，较旧任务最后完成也会覆盖 latest。

本轮通过发布脚本、网站和更新代码联合确认，没有在仓库实际触发发布。

建议：只有更高的语义版本才能更新 latest；重发旧版保持 latest 不变。协调跨版本发布的最终写入步骤，避免竞态；避免用新 run_number 重写旧版本发行物，明确不可变发行物与版本编码规则。

**F9 · 超过 30 项时静默漏记营养数据**

位置：[FoodAnalysisClient.kt:494](https://github.com/McGeeLee/shike/blob/cab076f83d39b3b190c037fe183eac899c060369/android/app/src/main/java/com/gee/eatapp/network/FoodAnalysisClient.kt#L494)。

响应 schema 没有 `maxItems`，但客户端只读取前 30 项，并用截断后的项目热量和覆盖服务端合计。31 项各 100 千卡、模型总量 3100 的响应会成为 30 项、3000 千卡，且无提示即可保存。

原样提取归一化函数，使用 JSON 测试桩验证输出：`foods=30; totalCalories=3000; originalTotal=3100`。这是低频完整性边界，不是远程执行漏洞。

建议：超限明确拒绝并提示重试，或完整汇总后再限制展示；schema 与客户端限制应一致，不能静默丢弃贡献热量的项目。

**网站依赖公告匹配及适用范围**

对锁文件中 557 个不同 npm 包名/版本集合调用 npm 官方 bulk advisory API。原始结果见 [security-audit-npm-2026-10-02.json](https://github.com/McGeeLee/shike/blob/main/docs/security-audit-npm-2026-10-02.json)。六个包名命中公告；这是版本匹配结果，不能直接等同于当前站点有六个可利用漏洞。

| 包 | 锁定版本 | 公告最高等级 | 覆盖本轮公告所需的版本下限 |
| --- | --- | --- | --- |
| next | 16.3.2 | Critical | 16.3.6 |
| sharp | 0.35.2、0.35.3 | High | 0.35.4 |
| brace-expansion | 1.1.18、5.0.9 | High | 各分支 1.1.21、5.0.12 |
| fast-uri | 3.1.5 | High | 3.1.8 |
| js-yaml | 4.3.1 | High | 4.3.2 |
| undici | 7.29.0 | High | 7.29.1 |

Next.js 命中三条严重公告：[Windows 服务端 RCE](https://github.com/vercel/next.js/security/advisories/GHSA-p293-qw3h-jr36)、[AVIF 图片优化 RCE](https://github.com/vercel/next.js/security/advisories/GHSA-2xp9-vwfh-vxw4)、[Node.js next/og ImageResponse RCE](https://github.com/vercel/next.js/security/advisories/GHSA-vcvr-r3jv-pc5j)。这些分别依赖 Windows 服务端运行、图片优化处理 AVIF、或向 Node.js ImageResponse 的 SVG 传入攻击者控制值。

当前 [next.config.ts](https://github.com/McGeeLee/shike/blob/cab076f83d39b3b190c037fe183eac899c060369/site/next.config.ts#L4) 配置 `output:'export'`，README 描述 Cloudflare Pages 部署 `out` 静态文件；网站没有 `next/image`、`next/og`、ImageResponse、API 路由或用户上传处理，OG 是固定 PNG。依据上述配置，本轮未找到这些 Next.js 公告在声明的生产部署中的触发入口；没有实际核查线上托管配置。开发/预览另有 vinext 服务链，不能把静态部署的结论推广到所有运行方式。

依赖用途：两份 sharp 分别来自 Next.js 和 Miniflare；brace-expansion、js-yaml 来自 ESLint 等配置/文件匹配工具；fast-uri 来自 Webpack/Ajv 的 schema 处理；undici 来自 Cloudflare/Wrangler/Miniflare 工具链。这些 npm 包不参与 Android APK 构建。

其余包涉及图片解码、URI 解析、展开/解析拒绝服务以及 HTTP/WebSocket 等公告。需要结合构建工具或服务端实际处理的不可信输入判断；当前审查未确认公网触发路径。建议升级直接依赖并重新生成锁文件，通过兼容的上游更新处理传递依赖，再跑审计、网站 Lint 与静态构建。不要用强制 audit fix 跳过兼容性验证。以上版本下限只覆盖本轮返回公告，不是永久安全保证。

**验证结果与边界**

- `./gradlew testDebugUnitTest --rerun --no-build-cache --offline --no-daemon`：19 个单元测试实际重跑通过，0 失败、0 错误。
- `./gradlew testDebugUnitTest lintDebug --offline --no-daemon`：构建成功，Lint 0 错误、10 个警告；现有检查未覆盖本报告问题。
- Kotlin 验证：实际营养模型计算；源码提取的响应归一化、设置切换；完整更新客户端的取消下载竞态。涉及 Android/网络的部分使用桩，已分别说明。
- 依赖检查：npm 官方公告服务对网站锁定版本进行匹配；未安装依赖，未跑网站构建。
- 当前无连接的 Android 设备，未执行真机或本轮设备 UI 测试；未调用用户付费模型、发布 Release 或测试线上攻击。
- 未完成 Android 传递依赖的全面 CVE 检索、GitHub 权限/Secrets 配置审计、完整 Git 历史秘密扫描，也未检查实际线上部署。当前源码常见密钥格式扫描未发现匹配；这不证明不存在其他格式的凭据。

已核查的有效边界包括：模型请求要求 HTTPS、禁用重定向、响应大小限制；API Key 的新存储使用 Keystore；备份/设备迁移排除应用数据；照片归档限制文件名且 FileProvider 不导出；更新有来源、大小、SHA-256、包名、版本和签名检查。本轮没有确认 API Key 通过模型请求重定向泄漏、照片路径遍历或更新签名绕过。

建议先修 F1、F2、F3，避免基本功能不可用、持续崩溃和升级数据导入受阻；同时修 F4/F5 的隐私残留、更新网站依赖，然后处理异步状态、下载与发布流程。
