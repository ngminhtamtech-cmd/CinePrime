'use strict';
// Single-page movie form: live preview, release window helper, people search, inline checks.
// Uploads, slug suggestion and trailer preview come from admin-editor.js; the backend re-validates everything.
(() => {
  const form=document.getElementById('movie-form');if(!form)return;
  const one=(s,r=form)=>r.querySelector(s),all=(s,r=form)=>[...r.querySelectorAll(s)];
  const field=name=>form.elements.namedItem(name);
  const DAY=86400000;
  const parse=v=>{if(!/^\d{4}-\d{2}-\d{2}$/.test(v||''))return null;const [y,m,d]=v.split('-').map(Number);return Date.UTC(y,m-1,d);};
  const iso=t=>new Date(t).toISOString().slice(0,10);
  const vn=t=>{const d=new Date(t);return String(d.getUTCDate()).padStart(2,'0')+'/'+String(d.getUTCMonth()+1).padStart(2,'0')+'/'+d.getUTCFullYear();};
  const today=parse(form.dataset.today)??Date.UTC(new Date().getFullYear(),new Date().getMonth(),new Date().getDate());
  const showFirst=parse(form.dataset.showFirst),showLast=parse(form.dataset.showLast),showCount=Number(form.dataset.showCount||0);
  const title=field('title'),duration=field('duration'),release=field('releaseDate'),end=field('endDate'),description=field('description'),poster=field('poster');

  // Inline messages next to a field (client hints; the server message wins after a failed save).
  function hint(name,message){const box=one('[data-field="'+name+'"]');if(!box)return;box.classList.toggle('has-error',!!message);let note=one(':scope > .mf-field-error',box);if(!message){note?.remove();return;}if(!note){note=document.createElement('p');note.className='mf-field-error';box.append(note);}note.textContent=message;}

  // 1. Duration in hours.
  const durationText=one('#mf-duration-text');
  function showDuration(){const n=Number(duration.value);durationText.textContent=n>0?(n>=60?Math.floor(n/60)+' giờ'+(n%60?' '+(n%60)+' phút':''):n+' phút'):'';}

  // 2. Release window: summary, timeline, quick ranges, status pill.
  const statusPill=document.getElementById('mf-status'),summary=one('#mf-window-summary'),todayMark=one('#mf-tl-today');
  const segs={coming:one('.mf-tl-seg.coming'),showing:one('.mf-tl-seg.showing'),ended:one('.mf-tl-seg.ended')};
  function lifecycle(){const r=parse(release.value),e=parse(end.value);if(r==null)return {key:'',label:'Chưa có ngày'};if(today<r)return {key:'coming',label:'Sắp chiếu'};if(e!=null&&today>e)return {key:'ended',label:'Đã kết thúc'};return {key:'showing',label:'Đang chiếu'};}
  function windowCheck(){
    const r=parse(release.value),e=parse(end.value);hint('releaseDate','');hint('endDate','');
    if(r!=null&&e!=null&&e<r){hint('endDate','Ngày kết thúc phải bằng hoặc sau ngày khởi chiếu.');return false;}
    if(showCount>0&&r!=null&&showFirst!=null&&showFirst<r){hint('releaseDate','Đang có suất chiếu ngày '+vn(showFirst)+', trước ngày khởi chiếu này.');return false;}
    if(showCount>0&&e!=null&&showLast!=null&&showLast>e){hint('endDate','Đang có suất chiếu ngày '+vn(showLast)+', sau ngày kết thúc này.');return false;}
    return r!=null&&e!=null;
  }
  function showWindow(){
    const r=parse(release.value),e=parse(end.value),state=lifecycle();
    statusPill.textContent=state.label;statusPill.dataset.status=state.key;
    if(r==null||e==null||e<r){summary.textContent=r==null?'Chọn ngày khởi chiếu và ngày kết thúc. Có thể bấm nhanh 2, 4, 6 hoặc 8 tuần.':'Chọn ngày kết thúc chiếu.';for(const s of Object.values(segs))s.style.flexGrow='1';todayMark.hidden=true;return;}
    const days=Math.round((e-r)/DAY)+1,from=Math.min(today,r)-3*DAY,to=Math.max(today,e)+7*DAY,span=to-from;
    segs.coming.style.flexGrow=String(Math.max(r-from,DAY));segs.showing.style.flexGrow=String(e-r+DAY);segs.ended.style.flexGrow=String(Math.max(to-e-DAY,DAY));
    for(const s of Object.values(segs))s.toggleAttribute('data-narrow',Number(s.style.flexGrow)/span<0.14);
    todayMark.hidden=false;todayMark.style.left=Math.min(99,Math.max(0,(today-from+DAY/2)/span*100))+'%';
    const relative=today<r?'còn '+Math.round((r-today)/DAY)+' ngày nữa khởi chiếu':today>e?'đã kết thúc '+Math.round((today-e)/DAY)+' ngày':'còn '+(Math.round((e-today)/DAY)+1)+' ngày chiếu';
    summary.textContent='Chiếu '+days+' ngày · '+vn(r)+' → '+vn(e)+' · Hôm nay '+state.label.toLowerCase()+', '+relative+'.';
  }
  all('[data-weeks]').forEach(button=>button.addEventListener('click',()=>{
    let r=parse(release.value);if(r==null){r=today;release.value=iso(r);}
    end.value=iso(r+(Number(button.dataset.weeks)*7-1)*DAY);end.dispatchEvent(new Event('input',{bubbles:true}));
  }));
  release.addEventListener('change',()=>{const r=parse(release.value);if(r!=null&&!end.value)end.value=iso(r+27*DAY);});

  // 3. People search (directors / actors) using the shared catalog endpoint.
  all('[data-people-picker]').forEach(root=>{
    const name=root.dataset.pickerName,chips=one('[data-selected]',root),search=one('.mf-search',root),results=one('.mf-results',root);
    let timer,controller,items=[],active=-1;
    const selected=()=>new Set(all('input[type="hidden"]',chips).map(i=>i.value));
    function addChip(person){
      if(selected().has(String(person.id)))return;
      const chip=document.createElement('span');chip.className='mf-chip is-selected';chip.dataset.id=person.id;
      const input=document.createElement('input');input.type='hidden';input.name=name;input.value=person.id;
      const label=document.createElement('span');label.textContent=person.name;
      const remove=document.createElement('button');remove.type='button';remove.className='mf-chip-remove';remove.textContent='×';remove.setAttribute('aria-label','Bỏ '+person.name);
      chip.append(input,label,remove);chips.append(chip);form.dispatchEvent(new Event('input',{bubbles:true}));
    }
    chips.addEventListener('click',e=>{const remove=e.target.closest('.mf-chip-remove');if(!remove)return;remove.closest('.mf-chip').remove();form.dispatchEvent(new Event('input',{bubbles:true}));search.focus();});
    function close(){results.hidden=true;search.setAttribute('aria-expanded','false');active=-1;}
    const roleLabel=name==='directorIds'?'đạo diễn':'diễn viên';let creating=false;
    const typed=()=>search.value.trim().replace(/\s+/g,' ');
    // Create the typed name as a new person (or reuse an active one with the same name) and add it straight away.
    async function create(){
      const value=typed();if(!value||creating)return;creating=true;render('Đang thêm “'+value+'”…');
      try{const data=await api('/api/admin/form-options/people',{method:'POST',body:JSON.stringify({name:value})});addChip(data.person);search.value='';close();toast(data.created?'Đã thêm '+roleLabel+' mới: '+data.person.name:'Đã chọn '+data.person.name+' (đã có sẵn).');}
      catch(error){render('Chưa thêm được: '+error.message);}
      finally{creating=false;}
    }
    function render(message){
      results.replaceChildren();const chosen=selected(),value=typed();
      const rows=message?[]:items.filter(p=>!p.create&&!chosen.has(String(p.id)));
      const exact=items.some(p=>!p.create&&p.name.toLocaleLowerCase('vi')===value.toLocaleLowerCase('vi'));
      if(!message&&value&&!exact)rows.push({create:true,name:value});
      if(message||!rows.length){const li=document.createElement('li');li.className='mf-empty';li.textContent=message||(value?'Đã chọn người này.':'Gõ tên để tìm hoặc thêm mới.');results.append(li);}
      rows.forEach((p,i)=>{
        const li=document.createElement('li');li.role='option';li.id=name+'-opt-'+i;li.classList.toggle('is-active',i===active);
        if(p.create){li.className+=' mf-create';const plus=document.createElement('span');plus.className='mf-create-icon';plus.textContent='+';const text=document.createElement('span');text.append('Thêm “');const strong=document.createElement('strong');strong.textContent=p.name;text.append(strong,'” làm '+roleLabel+' mới');li.append(plus,text);li.addEventListener('mousedown',e=>{e.preventDefault();create();});}
        else{li.textContent=p.name+(p.active?'':' · đã ngừng');li.addEventListener('mousedown',e=>{e.preventDefault();addChip(p);search.value='';close();});}
        results.append(li);
      });
      items=rows;results.hidden=false;search.setAttribute('aria-expanded','true');
    }
    async function lookup(){
      controller?.abort();controller=new AbortController();
      try{const data=await api('/api/admin/form-options?'+new URLSearchParams({kind:'people',q:typed(),page:'0'}),{signal:controller.signal});items=data.rows||[];active=-1;render();}
      catch(error){if(error.name!=='AbortError')render('Không tải được danh sách: '+error.message);}
    }
    const choose=pick=>{if(!pick)return;if(pick.create)create();else{addChip(pick);search.value='';close();}};
    search.addEventListener('input',e=>{e.stopPropagation();clearTimeout(timer);timer=setTimeout(lookup,180);});
    search.addEventListener('focus',()=>{if(!results.hidden)return;lookup();});
    search.addEventListener('blur',()=>setTimeout(close,120));
    search.addEventListener('keydown',e=>{
      if(e.key==='Escape'){close();return;}
      if(e.key==='ArrowDown'||e.key==='ArrowUp'){e.preventDefault();if(results.hidden){lookup();return;}active=Math.max(0,Math.min(items.length-1,active+(e.key==='ArrowDown'?1:-1)));render();search.setAttribute('aria-activedescendant',name+'-opt-'+active);}
      if(e.key==='Enter'){e.preventDefault();if(results.hidden||creating)return;choose(active>=0?items[active]:items.length===1?items[0]:null);}
    });
  });

  // 3b. "+ Khác": type a new genre inline; it is created (or an existing one reused) and ticked right away.
  const genreOther=document.getElementById('mf-genre-other'),genreBox=document.getElementById('mf-genre-new'),genreName=document.getElementById('mf-genre-name'),genreSave=document.getElementById('mf-genre-save');
  if(genreOther&&genreBox){
    const openGenre=on=>{genreBox.hidden=!on;genreOther.setAttribute('aria-expanded',String(on));if(on){genreName.value='';genreName.focus();}else genreOther.focus();};
    genreOther.addEventListener('click',()=>openGenre(genreBox.hidden));
    document.getElementById('mf-genre-cancel').addEventListener('click',()=>openGenre(false));
    async function addGenre(){
      const name=genreName.value.trim().replace(/\s+/g,' ');if(!name){genreName.focus();return;}
      genreSave.disabled=true;
      try{
        const data=await api('/api/admin/form-options/genres',{method:'POST',body:JSON.stringify({name})}),g=data.genre;
        let input=one('input[name="genreIds"][value="'+g.id+'"]');
        if(!input){const chip=document.createElement('label');chip.className='mf-chip';input=document.createElement('input');input.type='checkbox';input.name='genreIds';input.value=g.id;input.dataset.active='true';const text=document.createElement('span');text.textContent=g.name;chip.append(input,text);genreOther.before(chip);}
        else{input.disabled=false;input.dataset.active='true';input.closest('.mf-chip')?.classList.remove('is-inactive');const text=input.nextElementSibling;if(text)text.textContent=g.name;}
        input.checked=true;form.dispatchEvent(new Event('change'));openGenre(false);
        toast(data.created?'Đã thêm thể loại mới: '+g.name:data.reactivated?'Đã dùng lại thể loại: '+g.name:'Đã chọn '+g.name+' (đã có sẵn).');
      }catch(error){toast(error.message||'Chưa thêm được thể loại.');}
      finally{genreSave.disabled=false;}
    }
    genreSave.addEventListener('click',addGenre);
    genreName.addEventListener('keydown',e=>{if(e.key==='Enter'){e.preventDefault();addGenre();}else if(e.key==='Escape'){e.preventDefault();openGenre(false);}});
  }

  // 4. Live preview card and readiness checklist.
  const previewTitle=document.getElementById('mf-preview-title'),previewMeta=document.getElementById('mf-preview-meta'),previewAge=document.getElementById('mf-preview-age'),previewPoster=document.getElementById('mf-preview-poster'),ribbon=document.getElementById('mf-preview-ribbon');
  const genres=()=>all('input[name="genreIds"]:checked');
  function preview(){
    previewTitle.textContent=title.value.trim()||'Tên phim';
    const names=genres().map(i=>i.nextElementSibling.textContent.replace(/ · đã ngừng$/,''));
    previewMeta.textContent=[names.slice(0,2).join(' · ')||'Chưa chọn thể loại',duration.value?duration.value+' phút':''].filter(Boolean).join(' · ');
    previewAge.textContent=one('input[name="ageRating"]:checked')?.value||'P';
    previewPoster.src=poster.value||'/images/no-poster.svg';
    const state=lifecycle(),r=parse(release.value);ribbon.hidden=state.key!=='coming'&&state.key!=='ended';
    ribbon.textContent=state.key==='coming'?'Khởi chiếu '+vn(r):'Đã kết thúc';ribbon.dataset.status=state.key;
    previewPoster.parentElement.classList.toggle('is-ended',state.key==='ended');
  }
  function checklist(){
    const visible=field('active').checked;
    const checks={title:!!title.value.trim(),genre:genres().some(i=>i.dataset.active==='true'),dates:windowCheck(),poster:!!poster.value,description:!!description.value.trim()};
    for(const [key,ok] of Object.entries(checks)){const li=one('[data-check="'+key+'"]',document);li.classList.toggle('ok',ok);li.classList.toggle('missing',!ok);}
    const required=checks.title&&checks.dates&&(checks.genre||!visible);
    const ready=document.getElementById('mf-ready');
    ready.textContent=!required?'Còn thiếu thông tin bắt buộc.':!visible?'Phim đang ẩn: lưu được, khách chưa thấy.':checks.poster&&checks.description?'Đủ thông tin. Phim sẽ hiển thị cho khách sau khi lưu.':'Lưu được. Nên bổ sung poster và mô tả cho đẹp.';
    ready.dataset.state=required?(checks.poster&&checks.description||!visible?'ok':'warn'):'missing';
    return {required,checks,visible};
  }
  const counter=document.getElementById('mf-desc-count');
  function count(){counter.textContent=description.value.length.toLocaleString('vi-VN')+' / 6.000 ký tự';}

  function refresh(){showDuration();showWindow();preview();checklist();count();}
  form.addEventListener('input',refresh);form.addEventListener('change',refresh);poster.addEventListener('input',refresh);

  // Dirty state for the save bar (operations.js already warns before leaving).
  const saveState=document.getElementById('mf-save-state');let dirty=false;
  form.addEventListener('input',()=>{if(!dirty){dirty=true;saveState.textContent='Có thay đổi chưa lưu';saveState.classList.add('is-dirty');}});

  // Client-side checks before posting; the server still validates and re-renders with its own message.
  form.addEventListener('input',e=>{const box=e.target.closest?.('[data-field]');if(box&&box.dataset.serverError){delete box.dataset.serverError;hint(box.dataset.field,'');}});
  form.addEventListener('submit',e=>{
    const state=checklist(),bad=[],check=(name,message)=>{hint(name,message);if(message)bad.push(name);};
    check('title',state.checks.title?'':'Nhập tên phim.');
    const n=Number(duration.value);check('duration',Number.isInteger(n)&&n>=1&&n<=600?'':'Thời lượng từ 1 đến 600 phút.');
    check('genreIds',state.checks.genre||!state.visible?'':'Chọn ít nhất 1 thể loại đang dùng, hoặc tắt "Hiện trên website".');
    if(!release.value)check('releaseDate','Chọn ngày khởi chiếu.');if(!end.value)check('endDate','Chọn ngày kết thúc chiếu.');
    for(const name of ['releaseDate','endDate'])if(one('[data-field="'+name+'"]').classList.contains('has-error')&&!bad.includes(name))bad.push(name);
    const first=bad.length?one('[data-field="'+bad[0]+'"]'):null;if(first){e.preventDefault();e.stopImmediatePropagation();first.scrollIntoView({behavior:'smooth',block:'center'});one('input:not([type="hidden"]),textarea,select',first)?.focus({preventScroll:true});}
    else if(form.dataset.uploadBlocked!=='true'){saveState.textContent='Đang lưu…';}
  });

  // After a failed save: highlight the fields named by the server and bring the first into view.
  const errors=(form.dataset.errorFields||'').split(',').filter(Boolean);
  refresh();
  if(one('.mf-error')){dirty=true;saveState.textContent='Chưa lưu: sửa lỗi rồi bấm Lưu lại';saveState.classList.add('is-dirty');}
  if(errors.length){const message=one('.mf-error p')?.textContent||'';errors.forEach(name=>{hint(name,message);const box=one('[data-field="'+name+'"]');if(box)box.dataset.serverError='true';});const first=one('[data-field="'+errors[0]+'"]');if(first){if(first.matches('details'))first.open=true;first.closest('details')?.setAttribute('open','');requestAnimationFrame(()=>first.scrollIntoView({block:'center'}));}}
  else one('.mf-error')?.focus();
})();
