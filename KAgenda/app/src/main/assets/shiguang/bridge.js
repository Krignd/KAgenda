(function(token) {
  const pending = Object.create(null); let counter = 0;
  window.__kagendaSgReply = function(id, ok, value) {
    const callback = pending[id]; if (!callback) return; delete pending[id];
    ok ? callback.resolve(value) : callback.reject(new Error(String(value)));
  };
  function send(action, payload, promise) {
    const id = promise ? 'sg_' + (++counter) : '';
    const message = JSON.stringify({token:token, action:action, id:id, payload:payload || {}});
    if (!promise) { _kagendaWarehouse.dispatch(message); return; }
    return new Promise((resolve,reject) => { pending[id] = {resolve,reject}; _kagendaWarehouse.dispatch(message); });
  }
  const api = {
    showAlert:(title,content,confirm)=>send('alert',{title:String(title),content:String(content),confirm:String(confirm || '')},true),
    showPrompt:(title,tip,value,validator)=>send('prompt',{title:String(title),tip:String(tip),value:String(value || ''),validator:String(validator || '')},true),
    showSingleSelection:(title,items,index)=>send('selection',{title:String(title),items:String(items),index:index == null ? -1 : index},true),
    saveImportedCourses:(data)=>send('courses',{data:String(data)},true),
    savePresetTimeSlots:(data)=>send('times',{data:String(data)},true),
    saveCourseConfig:(data)=>send('config',{data:String(data)},true),
    saveComboSchedule:(data)=>send('combo',{data:String(data)},true),
    showToast:(text)=>send('toast',{text:String(text)},false),
    notifyTaskCompletion:()=>send('complete',{},false)
  };
  window.shiguangBridgePromise = api; window.shiguangBridge = api;
  window.AndroidBridgePromise = api; window.AndroidBridge = api;
})(__TOKEN__);
