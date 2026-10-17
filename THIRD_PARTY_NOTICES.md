# Third-party data and assets

The app's MIT license does not replace the following third-party notices.

## Community academic portal adapters

- Source: [ShiGuangSchedule/shiguang_warehouse](https://github.com/ShiGuangSchedule/shiguang_warehouse)
- Pinned commit: `1dc0772566ba610f495906410734db4e7b8ef0c1`
- Copyright (c) 2025 星河欲转, with individual adapter maintainers credited in the bundled index and original source comments.
- License: [original MIT notice](KAgenda/app/src/main/assets/licenses/shiguang-warehouse-MIT.txt)
- [Original project README](KAgenda/app/src/main/assets/licenses/shiguang-warehouse-README.md)

The bundle contains 252 academic adapter groups, 276 entry points and 266 distinct, unchanged JavaScript files. Checksums are in `assets/shiguang/provenance.json`; `.gitattributes` prevents line-ending changes. The host implements the public bridge protocol independently; no code from the upstream Android application is included. Scripts run only after the user starts extraction in the visible academic browser. Courses, semester dates and time slots are reviewed before saving. Combined schedules are currently rejected; community availability is not a claim of successful account testing at every institution.

## School emblems

- Source: [lovefc/china_school_badge](https://github.com/lovefc/china_school_badge)
- Pinned commit: `04f59e07f4b809acc60493324af5ca4aa610153d`
- Author: 封尘 / lovefc
- License: [original Apache-2.0 notice](KAgenda/app/src/main/assets/licenses/school-badges-Apache-2.0.txt)
- [Original project README](KAgenda/app/src/main/assets/licenses/school-badges-README.md)

The original font and a 310-name glyph mapping are bundled with provenance hashes in `assets/catalog/badges-source.json`. Emblems belong to their respective institutions. Institutions missing from this font use their portal-provided site icon after a successful import when available; missing icons retain the existing initial-letter fallback.

## Official institution names

The [Ministry of Education list](https://www.moe.gov.cn/jyb_xxgk/s5743/s5744/202606/t20260618_1441074.html), dated June 17, 2026, contains 2,952 ordinary and 244 adult institutions in mainland China. It excludes Hong Kong, Macao and Taiwan. Source attachment checksums are in `assets/catalog/source.json`. Three community campus entries are retained separately to avoid mapping them to a different main campus.

## Verification

Run `python KAgenda/scripts/verify_school_assets.py` and `node KAgenda/scripts/test_warehouse_bridge.cjs` from the repository root. After running the Android JVM tests, run `node KAgenda/scripts/test_warehouse_adapters.cjs`. This compiles all 266 native-generated script wrappers and exercises four unchanged adapters twice against synthetic portal responses. The JVM tests independently check the recorded output fixtures with the production Kotlin parser. These checks do not log into any academic portal or establish account-level availability.

## Offline pinyin index

- Source: [mozillazg/python-pinyin](https://github.com/mozillazg/python-pinyin), version `0.55.0`
- Copyright (c) 2016 mozillazg, 闲耘
- License: [original MIT notice](KAgenda/app/src/main/assets/licenses/pypinyin-MIT.txt)

Only generated spelling data is bundled; Python and pypinyin are not app runtime dependencies. `scripts/build_school_pinyin.py` generates the school-name index, contextual prefixes and character fallback. Data hashes and generator version are recorded in `assets/catalog/pinyin-source.json`; explicit pronunciation corrections and their sources are in `pinyin-overrides.json`.
