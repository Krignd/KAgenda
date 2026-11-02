// KAgenda Jiangsu undergraduate ports; protocol and field mappings adapted from
// mico-v/usts-cli and Ye111ow/cumt-schedule-desktop (MIT).
// NJTech request/fields: GiuseppeLR/njtech_timetable (Apache-2.0).
// CUMT Xuhai request/fields: Albiehao/cumtxh-jwxt-api (MIT).
// Pinned revisions, paths, dates and source hashes: academic/school-provenance.json.
// Original notices: licenses/jiangsu-school-bundle.txt.
// Changes: visible school login, explicit selected semester, exact origin/path,
// strict weeks and split periods, no guessed dates/times, all-or-nothing preview.
(function () {
  'use strict';
  const k = window.kagendaSchool;
  const profiles = {
    ext_usts_api: {host:'jwgl.usts.edu.cn', prefix:'/jwglxt', endpoint:'xskbcx_cxXsgrkb.html', https:true},
    ext_cumt_api: {host:'jwxt.cumt.edu.cn', prefix:'/jwglxt', endpoint:'xskbcx_cxXsKb.html'},
    ext_njtech_api: {host:'jwgl.njtech.edu.cn', prefix:'', endpoint:'xskbcx_cxXsgrkb.html', https:true},
    ext_cumtxh_api: {host:'jwxt.cumtxh.cn', prefix:'/jwglxt', endpoint:'xskbcx_cxXsgrkb.html'}
  };
  function selectedTerm() {
    const year = document.querySelector('#xnm'), term = document.querySelector('#xqm');
    k.requireValue(year && term, '请先打开个人课表，并选择学年学期');
    const xnm = String(year.value || '').trim(), xqm = String(term.value || '').trim();
    k.requireValue(/^\d{4}$/.test(xnm) && /^(3|12|16)$/.test(xqm), '请选择页面提供的明确学年学期，不支持全部学期');
    return {xnm,xqm};
  }
  function periods(value) {
    const text = String(value == null ? '' : value).trim().replace(/[第节\s]/g, '')
      .replace(/[，、]/g, ',').replace(/[－—–~～至]/g, '-');
    k.requireValue(text && text.length < 150, '课程缺少明确节次');
    const numbers = [];
    text.split(',').forEach(part => {
      const m = part.match(/^(\d+)(?:-(\d+))?$/);
      k.requireValue(m, '无法识别课程节次：' + part);
      const first = k.integer(m[1], 1, 24, '起始节次'), last = m[2] ? k.integer(m[2], first, 24, '结束节次') : first;
      for (let n = first; n <= last; n++) numbers.push(n);
    });
    const groups = [];
    Array.from(new Set(numbers)).sort((a,b)=>a-b).forEach(n => {
      const previous = groups[groups.length-1];
      if (previous && previous[1]+1 === n) previous[1] = n;
      else groups.push([n,n]);
    });
    return groups;
  }
  function rowRecord(row) {
    k.requireValue(row && typeof row === 'object' && !Array.isArray(row), '存在无法识别的课程记录');
    k.requireValue(typeof row.kcmc === 'string' && row.kcmc.trim(), '课程缺少名称');
  }
  function parse(response, usts, njtech) {
    k.requireValue(response && typeof response === 'object' && !Array.isArray(response) &&
      response.success !== false && response.success !== 'false' &&
      (response.code == null || ['0','200'].includes(String(response.code))), '教务接口返回异常，请先在网页重新查询');
    let rows = usts ? response.sjkList : response.kbList;
    k.requireValue(Array.isArray(rows) && rows.length <= 5000, '未取得完整课程列表，请先在网页完成查询');
    const total = response.totalSize ?? response.total;
    if (total != null) k.requireValue(k.integer(total,0,5000,'课程总数') === rows.length, '课程列表不完整，未保存任何课程');
    const other = usts ? response.kbList : response.sjkList;
    k.requireValue(other == null || (Array.isArray(other) && other.length <= 5000), '其他课程安排格式无效');
    // USTS's reviewed personal endpoint returns sjkList. A nonempty kbList is
    // a different, unverified response shape; never discard its courses.
    if (usts) k.requireValue(!other || other.length === 0, '课表返回了尚未核实的另一课程列表，请使用网页导出后导入');
    const untimedRows=new Set();
    if (njtech) {
      const practice=response.jxhjkcList;
      k.requireValue(practice==null || Array.isArray(practice), '教学环节课程格式无效');
      (other || []).concat(practice || []).forEach(row=>untimedRows.add(row));
      rows=rows.concat(other || [],practice || []);
      k.requireValue(rows.length<=5000, '完整课程列表超过范围');
    }
    const courses = []; let omitted = usts || njtech ? 0 : (other ? other.length : 0);
    rows.forEach(row => {
      rowRecord(row);
      const dayMissing = row.xqj == null || String(row.xqj).trim() === '';
      const periodValue=njtech ? row.jcs : row.jc;
      const periodMissing = periodValue == null || String(periodValue).trim() === '';
      if ((usts || (njtech && untimedRows.has(row))) && dayMissing && periodMissing) { omitted++; return; }
      const active = k.weeks(usts ? row.qsjsz : row.zcd);
      periods(periodValue).forEach(([first,last]) => courses.push(k.course(
        row.kcmc, usts ? row.jsxm : row.xm, row.cdmc, row.xqj, first, last, active)));
    });
    return {courses,omitted};
  }
  window.kagendaJiangsuParsers = {periods,parse};
  k.run(async () => {
    const profile = profiles[k.adapterId];
    k.requireValue(profile && location.hostname === profile.host && !location.port &&
      !location.username && !location.password &&
      (location.protocol === 'https:' || (!profile.https && location.protocol === 'http:')),
      '请在本校教务地址完成登录后重试');
    k.requireValue(location.pathname.startsWith(profile.prefix+'/kbcx/'), '请先进入本校个人课表查询页面');
    const selected = selectedTerm(), usts = k.adapterId === 'ext_usts_api', njtech=k.adapterId==='ext_njtech_api';
    const fields = {...selected};
    if (usts) Object.assign(fields,{kzlx:'ck',xsdm:'',kclbdm:'',kclxdm:''});
    if (k.adapterId === 'ext_cumtxh_api') Object.assign(fields,{kzlx:'ck',xsdm:''});
    if (njtech) {
      fields.kzlx='ck';
      for (const name of ['xsdm','kclbdm','kclxdm']) fields[name]=String(document.querySelector('#'+name)?.value || '');
    }
    const response = await k.json(profile.prefix+'/kbcx/'+profile.endpoint+(njtech ? '' : '?gnmkdm=N2151'), fields);
    const parsed = parse(response,usts,njtech), current = selectedTerm();
    k.requireValue(current.xnm === selected.xnm && current.xqm === selected.xqm, '查询期间学期已切换，请重新导入');
    if (njtech) for (const name of ['xsdm','kclbdm','kclxdm'])
      k.requireValue(String(document.querySelector('#'+name)?.value || '')===fields[name], '查询期间课表筛选已切换，请重新导入');
    await k.finish(parsed.courses,parsed.omitted);
  });
})();
