package com.kstudio.agenda.data

object SchedulePageCapture {
    /** Executed only after the user's capture action. No bridge, cookies, storage or form values. */
    val script = """
        (function() {
          var parts = [], inaccessible = 0;
          function text(node) { return String(node.innerText || node.textContent || '').trim(); }
          function capture(doc) {
            var body = doc.body;
            if (!body) return;
            var clone = body.cloneNode(true);
            clone.querySelectorAll('script,style,noscript,input,textarea,select,button,nav,header,footer,[hidden],[aria-hidden="true"]').forEach(function(n){n.remove();});
            var tables = clone.querySelectorAll('table');
            if (tables.length) {
              tables.forEach(function(table) {
                var rows = [];
                table.querySelectorAll('tr').forEach(function(row) {
                  var cells = [];
                  Array.from(row.children).forEach(function(cell) {
                    if (/^(TD|TH)$/.test(cell.tagName)) cells.push({text:text(cell),rows:cell.rowSpan||1,cols:cell.colSpan||1});
                  });
                  rows.push(cells);
                });
                parts.push(JSON.stringify({table:rows}));
              });
            } else parts.push(text(clone));
            doc.querySelectorAll('iframe,frame').forEach(function(frame) {
              try { if (frame.contentDocument && frame.contentDocument.body) capture(frame.contentDocument); else inaccessible++; }
              catch(e) { inaccessible++; }
            });
          }
          capture(document);
          var content = parts.filter(Boolean).join('\n\n');
          if (content.length > 30000) return JSON.stringify({error:'too_long'});
          return JSON.stringify({text:content,blocked:inaccessible});
        })();
    """.trimIndent()
}
