// Derived from XFY9326/Schedule, Apache-2.0, SCUJCCCourseParser.kt and
// SCUJCCCourseProvider.kt, snapshot d631e15cb7d114eb1df9f53a40e3f76fc3a4ea29.
// Copyright notices: licenses/single-school-XFY-Apache-2.0.txt.
// KAgenda: only the explicit-weekday Table1 layout; strict fields and complete
// frames; preserve noncontiguous periods; no fixed term dates or timetable.
(function () {
  'use strict';
  const k = window.kagendaSchool;
  function parseDetails(lines) {
    k.requireValue(lines.length === 4, '课程详情结构发生变化');
    const m = lines[1].match(/^周([一二三四五六日天1-7])第([\d,，、\-]+)节\{第([\d,，、\-]+)周(?:\|([单双])周)?\}$/);
    k.requireValue(m, '不能识别完整的星期、节次与周次：' + lines[1]);
    const day = /[1-7]/.test(m[1]) ? Number(m[1]) : '一二三四五六日'.indexOf(m[1].replace('天','日')) + 1;
    const active = k.weeks(m[3] + (m[4] ? '(' + m[4] + ')' : ''));
    const sections = [];
    m[2].split(/[,，、]/).forEach(part => {
      const range = part.match(/^(\d+)(?:-(\d+))?$/);
      k.requireValue(range, '节次格式无效');
      const first = k.integer(range[1], 1, 24, '节次'), last = range[2] ? k.integer(range[2], first, 24, '节次') : first;
      for(let i=first;i<=last;i++) sections.push(i);
    });
    const groups = [];
    Array.from(new Set(sections)).sort((a,b)=>a-b).forEach(v=>{const g=groups[groups.length-1];if(g && g[1]+1===v)g[1]=v;else groups.push([v,v]);});
    return groups.map(g=>k.course(lines[0], lines[2], lines[3], day, g[0], g[1], active));
  }
  function tables(doc, depth) {
    k.requireValue(depth <= 3, '课表嵌套层级过多，请直接打开课表页');
    k.requireValue(!doc.querySelector('#Table6'), '此入口只支持显示明确星期的 Table1 课表；请切换课表布局或选择其他解析器');
    const result = Array.from(doc.querySelectorAll('#Table1'));
    const frames = Array.from(doc.querySelectorAll('iframe,frame'));
    k.requireValue(frames.length <= 16, '课表框架数量异常');
    frames.forEach(frame=>{
      let nested;
      try { nested = frame.contentDocument; } catch (_) { /* handled below */ }
      k.requireValue(nested && nested.documentElement, '无法读取课表框架，请直接打开课表页面后重试');
      result.push(...tables(nested, depth+1));
    });
    return result;
  }
  window.kagendaScujccParsers = {parseDetails};
  k.run(async()=>{
    k.host(['jwweb.cdjcc.edu.cn']);
    const found = tables(document,0);
    k.requireValue(found.length === 1, '请打开 Table1 完整学期课表，避免混合多个学期');
    const result = [];
    Array.from(found[0].querySelectorAll('tr > td')).forEach(cell=>{
      if (!Array.from(cell.children).some(c=>c.tagName === 'BR')) return;
      const clone = cell.cloneNode(true); clone.querySelectorAll('font').forEach(n=>n.remove());
      clone.innerHTML.split(/<br\s*\/?>\s*<br\s*\/?>/i).forEach(chunk=>{
        if(!chunk.trim())return;
        const lines=chunk.split(/<br\s*\/?>/i).map(s=>new DOMParser().parseFromString(s,'text/html').body.textContent.trim());
        while(lines.length && !lines[lines.length-1])lines.pop();
        if(lines.every(s=>!s))return;
        result.push(...parseDetails(lines));
      });
    });
    await k.finish(result,0);
  });
})();
