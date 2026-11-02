// KAgenda Zhejiang undergraduate ports. Protocol/field mappings adapted from:
// wow-233/niwin-assistant (Apache-2.0; its inherited MIT notice is retained),
// Yingye66/zjgsu-campus-mcp-open-source (MIT), drfccv/ZFJW-backend (MIT),
// zhullyb/zf2ics (MIT), my832/dongfang-timetable (custom permissive license),
// wirano/CJLU_ClassTable (MIT), and Mioxiya/AISchedule-shufe-zj (MIT).
// ZWU endpoint/fields: ShiGuangSchedule/shiguang_warehouse (MIT).
// Exact revisions, source hashes, dates and modifications:
// academic/school-provenance.json; original texts: licenses/zhejiang-school-bundle.txt.
// Changes: manual browser login, selected term, same origin, strict completeness,
// no inferred dates/weeks/periods, explicit consent for unscheduled arrangements.
(function () {
  'use strict';
  const k = window.kagendaSchool;
  const profiles = {
    ext_wzu_api: {host:'jwxt.wzu.edu.cn', prefix:'/jwglxt', module:'N253508', fields:'wzu'},
    ext_zjgsu_web: {host:'jwxt.zjgsu.edu.cn', prefix:'/jwglxt', module:'N2151', fields:'zjgsu', activate:true},
    ext_zjyc: {host:'jwxt.zjyc.edu.cn', prefix:'', module:'N2151', fields:'zjyc', https:true},
    ext_zjut_api: {host:'www.gdjw.zjut.edu.cn', prefix:'/jwglxt', module:'N2151', fields:'zjut'},
    ext_zufedfc_html: {host:'jwglxt.zufedfc.edu.cn', prefix:'/jwglxt', html:true},
    ext_cjlu_api: {host:'jwxt.cjlu.edu.cn', prefix:'', module:'N2151', fields:'cjlu', https:true, endpoint:'xskbcx_cxXsKb.html'},
    ext_shufezj_api: {host:'jwxt.shufe-zj.edu.cn', prefix:'', module:'N2151', fields:'shufezj', https:true},
    ext_zwu_api: {host:'jw.zwu.edu.cn', prefix:'/jwglxt', module:'N2151', fields:'zwu', https:true}
  };
  function periods(value) {
    const text = String(value == null ? '' : value).trim().replace(/[（(]([^()（）]+)[）)]/g, '$1')
      .replace(/[第节\s]/g, '').replace(/[，、]/g, ',').replace(/[－—–~～至]/g, '-');
    k.requireValue(text && text.length < 150, '课程缺少明确节次');
    const values = [];
    text.split(',').forEach(part => {
      const m = part.match(/^(\d+)(?:-(\d+))?$/);
      k.requireValue(m, '无法识别课程节次：' + part);
      const first = k.integer(m[1], 1, 24, '起始节次'), last = m[2] ? k.integer(m[2], first, 24, '结束节次') : first;
      for (let i = first; i <= last; i++) values.push(i);
    });
    const groups = [];
    Array.from(new Set(values)).sort((a,b)=>a-b).forEach(value => {
      const previous = groups[groups.length - 1];
      if (previous && previous[1] + 1 === value) previous[1] = value;
      else groups.push([value,value]);
    });
    return groups;
  }
  function firstField(row, fields) {
    for (const field of fields) if (row[field] != null && String(row[field]).trim() !== '') return row[field];
    return '';
  }
  function parseApi(rows) {
    k.requireValue(Array.isArray(rows) && rows.length <= 5000, '课表课程列表格式无效');
    const courses = [];
    rows.forEach(row => {
      k.requireValue(row && typeof row === 'object' && !Array.isArray(row), '存在无法识别的课程记录');
      const active = k.weeks(row.zcd);
      periods(firstField(row, ['jc','jcor','jcs'])).forEach(([first,last]) => {
        courses.push(k.course(row.kcmc, firstField(row,['xm','jsxm']), row.cdmc, row.xqj, first, last, active));
      });
    });
    return courses;
  }
  function selectedTerm() {
    const year = document.querySelector('#xnm'), term = document.querySelector('#xqm');
    k.requireValue(year && term, '请先打开个人课表，并选择学年学期');
    const xnm = String(year.value || '').trim(), xqm = String(term.value || '').trim();
    k.requireValue(/^\d{4}$/.test(xnm) && /^(3|12|16)$/.test(xqm), '请选择页面提供的明确学年学期，不支持全部学期');
    return {xnm,xqm};
  }
  function guard(profile) {
    k.requireValue(profile, '未识别的学校适配入口');
    k.requireValue(location.hostname === profile.host &&
      (location.protocol === 'https:' || (!profile.https && location.protocol === 'http:')) &&
      !location.username && !location.password && !location.port,
      '请在本校教务地址完成登录后重试');
    k.requireValue(location.pathname.startsWith(profile.prefix + '/kbcx/'), '请先进入本校个人课表查询页面');
  }
  function parseHtml() {
    const table = document.querySelector('[id^="kbgrid_table"]');
    k.requireValue(table, '请先打开显示完整个人课表的网格页面');
    const heading = String(table.rows[0]?.textContent || '').replace(/\s/g, '');
    const label = heading.match(/(\d{4})[-–—](\d{4})学年第([123])学期/);
    k.requireValue(label && Number(label[2]) === Number(label[1]) + 1, '课表缺少明确学年学期，请先在网页完成查询');
    const year = document.querySelector('#xnm'), term = document.querySelector('#xqm');
    if (year || term) {
      const selected = selectedTerm();
      k.requireValue(selected.xnm === label[1] && selected.xqm === ({1:'3',2:'12',3:'16'})[label[3]],
        '当前显示课表与选择的学期不一致，请先在网页重新查询');
    }
    const blocks = Array.from(table.querySelectorAll('.timetable_con'));
    k.requireValue(blocks.length > 0 && blocks.length <= 5000, '没有可识别的个人课程安排');
    const courses = []; let omitted = 0;
    blocks.forEach(block => {
      const cell = block.closest('td'), day = cell && String(cell.id || '').match(/^([1-7])-\d+$/);
      const title = block.querySelector('.title');
      k.requireValue(day && title, '存在缺少星期或课程名称的排课记录');
      // The source identifies red-font unselected courses separately. Never
      // silently import them as selected classes or ignore them without notice.
      if (Array.from(block.querySelectorAll('font')).some(f => /^(red|#f00|#ff0000)$/i.test(f.getAttribute('color') || ''))) {
        omitted++; return;
      }
      const field = label => {
        const markers = Array.from(block.querySelectorAll('[title]')).filter(e =>
          String(e.getAttribute('title') || '').trim().startsWith(label));
        k.requireValue(markers.length <= 1, '课程包含多个' + label + '字段，请使用单一网格视图');
        return String(markers[0]?.closest('p')?.textContent || '').replace(/[\uE000-\uF8FF]/g, '').trim();
      };
      const arrangement = field('节/周'), match = arrangement.match(/^\s*[（(]([^()（）]+节)[）)]\s*(.+)$/);
      k.requireValue(match, '课程缺少明确节次或周次');
      const active = k.weeks(match[2]), name = String(title.textContent || '').replace(/[★●]/g, '').trim();
      const teacher = field('教师'), room = field('上课地点');
      periods(match[1]).forEach(([first,last]) => courses.push(k.course(name, teacher, room, day[1], first, last, active)));
    });
    return {courses,omitted};
  }
  async function activate(prefix, module) {
    const html = await k.page(prefix + '/kbcx/xskbcx_cxXskbcxIndex.html?gnmkdm=' + module + '&layout=default');
    const doc = new DOMParser().parseFromString(html, 'text/html');
    k.requireValue(doc.querySelector('#xnm') && doc.querySelector('#xqm') &&
      !doc.querySelector('input[type="password"]'), '课表模块未激活，请先在网页完成登录和课表查询');
  }
  window.kagendaZhejiangParsers = {periods, parseApi, parseHtml};
  k.run(async () => {
    let profile = profiles[k.adapterId];
    // Only the exact school's published WebVPN mapping is accepted. Requests
    // stay on the current origin; no hostname substitution or cross-site fetch.
    if (k.adapterId === 'ext_shufezj_api' && location.hostname === 'webvpn.shufe-zj.edu.cn') {
      profile = {...profile, host:'webvpn.shufe-zj.edu.cn',
        prefix:'/https/77726476706e69737468656265737421fae0598869236045780dc4b6921b263199db675f'};
    }
    guard(profile);
    if (profile.html) {
      const result = parseHtml(); await k.finish(result.courses, result.omitted); return;
    }
    const selected = selectedTerm(), query = new URL(location.href).searchParams;
    const module = query.get('gnmkdm') || profile.module;
    k.requireValue(/^N\d{4,8}$/.test(module), '课表模块标识无效，请重新打开个人课表');
    const fields = {...selected};
    if (profile.fields === 'zjgsu') fields.gnmkdm = module;
    else {
      fields.kzlx = profile.fields === 'zjyc' ? 'cx' : 'ck';
      fields.xsdm = '';
      if (profile.fields === 'wzu') Object.assign(fields, {kclbdm:'',kclxdm:''});
      if (profile.fields === 'zwu') fields.kclbdm = '';
    }
    if (profile.activate) await activate(profile.prefix, module);
    const su = profile.fields === 'cjlu' ? query.get('su') : null;
    const endpoint = profile.prefix + '/kbcx/' + (profile.endpoint || 'xskbcx_cxXsgrkb.html') +
      '?gnmkdm=' + module + (su ? '&su=' + encodeURIComponent(su) : '');
    let response = await k.json(endpoint, fields);
    if (profile.activate && response && String(response.code) === '910') {
      await activate(profile.prefix, module);
      response = await k.json(endpoint, fields);
    }
    k.requireValue(response && typeof response === 'object' && !Array.isArray(response) &&
      response.success !== false && response.success !== 'false' &&
      (response.code == null || ['0','200'].includes(String(response.code))), '教务接口返回异常，请先在网页重新查询');
    const rows = response.kbList == null && profile.fields === 'zjgsu' ? response.items : response.kbList;
    k.requireValue(Array.isArray(rows), '未取得完整课程列表，请先在网页完成查询');
    const total = response.totalSize ?? response.total;
    if (total != null) k.requireValue(k.integer(total, 0, 5000, '课程总数') === rows.length, '课程列表不完整，未保存任何课程');
    const extra = response.sjkList;
    k.requireValue(extra == null || Array.isArray(extra), '实践课程安排格式无效');
    const current = selectedTerm();
    k.requireValue(current.xnm === selected.xnm && current.xqm === selected.xqm, '查询期间学期已切换，请重新导入');
    await k.finish(parseApi(rows), extra ? extra.length : 0);
  });
})();
