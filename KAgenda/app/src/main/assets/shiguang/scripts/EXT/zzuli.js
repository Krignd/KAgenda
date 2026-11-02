// Derived from LingNc/stnet_syllabus, MIT, snapshot
// 02e9ff9ff806dcf51f3c475769ed1e64d19b898f, scripts/get_schedule.py and
// internal/parser/list_parser.go. Original license: single-school-ZZULI-MIT.txt.
// KAgenda: direct official origin in the signed-in WebView; actual term API
// (including term 0), no calendar-based guesses/AI, strict complete list,
// explicit weeks and all meetings, preserve room text and show native preview.
(function(){
  'use strict';
  const k=window.kagendaSchool;
  function parse(value,name,teacher) {
    const result=[];
    String(value).trim().split(/[；;]/).forEach(part=>{
      const line=part.trim();if(!line)return;
      const m=line.match(/^(.+?周(?:[（(][单双][）)])?)\s+([一二三四五六日天])\[(\d+)(?:-(\d+))?\]\s*(.*)$/);
      k.requireValue(m,'无法识别列表排课：'+line);
      const day='一二三四五六日'.indexOf(m[2].replace('天','日'))+1;
      const room=m[5].replace(/[（(]\d+[）)]\s*$/,'').trim();
      result.push(k.course(name,teacher,room,day,m[3],m[4]||m[3],k.weeks(m[1])));
    });
    k.requireValue(result.length>0,'缺少明确排课');return result;
  }
  window.kagendaZzuliParsers={parse};
  k.run(async()=>{
    k.host(['jwgl.zzuli.edu.cn']);
    k.requireValue(/^\/(?:student|wsxk)\//.test(location.pathname),'请在教务系统打开个人课表后再采集');
    const term=await k.json('/frame/desk/showYearTerm4User.action',{});
    k.requireValue(term && /^\d{4}$/.test(String(term.xn)) && /^[01]$/.test(String(term.xq_m)) && String(term.xnxqDesc||'').trim(),'未取得明确的当前学期');
    const accepted=await k.api.showAlert('导入当前学期',String(term.xnxqDesc)+'。此入口读取普通课程列表；整周实践环节不转为普通节次。','继续');
    k.requireValue(accepted===true,'已取消导入');
    let student=String(window._loginid||'').trim();
    if(!/^\d{4,30}$/.test(student)) {
      const info=await k.page('/frame/home/js/SetMainInfo.jsp');
      const match=info.match(/\bvar\s+_loginid\s*=\s*["'](\d{4,30})["']/);
      k.requireValue(match,'未取得登录用户标识，请重新登录');student=match[1];
    }
    const params=btoa('xn='+term.xn+'&xq='+term.xq_m+'&xh='+student);
    const html=await k.page('/wsxk/xkjg.ckdgxsxdkchj_data_exp10319.jsp?params='+encodeURIComponent(params));
    k.requireValue(/<table\b/i.test(html),'官方导出未返回 HTML 列表，请检查登录状态');
    const doc=new DOMParser().parseFromString(html,'text/html');
    const tables=Array.from(doc.querySelectorAll('table')).filter(t=>{
      const first=t.querySelector('tr');return first && /上课班级代码/.test(first.textContent);
    });
    k.requireValue(tables.length===1,'未找到唯一的课程列表，请使用列表格式导出');
    const result=[];let omitted=0;
    const rows=Array.from(tables[0].querySelectorAll('tr')).slice(1);
    rows.forEach(row=>{
      const cells=Array.from(row.children).filter(n=>n.tagName==='TD');
      if(!cells.length)return;
      k.requireValue(cells.length>=10,'课程列表列数发生变化');
      const name=cells[1].textContent.trim().replace(/^\[[^\]]+\]\s*/,''),teacher=cells[5].textContent.replace(/\s*\[\d+\]\s*/g,' ').trim();
      const value=cells[9].textContent.trim();if(!value){omitted++;return;}
      result.push(...parse(value,name,teacher));
    });
    await k.finish(result,omitted);
  });
})();
