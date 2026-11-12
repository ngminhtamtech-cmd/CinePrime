'use strict';
const $ = (selector, root = document) => root.querySelector(selector);
const money = value => new Intl.NumberFormat('vi-VN', {style:'currency',currency:'VND'}).format(value);
let toastTimer;
function toast(message) { const node=$('#toast'); node.textContent=message; node.classList.add('visible'); clearTimeout(toastTimer); toastTimer=setTimeout(()=>node.classList.remove('visible'),6000); }
async function api(url, options={}) {
  const headers={'Content-Type':'application/json',...options.headers};
  const token=$('meta[name="csrf-token"]')?.content, header=$('meta[name="csrf-header"]')?.content;
  if(token&&header)headers[header]=token;
  const response=await fetch(url,{...options,headers});
  if(response.redirected&&new URL(response.url).pathname==='/login'){document.dispatchEvent(new Event('session-expired'));throw new Error('Phiên đã hết hạn. Vui lòng đăng nhập lại.');}
  const data=await response.json().catch(()=>({message:'Không thể đọc phản hồi. Hãy tải lại trang.'}));
  if(response.status===401)document.dispatchEvent(new Event('session-expired'));
  if(!response.ok)throw Object.assign(new Error(data.message||'Không thể hoàn tất thao tác.'),{code:data.code,status:response.status});
  return data;
}
$('.menu-toggle')?.addEventListener('click',event=>{const open=$('#navigation').classList.toggle('open');event.currentTarget.setAttribute('aria-expanded',String(open));});
const carousel=$('#hero-carousel');
if(carousel){const slides=[...carousel.querySelectorAll('.hero-slide')];let index=0;function slide(delta){if(!slides.length)return;index=(index+delta+slides.length)%slides.length;slides.forEach((s,i)=>s.hidden=i!==index);if($('#hero-position'))$('#hero-position').textContent=String(index+1).padStart(2,'0')+' / '+String(slides.length).padStart(2,'0');}$('#hero-prev')?.addEventListener('click',()=>slide(-1));$('#hero-next')?.addEventListener('click',()=>slide(1));slide(0);}
document.querySelectorAll('[data-movie-tab]').forEach(button=>button.addEventListener('click',()=>{
  document.querySelectorAll('[data-movie-tab]').forEach(b=>b.classList.toggle('active',b===button));
  ['showing','coming'].forEach(id=>$('#'+id).hidden=id!==button.dataset.movieTab);
}));
(() => {
  const dialog=document.createElement('dialog');dialog.className='cine-dialog';dialog.setAttribute('aria-labelledby','confirm-title');
  const title=document.createElement('h2');title.id='confirm-title';title.textContent='Xác nhận thao tác';
  const text=document.createElement('p'),actions=document.createElement('div');actions.className='dialog-actions';
  const cancel=document.createElement('button'),confirm=document.createElement('button');cancel.type=confirm.type='button';cancel.className='button secondary';cancel.textContent='Hủy';confirm.className='button';confirm.textContent='Xác nhận';
  actions.append(cancel,confirm);dialog.append(title,text,actions);document.body.append(dialog);let source;
  cancel.addEventListener('click',()=>dialog.close());dialog.addEventListener('close',()=>{confirm.disabled=false;source?.focus();});
  confirm.addEventListener('click',()=>{const button=source;dialog.close();if(button?.form)button.form.requestSubmit(button);});
  document.querySelectorAll('[data-confirm]').forEach(button=>button.addEventListener('click',event=>{event.preventDefault();source=button;text.textContent=button.dataset.confirm;confirm.textContent=button.textContent.trim();dialog.showModal();cancel.focus();}));
})();
document.addEventListener('error',event=>{const image=event.target;if(!(image instanceof HTMLImageElement))return;if(image.classList.contains('hero-banner')){image.hidden=true;return;}if(image.src.endsWith('/images/no-poster.svg'))return;if(image.matches('.record-thumb,.detail-poster,.summary-poster,.media-preview,.movie-card img'))image.src='/images/no-poster.svg';},true);
function trailerSource(value){
  if(/^\/media\/[a-f0-9-]{36}\.mp4$/.test(value))return {src:value,type:'video',external:''};
  try{const url=new URL(value),host=url.hostname.toLowerCase();let id='';if(url.protocol!=='https:'||url.username||url.password||url.port)throw new Error();
    if(host==='youtu.be')id=url.pathname.substring(1);
    else if(['youtube.com','www.youtube.com','m.youtube.com','youtube-nocookie.com','www.youtube-nocookie.com'].includes(host)){if(url.pathname==='/watch')id=url.searchParams.get('v')||'';else if(/^\/(shorts|embed)\/[A-Za-z0-9_-]{11}$/.test(url.pathname))id=url.pathname.split('/').pop();}
    if(!/^[A-Za-z0-9_-]{11}$/.test(id))throw new Error();
    return {src:'https://www.youtube-nocookie.com/embed/'+id,type:'iframe',external:'https://www.youtube.com/watch?v='+id};
  }catch{throw new Error('Dán link YouTube hợp lệ hoặc tải trailer MP4 trước khi xem thử.');}
}
function openTrailer(value,label='Trailer phim'){
  const source=trailerSource(value);let dialog=document.getElementById('trailer-dialog');if(!dialog){dialog=document.createElement('dialog');dialog.id='trailer-dialog';dialog.className='cine-dialog trailer-dialog';document.body.append(dialog);}
  const returnFocus=document.activeElement,heading=document.createElement('h2'),close=document.createElement('button'),player=document.createElement(source.type),footer=document.createElement('p');heading.id='trailer-title';heading.textContent=label;dialog.setAttribute('aria-labelledby',heading.id);close.type='button';close.className='button secondary';close.textContent='Đóng trailer';player.className='trailer-player';
  if(source.type==='video'){player.controls=true;player.preload='metadata';}else{player.title=label;player.allow='fullscreen; encrypted-media';player.setAttribute('allowfullscreen','');}
  player.src=source.src;
  if(source.external){const link=document.createElement('a');link.href=source.external;link.target='_blank';link.rel='noopener noreferrer';link.textContent='Video không phát? Mở trên YouTube ↗';footer.append(link);}
  dialog.replaceChildren(heading,close,player,footer);close.addEventListener('click',()=>dialog.close());dialog.onclose=()=>{if(player instanceof HTMLVideoElement)player.pause();player.removeAttribute('src');player.remove();returnFocus?.focus();};dialog.showModal();close.focus();
}
document.querySelectorAll('[data-trailer]').forEach(button=>button.addEventListener('click',()=>{try{openTrailer(button.dataset.trailer,button.dataset.trailerTitle||'Trailer phim');}catch(error){toast(error.message);}}));
const query=new URLSearchParams(location.search);
document.querySelectorAll('[data-auto-movie-filter] select,[data-auto-movie-filter] input[type="date"]').forEach(input=>input.addEventListener('change',()=>input.form.requestSubmit()));
const quick=$('#quick-book');
if(quick){
  const film=$('[name="movieId"]',quick),cinema=$('[name="cinemaId"]',quick),day=$('[name="date"]',quick),show=$('[name="showtimeId"]',quick);
  let options=[];
  function option(value,text){const o=document.createElement('option');o.value=value;o.textContent=text;return o;}
  function update(){
    const oldCinema=cinema.value;const matches=options.filter(s=>String(s.movieId)===film.value&&s.canHold);
    const cinemas=new Map(matches.map(s=>[String(s.cinemaId),s.cinemaName]));cinema.replaceChildren(option('','2. Chọn rạp'));cinemas.forEach((label,id)=>cinema.append(option(id,label)));if(cinemas.has(oldCinema))cinema.value=oldCinema;cinema.disabled=!film.value;
    const dates=matches.filter(s=>String(s.cinemaId)===cinema.value).map(s=>s.date).sort();if(dates.length){day.min=dates[0];day.max=dates.at(-1);if(!dates.includes(day.value))day.value=dates[0];}day.disabled=!cinema.value;
    const slots=matches.filter(s=>String(s.cinemaId)===cinema.value&&s.date===day.value);show.replaceChildren(option('',slots.length?'4. Chọn suất':'Chưa có suất phù hợp'));slots.forEach(s=>show.append(option(s.id,s.when)));show.disabled=!slots.length;
  }
  api('/catalog/showtimes').then(data=>{options=data;show.hidden=false;show.required=true;update();[film,cinema,day].forEach(n=>n.addEventListener('change',update));quick.addEventListener('submit',e=>{if(show.value){e.preventDefault();location.assign('/booking/'+show.value);}});}).catch(()=>{show.hidden=true;});
}
if(query.has('error'))toast('Email hoặc mật khẩu không đúng, hoặc tài khoản đã khóa.');
if(query.has('expired'))toast('Phiên đã hết hạn hoặc thông tin bảo mật đã thay đổi. Vui lòng đăng nhập lại.');
if(query.has('registered'))toast('Đăng ký thành công. Bạn có thể đăng nhập.');
if(query.has('sent'))toast('Nếu email phù hợp, liên kết đặt lại mật khẩu sẽ được gửi.');
if(query.has('reset'))toast('Mật khẩu đã cập nhật. Vui lòng đăng nhập lại.');
if(query.has('saved'))toast('Đã lưu thay đổi.');

