# Third-party data and assets

The app's MIT license does not replace the following third-party notices.

## Frosted glass rendering

- Library: [chrisbanes/haze](https://github.com/chrisbanes/haze/tree/1.1.1), version `1.1.1`.
- Copyright Christopher Banes and the Haze project contributors.
- License: [Apache-2.0](KAgenda/app/src/main/assets/licenses/haze-Apache-2.0.txt).

The app uses Haze for background-only blur with separate scenery and page sources. Its navigation shape and spring motion are implemented in this repository. Haze 1.1.1 matches the app's Kotlin 2.0 / Compose 1.7 toolchain; older Android versions use the library's material fallback.

## Local language-model runtime and optional weights

- Runtime: [ggml-org/llama.cpp](https://github.com/ggml-org/llama.cpp), commit `a7a98e0fffed794396b3fbad4dcdbbc184963645` (b6500).
- Source archive SHA-256: `4d78e6aa4a9124b58dff994416525294fbab2990fe905a640d4cbd26bf563a31`.
- Copyright (c) 2023-2024 The ggml authors. [Bundled MIT notice](KAgenda/app/src/main/assets/licenses/llama.cpp-MIT.txt).
- Optional models: Qwen3 by the Qwen team, [0.6B](https://huggingface.co/Qwen/Qwen3-0.6B) and [1.7B](https://huggingface.co/Qwen/Qwen3-1.7B), Apache-2.0. Q4_K_M conversions are published by [Unsloth](https://huggingface.co/unsloth).
- The app contains download metadata only. Fixed conversion revisions, exact file sizes and SHA-256 digests are in `data/LocalAiModels.kt`. No model weights are distributed inside the APK or user-data backups. Downloaded weights retain their original license.

## Community academic portal adapters

- Source: [ShiGuangSchedule/shiguang_warehouse](https://github.com/ShiGuangSchedule/shiguang_warehouse)
- Pinned commit: `c4a35feb061a3f6db2d6c122927083c067c61309`
- Copyright (c) 2025 星河欲转, with individual adapter maintainers credited in the bundled index and original source comments.
- License: [original MIT notice](KAgenda/app/src/main/assets/licenses/shiguang-warehouse-MIT.txt)
- [Original project README](KAgenda/app/src/main/assets/licenses/shiguang-warehouse-README.md)

The bundle contains 254 academic adapter groups, 278 dedicated entry points, four general adapters and 272 distinct, unchanged JavaScript files. Checksums, pinned file URLs and per-file Git modification dates are in `assets/shiguang/provenance.json`. General adapters are indexed separately in `general.json`; unrelated global tools are excluded. The host implements the public bridge protocol independently; no code from the upstream Android application is included. Scripts run only after the user starts extraction in the visible academic browser. Courses, semester dates and time slots are reviewed before saving. Seven entry points that declare combined schedules are currently rejected; community availability is not a claim of successful account testing at every institution.

## General and historical academic parsers

- Source: [Matey-ace/wakeup-course-schedule](https://github.com/Matey-ace/wakeup-course-schedule), commit `43631a54cb70ceb2d983a4d646022d84352c9d85` (snapshot 2026-08-29).
- Original work: **YZune / WakeUp课程表 3.612**, [WakeupSchedule_Kotlin](https://github.com/YZune/WakeupSchedule_Kotlin). The snapshot date does not imply a recent rewrite or current validation of these inherited parsers or URLs.
- License: [Apache-2.0](KAgenda/app/src/main/assets/licenses/wakeup-Apache-2.0.txt); [upstream NOTICE](KAgenda/app/src/main/assets/licenses/wakeup-NOTICE.txt).
- Adapted files retain their original source URL and author in `data/academic/wakeup/`. Changes: namespace relocation, modern jsoup null checks, strict missing-week/section checks, parity reset, single-section handling, and removal of upstream Android persistence from the base parser. The KAgenda facade validates every emitted row, processes every URP response group, and stages output for review.
- `assets/academic/wakeup-provenance.json` records original paths, original SHA-256 hashes and modified-file hashes. The source generator is `scripts/vendor_academic_sources.py`.
- Fifteen parser types are exposed. The copied JNU source is retained for provenance only and is not exposed, because it assumes weeks 1–18 and merges by course name across days. Historical school hints are exact-name matches only and remain unverified; no URLs are guessed from school names.
- HTML parsing dependency: [jhy/jsoup](https://github.com/jhy/jsoup), `1.23.2`, Copyright Jonathan Hedley, [MIT notice](KAgenda/app/src/main/assets/licenses/jsoup-MIT.txt).
- JSON compatibility dependency: [google/gson](https://github.com/google/gson/tree/gson-parent-2.11.0), `2.11.0`, Copyright The Gson Authors, [Apache-2.0](KAgenda/app/src/main/assets/licenses/gson-Apache-2.0.txt).

No implementation code from the GPL-3.0 applications Sleepy or Dawn-Course is included. `baoozak/timetable` was reviewed but not bundled: several providers contain placeholder JSON parsing or inferred default positions, and directory system assignments were not sufficiently supported to treat as current adapters.

School-by-school priority and source inventory: [coverage report](KAgenda/docs/academic-import-coverage.md), generated with `python KAgenda/scripts/report_academic_coverage.py`. All 3,196 official institutions can attempt general import; this is not a claim that every institution has an implemented, current school-specific adapter.

HTTP portal access retains the existing explicit-host policy. `scripts/extend_academic_network_config.py` adds the pinned sources' HTTP hosts while retaining existing school/SSO hosts; the base policy still denies cleartext. Unlisted manually entered HTTP hosts receive an explicit message. Only the debug resource overlay allows the loopback server used in emulator checks. No TLS trust override is added.

## Additional school-specific ports

Fifteen entry points for thirteen schools are adapted from ten pinned projects. Full per-file source URLs, upstream hashes/dates, local hashes, supporting sources and changes are in [school-provenance.json](KAgenda/app/src/main/assets/academic/school-provenance.json). These ports use the existing visible WebView and native preview/save protocol. Their source dates are not claims of school-account validation.

| Project | License notice |
|---|---|
| [WheretoSleepinNJU/NJU-Class-Shedule-Flutter](https://github.com/WheretoSleepinNJU/NJU-Class-Shedule-Flutter) | [Apache-2.0](KAgenda/app/src/main/assets/licenses/single-school-NJU-Apache-2.0.txt) |
| [LuBanQAQ/dnui-wakeup-export](https://github.com/LuBanQAQ/dnui-wakeup-export) | [MIT](KAgenda/app/src/main/assets/licenses/single-school-DNUI-MIT.txt) |
| [west2-online/jwch](https://github.com/west2-online/jwch) | [Apache-2.0](KAgenda/app/src/main/assets/licenses/single-school-FZU-Apache-2.0.txt) |
| [Jnove/ZJUCoursetrace](https://github.com/Jnove/ZJUCoursetrace) | [MIT](KAgenda/app/src/main/assets/licenses/single-school-ZJU-MIT.txt) |
| [SLctfTeam/USST-Lecture-Table-Calendar](https://github.com/SLctfTeam/USST-Lecture-Table-Calendar) | [MIT](KAgenda/app/src/main/assets/licenses/single-school-USST-MIT.txt) |
| [drfccv/ZFJW-backend](https://github.com/drfccv/ZFJW-backend) | [MIT](KAgenda/app/src/main/assets/licenses/single-school-ZFJW-MIT.txt) |
| [suse-edu-cn/SUSE-OAA-APP](https://github.com/suse-edu-cn/SUSE-OAA-APP) | [MIT](KAgenda/app/src/main/assets/licenses/single-school-SUSE-MIT.txt) |
| [sceneeeee/xjtlu-timetable-bookmarklet](https://github.com/sceneeeee/xjtlu-timetable-bookmarklet) | [MIT](KAgenda/app/src/main/assets/licenses/single-school-XJTLU-MIT.txt) |
| [XFY9326/Schedule](https://github.com/XFY9326/Schedule) | [Apache-2.0](KAgenda/app/src/main/assets/licenses/single-school-XFY-Apache-2.0.txt) |
| [LingNc/stnet_syllabus](https://github.com/LingNc/stnet_syllabus) | [MIT](KAgenda/app/src/main/assets/licenses/single-school-ZZULI-MIT.txt) |

The shared Zhengfang port preserves all four projects' complete MIT notices in its [combined notice](KAgenda/app/src/main/assets/licenses/single-school-ZF-bundle-MIT.txt). No adopted project had a separate NOTICE file in its pinned tree. The NJU port excludes the THU script with unresolved upstream licensing and fixed-date assumptions. XFY is used only for the explicit-weekday SCUJCC Table1 layout. Unscheduled/full-week activities are disclosed rather than assigned invented periods; unsupported layouts stop extraction. XJTLU clock times become reviewed time slots, with no fixed start Monday or final teaching week. No source project's server code, account storage or account forwarding is included.

Run `node KAgenda/scripts/test_single_school_adapters.cjs` and `python KAgenda/scripts/verify_school_assets.py`. Emulator runner `com.kstudio.agenda.SingleSchoolDeviceRunner` checks production JS bridge/runner, real Chromium DOM and native payload conversion with synthetic data and no school requests. [Research decisions](KAgenda/docs/single-school-research/README.md) and [validation](KAgenda/docs/single-school-research/validation.md) distinguish implemented ports from real-account validation (zero).

## Zhejiang undergraduate additions (2026-10-20)

The eight ports in `EXT/zhejiang.js` adapt the following pinned source mappings;
the complete original license/NOTICE texts are retained in the app's
[combined notice](KAgenda/app/src/main/assets/licenses/zhejiang-school-bundle.txt).
Per-file revisions, dates, SHA-256 values and local modifications are in
`academic/school-provenance.json` and the [implementation record](KAgenda/docs/zhejiang-undergraduate-research/implementation.md).

| Source | License / scope |
| --- | --- |
| [wow-233/niwin-assistant](https://github.com/wow-233/niwin-assistant) | Apache-2.0; WZU request/field mapping. Original NOTICE and inherited Shiguang MIT text retained. |
| [Yingye66/zjgsu-campus-mcp-open-source](https://github.com/Yingye66/zjgsu-campus-mcp-open-source) | MIT; ZJGSU web timetable and module activation. |
| [drfccv/ZFJW-backend](https://github.com/drfccv/ZFJW-backend) | MIT; Zhejiang A&F University Jiyang College configuration and request fields. |
| [zhullyb/zf2ics](https://github.com/zhullyb/zf2ics) | MIT; ZJUT timetable request and `jcor` periods. |
| [my832/dongfang-timetable](https://github.com/my832/dongfang-timetable) | Custom permissive grant, identified as `LicenseRef-Dongfang-Permissive`; original text retained verbatim rather than relabeled MIT. Dongfang grid structure and school URL. |
| [wirano/CJLU_ClassTable](https://github.com/wirano/CJLU_ClassTable) | MIT; CJLU personal timetable endpoint/fields; explicit selected term and parity repair. |
| [Mioxiya/AISchedule-shufe-zj](https://github.com/Mioxiya/AISchedule-shufe-zj) | MIT; SUFE Zhejiang same-origin direct/WebVPN request, without its fixed old term. |
| [ShiGuangSchedule/shiguang_warehouse](https://github.com/ShiGuangSchedule/shiguang_warehouse) | MIT; Wanli timetable fields and portal, without the unsupported combined-time protocol. |

The existing `EXT/zhengfang.js` ZJU request now sends the complete academic-year
label, following [Harry-Linner/CampusOS](https://github.com/Harry-Linner/CampusOS).
Its original MIT text is included in `single-school-ZF-bundle-MIT.txt`, with the
pinned source file and hash retained in the shared port provenance.

KAgenda uses manual browser login and same-origin requests. The ports require an
explicit selected semester, weeks and periods, reject incomplete responses, and
ask before omitting unsupported arrangements. Fixed calendar dates, time presets,
account storage, automatic form filling and third-party servers are not ported.
Synthetic and emulator validation does not establish real-account availability.

## Jiangsu undergraduate school ports

`EXT/nuaa.js` independently implements the undergraduate EAMS protocol documented
by [shiywhh/i-niuaa](https://github.com/shiywhh/i-niuaa) (MIT), pinned at
`2acccb997836be11c220bf3e91e25a249fdf0d08`. DeepSeek Harness prepared an independent
draft; KAgenda reviewed and corrected roster completeness, explicit calendar
length, disjoint periods and final full-semester snapshot checks. No upstream
course-selection code, period-time presets or automatic login is included.
The original MIT notice, file hashes and dates are included in the Jiangsu
license bundle and `academic/school-provenance.json`. Real-account validation
is tracked separately from synthetic regression checks.

`EXT/jiangsu.js` adapts the personal timetable request and field mappings from
[mico-v/usts-cli](https://github.com/mico-v/usts-cli) (MIT, Suzhou University of
Science and Technology) and [Ye111ow/cumt-schedule-desktop](https://github.com/Ye111ow/cumt-schedule-desktop)
(MIT, China University of Mining and Technology in Xuzhou),
[GiuseppeLR/njtech_timetable](https://github.com/GiuseppeLR/njtech_timetable)
(Apache-2.0, Nanjing Tech University), and
[Albiehao/cumtxh-jwxt-api](https://github.com/Albiehao/cumtxh-jwxt-api)
(MIT, the separate Xuhai College).
`EXT/njucm.js` adapts the DOM extraction and complete-term audit from
[Miao-30/NJUCM-weekdesk](https://github.com/Miao-30/NJUCM-weekdesk)
(MIT, Nanjing University of Chinese Medicine). It reads the page-provided
semester start date and twelve period times and audits all teaching weeks with
experiments, rejecting stale, incomplete or changed views before staging data.
`EXT/nuist.js` adapts the scheduled-course request and response fields from
[zyc-816/NUISTTable](https://github.com/zyc-816/NUISTTable) (MIT, Nanjing
University of Information Science and Technology). It queries every week of an
explicit calendar range, rechecks the server term and start date, and rejects
partial results. The source's fixed twenty-week loop and stored cookies are
not ported. This route covers scheduled courses only.
`EXT/seu.js` independently combines the SEU structured course/calendar protocol
from [Haa1024/SEU-Timetable](https://github.com/Haa1024/SEU-Timetable) (MIT) with
the explicit semester-list GET protocol from
[sakimidare/SEUTimetable](https://github.com/sakimidare/SEUTimetable)
(Apache-2.0). It reads a full selected term without a week filter, uses the
structured period/week fields instead of repeated `YPSJDD` text, and rechecks
the snapshot and calendar before staging. The earlier SEU entry is retained.
All original copyright and license texts are retained verbatim in
[jiangsu-school-bundle.txt](KAgenda/app/src/main/assets/licenses/jiangsu-school-bundle.txt).
Pinned source files, commits, dates, hashes and local changes are recorded in
`academic/school-provenance.json` and the provincial research records.
The USTS personal response uses `sjkList/qsjsz`; CUMT and Xuhai use `kbList/zcd`.
NJTech reads `jcs` and includes the verified `sjkList/jxhjkcList` containers.
KAgenda retains visible school login, explicit semester selection and native
confirmation. Source clock presets and inferred semester dates are not ported.
Synthetic validation does not establish real-account availability.

`EXT/njust.js` independently implements the NJUST undergraduate personal-term
protocol described by [flyShaoyu/NJUSTLocalClassSche](https://github.com/flyShaoyu/NJUSTLocalClassSche)
(Apache-2.0, commit `50299ccab1f36f82c83a4c0c259e1513df702b83`) and
[JirakiRance/NJUST-NannoSchedule](https://github.com/JirakiRance/NJUST-NannoSchedule)
(MIT, commit `32342f7d66e9b5f3ec46a3d736966dd9ede665e2`). DeepSeek assisted
the draft; KAgenda reviewed and corrected the implementation. The adapter uses
the migrated HTTPS school endpoint, explicit server semester and calendar week
count, and audits every grid/detail meeting before staging. Source defaults,
automatic login, stored credentials, inferred periods and silent omissions are
not ported. Both original license texts are retained in the Jiangsu bundle;
fixed files, dates and hashes are recorded in the asset provenance. Real school
account validation remains pending.

`EXT/suda-personal.js` independently implements Suzhou University's WebForms
personal timetable protocol documented by [hlfzsi/Aero_su](https://github.com/hlfzsi/Aero_su)
(Apache-2.0, commit `bd6aa7f2e9596c50c58b0f9af027caec0a89acba`).
DeepSeek assisted an independent parser draft; KAgenda corrected the draft,
added scheduled practice rows, and implemented the visible-login bridge.
Explicit server year/semester selection uses fresh WebForms states and the
source's three-second request interval. A second complete snapshot must match
before staging; missing or unscheduled rows abort the import. Calendar dates,
period clocks and absent practice class identifiers are not fabricated.
The upstream test file was read solely to check table header contracts; its
student timetable fixtures were neither copied nor executed. All bundled
tests use self-authored synthetic pages. The original Apache-2.0 license is
retained in the Jiangsu bundle, with fixed source files, dates and hashes in
the asset provenance. Actual school account validation remains pending.

The NJU undergraduate route in `EXT/nju.js` additionally uses the documented
header and visible-pager contracts from [Fallforgotten/nju-timetable-desktop](https://github.com/Fallforgotten/nju-timetable-desktop)
(MIT, copyright 2026 Fallforgotten, commit
`2380b4fbbf36e79424e0b3ecf3152658b3d4a61d`). KAgenda independently audits
personal-page identity, full academic year/semester, explicit headers, the
complete single-page row total, filters and post-interaction snapshots.
Unexpanded pagination is rejected instead of saving a partial timetable.
The upstream partial-download workflow and personal fixtures are not ported.
The original Apache-2.0 and MIT notices are retained verbatim in
`licenses/nju-personal-bundle.txt`; fixed files, dates and hashes are recorded
in `academic/school-provenance.json`. No upstream code is executed and real
school-account verification remains pending.

`EXT/just-personal.js` independently implements the Jiangsu University of
Science and Technology personal timetable DOM contract from
[lawrencehaohan-coder/suike](https://github.com/lawrencehaohan-coder/suike)
(MIT, copyright 2026 Suike contributors, commit
`99b7dc1f6db54fd9c36afb69ac4d30a724c9ef1b`). DeepSeek drafted the standalone
parser; KAgenda corrected its DOM field handling, teaching-class syntax,
explicit period labels, group-header rowspans and filter protections, and
implemented the visible-login host. Every selected course is audited; unknown
or unscheduled practice data aborts the batch. The calendar week count is
entered explicitly and final snapshots must match after user interaction.
Source clock reuse, inferred dates, partial-row omission and campus assets
are not ported. The original MIT notice is retained in the Jiangsu bundle;
fixed source files, dates and hashes are recorded in the asset provenance.
No upstream code or personal timetable fixtures are executed or used. Actual
school account validation remains pending.

`EXT/wxxy.js` independently implements Wuxi University's personal timetable
POST and decimal `oldzc` week-mask protocol from
[ikliml666/Wxxy-CampusHub](https://github.com/ikliml666/Wxxy-CampusHub), commit
`684c2002cfd9f0ed939f1f926c9ae0b9e7fe3062`. Both the `campus-schedule` and
`campus-auth` packages declare Apache-2.0 in their Cargo manifests; this is a
package-level statement, not a license claim about the entire repository.
The original complete Apache-2.0 text and the package NOTICE, including
XingHeYuZhuan's upstream attribution, are retained in the Jiangsu bundle with
the package declarations. KAgenda copies no Rust models, clock presets,
calendar inference, TGT login or upstream personal/test fixtures. It requires
explicit server term controls, rejects missing or additional unsupported
course rows, and rechecks the complete courses and response identity after
the user's calendar prompt. Source files, dates, hashes and implementation
changes are recorded in the asset provenance. Real school account validation
remains pending.

## School emblems

- Source: [lovefc/china_school_badge](https://github.com/lovefc/china_school_badge)
- Pinned commit: `04f59e07f4b809acc60493324af5ca4aa610153d`
- Author: 封尘 / lovefc
- License: [original Apache-2.0 notice](KAgenda/app/src/main/assets/licenses/school-badges-Apache-2.0.txt)
- [Original project README](KAgenda/app/src/main/assets/licenses/school-badges-README.md)

The original font and a 310-name glyph mapping are bundled with provenance hashes in `assets/catalog/badges-source.json`. Emblems belong to their respective institutions. Institutions missing from this font use their portal-provided site icon after a successful import when available; missing icons retain the existing initial-letter fallback.

## Official institution names

The [Ministry of Education list](https://www.moe.gov.cn/jyb_xxgk/s5743/s5744/202606/t20260618_1441074.html), dated June 17, 2026, contains 2,952 ordinary and 244 adult institutions in mainland China. It excludes Hong Kong, Macao and Taiwan. Source attachment checksums are in `assets/catalog/source.json`. Three community campus entries are retained separately to avoid mapping them to a different main campus.

## Shanghai personal timetable port

The independent KAgenda Tongji personal-report port (`EXT/tongji-personal.js`) uses public protocol references from [gzy31007/TJDesktopTimetable](https://github.com/gzy31007/TJDesktopTimetable/tree/4d79f8c0cedc7c8e6058129e6468d6e3365bfc73) (MIT, Copyright 2026 gzy31007) and Linho1219's [Tongji community adapter](https://github.com/ShiGuangSchedule/shiguang_warehouse/blob/c4a35feb061a3f6db2d6c122927083c067c61309/resources/TONGJI/tongji_01.js) (warehouse MIT, Copyright 2025 星河欲转). Both complete original notices are retained in `KAgenda/app/src/main/assets/licenses/shanghai-tongji-MIT.txt`; fixed paths, file dates, hashes and KAgenda changes are recorded in `academic/school-provenance.json`. The original community entry remains separate.

DSH drafted the new port and synthetic tests; KAgenda reviewed and corrected URL semantics, same-class identity, completeness checks and snapshots after user interaction. No upstream programs or upstream fixtures were executed. Validation uses synthetic data and the emulator's real WebView/production bridge; no Tongji account availability is claimed.

The Shanghai University personal port (`EXT/shu-personal.js`) independently composes KAgenda's strict host with the request, semester-option and row protocol from [zmdld11/SHU-Class-Schedule](https://github.com/zmdld11/SHU-Class-Schedule/tree/0c7090795bc3119e6f4c83ff2569d458eada53b2) (MIT, Copyright 2026 zmdld11). The complete original notice is bundled in `licenses/shanghai-shu-MIT.txt`. It keeps the live server semester code, including 32; it does not copy candidate-term probing, dropping conflicting rows, clocks, themes or upstream fixtures. Provenance records fixed paths, dates and hashes; real school accounts remain unverified.

The SJTU undergraduate personal port (`EXT/sjtu-undergraduate.js`) independently implements the `i.sjtu.edu.cn` protocol documented by [dujiajun/CourseBlock](https://github.com/dujiajun/CourseBlock/tree/20feb37e37b2cd5779a7d3e24ef49983df7bbd5e), MIT, Copyright 2021 Du Jiajun. The complete original notice is bundled in `licenses/shanghai-sjtu-undergraduate-MIT.txt`. It preserves actual teaching-class identifiers and notes; fixed historical calendars, default clocks and upstream samples are not used. The separate postgraduate entry remains available.

The ECNU undergraduate visible-DOM port (`EXT/ecnu-personal.js`) independently implements DOM facts documented by [Taulee/ECNUCourseTableTool-2.0](https://github.com/Taulee/ECNUCourseTableTool-2.0/tree/d3d60512b3c084df8f65154e6fc5b45fedccb1ea), MIT, Copyright 2026 Taulee. The complete original notice is bundled in `licenses/shanghai-ecnu-MIT.txt`. Its acknowledged [older upstream](https://github.com/SJF-ECNU/ECNUCourseTableToolForIOS/tree/530ef2b136bfc78f5a5980e1ec7da455793e3408) declares MIT in its README without a separate license file; no older upstream code, binaries, HTML or personal fixtures are copied. DSH drafted code and synthetic tests; KAgenda corrected multi-course groups, duplicate handling, structured identities, live controls, rowspans, staging checks and the isolated browser test launcher. Fixed source paths, dates, hashes and boundaries are recorded in `academic/school-provenance.json`. School account validation remains pending.

The existing USST MIT protocol port now retains the source's documented campus and course details. Shared KAgenda JS/native conversion preserves teaching-class metadata; equivalent complete course facts retain all distinct detail blocks. Unknown practice branches and missing teacher/room abort USST imports before staging.

## Verification

The independent HFUT EAMS 5.0 personal port (`EXT/hfut-personal.js`) uses public request and response contracts from [Chiu-xaH/HFUT-Schedule](https://github.com/Chiu-xaH/HFUT-Schedule/tree/fb0b0de01bb853de24281e9d0a9727954eb718ab) (Apache-2.0) and [Junpgle/HFUTCampusNet](https://github.com/Junpgle/HFUTCampusNet/tree/9f62dac63c6a200ef328066e02f7722de8407c45) (MIT, Copyright 2026 HFUTCampusNet Contributors). Both complete original licenses are bundled in `licenses/anhui-hfut-bundle.txt`; fixed paths, actual file dates, hashes and changes are recorded in `academic/school-provenance.json`. DSH received public upstream DTOs only for its standalone normalizer draft; KAgenda implemented the host locally and corrected DTO ID types, uninterpreted school indexes, explicit calendar checks and detail preservation. No upstream programs, fixtures, login implementation, credentials, inferred semester codes or fixed clocks are used. Actual school account validation remains separate and pending.

Run `python KAgenda/scripts/verify_school_assets.py`, `node KAgenda/scripts/test_warehouse_bridge.cjs` and `node KAgenda/scripts/test_academic_capture.cjs` from the repository root. After running the Android JVM tests, run `node KAgenda/scripts/test_warehouse_adapters.cjs`. This compiles all 272 native-generated script wrappers and exercises four unchanged adapters twice against synthetic portal responses. JVM tests cover all 15 exposed WakeUp parser types, whole-directory routing and preservation of independent/custom flows. Build device tests with `:app:assembleDebugAndroidTest -Pkagenda.testRunner=com.kstudio.agenda.AcademicImportDeviceRunner`; this separate emulator-only instrumentation entry point exercises production WebView capture, preview, failure protection and explicit saving using synthetic data. These checks do not log into any academic portal or establish account-level availability.

The Wuhu College mobile personal port (`EXT/wuhu-personal.js`, 金智教务) is **original KAgenda implementation**, under the project's MIT license. [skreon/wuhuAiSchedule](https://github.com/skreon/wuhuAiSchedule/tree/06aee566f8f46bebff2d4dad531a28a018b50b17) is an **unlicensed protocol reference only**: no upstream implementation is copied, adapted, packaged or relicensed. The original project MIT text is bundled in `licenses/anhui-wuhu-original-MIT.txt`; it grants no rights to the reference repository. Endpoint/field references, pinned file dates/hashes and this distinction are recorded in `academic/school-provenance.json`. DSH drafted a pure validator from a public protocol-facts document, with neither upstream implementation nor KAgenda source as input. KAgenda authored the WebView host locally and corrected the draft's response-field assumptions, disjoint-week room handling, error and pagination checks, and metadata preservation. All weeks, all teacher names and the school's real time slots are verified twice before staging; unknown practice branches, incomplete data and session failures abort. Synthetic tests do not establish real-account availability.

The separate `academic/school-systems.json` records exact-school vendor facts, fixed source links, original reference-license identifiers and (where available) hashes of unauthenticated official login pages. These records add **labels only**; they do not add importers, change existing support flags, erase historical system evidence or transfer a parent school's route to an independent college. Some references use GPL or have no license; no implementation from those references is copied into these vendor facts. Current account availability remains a separate question.

## Offline pinyin index

- Source: [mozillazg/python-pinyin](https://github.com/mozillazg/python-pinyin), version `0.55.0`
- Copyright (c) 2016 mozillazg, 闲耘
- License: [original MIT notice](KAgenda/app/src/main/assets/licenses/pypinyin-MIT.txt)

Only generated spelling data is bundled; Python and pypinyin are not app runtime dependencies. `scripts/build_school_pinyin.py` generates the school-name index, contextual prefixes and character fallback. Data hashes and generator version are recorded in `assets/catalog/pinyin-source.json`; explicit pronunciation corrections and their sources are in `pinyin-overrides.json`.


## Non-academic timetable imports

- PDF text extraction: [TomRoush/PdfBox-Android](https://github.com/TomRoush/PdfBox-Android), Maven `com.tom-roush:pdfbox-android:2.0.27.0`, tag `v2.0.27.0`, commit `45da92629dad5b3c9887eceefecc89a1423f5457`. Original [license](KAgenda/app/src/main/assets/licenses/pdfbox-android-Apache-2.0.txt) and [NOTICE](KAgenda/app/src/main/assets/licenses/pdfbox-android-NOTICE.txt) are bundled unchanged. Optional JPX image decoding is not included; imports extract text.
- Chinese screenshot text recognition: Google ML Kit `com.google.mlkit:text-recognition-chinese:16.0.1`, bundled model. This is a Google SDK governed by the [ML Kit terms](https://developers.google.com/ml-kit/terms), not a claim that the model or SDK is open source. The bundled recognizer runs on-device and avoids requiring a first-use Google Play model download.
- HTML and OOXML structural extraction reuses the already bundled jsoup dependency and its existing MIT notice.

See [import format and validation](KAgenda/docs/nonacademic-import.md).
