'use strict';
// Add/edit short records in a pop-up on their list page. The form fragment comes from /admin/manage/{key}/dialog
// and posts to the same /admin/manage/{key}/save endpoint as the full-page form (X-Admin-Form: steps → JSON).
(() => {
  const one=(s,r=document)=>r.querySelector(s),all=(s,r=document)=>[...r.querySelectorAll(s)];
  if(!one('[data-dialog-form]'))return;
  const node=(tag,text,cls)=>{const el=document.createElement(tag);if(text!==undefined)el.textContent=text;if(cls)el.className=cls;return el;};
  const slugify=v=>v.normalize('NFD').replace(/[̀-ͯ]/g,'').replace(/[đĐ]/g,'d').toLowerCase().replace(/[^a-z0-9]+/g,'-').replace(/^-|-$/g,'').slice(0,120);
  const dialog=node('dialog',undefined,'admin-form-dialog');dialog.setAttribute('aria-labelledby','admin-dialog-title');document.body.append(dialog);
  let opener=null,form=null,dirty=false,busy=false,uncertain=false,media=null;

  function message(text){const shell=node('div',undefined,'dialog-shell dialog-message');const head=node('header',undefined,'dialog-head');const title=node('h2',text.title);title.id='admin-dialog-title';head.append(title);const p=node('p',text.body);const actions=node('footer',undefined,'dialog-actions');const close=node('button','Đóng','button secondary');close.type='button';close.dataset.dialogClose='';actions.append(close);shell.append(head,p,actions);return shell;}
  async function open(url,trigger){
    opener=trigger;dirty=false;busy=false;uncertain=false;form=null;media=null;
    dialog.className='admin-form-dialog';dialog.replaceChildren(message({title:'Đang mở form…',body:'Đang tải dữ liệu mới nhất.'}));if(!dialog.open)dialog.showModal();
    try{
      const response=await fetch(url,{headers:{Accept:'text/html'}});
      if(response.redirected||response.status===401||response.status===403)throw new Error('Phiên đăng nhập đã hết hạn. Tải lại trang để đăng nhập lại.');
      if(!response.ok)throw new Error('Không mở được form (mã '+response.status+'). Tải lại trang rồi thử lại.');
      const template=document.createElement('template');template.innerHTML=await response.text();
      const shell=template.content.querySelector('.dialog-shell');if(!shell)throw new Error('Không đọc được form. Tải lại trang rồi thử lại.');
      dialog.replaceChildren(shell);setup(shell);
    }catch(error){dialog.replaceChildren(message({title:'Chưa mở được form',body:error.message}));one('[data-dialog-close]',dialog)?.focus();}
  }
  function setup(shell){
    form=one('form',shell);const key=form.dataset.dialogFormKey,editing=form.dataset.editing==='true';dialog.classList.add('is-'+key);
    media=window.AdminMedia?.bind(form)||null;
    const state=one('.dialog-state',shell);
    form.addEventListener('input',()=>{dirty=true;if(state&&!busy)state.textContent='Có thay đổi chưa lưu';});form.addEventListener('change',()=>{dirty=true;});
    const title=one('[name="title"]',form),slug=one('[name="slug"]',form);
    if(title&&slug&&!editing){let manual=false;slug.addEventListener('input',()=>manual=true);title.addEventListener('input',()=>{if(!manual)slug.value=slugify(title.value);});}
    form.addEventListener('submit',submit);
    const first=all('input:not([type="hidden"]):not([type="file"]),select,textarea',form).find(el=>!el.disabled&&el.offsetParent!==null);(first||one('[data-dialog-close]',shell))?.focus();
  }
  function showError(text){const box=one('[data-dialog-error]',dialog);if(!box)return;box.textContent=text;box.hidden=!text;}
  function clearFields(){all('.form-field.has-error,.dialog-check.has-error',form).forEach(f=>{f.classList.remove('has-error');const err=one('.field-error',f);if(err){err.hidden=true;err.textContent='';}all('[aria-invalid]',f).forEach(i=>i.removeAttribute('aria-invalid'));});}
  function markField(name,text){
    const field=one('[data-field="'+CSS.escape(name)+'"]',form);if(!field)return null;field.classList.add('has-error');
    const err=one('.field-error',field);if(err&&text){err.textContent=text;err.hidden=false;}
    const input=one('input:not([type="hidden"]):not([type="file"]),select,textarea',field);if(input)input.setAttribute('aria-invalid','true');
    const details=field.closest('details');if(details)details.open=true;return input;
  }
  async function submit(event){
    event.preventDefault();if(busy||uncertain)return;
    showError('');clearFields();
    if(media?.blocked()){showError('Đợi ảnh tải xong, hoặc bấm Thử lại / Bỏ lựa chọn mới trước khi lưu.');return;}
    const slug=one('[name="slug"]',form),title=one('[name="title"]',form);if(slug&&!slug.value.trim()&&title)slug.value=slugify(title.value);
    const bad=all('input,select,textarea',form).find(el=>!el.disabled&&el.type!=='hidden'&&el.type!=='file'&&!el.checkValidity());
    if(bad){const input=markField(bad.closest('[data-field]')?.dataset.field||bad.name,bad.validationMessage);(input||bad).focus();return;}
    const submitButton=one('button[type="submit"]',form),state=one('.dialog-state',dialog);
    busy=true;submitButton.disabled=true;submitButton.setAttribute('aria-busy','true');if(state)state.textContent='Đang lưu…';
    const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),30000);
    try{
      const response=await fetch(form.action,{method:'POST',body:new FormData(form),signal:controller.signal,headers:{'X-Admin-Form':'steps','Accept':'application/json'}});
      if(response.status===401||response.status===403||response.redirected){showError('Phiên đăng nhập hoặc mã bảo vệ form đã hết hạn. Nội dung vẫn còn trong form; hãy sao chép phần cần giữ, đăng nhập lại ở tab khác rồi thử lại.');return;}
      const data=await response.json();
      if(!response.ok){
        showError(data.message||'Chưa thể lưu. Kiểm tra lại thông tin.');
        if(data.passwordRequired){const p=one('[name="initialPassword"]',form);if(p)p.value='';}
        const fields=(data.fields||[]).concat(data.passwordRequired?['initialPassword']:[]);let focus=null;for(const f of fields){const input=markField(f,'');focus=focus||input;}
        (focus||one('[data-dialog-error]',dialog)).focus?.();if(state)state.textContent='Chưa lưu';return;
      }
      if(!data.redirect||!data.redirect.startsWith('/admin/'))throw new Error('Không đọc được kết quả lưu.');
      dirty=false;if(state)state.textContent='Đã lưu. Đang tải lại danh sách…';location.assign(data.redirect);
    }catch(error){
      uncertain=true;showError('Chưa xác định được kết quả lưu ('+(error.name==='AbortError'?'quá thời gian chờ':error.message)+'). Không tự gửi lại để tránh tạo trùng. Tải lại danh sách để kiểm tra trước khi nhập lại.');
      const reload=node('button','Tải lại danh sách','button secondary');reload.type='button';reload.addEventListener('click',()=>{dirty=false;location.reload();});one('[data-dialog-error]',dialog).append(' ',reload);
    }finally{clearTimeout(timer);busy=false;if(!uncertain){submitButton.disabled=false;}submitButton.removeAttribute('aria-busy');}
  }
  function close(){dirty=false;dialog.close();}
  function requestClose(){
    if(busy)return;if(!dirty||!form){close();return;}
    if(one('.dialog-confirm',dialog))return;
    const bar=node('div',undefined,'dialog-confirm');bar.setAttribute('role','alertdialog');bar.setAttribute('aria-label','Bỏ thay đổi chưa lưu?');
    const stay=node('button','Tiếp tục nhập','button secondary'),discard=node('button','Bỏ thay đổi','button danger');stay.type=discard.type='button';
    stay.addEventListener('click',()=>{bar.remove();form.querySelector('input:not([type="hidden"]),select,textarea')?.focus();});discard.addEventListener('click',close);
    bar.append(node('span','Còn thay đổi chưa lưu. Đóng form sẽ bỏ các thay đổi này.'),stay,discard);one('.dialog-actions',dialog).before(bar);stay.focus();
  }
  dialog.addEventListener('click',e=>{if(e.target.closest('[data-dialog-close]'))requestClose();});
  dialog.addEventListener('cancel',e=>{e.preventDefault();requestClose();});
  // The browser restores focus to whatever was focused before showModal(); put it back on the button that opened the pop-up.
  dialog.addEventListener('close',()=>{dialog.replaceChildren();form=null;media=null;const back=opener;setTimeout(()=>{if(back&&back.isConnected)back.focus();},0);});
  window.addEventListener('beforeunload',e=>{if(dialog.open&&dirty){e.preventDefault();e.returnValue='';}});
  document.addEventListener('click',e=>{
    const trigger=e.target.closest('[data-dialog-form]');if(!trigger||e.defaultPrevented||e.button!==0||e.ctrlKey||e.metaKey||e.shiftKey||e.altKey)return;
    e.preventDefault();trigger.closest('details[open]')?.removeAttribute('open');open(trigger.dataset.dialogForm,trigger);
  });
})();
