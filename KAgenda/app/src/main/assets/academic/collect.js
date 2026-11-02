// KAgenda's local capture host. No credential storage, upload, title transport, or guessed semester.
(async function (token, type) {
  const sources = [], seen = new Set();
  let size = 0;
  function add(source) {
    if (!source || seen.has(source)) return;
    if (sources.length >= 16 || (size += source.length) > 1500000) throw Error('Timetable too large');
    seen.add(source); sources.push(source);
  }
  function visit(win, depth) {
    if (depth > 8) throw Error('Too many frames');
    let doc;
    try { doc = win.document; } catch (_) { return; }
    if (!doc || !doc.documentElement) return;
    const body = doc.body ? doc.body.innerText.trim() : '';
    add(type === 'urp_new' && body.startsWith('{') ? body : doc.documentElement.outerHTML);
    for (let i = 0; i < win.frames.length; i++) visit(win.frames[i], depth + 1);
  }
  try {
    visit(window, 0);
    if (type === 'urp_new' && !sources.some(s => s.trim().startsWith('{'))) {
      const url = new URL('student/courseSelect/thisSemesterCurriculum/callback', location.origin + '/');
      const response = await fetch(url.href, { credentials: 'same-origin', redirect: 'error' });
      if (!response.ok) throw Error('URP request failed');
      const body = await response.text();
      if (!body.trim().startsWith('{')) throw Error('Open timetable after login');
      add(body);
    }
    _kagendaWarehouse.dispatch(JSON.stringify({ token, action: 'academic', payload: { sources } }));
  } catch (_) {
    _kagendaWarehouse.dispatch(JSON.stringify({ token, action: 'academicError', payload: {} }));
  }
})(__TOKEN__, __PARSER__);
