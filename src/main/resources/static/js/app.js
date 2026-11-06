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
  if(response.redirected&&new URL(response.url).pathname==='/login')throw new Error('Phiên đã hết hạn. Vui lòng đăng nhập lại.');
  const data=await response.json().catch(()=>({message:'Không thể đọc phản hồi. Hãy tải lại trang.'}));
  if(!response.ok)throw new Error(data.message||'Không thể hoàn tất thao tác.');
  return data;
}
$('.menu-toggle')?.addEventListener('click',event=>{const open=$('#navigation').classList.toggle('open');event.currentTarget.setAttribute('aria-expanded',String(open));});
const carousel=$('#hero-carousel');
if(carousel&&$('#hero-prev')){const slides=[...carousel.querySelectorAll('.hero-content')];let index=0;function slide(delta){index=(index+delta+slides.length)%slides.length;slides.forEach((s,i)=>s.hidden=i!==index);$('#hero-position').textContent=String(index+1).padStart(2,'0')+' / '+String(slides.length).padStart(2,'0');}$('#hero-prev').addEventListener('click',()=>slide(-1));$('#hero-next').addEventListener('click',()=>slide(1));slide(0);}
document.querySelectorAll('[data-movie-tab]').forEach(button=>button.addEventListener('click',()=>{
  document.querySelectorAll('[data-movie-tab]').forEach(b=>b.classList.toggle('active',b===button));
  ['showing','coming'].forEach(id=>$('#'+id).hidden=id!==button.dataset.movieTab);
}));
document.querySelectorAll('[data-confirm]').forEach(button=>button.addEventListener('click',e=>{if(!confirm(button.dataset.confirm))e.preventDefault();}));
const query=new URLSearchParams(location.search);
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
  let seats=[],selected=new Set(),loading=false,holding=false;
  const status=$('#seat-status'),button=$('#hold-seats');
  function total(){const picked=seats.filter(s=>selected.has(s.id));$('#selected-seats').textContent=picked.length?picked.map(s=>s.label).join(', '):'Chưa chọn';$('#seat-total').textContent=money(picked.reduce((sum,s)=>sum+s.price,0));button.disabled=!picked.length||holding;}
  function choose(seat){
    const group=seat.type==='COUPLE'?seats.filter(s=>s.coupleGroup===seat.coupleGroup&&s.roomId===seat.roomId):[seat];
    const removing=selected.has(seat.id);
    if(!removing&&group.some(s=>s.state!=='AVAILABLE'))return toast('Cặp ghế không còn đủ chỗ trống.');
    if(!removing&&selected.size+group.filter(s=>!selected.has(s.id)).length>8)return toast('Mỗi đơn tối đa 8 ghế vật lý.');
    group.forEach(s=>removing?selected.delete(s.id):selected.add(s.id));render();
  }
  function render(){
    const oldFocus=document.activeElement?.dataset?.seatId;
    seatMap.replaceChildren();
    const rows=new Map();seats.forEach(s=>{if(!rows.has(s.rowLabel))rows.set(s.rowLabel,[]);rows.get(s.rowLabel).push(s);});
    [...rows].sort(([a],[b])=>a.localeCompare(b)).forEach(([label,row])=>{
      const container=document.createElement('div');container.className='seat-row';const heading=document.createElement('span');heading.textContent=label;container.append(heading);
      row.sort((a,b)=>a.position-b.position).forEach(seat=>{
        const b=document.createElement('button');b.type='button';b.className='seat '+seat.type.toLowerCase()+' '+seat.state.toLowerCase()+(selected.has(seat.id)?' selected':'');b.dataset.seatId=seat.id;b.textContent=seat.label;
        b.disabled=seat.state!=='AVAILABLE'||holding;b.setAttribute('aria-pressed',String(selected.has(seat.id)));
        const pairPrice=seat.type==='COUPLE'?seats.filter(s=>s.coupleGroup===seat.coupleGroup&&s.roomId===seat.roomId).reduce((sum,s)=>sum+s.price,0):null;
        b.title=`${seat.label} · ${seat.type} · ${pairPrice?'Cả cặp '+money(pairPrice):seat.priceText} · ${seat.state}`;b.setAttribute('aria-label',b.title);b.addEventListener('click',()=>choose(seat));container.append(b);
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

const scanner=$('#staff-scanner');
if(scanner){
  let controls=null,reader=null,busy=false,lastCode='',lastScan=0;
  const result=$('#scan-result'),start=$('#start-camera'),stop=$('#stop-camera'),video=$('#qr-video');
  function closeCamera(){controls?.stop();controls=null;video.srcObject?.getTracks().forEach(t=>t.stop());video.hidden=true;stop.hidden=true;start.disabled=false;}
  function renderTickets(tickets){
    result.replaceChildren();tickets.forEach(t=>{
      const card=document.createElement('article');card.className='scan-ticket';
      const title=document.createElement('h3');title.textContent=`${t.order.movieTitle} · Ghế ${t.seat}`;card.append(title);
      [t.code,`${t.order.cinemaName} · ${t.order.roomName}`,t.order.when,`Trạng thái: ${t.status}`].forEach(text=>{const p=document.createElement('p');p.textContent=text;card.append(p);});
      const confirmButton=document.createElement('button');confirmButton.type='button';confirmButton.className='button';confirmButton.textContent='XÁC NHẬN VÀO RẠP';confirmButton.disabled=t.status!=='VALID';
      confirmButton.addEventListener('click',async()=>{confirmButton.disabled=true;try{await api('/api/staff/check-in',{method:'POST',body:JSON.stringify({ticketId:t.id,showtimeId:Number(scanner.dataset.showtime)})});closeCamera();toast('Check-in thành công.');location.reload();}catch(error){toast(error.message);confirmButton.disabled=false;}});card.append(confirmButton);result.append(card);
    });
  }
  async function lookup(code){if(busy)return;busy=true;result.textContent='Đang kiểm tra vé…';try{const tickets=await api('/api/staff/lookup',{method:'POST',body:JSON.stringify({code})});renderTickets(tickets);}catch(error){result.textContent=error.message;}finally{busy=false;}}
  $('#manual-lookup').addEventListener('submit',e=>{e.preventDefault();lookup($('#ticket-code').value);});
  start.addEventListener('click',async()=>{
    if(!window.ZXingBrowser){toast('Chưa tải được bộ đọc QR. Bạn có thể tra cứu mã thủ công.');return;}
    start.disabled=true;video.hidden=false;stop.hidden=false;
    try{reader=new ZXingBrowser.BrowserQRCodeReader();controls=await reader.decodeFromVideoDevice(undefined,video,(decoded)=>{if(decoded){const code=decoded.getText();if(code!==lastCode||Date.now()-lastScan>5000){lastCode=code;lastScan=Date.now();lookup(code);}}});}
    catch(error){closeCamera();toast('Không mở được camera. Kiểm tra quyền camera, dùng HTTPS hoặc nhập mã thủ công.');}
  });
  stop.addEventListener('click',closeCamera);window.addEventListener('pagehide',closeCamera);
}
