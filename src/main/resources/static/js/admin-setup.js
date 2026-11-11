'use strict';
(() => {
  if(!document.body.classList.contains('admin-compact'))return;
  const one=(s,r=document)=>r.querySelector(s),all=(s,r=document)=>[...r.querySelectorAll(s)];
  const labelType=t=>({STANDARD:'Standard · Ghế thường',VIP:'VIP',COUPLE:'Couple · Ghế đôi'}[t]||t);
  const labelState=t=>({AVAILABLE:'Còn trống',HELD:'Đang giữ',SOLD:'Đã bán',MAINTENANCE:'Bảo trì'}[t]||t);
  function element(tag,text,cls){const node=document.createElement(tag);if(text!==undefined)node.textContent=text;if(cls)node.className=cls;return node;}
  function options(select,rows,name){if(!select)return;const selected=select.value,fragment=document.createDocumentFragment();fragment.append(new Option('— Chọn —',''));for(const row of rows){const option=new Option(row[name]+(row.active===false?' · Đã ngừng':''),String(row.id));if(row.cinemaId!=null)option.dataset.cinema=String(row.cinemaId);fragment.append(option);}if(selected&&!rows.some(r=>String(r.id)===selected))fragment.append(new Option('Không còn khả dụng · #'+selected,selected));select.replaceChildren(fragment);select.value=selected;}
  const form=one('.ops-edit-form'),genreBox=one('#movie-genre-options'),context=one('[data-form-context]');let catalogController,catalogGeneration=0;
  async function refreshCatalog(){
    if(!form||document.hidden)return;const request=++catalogGeneration;catalogController?.abort();catalogController=new AbortController();
    const cinema=one('#field-cinemaId',form)||one('#editor-cinema',form),room=one('#field-roomId',form)||one('#editor-room',form);
    const params=new URLSearchParams(),chosenCinema=cinema?.value||context?.dataset.cinema,chosenRoom=room?.value||one('[name="setupRoomId"]',form)?.value||context?.dataset.room;
    if(chosenCinema)params.set('cinemaId',chosenCinema);if(chosenRoom)params.set('roomId',chosenRoom);
    try{const data=await api('/api/admin/setup-context?'+params,{signal:catalogController.signal});if(request!==catalogGeneration)return;
      if(genreBox&&form.dataset.stepsReady!=='true'){const selected=new Set(all('input:checked',genreBox).map(n=>n.value));const fragment=document.createDocumentFragment();for(const genre of data.genres){const label=element('label',undefined,'check'),input=element('input');input.type='checkbox';input.name='genreIds';input.value=String(genre.id);input.checked=selected.has(input.value);input.dataset.active=String(genre.active);input.disabled=!genre.active&&!input.checked;label.append(input,element('span',genre.name+(genre.active?'':' · Đã ngừng')));fragment.append(label);selected.delete(input.value);}for(const id of selected){const label=element('label',undefined,'check'),input=element('input');input.type='checkbox';input.name='genreIds';input.value=id;input.checked=true;label.append(input,element('span','Thể loại không còn khả dụng · #'+id));fragment.append(label);}genreBox.replaceChildren(fragment);genreWarning();}
      options(cinema,data.cinemas,'name');options(room,data.rooms,'name');options(one('#editor-movie',form),data.movies,'title');
      if(context){one('strong',context).textContent=data.cinemaName;one('span',context).textContent=data.roomName;context.dataset.cinema=data.cinemaId;context.dataset.room=data.roomId;const link=one('a',context);if(link){link.hidden=!data.roomId;link.href='/admin/setup/rooms/'+data.roomId;}}
      const status=one('#catalog-refresh-status');if(status)status.textContent='Đã đọc danh mục mới. Nội dung và lựa chọn đang nhập được giữ.';
      form.dispatchEvent(new Event('catalog-refreshed'));
    }catch(error){if(request===catalogGeneration&&error.name!=='AbortError'){const status=one('#catalog-refresh-status');if(status)status.textContent='Chưa làm mới được danh mục: '+error.message;else toast(error.message);}}
  }
  function genreWarning(){if(form?.dataset.stepsReady==='true')return;const warning=one('#genre-warning');if(warning&&genreBox)warning.hidden=all('input:checked',genreBox).some(i=>i.dataset.active==='true');}genreBox?.addEventListener('change',genreWarning);
  if(form){
    all('[data-refresh-catalog]').forEach(b=>b.addEventListener('click',refreshCatalog));window.addEventListener('focus',refreshCatalog);
    document.addEventListener('visibilitychange',()=>{if(!document.hidden)refreshCatalog();else{catalogController?.abort();catalogGeneration++;}});
    const cinema=one('#field-cinemaId',form),room=one('#field-roomId',form);
    cinema?.addEventListener('change',()=>{if(room)room.value='';const flow=one('[name="setupRoomId"]',form);if(flow)flow.value='';if(context){context.dataset.room='';context.dataset.cinema=cinema.value;}refreshCatalog();});
    room?.addEventListener('change',()=>{const flow=one('[name="setupRoomId"]',form);if(flow)flow.value=room.value;if(context)context.dataset.room=room.value;refreshCatalog();});if(context)refreshCatalog();
  }
})();
