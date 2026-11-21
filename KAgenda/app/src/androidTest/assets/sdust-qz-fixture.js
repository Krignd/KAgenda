// KAgenda-owned synthetic HTML and protocol fixtures. No school samples or network.
(function(){
  const accepted=['regular','second','two-teachers','reorder','empty-remark','empty-table','en','fr'];
  const rejected=['cancel','one-week','term-label','server-term','server-no-selected','missing-teacher','teacher-conflict','room','week','parity','section','section-reverse','unknown-label','partial','unknown-node','empty','unknown-table','remark','unknown-row','weekday','merged-cell','second-failure','identity-change','raw-change','day-change','dom-change','short-weeks','bad-monday','http','redirect','wrong-url','encoding','replacement','iframe','login','pages'];
  const escape=value=>String(value).replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  function page(fault='',reads=0,live=false){
    const second=fault==='second',term=fault==='server-term'&&!live?'2025-2026-2':second?'2026-2027-2':'2026-2027-1';
    const label=term.startsWith('2025')?'2025-2026学年第二学期':'2026-2027学年第'+(second||fault==='term-label'?'二':'一')+'学期';
    const sel=fault==='server-no-selected'&&!live?'':' selected',week=fault==='one-week'?'2':'',weekLabel=week?'第2周':'全部';
    function item(alternate=false,bad=false){
      const name=alternate?'合成同名课程':'合成高等数学',teacher=fault==='missing-teacher'||bad?'':alternate?'另一合成教师':'合成甲、合成乙';
      const room=fault==='room'?'未安排':alternate?'合成校区二 第五教学楼B202(校区二)':'合成校区一 第一教学楼A101(校区一)';
      const time=fault==='week'?'1-8(周)未知[1-2节]':fault==='parity'?'2(周)(单周)[1-2节]':fault==='section'?'1-8(周)08:00-09:30':fault==='section-reverse'?'1-8(周)[5-3节]':'1-8(周)(单周),12(周)[1-2,5节]';
      const group=fault==='raw-change'&&reads>1?'CHANGED':'合成教学班';
      return '<li><div>'+name+'</div>'+(teacher?'老师:'+teacher+'<br>':'')+(fault==='teacher-conflict'?'教师:冲突教师<br>':'')+'地点:'+room+'<br>时间:'+time+'<br>教学班:'+group+'<br>班级:合成行政班'+(fault==='unknown-label'?'<br>未知安排:合成实训':'')+'</li>';
    }
    let entries=fault==='empty'?'':item();if(['two-teachers','reorder'].includes(fault))entries+=item(true);if(fault==='reorder'&&reads>1)entries=item(true)+item();if(fault==='partial')entries+=item(true,true);if(fault==='unknown-node')entries+='<div>未知课程结构</div>';
    const list=fault==='empty'?'':'<span>合成高等数学</span><ul class="qz-toolitiplists">'+entries+'</ul>';
    const day=fault==='day-change'&&reads>1?3:2;let cells='<td>第一大节 (01,02小节) 08:00-09:30</td>';for(let n=1;n<=7;n++)cells+='<td'+(fault==='merged-cell'&&n===day?' rowspan=2':'')+'>'+(n===day?list:'')+'</td>';
    const header='<tr><th>节次</th>'+['一','二','三','四','五','六',fault==='weekday'?'八':'日'].map(n=>'<th>星期'+n+'</th>').join('')+'</tr>';
    const remark=fault==='remark'?'<tr><td>备注</td><td colspan=7>合成未排实践</td></tr>':fault==='empty-remark'?'<tr><td colspan=8>备注</td></tr>':'';
    const unknownRow=fault==='unknown-row'?'<tr><td>第二大节</td><td></td><td>未知非空课程</td><td></td><td></td><td></td><td></td><td></td></tr>':'';
    const id=fault==='identity-change'&&reads>1?'SYN-OTHER':'SYN-SELF';
    return '<html><body><h1>个人课表信息</h1><p>学号: '+id+'</p><select id=xnxq01id name=xnxq01id><option value="'+term+'"'+sel+'>'+label+'</option>'+(term.startsWith('2025')?'':'<option value="2025-2026-2">2025-2026学年第二学期</option>')+'</select><select id=zc name=zc><option value="'+week+'"'+sel+'>'+weekLabel+'</option></select><button>查询</button><table id=kbtable><thead>'+header+'</thead><tbody><tr>'+cells+'</tr>'+unknownRow+remark+'</tbody></table>'+(fault==='unknown-table'?'<table id=dataTables><tr><td>未排课程 合成实践</td></tr></table>':fault==='empty-table'?'<table></table>':'')+(fault==='pages'?'<p>共2页</p>':'')+(fault==='iframe'?'<iframe src="about:blank"></iframe>':'')+(fault==='login'?'<input type=password>':'')+'</body></html>';
  }
  function install(fault=''){
    window.__messages=[];window.__done=false;window.__reads=0;window.__requests=[];window.kagendaUiLanguage=['en','fr'].includes(fault)?fault:'zh';
    window._kagendaWarehouse={dispatch:function(raw){const m=JSON.parse(raw);window.__messages.push(m);if(m.action==='complete')window.__done=true;if(m.id){let answer=true;if(m.action==='selection')answer=fault==='cancel'?null:0;if(m.action==='prompt'){answer=/^(?:开学|Monday|Lundi)/.test(m.payload.title)?fault==='bad-monday'?'2026-09-08':'2026-09-07':fault==='short-weeks'?'11':'18';if(fault==='dom-change'&&!/^(?:开学|Monday|Lundi)/.test(m.payload.title))document.querySelector('#xnxq01id').value='2025-2026-2';}window.__kagendaSgReply(m.id,true,answer);}}};
    window.fetch=async function(raw,init){const u=new URL(raw),fields=new URLSearchParams(init.body);window.__reads++;window.__requests.push(u.href);
      if(u.origin!=='https://jwglxt.sdust.edu.cn'||u.pathname!=='/jsxsd/xskb/xskb_list.do'||u.search||init.method!=='POST'||init.credentials!=='same-origin'||init.redirect!=='error'||init.cache!=='no-store'||init.referrerPolicy!=='same-origin')throw Error('Wrong exact SDUST request');
      if(fields.size!==10||fields.get('xnxq01id')!==(fault==='second'?'2026-2027-2':'2026-2027-1')||fields.get('zc')!==''||fields.get('sfFD')!=='1'||fields.get('baseUrl')!=='/jsxsd'||fields.get('xsflMapListJsonStr')!==encodeURIComponent('讲课学时,实践学时,实验学时,上机学时,课内实践学时')||!['viweType','showallprint','showkchprint','showkink','showfzmprint'].every(k=>fields.get(k)==='0'))throw Error('Wrong all-course semester form');
      if(fault==='second-failure'&&window.__reads>1)throw Error('Synthetic second read failure');const text=page(fault,window.__reads,false);
      return {ok:fault!=='http',redirected:fault==='redirect',url:fault==='wrong-url'?'https://wrong.invalid/':u.href,headers:{get:()=>fault==='encoding'?'text/html; charset=GBK':'text/html; charset=UTF-8'},text:async()=>fault==='replacement'?text+'\ufffd':text};
    };
  }
  function check(fault=''){
    const messages=window.__messages,data=action=>messages.filter(m=>m.action===action).at(-1)?.payload?.data||'';
    if(rejected.includes(fault)){if(['courses','config','times'].some(a=>data(a)))throw Error('Partial data staged');if(!messages.some(m=>m.action==='alert'))throw Error('Missing rejection alert');return {rejected:true};}
    if(!data('courses'))throw Error('No courses: '+JSON.stringify(messages));const courses=JSON.parse(data('courses')),config=JSON.parse(data('config'));if(courses.length!==(['two-teachers','reorder'].includes(fault)?4:2)||config.semesterTotalWeeks!==18||config.semesterStartDate!=='2026-09-07'||data('times')||window.__requests.length!==2)throw Error('Incomplete native staging');
    if(!courses.every(c=>JSON.stringify(c.weeks)==='[1,3,5,7,12]'&&c.day===2&&c.position.includes('教学楼')&&c.extraInfo.includes('合成教学班')&&c.extraInfo.includes('合成行政班'))||JSON.stringify(courses).includes('SYN-SELF'))throw Error('Lost course facts');
    if(['en','fr'].includes(fault)&&messages.some(m=>['selection','prompt'].includes(m.action)&&/[\u3400-\u9fff]/.test(m.payload.title)))throw Error('Untranslated prompt');return {rejected:false,courses:data('courses'),config:data('config'),times:data('times')};
  }
  window.kagendaSdustFixture={accepted,rejected,page,install,check};
})();