const seatMap=$('#seat-map');
if(seatMap){
  let seats=[],selected=new Set(),loading=false,holding=false,lastPicked=null;
  const status=$('#seat-status'),button=$('#hold-seats'),view3d=$('#seat-view-3d'),room3d=$('#room-view-3d');
  function total(){const picked=seats.filter(s=>selected.has(s.id));$('#selected-seats').textContent=picked.length?picked.map(s=>s.label).join(', '):'Chưa chọn';$('#seat-total').textContent=money(picked.reduce((sum,s)=>sum+s.price,0));button.disabled=!picked.length||holding;if(view3d)view3d.disabled=!picked.length;if(room3d)room3d.disabled=!seats.length;}
  function open3d(seatView,trigger){if(!window.Seat3D)return toast('Chưa tải được trình xem 3D.');const picked=seats.filter(s=>selected.has(s.id)),focus=seats.find(s=>s.id===lastPicked&&selected.has(s.id))||picked.at(-1);window.Seat3D.open({seats,title:seatMap.dataset.room,selectedLabels:picked.map(s=>s.label),focusLabel:seatView?focus?.label:null,mode:seatView?'seat':'overview',returnTo:trigger});}
  view3d?.addEventListener('click',()=>open3d(true,view3d));room3d?.addEventListener('click',()=>open3d(false,room3d));
  function choose(seat){
    const group=seat.type==='COUPLE'?seats.filter(s=>s.coupleGroup===seat.coupleGroup&&s.roomId===seat.roomId):[seat];
    const removing=selected.has(seat.id);
    if(!removing&&group.some(s=>s.state!=='AVAILABLE'))return toast('Cặp ghế không còn đủ chỗ trống.');
    if(!removing&&selected.size+group.filter(s=>!selected.has(s.id)).length>8)return toast('Mỗi đơn tối đa 8 ghế vật lý.');
    group.forEach(s=>removing?selected.delete(s.id):selected.add(s.id));lastPicked=removing?null:seat.id;render();
  }
  function render(){
    const oldFocus=document.activeElement?.dataset?.seatId;
    seatMap.replaceChildren();
    const rows=new Map();seats.forEach(s=>{if(!rows.has(s.rowLabel))rows.set(s.rowLabel,[]);rows.get(s.rowLabel).push(s);});
    const firstPosition=Math.min(...seats.map(s=>s.position));
    [...rows].sort(([a],[b])=>a.localeCompare(b)).forEach(([label,row])=>{
      const container=document.createElement('div');container.className='seat-row';const heading=document.createElement('span');heading.textContent=label;container.append(heading);
      let next=firstPosition;
      row.sort((a,b)=>a.position-b.position).forEach(seat=>{
        // Missing positions are aisles set by the admin; keep columns aligned across rows.
        for(;next<seat.position;next++){const gap=document.createElement('i');gap.className='seat seat-gap';gap.setAttribute('aria-hidden','true');container.append(gap);}next=seat.position+1;
        const b=document.createElement('button');b.type='button';b.className='seat '+seat.type.toLowerCase()+' '+seat.state.toLowerCase()+(selected.has(seat.id)?' selected':'');b.dataset.seatId=seat.id;b.textContent=seat.label;
        b.disabled=seat.state!=='AVAILABLE'||holding;b.setAttribute('aria-pressed',String(selected.has(seat.id)));
        const pairPrice=seat.type==='COUPLE'?seats.filter(s=>s.coupleGroup===seat.coupleGroup&&s.roomId===seat.roomId).reduce((sum,s)=>sum+s.price,0):null;
        const typeLabel={STANDARD:'Standard · Ghế thường',VIP:'VIP',COUPLE:'Couple · Ghế đôi'}[seat.type]||seat.type,stateLabel={AVAILABLE:'Còn trống',HELD:'Đang giữ',SOLD:'Đã bán',MAINTENANCE:'Bảo trì'}[seat.state]||seat.state;
        b.title=`${seat.label} · ${typeLabel} · ${pairPrice?'Cả cặp '+money(pairPrice):seat.priceText} · ${stateLabel}`;if(seat.type==='COUPLE'){const pair=seats.filter(s=>s.coupleGroup===seat.coupleGroup&&s.roomId===seat.roomId).sort((a,b)=>a.position-b.position);b.classList.add(pair[0]?.id===seat.id?'pair-first':'pair-last');}b.setAttribute('aria-label',b.title);b.addEventListener('click',()=>choose(seat));container.append(b);
      });seatMap.append(container);
    });
    if(!seats.length)seatMap.textContent='Suất chưa có sơ đồ ghế mở bán.';
    total();if(oldFocus)$(`[data-seat-id="${oldFocus}"]`,seatMap)?.focus({preventScroll:true});
  }
  async function refresh(){if(loading||holding||document.hidden)return;loading=true;try{
    const data=await api(`/api/showtimes/${seatMap.dataset.showtime}/seats`);seats=data.seats;
    let changed=false;for(const id of selected)if(!seats.some(s=>s.id===id&&s.state==='AVAILABLE')){selected.delete(id);changed=true;}
    if(changed)toast('Một số ghế vừa được giữ hoặc bán. Vui lòng kiểm tra lại.');status.textContent='Trạng thái ghế cập nhật mỗi 5 giây.';render();
  }catch(error){status.textContent=error.message+' Chúng tôi sẽ thử tải lại.';button.disabled=true;}finally{loading=false;}}
  button.addEventListener('click',async()=>{if(holding)return;holding=true;button.disabled=true;button.textContent='ĐANG GIỮ GHẾ…';try{const result=await api('/api/holds',{method:'POST',body:JSON.stringify({showtimeId:Number(seatMap.dataset.showtime),seats:[...selected]})});location.assign(result.url);}catch(error){toast(error.message);holding=false;button.textContent='TIẾP TỤC →';await refresh();}});
  refresh();setInterval(refresh,5000);
}

const orderStatus=$('#order-status'),countdown=$('#countdown');
if(orderStatus&&orderStatus.dataset.status==='PENDING_PAYMENT'){
  let offset=0,deadline=countdown?Date.parse(countdown.dataset.deadline):0;
  function tick(){if(!countdown)return;const remaining=Math.max(0,Math.ceil((deadline-(Date.now()+offset))/1000));countdown.textContent=`${String(Math.floor(remaining/60)).padStart(2,'0')}:${String(remaining%60).padStart(2,'0')}`;}
  async function refresh(){if(document.hidden)return;try{const result=await api('/api/orders/'+orderStatus.dataset.orderId);offset=Date.parse(result.serverTime)-Date.now();deadline=Date.parse(result.expiresAt);tick();if(result.status!==orderStatus.dataset.status)location.reload();}catch(error){toast(error.message);}}
  refresh();setInterval(refresh,5000);setInterval(tick,1000);
}
