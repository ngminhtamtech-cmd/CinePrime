/* Week table (room × day) with an hour-by-hour day board as the second view. One batch = one cinema, one week; the server stays the authority for every check. */
(() => {
  'use strict';
  const root=document.getElementById('showtime-batch');if(!root)return;
  const $=id=>document.getElementById(`batch-${id}`),C=window.CinePrimeCalendar,draft=new C.Draft(),origins=new Map();
  const names=['Thứ 2','Thứ 3','Thứ 4','Thứ 5','Thứ 6','Thứ 7','Chủ nhật'],short=['T2','T3','T4','T5','T6','T7','CN'];
  const TONES=8;
  const node=(tag,text,cls)=>{const el=document.createElement(tag);if(text!==undefined)el.textContent=text;if(cls)el.className=cls;return el;};
  const button=(text,fn,cls='button secondary')=>{const el=node('button',text,cls);el.type='button';el.onclick=fn;return el;};
  const dateLabel=day=>/^\d{4}-\d\d-\d\d$/.test(day||'')?`${day.slice(8,10)}/${day.slice(5,7)}/${day.slice(0,4)}`:day||'Chưa có ngày';
  const when=value=>value?`${value.slice(11,16)} · ${dateLabel(value.slice(0,10))}`:'—';
  const uuid=()=>crypto.randomUUID(),small=matchMedia('(max-width:620.98px)'),stacked=matchMedia('(max-width:850.98px)');
  let rooms=[...$('room').options].filter(o=>o.value).map(o=>({id:o.value,name:o.textContent,format:o.dataset.format,active:true})),movies=[];
  const durations=new Map();
  const title=id=>movies.find(m=>String(m.id)===String(id))?.title||[...$('line-movie').options].find(o=>o.value===String(id))?.textContent||`Phim #${id}`;
  const roomName=id=>rooms.find(r=>String(r.id)===String(id))?.name||`Phòng #${id}`;
  /* Each film keeps one thin colour bar (tokens --film-0…7 in showtime-batch.css) so its shows are easy to follow across the week. */
  const tone=id=>`var(--film-${Math.max(0,movies.findIndex(m=>String(m.id)===String(id)))%TONES})`;
  let activeCinema=$('cinema').value,activeDay=$('day').value,week=C.monday(activeDay),movieId=root.dataset.movie||'',calendar=null,loading=false,loadError='';
  let asking=false,busy=false,pending=null,editing=null,editorDirty=false,page=0,calendarRevision=0,previewController=null,previewTimer=null,reviewing=false,reviewCommand=null;
  let serverTime=null,loadedAt=Date.now(),view={from:540,to:1380},hourPx=40,scroll={top:0,left:0},initialScroll=true,ghost=null,drag=null,suppressClick=false,resizeTimer=null;
  let mode='week',focusMovie='',viewRoom='',target='DRAFT';
  const now=()=>new Date(Date.parse(serverTime||new Date().toISOString())+Date.now()-loadedAt).toISOString();
  const today=()=>C.iso(Date.parse(now())+7*3600000).slice(0,10);
  const validDay=day=>Number.isFinite(C.stamp(`${day}T00:00`)),inWeek=day=>validDay(day)&&C.monday(day)===week;
  const dayName=day=>inWeek(day)?`${names[Math.round((C.stamp(`${day}T00:00`)-C.stamp(`${week}T00:00`))/86400000)]} ${day.slice(8)}/${day.slice(5,7)}`:dateLabel(day);
  const checked=key=>draft.preview?.rows.find(r=>r.input.rowKey===key);
  const defaults=room=>({movieId,roomId:String(room),language:$('language').value||'Phụ đề',format:$('format').value||rooms.find(r=>String(r.id)===String(room))?.format||'',surcharge:$('surcharge').value||'0'});
  /* Background checks run as Nháp so opening problems (missing prices, seats) show as warnings; "Lưu và mở bán" re-checks as OPEN. */
  const payload=(rows,status='DRAFT')=>({cinemaId:Number(activeCinema),source:C.source(rows),targetStatus:status,rows});
  const dirty=()=>!!(draft.rows.length||editorDirty||pending);
  const sourceText=row=>{const o=origins.get(row.rowKey);return row.sourceRow!=null?`${o?.fileName||'Excel'} · ${o?.sheetName||'Đăng ký suất mới'} · dòng ${row.sourceRow}${o?.cell?` · ô ${o.cell}${o.token>1?` · giờ ${o.token}`:''}`:''}`:'Xếp trên bảng';};
  const legacyWarning=()=>[...origins.values()].some(o=>o.version===1)?'Mẫu v1: mọi dòng có dữ liệu được hiểu là suất mới.':[...origins.values()].some(o=>o.version===2)?'Mẫu v2: mỗi dòng đăng ký là một suất mới.':'';
  const locked=()=>busy||!!pending||reviewing||asking;
  function message(text,type=''){$('message').className=`sched-message ${type}`;$('message').textContent=text;}
  function notify(text,items=[]){const box=$('notice');box.replaceChildren();box.hidden=!text;if(!text)return;box.append(node('span',text));if(items.length){const more=node('details'),list=node('ul');more.append(node('summary',`Xem ${items.length} dòng chi tiết`));items.forEach(item=>list.append(node('li',item)));more.append(list);box.append(more);}box.append(button('×',()=>notify(''),'sched-notice-close'));box.lastChild.setAttribute('aria-label','Ẩn ghi chú');}
  function ask(text,yes='Xác nhận',heading='Xác nhận'){if($('dialog').open)return Promise.resolve(false);asking=true;state();return new Promise(resolve=>{const d=$('dialog');$('dialog-title').textContent=heading;$('dialog-text').textContent=text;$('dialog-yes').textContent=yes;const done=v=>{d.close();asking=false;state();resolve(v);};$('dialog-yes').onclick=()=>done(true);$('dialog-no').onclick=()=>done(false);d.oncancel=e=>{e.preventDefault();done(false);};d.showModal();});}
  async function request(path,options={}){
    const headers=new Headers(options.headers||{});headers.set('Accept','application/json');if(options.body&&!(options.body instanceof FormData))headers.set('Content-Type','application/json');
    const token=document.querySelector('meta[name=csrf-token]')?.content,header=document.querySelector('meta[name=csrf-header]')?.content;if(token&&header)headers.set(header,token);
    const response=await fetch(`/api/admin/showtime-batches${path}`,{...options,headers,cache:'no-store'});let body;
    try{body=await response.json();}catch(_){throw Object.assign(new Error('Phản hồi chưa đầy đủ. Kiểm tra kết quả trước khi gửi tiếp.'),{status:response.status});}
    if(!response.ok)throw Object.assign(new Error(body.message||'Không hoàn tất được thao tác.'),{status:response.status,body});return {body,status:response.status};
  }
  function options(id,items,empty){const select=$(id),old=select.value;select.replaceChildren();if(empty){const o=node('option',empty);o.value='';select.append(o);}for(const item of items){const o=node('option',item.name||item.title);o.value=String(item.id);select.append(o);}select.value=[...select.options].some(o=>o.value===old)?old:'';}
  function updateCatalog(){
    durations.clear();movies.forEach(m=>durations.set(String(m.id),+m.duration));const active=rooms.filter(r=>r.active);
    options('room',active,'Chọn phòng');options('line-room',rooms);options('line-movie',movies);
    if(!$('room').value&&active.length)$('room').value=String(active[0].id);
    if(!movies.some(m=>String(m.id)===movieId&&m.active&&m.status!=='ENDED'))movieId='';
  }
  function setBounds(){$('line-day').min=week;$('line-day').max=C.addDays(week,6);$('week-label').textContent=`Tuần ${dateLabel(week).slice(0,5)} – ${dateLabel(C.addDays(week,6))}`;}
  function state(){
    root.querySelectorAll('input,select,button').forEach(el=>{if(!el.closest('dialog,.sched-panel'))el.disabled=locked();});
    const ready=!locked()&&!loading&&!!calendar,bad=draft.preview?.invalid||0,warn=draft.preview?draft.preview.rows.filter(r=>!r.errors.length&&r.warnings?.length).length:0;
    $('add-open').disabled=!ready;$('copy').disabled=!ready||!draft.rows.length;
    $('commit').textContent=`Lưu và mở bán ${draft.rows.length} suất`;$('commit').disabled=!ready||editorDirty||!draft.rows.length;$('draft').disabled=!ready||editorDirty||!draft.rows.length;
    $('prev').disabled=locked()||page===0;$('next').disabled=locked()||(page+1)*5>=filteredRows().length;
    $('result').hidden=!pending;$('result').disabled=busy;$('retry').hidden=!pending?.command;$('retry').disabled=busy;$('jump').hidden=!bad;$('list').hidden=!draft.rows.length;
    $('total').textContent=draft.rows.length?`${draft.rows.length} suất mới chưa lưu · ${new Set(draft.rows.map(r=>r.date)).size} ngày · ${new Set(draft.rows.map(r=>r.roomId)).size} phòng${bad?` · ${bad} cần sửa`:''}${warn?` · ${warn} chưa đủ điều kiện mở bán`:''}`:'Chưa có suất mới';
    $('hint').textContent=!calendar?'':movieId?`${title(movieId)} · ${durations.get(movieId)} phút + 15 phút dọn. Bấm cột phòng để thêm, kéo suất mới để dời.`:'Chọn phim bên cạnh rồi bấm vào cột phòng, hoặc bấm + Thêm suất.';
    root.classList.toggle('is-locked',locked());
  }
  const idle=()=>mode==='week'?'Bấm + Thêm ở ngày muốn chiếu (hoặc phím N) để xếp suất. Chưa có gì được lưu cho đến khi bạn bấm Lưu nháp hoặc Lưu và mở bán.':'Chọn phim rồi bấm vào cột phòng để thêm suất. Chưa có gì được lưu cho đến khi bạn bấm Lưu nháp hoặc Lưu và mở bán.';
  function problem(row){return C.problem(row,durations,calendar?.events||[],draft.rows,now());}
  function renderMain(){renderRoomTabs();mode==='day'?renderBoard():renderWeek();}
  function render(){renderDays();renderMovies();renderStrip();renderMain();renderRows();}
  function setMode(next,day){
    if(locked())return;mode=next;if(day&&inWeek(day)){activeDay=day;$('day').value=day;}root.classList.toggle('is-week',mode==='week');root.classList.toggle('is-day',mode==='day');
    for(const [id,on] of [['view-week',mode==='week'],['view-day',mode==='day']]){$(id).classList.toggle('active',on);$(id).setAttribute('aria-pressed',String(on));}
    initialScroll=true;clearDrag();render();if(!draft.rows.length&&calendar&&!pending)message(idle());
  }
  function changed(rows=draft.rows,keepNotice=false){if(!keepNotice)notify('');draft.replace(rows);message(draft.rows.length?`Đang xếp ${draft.rows.length} suất. Đang kiểm tra toàn bộ đợt…`:`Chưa có suất mới. ${idle()}`);reviewCommand=null;clearTimeout(previewTimer);previewController?.abort();render();if(draft.rows.length)previewTimer=setTimeout(check,300);}
  async function check(){
    clearTimeout(previewTimer);if(locked()||editorDirty||!calendar||!draft.rows.length)return;previewController?.abort();const controller=new AbortController();previewController=controller;const revision=draft.revision;
    try{const {body}=await request('/preview',{method:'POST',body:JSON.stringify(payload(draft.rows)),signal:controller.signal});if(previewController!==controller||!draft.accept(revision,body))return;const warned=body.rows.filter(r=>!r.errors.length&&r.warnings?.length).length;message([body.canCommit?`${body.total} suất hợp lệ, chưa lưu. Bấm Lưu nháp hoặc Lưu và mở bán khi xong.`:`${body.invalid}/${body.total} suất cần sửa. Chưa lưu suất nào.`,warned?`${warned} suất lưu nháp được nhưng chưa đủ điều kiện mở bán (xem danh sách).`:'',legacyWarning()].filter(Boolean).join(' '),body.canCommit?'':'error');renderDays();renderMain();renderRows();}
    catch(e){if(e.name!=='AbortError'&&revision===draft.revision)message(e.message,'error');}finally{state();}
  }
  async function loadCalendar(keepMessage=false){
    const revision=++calendarRevision;calendar=null;loading=true;loadError='';draft.invalidate();clearTimeout(previewTimer);previewController?.abort();setBounds();render();
    if(!activeCinema){loading=false;loadError='Chọn rạp để xem lịch.';render();return;}
    try{const {body}=await request(`/calendar?cinemaId=${activeCinema}&weekStart=${week}`);if(revision!==calendarRevision)return;calendar=body;serverTime=body.serverTime;loadedAt=Date.now();rooms=body.rooms;movies=body.movies;updateCatalog();loading=false;render();if(draft.rows.length)check();else if(!keepMessage)message(idle());}
    catch(e){if(revision!==calendarRevision)return;loading=false;loadError=`Không tải được lịch. Chưa thể thêm hoặc lưu suất. ${e.message}`;render();if(!keepMessage)message(loadError,'error');}finally{if(revision===calendarRevision)state();}
  }
  function add(row){if(locked()||!calendar)return false;const reason=problem(row);if(reason){message(reason,'error');return false;}try{changed(C.merge(draft.rows,[{...row,rowKey:uuid(),sourceRow:null}]));return true;}catch(e){message(e.message,'error');return false;}}
  function remove(key){if(locked())return;origins.delete(key);changed(draft.rows.filter(r=>r.rowKey!==key));}
  /* ---- Stored shows: edited, moved and cancelled right here, each change saved at once. ---- */
  let undoTimer=null,carry=null;
  const savedRow=e=>({rowKey:`saved-${e.id}`,savedId:e.id,sourceRow:null,movieId:String(e.movieId),roomId:String(e.roomId),date:e.startsAt.slice(0,10),time:e.startsAt.slice(11,16),language:e.language||'',format:e.format||'',surcharge:String(e.surcharge??0),status:e.status});
  const others=source=>source?.savedId?(calendar?.events||[]).filter(e=>e.id!==source.savedId):(calendar?.events||[]);
  const movable=e=>!e.locked&&C.stamp(e.startsAt)>Date.parse(now())+7*3600000;
  const formatFor=(row,roomId)=>{const old=rooms.find(r=>String(r.id)===String(row.roomId)),next=rooms.find(r=>String(r.id)===String(roomId));return old&&next&&(row.format||'')===old.format?next.format:row.format;};
  function notifyUndo(text,undo){
    notify(text);clearTimeout(undoTimer);if(!undo)return;const box=$('notice'),again=button('Hoàn tác',async()=>{again.remove();clearTimeout(undoTimer);await undo();},'button secondary sched-undo');box.insertBefore(again,box.lastChild);
    undoTimer=setTimeout(()=>again.remove(),10000);
  }
  function showFail(e){
    if(e.status===409){message(e.message,'error');loadCalendar(true);return;}
    message(e.message||'Không lưu được suất.','error');
  }
  /* Sends one stored show to the server; returns the saved event or throws with the server's message. */
  async function putShow(e,row){
    const {body}=await request(`/showtimes/${e.id}`,{method:'PUT',body:JSON.stringify({expected:e.token,movieId:String(row.movieId),roomId:String(row.roomId),date:row.date,time:row.time,language:row.language,format:row.format,surcharge:String(row.surcharge??0),status:row.status||e.status})});return body;
  }
  const describe=e=>`${e.movieTitle} · ${roomName(e.roomId)} · ${e.startsAt.slice(11,16)} ${dayName(e.startsAt.slice(0,10))}`;
  async function moveSaved(e,row){
    if(locked())return;const before=savedRow(e);busy=true;state();message(`Đang dời ${e.movieTitle}…`);
    try{const saved=await putShow(e,row);await loadCalendar(true);notifyUndo(`Đã dời ${describe(e)} → ${saved.startsAt.slice(11,16)} ${dayName(saved.startsAt.slice(0,10))}, ${roomName(saved.roomId)}. Đã lưu.`,async()=>{
      busy=true;state();try{await putShow(saved,before);await loadCalendar(true);notify(`Đã trả ${saved.movieTitle} về ${before.time} ${dayName(before.date)}, ${roomName(before.roomId)}.`);}catch(x){showFail(x);}finally{busy=false;state();}
    });message('Đã lưu thay đổi suất chiếu.','success');}
    catch(x){showFail(x);}finally{busy=false;state();}
  }
  /* Week table drag and drop: a chip dropped on another day keeps its time, dropped on a room tab keeps its day and time. */
  function carrier(chip,take){
    chip.draggable=true;chip.title='Kéo sang ngày khác hoặc thả lên tên phòng để dời';
    chip.addEventListener('dragstart',e=>{if(locked()){e.preventDefault();return;}carry=take();e.dataTransfer.effectAllowed='move';e.dataTransfer.setData('text/plain',carry.row.rowKey);chip.classList.add('dragging');root.classList.add('is-carrying');});
    chip.addEventListener('dragend',()=>{carry=null;chip.classList.remove('dragging');root.classList.remove('is-carrying');root.querySelectorAll('.drop-on').forEach(el=>el.classList.remove('drop-on'));});
  }
  function dropZone(el,patch,hint){
    el.addEventListener('dragover',e=>{if(!carry)return;e.preventDefault();e.dataTransfer.dropEffect='move';el.classList.add('drop-on');if(hint)el.title=hint;});
    el.addEventListener('dragleave',e=>{if(!el.contains(e.relatedTarget))el.classList.remove('drop-on');});
    el.addEventListener('drop',e=>{if(!carry)return;e.preventDefault();el.classList.remove('drop-on');const taken=carry;carry=null;dropTo(taken,patch());});
  }
  function dropTo(taken,patch){
    const from=taken.row,next={...from,...patch};if(patch.roomId)next.format=formatFor(from,patch.roomId);
    if(next.date===from.date&&String(next.roomId)===String(from.roomId))return;
    const reason=C.problem(next,durations,others(from),draft.rows,now());if(reason){message(`Không dời suất: ${reason}`,'error');return;}
    if(taken.event)moveSaved(taken.event,next);else{changed(draft.rows.map(r=>r.rowKey===next.rowKey?next:r));notify(`Đã dời suất mới ${title(next.movieId)} sang ${next.time} ${dayName(next.date)}, ${roomName(next.roomId)}. Chưa lưu cho đến khi bạn bấm Tạo.`);}
  }
  /* Stored shows open the shared popup (showtime-editor.js); drafts on the board count when it checks for clashes. */
  function openShow(e){
    if(locked()||!window.CinePrimeShowEditor)return;
    window.CinePrimeShowEditor.open({event:e,movies,rooms,events:calendar?.events||[],drafts:draft.rows.map(r=>({...r,movieTitle:title(r.movieId)})),serverTime:now(),today:today(),cinemaId:activeCinema,returnTo:`/admin/schedule?cinemaId=${activeCinema}&date=${e.startsAt.slice(0,10)}`,
      onSaved:async(saved,before)=>{await loadCalendar(true);const moved=saved.startsAt!==before.startsAt||String(saved.roomId)!==String(before.roomId);
        notifyUndo(`Đã lưu ${saved.movieTitle} · ${roomName(saved.roomId)} · ${saved.startsAt.slice(11,16)} ${dayName(saved.startsAt.slice(0,10))}.`,moved&&!saved.locked?async()=>{busy=true;state();try{await putShow(saved,savedRow(before));await loadCalendar(true);notify(`Đã trả suất về ${before.startsAt.slice(11,16)} ${dayName(before.startsAt.slice(0,10))}.`);}catch(x){showFail(x);}finally{busy=false;state();}}:null);
        message('Đã lưu thay đổi suất chiếu.','success');},
      onCancelled:async e=>{await loadCalendar(true);notify(`Đã hủy suất ${describe(e)}.`);message('Đã hủy suất chiếu.','success');},
      onStale:async text=>{await loadCalendar(true);message(text,'error');}});
  }
  const showExisting=openShow;
  /* The shows already in a room on a day, as the same even chip grid the edit popup uses. */
  const dayStrip=(items,heading)=>window.CinePrimeShowEditor?window.CinePrimeShowEditor.strip(items.map(i=>({startsAt:i.startsAt,endsAt:i.endsAt,movieTitle:i.movieTitle,draft:!!i.row})),heading):node('span');
  /* Saved and unsaved shows that touch a day, in any room. */
  function dayItems(day){
    const start=C.stamp(`${day}T00:00`),end=start+86400000,list=[];
    for(const e of calendar?.events||[])if(C.stamp(e.startsAt)<end&&C.stamp(e.cleanupUntil)>start)list.push(e);
    for(const row of draft.rows){const t=C.timing(row,durations);if(t&&C.stamp(t.startsAt)<end&&C.stamp(t.cleanupUntil)>start)list.push({...t,row,movieId:row.movieId,movieTitle:title(row.movieId)});}
    return list;
  }
  /* The chosen hours, widened so no show that starts on this day is ever hidden. */
  function viewWindow(){
    let from=Number($('from').value)*60,to=Number($('to').value)*60;if(!(to>from))to=Math.min(1440,from+60);const base=C.stamp(`${activeDay}T00:00`);
    for(const item of dayItems(activeDay)){const start=(C.stamp(item.startsAt)-base)/60000,until=(C.stamp(item.cleanupUntil)-base)/60000;if(start<0)continue;from=Math.min(from,Math.floor(start/60)*60);to=Math.max(to,Math.min(1440,Math.ceil(until/60)*60));}
    return {from,to};
  }
  function boardRooms(){
    const used=new Set(dayItems(activeDay).map(i=>String(i.roomId))),list=rooms.filter(r=>r.active||used.has(String(r.id)));if(!small.matches)return list;
    const chosen=list.find(r=>String(r.id)===$('room').value)||list[0];return chosen?[chosen]:[];
  }
  function renderDays(){
    const box=$('days');box.replaceChildren();
    for(let d=0;d<7;d++){
      const day=C.addDays(week,d),saved=(calendar?.events||[]).filter(e=>e.startsAt.slice(0,10)===day).length,fresh=draft.rows.filter(r=>r.date===day),bad=fresh.filter(r=>checked(r.rowKey)?.errors.length).length;
      const tab=button('',()=>{if(day===activeDay)return;activeDay=day;$('day').value=day;render();},`sched-day${day===activeDay?' active':''}${day<today()?' past':''}${day===today()?' today':''}${bad?' bad':''}`);
      tab.setAttribute('role','tab');tab.setAttribute('aria-selected',String(day===activeDay));
      tab.append(node('strong',`${short[d]} ${day.slice(8)}/${day.slice(5,7)}`),node('small',[calendar?`${saved} đã lưu`:'',fresh.length?`${fresh.length} mới`:'',bad?`${bad} lỗi`:''].filter(Boolean).join(' · ')||'—'));box.append(tab);
    }
  }
  function renderMovies(){
    const box=$('movies'),q=$('movie-filter').value.trim().toLowerCase(),list=movies.filter(m=>m.active&&m.status!=='ENDED');box.replaceChildren();$('movie-filter').hidden=list.length<=6;
    for(const m of list.filter(m=>!q||m.title.toLowerCase().includes(q)||String(m.id)===movieId)){
      const on=String(m.id)===movieId,item=button('',()=>{movieId=String(m.id);renderMovies();renderMain();state();},`sched-movie${on?' active':''}`);
      item.setAttribute('role','radio');item.setAttribute('aria-checked',String(on));item.style.setProperty('--tone',tone(m.id));
      item.append(node('strong',m.title),node('small',`${m.duration} phút${m.status==='COMING_SOON'?' · Sắp chiếu':''}`));box.append(item);
    }
    if(!box.children.length)box.append(node('p',!calendar?(loading?'Đang tải phim…':'Chưa tải được phim.'):list.length?'Không có phim khớp.':'Chưa có phim đang hoạt động.','sched-empty'));
  }
  /* Room list above the calendar: in the week view each room is its own calendar; the label carries that room's count for the week. */
  function renderRoomTabs(){
    const box=$('room-tabs'),had=box.contains(document.activeElement),wide=mode==='week',chosen=wide?currentRoom():$('room').value,items=weekItems();box.replaceChildren();
    for(const room of wide?weekRooms():rooms.filter(r=>r.active)){
      let all=0,fresh=0,bad=0;for(let d=0;d<7;d++)for(const i of items.get(`${room.id}|${C.addDays(week,d)}`)||[]){if(i.ghost)continue;all++;if(i.row){fresh++;if(checked(i.row.rowKey)?.errors.length)bad++;}}
      const tab=button('',()=>pickRoom(room.id),`button secondary sched-room${String(room.id)===chosen?' active':''}${bad?' bad':''}`);tab.setAttribute('aria-pressed',String(String(room.id)===chosen));
      if(wide&&room.active)dropZone(tab,()=>({roomId:String(room.id)}),`Thả để chuyển sang ${room.name}, giữ ngày và giờ`);
      tab.append(node('strong',room.name),node('small',[room.active?room.format:'Ngừng hoạt động',calendar?`${all} suất`:'',fresh?`${fresh} mới`:'',bad?`${bad} lỗi`:''].filter(Boolean).join(' · ')));box.append(tab);
    }
    if(had)box.querySelector('.active')?.focus();
  }
  function pickRoom(id){if(locked())return;viewRoom=String(id);if(panel.open){pickPanelRoom(viewRoom);return;}if([...$('room').options].some(o=>o.value===viewRoom))$('room').value=viewRoom;focusMovie='';renderStrip();renderMain();state();}
  /* On wide screens the board (or the week table) ends exactly above the sticky footer, and the side panel never grows past it. */
  function fit(){
    const box=$('board'),side=root.querySelector('.sched-side'),wrap=$('week-wrap'),foot=root.querySelector('.sched-footer').offsetHeight;
    if(stacked.matches){box.style.height='';side.style.maxHeight='';wrap.style.maxHeight='';return;}
    if(mode==='week'){wrap.style.maxHeight=`${Math.max(300,innerHeight-(wrap.getBoundingClientRect().top+scrollY)-foot-10)}px`;return;}
    const space=Math.max(340,innerHeight-(box.getBoundingClientRect().top+scrollY)-foot-10);box.style.height=`${space}px`;side.style.maxHeight=`${space+box.offsetTop-side.offsetTop}px`;
  }
  /* Rooms worth a row: active ones, plus stopped rooms that still hold a show this week. */
  function weekRooms(){const used=new Set((calendar?.events||[]).map(e=>String(e.roomId)));draft.rows.forEach(r=>used.add(String(r.roomId)));return rooms.filter(r=>r.active||used.has(String(r.id)));}
  /* Every show of the week keyed by room and start day; a show that runs past midnight stays on the day it starts. */
  function weekItems(){
    const map=new Map(),put=(room,day,item)=>{const key=`${room}|${day}`;if(!map.has(key))map.set(key,[]);map.get(key).push(item);};
    for(const e of calendar?.events||[])put(e.roomId,e.startsAt.slice(0,10),e);
    for(const row of draft.rows){const t=C.timing(row,durations);put(row.roomId,row.date,{...(t||{startsAt:`${row.date}T${row.time||'00:00'}`,endsAt:'',cleanupUntil:''}),row,roomId:String(row.roomId),movieId:row.movieId,movieTitle:title(row.movieId)});}
    for(const row of seriesRows()){const t=C.timing(row,durations);if(t)put(row.roomId,row.date,{...t,ghost:true,roomId:String(row.roomId),movieId:row.movieId,movieTitle:title(row.movieId)});}
    for(const list of map.values())list.sort((a,b)=>String(a.startsAt).localeCompare(String(b.startsAt)));return map;
  }
  /* The room whose week is on screen; falls back to the target room, then to the first room. */
  function currentRoom(){const list=weekRooms();if(!list.some(r=>String(r.id)===viewRoom))viewRoom=String((list.find(r=>String(r.id)===$('room').value)||list[0])?.id||'');return viewRoom;}
  /* One room at a time: seven day columns on wide screens, seven day rows on narrow ones. */
  function renderWeek(){
    const table=$('week'),wrap=$('week-wrap'),top=wrap.scrollTop,left=wrap.scrollLeft;table.replaceChildren();
    const empty=text=>{const tr=node('tr'),td=node('td',text,'sched-empty');tr.append(td);table.append(tr);state();};
    if(!calendar)return empty(loading?'Đang tải lịch…':loadError||'Chọn rạp để xem lịch.');
    const room=weekRooms().find(r=>String(r.id)===currentRoom());if(!room)return empty('Rạp chưa có phòng hoạt động. Tạo phòng trước khi xếp suất.');
    const items=weekItems(),rows=stacked.matches,head=node('tr'),body=node('tbody'),line=node('tr');table.classList.toggle('by-row',rows);table.setAttribute('aria-label',`Lịch cả tuần của ${room.name}`);
    for(let d=0;d<7;d++){
      const day=C.addDays(week,d),shown=items.get(`${room.id}|${day}`)||[],all=shown.filter(i=>!i.ghost),bad=all.filter(i=>i.row&&checked(i.row.rowKey)?.errors.length).length,fresh=all.filter(i=>i.row).length,when=`${day<today()?'past':''}${day===today()?' today':''}`.trim();
      const th=node('th',undefined,`${when}${bad?' bad':''}`.trim()),open=button('',()=>setMode('day',day),'week-day');th.scope=rows?'row':'col';open.title='Xem ngày này theo giờ, mọi phòng';
      open.append(node('strong',`${short[d]} ${day.slice(8)}/${day.slice(5,7)}${day===today()?' · hôm nay':''}`),node('small',[all.length?`${all.length} suất`:day<today()?'đã qua':'chưa có suất',fresh?`${fresh} mới`:'',bad?`${bad} lỗi`:''].filter(Boolean).join(' · ')));th.append(open);
      const td=node('td',undefined,when),cell=node('div',undefined,'week-cell');
      if(day>=today()&&room.active)dropZone(td,()=>({date:day}),'');
      for(const item of shown){
        if(item.ghost){const ghostChip=node('div',undefined,'week-chip ghost');ghostChip.style.setProperty('--tone',tone(item.movieId));const inner=node('div',undefined,'chip-body');inner.append(node('b',`${item.startsAt.slice(11,16)} – ${item.endsAt.slice(11,16)}`),node('span',`Dự kiến · ${item.movieTitle}`));ghostChip.append(inner);ghostChip.title='Suất dự kiến trong bảng Nhiều suất nối tiếp, chưa thêm vào lịch';cell.append(ghostChip);continue;}
        const row=item.row,value=row&&checked(row.rowKey),wrong=!!value?.errors.length,label=row?'Mới, chưa lưu':item.locked?'Có vé hoặc lượt giữ ghế, không dời được':item.status==='OPEN'?'Đang bán vé':'Nháp, chưa bán';
        const chip=node('div',undefined,`week-chip ${row?wrong?'fresh error':'fresh':item.status==='DRAFT'?'saved draft':'saved open'}${!row&&item.locked?' locked':''}${focusMovie&&String(item.movieId)!==focusMovie?' dim':''}`),show=button('',()=>row?edit(row):showExisting(item),'chip-body'),time=row?row.time||'--:--':item.startsAt.slice(11,16);
        chip.style.setProperty('--tone',tone(item.movieId));show.append(node('b',`${time}${item.endsAt?` – ${item.endsAt.slice(11,16)}`:''}`),node('span',item.movieTitle));if(wrong)show.append(node('small',value.errors[0].message));
        show.title=`${item.movieTitle} · ${time}${item.endsAt?` – ${item.endsAt.slice(11,16)} · dọn phòng đến ${item.cleanupUntil.slice(11,16)}`:''} · ${wrong?value.errors.map(e=>e.message).join(' '):label}`;chip.append(show);
        if(row){chip.dataset.rowKey=row.rowKey;const drop=button('×',()=>remove(row.rowKey),'chip-remove');drop.title='Bỏ suất này';drop.setAttribute('aria-label',`Bỏ suất ${item.movieTitle} ${time} ${dayName(day)}`);chip.append(drop);}
        if(!row&&item.locked)show.append(node('em','Có vé','chip-lock'));
        if(row||movable(item))carrier(chip,()=>row?{row}:{row:savedRow(item),event:item});
        cell.append(chip);
      }
      if(room.active&&day>=today()){const more=button('+ Thêm',()=>openAdd(room.id,day),'week-add');more.setAttribute('aria-label',`Thêm suất tại ${room.name}, ${dayName(day)}`);cell.append(more);}
      else if(!cell.children.length)cell.append(node('span','—','week-none'));
      td.append(cell);
      if(rows){const tr=node('tr');tr.append(th,td);body.append(tr);}else{head.append(th);line.append(td);}
    }
    if(rows)table.append(body);else{const thead=node('thead');thead.append(head);body.append(line);table.append(thead,body);}
    fit();wrap.scrollTop=top;wrap.scrollLeft=left;state();
  }
  /* Shows per film this week; a click keeps that film's shows bright and dims the rest. */
  function renderStrip(){
    const box=$('strip'),count=new Map(),bump=(id,fresh)=>{const c=count.get(String(id))||{all:0,fresh:0};c.all++;if(fresh)c.fresh++;count.set(String(id),c);};box.replaceChildren();
    const only=currentRoom();for(const e of calendar?.events||[])if(String(e.roomId)===only)bump(e.movieId,false);for(const row of draft.rows)if(String(row.roomId)===only)bump(row.movieId,true);
    if(focusMovie&&!count.has(focusMovie))focusMovie='';if(!count.size)return;
    box.append(node('span','Phim của phòng này:','strip-label'));
    for(const [id,c] of [...count].sort((a,b)=>b[1].all-a[1].all)){
      const on=focusMovie===id,item=button(`${title(id)} · ${c.all} suất${c.fresh?` (${c.fresh} mới)`:''}`,()=>{focusMovie=on?'':id;renderStrip();renderWeek();},`strip-movie${on?' active':''}`);
      item.style.setProperty('--tone',tone(id));item.setAttribute('aria-pressed',String(on));item.title=on?'Bấm lần nữa để hiện lại mọi phim':'Làm nổi các suất của phim này';box.append(item);
    }
  }
  /* ---- Ngăn "Thêm suất": one side panel for one show, a back-to-back chain, or several films in one room. ----
     Room, films (in order), days, first start and last start; every planned show is listed per day with its real time,
     editable (typing or dragging a show pins it), and the shows after it move along, always 15 minutes of cleanup apart. */
  const panel={open:false,films:[],slots:new Map(),extra:new Map(),result:[],focus:null,drag:null};
  const usable=()=>movies.filter(m=>m.active&&m.status!=='ENDED');
  const panelRoom=()=>$('room').value;
  const panelMode=()=>root.querySelector('input[name=batch-add-mode]:checked')?.value||'many';
  const panelOrder=()=>root.querySelector('input[name=batch-add-order]:checked')?.value||'mix';
  const panelDays=()=>[...$('add-days').querySelectorAll('input:checked')].map(input=>input.value).sort();
  const movieOf=id=>movies.find(m=>String(m.id)===String(id));
  /* A day is outside a film's release window when it is before its first day or after its last day. */
  const outside=(day,id)=>{const m=movieOf(id);return !!m&&((m.releaseDate&&day<m.releaseDate)||(m.endDate&&day>m.endDate));};
  /* Film of slot i: one film fills the day; several alternate (Xen kẽ) or run one after another with a count each (Lần lượt). */
  function picker(){
    const films=panel.films;if(!films.length)return ()=>null;if(films.length===1||panelMode()==='one')return ()=>films[0].id;
    if(panelOrder()==='mix')return i=>films[i%films.length].id;
    return i=>{let n=i;for(const f of films){if(n<f.count)return f.id;n-=f.count;}return null;};
  }
  const pinned=()=>[...panel.slots.values()].some(list=>list.some(s=>s&&s.time));
  function panelSeed(day){const room=panelRoom();return {movieId:String(panel.films[0]?.id||''),roomId:room,date:day,time:$('add-start').value,language:$('language').value||'Phụ đề',format:$('format').value||rooms.find(r=>String(r.id)===String(room))?.format||'',surcharge:$('surcharge').value||'0'};}
  /* Shows the panel proposes, day by day; later days see the earlier days' rows, so a show past midnight still counts. */
  function panelPlan(){
    const out=[],one=panelMode()==='one',start=C.minutes($('add-start').value),last=one?start:C.minutes($('add-until').value);
    if(!calendar||!panelRoom()||!panel.films.length||!Number.isFinite(start)||!Number.isFinite(last))return out;
    const pick=picker();let made=[];
    for(const day of panelDays()){
      const slots=one?[{...(panel.slots.get(day)?.[0]||{}),time:(panel.slots.get(day)?.[0]?.time)||$('add-start').value}]:panel.slots.get(day)||[];
      const list=C.series(panelSeed(day),last,slots,one?0:panel.extra.get(day)||0,durations,calendar.events,[...draft.rows,...made],now(),5,pick).slice(0,one?1:96);
      for(const item of list){const bad=panel.films.find(f=>String(f.id)===String(item.row.movieId))&&outside(day,item.row.movieId);if(bad&&!item.reason&&!item.off)item.reason=`Ngoài thời gian chiếu của ${title(item.row.movieId)}.`;}
      out.push({day,list});made=made.concat(list.filter(x=>!x.off&&!x.reason).map(x=>x.row));
    }
    return out;
  }
  const seriesRows=()=>panel.open?panel.result.flatMap(d=>d.list.filter(x=>!x.off&&!x.reason).map(x=>x.row)):[];
  function renderRooms(){
    const box=$('add-rooms');box.replaceChildren();
    for(const r of rooms.filter(r=>r.active)){const on=String(r.id)===panelRoom(),chip=button(r.name,()=>pickPanelRoom(String(r.id)),`sched-chip${on?' on':''}`);chip.setAttribute('role','radio');chip.setAttribute('aria-checked',String(on));chip.title=r.format;box.append(chip);}
  }
  function renderFilms(){
    const box=$('add-films');box.replaceChildren();const many=panel.films.length>1&&panelMode()!=='one',seq=many&&panelOrder()==='seq';
    panel.films.forEach((f,i)=>{
      const m=movieOf(f.id),li=node('li',undefined,'sched-film');li.style.setProperty('--tone',tone(f.id));
      const info=node('div');info.append(node('strong',m?.title||`Phim #${f.id}`),node('small',[`${m?.duration||'?'} phút`,m?.endDate?`chiếu đến ${dateLabel(m.endDate)}`:'',m?.status==='COMING_SOON'?'Sắp chiếu':''].filter(Boolean).join(' · ')));
      li.append(node('span',String(i+1),'sched-film-no'),info);
      if(seq){const step=node('span',undefined,'sched-step');step.append(button('−',()=>{f.count=Math.max(1,f.count-1);renderPanel();},'sched-step-btn'),node('b',String(f.count)),button('+',()=>{f.count=Math.min(20,f.count+1);renderPanel();},'sched-step-btn'));step.children[0].setAttribute('aria-label',`Bớt một suất ${m?.title||''}`);step.children[2].setAttribute('aria-label',`Thêm một suất ${m?.title||''}`);li.append(step);}
      if(i>0){const up=button('↑',()=>{panel.films.splice(i-1,0,panel.films.splice(i,1)[0]);changePlan();},'sched-mini');up.setAttribute('aria-label',`Đưa ${m?.title||''} lên trước`);li.append(up);}
      if(panel.films.length>1){const drop=button('×',()=>{panel.films.splice(i,1);changePlan();},'sched-mini');drop.setAttribute('aria-label',`Bỏ ${m?.title||''} khỏi danh sách`);li.append(drop);}
      box.append(li);
    });
    const select=$('add-movie'),left=usable().filter(m=>!panel.films.some(f=>String(f.id)===String(m.id)));select.replaceChildren();
    for(const m of left){const o=node('option',`${m.title} · ${m.duration} phút${m.status==='COMING_SOON'?' · Sắp chiếu':''}`);o.value=String(m.id);select.append(o);}
    const canAdd=left.length>0&&panelMode()!=='one';select.hidden=!canAdd;$('add-film').hidden=!canAdd;$('add-film').textContent=panel.films.length?'+ Thêm phim':'+ Chọn phim';
    $('add-order').hidden=!many;
  }
  function renderPanelDays(){
    const box=$('add-days'),picked=new Set(panelDays());box.replaceChildren();
    for(let d=0;d<7;d++){
      const date=C.addDays(week,d),label=node('label','', 'sched-chip'),input=node('input'),past=date<today(),out=panel.films.find(f=>outside(date,f.id));
      input.type='checkbox';input.value=date;input.disabled=past;input.checked=picked.has(date)&&!past;label.append(input,node('span',`${short[d]} ${date.slice(8)}/${date.slice(5,7)}`));
      if(past)label.title='Ngày đã qua';else if(out)label.title=`Ngoài thời gian chiếu của ${title(out.id)}`;label.classList.toggle('warn',!past&&!!out);box.append(label);
    }
  }
  const strip=(day,list)=>{
    /* One day of the room, 08:00 → 02:00: grey = shows already there, numbered = planned shows (drag one to move it and pin it). */
    const from=480,span=1080,box=node('div',undefined,'series-strip');box.dataset.day=day;const place=(el,a,b)=>{el.style.left=`${Math.max(0,(a-from)/span*100)}%`;el.style.width=`${Math.max(.6,(Math.min(b,from+span)-Math.max(a,from))/span*100)}%`;};
    const base=C.stamp(`${day}T00:00`);
    for(const i of dayItems(day).filter(i=>String(i.roomId)===panelRoom()&&!list.some(x=>x.row.rowKey===i.row?.rowKey))){const el=node('span',undefined,`series-block ${i.row?'fresh':'saved'}`);place(el,(C.stamp(i.startsAt)-base)/60000,(C.stamp(i.endsAt)-base)/60000);el.title=`${i.startsAt.slice(11,16)}–${i.endsAt.slice(11,16)} ${i.movieTitle}${i.row?' (mới, chưa lưu)':''}`;box.append(el);}
    for(const item of list){if(item.off)continue;const at=C.minutes(item.row.time),len=(durations.get(String(item.row.movieId))||0),el=node('span',String(panel.films.findIndex(f=>String(f.id)===String(item.row.movieId))+1),`series-block plan${item.reason?' bad':''}${item.typed?' pin':''}`);place(el,at,at+len);el.style.setProperty('--tone',tone(item.row.movieId));el.title=`${item.row.time} ${title(item.row.movieId)}${item.reason?` · ${item.reason}`:''} · kéo để dời`;el.dataset.index=item.index;el.dataset.minute=at;box.append(el);}
    return box;
  };
  function renderPanel(){
    if(!panel.open)return;panel.result=panelPlan();renderRooms();renderFilms();
    const one=panelMode()==='one';$('add-until-wrap').hidden=one;$('add-start-label').textContent=one?'Giờ bắt đầu':'Suất đầu từ';
    const box=$('add-list');box.replaceChildren();let ok=0,bad=0;
    if(!panel.films.length)box.append(node('p','Chọn ít nhất một phim.','sched-empty'));
    else if(!panelDays().length)box.append(node('p','Chọn ít nhất một ngày chiếu.','sched-empty'));
    else if(!one&&C.minutes($('add-until').value)<C.minutes($('add-start').value))box.append(node('p','Giờ suất cuối phải sau giờ suất đầu.','sched-empty'));
    for(const {day,list} of panel.result){
      const valid=list.filter(x=>!x.off&&!x.reason).length,wrong=list.filter(x=>!x.off&&x.reason).length;ok+=valid;bad+=wrong;
      const group=node('section',undefined,'series-day'),head=node('header');head.append(node('strong',dayName(day)),node('small',[`${valid} suất`,wrong?`${wrong} cần sửa`:''].filter(Boolean).join(' · ')));group.append(head,strip(day,list));
      const ol=node('ol');
      for(const item of list){
        const li=node('li',undefined,`series-slot${item.off?' off':''}${item.reason?' bad':''}${item.typed?' typed':''}`),use=node('input'),time=node('input'),slot=()=>{const l=panel.slots.get(day)||[];panel.slots.set(day,l);return l[item.index]||(l[item.index]={});};
        li.style.setProperty('--tone',tone(item.row.movieId));
        use.type='checkbox';use.checked=!item.off;use.setAttribute('aria-label',`Chiếu suất ${item.row.time} ${dayName(day)}`);use.onchange=()=>{slot().off=!use.checked;renderPanel();};
        time.type='time';time.step='60';time.value=item.row.time;time.dataset.focus=`${day}|${item.index}`;time.setAttribute('aria-label',`Giờ bắt đầu suất ${item.index+1} ${dayName(day)}${item.typed?', đã ghim':''}`);
        time.onchange=()=>{if(!time.value)return;slot().time=time.value;panel.focus=time.dataset.focus;renderPanel();};
        const end=node('small',`${panel.films.length>1?`${title(item.row.movieId)} · `:''}hết ${item.endsAt.slice(11,16)}`,'series-end');
        li.append(use,time,end);
        if(item.typed&&!one){const unpin=button('Bỏ ghim',()=>{delete slot().time;renderPanel();},'series-pin');unpin.title='Giờ này lại tự dời theo suất trước';li.append(unpin);}
        if(item.reason){const why=node('small',item.reason,'series-why');li.append(why);}else if(item.late)li.append(node('small','Sau giờ suất cuối đã đặt','series-note'));
        ol.append(li);
      }
      group.append(ol);if(!one)group.append(button('+ Thêm một suất',()=>{panel.extra.set(day,(panel.extra.get(day)||0)+1);renderPanel();},'series-more'));
      if(!list.length)group.append(node('p','Không còn chỗ trống trong khung giờ này.','sched-empty'));box.append(group);
    }
    const days=panel.result.filter(d=>d.list.some(x=>!x.off&&!x.reason)).length,perDay=days?Math.round(ok/days):0;
    $('add-count').textContent=ok?(one?`${days} ngày`:`${perDay} suất/ngày`):'';
    const films=new Map();for(const r of seriesRows())films.set(r.movieId,(films.get(r.movieId)||0)+1);
    $('add-summary').textContent=ok?`${ok} suất tại ${roomName(panelRoom())}, ${days} ngày${films.size>1?` · ${[...films].map(([id,n])=>`${title(id)} ${n}`).join(', ')}`:` · ${title(panel.films[0].id)}`}. Mỗi suất cách nhau 15 phút dọn phòng.${bad?` ${bad} suất trùng lịch hoặc ngoài thời gian chiếu sẽ không được thêm.`:''}`:'Chưa có suất hợp lệ để thêm.';
    $('add-apply').textContent=ok?`Thêm ${ok} suất vào lịch`:'Thêm vào lịch';$('add-apply').disabled=!ok;
    if(mode==='week'&&panelRoom())viewRoom=panelRoom();renderMain();
    if(panel.focus){const el=box.querySelector(`[data-focus="${panel.focus}"]`);panel.focus=null;el?.focus();}
  }
  /* Room or film changes drop the pinned times, so ask first when some exist. */
  async function changePlan(){if(pinned()&&!await ask('Đổi phòng hoặc danh sách phim sẽ bỏ các giờ đã ghim. Tiếp tục?','Bỏ giờ đã ghim','Có giờ đã ghim')){renderPanel();return false;}panel.slots.clear();panel.extra.clear();renderPanel();return true;}
  async function pickPanelRoom(id){if(id===panelRoom())return;const old=panelRoom();$('room').value=id;if(!await changePlan()){$('room').value=old;renderPanel();}}
  /* Earliest free start in the room on the first chosen day: after the shows already there, never in the past. */
  function suggestStart(day,room){
    if(!calendar||!day||!room||!panel.films.length)return '09:00';const base=C.stamp(`${day}T00:00`),soon=day===today()?C.roundUp((Date.parse(now())+7*3600000-base)/60000+1):0,from=Math.max(540,soon);
    const fits=C.fillDay({movieId:String(panel.films[0].id),roomId:room,date:day,time:C.clock(Math.min(1435,from))},1435,durations,calendar.events,draft.rows,now(),()=>'suggest').added[0];
    return fits?fits.time:C.clock(Math.max(0,Math.min(1435,C.firstFree(from,C.busy(day,room,durations,calendar.events,draft.rows)))));
  }
  function openAdd(room,day,time){
    if(locked()||!calendar)return;const list=usable(),active=rooms.filter(r=>r.active);
    if(!active.length){message('Rạp chưa có phòng hoạt động. Tạo phòng trước khi xếp suất.','error');return;}if(!list.length){message('Chưa có phim đang hoạt động để xếp suất.','error');return;}
    if(room&&active.some(r=>String(r.id)===String(room)))$('room').value=String(room);if(!active.some(r=>String(r.id)===panelRoom()))$('room').value=String(active[0].id);
    if(!panel.films.length||!panel.open){const first=list.find(m=>String(m.id)===movieId)||list[0];panel.films=[{id:String(first.id),count:3}];}
    const pick=day&&day>=today()&&inWeek(day)?day:[0,1,2,3,4,5,6].map(d=>C.addDays(week,d)).find(d=>d>=today());
    panel.slots.clear();panel.extra.clear();panel.open=true;$('add').hidden=false;root.classList.add('has-panel');
    renderPanelDays();$('add-days').querySelectorAll('input').forEach(input=>{input.checked=!input.disabled&&input.value===pick;});
    $('add-start').value=time&&/^\d\d:\d\d$/.test(time)?time:suggestStart(pick,panelRoom());
    if(time)root.querySelector('input[name=batch-add-mode][value=one]').checked=true;
    renderPanel();setTimeout(()=>$('add').querySelector('.sched-chip.on')?.focus(),30);
  }
  function closeAdd(){if(!panel.open)return;panel.open=false;panel.result=[];$('add').hidden=true;root.classList.remove('has-panel');renderMain();$('add-open').focus();}
  function setPanelDays(test){$('add-days').querySelectorAll('input:not(:disabled)').forEach(input=>{const d=(new Date(`${input.value}T00:00Z`).getUTCDay()+6)%7;input.checked=test(d);});renderPanel();}
  /* Drag a planned show along the day strip: it lands on a 5-minute step and is pinned there. */
  $('add-list').addEventListener('pointerdown',e=>{const block=e.target.closest('.series-block.plan');if(!block||e.button!==0)return;const box=block.parentElement;panel.drag={block,box,day:box.dataset.day,index:Number(block.dataset.index),x:e.clientX,minute:Number(block.dataset.minute),moved:false};block.setPointerCapture(e.pointerId);});
  $('add-list').addEventListener('pointermove',e=>{const d=panel.drag;if(!d)return;const dx=e.clientX-d.x;if(Math.abs(dx)<3&&!d.moved)return;d.moved=true;const width=d.box.getBoundingClientRect().width,minute=Math.max(0,Math.min(1435,Math.round((d.minute+dx/width*1080)/5)*5));d.to=minute;d.block.style.left=`${Math.max(0,(minute-480)/1080*100)}%`;d.block.title=C.clock(minute);});
  $('add-list').addEventListener('pointerup',()=>{const d=panel.drag;panel.drag=null;if(!d||!d.moved||d.to==null)return;const l=panel.slots.get(d.day)||[];panel.slots.set(d.day,l);(l[d.index]||(l[d.index]={})).time=C.clock(d.to);renderPanel();});
  $('add-list').addEventListener('pointercancel',()=>{panel.drag=null;renderPanel();});
  function renderBoard(){
    const box=$('board');if(box.querySelector('.sched-grid'))scroll={top:box.scrollTop,left:box.scrollLeft};box.replaceChildren();ghost=null;
    if(!calendar){box.append(node('p',loading?'Đang tải lịch…':loadError||'Chọn rạp để xem lịch.','sched-empty'));return;}
    const list=boardRooms();if(!list.length){box.append(node('p','Rạp chưa có phòng hoạt động. Tạo phòng trước khi xếp suất.','sched-empty'));return;}
    fit();view=viewWindow();const hours=(view.to-view.from)/60,height=box.clientHeight||480;hourPx=Math.max(small.matches?66:26,Math.floor((height-48)/hours));
    const grid=node('div',undefined,'sched-grid');grid.style.setProperty('--hour',`${hourPx}px`);grid.style.gridTemplateColumns=`52px repeat(${list.length},minmax(${small.matches?0:150}px,1fr))`;
    grid.append(node('div','Giờ','sched-head axis'));
    for(const room of list){const head=button(room.name,()=>{if(room.active)pickRoom(room.id);},`sched-head${String(room.id)===$('room').value?' target':''}`);head.title=room.active?'Chọn phòng này cho Thêm theo giờ':'Phòng đã ngừng hoạt động';head.append(node('small',room.active?room.format:'Ngừng hoạt động'));grid.append(head);}
    const axis=node('div',undefined,'sched-axis');axis.style.height=`${hours*hourPx}px`;for(let minute=view.from;minute<view.to;minute+=60){const mark=node('span',C.clock(minute));mark.style.top=`${(minute-view.from)/60*hourPx}px`;axis.append(mark);}grid.append(axis);
    const base=C.stamp(`${activeDay}T00:00`),items=dayItems(activeDay),nowMinute=(Date.parse(now())+7*3600000-base)/60000;
    for(const room of list){
      const col=node('div',undefined,`sched-col${room.active?'':' inactive'}`);col.dataset.room=room.id;col.style.height=`${hours*hourPx}px`;
      for(let h=1;h<hours;h++){const line=node('span',undefined,'sched-hourline');line.style.top=`${h*hourPx}px`;col.append(line);}
      if(nowMinute>view.from){const past=node('div',undefined,'sched-past');past.style.height=`${Math.min(hours,(nowMinute-view.from)/60)*hourPx}px`;col.append(past);}
      const daily=items.filter(i=>String(i.roomId)===String(room.id)).sort((a,b)=>C.stamp(a.startsAt)-C.stamp(b.startsAt));
      for(const item of daily){
        const starts=(C.stamp(item.startsAt)-base)/60000,from=Math.max(view.from,starts),until=Math.min(view.to,(C.stamp(item.cleanupUntil)-base)/60000),end=Math.min(until,Math.max(from,(C.stamp(item.endsAt)-base)/60000));if(until<=from)continue;
        const row=item.row,value=row&&checked(row.rowKey),bad=!!value?.errors.length,carried=starts<0,block=node('article',undefined,`sched-event ${row?bad?'fresh error':'fresh':item.status==='DRAFT'?'saved draft':'saved open'}${!row&&item.locked?' locked':''}${!row&&!carried&&movable(item)?' movable':''}`);
        block.style.top=`${(from-view.from)/60*hourPx}px`;block.style.height=`${(until-from)/60*hourPx}px`;block.style.setProperty('--tone',tone(item.movieId));
        const peers=daily.filter(other=>C.overlaps(item,other)),lane=peers.indexOf(item);if(peers.length>1){block.style.left=`calc(${lane/peers.length*100}% + 2px)`;block.style.width=`calc(${100/peers.length}% - 4px)`;block.style.right='auto';}
        const label=row?'Mới, chưa lưu':item.locked?'Có vé':item.status==='OPEN'?'Đang bán vé':'Nháp',body=button('',()=>row?edit(row):showExisting(item),'event-body');
        body.append(node('strong',`${carried?'Tiếp · ':''}${item.startsAt.slice(11,16)} ${item.movieTitle}`),node('small',bad?value.errors[0].message:`${label} · hết ${item.endsAt.slice(11,16)}`));
        body.title=`${item.movieTitle} · ${when(item.startsAt)} – ${item.endsAt.slice(11,16)} · dọn đến ${item.cleanupUntil.slice(11,16)} · ${bad?value.errors.map(e=>e.message).join(' '):label}`;block.append(body);
        if(row){block.dataset.rowKey=row.rowKey;if(!carried){
          const drop=button('×',()=>remove(row.rowKey),'event-remove');drop.title='Bỏ suất này';drop.setAttribute('aria-label',`Bỏ suất ${item.movieTitle} ${when(item.startsAt)}`);block.append(drop);
          body.addEventListener('pointerdown',e=>{if(locked()||e.button!==0||e.pointerType==='touch')return;drag={row,block,id:e.pointerId,x:e.clientX,y:e.clientY,grab:(e.clientY-block.getBoundingClientRect().top)/hourPx*60,active:false,spot:null};});
          body.addEventListener('keydown',e=>{if(e.key==='Delete'){e.preventDefault();remove(row.rowKey);}});
        }}
        else if(!carried&&movable(item))body.addEventListener('pointerdown',e=>{if(locked()||e.button!==0||e.pointerType==='touch')return;drag={row:savedRow(item),event:item,block,id:e.pointerId,x:e.clientX,y:e.clientY,grab:(e.clientY-block.getBoundingClientRect().top)/hourPx*60,active:false,spot:null};});
        if(until>end){const clean=node('div',undefined,'event-clean');clean.style.height=`${(until-end)/60*hourPx}px`;clean.title='Dọn phòng 15 phút';block.append(clean);}
        col.append(block);
      }
      grid.append(col);
    }
    box.append(grid);box.scrollTop=initialScroll?(activeDay===today()?Math.max(0,(nowMinute-view.from)/60*hourPx-40):0):scroll.top;box.scrollLeft=initialScroll?0:scroll.left;initialScroll=false;state();
  }
  /* Where a show would land for a pointer in a room column; `source` is the unsaved row being dragged, if any. */
  function place(col,clientY,source,grab=0){
    const movie=source?source.movieId:movieId,duration=durations.get(String(movie));if(!duration||!calendar)return null;const room=col.dataset.room;
    const minute=Math.max(0,Math.min(1439,view.from+(clientY-col.getBoundingClientRect().top)/hourPx*60-grab)),events=others(source),spot=C.snap(minute,duration+15,C.busy(activeDay,room,durations,events,draft.rows,source?.rowKey));
    const old=source&&rooms.find(r=>String(r.id)===String(source.roomId)),next=rooms.find(r=>String(r.id)===String(room));
    const row=source?{...source,roomId:String(room),date:activeDay,time:spot.time,format:old&&next&&source.format===old.format?next.format:source.format}:{...defaults(room),rowKey:'candidate',sourceRow:null,date:activeDay,time:spot.time};
    return {...spot,row,duration,reason:spot.reason||C.problem(row,durations,events,draft.rows,now())};
  }
  function clearGhost(){ghost?.remove();ghost=null;}
  function drawGhost(col,spot){
    clearGhost();const t=C.timing(spot.row,durations);if(!t)return;ghost=node('div',undefined,`sched-ghost${spot.reason?' invalid':''}`);
    ghost.append(node('strong',`${spot.time} → ${t.endsAt.slice(11,16)}`),node('small',spot.reason||`${title(spot.row.movieId)} · dọn đến ${t.cleanupUntil.slice(11,16)}`));
    ghost.style.top=`${Math.max(0,(spot.minute-view.from)/60*hourPx)}px`;ghost.style.height=`${Math.max(18,Math.min(spot.duration+15,view.to-Math.max(view.from,spot.minute))/60*hourPx)}px`;col.append(ghost);
  }
  function clearDrag(){drag?.block.classList.remove('dragging');drag=null;clearGhost();}
  root.addEventListener('click',e=>{if(suppressClick){e.preventDefault();e.stopPropagation();suppressClick=false;}},true);
  $('board').addEventListener('pointermove',e=>{
    if(drag||e.pointerType==='touch')return;const col=e.target.closest?.('.sched-col');
    if(!col||e.target.closest('.sched-event')||locked()||!movieId||col.classList.contains('inactive')){clearGhost();return;}
    const spot=place(col,e.clientY);if(spot)drawGhost(col,spot);
  });
  $('board').addEventListener('pointerleave',()=>{if(!drag)clearGhost();});
  $('board').addEventListener('click',e=>{
    const col=e.target.closest('.sched-col');if(!col||e.target.closest('.sched-event')||locked()||!calendar)return;
    if(col.classList.contains('inactive')){message('Phòng đã ngừng hoạt động. Mở lại phòng trước khi xếp suất.','error');return;}
    if(!movieId){message('Chọn phim ở danh sách bên cạnh trước khi thêm suất.','error');return;}
    const spot=place(col,e.clientY);if(!spot)return;clearGhost();if(spot.reason){message(spot.reason,'error');return;}$('room').value=String(col.dataset.room);add(spot.row);
  });
  addEventListener('pointermove',e=>{
    if(!drag||e.pointerId!==drag.id)return;if(!drag.active&&Math.hypot(e.clientX-drag.x,e.clientY-drag.y)<6)return;if(locked()||e.buttons===0){clearDrag();return;}
    drag.active=true;drag.block.classList.add('dragging');e.preventDefault();const box=$('board'),bounds=box.getBoundingClientRect();
    if(e.clientY<bounds.top+70)box.scrollTop-=16;else if(e.clientY>bounds.bottom-30)box.scrollTop+=16;
    const col=document.elementFromPoint(e.clientX,e.clientY)?.closest('.sched-col');
    if(col&&!col.classList.contains('inactive')){drag.spot=place(col,e.clientY,drag.row,drag.grab);if(drag.spot)drawGhost(col,drag.spot);}else{drag.spot=null;clearGhost();}
  },{passive:false});
  addEventListener('pointerup',e=>{
    if(!drag||e.pointerId!==drag.id)return;const done=drag;clearDrag();if(!done.active)return;suppressClick=true;setTimeout(()=>{suppressClick=false;},0);
    if(!done.spot)return;if(done.spot.reason){message(`Không dời suất: ${done.spot.reason}`,'error');return;}if(done.event){const r=done.spot.row;if(r.time!==done.row.time||String(r.roomId)!==String(done.row.roomId)||r.date!==done.row.date)moveSaved(done.event,r);return;}changed(draft.rows.map(r=>r.rowKey===done.row.rowKey?done.spot.row:r));
  });
  addEventListener('pointercancel',e=>{if(drag?.id===e.pointerId)clearDrag();});
  function filteredRows(){return $('errors-only').checked?draft.rows.filter(r=>checked(r.rowKey)?.errors.length):draft.rows;}
  function renderRows(){
    const rows=filteredRows();page=Math.max(0,Math.min(page,Math.ceil(rows.length/5)-1));$('rows').replaceChildren();$('panel-title').textContent=`Đang xếp ${draft.rows.length} suất`;
    $('summary').textContent=!draft.rows.length?'':draft.preview?`${draft.preview.total-draft.preview.invalid} hợp lệ · ${draft.preview.invalid} cần sửa.`:'Đang chờ kiểm tra toàn bộ đợt.';
    for(const row of rows.slice(page*5,page*5+5)){
      const value=checked(row.rowKey),card=node('article',undefined,`sched-row${value?.errors.length?' has-error':''}`),t=C.timing(row,durations);
      card.append(node('strong',value?.movieTitle||title(row.movieId)),node('span',`${dayName(row.date)} ${row.time} · ${value?.roomName||roomName(row.roomId)}`),node('small',sourceText(row)));
      if(t)card.append(node('small',`Kết thúc ${when(t.endsAt)} · Hết dọn phòng ${when(t.cleanupUntil)}`));
      for(const [items,cls] of [[value?.errors.map(e=>e.message),''],[value?.warnings,'warnings']])if(items?.length){const list=node('ul',undefined,cls);items.forEach(text=>list.append(node('li',text)));card.append(list);}
      const actions=node('div',undefined,'actions');actions.append(button('Xem / sửa',()=>edit(row)),button('Bỏ suất',()=>remove(row.rowKey)));card.append(actions);$('rows').append(card);
    }
    if(!rows.length)$('rows').append(node('p',draft.rows.length?'Không có dòng lỗi.':'Chưa có suất đang xếp.','sched-empty'));$('page').textContent=`${rows.length?page+1:0} / ${Math.ceil(rows.length/5)}`;state();
  }
  function edit(row){
    if(locked())return;editing=row.rowKey;editorDirty=false;
    for(const [id,field] of [['line-movie','movieId'],['line-room','roomId'],['line-day','date'],['time','time'],['line-language','language'],['line-format','format'],['line-surcharge','surcharge']]){
      if((id==='line-room'||id==='line-movie')&&![...$(id).options].some(o=>o.value===String(row[field]))){const option=node('option',`Cần sửa mã ${row[field]||'(trống)'}`);option.value=row[field]||'';$(id).append(option);}$(id).value=row[field]||'';
    }
    const value=checked(row.rowKey);$('editor-source').textContent=sourceText(row);$('edit-error').hidden=!value?.errors.length;$('edit-error').textContent=value?.errors.map(e=>e.message).join(' ')||'';updateEnd();
    if(inWeek(row.date)&&row.date!==activeDay){activeDay=row.date;$('day').value=row.date;render();}$('editor').showModal();
  }
  function updateEnd(){const t=C.timing({movieId:$('line-movie').value,roomId:$('line-room').value,date:$('line-day').value,time:$('time').value},durations);$('line-end').textContent=t?`Kết thúc ${when(t.endsAt)} · Hết dọn phòng ${when(t.cleanupUntil)}`:'Chọn phim, ngày và giờ để tính kết thúc.';}
  function closeEdit(){editing=null;editorDirty=false;$('editor').close();state();if(draft.rows.length&&!draft.ready())check();}
  $('line-form').addEventListener('input',()=>{editorDirty=true;$('edit-error').hidden=true;updateEnd();state();});
  $('line-form').addEventListener('submit',e=>{e.preventDefault();if(locked()||!editing)return;if(!inWeek($('line-day').value)){$('edit-error').textContent='Chọn ngày thuộc tuần đang xếp.';$('edit-error').hidden=false;return;}const old=draft.rows.find(r=>r.rowKey===editing);if(!old)return;const row={...old,movieId:$('line-movie').value,roomId:$('line-room').value,date:$('line-day').value,time:$('time').value,language:$('line-language').value,format:$('line-format').value,surcharge:$('line-surcharge').value};closeEdit();changed(draft.rows.map(r=>r.rowKey===row.rowKey?row:r));});
  $('cancel-edit').onclick=closeEdit;$('editor').addEventListener('cancel',()=>{editing=null;editorDirty=false;state();if(draft.rows.length&&!draft.ready())check();});$('delete-edit').onclick=()=>{const key=editing;closeEdit();remove(key);};
  async function switchScope(cinema,day){
    if(locked())return;if(!validDay(day)){$('day').value=activeDay;return;}
    if(cinema===activeCinema&&C.monday(day)===week){activeDay=day;$('day').value=day;render();return;}
    if(dirty()&&!await ask('Đổi rạp hoặc tuần sẽ bỏ các suất đang xếp chưa lưu. Ở lại để tạo lịch trước.','Bỏ thay đổi và chuyển','Có lịch chưa lưu')){$('day').value=activeDay;$('cinema').value=activeCinema;return;}
    activeCinema=cinema;activeDay=day;week=C.monday(day);$('day').value=day;$('cinema').value=cinema;draft.replace([]);origins.clear();notify('');page=0;initialScroll=true;clearDrag();await loadCalendar();
  }
  $('day').onchange=()=>switchScope(activeCinema,$('day').value);$('cinema').onchange=()=>switchScope($('cinema').value,activeDay);$('back').onclick=()=>switchScope(activeCinema,C.addDays(week,-7));$('forward').onclick=()=>switchScope(activeCinema,C.addDays(week,7));$('today').onclick=()=>switchScope(activeCinema,today());$('refresh').onclick=()=>loadCalendar();
  $('movie-filter').oninput=renderMovies;
  for(const id of ['from','to'])$(id).onchange=()=>{if(Number($('to').value)<=Number($('from').value))$(id==='from'?'to':'from').value=String(Number($(id).value)+(id==='from'?1:-1));initialScroll=true;renderMain();};
  $('errors-only').onchange=()=>{page=0;renderRows();};$('prev').onclick=()=>{page--;renderRows();};$('next').onclick=()=>{page++;renderRows();};
  $('view-week').onclick=()=>setMode('week');$('view-day').onclick=()=>setMode('day');
  $('add-open').onclick=()=>panel.open?$('add').querySelector('.sched-chip.on')?.focus():openAdd(mode==='week'?currentRoom():$('room').value,activeDay>=today()?activeDay:'');
  $('add-close').onclick=closeAdd;$('add-cancel').onclick=closeAdd;
  $('add-all').onclick=()=>setPanelDays(()=>true);$('add-weekdays').onclick=()=>setPanelDays(d=>d<5);$('add-weekend').onclick=()=>setPanelDays(d=>d>=5);
  $('add-film').onclick=async()=>{const id=$('add-movie').value;if(!id)return;const old=panel.films.slice();panel.films.push({id,count:3});if(!await changePlan())panel.films=old;renderPanel();};
  $('add-form').addEventListener('change',e=>{
    if(e.target.closest('#batch-add-list')||e.target.id==='batch-add-movie')return;
    if(e.target.name==='batch-add-mode'||e.target.name==='batch-add-order'){panel.slots.clear();panel.extra.clear();}
    // A new first start moves the first show of every day; the last start never drops pins.
    if(e.target.id==='batch-add-start')panel.slots.forEach(list=>{if(list[0])delete list[0].time;});
    renderPanel();
  });
  $('add').addEventListener('keydown',e=>{if(e.key==='Escape'&&!document.querySelector('dialog[open]')){e.preventDefault();closeAdd();}});
  $('add-form').addEventListener('submit',e=>{
    e.preventDefault();if(locked()||!calendar)return;const picked=seriesRows().map(row=>({...row,rowKey:uuid()}));if(!picked.length)return;
    const skipped=panel.result.flatMap(d=>d.list.filter(x=>!x.off&&x.reason).map(x=>`${dayName(d.day)} ${x.row.time}: ${x.reason}`));let rows;
    try{rows=C.merge(draft.rows,picked);}catch(error){$('add-summary').textContent=error.message;return;}
    const room=panelRoom(),films=[...new Set(picked.map(r=>r.movieId))];movieId=films[0];viewRoom=String(room);closeAdd();changed(rows);
    notify(`Đã thêm ${picked.length} suất ${films.map(title).join(', ')} tại ${roomName(room)}, ${new Set(picked.map(r=>r.date)).size} ngày. Chưa lưu cho đến khi bạn bấm Lưu nháp hoặc Lưu và mở bán.${skipped.length?` Bỏ qua ${skipped.length} suất trùng lịch hoặc ngoài thời gian chiếu.`:''}`,skipped);
  });
  /* Chép ngày works in both views: pick the day whose new shows are copied, then the days that receive them. */
  let copyFrom='';
  function fillCopy(){
    const source=draft.rows.filter(r=>r.date===copyFrom);
    $('copy-info').textContent=`Chép ${source.length} suất mới của ${dayName(copyFrom)} sang các ngày bạn chọn, giữ nguyên phim, phòng và giờ. Suất bị trùng lịch sẽ không được thêm và được liệt kê lại.`;$('copy-error').hidden=true;
    const box=$('copy-days');box.replaceChildren();
    for(let d=0;d<7;d++){const day=C.addDays(week,d);if(day===copyFrom)continue;const label=node('label'),input=node('input');input.type='checkbox';input.value=day;input.disabled=day<today();label.append(input,node('span',`${dayName(day)}${input.disabled?' · đã qua':''}`));box.append(label);}
  }
  $('copy').onclick=()=>{
    if(locked()||!calendar)return;const days=[...new Set(draft.rows.map(r=>r.date))].filter(inWeek).sort();if(!days.length){message('Chưa có suất mới để chép. Thêm suất cho một ngày trước.','error');return;}
    copyFrom=days.includes(activeDay)?activeDay:days[0];const select=$('copy-from');select.replaceChildren();
    for(const day of days){const o=node('option',`${dayName(day)} · ${draft.rows.filter(r=>r.date===day).length} suất mới`);o.value=day;select.append(o);}select.value=copyFrom;select.onchange=()=>{copyFrom=select.value;fillCopy();};
    fillCopy();$('copy-dialog').showModal();
  };
  $('copy-cancel').onclick=()=>$('copy-dialog').close();$('copy-all').onclick=()=>$('copy-days').querySelectorAll('input:not(:disabled)').forEach(input=>{input.checked=true;});
  $('copy-apply').onclick=()=>{
    const days=[...$('copy-days').querySelectorAll('input:checked')].map(input=>input.value);if(!days.length){$('copy-error').hidden=false;return;}
    const result=C.copyDay(draft.rows.filter(r=>r.date===copyFrom),days,durations,calendar.events,draft.rows,now(),uuid);$('copy-dialog').close();
    if(result.added.length)changed(C.merge(draft.rows,result.added));
    const skipped=result.skipped.map(s=>`${dayName(s.row.date)} ${s.row.time} · ${title(s.row.movieId)} · ${roomName(s.row.roomId)}: ${s.reason}`),lost=new Map();result.skipped.forEach(s=>lost.set(s.row.date,(lost.get(s.row.date)||0)+1));
    notify(`Đã chép ${result.added.length} suất sang ${new Set(result.added.map(r=>r.date)).size} ngày.${result.skipped.length?` Không thêm ${result.skipped.length} suất bị trùng lịch hoặc đã qua giờ: ${[...lost].map(([day,count])=>`${dayName(day)} (${count})`).join(', ')}.`:''}`,skipped);
  };
  $('jump').onclick=()=>{
    const item=draft.preview?.rows.find(r=>r.errors.length);if(!item||locked())return;const row=item.input;if(inWeek(row.date)){activeDay=row.date;$('day').value=row.date;}
    if(rooms.some(r=>r.active&&String(r.id)===String(row.roomId)))$('room').value=String(row.roomId);if(row.roomId)viewRoom=String(row.roomId);render();
    const body=[...root.querySelectorAll(mode==='day'?'#batch-board .sched-event':'#batch-week .week-chip')].find(el=>el.dataset.rowKey===row.rowKey)?.querySelector('button');
    if(body){body.scrollIntoView({block:'center',inline:'center'});body.focus();}else{$('errors-only').checked=true;page=0;renderRows();$('pending-panel').open=true;$('pending-panel').scrollIntoView({block:'center'});}
  };
  $('list').onclick=()=>{$('pending-panel').open=true;$('pending-panel').scrollIntoView({block:'start',behavior:'smooth'});};
  $('import').onclick=()=>{$('excel').open=false;$('file').click();};
  $('file').onchange=async()=>{
    const file=$('file').files[0];if(!file||locked())return;if(file.size>5*1024*1024||!/\.xlsx$/i.test(file.name)){message('Chọn file .xlsx tối đa 5 MB.','error');$('file').value='';return;}
    busy=true;previewController?.abort();clearTimeout(previewTimer);state();message('Đang đọc file; lịch đang xếp được giữ nguyên…');let merged=null,failure='',count=0;
    try{
      const form=new FormData();form.set('cinemaId',activeCinema);form.set('targetStatus','DRAFT');form.set('weekStart',week);form.set('file',file);const {body}=await request('/import-preview',{method:'POST',body:form});const incoming=body.preview.rows.map(r=>r.input);
      if(incoming.some(r=>validDay(r.date)&&!inWeek(r.date)))throw new Error('File có ngày ngoài tuần đang xếp. Chưa bổ sung file; sửa ngày hoặc chọn đúng tuần.');merged=C.merge(draft.rows,incoming);count=incoming.length;
      for(const row of incoming)origins.set(row.rowKey,{fileName:file.name,sheetName:body.excel?.sheetName,version:body.excel?.templateVersion||1,...body.excel?.cells?.[row.rowKey]});
      const first=incoming.find(r=>inWeek(r.date));if(first){activeDay=first.date;$('day').value=first.date;if(rooms.some(room=>room.active&&String(room.id)===String(first.roomId)))$('room').value=String(first.roomId);if(first.roomId)viewRoom=String(first.roomId);}
    }catch(e){failure=e.message;}
    finally{busy=false;$('file').value='';if(merged){changed(merged);notify(`Đã đọc ${count} suất từ ${file.name}. Kiểm tra trên bảng hoặc trong danh sách toàn đợt.`);$('pending-panel').open=true;}else{state();if(draft.rows.length&&!draft.ready())await check();if(failure)message(failure,'error');}}
  };
  async function download(template){
    $('excel').open=false;if(!activeCinema||locked())return;
    try{const response=await fetch(`/api/admin/showtime-batches/${template?'template':'export'}.xlsx?cinemaId=${activeCinema}&weekStart=${week}`,{cache:'no-store'});if(!response.ok){const error=await response.json();throw new Error(error.message||'Không tải được Excel.');}const url=URL.createObjectURL(await response.blob()),a=node('a');a.href=url;a.download=`cineprime-${template?'mau':'lich'}-${week}.xlsx`;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),10000);notify(`Đã chuẩn bị ${template?'mẫu trống':'lịch tuần của toàn rạp'}. Điền sheet Đăng ký suất mới rồi nhập lại.`);}
    catch(e){message(e.message,'error');}
  }
  $('template').onclick=()=>download(true);$('export').onclick=()=>download(false);
  function remember(key){const url=new URL(location.href);key?url.searchParams.set('requestKey',key):url.searchParams.delete('requestKey');history.replaceState(null,'',url);}
  function validResult(result){if(!result||result.requestKey!==pending?.key||!Array.isArray(result.created)||!result.created.length||!['OPEN','DRAFT'].includes(result.targetStatus)||!Number.isSafeInteger(result.cinemaId))return false;const keys=new Set();for(const row of result.created){if(!row.rowKey||keys.has(row.rowKey)||!Number.isSafeInteger(row.showtimeId)||!validDay(row.date))return false;keys.add(row.rowKey);}const input=pending.command?.input;return !input||(result.cinemaId===input.cinemaId&&result.targetStatus===input.targetStatus&&keys.size===input.rows.length&&input.rows.every(r=>result.created.some(c=>c.rowKey===r.rowKey&&c.date===r.date)));}
  async function success(result){
    if(!validResult(result))throw new Error('Kết quả lưu chưa đầy đủ; cần tra lại bằng cùng mã yêu cầu.');pending=null;draft.replace([]);origins.clear();notify('');remember(null);activeCinema=String(result.cinemaId);$('cinema').value=activeCinema;
    const days=[...new Set(result.created.map(r=>r.date))].sort();if(!days.includes(activeDay)||C.monday(activeDay)!==C.monday(days[0]))activeDay=days[0];week=C.monday(activeDay);$('day').value=activeDay;renderRows();
    message(`Đã tạo ${result.created.length} suất ${result.targetStatus==='OPEN'?'đang bán vé':'nháp · chưa bán vé'}. Mã lưu: ${result.requestKey}`,'success');
    for(const date of days){const a=node('a',dateLabel(date));a.href=`/admin/schedule?cinemaId=${result.cinemaId}&date=${date}`;$('message').append(a);}await loadCalendar(true);
  }
  async function send(command){busy=true;state();message(`Đang tạo ${command.input.rows.length} suất. Giữ trang đến khi có kết quả.`);try{const {body}=await request('/commit',{method:'POST',body:JSON.stringify(command)});await success(body);}catch(e){if([400,401,403,409,422].includes(e.status)){pending=null;remember(null);draft.invalidate();if(e.body?.preview)draft.accept(draft.revision,e.body.preview);render();message(e.message,'error');if(e.status===409)await loadCalendar(true);}else message('Chưa xác định được kết quả. Giữ nguyên mã yêu cầu; kiểm tra kết quả hoặc gửi lại cùng yêu cầu.','error');}finally{busy=false;state();if(!pending&&draft.rows.length&&!draft.ready())check();}}
  function closeReview(){reviewing=false;reviewCommand=null;$('review').close();state();}
  function renderReview(preview){
    const open=target==='OPEN';$('review-title').textContent=open?'Lưu và mở bán':'Lưu nháp';
    $('review-summary').textContent=`${preview.total} suất · ${new Set(draft.rows.map(r=>r.movieId)).size} phim · ${new Set(draft.rows.map(r=>r.roomId)).size} phòng · ${open?'Mở bán ngay: khách đặt được vé sau khi lưu':'Lưu nháp: khách chưa đặt được, mở bán sau ở tab Danh sách'}. Chưa ghi dữ liệu.`;
    $('review-error').hidden=preview.canCommit;$('review-error').textContent=`${preview.invalid} suất cần sửa${open?' hoặc chưa đủ điều kiện mở bán (thiếu giá, ghế…)':''}. Chưa lưu suất nào.${open?' Có thể Lưu nháp trước rồi mở bán sau.':''}`;
    const days=new Map();for(const item of preview.rows){const day=item.input.date;if(!days.has(day))days.set(day,new Map());const name=item.roomName||roomName(item.input.roomId);days.get(day).set(name,(days.get(day).get(name)||0)+1);}
    $('review-groups').replaceChildren();for(const day of [...days.keys()].sort()){const line=node('p');line.append(node('strong',dayName(day)),node('span',[...days.get(day)].map(([name,count])=>`${name}: ${count} suất`).join(' · ')));$('review-groups').append(line);}
    $('review-details').open=!preview.canCommit;$('review-rows').replaceChildren();
    for(const item of [...preview.rows].sort((a,b)=>String(a.startsAt).localeCompare(String(b.startsAt))||String(a.roomName).localeCompare(String(b.roomName)))){
      const tr=node('tr',undefined,item.errors.length?'review-invalid':''),name=node('td');name.dataset.label='Phim / phòng';name.append(node('strong',`${item.movieTitle||title(item.input.movieId)} · ${item.roomName||roomName(item.input.roomId)}`),node('small',sourceText(item.input)));tr.append(name);
      for(const [label,value] of [['Bắt đầu',when(item.startsAt)],['Kết thúc',when(item.endsAt)],['Hết dọn',when(item.cleanupUntil)]]){const td=node('td',value);td.dataset.label=label;tr.append(td);}
      const status=node('td',item.errors.map(e=>e.message).concat(item.warnings).join(' ')||'Hợp lệ');status.dataset.label='Kiểm tra';status.append(button('Xem / sửa',()=>{closeReview();edit(item.input);}));tr.append(status);$('review-rows').append(tr);
    }
    $('confirm').disabled=!preview.canCommit;$('confirm').textContent=open?`Mở bán ${preview.total} suất`:`Lưu nháp ${preview.total} suất`;
  }
  /* Lưu nháp: when every row passes and nothing is flagged, it saves at once. Lưu và mở bán: always one confirmation, since customers can book right after. */
  async function save(status){
    if(locked()||!calendar||editorDirty||!draft.rows.length)return;target=status;clearTimeout(previewTimer);previewController?.abort();const revision=draft.revision,input=payload(draft.rows,status);busy=true;state();message(status==='OPEN'?'Đang kiểm tra lần cuối với lịch và điều kiện mở bán…':'Đang kiểm tra lần cuối với lịch…');
    let direct=null;
    try{const {body}=await request('/preview',{method:'POST',body:JSON.stringify(input)});if(!draft.accept(revision,body))return;const clean=body.canCommit&&body.rows.every(r=>!r.warnings?.length);
      if(status==='DRAFT'&&clean)direct={input,fingerprint:body.fingerprint,requestKey:uuid()};
      else{reviewCommand=body.canCommit?{input,fingerprint:body.fingerprint}:null;reviewing=true;renderDays();renderMain();renderRows();renderReview(body);message(body.canCommit?`${body.total} suất hợp lệ. Xác nhận để lưu.`:`${body.invalid}/${body.total} suất cần sửa. Chưa lưu suất nào.`,body.canCommit?'':'error');$('review').showModal();}}
    catch(e){message(e.message,'error');}finally{busy=false;state();}
    if(direct){pending={key:direct.requestKey,command:direct};remember(direct.requestKey);await send(direct);}
  }
  $('commit').onclick=()=>save('OPEN');$('draft').onclick=()=>save('DRAFT');
  $('review-back').onclick=closeReview;$('review').addEventListener('cancel',()=>{reviewing=false;reviewCommand=null;state();});
  $('confirm').onclick=async()=>{if(busy||pending||!reviewing||!reviewCommand)return;const command={...reviewCommand,requestKey:uuid()};closeReview();pending={key:command.requestKey,command};remember(command.requestKey);await send(command);};
  $('result').onclick=async()=>{if(busy||!pending)return;busy=true;state();try{const {body,status}=await request(`/results/${encodeURIComponent(pending.key)}`);status===200?await success(body):message(body.message,'error');}catch(e){message(e.message,'error');}finally{busy=false;state();}};
  $('retry').onclick=async()=>{if(!busy&&pending?.command&&await ask('Gửi lại nguyên dữ liệu với cùng mã yêu cầu? Nếu đã tạo, hệ thống trả kết quả cũ.'))await send(pending.command);};
  addEventListener('keydown',e=>{if(e.key==='Escape')clearDrag();});addEventListener('beforeunload',e=>{if(dirty()){e.preventDefault();e.returnValue='';}});
  addEventListener('resize',()=>{clearTimeout(resizeTimer);resizeTimer=setTimeout(()=>{if(!drag)mode==='day'?renderBoard():fit();},150);});small.addEventListener('change',()=>{initialScroll=true;render();});stacked.addEventListener('change',()=>render());
  new ResizeObserver(()=>{clearTimeout(resizeTimer);resizeTimer=setTimeout(()=>{if(!drag&&calendar)mode==='day'?renderBoard():fit();},80);}).observe(root.querySelector('.sched-footer'));
  document.addEventListener('click',e=>{if($('excel').open&&!$('excel').contains(e.target))$('excel').open=false;});
  setInterval(()=>{if(!document.hidden&&!locked()&&!editing&&!drag&&!ghost&&calendar&&!panel.open&&!document.querySelector('dialog.show-editor[open]')&&!$('week-wrap').contains(document.activeElement)){renderDays();renderMain();}},60000);
  for(let hour=0;hour<24;hour++){const from=node('option',C.clock(hour*60)),to=node('option',hour===23?'24:00':C.clock((hour+1)*60));from.value=String(hour);to.value=String(hour+1);$('from').append(from);$('to').append(to);}$('from').value='9';$('to').value='23';
  /* Arriving from "Theo ngày" or "+ Thêm suất chiếu" (…&add=1[&time=HH:MM]) opens the panel with room, day and time filled in. */
  const query=new URL(location.href).searchParams,wantAdd=query.get('add')==='1',wantTime=query.get('time');
  setBounds();renderRows();loadCalendar().then(()=>{if(wantAdd&&calendar&&!pending){openAdd(root.dataset.room||currentRoom(),root.dataset.date,wantTime);const url=new URL(location.href);url.searchParams.delete('add');url.searchParams.delete('time');history.replaceState(null,'',url);}});
  const key=query.get('requestKey');if(key){pending={key};state();$('result').click();}
})();
