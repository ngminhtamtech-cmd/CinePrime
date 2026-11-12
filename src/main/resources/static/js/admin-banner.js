'use strict';
// Banner editor: kind toggle, drag & drop upload, live 3:1 preview with the same wide/framed rules as the home page, compact schedule.
(() => {
  const one=(s,r=document)=>r.querySelector(s),all=(s,r=document)=>[...r.querySelectorAll(s)];
  const form=one('.banner-editor');if(!form)return;
  const wideMin=Number(form.dataset.wide)||1600,framedMin=Number(form.dataset.framed)||560;
  const image=one('#field-image'),movie=one('#banner-movie'),title=one('#banner-title'),clear=one('#be-clear-image');
  const frame=one('.be-frame'),preview=one('#be-preview-image'),empty=one('#be-preview-empty'),status=one('#be-preview-status'),saveState=one('#be-save-state');
  const kind=()=>one('input[name="kind"]:checked',form)?.value||'PROMO';
  let generation=0,dirty=false;

  function mode(w,h){return w>=wideMin&&w*9>=h*16?'wide':w>=framedMin&&w*3>=h*4?'framed':'none';}
  function source(){const own=image.value.trim();if(own)return {url:own,own:true};if(kind()==='MOVIE'){const opt=movie.selectedOptions[0];if(opt&&opt.value&&opt.dataset.image)return {url:opt.dataset.image,own:false};}return {url:'',own:false};}
  function say(text,tone){status.textContent=text;status.dataset.tone=tone||'';}
  function render(){
    const isMovie=kind()==='MOVIE';
    form.dataset.kind=isMovie?'MOVIE':'PROMO';
    all('[data-banner-kind]',form).forEach(el=>{el.hidden=el.dataset.bannerKind!==form.dataset.kind;});
    clear.hidden=!(isMovie&&image.value.trim());
    const opt=movie.selectedOptions[0];title.placeholder=isMovie&&opt&&opt.value?opt.textContent.trim():'';
    const src=source(),current=++generation;
    if(!src.url){preview.hidden=true;preview.removeAttribute('src');empty.hidden=false;frame.dataset.mode='empty';
      if(isMovie)say(movie.value?'Phim này chưa có ảnh ngang. Tải ảnh riêng ở bên trái, nếu không banner sẽ chưa hiện trên trang chủ.':'Chọn phim để xem ảnh banner của phim.',movie.value?'warn':'');
      else say('Banner khuyến mãi cần một ảnh ngang.','warn');return;}
    empty.hidden=true;const probe=new Image();
    probe.onload=()=>{if(current!==generation)return;const w=probe.naturalWidth,h=probe.naturalHeight,m=mode(w,h);preview.src=src.url;preview.hidden=false;frame.dataset.mode=m;
      const from=src.own?'Ảnh riêng':'Ảnh của phim';
      if(m==='wide')say(from+' '+w+'×'+h+'px · phủ kín banner.','ok');
      else if(m==='framed')say(from+' '+w+'×'+h+'px · nhỏ hơn '+wideMin+'px nên nằm giữa khung trên nền tối, không bị kéo giãn. Dùng ảnh 1920×640 để phủ kín.','info');
      else say(from+' '+w+'×'+h+'px · quá nhỏ hoặc là ảnh dọc, banner sẽ chưa hiện trên trang chủ.','warn');};
    probe.onerror=()=>{if(current!==generation)return;preview.hidden=true;empty.hidden=false;frame.dataset.mode='empty';say('Không đọc được ảnh. Tải lại ảnh khác.','warn');};
    probe.src=src.url;
  }

  // Drag & drop onto the upload zone; the shared uploader (admin-editor.js) listens for the file input's change.
  const zone=one('.be-dropzone'),file=one('.be-file');
  ['dragenter','dragover'].forEach(t=>zone.addEventListener(t,e=>{e.preventDefault();zone.classList.add('is-over');}));
  ['dragleave','dragend','drop'].forEach(t=>zone.addEventListener(t,()=>zone.classList.remove('is-over')));
  zone.addEventListener('drop',e=>{e.preventDefault();const files=e.dataTransfer?.files;if(!files||!files.length)return;const transfer=new DataTransfer();transfer.items.add(files[0]);file.files=transfer.files;file.dispatchEvent(new Event('change',{bubbles:true}));});

  clear.addEventListener('click',()=>{image.value='';image.dispatchEvent(new Event('input',{bubbles:true}));const cancel=one('[data-upload-cancel]',form);if(cancel)cancel.hidden=true;one('.upload-status',form).textContent='Đã gỡ ảnh riêng. Banner dùng ảnh của phim.';});
  image.addEventListener('input',render);movie.addEventListener('change',render);all('input[name="kind"]',form).forEach(r=>r.addEventListener('change',render));

  // Schedule: one summary line; the two date fields open on demand.
  const schedule=one('.be-schedule'),toggle=one('#be-schedule-toggle'),start=one('#banner-start'),end=one('#banner-end'),summary=one('#be-schedule-summary');
  const fmt=v=>{if(!v)return '';const [d,t]=v.split('T');const [y,mo,da]=d.split('-');return (t||'').slice(0,5)+' '+da+'/'+mo+'/'+y;};
  function scheduleText(){summary.textContent=(start.value?'Từ '+fmt(start.value):'Hiển thị ngay')+' · '+(end.value?'đến '+fmt(end.value):'không giới hạn thời gian');}
  function setOpen(open){schedule.dataset.open=String(open);toggle.setAttribute('aria-expanded',String(open));toggle.textContent=open?'Thu gọn':'Đặt lịch';if(open)start.focus();}
  toggle.addEventListener('click',()=>setOpen(schedule.dataset.open!=='true'));
  one('#be-schedule-clear').addEventListener('click',()=>{start.value='';end.value='';scheduleText();form.dispatchEvent(new Event('input'));});
  [start,end].forEach(i=>i.addEventListener('change',scheduleText));
  toggle.setAttribute('aria-expanded',String(schedule.dataset.open==='true'));toggle.textContent=schedule.dataset.open==='true'?'Thu gọn':'Đặt lịch';scheduleText();

  form.addEventListener('input',()=>{dirty=true;saveState.textContent='Có thay đổi chưa lưu';});form.addEventListener('change',()=>{dirty=true;saveState.textContent='Có thay đổi chưa lưu';});
  form.addEventListener('submit',()=>{dirty=false;});
  window.addEventListener('beforeunload',e=>{if(dirty){e.preventDefault();e.returnValue='';}});
  if(one('.form-error'))dirty=true;
  render();
})();
