/* Week table (room × day) with an hour-by-hour day board as the second view. One batch = one cinema, one week; the server stays the authority for every check. */
(() => {
  'use strict';
  const root=document.getElementById('showtime-batch');if(!root)return;
  const $=id=>document.getElementById(`batch-${id}`),C=window.CinePrimeCalendar,draft=new C.Draft(),origins=new Map();
  const names=['Thứ 2','Thứ 3','Thứ 4','Thứ 5','Thứ 6','Thứ 7','Chủ nhật'],short=['T2','T3','T4','T5','T6','T7','CN'];
  const tones=['#1459a5','#279568','#7c3aed','#c2410c','#0e7490','#be185d','#4d7c0f','#a16207'];
  const node=(tag,text,cls)=>{const el=document.createElement(tag);if(text!==undefined)el.textContent=text;if(cls)el.className=cls;return el;};
  const button=(text,fn,cls='button secondary')=>{const el=node('button',text,cls);el.type='button';el.onclick=fn;return el;};
  const dateLabel=day=>/^\d{4}-\d\d-\d\d$/.test(day||'')?`${day.slice(8,10)}/${day.slice(5,7)}/${day.slice(0,4)}`:day||'Chưa có ngày';
  const when=value=>value?`${value.slice(11,16)} · ${dateLabel(value.slice(0,10))}`:'—';
  const uuid=()=>crypto.randomUUID(),small=matchMedia('(max-width:620.98px)'),stacked=matchMedia('(max-width:850.98px)');
  let rooms=[...$('room').options].filter(o=>o.value).map(o=>({id:o.value,name:o.textContent,format:o.dataset.format,active:true})),movies=[];
  const durations=new Map();
  const title=id=>movies.find(m=>String(m.id)===String(id))?.title||[...$('line-movie').options].find(o=>o.value===String(id))?.textContent||`Phim #${id}`;
  const roomName=id=>rooms.find(r=>String(r.id)===String(id))?.name||`Phòng #${id}`;
  const tone=id=>tones[Math.max(0,movies.findIndex(m=>String(m.id)===String(id)))%tones.length];
  let activeCinema=$('cinema').value,activeDay=$('day').value,week=C.monday(activeDay),movieId=root.dataset.movie||'',calendar=null,loading=false,loadError='';
  let asking=false,busy=false,pending=null,editing=null,editorDirty=false,page=0,calendarRevision=0,previewController=null,previewTimer=null,reviewing=false,reviewCommand=null;
  let serverTime=null,loadedAt=Date.now(),view={from:540,to:1380},hourPx=40,scroll={top:0,left:0},initialScroll=true,ghost=null,drag=null,suppressClick=false,resizeTimer=null;
  let mode='week',focusMovie='',addTouched=false,viewRoom='';
  const now=()=>new Date(Date.parse(serverTime||new Date().toISOString())+Date.now()-loadedAt).toISOString();
  const today=()=>C.iso(Date.parse(now())+7*3600000).slice(0,10);
  const validDay=day=>Number.isFinite(C.stamp(`${day}T00:00`)),inWeek=day=>validDay(day)&&C.monday(day)===week;
  const dayName=day=>inWeek(day)?`${names[Math.round((C.stamp(`${day}T00:00`)-C.stamp(`${week}T00:00`))/86400000)]} ${day.slice(8)}/${day.slice(5,7)}`:dateLabel(day);
  const checked=key=>draft.preview?.rows.find(r=>r.input.rowKey===key);
  const defaults=room=>({movieId,roomId:String(room),language:$('language').value||'Phụ đề',format:$('format').value||rooms.find(r=>String(r.id)===String(room))?.format||'',surcharge:$('surcharge').value||'0'});
  const payload=rows=>({cinemaId:Number(activeCinema),source:C.source(rows),targetStatus:$('status').value,rows});
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
    root.querySelectorAll('input,select,button').forEach(el=>{if(!el.closest('dialog'))el.disabled=locked();});
    const ready=!locked()&&!loading&&!!calendar,bad=draft.preview?.invalid||0;
    $('add-open').disabled=!ready;$('copy').disabled=!ready||!draft.rows.some(r=>r.date===activeDay);$('guide').hidden=!!draft.rows.length;
    $('commit').textContent=`Tạo ${draft.rows.length} suất`;$('commit').disabled=!ready||editorDirty||!draft.rows.length;
    $('prev').disabled=locked()||page===0;$('next').disabled=locked()||(page+1)*5>=filteredRows().length;
    $('result').hidden=!pending;$('result').disabled=busy;$('retry').hidden=!pending?.command;$('retry').disabled=busy;$('jump').hidden=!bad;$('list').hidden=!draft.rows.length;
    $('total').textContent=draft.rows.length?`${draft.rows.length} suất mới chưa lưu · ${new Set(draft.rows.map(r=>r.date)).size} ngày · ${new Set(draft.rows.map(r=>r.roomId)).size} phòng${bad?` · ${bad} cần sửa`:''}`:'Chưa có suất mới';
    $('hint').textContent=!calendar?'':movieId?`${title(movieId)} · ${durations.get(movieId)} phút + 15 phút dọn. Bấm cột phòng để thêm, kéo suất mới để dời.`:'Chọn phim bên cạnh rồi bấm vào cột phòng, hoặc bấm + Thêm suất.';
    root.classList.toggle('is-locked',locked());
  }
  const idle=()=>mode==='week'?'Chọn phòng, rồi bấm + Thêm ở ngày muốn chiếu. Chưa có gì được lưu cho đến khi bạn bấm Tạo.':'Chọn phim rồi bấm vào cột phòng để thêm suất. Chưa có gì được lưu cho đến khi bạn bấm Tạo.';
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
    try{const {body}=await request('/preview',{method:'POST',body:JSON.stringify(payload(draft.rows)),signal:controller.signal});if(previewController!==controller||!draft.accept(revision,body))return;message([body.canCommit?`${body.total} suất hợp lệ, chưa lưu. Bấm Tạo ${body.total} suất khi xong.`:`${body.invalid}/${body.total} suất cần sửa. Chưa lưu suất nào.`,legacyWarning()].filter(Boolean).join(' '),body.canCommit?'':'error');renderDays();renderMain();renderRows();}
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
  function showExisting(e){$('event-info').replaceChildren(node('h3',e.movieTitle),node('p',`${roomName(e.roomId)} · ${when(e.startsAt)} – ${when(e.endsAt)}`),node('p',`Dọn phòng đến ${when(e.cleanupUntil)}`),node('p',e.status==='OPEN'?'Đang bán vé':'Nháp · chưa bán vé'),node('p','Suất đã lưu được chỉnh ở trang riêng; hệ thống bảo vệ suất có vé hoặc lượt giữ ghế.'));$('event-edit').href=`/admin/manage/showtimes/edit?id=${e.id}&returnTo=${encodeURIComponent(`/admin/manage/showtimes?cinemaId=${activeCinema}&date=${activeDay}`)}`;$('event-dialog').showModal();}
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
      let all=0,fresh=0,bad=0;for(let d=0;d<7;d++)for(const i of items.get(`${room.id}|${C.addDays(week,d)}`)||[]){all++;if(i.row){fresh++;if(checked(i.row.rowKey)?.errors.length)bad++;}}
      const tab=button('',()=>pickRoom(room.id),`button secondary sched-room${String(room.id)===chosen?' active':''}${bad?' bad':''}`);tab.setAttribute('aria-pressed',String(String(room.id)===chosen));
      tab.append(node('strong',room.name),node('small',[room.active?room.format:'Ngừng hoạt động',calendar?`${all} suất`:'',fresh?`${fresh} mới`:'',bad?`${bad} lỗi`:''].filter(Boolean).join(' · ')));box.append(tab);
    }
    if(had)box.querySelector('.active')?.focus();
  }
  function pickRoom(id){if(locked())return;viewRoom=String(id);if([...$('room').options].some(o=>o.value===viewRoom))$('room').value=viewRoom;focusMovie='';renderStrip();renderMain();state();}
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
      const day=C.addDays(week,d),all=items.get(`${room.id}|${day}`)||[],bad=all.filter(i=>i.row&&checked(i.row.rowKey)?.errors.length).length,fresh=all.filter(i=>i.row).length,when=`${day<today()?'past':''}${day===today()?' today':''}`.trim();
      const th=node('th',undefined,`${when}${bad?' bad':''}`.trim()),open=button('',()=>setMode('day',day),'week-day');th.scope=rows?'row':'col';open.title='Xem ngày này theo giờ, mọi phòng';
      open.append(node('strong',`${short[d]} ${day.slice(8)}/${day.slice(5,7)}${day===today()?' · hôm nay':''}`),node('small',[all.length?`${all.length} suất`:day<today()?'đã qua':'chưa có suất',fresh?`${fresh} mới`:'',bad?`${bad} lỗi`:''].filter(Boolean).join(' · ')));th.append(open);
      const td=node('td',undefined,when),cell=node('div',undefined,'week-cell');
      for(const item of all){
        const row=item.row,value=row&&checked(row.rowKey),wrong=!!value?.errors.length,label=row?'Mới, chưa lưu':item.status==='OPEN'?'Đang bán vé':'Nháp, chưa bán';
        const chip=node('div',undefined,`week-chip ${row?wrong?'fresh error':'fresh':item.status==='DRAFT'?'saved draft':'saved open'}${focusMovie&&String(item.movieId)!==focusMovie?' dim':''}`),show=button('',()=>row?edit(row):showExisting(item),'chip-body'),time=row?row.time||'--:--':item.startsAt.slice(11,16);
        chip.style.setProperty('--tone',tone(item.movieId));show.append(node('b',`${time}${item.endsAt?` – ${item.endsAt.slice(11,16)}`:''}`),node('span',item.movieTitle));if(wrong)show.append(node('small',value.errors[0].message));
        show.title=`${item.movieTitle} · ${time}${item.endsAt?` – ${item.endsAt.slice(11,16)} · dọn phòng đến ${item.cleanupUntil.slice(11,16)}`:''} · ${wrong?value.errors.map(e=>e.message).join(' '):label}`;chip.append(show);
        if(row){chip.dataset.rowKey=row.rowKey;const drop=button('×',()=>remove(row.rowKey),'chip-remove');drop.title='Bỏ suất này';drop.setAttribute('aria-label',`Bỏ suất ${item.movieTitle} ${time} ${dayName(day)}`);chip.append(drop);}
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
  const addDays=()=>[...$('add-days').querySelectorAll('input:checked')].map(input=>input.value),addMany=()=>root.querySelector('input[name=batch-add-mode]:checked')?.value==='many';
  /* Earliest free start in the chosen room on the first chosen day: after the shows already there, never in the past. */
  function suggestAdd(){
    const day=addDays()[0],room=$('room').value;if(!calendar||!day||!room||addTouched)return;const base=C.stamp(`${day}T00:00`),spans=C.busy(day,room,durations,calendar.events,draft.rows);
    const soon=day===today()?C.roundUp((Date.parse(now())+7*3600000-base)/60000+1):0,from=Math.max(540,soon),fits=C.fillDay({movieId:$('add-movie').value,roomId:room,date:day,time:C.clock(Math.min(1435,from))},1435,durations,calendar.events,draft.rows,now(),()=>'suggest').added[0];
    $('add-start').value=fits?fits.time:C.clock(Math.max(0,Math.min(1435,C.firstFree(from,spans))));
  }
  function computeAdd(key){
    const room=$('room').value,movie=$('add-movie').value,start=$('add-start').value,many=addMany(),last=C.minutes($('add-until').value),days=addDays();
    if(!room||!movie)return {error:'Chọn phòng và phim.'};if(!days.length)return {error:'Chọn ít nhất một ngày chiếu.'};
    if(!Number.isFinite(C.minutes(start)))return {error:'Nhập giờ bắt đầu.'};if(many&&!Number.isFinite(last))return {error:'Nhập giờ bắt đầu muộn nhất của suất cuối.'};
    if(many&&last<C.minutes(start))return {error:'Giờ của suất cuối phải sau giờ bắt đầu.'};
    return C.plan({...defaults(room),movieId:movie,time:start},days,many?last:null,durations,calendar.events,draft.rows,now(),key);
  }
  function previewAdd(){
    const box=$('add-preview'),apply=$('add-apply'),many=addMany();$('add-until-field').hidden=!many;box.replaceChildren();if(!calendar)return;
    const day=addDays()[0],room=$('room').value,there=day&&room?dayItems(day).filter(i=>String(i.roomId)===room&&i.startsAt.slice(0,10)===day).sort((a,b)=>C.stamp(a.startsAt)-C.stamp(b.startsAt)):[];
    $('add-busy').textContent=!day||!room?'':there.length?`${roomName(room)}, ${dayName(day)} đang có: ${there.map(i=>`${i.startsAt.slice(11,16)}–${i.endsAt.slice(11,16)} ${i.movieTitle}`).join(' · ')}.`:`${roomName(room)}, ${dayName(day)} chưa có suất nào.`;
    let n=0;const result=computeAdd(()=>`preview-${n++}`);
    if(result.error||!result.added.length){
      box.className='sched-add-preview empty';box.append(node('p',result.error||'Không thêm được suất nào.'));
      if(result.skipped?.length){const list=node('ul');result.skipped.forEach(s=>list.append(node('li',`${dayName(s.row.date)} ${s.row.time}: ${s.reason}`)));box.append(list);}
      apply.textContent='Thêm vào lịch';apply.disabled=true;return;
    }
    const movie=$('add-movie').value,byDay=new Map();result.added.forEach(row=>{if(!byDay.has(row.date))byDay.set(row.date,[]);byDay.get(row.date).push(row.time);});
    box.className='sched-add-preview';box.append(node('p',`Sẽ thêm ${result.added.length} suất ${title(movie)} tại ${roomName(room)}. Mỗi suất dài ${durations.get(String(movie))} phút, cộng 15 phút dọn phòng.`));
    const list=node('ul');for(const [date,times] of byDay)list.append(node('li',`${dayName(date)}: ${times.join(' · ')}`));box.append(list);
    if(result.skipped.length){box.append(node('p',`Không thêm ${result.skipped.length} suất:`,'skipped'));const lost=node('ul',undefined,'skipped');result.skipped.forEach(s=>lost.append(node('li',`${dayName(s.row.date)} ${s.row.time}: ${s.reason}`)));box.append(lost);}
    apply.textContent=`Thêm ${result.added.length} suất vào lịch`;apply.disabled=false;
  }
  function openAdd(room,day){
    if(locked()||!calendar)return;const list=movies.filter(m=>m.active&&m.status!=='ENDED'),active=rooms.filter(r=>r.active);
    if(!active.length){message('Rạp chưa có phòng hoạt động. Tạo phòng trước khi xếp suất.','error');return;}if(!list.length){message('Chưa có phim đang hoạt động để xếp suất.','error');return;}
    if(room&&active.some(r=>String(r.id)===String(room)))$('room').value=String(room);if(!$('room').value)$('room').value=String(active[0].id);
    const select=$('add-movie');select.replaceChildren();for(const m of list){const o=node('option',`${m.title} · ${m.duration} phút${m.status==='COMING_SOON'?' · Sắp chiếu':''}`);o.value=String(m.id);select.append(o);}
    select.value=list.some(m=>String(m.id)===movieId)?movieId:String(list[0].id);
    const box=$('add-days');box.replaceChildren();
    for(let d=0;d<7;d++){const date=C.addDays(week,d),label=node('label'),input=node('input');input.type='checkbox';input.value=date;input.disabled=date<today();input.checked=date===day&&!input.disabled;label.append(input,node('span',`${short[d]} ${date.slice(8)}/${date.slice(5,7)}${input.disabled?' · đã qua':''}`));box.append(label);}
    root.querySelector('input[name=batch-add-mode][value=one]').checked=true;addTouched=false;suggestAdd();previewAdd();$('add-dialog').showModal();
  }
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
      if(nowMinute>view.from){const past=node('div',undefined,'sched-past');past.style.height=`${Math.min(hours,(nowMinute-view.from)/60)*hourPx}px`;col.append(past);}
      const daily=items.filter(i=>String(i.roomId)===String(room.id)).sort((a,b)=>C.stamp(a.startsAt)-C.stamp(b.startsAt));
      for(const item of daily){
        const starts=(C.stamp(item.startsAt)-base)/60000,from=Math.max(view.from,starts),until=Math.min(view.to,(C.stamp(item.cleanupUntil)-base)/60000),end=Math.min(until,Math.max(from,(C.stamp(item.endsAt)-base)/60000));if(until<=from)continue;
        const row=item.row,value=row&&checked(row.rowKey),bad=!!value?.errors.length,carried=starts<0,block=node('article',undefined,`sched-event ${row?bad?'fresh error':'fresh':item.status==='DRAFT'?'saved draft':'saved open'}`);
        block.style.top=`${(from-view.from)/60*hourPx}px`;block.style.height=`${(until-from)/60*hourPx}px`;block.style.setProperty('--tone',tone(item.movieId));
        const peers=daily.filter(other=>C.overlaps(item,other)),lane=peers.indexOf(item);if(peers.length>1){block.style.left=`calc(${lane/peers.length*100}% + 2px)`;block.style.width=`calc(${100/peers.length}% - 4px)`;block.style.right='auto';}
        const label=row?'Mới, chưa lưu':item.status==='OPEN'?'Đang bán vé':'Nháp',body=button('',()=>row?edit(row):showExisting(item),'event-body');
        body.append(node('strong',`${carried?'Tiếp · ':''}${item.startsAt.slice(11,16)} ${item.movieTitle}`),node('small',bad?value.errors[0].message:`${label} · hết ${item.endsAt.slice(11,16)}`));
        body.title=`${item.movieTitle} · ${when(item.startsAt)} – ${item.endsAt.slice(11,16)} · dọn đến ${item.cleanupUntil.slice(11,16)} · ${bad?value.errors.map(e=>e.message).join(' '):label}`;block.append(body);
        if(row){block.dataset.rowKey=row.rowKey;if(!carried){
          const drop=button('×',()=>remove(row.rowKey),'event-remove');drop.title='Bỏ suất này';drop.setAttribute('aria-label',`Bỏ suất ${item.movieTitle} ${when(item.startsAt)}`);block.append(drop);
          body.addEventListener('pointerdown',e=>{if(locked()||e.button!==0||e.pointerType==='touch')return;drag={row,block,id:e.pointerId,x:e.clientX,y:e.clientY,grab:(e.clientY-block.getBoundingClientRect().top)/hourPx*60,active:false,spot:null};});
          body.addEventListener('keydown',e=>{if(e.key==='Delete'){e.preventDefault();remove(row.rowKey);}});
        }}
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
    const minute=Math.max(0,Math.min(1439,view.from+(clientY-col.getBoundingClientRect().top)/hourPx*60-grab)),spot=C.snap(minute,duration+15,C.busy(activeDay,room,durations,calendar.events,draft.rows,source?.rowKey));
    const old=source&&rooms.find(r=>String(r.id)===String(source.roomId)),next=rooms.find(r=>String(r.id)===String(room));
    const row=source?{...source,roomId:String(room),date:activeDay,time:spot.time,format:old&&next&&source.format===old.format?next.format:source.format}:{...defaults(room),rowKey:'candidate',sourceRow:null,date:activeDay,time:spot.time};
    return {...spot,row,duration,reason:spot.reason||problem(row)};
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
    if(!done.spot)return;if(done.spot.reason){message(`Không dời suất: ${done.spot.reason}`,'error');return;}changed(draft.rows.map(r=>r.rowKey===done.row.rowKey?done.spot.row:r));
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
  $('cancel-edit').onclick=closeEdit;$('editor').addEventListener('cancel',()=>{editing=null;editorDirty=false;state();if(draft.rows.length&&!draft.ready())check();});$('delete-edit').onclick=()=>{const key=editing;closeEdit();remove(key);};$('event-close').onclick=()=>$('event-dialog').close();
  async function switchScope(cinema,day){
    if(locked())return;if(!validDay(day)){$('day').value=activeDay;return;}
    if(cinema===activeCinema&&C.monday(day)===week){activeDay=day;$('day').value=day;render();return;}
    if(dirty()&&!await ask('Đổi rạp hoặc tuần sẽ bỏ các suất đang xếp chưa lưu. Ở lại để tạo lịch trước.','Bỏ thay đổi và chuyển','Có lịch chưa lưu')){$('day').value=activeDay;$('cinema').value=activeCinema;return;}
    activeCinema=cinema;activeDay=day;week=C.monday(day);$('day').value=day;$('cinema').value=cinema;draft.replace([]);origins.clear();notify('');page=0;initialScroll=true;clearDrag();await loadCalendar();
  }
  $('day').onchange=()=>switchScope(activeCinema,$('day').value);$('cinema').onchange=()=>switchScope($('cinema').value,activeDay);$('back').onclick=()=>switchScope(activeCinema,C.addDays(week,-7));$('forward').onclick=()=>switchScope(activeCinema,C.addDays(week,7));$('today').onclick=()=>switchScope(activeCinema,today());$('refresh').onclick=()=>loadCalendar();
  $('room').onchange=()=>pickRoom($('room').value);$('status').onchange=()=>changed(draft.rows,true);$('movie-filter').oninput=renderMovies;
  for(const id of ['from','to'])$(id).onchange=()=>{if(Number($('to').value)<=Number($('from').value))$(id==='from'?'to':'from').value=String(Number($(id).value)+(id==='from'?1:-1));initialScroll=true;renderMain();};
  $('errors-only').onchange=()=>{page=0;renderRows();};$('prev').onclick=()=>{page--;renderRows();};$('next').onclick=()=>{page++;renderRows();};
  $('view-week').onclick=()=>setMode('week');$('view-day').onclick=()=>setMode('day');
  $('add-open').onclick=()=>openAdd(mode==='week'?currentRoom():$('room').value,activeDay>=today()?activeDay:'');
  $('add-cancel').onclick=()=>$('add-dialog').close();$('add-start').addEventListener('input',()=>{addTouched=true;});
  $('add-all').onclick=()=>{$('add-days').querySelectorAll('input:not(:disabled)').forEach(input=>{input.checked=true;});suggestAdd();previewAdd();};
  $('add-form').addEventListener('input',e=>{if(['batch-room','batch-add-movie'].includes(e.target.id)||e.target.closest('#batch-add-days'))suggestAdd();previewAdd();});
  $('add-form').addEventListener('submit',e=>{
    e.preventDefault();if(locked()||!calendar)return;const result=computeAdd(uuid);if(result.error||!result.added.length){previewAdd();return;}
    const movie=$('add-movie').value,room=$('room').value;let rows;try{rows=C.merge(draft.rows,result.added);}catch(error){$('add-preview').className='sched-add-preview empty';$('add-preview').replaceChildren(node('p',error.message));return;}
    movieId=movie;viewRoom=String(room);$('add-dialog').close();changed(rows);
    notify(`Đã thêm ${result.added.length} suất ${title(movie)} tại ${roomName(room)}, ${new Set(result.added.map(r=>r.date)).size} ngày. Chưa lưu cho đến khi bạn bấm Tạo.${result.skipped.length?` Không thêm ${result.skipped.length} suất bị trùng lịch hoặc đã qua giờ.`:''}`,result.skipped.map(s=>`${dayName(s.row.date)} ${s.row.time}: ${s.reason}`));
  });
  $('copy').onclick=()=>{
    if(locked()||!calendar)return;const source=draft.rows.filter(r=>r.date===activeDay);if(!source.length){message('Ngày đang xem chưa có suất mới để chép.','error');return;}
    $('copy-info').textContent=`Chép ${source.length} suất mới của ${dayName(activeDay)} sang các ngày bạn chọn, giữ nguyên phim, phòng và giờ. Suất bị trùng lịch sẽ không được thêm và được liệt kê lại.`;$('copy-error').hidden=true;
    const box=$('copy-days');box.replaceChildren();
    for(let d=0;d<7;d++){const day=C.addDays(week,d);if(day===activeDay)continue;const label=node('label'),input=node('input');input.type='checkbox';input.value=day;input.disabled=day<today();label.append(input,node('span',`${dayName(day)}${input.disabled?' · đã qua':''}`));box.append(label);}
    $('copy-dialog').showModal();
  };
  $('copy-cancel').onclick=()=>$('copy-dialog').close();$('copy-all').onclick=()=>$('copy-days').querySelectorAll('input:not(:disabled)').forEach(input=>{input.checked=true;});
  $('copy-apply').onclick=()=>{
    const days=[...$('copy-days').querySelectorAll('input:checked')].map(input=>input.value);if(!days.length){$('copy-error').hidden=false;return;}
    const result=C.copyDay(draft.rows.filter(r=>r.date===activeDay),days,durations,calendar.events,draft.rows,now(),uuid);$('copy-dialog').close();
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
      const form=new FormData();form.set('cinemaId',activeCinema);form.set('targetStatus',$('status').value);form.set('weekStart',week);form.set('file',file);const {body}=await request('/import-preview',{method:'POST',body:form});const incoming=body.preview.rows.map(r=>r.input);
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
    $('review-summary').textContent=`${preview.total} suất · ${new Set(draft.rows.map(r=>r.movieId)).size} phim · ${new Set(draft.rows.map(r=>r.roomId)).size} phòng · ${$('status').value==='OPEN'?'Bán vé ngay, khách đặt được sau khi tạo':'Lưu nháp, khách chưa đặt được'}. Chưa ghi dữ liệu.`;
    $('review-error').hidden=preview.canCommit;$('review-error').textContent=`${preview.invalid} suất cần sửa. Chưa thể tạo lịch.`;
    const days=new Map();for(const item of preview.rows){const day=item.input.date;if(!days.has(day))days.set(day,new Map());const name=item.roomName||roomName(item.input.roomId);days.get(day).set(name,(days.get(day).get(name)||0)+1);}
    $('review-groups').replaceChildren();for(const day of [...days.keys()].sort()){const line=node('p');line.append(node('strong',dayName(day)),node('span',[...days.get(day)].map(([name,count])=>`${name}: ${count} suất`).join(' · ')));$('review-groups').append(line);}
    $('review-details').open=!preview.canCommit;$('review-rows').replaceChildren();
    for(const item of [...preview.rows].sort((a,b)=>String(a.startsAt).localeCompare(String(b.startsAt))||String(a.roomName).localeCompare(String(b.roomName)))){
      const tr=node('tr',undefined,item.errors.length?'review-invalid':''),name=node('td');name.dataset.label='Phim / phòng';name.append(node('strong',`${item.movieTitle||title(item.input.movieId)} · ${item.roomName||roomName(item.input.roomId)}`),node('small',sourceText(item.input)));tr.append(name);
      for(const [label,value] of [['Bắt đầu',when(item.startsAt)],['Kết thúc',when(item.endsAt)],['Hết dọn',when(item.cleanupUntil)]]){const td=node('td',value);td.dataset.label=label;tr.append(td);}
      const status=node('td',item.errors.map(e=>e.message).concat(item.warnings).join(' ')||'Hợp lệ');status.dataset.label='Kiểm tra';status.append(button('Xem / sửa',()=>{closeReview();edit(item.input);}));tr.append(status);$('review-rows').append(tr);
    }
    $('confirm').disabled=!preview.canCommit;$('confirm').textContent=`Xác nhận tạo ${preview.total} suất`;
  }
  $('commit').onclick=async()=>{
    if(locked()||!calendar||editorDirty||!draft.rows.length)return;clearTimeout(previewTimer);previewController?.abort();const revision=draft.revision,input=payload(draft.rows);busy=true;state();message('Đang kiểm tra lần cuối với lịch và điều kiện mở bán hiện tại…');
    try{const {body}=await request('/preview',{method:'POST',body:JSON.stringify(input)});if(!draft.accept(revision,body))return;reviewCommand=body.canCommit?{input,fingerprint:body.fingerprint}:null;reviewing=true;renderDays();renderMain();renderRows();renderReview(body);message(body.canCommit?`${body.total} suất hợp lệ. Xác nhận để tạo lịch.`:`${body.invalid}/${body.total} suất cần sửa. Chưa lưu suất nào.`,body.canCommit?'':'error');$('review').showModal();}
    catch(e){message(e.message,'error');}finally{busy=false;state();}
  };
  $('review-back').onclick=closeReview;$('review').addEventListener('cancel',()=>{reviewing=false;reviewCommand=null;state();});
  $('confirm').onclick=async()=>{if(busy||pending||!reviewing||!reviewCommand)return;const command={...reviewCommand,requestKey:uuid()};closeReview();pending={key:command.requestKey,command};remember(command.requestKey);await send(command);};
  $('result').onclick=async()=>{if(busy||!pending)return;busy=true;state();try{const {body,status}=await request(`/results/${encodeURIComponent(pending.key)}`);status===200?await success(body):message(body.message,'error');}catch(e){message(e.message,'error');}finally{busy=false;state();}};
  $('retry').onclick=async()=>{if(!busy&&pending?.command&&await ask('Gửi lại nguyên dữ liệu với cùng mã yêu cầu? Nếu đã tạo, hệ thống trả kết quả cũ.'))await send(pending.command);};
  addEventListener('keydown',e=>{if(e.key==='Escape')clearDrag();});addEventListener('beforeunload',e=>{if(dirty()){e.preventDefault();e.returnValue='';}});
  addEventListener('resize',()=>{clearTimeout(resizeTimer);resizeTimer=setTimeout(()=>{if(!drag)mode==='day'?renderBoard():fit();},150);});small.addEventListener('change',()=>{initialScroll=true;render();});stacked.addEventListener('change',()=>render());
  new ResizeObserver(()=>{clearTimeout(resizeTimer);resizeTimer=setTimeout(()=>{if(!drag&&calendar)mode==='day'?renderBoard():fit();},80);}).observe(root.querySelector('.sched-footer'));
  document.addEventListener('click',e=>{if($('excel').open&&!$('excel').contains(e.target))$('excel').open=false;});
  setInterval(()=>{if(!document.hidden&&!locked()&&!editing&&!drag&&!ghost&&calendar&&!$('add-dialog').open&&!$('week-wrap').contains(document.activeElement)){renderDays();renderMain();}},60000);
  for(let hour=0;hour<24;hour++){const from=node('option',C.clock(hour*60)),to=node('option',hour===23?'24:00':C.clock((hour+1)*60));from.value=String(hour);to.value=String(hour+1);$('from').append(from);$('to').append(to);}$('from').value='9';$('to').value='23';
  setBounds();renderRows();loadCalendar();const key=new URL(location.href).searchParams.get('requestKey');if(key){pending={key};state();$('result').click();}
})();
