'use strict';
(() => {
  const one=(s,r=document)=>r.querySelector(s),all=(s,r=document)=>[...r.querySelectorAll(s)];
  // Shared by full-page forms and pop-up forms (admin-dialog.js): auto-upload, retry/cancel, preview, and block saving while busy.
  function bindMedia(form){
  const states=new Map();
  function updateSave(){const blocked=[...states.values()].some(s=>s.busy||s.failed);form.dataset.uploadBlocked=String(blocked);for(const [field,state] of states)field.dataset.uploadState=state.busy?'busy':state.failed?'failed':'ready';all('button[type="submit"]',form).forEach(save=>{if(!save.dataset.initiallyLocked)save.dataset.initiallyLocked=String(save.disabled);if(!form.dataset.submitting)save.disabled=blocked||save.dataset.initiallyLocked==='true';});form.dispatchEvent(new Event('upload-state'));}
  all('.media-field',form).forEach(field=>{
    const file=one('input[type="file"]',field),target=document.getElementById(field.dataset.target),status=one('.upload-status',field),retry=one('[data-upload-retry]',field),cancel=one('[data-upload-cancel]',field);
    const state={busy:false,failed:false,generation:0,controller:null,original:target.value};states.set(field,state);
    function preview(){if(field.dataset.video==='true')return;let image=one('.media-preview',field);if(!image){image=document.createElement('img');image.className='media-preview';image.alt='Ảnh đang chọn';field.append(image);}image.src=target.value||'/images/no-poster.svg';}
    async function upload(){
      if(!file.files.length)return;
      const selected=file.files[0],generation=++state.generation;state.controller?.abort();state.controller=new AbortController();state.busy=true;state.failed=false;retry.hidden=true;cancel.hidden=false;status.textContent='Đang tải '+selected.name+'…';updateSave();
      try{
        const body=new FormData();body.append('file',selected);body.append('kind',field.dataset.kind);
        const token=one('meta[name="csrf-token"]'),header=one('meta[name="csrf-header"]');
        const response=await fetch('/admin/media',{method:'POST',headers:{[header.content]:token.content},body,signal:state.controller.signal});
        if(response.redirected)throw new Error('Phiên hết hạn. Đăng nhập lại để tải ảnh.');
        const data=await response.json().catch(()=>({message:'Không đọc được phản hồi upload.'}));
        if(!response.ok)throw new Error(data.message||'Không tải được file.');
        if(generation!==state.generation)return;
        target.value=data.url;target.dispatchEvent(new Event('input',{bubbles:true}));preview();status.textContent='Đã tải '+selected.name+'. '+(data.warning?data.warning+' ':'')+'Bấm Lưu thay đổi để áp dụng.';
      }catch(error){if(generation!==state.generation)return;state.failed=true;retry.hidden=false;status.textContent=error.message+' Có thể Thử lại hoặc Bỏ lựa chọn mới để giữ dữ liệu cũ.';}
      finally{if(generation===state.generation){state.busy=false;updateSave();}}
    }
    file.addEventListener('change',upload);retry.addEventListener('click',upload);
    cancel.addEventListener('click',()=>{state.generation++;state.controller?.abort();state.busy=state.failed=false;file.value='';target.value=state.original;target.dispatchEvent(new Event('input',{bubbles:true}));retry.hidden=cancel.hidden=true;status.textContent='Đã giữ lại ảnh/video trước khi chỉnh sửa.';preview();updateSave();});
    if(field.dataset.video!=='true')preview();
  });
  form.addEventListener('submit',event=>{if([...states.values()].some(s=>s.busy||s.failed)){event.preventDefault();if(!form.dataset.adminForm)one('.upload-status',form)?.scrollIntoView({block:'center'});}},true);
  return {blocked:()=>[...states.values()].some(s=>s.busy||s.failed)};
  }
  window.AdminMedia={bind:bindMedia};
  const form=one('.ops-edit-form:not(.dialog-form)');if(!form)return;
  bindMedia(form);
  const title=form.dataset.movieForm==='true'?one('#field-title',form):null,slug=one('#field-slug');
  if(title&&slug){let manual=!!one('input[name="id"]',form)||!!slug.value;slug.addEventListener('input',()=>manual=true);title.addEventListener('input',()=>{if(!manual)slug.value=title.value.normalize('NFD').replace(/[\u0300-\u036f]/g,'').replace(/[đĐ]/g,'d').toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'');});}
  all('[data-trailer-preview]',form).forEach(button=>button.addEventListener('click',()=>{try{openTrailer(document.getElementById(button.dataset.trailerTarget).value, 'Xem thử trailer');}catch(error){toast(error.message);}}));

  if(form.dataset.showtimeForm!=='true')return;
  const cinema=one('#editor-cinema'),room=one('#editor-room'),movie=one('#editor-movie'),date=one('#editor-date'),time=one('#editor-time'),start=one('#field-startsAt'),preview=one('#showtime-preview'),body=one('#preview-rows'),summary=one('#preview-summary'),proposal=one('#preview-proposal'),status=one('#preview-status'),prev=one('#preview-prev'),next=one('#preview-next'),label=one('#preview-page');
  let page=0,generation=0,controller=null,timer;
  function syncTime(){if(start.dataset.locked!=='true')start.value=date.value&&time.value?date.value+'T'+time.value:'';}
  function rooms(){for(const option of [...room.options])if(option.value){option.hidden=option.disabled=option.dataset.cinema!==cinema.value;}if(room.selectedOptions[0]?.disabled)room.value='';}
  async function load(target=0){
    syncTime();const request=++generation;controller?.abort();controller=new AbortController();prev.disabled=next.disabled=true;
    if(!cinema.value||!room.value||!date.value){body.replaceChildren();label.textContent='';proposal.textContent='';status.textContent='';summary.textContent='Chọn rạp, phòng và ngày để xem lịch phòng.';return;}
    status.textContent='Đang kiểm tra lịch phòng…';
    const params=new URLSearchParams({cinemaId:cinema.value,roomId:room.value,date:date.value,page:String(target)});
    if(movie.value)params.set('movieId',movie.value);if(start.value)params.set('startsAt',start.value);if(preview.dataset.editing)params.set('editingId',preview.dataset.editing);
    try{
      const data=await api('/api/admin/showtime-preview?'+params,{signal:controller.signal});if(request!==generation)return;
      page=data.data.page;prev.disabled=page===0;next.disabled=!data.data.hasMore;label.textContent='Trang '+(page+1)+' · '+data.data.total+' suất';
      summary.textContent=data.conflictCount?'Có '+data.conflictCount+' suất xung đột (đã xét cả 15 phút vệ sinh). Các suất xung đột được ưu tiên ở đầu danh sách.':'Chưa phát hiện xung đột trong dữ liệu hiện tại. Backend kiểm tra lại khi Lưu.';
      summary.classList.toggle('conflict',data.conflictCount>0);proposal.textContent=data.proposal.interval?'Dự kiến '+data.proposal.interval+' · Hết vệ sinh: '+data.proposal.cleanupUntil:'Chọn phim và giờ để tính kết thúc dự kiến.';
      const readiness=one('#preview-readiness');if(readiness){readiness.textContent=data.readiness?.canOpen?'Đủ điều kiện mở bán theo cấu hình hiện tại. Backend kiểm tra lại khi Lưu.':(data.readiness?.errors||[]).join(' ');readiness.className=data.readiness?.canOpen?'action-notice':'notice';const fix=one('#preview-fix-setup');fix.hidden=!room.value;fix.href='/admin/setup/rooms/'+room.value+'?step=prices';}
      const fragment=document.createDocumentFragment();for(const item of data.data.rows){const row=document.createElement('tr');if(item.conflict)row.className='conflict-row';for(const [name,value] of [['Phim / Trạng thái',item.movie+' · '+item.status+(item.conflict?' · Trùng lịch':'')],['Thời gian',item.when+' · '+item.interval],['Hết vệ sinh',item.cleanupUntil]]){const td=document.createElement('td');td.dataset.label=name;td.textContent=value;row.append(td);}fragment.append(row);}body.replaceChildren(fragment);status.textContent=data.data.rows.length?'':'Phòng chưa có suất trong phạm vi đang xem.';
    }catch(error){if(request===generation&&error.name!=='AbortError')status.textContent='Không kiểm tra được lịch: '+error.message+' Vui lòng kiểm tra lại trước khi Lưu.';}
  }
  function schedule(){clearTimeout(timer);timer=setTimeout(()=>load(0),250);}
  cinema.addEventListener('change',()=>{room.value='';rooms();load(0);});for(const field of [room,movie,date,time]){field.addEventListener('change',schedule);field.addEventListener('input',schedule);}prev.addEventListener('click',()=>load(page-1));next.addEventListener('click',()=>load(page+1));form.addEventListener('submit',syncTime,true);form.addEventListener('catalog-refreshed',schedule);rooms();load(0);
})();
