// Protocol/field mapping derived from drfccv/ZFJW-backend (MIT),
// SLctfTeam/USST-Lecture-Table-Calendar (MIT), and Jnove/ZJUCoursetrace (MIT).
// SUSE endpoint/form fields: suse-edu-cn/SUSE-OAA-APP (MIT).
// ZJU full academic-year request: Harry-Linner/CampusOS (MIT).
// Pinned paths, dates, copyrights and original licenses are retained in
// academic/school-provenance.json and licenses/single-school-*.txt.
// KAgenda changes: manual WebView login, selected semester from the real page,
// same-origin requests, strict fields/parity, no guessed Monday/first period/
// 1-16 weeks, no name-only merging, and a native confirmation preview.
(function () {
  'use strict';
  const k = window.kagendaSchool;
  function periods(value) {
    const result = []; const text = String(value || '').replace(/节/g, '').replace(/第/g, '').trim();
    k.requireValue(text && text.length < 150, '缺少明确节次');
    text.split(/[,，、]/).forEach(part => {
      const m = part.trim().match(/^(\d+)(?:\s*[-~]\s*(\d+))?$/);
      k.requireValue(m, '无法识别节次：' + part);
      const first = k.integer(m[1], 1, 24, '节次'), last = m[2] ? k.integer(m[2], first, 24, '节次') : first;
      for (let i = first; i <= last; i++) result.push(i);
    });
    const values = Array.from(new Set(result)).sort((a,b) => a-b), groups = [];
    values.forEach(v => { const last = groups[groups.length - 1]; if (last && last[1] + 1 === v) last[1] = v; else groups.push([v,v]); });
    return groups;
  }
  function decode(value) {
    return new DOMParser().parseFromString(String(value), 'text/html').body.textContent.trim();
  }
  function parse(rows, zju) {
    k.requireValue(Array.isArray(rows) && rows.length <= 5000, '课表数据格式无效');
    const result = []; let omitted = 0;
    rows.forEach(row => {
      if (zju && String(row.sfyjskc) === '1') { omitted++; return; }
      let active = k.weeks(row.zcd);
      if (zju && row.dsz != null) {
        const flag = k.integer(row.dsz, 0, 2, '单双周标记');
        if (flag !== 2) active = active.filter(w => flag === 0 ? w % 2 === 1 : w % 2 === 0);
        k.requireValue(active.length > 0, '周次与单双周标记不一致');
      }
      const name = row.kcmc || (row.kcb ? decode(String(row.kcb).split(/<br\s*\/?>/i)[0]) : '');
      const startIndex=result.length;
      const teacher = row.xm || row.jsxm || '';
      let room = row.cdmc || '';
      if (k.adapterId === 'ext_usst') k.requireValue(typeof row.xm === 'string' && row.xm.trim() &&
        typeof row.cdmc === 'string' && row.cdmc.trim(), '上理工课程缺少教师或教室');
      if (k.adapterId === 'ext_usst' && row.xqmc != null) {
        k.requireValue(typeof row.xqmc === 'string', '校区字段格式无效');
        const campus = row.xqmc.trim();
        if (campus && !String(room).includes(campus)) room = campus + ' ' + room;
      }
      if (zju) {
        const first = k.integer(row.djj, 1, 24, '起始节次'), count = k.integer(row.skcd, 1, 24, '连续节数');
        result.push(k.course(name, teacher, room, row.xqj, first, first + count - 1, active));
      } else periods(row.jc || row.jcs).forEach(([first,last]) => result.push(k.course(name, teacher, room, row.xqj, first, last, active)));
      if(k.adapterId==='ext_usst') {
        const details=[];
        for(const [key,label] of [['xqmc','校区'],['kcxz','课程性质'],['khfsmc','考核方式'],['kclb','课程类别']]) {
          if(row[key]==null || row[key]==='')continue;
          k.requireValue(typeof row[key]==='string' && row[key].length<=500,'课程附加字段无效');
          details.push(label+'：'+row[key].trim());
        }
        if(details.length)result.slice(startIndex).forEach(course=>{course.extraInfo=details.join('\n');});
      }
    });
    return {courses: result, omitted};
  }
  window.kagendaZfParsers = {parse, periods};
  k.run(async () => {
    const zju = k.adapterId === 'ext_zju';
    if (zju) k.host(['zdbk.zju.edu.cn']);
    else if (k.adapterId === 'ext_usst') k.host(['jwgl.usst.edu.cn']);
    else if (k.adapterId === 'ext_suse') k.host(['jwgl.suse.edu.cn']);
    else k.requireValue(location.protocol === 'https:', '请打开 HTTPS 教务个人课表页面');
    const year = document.querySelector('#xnm'), term = document.querySelector('#xqm');
    k.requireValue(year && term && String(year.value || '').trim() && String(term.value || '').trim(), '请先进入个人课表页并选择学年学期');
    k.requireValue(/\/kbcx\//.test(location.pathname), '请先进入个人课表查询页面');
    const prefix = location.pathname.slice(0, location.pathname.indexOf('/kbcx/'));
    const fields = {xnm: year.value, xqm: term.value};
    if (zju) {
      // ZJU can accept a start year yet silently return another season's
      // timetable. Preserve the explicit academic-year label required by the
      // source protocol instead of forwarding only the start year.
      const selectedYear = String(year.value).trim().match(/^(\d{4})(?:-(\d{4}))?$/);
      k.requireValue(selectedYear, '请选择明确的学年');
      const start = k.integer(selectedYear[1], 2000, 2199, '学年');
      k.requireValue(!selectedYear[2] || Number(selectedYear[2]) === start + 1, '学年起止年份不一致');
      fields.xnm = start + '-' + (start + 1);
      fields.xqmmc = String(term.value).includes('|') ? String(term.value).split('|')[1] : term.value;
      fields.xxqf = '0'; fields.xsfs = '0';
      const captcha = document.querySelector('[name="captcha_value"]'); fields.captcha_value = captcha ? captcha.value : '';
    } else {
      fields.kzlx = k.adapterId === 'ext_suse' ? 'ck' : 'cx';
      if (k.adapterId === 'ext_suse') Object.assign(fields, {xsdm:'',kclbdm:'',kclxdm:''});
    }
    const endpoint = zju ? '/kbcx/xskbcx_cxXsKb.html?gnmkdm=N253508' : '/kbcx/xskbcx_cxXsgrkb.html?gnmkdm=N2151';
    const su = zju ? new URL(location.href).searchParams.get('su') : null;
    const response = await k.json(prefix + endpoint + (su ? '&su=' + encodeURIComponent(su) : ''), fields);
    const rows = response && (response.kbList || (zju && response.kblist));
    k.requireValue(Array.isArray(rows), '未取得课表；如网页需要验证码，请先在网页完成查询');
    const result = parse(rows, zju);
    const extra = response.sjkList;
    k.requireValue(extra == null || Array.isArray(extra), '其他课程安排格式无效');
    if(k.adapterId==='ext_usst')k.requireValue(!extra || extra.length===0,'存在尚未核实的实践课程列表，未导入部分课表');
    await k.finish(result.courses, result.omitted + (extra ? extra.length : 0));
  });
})();
