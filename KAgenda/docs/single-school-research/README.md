# 全校逐一检索与单校适配结果

> **2026-10-20 更正：已确认漏检 HDUHelper。** 该仓库在首轮查询前已存在，却未出现在返回线索与候选表。3202 个名称的首轮查询执行完成，不代表相关仓库已找全；全国仓库发现的交叉复核仍未完成。下列 181 个候选等数字为首轮快照，补充核查见 [漏检证据与源码分析](followup-hduhelper.md)。

调查日期：2026-10-19（Asia/Shanghai）。

已完成 **3202 个学校名称入口**的独立 GitHub 定向查询（3196 所官方学校 + 6 个社区额外名称），未搜索 0。返回 468 个原始仓库线索，筛选并记录 181 个元数据候选；取得 194 个固定版本文件、62 个源码树。

本轮从 10 个项目接入 **13 所学校、15 个入口**，经过合成样本验证，**真实学校账号验证为 0**。没有线索不等于不存在仓库；本轮未采用不等于该校不支持。逐校查询完成不能证明 GitHub 全部仓库已穷尽。

## 已接入入口

日期为源文件在固定快照中的最后提交日期，不是仓库推送日或学校可用性验证日。原有独立流程、专属入口和通用后备保留。

| 学校/学段入口 | 固定源文件 | 文件日期 UTC | 许可 |
|---|---|---|---|
| 南京大学本科教务列表 | [WheretoSleepinNJU/NJU-Class-Shedule-Flutter](https://github.com/WheretoSleepinNJU/NJU-Class-Shedule-Flutter/blob/25125ef6c7092c47f9341adcfaa7e1dc1974b44d/api/tools/njubksjw2.js) | 2026-01-17 | Apache-2.0 |
| 南京大学本科已选课程 | [WheretoSleepinNJU/NJU-Class-Shedule-Flutter](https://github.com/WheretoSleepinNJU/NJU-Class-Shedule-Flutter/blob/25125ef6c7092c47f9341adcfaa7e1dc1974b44d/api/tools/njubksxk.js) | 2026-01-17 | Apache-2.0 |
| 南京大学研究生教务 | [WheretoSleepinNJU/NJU-Class-Shedule-Flutter](https://github.com/WheretoSleepinNJU/NJU-Class-Shedule-Flutter/blob/25125ef6c7092c47f9341adcfaa7e1dc1974b44d/api/tools/njuyjsjw.js) | 2025-09-28 | Apache-2.0 |
| 中国人民大学研究生课表 | [WheretoSleepinNJU/NJU-Class-Shedule-Flutter](https://github.com/WheretoSleepinNJU/NJU-Class-Shedule-Flutter/blob/25125ef6c7092c47f9341adcfaa7e1dc1974b44d/api/tools/rucyjsjw.js) | 2026-01-17 | Apache-2.0 |
| 东南大学本研教务课表 | [WheretoSleepinNJU/NJU-Class-Shedule-Flutter](https://github.com/WheretoSleepinNJU/NJU-Class-Shedule-Flutter/blob/25125ef6c7092c47f9341adcfaa7e1dc1974b44d/api/tools/seubksjw.js) | 2026-08-28 | Apache-2.0 |
| 上海交通大学研究生选课 | [WheretoSleepinNJU/NJU-Class-Shedule-Flutter](https://github.com/WheretoSleepinNJU/NJU-Class-Shedule-Flutter/blob/25125ef6c7092c47f9341adcfaa7e1dc1974b44d/api/tools/sjtuyjsxk.js) | 2026-01-17 | Apache-2.0 |
| 国科大在线研究生课表 | [WheretoSleepinNJU/NJU-Class-Shedule-Flutter](https://github.com/WheretoSleepinNJU/NJU-Class-Shedule-Flutter/blob/25125ef6c7092c47f9341adcfaa7e1dc1974b44d/api/tools/ucasyjsxk.js) | 2026-09-06 | Apache-2.0 |
| 大连东软我的课表 | [LuBanQAQ/dnui-wakeup-export](https://github.com/LuBanQAQ/dnui-wakeup-export/blob/1035f4b4ceb7ba3a801325c57547b4def8a697b6/dnui-wakeup-export.user.js) | 2026-01-04 | MIT |
| 福州大学本科我的选课（含调课） | [west2-online/jwch](https://github.com/west2-online/jwch/blob/81779d5a6307fa46f2017cba89d246691b5be73e/course.go) | 2026-09-20 | Apache-2.0 |
| 浙江大学本科个人课表 | [Jnove/ZJUCoursetrace](https://github.com/Jnove/ZJUCoursetrace/blob/faed4d975958db8e6277727001081f91f673f048/lib/zju/api.ts) | 2026-10-08 | MIT |
| 上海理工大学个人课表 | [SLctfTeam/USST-Lecture-Table-Calendar](https://github.com/SLctfTeam/USST-Lecture-Table-Calendar/blob/bce264b562c198f4bb9863e516cecd92bf4aa306/src/utils/information.js) | 2024-09-12 | MIT |
| 西交利物浦完整学期课表 | [sceneeeee/xjtlu-timetable-bookmarklet](https://github.com/sceneeeee/xjtlu-timetable-bookmarklet/blob/64951fce1204a3bce92a44885b85040c8996a5c7/src/lib/parseTimetable.ts) | 2026-05-28 | MIT |
| 四川轻化工大学个人课表 | [suse-edu-cn/SUSE-OAA-APP](https://github.com/suse-edu-cn/SUSE-OAA-APP/blob/53d06ec130a106f4bf6669e1eb536088a841a9b0/shared/src/commonMain/kotlin/com/suseoaa/projectoaa/shared/data/remote/api/SchoolApiService.kt) | 2026-09-05 | MIT |
| 成都锦城学院 Table1 学期课表 | [XFY9326/Schedule](https://github.com/XFY9326/Schedule/blob/d631e15cb7d114eb1df9f53a40e3f76fc3a4ea29/app/src/main/java/tool/xfy9326/schedule/content/adapters/parser/SCUJCCCourseParser.kt) | 2026-03-21 | Apache-2.0 |
| 郑州轻工业大学当前学期列表 | [LingNc/stnet_syllabus](https://github.com/LingNc/stnet_syllabus/blob/02e9ff9ff806dcf51f3c475769ed1e64d19b898f/internal/parser/list_parser.go) | 2026-03-09 | MIT |

## 接入边界与修正

- 可见网页登录、同源读取、先预览再保存；不会把学校账号提交给候选仓库的后端。
- 本研入口分开；南京/人大/东南明确选学期，分页缺失停止；上交按所选学期过滤；国科大逐周检查完整性并保留教室变化。
- 福大执行调课；浙大使用独立路径和单双周字段；四川轻化工提示整周实践；不默认为星期一、第一节或 1–16 周。
- 西交利物浦只支持结构明确的 timetableplus 布局；开放周次需填写终周，实际时间生成待确认作息；受保护框架需直接打开。
- 锦城只支持明确星期的历史 Table1，Table6 未支持；郑州轻工业读取校方当前学期列表，保留编码 0，整周实践不转为普通节次。
- 清华研究生引用 thu-info-lib 的许可来源未解决，且含固定日期/吞错，未移植；合工大/北大/电子科大仍缺能核对完整学期的响应。

## 候选逐项结论

审查层级：metadata 元数据筛选；pinned_tree 已定位源码树；pinned_files 已读取固定文件。元数据层不宣称已读全部代码。GPL/LGPL/AGPL/MPL/BSL 未直接复制，集成策略与义务需要单独确定。

| 学校线索 | 仓库 | 许可元数据 | 层级 | 本轮结论 |
|---|---|---|---|---|
| 浙江大学 | [Lucent-Snow/zju-scholar](https://github.com/Lucent-Snow/zju-scholar) | GPL-3.0 | pinned_files | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。已读 zju_academic.py；浙大专属查询实现，GPL，保留接口线索，暂不移植代码。 |
| 清华大学 | [fnoi16wjhui/Learning-Assistant](https://github.com/fnoi16wjhui/Learning-Assistant) | MIT | pinned_files | 已读 HTML 周视图，依赖 anchor Monday 生成日期；不能将某周扩展为完整学期。 |
| 清华大学 | [Starrah/THUCourseHelper](https://github.com/Starrah/THUCourseHelper) | MIT | metadata | 2020 旧应用，优先审查了近期同校实现，仍未确认完整学期采集与来源，不移植旧登录流程。 |
| 上海交通大学 | [SJTU-Geek/sjtu-mcp-server](https://github.com/SJTU-Geek/sjtu-mcp-server) | GPL-3.0 | pinned_files | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。已读 C# 教务服务与辅助类；GPL，暂作系统和字段参考。 |
| 上海交通大学 | [kuan-er/sjtu-agent](https://github.com/kuan-er/sjtu-agent) | MIT | pinned_files | 已读 calendar/登录模块，calendar 是校历；本轮采用另一项目明确的研究生选课接口。 |
| 合肥工业大学 | [Chiu-xaH/HFUT-Schedule](https://github.com/Chiu-xaH/HFUT-Schedule) | Apache-2.0 | pinned_files | 已读 eams5 接口与模型；内部学生/业务/学期 ID 及实际时间到节次映射仍缺可核对响应，不沿用公式学期或猜测 ID，保留后备。 |
| 电子科技大学 | [MilLoong/UESTC-EAMS-Helper-App](https://github.com/MilLoong/UESTC-EAMS-Helper-App) | MIT | pinned_files | 已读逐周移动 API；缺完整学期边界响应，原实现有字段默认值与周次去重风险，保留现有本校脚本。 |
| 电子科技大学 | [MilLoong/UESTC-EAMS-Helper-Python](https://github.com/MilLoong/UESTC-EAMS-Helper-Python) | MIT | pinned_files | 同作者 Android 项目的交叉参考，仍缺完整学期响应，不重复计算学校。 |
| 电子科技大学 | [Vizards/uestc-api](https://github.com/Vizards/uestc-api) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。旧 GPL 实现；2019 年推送，低优先级。 |
| 电子科技大学 | [Saafo/uestc-coursetable-parser](https://github.com/Saafo/uestc-coursetable-parser) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。GPL；仓库推送新不代表旧 EAMS 解析已适配当前系统，待逐文件复核。 |
| 电子科技大学 | [zh-weng/uestc-schedule](https://github.com/zh-weng/uestc-schedule) | MIT | pinned_files | 旧 EAMS 导出线索（2023）；已优先核查同校 2026 移动项目，仍缺完整学期响应，保留现有专属脚本。 |
| 南京大学 | [WheretoSleepinNJU/NJU-Class-Shedule-Flutter](https://github.com/WheretoSleepinNJU/NJU-Class-Shedule-Flutter) | Apache-2.0 | pinned_files | 已移植到 EXT 脚本，固定提交、源文件哈希/日期和原许可已记录；不宣称真实账号验证。 |
| 南京大学 | [SuperKenVery/nju-schedule-ics](https://github.com/SuperKenVery/nju-schedule-ics) | 未取得明确许可 | metadata | 本次公开元数据请求失败/404，不作为已取得的可采用源码。 |
| 南京大学 | [Mellow-Winds/NJU-Hub](https://github.com/Mellow-Winds/NJU-Hub) | 未取得明确许可 | pinned_files | 本次未取得明确的可采用许可，未复制代码。已读课表 DOM 采集与时间解析；未找到明确许可证，暂不采用代码。 |
| 北京邮电大学 | [Nemoyuzx/where_to_study](https://github.com/Nemoyuzx/where_to_study) | GPL-3.0 | pinned_files | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。已读原生 Kotlin 移动教务客户端；GPL，北邮现有专属脚本之外的接口参考。 |
| 西安电子科技大学 | [xdlinux/xidian-scripts](https://github.com/xdlinux/xidian-scripts) | LGPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。LGPL；候选线索，尚未深入审查课表模块及其单独授权。 |
| 西安电子科技大学 | [BenderBlog/traintime_pda](https://github.com/BenderBlog/traintime_pda) | MPL-2.0 | pinned_tree | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。MPL-2.0；近期西电客户端，需核对具体模块、文件级义务和采集端依赖。 |
| 天津大学 | [twtstudio/WePeiYang-Flutter](https://github.com/twtstudio/WePeiYang-Flutter) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。GPL；近期天大客户端，尚需追到采集后端，不能把前端接口当学校直接接口。 |
| 重庆邮电大学 | [qwqVictor/CQUPT-ics](https://github.com/qwqVictor/CQUPT-ics) | MIT | metadata | 流程依赖 Redrock/WeCQUPT 第三方服务与用户标识，非独立校方直连方案。 |
| 广西大学（研究生） | [GaleBird/traintime-pda-gxu](https://github.com/GaleBird/traintime-pda-gxu) | MPL-2.0 | pinned_tree | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。MPL-2.0；广西大学研究生方向的分支，需分清继承代码与本校采集模块。 |
| 武汉大学 | [whu-ham/whu-ham.github.io](https://github.com/whu-ham/whu-ham.github.io) | MIT | metadata | 生活助手文档/发布网站，不是已取得的 Android 教务采集源码。 |
| 武汉大学 | [ClosedWHU/Luotopia](https://github.com/ClosedWHU/Luotopia) | NOASSERTION | metadata | 本次未取得明确的可采用许可，未复制代码。README 说明为官网与发布入口；未取得 Android 客户端解析源码，非可移植适配器。 |
| 四川大学 | [The-Brotherhood-of-SCU/Bugaoshan](https://github.com/The-Brotherhood-of-SCU/Bugaoshan) | AGPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。AGPL；川大近期项目，待追采集逻辑，暂不直接移植。 |
| 四川大学 | [The-Brotherhood-of-SCU/SCU-CLI](https://github.com/The-Brotherhood-of-SCU/SCU-CLI) | AGPL-3.0 | pinned_tree | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。已定位 internal/api/zhjw.go；AGPL，保留教务接口研究线索。 |
| 四川大学 | [The-Brotherhood-of-SCU/scu-plus](https://github.com/The-Brotherhood-of-SCU/scu-plus) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。GPL；川大同组织项目，待区分前后端与重复实现。 |
| 浙江工业大学 | [zjutjh/WeJH-Taro](https://github.com/zjutjh/WeJH-Taro) | 未取得明确许可 | pinned_tree | 本次未取得明确的可采用许可，未复制代码。浙工大前端候选；许可未明，还需确认采集后端是否公开。 |
| 广西大学、河南工业大学 | [yusixian/AISchedule_myparser](https://github.com/yusixian/AISchedule_myparser) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。广西大学/河南工业大学脚本线索；许可未明，API 最后推送为 2023 年，勿按搜索摘要当作 2025 年更新。 |
| 华南理工大学、广东工业大学 | [he11x/feishu](https://github.com/he11x/feishu) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。华工/广工课表线索；2021 年、许可未明，低优先级。 |
| 广东工业大学 | [gdutday/gdutday-wechat](https://github.com/gdutday/gdutday-wechat) | MIT | pinned_files | 已读 APIs.js，相关地址为第三方云函数/数据站（文件日期 2020），未取得独立校方采集链。 |
| 厦门大学 | [vintcessun/xmu_assistant_bot](https://github.com/vintcessun/xmu_assistant_bot) | AGPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。已定位 src/api/xmu_service/jw/schedule；AGPL，先保留接口线索。 |
| 厦门大学 | [vintcessun/xmu_assistant_sign_bot](https://github.com/vintcessun/xmu_assistant_sign_bot) | 未取得明确许可 | metadata | 本次公开元数据请求失败/404，不作为已取得的可采用源码。 |
| 湖南师范大学 | [starsstreaming/CS146S-learning](https://github.com/starsstreaming/CS146S-learning) | 未取得明确许可 | pinned_tree | 本次未取得明确的可采用许可，未复制代码。课程作业仓库内有湖南师大 EAMS 文件；未明确授权，不因 2026 年活跃直接采用。 |
| 东北大学 | [weNEU/neu-eone](https://github.com/weNEU/neu-eone) | MIT | pinned_files | 已读 2019 nespider.py，旧 HTTP IP EAMS/SSO；不把历史 URL 当当前系统，保留 URP/树维后备。 |
| 武汉理工大学 | [saicem/OhMyWhut.FastFetcher](https://github.com/saicem/OhMyWhut.FastFetcher) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。武汉理工课表采集线索；2023 年、许可未明，待进一步核查。 |
| 山东商业职业技术学院（示例校） | [Ares-Gao/WeCourseService](https://github.com/Ares-Gao/WeCourseService) | MIT | pinned_files | 已读 TaskActivity，需要本校配置和响应；已有树维/URP 后备，不按系统名自动分配学校，未继承关闭 TLS 校验。 |
| 福州大学 | [west2-online/fzuhelper-app](https://github.com/west2-online/fzuhelper-app) | AGPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。AGPL 客户端；已追到 server 和独立 jwch/yjsy 库，优先研究采集库。 |
| 福州大学 | [west2-online/fzuhelper-server](https://github.com/west2-online/fzuhelper-server) | Apache-2.0 | pinned_files | 已读服务依赖；本科采集来自已采用的 jwch，不重复打包服务端；研究生 yjsy 需单独许可。 |
| 中国科学院大学 | [wirsbf/TraintimePda-UCAS](https://github.com/wirsbf/TraintimePda-UCAS) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。国科大客户端分支；仓库授权未明确，继承文件可能另有许可证，暂不直接采用。 |
| 北京大学 | [pkuinfo/pkucli](https://github.com/pkuinfo/pkucli) | MIT | pinned_files | 已读 portal/calendar.rs 为校历；个人课表在 treehole 且有独立授权流程，本轮不移植。 |
| 北京大学 | [sshwy/pku3b](https://github.com/sshwy/pku3b) | MIT | pinned_files | 已读 portal 个人课表接口；CLI 展示缺能严格还原全部有效周次的响应样本，不把课程格子当完整学期排课。 |
| 南通大学 | [MrZhencn/ntu-schedule](https://github.com/MrZhencn/ntu-schedule) | 未取得明确许可 | pinned_files | 本次未取得明确的可采用许可，未复制代码。已读正方 V9 API/解析；当前默认学期处理值得参考，未找到许可证，暂不采用代码。 |
| 西北工业大学 | [CR200J1-A/NPU-Course-picker](https://github.com/CR200J1-A/NPU-Course-picker) | 未取得明确许可 | pinned_tree | 本次未取得明确的可采用许可，未复制代码。已定位 DownLoadCourse.js.js；许可未明，下载课表和选课操作需分开，尚待深入。 |
| 常州大学 | [CCZU-OSSA/CCZU-iCal-rs](https://github.com/CCZU-OSSA/CCZU-iCal-rs) | GPL-3.0 | pinned_tree | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。常州大学 GPL 项目；候选线索，尚未深入审查。 |
| 大连东软信息学院 | [LuBanQAQ/dnui-wakeup-export](https://github.com/LuBanQAQ/dnui-wakeup-export) | MIT | pinned_files | 已移植到 EXT 脚本，固定提交、源文件哈希/日期和原许可已记录；不宣称真实账号验证。 |
| 郑州大学 | [TorCroft/ZZU-API](https://github.com/TorCroft/ZZU-API) | MIT | pinned_files | 已读 2023 移动 API，需要校园 App jw token 与日期区间；缺可见网页取得令牌/学期边界的证据，不猜测或套用示例 token。 |
| 广东工业大学 | [vtmer/gdut-jwgl-helper](https://github.com/vtmer/gdut-jwgl-helper) | 未取得明确许可 | pinned_tree | 本次未取得明确的可采用许可，未复制代码。2020 年、许可未明；广工旧系统线索，低优先级。 |
| 福州大学 | [west2-online/jwch](https://github.com/west2-online/jwch) | Apache-2.0 | pinned_files | 已移植到 EXT 脚本，固定提交、源文件哈希/日期和原许可已记录；不宣称真实账号验证。 |
| 福州大学 | [west2-online/yjsy](https://github.com/west2-online/yjsy) | 未取得明确许可 | pinned_files | 已定位研究生采集，未取得明确许可证，不能沿用 server/jwch 授权。 |
| 清华大学 | [thu-info-community/thu-info-app](https://github.com/thu-info-community/thu-info-app) | NOASSERTION | pinned_files | 本次未取得明确的可采用许可，未复制代码。已读 lib/schedule.ts 与 LICENSE；当前 BSL 1.1，并非可直接按 MIT/Apache 采用的开源候选。 |
| 燕山大学 | [Youwenqwq/ysu-sdk](https://github.com/Youwenqwq/ysu-sdk) | GPL-3.0 | pinned_files | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。已读 JWXT 客户端；含普通/实验/未排课查询，GPL，保留研究线索。 |
| 华中师范大学 | [asynccnu/ccnubox-be](https://github.com/asynccnu/ccnubox-be) | 未取得明确许可 | pinned_files | 本次未取得明确的可采用许可，未复制代码。已读 be-classlist/crawler，存在本科/研究生学校接口；未找到明确许可证，暂不采用代码。 |
| 上海师范大学 | [hyl64/shnu-](https://github.com/hyl64/shnu-) | 未取得明确许可 | pinned_files | 本次未取得明确的可采用许可，未复制代码。已读 course_parser.py；树维 TaskActivity 周位图解析，未找到许可证，暂不采用代码。 |
| 山东师范大学 | [CelPlume/SDNUChronoSync](https://github.com/CelPlume/SDNUChronoSync) | 未取得明确许可 | metadata | 本次公开元数据请求失败/404，不作为已取得的可采用源码。 |
| 山东大学（威海） | [kxxt/table2ics-for-sduwh](https://github.com/kxxt/table2ics-for-sduwh) | GPL-3.0 | pinned_tree | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。GPL、2023 年；山大威海 Excel 转 ICS，不是登录教务获取课表。 |
| 中山大学 | [Tidyzq/sysujwxt](https://github.com/Tidyzq/sysujwxt) | 未取得明确许可 | pinned_tree | 本次未取得明确的可采用许可，未复制代码。2016 年旧中大 Node API，许可未明；与现有 2026 年专属脚本相比不优先。 |
| 北京大学 | [Leoforever123/PKUHelper.github.io](https://github.com/Leoforever123/PKUHelper.github.io) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 北京科技大学 | [coderirse/BeikeSchedule](https://github.com/coderirse/BeikeSchedule) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 北京科技大学 | [nladuo/ustb_choose_course_system](https://github.com/nladuo/ustb_choose_course_system) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 北京科技大学 | [isHarryh/USTB-Awesome-JS](https://github.com/isHarryh/USTB-Awesome-JS) | MIT | pinned_tree | 源码树为锐格/慕课/超星及抢课辅助线索，未确认个人完整学期课表模块。 |
| 北京化工大学 | [bakahuiii/THEIA-Android](https://github.com/bakahuiii/THEIA-Android) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 北京邮电大学 | [Yokumii/bupt-campus-console](https://github.com/Yokumii/bupt-campus-console) | Apache-2.0 | pinned_tree | 前端服务控制台，未取得独立公开的校方采集解析链，保留北邮入口。 |
| 北京邮电大学 | [Yuchen114514/bupt-classrooom-searcher](https://github.com/Yuchen114514/bupt-classrooom-searcher) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 北京邮电大学 | [helium777/bupt-course-grab](https://github.com/helium777/bupt-course-grab) | MIT | metadata | 本科抢课脚本，不是完整学期个人课表解析。 |
| 北京林业大学 | [Bloomberg2000/bjfu_course_ics_generator](https://github.com/Bloomberg2000/bjfu_course_ics_generator) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 北京林业大学 | [viaoliu/BJFU_empty_classrooms_searching](https://github.com/viaoliu/BJFU_empty_classrooms_searching) | MIT | metadata | 空教室占用查询，不等同本人已选课程。 |
| 北京师范大学 | [Pangdading/BNU-Schoolwork-Assist](https://github.com/Pangdading/BNU-Schoolwork-Assist) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 华北电力大学 | [ki-ljl/ncepu-edm](https://github.com/ki-ljl/ncepu-edm) | MIT | pinned_files | 已读课程/HTTP 工具（2022），缺可核对的当前完整学期周次响应，保留后备。 |
| 首都师范大学科德学院、燕京理工学院、北华大学、通化师范学院、湘潭大学、湖南大学、湖南工业大学、华南理工大学 | [cxyzzz/HUT_API](https://github.com/cxyzzz/HUT_API) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 北京工业职业技术学院、北京信息职业技术学院、北京京北职业技术学院、天津滨海职业学院、天津工程职业技术学院、天津渤海职业技术学院、天津电子信息职业技术学院、吉林大学、吉林司法警官职业学院、吉林电子信息职业技术学院、白城职业技术学院、上海城建职业学院、上海海事职业技术学院、上海电子信息职业技术学院、沙洲职业工学院、江苏经贸职业技术学院、九州职业技术学院、硅湖职业技术学院、苏州卫生职业技术学院、福建水利电力职业技术学院、厦门海洋职业技术学院、福建农业职业技术学院、华东交通大学、平顶山工业职业技术学院、济源职业技术学院、武汉工商学院、荆州学院、湖北商贸学院、湖南信息职业技术学院、湖南城建职业技术学院、湖南中医药高等专科学校、长沙卫生职业学院、湖南食品药品职业学院、湖南有色金属职业技术学院、湖南吉利汽车职业技术学院、佛山职业技术学院、广东酒店管理职业技术学院、广东茂名幼儿师范专科学校、广州卫生职业技术学院、广东江门中医药职业学院、重庆三峡医药高等专科学校、重庆医药高等专科学校、重庆青年职业技术学院、成都银杏酒店管理学院、成都文理学院、四川工商学院、成都职业技术学院、四川化工职业技术学院、四川水利职业技术学院、四川工商职业技术学院、雅安职业技术学院、广安职业技术学院、四川中医药高等专科学校、四川科技职业学院、四川文轩职业学院、川南幼儿师范高等专科学校、贵州中医药大学、六盘水师范学院、贵州商学院、贵州中医药大学时珍学院、文山学院、滇池学院 | [imshixin/AiSchedule](https://github.com/imshixin/AiSchedule) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 南开大学、天津大学、天津科技大学 | [ljk5403/libnku](https://github.com/ljk5403/libnku) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 天津理工大学 | [AirboZH/Ai_Schedule](https://github.com/AirboZH/Ai_Schedule) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 天津师范大学、天津职业技术师范大学、中央司法警官学院、河北传媒学院、河北工程技术学院、山西传媒学院、山西工程技术学院、内蒙古大学、内蒙古科技大学、内蒙古师范大学、内蒙古民族大学、大连外国语大学、东北财经大学、山东外事职业大学、青岛工学院、齐鲁理工学院、山东财经大学东方学院、山东政法学院、齐鲁师范学院、四川大学 | [baifan97/urp](https://github.com/baifan97/urp) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 河北大学、河北地质大学、河北工业大学 | [sasaju/NormalSchedule](https://github.com/sasaju/NormalSchedule) | Apache-2.0 | pinned_files | 已读 CourseService/研究生视图，2022 后端包装接口，缺独立校方解析链，不向其后端转交账号。 |
| 河北大学、河北地质大学、河北工业大学 | [jeremisty99/AISchedule-HEBUT](https://github.com/jeremisty99/AISchedule-HEBUT) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 燕山大学 | [Youwenqwq/ysu-client](https://github.com/Youwenqwq/ysu-client) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 中北大学 | [Dreace/NUC-Information](https://github.com/Dreace/NUC-Information) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 太原理工大学 | [snake-lvyonghao/Tyut-Proxy](https://github.com/snake-lvyonghao/Tyut-Proxy) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 东北大学 | [CreamPig233/neu_wisedu2wakeup](https://github.com/CreamPig233/neu_wisedu2wakeup) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 东北大学 | [Raven98/NEUspider](https://github.com/Raven98/NEUspider) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 大连交通大学、大连海事大学 | [duniqb/mobile-dj](https://github.com/duniqb/mobile-dj) | MIT | pinned_files | 已读 JwSpiderService，文件仍为 2020 旧 HTTP academic 流程，缺当前个人课表完整结构。 |
| 大连海事大学、大连工业大学 | [ichont/DMU-Course-Help](https://github.com/ichont/DMU-Course-Help) | Apache-2.0 | metadata | 课程攻略/学习资料仓库，不是个人课表采集器。 |
| 大连外国语大学、东北财经大学 | [zanghuaren/Dlufl-TakeCourse](https://github.com/zanghuaren/Dlufl-TakeCourse) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 大连民族大学 | [Bingtagui404/Super-class-table](https://github.com/Bingtagui404/Super-class-table) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 吉林大学 | [ssmiao/JLU_classes_to_ical_file](https://github.com/ssmiao/JLU_classes_to_ical_file) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 北华大学、通化师范学院 | [yapi0420/Bhucatas](https://github.com/yapi0420/Bhucatas) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 哈尔滨工业大学 | [CryoLogicat/hit-timetable-android](https://github.com/CryoLogicat/hit-timetable-android) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 齐齐哈尔医学院、黑龙江东方学院 | [staytomorrow/QMUschedule](https://github.com/staytomorrow/QMUschedule) | CC0-1.0 | pinned_tree | 2022 易语言工程线索，未取得可独立审查、以脱敏样本验证的解析模块。 |
| 复旦大学 | [lan-kehan/fudan-course-table-export](https://github.com/lan-kehan/fudan-course-table-export) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 同济大学 | [FlowerBlackG/OneTJ](https://github.com/FlowerBlackG/OneTJ) | MulanPSL-2.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 同济大学 | [YourTongji/YourTJCourse-iOS](https://github.com/YourTongji/YourTJCourse-iOS) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 上海理工大学 | [SLctfTeam/USST-Lecture-Table-Calendar](https://github.com/SLctfTeam/USST-Lecture-Table-Calendar) | MIT | pinned_files | 已移植到 EXT 脚本，固定提交、源文件哈希/日期和原许可已记录；不宣称真实账号验证。 |
| 上海健康医学院、华东师范大学 | [jtchen2k/ECNU-class2ics](https://github.com/jtchen2k/ECNU-class2ics) | MIT | pinned_files | 已读 2020/2021 GetClassJson 等，旧模拟登录/验证码服务流程，缺当前响应，未移植反馈/消息发送代码。 |
| 上海健康医学院、华东师范大学 | [JJAYCHEN1e/ECNU_Bring-Your-Timetable-to-Calendar-App](https://github.com/JJAYCHEN1e/ECNU_Bring-Your-Timetable-to-Calendar-App) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 上海大学 | [zmdld11/SHU-jwxk-assistant](https://github.com/zmdld11/SHU-jwxk-assistant) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 上海大学 | [panghaibin/shu-web-js](https://github.com/panghaibin/shu-web-js) | MIT | metadata | 选课增强线索，未确认完整个人课表导入模块。 |
| 南京大学、苏州大学、东南大学、南华大学 | [Ricraft/wakeup-schedule](https://github.com/Ricraft/wakeup-schedule) | MIT | pinned_files | 已读南华导入器，为强智 HTML 文件解析配置；已有强智后备，不重复计为新增学校适配。 |
| 南京大学、东南大学、南京航空航天大学 | [NN708/SEU-Curriculum-Export](https://github.com/NN708/SEU-Curriculum-Export) | MIT | pinned_files | 已读旧导出代码；文件年代早于本轮采用的东南大学明确接口，不重复接入。 |
| 南京大学、东南大学、南京航空航天大学 | [miaotony/NUAA_ClassSchedule](https://github.com/miaotony/NUAA_ClassSchedule) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 南京大学、东南大学、南京航空航天大学 | [NUAA-Open-Source/NUAA-iCal-Python](https://github.com/NUAA-Open-Source/NUAA-iCal-Python) | MIT | metadata | 2019 旧导出线索，缺当前响应验证，保留现有学校/解析入口。 |
| 南京理工大学、南京工业大学 | [NJUST-OpenLib/NJUST-JWC-COURSE](https://github.com/NJUST-OpenLib/NJUST-JWC-COURSE) | MIT | pinned_files | 已读 2026 schedule.py/constants.py；两表按课程名/大节拼接可能错配，入口是旧 HTTP IP，缺当前可靠页面与响应，保留强智后备。 |
| 中国矿业大学、南京工业大学 | [shaxiu/njtech_grabber](https://github.com/shaxiu/njtech_grabber) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 南京工业大学、浙江农林大学、浙江中医药大学、浙江农林大学暨阳学院、九江学院、南昌职业大学、广东工程职业技术学院、西安邮电大学 | [drfccv/ZFJW-backend](https://github.com/drfccv/ZFJW-backend) | MIT | pinned_files | 已移植到 EXT 脚本，固定提交、源文件哈希/日期和原许可已记录；不宣称真实账号验证。 |
| 南京邮电大学 | [gaoliang/NJUPT-API](https://github.com/gaoliang/NJUPT-API) | MIT | pinned_files | 已读 zhengfang.py，文件最后提交 2019；旧 ASPX/按指定周查询，保留现有本校脚本和旧版正方后备。 |
| 河海大学 | [zewenli98/HHU_course](https://github.com/zewenli98/HHU_course) | MIT | metadata | 计算机课程资料，不是个人教务课表采集。 |
| 淮阴师范学院、合肥师范学院、福建农林大学、宜宾学院 | [zimo0o0omiz/wisedu-unified-login-api](https://github.com/zimo0o0omiz/wisedu-unified-login-api) | MIT | metadata | 统一身份登录 API，登录能力本身不能证明完整学期课表采集。 |
| 南京工业职业技术大学、常州工学院 | [Yorick-Ryu/NIIT_getCourse](https://github.com/Yorick-Ryu/NIIT_getCourse) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 南京工业职业技术大学、常州工学院 | [GuoXianSen/IntelligentCampus](https://github.com/GuoXianSen/IntelligentCampus) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 南通理工学院、东南大学成贤学院、苏州科技大学天平学院、江苏大学京江学院、江苏师范大学科文学院、浙大城市学院、浙大宁波理工学院、合肥师范学院、安徽国防科技职业学院、湖南工学院、湘中幼儿师范高等专科学校、广东科技学院、广东工程职业技术学院、广东创新科技职业学院、广东艺术职业学院、广西警察学院、广西农业职业技术大学、柳州工学院、重庆文化艺术职业学院、重庆资源与环境保护职业学院、重庆护理职业学院、电子科技大学成都学院、成都理工大学工程技术学院、新疆医科大学、喀什大学 | [Surine/ScheduleX](https://github.com/Surine/ScheduleX) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 西交利物浦大学 | [PeronGH/XJTLU-App](https://github.com/PeronGH/XJTLU-App) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 西交利物浦大学 | [sceneeeee/xjtlu-timetable-bookmarklet](https://github.com/sceneeeee/xjtlu-timetable-bookmarklet) | MIT | pinned_files | 已移植到 EXT 脚本，固定提交、源文件哈希/日期和原许可已记录；不宣称真实账号验证。 |
| 西交利物浦大学 | [NineLivesNoob/XJTLU_ICS_Y2S2_Course-notes_23-24](https://github.com/NineLivesNoob/XJTLU_ICS_Y2S2_Course-notes_23-24) | MIT | metadata | ICS 指 Information and Computing Science 专业，此仓库是课程笔记，不是 iCalendar。 |
| 浙江大学 | [Meteors27/ZJU-Calendar-Maker](https://github.com/Meteors27/ZJU-Calendar-Maker) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 浙江大学 | [Jnove/ZJUCoursetrace](https://github.com/Jnove/ZJUCoursetrace) | MIT | pinned_files | 已移植到 EXT 脚本，固定提交、源文件哈希/日期和原许可已记录；不宣称真实账号验证。 |
| 杭州电子科技大学 | [Cyrus97/HDU](https://github.com/Cyrus97/HDU) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 浙江工业大学 | [CodeBoy2006/zfxk-helper](https://github.com/CodeBoy2006/zfxk-helper) | Apache-2.0 | pinned_tree | 选课 SDK/工作台，未确认完整个人课表模块；本轮采用明确课表接口的项目。 |
| 浙江农林大学 | [20czy/zafu_xiaolin_campus_agent](https://github.com/20czy/zafu_xiaolin_campus_agent) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 浙江农林大学、浙江中医药大学 | [Void-JackLee/ZFCourseTakingAssistant](https://github.com/Void-JackLee/ZFCourseTakingAssistant) | GPL-2.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 浙江工商大学、嘉兴大学 | [FHfanshu/ZJXU-CampusHub](https://github.com/FHfanshu/ZJXU-CampusHub) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 中国科学技术大学 | [ustcjerry/ustc-yjs-course-hacker](https://github.com/ustcjerry/ustc-yjs-course-hacker) | MIT | metadata | 2019 研究生选课自动化，不是个人课表导入。 |
| 厦门大学 | [XMU-Helper/home](https://github.com/XMU-Helper/home) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 厦门理工学院、三明学院 | [rawchen/zfsoft-xmut-api](https://github.com/rawchen/zfsoft-xmut-api) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 三明学院 | [zaochih/zf-to-ics](https://github.com/zaochih/zf-to-ics) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 宜春学院、井冈山大学 | [Zxilly/JGSU-timetable-export](https://github.com/Zxilly/JGSU-timetable-export) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 山东大学、中国海洋大学 | [ruabbit233/docker-ouc-portal](https://github.com/ruabbit233/docker-ouc-portal) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 山东大学 | [SDU-toolhub/awesome-SDU-scripts](https://github.com/SDU-toolhub/awesome-SDU-scripts) | MIT | metadata | 校园脚本名单线索，未取得可直接移植的本校完整课表模块，不按名单宣称适配。 |
| 山东科技大学 | [WindrunnerMax/SHST](https://github.com/WindrunnerMax/SHST) | MIT | pinned_files | 元数据筛选后仍未确认固定版本的完整个人课表采集、解析及响应结构，不能据搜索摘要生成适配。 |
| 齐鲁工业大学、山东理工大学 | [C1ouDreamW/qlu-toolbox](https://github.com/C1ouDreamW/qlu-toolbox) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 郑州大学、河南理工大学 | [HPUhushicheng/Hpu-Api-docs](https://github.com/HPUhushicheng/Hpu-Api-docs) | Apache-2.0 | metadata | 描述含非商业限制，API 元数据 MIT 不足以确认具体采用条件，未打包。 |
| 郑州轻工业大学 | [LingNc/stnet_syllabus](https://github.com/LingNc/stnet_syllabus) | MIT | pinned_files | 已移植到 EXT 脚本，固定提交、源文件哈希/日期和原许可已记录；不宣称真实账号验证。 |
| 中原工学院 | [jungou/ZZTI](https://github.com/jungou/ZZTI) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 武汉大学 | [whaliendev/WHU-GPA-helperX](https://github.com/whaliendev/WHU-GPA-helperX) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 湖北工业大学、华中农业大学 | [superdaobo/mini-hbut](https://github.com/superdaobo/mini-hbut) | NOASSERTION | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 华中师范大学 | [gwuhaolin/myccnu](https://github.com/gwuhaolin/myccnu) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 湖南大学 | [jiananlan/HNU_schedule_generater](https://github.com/jiananlan/HNU_schedule_generater) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 中南大学 | [csuhan/csugo](https://github.com/csuhan/csugo) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 中南大学、湖南科技大学 | [Rui-li023/CSUAutoSelect](https://github.com/Rui-li023/CSUAutoSelect) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 中南大学 | [cnzhoutao/SchoolApi](https://github.com/cnzhoutao/SchoolApi) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 中南林业科技大学、湖南师范大学 | [law52525/CsuftJwc](https://github.com/law52525/CsuftJwc) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 中南林业科技大学、湖南师范大学 | [yilinyo/CsuftSpiderBackend](https://github.com/yilinyo/CsuftSpiderBackend) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 衡阳师范学院、邵阳学院、南华大学、湖南工学院 | [PYLinTech/xykcb_server](https://github.com/PYLinTech/xykcb_server) | Apache-2.0 | pinned_files | 已读湖南工学院/衡阳师院/南华模块及 NOTICE；存在按格子推断节次和省略失败结果等行为，保留现有解析后备，未复制第三方账号传递流程。 |
| 南华大学 | [WGowi/USCCampusEastStreet](https://github.com/WGowi/USCCampusEastStreet) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 湖南工学院 | [xylophonee/Course-Table-For-HNIT-Flutter](https://github.com/xylophonee/Course-Table-For-HNIT-Flutter) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 湖南工学院 | [PYLinTech/xykcb](https://github.com/PYLinTech/xykcb) | Apache-2.0 | metadata | 前端；已追到 xykcb_server，不把第三方服务接口当校方直接采集实现。 |
| 湖南三一工业职业技术学院 | [xmb505/SANY_Schedule](https://github.com/xmb505/SANY_Schedule) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 中山大学 | [jaisonZheng/learnXsysu](https://github.com/jaisonZheng/learnXsysu) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 华南理工大学、华南农业大学、广东海洋大学 | [greyovo/AIScheduleSCAU](https://github.com/greyovo/AIScheduleSCAU) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 华南理工大学、华南农业大学、广东海洋大学、广州大学 | [MengyangGao/gzic.online](https://github.com/MengyangGao/gzic.online) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 深圳大学 | [ChronicleY/InfoBoard](https://github.com/ChronicleY/InfoBoard) | MIT | metadata | 公文通助手，不是个人课程表导入。 |
| 广东白云学院 | [danbaixi/yunxiaozhi](https://github.com/danbaixi/yunxiaozhi) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 仲恺农业工程学院、五邑大学、广东金融学院、电子科技大学中山学院 | [Jin-Cheng-Ming/ZHKU-Connector](https://github.com/Jin-Cheng-Ming/ZHKU-Connector) | MIT | metadata | 校园网络登录工具，不是教务个人课表采集。 |
| 广东金融学院 | [EwingYangs/GDUF-QZAPI](https://github.com/EwingYangs/GDUF-QZAPI) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 东莞理工学院、广东工业大学 | [Bertramoon/dgut-requests](https://github.com/Bertramoon/dgut-requests) | BSD-2-Clause | pinned_files | 已读 2023 get_lesson，青果 HTML 原样返回，缺当前本校完整样本，保留青果后备。 |
| 广东工业大学 | [gdutday/GDUT.ClassSchedule](https://github.com/gdutday/GDUT.ClassSchedule) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 广东工业大学 | [gdutday/gdut-lib](https://github.com/gdutday/gdut-lib) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 广东科技学院 | [Feldan/ZFClassTable2ICS](https://github.com/Feldan/ZFClassTable2ICS) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 南方科技大学 | [Fros1er/SUSTechTISHelper](https://github.com/Fros1er/SUSTechTISHelper) | Apache-2.0 | pinned_files | 已读 2026 用户脚本；主要是选课行暂存和选课辅助，暂存表不能证明已选完整课表。 |
| 深圳技术大学 | [OpaqueGlass/HEU_Timetable_Parser](https://github.com/OpaqueGlass/HEU_Timetable_Parser) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 广西大学、桂林电子科技大学 | [yiwent/guet](https://github.com/yiwent/guet) | Apache-2.0 | pinned_files | 已读 2017 CourseService.java，旧 ASP 和按格子推断节次，缺当前样本，不优先于现有学校入口。 |
| 重庆大学、重庆邮电大学 | [NMID-CQUPT/CLF-CQPUT](https://github.com/NMID-CQUPT/CLF-CQPUT) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 重庆大学、重庆邮电大学 | [cqululei/wecqupt](https://github.com/cqululei/wecqupt) | AGPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 重庆大学、重庆邮电大学 | [lanshan-studio/wecqupt](https://github.com/lanshan-studio/wecqupt) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 重庆大学 | [haowang02/cqu-graduate-curricula](https://github.com/haowang02/cqu-graduate-curricula) | MIT | pinned_files | 已读 2023 main.py，旧 HTTP mis 与固定日期/作息换算，缺当前完整响应，不直接移植。 |
| 四川大学 | [The-Brotherhood-of-SCU/Bugaoshan-Linux](https://github.com/The-Brotherhood-of-SCU/Bugaoshan-Linux) | AGPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 四川大学 | [The-Brotherhood-of-SCU/Bugaoshan-HarmonyOS](https://github.com/The-Brotherhood-of-SCU/Bugaoshan-HarmonyOS) | AGPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 四川大学 | [xiaomingplus/scuApi](https://github.com/xiaomingplus/scuApi) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 四川大学 | [dsgygb/scuApi](https://github.com/dsgygb/scuApi) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 四川大学 | [PIKACHUIM/UrpHelps](https://github.com/PIKACHUIM/UrpHelps) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 四川大学 | [mohuishou/scuplus-jwc-package](https://github.com/mohuishou/scuplus-jwc-package) | 未取得明确许可 | metadata | 本次公开元数据请求失败/404，不作为已取得的可采用源码。 |
| 四川大学 | [mohuishou/scujwc-go](https://github.com/mohuishou/scujwc-go) | 未取得明确许可 | metadata | 本次公开元数据请求失败/404，不作为已取得的可采用源码。 |
| 四川大学 | [ff66ccff/SCU_COURSE_searcher](https://github.com/ff66ccff/SCU_COURSE_searcher) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 西南交通大学、电子科技大学 | [junyilou/python-ical-timetable](https://github.com/junyilou/python-ical-timetable) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 西南交通大学 | [HaaaaE/swjtu-course-enroll](https://github.com/HaaaaE/swjtu-course-enroll) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 成都信息工程大学 | [yanyaoli/cuit-course-helper](https://github.com/yanyaoli/cuit-course-helper) | Apache-2.0 | metadata | 网课辅助，不是个人学期课表导入。 |
| 四川轻化工大学 | [suse-edu-cn/SUSE-OAA-APP](https://github.com/suse-edu-cn/SUSE-OAA-APP) | MIT | pinned_files | 已移植到 EXT 脚本，固定提交、源文件哈希/日期和原许可已记录；不宣称真实账号验证。 |
| 成都工业学院 | [longer96/CDTU](https://github.com/longer96/CDTU) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 成都银杏酒店管理学院、成都文理学院、四川工商学院 | [iFulling/cdcasSK](https://github.com/iFulling/cdcasSK) | MIT | pinned_files | 已读 README/app.js，实际为英华学堂网课播放/答题助手，无个人学期课表导入。 |
| 成都外国语学院、成都锦城学院 | [XFY9326/Schedule](https://github.com/XFY9326/Schedule) | Apache-2.0 | pinned_files | 已移植到 EXT 脚本，固定提交、源文件哈希/日期和原许可已记录；不宣称真实账号验证。仅采用锦城 Table1；安徽科技/南京审计历史入口及 Table6 未移植。 |
| 西安交通大学、西北工业大学 | [yan-xiaoo/XJTUToolBox](https://github.com/yan-xiaoo/XJTUToolBox) | GPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |
| 西安交通大学、西北工业大学 | [xinghehuanyu-sudo/xjtu-toolbox-android-](https://github.com/xinghehuanyu-sudo/xjtu-toolbox-android-) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 长安大学 | [HaneChiri/CHD_portal_login](https://github.com/HaneChiri/CHD_portal_login) | 未取得明确许可 | metadata | 本次未取得明确的可采用许可，未复制代码。 |
| 新疆大学 | [xjuIcthub/xju-api](https://github.com/xjuIcthub/xju-api) | AGPL-3.0 | metadata | 本轮未确定该许可的兼容集成与履约方式，未复制进当前适配包。 |

## 证据与复查

- [逐校台账](all-school-search.json)：3202 条原始查询、时间、证据哈希与接入状态；[查询批次](search-batches/) 保留逐批记录。
- [原始线索结论](all-school-review.json) 和 [仓库结论](repository-review.json) 记录误匹配、许可/来源限制和已采用项。
- [固定来源](source-evidence.json) 记录原始路径、SHA-256、提交与逐文件日期；[打包来源](../../app/src/main/assets/academic/school-provenance.json) 还包含修改后哈希、辅助文件与原许可。
- 原始下载/只读 bare Git 缓存位于忽略的 app/build/reports/academic-research；没有执行第三方项目。API 限流处使用正常只读 Git 获取固定提交和文件历史，不以仓库推送日代替文件日期。
- 复查：python scripts/verify_school_assets.py；node scripts/test_single_school_adapters.cjs；[Android 与设备验证记录](validation.md)。

首轮按学校全名的查询执行完成，但相关仓库不能视为已经找全。没有确认本校源码的学校仍保留通用导入，需要继续交叉检索、来源审查及完整脱敏响应验证。
