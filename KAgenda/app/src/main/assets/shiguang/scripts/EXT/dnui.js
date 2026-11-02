// Derived from LuBanQAQ/dnui-wakeup-export, MIT, Copyright (c) 2026 LuBan.
// https://github.com/LuBanQAQ/dnui-wakeup-export/blob/1035f4b4ceb7ba3a801325c57547b4def8a697b6/dnui-wakeup-export.user.js
// KAgenda: WebView instead of GM/unsafeWindow; strict fields and weeks;
// preserve selected semester; stage a preview rather than downloading CSV.
(function () {
  'use strict';
  const k = window.kagendaSchool;
  k.run(async () => {
    k.host(['xk.neusoft.edu.cn']);
    k.requireValue(location.pathname.replace(/\/$/, '') === '/xsxk/web/curriculum', '请打开“我的课表”页面');
    let list = window.scheduleList;
    if (!Array.isArray(list)) {
      for (const script of Array.from(document.querySelectorAll('script'))) {
        const match = String(script.textContent || '').match(/\bvar\s+scheduleList\s*=\s*(\[[\s\S]*?\]);/);
        if (match) { list = JSON.parse(match[1]); break; }
      }
    }
    k.requireValue(Array.isArray(list) && list.length > 0 && list.length <= 5000, '没有读取到课表数据，请等待页面加载后重试');
    const terms = new Set(list.map(r => String(r.XNXQ || '').trim()).filter(Boolean));
    k.requireValue(terms.size <= 1, '页面混有不同学期的数据，请重新选择学期');
    const result = list.map(row => {
      const teacher = String(row.SKJS || '').replace(/^.*?\]/, '');
      return k.course(row.KCM, teacher, row.YPSJDD, row.SKXQ, row.KSJC, row.JSJC, k.weeks(row.SKZCMC || row.SKZC));
    });
    await k.finish(result, 0);
  });
})();
