'use strict';
(() => {
  const one=(s,r=document)=>r.querySelector(s),all=(s,r=document)=>[...r.querySelectorAll(s)];
  const form=one('[data-admin-form]');if(!form)return;
  const key=form.dataset.adminForm,editing=form.dataset.editing==='true';
  all('input,select,textarea',form).forEach(input=>{if(input.matches(':disabled'))input.disabled=true;});
  const page=(group,title,fields)=>({group,title,fields});
  // Deliberately explicit. Changing field order cannot silently change the workflow.
  const definitions={
    movies:[page('Thể loại','Thể loại',['genreIds']),page('Thông tin','Tên và đường dẫn',['title','slug']),page('Thông tin','Phân loại và lịch phát hành',['ageRating','duration','releaseDate']),page('Thông tin','Mô tả phim',['description']),page('Thông tin','Đạo diễn',['directorIds']),page('Thông tin','Diễn viên',['actorIds']),page('Poster/Banner','Poster và banner',['poster','banner']),page('Trailer/Hiển thị','Trailer',['trailer']),page('Trailer/Hiển thị','Trạng thái hiển thị',['status','featured','active'])],
    showtimes:[page('Phim/Rạp/Phòng','Chọn phim, rạp và phòng',['movieId','cinemaId','roomId']),page('Thời gian','Ngày và giờ chiếu',['show-date','show-time']),page('Thời gian','Định dạng và phụ thu',['language','format','surcharge']),page('Kiểm tra','Kiểm tra lịch và mở bán',['status','preview'])],
    vouchers:[page('Ưu đãi','Mã và mức giảm',['code','kind','value']),page('Ưu đãi','Mô tả ưu đãi',['description']),page('Điều kiện','Điều kiện và lượt dùng',['minimum','maximum','quota','perUser']),page('Thời hạn','Thời hạn áp dụng',['startsAt','endsAt','active'])],
    contents:[page('Thông tin','Thông tin và liên kết',['kind','title','slug','link']),page('Nội dung','Nội dung đầy đủ',['body']),page('Xuất bản','Ảnh và xuất bản',['image','publishedAt','active'])],
    users:[page('Liên hệ','Thông tin liên hệ',['email','name','phone']),page('Phân quyền','Vai trò và rạp phụ trách',['role','cinemaId','enabled']),page('Bảo mật','Mật khẩu ban đầu',['initialPassword'])],
    cinemas:[page('Thông tin','Tên và thành phố',['name','city']),page('Thông tin','Địa chỉ và liên hệ',['address','phone']),page('Giới thiệu','Mô tả và hoạt động',['description','active'])],
    products:[page('Sản phẩm','Tên, loại và giá',['name','kind','price']),page('Mô tả','Mô tả sản phẩm',['description']),page('Hiển thị','Ảnh và hoạt động',['image','active'])]
  };
  const node=(tag,text,cls)=>{const el=document.createElement(tag);if(text!==undefined)el.textContent=text;if(cls)el.className=cls;return el;};
  const button=(text,run,cls='button secondary')=>{const b=node('button',text,cls);b.type='button';b.addEventListener('click',run);return b;};
  const controls=new Map(all('[data-field]',form).map(el=>[el.dataset.field,el]));
  const original=new Map();all('input[name],select[name],textarea[name]',form).filter(el=>(el.type!=='hidden'||['poster','banner','image'].includes(el.name))&&el.type!=='password'&&el.type!=='file').forEach(el=>original.set(el,el.type==='checkbox'?el.checked:el.value));
  let dirty=!!one('.form-error'),busy=false,uncertain=false,index=0,reached=0,reviewPage=0;
  const main=one('#main'),heading=one('#admin-form-heading'),savebar=one('.form-savebar',form),saves=all('button[type="submit"]',savebar);
  const locked=saves.every(b=>b.disabled);for(const b of saves)b.dataset.locked=String(b.disabled);
  const returnTo=one('[name="returnTo"]',form).value;
  form.noValidate=true;form.dataset.stepsReady='true';document.body.classList.add('form-focus');
  const frame=node('div',undefined,'admin-form-frame');main.append(frame);frame.append(heading);heading.querySelector('p').textContent=editing?'Chỉnh từng phần, xem lại trước khi lưu.':'Hoàn thành từng phần. Chỉ ghi dữ liệu khi bấm Lưu.';
  const progress=node('nav',undefined,'form-progress');progress.setAttribute('aria-label','Các phần của form');
  const mobileSelect=node('select');mobileSelect.setAttribute('aria-label','Chuyển phần nhập liệu');
  const message=node('div',undefined,'wizard-message');message.setAttribute('role','status');message.hidden=true;
  const serverError=one('.form-error',main);if(serverError){message.textContent=serverError.textContent;message.hidden=false;serverError.remove();}
  const lockMessage=one('#form-lock',main)?.textContent;
  const context=one('[data-form-context]',main),notice=one(':scope > .notice',main);const contextLine=node('p',context?context.querySelector('strong').textContent+' · '+(context.querySelector('span')?.textContent||''):key==='products'||key==='vouchers'?'Toàn hệ thống':key==='prices'?'Giá dùng chung cho các phòng trong rạp đang chọn':'','form-context-line');
  if(notice&&!contextLine.textContent)contextLine.textContent=notice.textContent;
  frame.append(contextLine,progress,message,form);
  // Preserve hidden fields and existing listeners, including upload, dates and CSRF.
  const stage=node('div',undefined,'form-stage');form.prepend(stage);
  let pages=definitions[key]?.map(p=>({...p,fields:p.fields.filter(f=>controls.has(f))})).filter(p=>p.fields.length)||[];
  const wizard=pages.length>0;
  progress.hidden=!wizard;
  if(!wizard)pages=[page('Thông tin','Thông tin '+heading.querySelector('h1').textContent.split('·').at(-1).trim(),[...controls.keys()])];
  pages.forEach((p,i)=>{
    p.el=node('section',undefined,'form-step');p.el.hidden=true;p.el.dataset.step=String(i);p.el.setAttribute('aria-labelledby','step-title-'+i);
    const title=node('h2',p.title);title.id='step-title-'+i;title.tabIndex=-1;p.el.append(title);const fields=node('div',undefined,'step-fields');p.el.append(fields);p.fields.forEach(f=>fields.append(controls.get(f)));stage.append(p.el);
  });
  if(wizard){const el=node('section',undefined,'form-step form-review');el.hidden=true;el.append(node('h2','Xác nhận thông tin'));el.querySelector('h2').tabIndex=-1;pages.push({group:'Xác nhận',title:'Xác nhận',fields:[],el});stage.append(el);}
  // Empty legacy groups remain in the same form so their hidden payload is still submitted.
  all(':scope > .form-group',form).forEach(el=>el.hidden=true);
  savebar.classList.add('wizard-footer');one(':scope > span',savebar).remove();
  const footerState=node('span',dirty?'Có dữ liệu cần kiểm tra':'Chưa lưu','wizard-state');footerState.setAttribute('role','status');
  const previous=button('← Quay lại',()=>show(index-1)),next=button('Kế tiếp →',()=>advance(),'button');
  const cancel=one('a',savebar);const actions=one('.actions',savebar);cancel.after(previous,next);savebar.prepend(footerState);
  frame.append(savebar);form.append(savebar);
  const groups=[...new Set(pages.map(p=>p.group))],groupButtons=[];
  groups.forEach((g,i)=>{const b=button((i+1)+'. '+g,()=>goGroup(g),'step-link');groupButtons.push(b);progress.append(b);mobileSelect.append(new Option(g,String(i)));});progress.append(mobileSelect);
  mobileSelect.addEventListener('change',()=>goGroup(groups[Number(mobileSelect.value)]));
  function announce(text,error=false){message.textContent=text;message.hidden=!text;message.classList.toggle('error',error);message.setAttribute('role',error?'alert':'status');}
  function formChanged(){dirty=true;footerState.textContent='Có thay đổi chưa lưu';}
  form.addEventListener('input',formChanged);form.addEventListener('change',formChanged);
  function validate(p){
    const bad=all('input,select,textarea',p.el).find(el=>!el.disabled&&el.type!=='hidden'&&!el.checkValidity());if(bad){show(pages.indexOf(p),false);const details=bad.closest('details');if(details)details.open=true;bad.focus();bad.reportValidity();return false;}
    const upload=one('.media-field[data-upload-state="busy"],.media-field[data-upload-state="failed"]',p.el);if(upload){show(pages.indexOf(p),false);announce('Hoàn tất tải file, thử lại hoặc bỏ lựa chọn mới trước khi tiếp tục.',true);one('[data-upload-retry]:not([hidden]),[data-upload-cancel]:not([hidden])',upload)?.focus();return false;}
    return true;
  }
  function validateAll(){for(const p of pages)if(!validate(p))return false;
    if(key==='movies'&&one('[name="active"]',form).checked&&one('[name="status"]',form).value!=='STOPPED'&&!pickers.get('genreIds')?.active()){
      show(0,false);announce('Chọn ít nhất một thể loại đang hoạt động trước khi hiển thị phim.',true);return false;
    }return true;
  }
  function show(target,focus=true){
    if(busy)return;document.body.classList.remove('form-keyboard');index=Math.max(0,Math.min(pages.length-1,target));reached=Math.max(reached,index);pages.forEach((p,i)=>p.el.hidden=i!==index);
    const current=pages[index],group=groups.indexOf(current.group);groupButtons.forEach((b,i)=>{const at=pages.findIndex(p=>p.group===groups[i]);b.disabled=!editing&&at>reached;b.setAttribute('aria-current',i===group?'step':'false');b.classList.toggle('completed',at<index);mobileSelect.options[i].disabled=b.disabled;});mobileSelect.value=String(group);
    const siblings=pages.filter(p=>p.group===current.group);const sub=siblings.length>1?' · '+(siblings.indexOf(current)+1)+'/'+siblings.length:'';
    progress.dataset.position=current.group+sub;previous.hidden=!wizard;previous.disabled=index===0;next.hidden=!wizard||index===pages.length-1;
    saves.forEach(b=>b.hidden=wizard&&index!==pages.length-1);
    if(wizard&&index===pages.length-1)renderReview();
    form.dataset.currentStep=String(index);form.dataset.currentGroup=current.group;if(lockMessage&&message.hidden)announce(lockMessage);updateUpload();
    if(focus)current.el.querySelector('h2').focus({preventScroll:true});
  }
  function advance(){if(!validate(pages[index]))return;if(index===pages.length-2&&!validateAll())return;announce('');show(index+1);}
  function goGroup(group){const target=pages.findIndex(p=>p.group===group);if(editing||target<=reached)show(target);}
  function updateUpload(){next.disabled=busy||!!one('.media-field[data-upload-state="busy"],.media-field[data-upload-state="failed"]',pages[index].el);saves.forEach(b=>b.disabled=locked||b.dataset.locked==='true'||busy||uncertain||form.dataset.uploadBlocked==='true');}
  form.addEventListener('upload-state',updateUpload);
  const pickers=new Map();
  all('[data-picker]',form).forEach(root=>{
    root.dataset.pickerReady='true';const name=root.dataset.pickerName,selected=new Map();all('input[type="checkbox"]:checked',root).forEach(i=>selected.set(i.value,{id:i.value,name:i.parentElement.querySelector('span').textContent,active:i.dataset.active==='true',available:true}));
    const old=one('.genre-options',root),hidden=node('div');hidden.hidden=true;old.replaceWith(hidden);
    const tools=node('div',undefined,'picker-tools'),search=node('input');search.type='search';search.placeholder='Tìm '+(name==='genreIds'?'thể loại':'người tham gia');search.setAttribute('aria-label',search.placeholder);
    const selectedOnly=button('Đã chọn',()=>{onlySelected=!onlySelected;current=0;selectedOnly.setAttribute('aria-pressed',String(onlySelected));load();},'picker-filter');selectedOnly.setAttribute('aria-pressed','false');tools.append(search,selectedOnly);
    const list=node('div',undefined,'picker-rows'),status=node('p',undefined,'picker-status');status.setAttribute('role','status');
    const nav=node('nav',undefined,'picker-pager'),label=node('span'),prev=button('Trước',()=>load(current-1),'picker-page'),after=button('Sau',()=>load(current+1),'picker-page');nav.append(label,prev,after);
    root.insertBefore(tools,hidden);hidden.after(list,nav,status);all('p.muted,.notice',root).forEach(p=>{if(p.id!=='genre-warning')p.hidden=true;});
    let current=0,onlySelected=false,generation=0,controller,timer;
    function sync(){hidden.replaceChildren();for(const id of selected.keys()){const input=node('input');input.type='hidden';input.name=name;input.value=id;hidden.append(input);}selectedOnly.textContent='Đã chọn ('+selected.size+')';const warning=one('#genre-warning');if(name==='genreIds'&&warning)warning.hidden=[...selected.values()].some(v=>v.active);}
    function render(rows,total){
      list.replaceChildren();for(const item of rows){const id=String(item.id),row=node('label',undefined,'picker-row'),check=node('input');check.type='checkbox';check.checked=selected.has(id);check.disabled=name==='genreIds'&&!item.active&&!check.checked;const text=node('span',item.name+(item.active?'':' · Đã ngừng'));check.addEventListener('change',()=>{if(check.checked)selected.set(id,item);else selected.delete(id);sync();formChanged();root.dispatchEvent(new Event('input',{bubbles:true}));if(onlySelected)load();});row.append(check,text);list.append(row);}
      if(!rows.length)list.append(node('p','Không có lựa chọn phù hợp.','muted'));prev.disabled=current===0;after.disabled=(current+1)*5>=total;label.textContent='Trang '+(current+1)+' · '+total+' mục';
    }
    async function load(target=0){
      current=Math.max(0,target);const request=++generation;controller?.abort();controller=new AbortController();status.textContent='Đang đọc danh mục…';
      try{const params=new URLSearchParams({kind:root.dataset.picker,q:onlySelected?'':search.value,page:String(onlySelected?0:current),selected:[...selected.keys()].join(',')});const data=await api('/api/admin/form-options?'+params,{signal:controller.signal});if(request!==generation)return;
        for(const item of data.selected)if(selected.has(String(item.id)))selected.set(String(item.id),item);sync();
        if(onlySelected){const rows=[...selected.values()].filter(v=>v.name.toLocaleLowerCase('vi').includes(search.value.toLocaleLowerCase('vi')));current=Math.min(current,Math.max(0,Math.ceil(rows.length/5)-1));render(rows.slice(current*5,(current+1)*5),rows.length);}else{current=data.page;render(data.rows,data.total);}
        status.textContent=selected.size+' đã chọn · Chưa ghi vào bản ghi phim.';
      }catch(error){if(request===generation&&error.name!=='AbortError')status.textContent='Giữ lựa chọn hiện có. '+error.message;}
    }
    search.addEventListener('input',e=>{e.stopPropagation();clearTimeout(timer);timer=setTimeout(()=>load(),180);});sync();load();
    pickers.set(name,{selected,active:()=>[...selected.values()].some(v=>v.active&&v.available!==false),refresh:()=>load(current)});
    window.addEventListener('focus',()=>load(current));document.addEventListener('visibilitychange',()=>{if(!document.hidden)load(current);else{controller?.abort();generation++;}});
    all('[data-refresh-catalog]',root).forEach(b=>b.addEventListener('click',()=>load(current)));
  });
  function valueOf(el){if(['poster','banner','image'].includes(el.name))return el.value?'Đã chọn ảnh':'Chưa có ảnh';if(el.type==='checkbox')return el.checked?'Có':'Không';if(el.tagName==='SELECT')return el.selectedOptions[0]?.textContent||'—';if(el.type==='password')return el.value?'Đã nhập':'Chưa nhập';if(el.dataset.money==='true'&&/^\d+$/.test(el.value))return new Intl.NumberFormat('vi-VN').format(BigInt(el.value))+' đ';return el.value||'—';}
  function renderReview(){
    const review=pages.at(-1).el;all('.review-content,.review-pager',review).forEach(e=>e.remove());
    const content=node('div',undefined,'review-content');const reviewGroups=groups.filter(g=>g!=='Xác nhận');reviewPage=Math.min(reviewPage,Math.max(0,Math.ceil(reviewGroups.length/4)-1));
    for(const g of reviewGroups.slice(reviewPage*4,reviewPage*4+4)){
      const card=node('section',undefined,'review-card'),title=node('h3',g),parts=pages.filter(p=>p.group===g),labels=[];let changed=false;
      for(const p of parts)for(const f of p.fields){if(pickers.has(f)){const picker=pickers.get(f);labels.push((f==='genreIds'?'Thể loại':f==='directorIds'?'Đạo diễn':'Diễn viên')+': '+[...picker.selected.values()].slice(0,2).map(v=>v.name).join(', ')+(picker.selected.size>2?'…':'')+' ('+picker.selected.size+' đã chọn)');const initial=initialSelections.get(f);if(initial!==[...picker.selected.keys()].sort().join(','))changed=true;continue;}
        const field=controls.get(f);for(const input of all('input,textarea,select',field)){if(input.type==='file'||input.type==='hidden'&&!['poster','banner','image'].includes(input.name)||!input.name)continue;
          if(input.type==='password'){labels.push('Mật khẩu: '+(input.value?'Đã nhập':'Chưa nhập'));continue;}
          const label=one('label',field)?.textContent||field.querySelector('span')?.textContent||input.name;
          labels.push(label.trim()+': '+(input.tagName==='TEXTAREA'?input.value.length+' ký tự':valueOf(input)));
          if(original.has(input)&&original.get(input)!==(input.type==='checkbox'?input.checked:input.value))changed=true;
        }
      }
      card.append(title);if(editing&&changed)title.append(node('span',' · Đã thay đổi','changed-badge'));
      card.append(node('p',labels.join(' · ')));const photos=parts.flatMap(p=>all('.media-preview',p.el));for(const photo of photos)if(photo.getAttribute('src')&&!photo.src.endsWith('no-poster.svg')){const copy=photo.cloneNode();copy.className='media-preview review-photo';card.append(copy);}
      card.append(button('Sửa '+g,()=>show(pages.indexOf(parts[0])),'review-edit'));content.append(card);
    }
    review.append(content);if(reviewGroups.length>4){const pager=node('nav',undefined,'review-pager');const p=button('Trước',()=>{reviewPage--;renderReview();}),n=button('Sau',()=>{reviewPage++;renderReview();});p.disabled=reviewPage===0;n.disabled=(reviewPage+1)*4>=reviewGroups.length;pager.append(node('span','Trang '+(reviewPage+1)),p,n);review.append(pager);}
  }
  const initialSelections=new Map([...pickers].map(([name,p])=>[name,[...p.selected.keys()].sort().join(',')]));
  const related=one('#genre-related',main);if(related){const dialog=node('dialog',undefined,'cine-dialog form-related-dialog'),open=button('Xem phim liên quan',()=>{dialog.showModal();close.focus();}),close=button('Đóng',()=>dialog.close());dialog.append(related,close);document.body.append(dialog);pages[0].el.append(open);dialog.addEventListener('close',()=>open.focus());}
  // Keep essential validation next to the field, including non-wizard short forms.
  const required={movies:['title','slug','duration','ageRating','status'],cinemas:['name','city'],rooms:['cinemaId','name','format'],genres:['name'],people:['name'],'movie-genres':['movieId','genreId'],credits:['movieId','personId','kind'],prices:['cinemaId','seatType','dayType','fromMinute','toMinute','amount'],products:['name','kind','price'],vouchers:['code','kind','value','minimum','maximum','quota','perUser','startsAt','endsAt'],contents:['kind','title','slug'],users:['email','name','role'],showtimes:['movieId','cinemaId','roomId','show-date','show-time','language','format','status','surcharge']};
  for(const f of required[key]||[]){const field=controls.get(f),input=field&&one('input:not([type="hidden"]),select,textarea',field);if(input)input.required=true;}
  const duration=one('[name="duration"]',form);if(duration){duration.min='1';duration.max='600';}all('input[data-money="true"]',form).forEach(el=>el.min='0');
  all('textarea',form).forEach(el=>{el.maxLength=el.name==='body'?20000:['biography'].includes(el.name)?255:['description'].includes(el.name)&&['movies','cinemas'].includes(key)?6000:255;});
  one('[name="slug"]',form)?.setAttribute('pattern','[a-z0-9-]+');
  for(const f of ['fromMinute','toMinute'])one('[name="'+f+'"]',form)?.setAttribute('pattern',f==='toMinute'?'(([01][0-9]|2[0-3]):[0-5][0-9]|24:00)':'([01][0-9]|2[0-3]):[0-5][0-9]');
  function markError(fields,text,passwordRequired=false){
    if(passwordRequired){const p=one('[name="initialPassword"]',form);if(p)p.value='';fields=[...fields,'initialPassword'];}
    const target=pages.findIndex(p=>p.fields.some(f=>fields.includes(f)||f==='show-time'&&fields.includes('startsAt')));show(target<0?pages.length-1:target,false);announce(text,true);
    const field=fields.map(f=>controls.get(f)).find(Boolean);if(field){const details=one('details',field);if(details)details.open=true;const input=one('input:not([type="hidden"]),select,textarea,button',field);input?.focus({preventScroll:true});}
  }
  form.addEventListener('keydown',e=>{if(e.key==='Enter'&&e.target.matches('input:not([type="checkbox"]):not([type="file"])'))e.preventDefault();});
  form.addEventListener('submit',async e=>{
    e.preventDefault();e.stopImmediatePropagation();if(busy||uncertain||locked)return;
    if(wizard&&index!==pages.length-1){advance();return;}if(!validateAll())return;
    busy=true;announce('Đang lưu…');footerState.textContent='Đang xử lý';const body=new FormData(form);if(e.submitter?.name)body.set(e.submitter.name,e.submitter.value);saves.forEach(b=>{b.disabled=true;b.setAttribute('aria-busy','true');});next.disabled=previous.disabled=true;stage.inert=progress.inert=true;
    const controller=new AbortController(),timeout=setTimeout(()=>controller.abort(),30000);
    try{
      const response=await fetch(form.action,{method:'POST',body,signal:controller.signal,headers:{'X-Admin-Form':'steps','Accept':'application/json'}});
      if(response.status===401||response.status===403){announce('Phiên hoặc mã bảo vệ form không còn hợp lệ. Nội dung được giữ; đăng nhập lại ở tab khác rồi tải lại form sau khi sao chép nội dung cần giữ.',true);return;}
      if(response.redirected)throw new Error('Phiên có thể đã hết hạn. Kiểm tra trạng thái và đăng nhập lại trước khi thử tiếp.');
      const data=await response.json();if(!response.ok){busy=false;stage.inert=progress.inert=false;markError(data.fields||[],data.message||'Chưa thể lưu.',data.passwordRequired);return;}
      if(!data.redirect||!data.redirect.startsWith('/admin/'))throw new Error('Không đọc được kết quả lưu.');
      dirty=false;form.dispatchEvent(new Event('form-saved'));location.assign(data.redirect);
    }catch(error){uncertain=true;announce('Chưa xác định kết quả lưu. '+error.message+' Không tự gửi lại. Mở danh sách để kiểm tra trước.',true);const link=node('a','Kiểm tra danh sách ↗','check-result');link.href=returnTo;link.target='_blank';link.rel='noopener';message.append(' ',link,button('Tiếp tục chỉnh sửa',()=>{uncertain=false;announce('Kiểm tra dữ liệu đã lưu trước khi gửi lại.');updateUpload();}));}
    finally{clearTimeout(timeout);busy=false;stage.inert=progress.inert=false;saves.forEach(b=>b.removeAttribute('aria-busy'));previous.disabled=index===0;updateUpload();if(dirty)footerState.textContent='Có thay đổi chưa lưu';}
  },true);
  window.addEventListener('beforeunload',e=>{if(dirty){e.preventDefault();e.returnValue='';}});
  const exitDialog=node('dialog',undefined,'cine-dialog form-exit-dialog'),exitTitle=node('h2','Còn thay đổi chưa lưu');exitTitle.id='form-exit-title';exitDialog.setAttribute('aria-labelledby',exitTitle.id);
  const stay=button('Quay lại nhập liệu',()=>exitDialog.close(),'button'),discard=button('Bỏ thay đổi và về danh sách',()=>{dirty=false;form.dispatchEvent(new Event('form-saved'));exitDialog.close();location.assign(returnTo);});
  const exitActions=node('div',undefined,'actions');exitActions.append(stay,discard);exitDialog.append(exitTitle,node('p','Các bước đang nhập chưa được lưu. File đã tải vẫn được giữ; bản ghi chỉ thay đổi sau khi Lưu thành công.'),exitActions);document.body.append(exitDialog);exitDialog.addEventListener('close',()=>cancel.focus());
  cancel.addEventListener('click',e=>{if(busy){e.preventDefault();return;}if(dirty){e.preventDefault();exitDialog.showModal();stay.focus();}});
  // Small previews can be enlarged without taking space from the form.
  form.addEventListener('click',e=>{if(!e.target.matches('.media-preview'))return;const dialog=node('dialog',undefined,'form-image-dialog'),image=e.target.cloneNode(),close=button('Đóng',()=>dialog.close());image.className='';dialog.append(image,close);document.body.append(dialog);dialog.addEventListener('close',()=>{dialog.remove();e.target.focus();},{once:true});dialog.showModal();close.focus();});
  all('.media-preview',form).forEach(img=>{img.tabIndex=0;img.setAttribute('role','button');img.setAttribute('aria-label','Mở ảnh lớn');img.addEventListener('keydown',e=>{if(e.key==='Enter'||e.key===' '){e.preventDefault();img.click();}});});
  const errorFields=(form.dataset.errorFields||'').split(',');show(0,false);if(!message.hidden)markError(errorFields,message.textContent);
  let keyboardControl;
  const keyboardBar=node('div',undefined,'keyboard-fields'),keyboardLabel=node('span');
  function keyboardFields(){return pages[index].fields.map(f=>controls.get(f)).filter(f=>one('input:not([type="hidden"]):not([type="checkbox"]):not([type="file"]),select,textarea',f));}
  function keyboardMove(delta){const fields=keyboardFields(),current=fields.indexOf(keyboardControl?.closest('[data-field]')),field=fields[current+delta];if(field)one('input:not([type="hidden"]):not([type="checkbox"]):not([type="file"]),select,textarea',field)?.focus();}
  keyboardBar.append(button('← Trường trước',()=>keyboardMove(-1),'keyboard-field-button'),keyboardLabel,button('Trường sau →',()=>keyboardMove(1),'keyboard-field-button'));
  const viewport=()=>{
    const height=window.visualViewport?.height||innerHeight;document.documentElement.style.setProperty('--form-height',height+'px');
    const compact=innerWidth<=850&&height<600&&keyboardControl&&pages[index].el.contains(keyboardControl);document.body.classList.toggle('form-keyboard',!!compact);
    all('.keyboard-field',form).forEach(f=>f.classList.remove('keyboard-field'));
    if(compact){const field=keyboardControl.closest('[data-field]');field?.classList.add('keyboard-field');pages[index].el.prepend(keyboardBar);const fields=keyboardFields(),position=fields.indexOf(field);keyboardLabel.textContent=(position+1)+'/'+fields.length;const buttons=all('button',keyboardBar);buttons[0].disabled=position<=0;buttons[1].disabled=position>=fields.length-1;}
  };
  form.addEventListener('focusin',e=>{if(e.target.matches('input:not([type="hidden"]):not([type="checkbox"]):not([type="file"]),select,textarea')){keyboardControl=e.target;viewport();}});
  viewport();window.visualViewport?.addEventListener('resize',viewport);window.addEventListener('resize',viewport);
  const lifecycleNote=document.getElementById('movie-lifecycle-note');
  if(lifecycleNote){
    const date=form.elements.namedItem('releaseDate'),status=form.elements.namedItem('status');
    const showLifecycle=()=>{const value=status.value==='STOPPED'?'Ngừng chiếu':date.value?(date.value>lifecycleNote.dataset.today?'Sắp chiếu':'Đang chiếu'):lifecycleNote.dataset.fallback;
      lifecycleNote.textContent=value+(date.value?' · Tự động theo ngày khởi chiếu (giờ Việt Nam).':' · Chưa có ngày khởi chiếu, đang giữ trạng thái cũ. Hãy bổ sung ngày.');};
    date.addEventListener('input',showLifecycle);status.addEventListener('change',showLifecycle);showLifecycle();
  }
})();
