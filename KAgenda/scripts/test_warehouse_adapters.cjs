// Executes pinned, unchanged adapters against synthetic portal responses. No network or real accounts.
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const vm = require('node:vm');
const root = path.join(__dirname, '..');
const assets = path.join(root, 'app/src/main/assets/shiguang');
const provenance = JSON.parse(fs.readFileSync(path.join(assets, 'provenance.json'), 'utf8'));
const bridge = fs.readFileSync(path.join(assets, 'bridge.js'), 'utf8');
const runner = fs.readFileSync(path.join(assets, 'runner.js'), 'utf8');
const slots = [{ startTime: '08:00', endTime: '08:45' }, { startTime: '08:55', endTime: '09:40' }];
const cases = [
    {
        id: 'cqu-api', script: 'CQU/cqu.js', location: { hostname: 'my.cqu.edu.cn' },
        document: { querySelector: () => ({ innerText: 'fixture [fixture-student]' }) },
        localStorage: { getItem: () => '"fixture-only-token"' },
        response(url) {
            if (url.endsWith('/session/info-detail')) return { curSessionId: 'fixture-term' };
            if (url.endsWith('/session/info/fixture-term')) return { data: { beginDate: '2026-09-07T00:00:00Z' } };
            if (url.includes('/maxWeek/')) return { data: 22 };
            if (url.includes('/session-time-pattern')) return { data: { classPeriodVOS: slots.map((slot, i) => ({ ...slot, periodOrder: i + 1 })) } };
            if (url.includes('/my-table-detail')) return { classTimetableVOList: [{ courseName: '模拟课程', instructorName: '模拟教师-100', position: 'A201', weekDay: 1, periodFormat: '1-2', teachingWeek: '10101' }] };
            throw Error(`Unmocked endpoint: ${url}`);
        },
    },
    {
        id: 'tongji-selection', script: 'TONGJI/tongji_01.js',
        response(url) {
            if (url.includes('currentAuthId')) return {};
            if (url.includes('currentTermCalendar')) return { data: { schoolCalendar: { id: 'fixture-term' }, simpleName: 'fixture' } };
            if (url.includes('/detail?')) return { data: { beginDay: '2026-09-07T00:00:00Z', noWeekendWorkTimes: slots.map(slot => ({ beginTime: slot.startTime, endTime: slot.endTime })) } };
            if (url.includes('findStudentTimetab')) return { data: [{ courseName: '模拟课程', timeTableList: [{ teacherName: '模拟教师(100)', roomIdI18n: 'A201', dayOfWeek: 2, timeStart: 1, timeEnd: 2, weeks: [1, 3, 5] }] }] };
            throw Error(`Unmocked endpoint: ${url}`);
        },
    },
    {
        id: 'uzz-form', script: 'UZZ/uzz_01.js', location: { origin: 'https://fixture.invalid' },
        jQuery: selector => ({ length: 1, val: () => selector === '#xnm' ? '2026' : '3' }),
        response(url) {
            assert.ok(url.includes('xskbcx_cxXsgrkb.html'));
            return { kbList: [{ zcd: '1-5周(单),8-10周(双)', jcs: '1-2', xqj: '3', kcmc: '模拟课程', xm: '模拟教师', cdmc: 'A201' }] };
        },
    },
    {
        id: 'niit-merge', script: 'NIIT/niit.js',
        document: { querySelector: () => ({ getAttribute: () => '2026-2027-1' }) },
        response(url) {
            assert.ok(url.includes('cxxszhxqkb.do'));
            return { datas: { fixture: { rows: ['10100', '00001'].map(SKZC => ({ SKXQ: '4', KSJC: '1', JSJC: '2', KCM: '模拟课程', SKJS: '模拟教师/100', JASMC: 'A201', SKZC })) } } };
        },
    },
];

async function verify(testCase) {
    const bytes = fs.readFileSync(path.join(assets, 'scripts', testCase.script));
    assert.equal(crypto.createHash('sha256').update(bytes).digest('hex'), provenance.scripts[testCase.script]);
    let draft, complete, completed;
    const context = {
        location: testCase.location || {}, document: testCase.document || {}, localStorage: testCase.localStorage || {},
        jQuery: testCase.jQuery, console: { log() {}, warn() {}, error() {} }, Intl, URLSearchParams,
        fetch: async url => ({ ok: true, status: 200, json: async () => testCase.response(String(url)) }),
        _kagendaWarehouse: { dispatch(raw) {
            const message = JSON.parse(raw); assert.equal(message.token, 'fixture-session');
            let response = true;
            switch (message.action) {
                case 'courses': draft.coursesRaw = message.payload.data; break;
                case 'times': draft.timesRaw = message.payload.data; break;
                case 'config': draft.configUpdates.push(message.payload.data); break;
                case 'selection': response = 0; break;
                case 'alert': response = true; break;
                case 'prompt': throw Error('Unexpected fixture prompt');
                case 'toast': return;
                case 'complete': completed = true; complete(draft); return;
                default: throw Error(`Unsupported fixture action: ${message.action}`);
            }
            context.__kagendaSgReply(message.id, true, response);
        } },
    };
    context.window = context;
    vm.createContext(context);
    let first;
    for (let run = 0; run < 2; run++) {
        draft = { coursesRaw: '', timesRaw: '', configUpdates: [] }; completed = false;
        const done = new Promise(resolve => { complete = resolve; });
        // Exact wrapper generated by the Kotlin host during its regression test.
        const nativeRunner = path.join(root, 'app/build/reports/warehouse-runners', testCase.id + '.js');
        vm.runInContext(fs.readFileSync(nativeRunner, 'utf8'), context, { timeout: 1000 });
        const timer = setTimeout(() => complete(null), 2000);
        const result = await done; clearTimeout(timer);
        assert.ok(completed && result, `${testCase.id}: adapter never completed`);
        const courses = JSON.parse(result.coursesRaw);
        assert.equal(courses.length, 1); assert.equal(courses[0].name, '模拟课程');
        assert.equal(courses[0].position, 'A201'); assert.equal(courses[0].startSection, 1); assert.equal(courses[0].endSection, 2);
        assert.deepEqual(courses[0].weeks, testCase.id === 'uzz-form' ? [1, 3, 5, 8, 10] : [1, 3, 5]);
        assert.ok(JSON.parse(result.timesRaw).length >= 2);
        if (run === 0) first = structuredClone(result); else assert.deepEqual(result, first, `${testCase.id}: repeat import differs`);
    }
    const fixture = path.join(root, 'app/src/test/resources/warehouse', testCase.id + '.json');
    if (process.argv.includes('--write-fixtures')) { fs.mkdirSync(path.dirname(fixture), { recursive: true }); fs.writeFileSync(fixture, JSON.stringify(first, null, 2) + '\n'); }
    else assert.deepEqual(first, JSON.parse(fs.readFileSync(fixture, 'utf8')));
    console.log(`${testCase.id}: original extraction, staged bridge and repeat execution passed`);
}

(async () => {
    for (const script of Object.keys(provenance.scripts)) {
        const wrapper = path.join(root, 'app/build/reports/warehouse-runners/all', script);
        new vm.Script(fs.readFileSync(wrapper, 'utf8'), { filename: script });
    }
    console.log(`${Object.keys(provenance.scripts).length} native-generated wrapped scripts compiled successfully`);
    for (const testCase of cases) await verify(testCase);
})().catch(error => { console.error(error); process.exitCode = 1; });
