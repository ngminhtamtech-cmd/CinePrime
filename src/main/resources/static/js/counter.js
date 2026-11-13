'use strict';
// Box-office sale flow. The server owns prices, totals, hold expiry and every rule; this script only drives the screen.
(() => {
  const root=document.querySelector('#counter');if(!root)return;
  const one=(s,r=document)=>r.querySelector(s),all=(s,r=document)=>[...r.querySelectorAll(s)];
  const map=one('#counter-seat-map'),status=one('#counter-seat-status'),showLabel=one('#counter-show-label');
  const holdButton=one('#cart-hold'),pay=one('#cart-pay'),release=one('#cart-release'),cash=one('#cash-received');
  let showId=null,seats=[],selected=new Set(),order=null,busy=false,offset=0,deadline=0,customer=null,requestKey=null,loading=false,comboTimer=null;
  const views={select:one('#cart-select'),held:one('#cart-held'),done:one('#cart-done')};
  function view(name){for(const [key,node] of Object.entries(views))node.hidden=key!==name;}
  function key(){return crypto.randomUUID?crypto.randomUUID():Date.now().toString(36)+'-'+Math.random().toString(36).slice(2,12);}
  function digits(value){return /^\d{1,12}$/.test(value)?Number(value):null;}
  // Operations screens write money as "170.000 đ" (UI-Template.md §3.4).
  const vnd=n=>Number(n).toLocaleString('vi-VN')+' đ',typeName={STANDARD:'Thường',VIP:'VIP',COUPLE:'Đôi'};

  // 1 · Showtimes
  all('.show-card').forEach(card=>card.addEventListener('click',()=>{if(busy)return;if(order&&order.status==='PENDING_PAYMENT'){toast('Đang giữ ghế cho đơn khác. Thu tiền hoặc hủy giữ ghế trước khi đổi suất.');return;}choose(Number(card.dataset.showtime));}));
  function choose(id){showId=id;selected.clear();all('.show-card').forEach(c=>c.setAttribute('aria-pressed',String(Number(c.dataset.showtime)===id)));loadSeats();}
  one('#counter-date')?.addEventListener('change',e=>{if(order&&order.status==='PENDING_PAYMENT'){toast('Hủy giữ ghế trước khi đổi ngày.');e.target.value=root.dataset.day;return;}e.target.form.submit();});

  // 2 · Seats
  async function loadSeats(){if(!showId||loading||document.hidden)return;loading=true;try{const data=await api(`/api/staff/counter/showtimes/${showId}/seats`);seats=data.seats;showLabel.textContent=`${data.show.movieTitle} · ${data.show.roomName} · ${data.show.when}`;if(!data.show.sellable)status.textContent='Suất đã ngừng bán tại quầy.';for(const id of [...selected]){const s=seats.find(x=>x.id===id);if(!s||s.state!=='AVAILABLE')selected.delete(id);}render();}catch(error){status.textContent=error.message;}finally{loading=false;}}
  function pair(seat){return seat.type==='COUPLE'?seats.filter(s=>s.coupleGroup===seat.coupleGroup&&s.roomId===seat.roomId):[seat];}
  function pick(seat){
    if(order&&order.status==='PENDING_PAYMENT')return toast('Ghế đã được giữ. Hủy giữ ghế nếu cần chọn lại.');
    const group=pair(seat),removing=selected.has(seat.id);
    if(!removing&&group.some(s=>s.state!=='AVAILABLE'))return toast('Cặp ghế không còn đủ chỗ trống.');
    if(!removing&&selected.size+group.filter(s=>!selected.has(s.id)).length>8)return toast('Mỗi đơn tối đa 8 ghế.');
    group.forEach(s=>removing?selected.delete(s.id):selected.add(s.id));render();
  }
  function render(){
    const focus=document.activeElement?.dataset?.seatId;map.replaceChildren();
    if(!seats.length){const p=document.createElement('p');p.className='empty';p.textContent='Suất chưa có sơ đồ ghế mở bán.';map.append(p);}
    const rows=new Map();seats.forEach(s=>{if(!rows.has(s.rowLabel))rows.set(s.rowLabel,[]);rows.get(s.rowLabel).push(s);});
    const first=Math.min(...seats.map(s=>s.position));
    [...rows].sort(([a],[b])=>a.localeCompare(b)).forEach(([label,row])=>{
      const line=document.createElement('div');line.className='seat-row';const head=document.createElement('span');head.className='row-label';head.textContent=label;line.append(head);let next=first;
      row.sort((a,b)=>a.position-b.position).forEach(seat=>{
        for(;next<seat.position;next++){const gap=document.createElement('i');gap.className='seat seat-gap';gap.setAttribute('aria-hidden','true');line.append(gap);}next=seat.position+1;
        const held=order&&order.status==='PENDING_PAYMENT'&&seat.mine,b=document.createElement('button');b.type='button';b.dataset.seatId=seat.id;b.textContent=seat.label.startsWith(seat.rowLabel)?seat.label.slice(seat.rowLabel.length):seat.label;
        b.className='seat '+seat.type.toLowerCase()+' '+(held?'mine':seat.state.toLowerCase())+(selected.has(seat.id)?' selected':'');
        if(seat.type==='COUPLE'){const p=pair(seat).sort((x,y)=>x.position-y.position);b.classList.add(p[0]?.id===seat.id?'pair-first':'pair-last');}
        b.disabled=busy||held||seat.state!=='AVAILABLE';b.setAttribute('aria-pressed',String(selected.has(seat.id)));
        const state={AVAILABLE:'Còn trống',HELD:'Đang giữ',SOLD:'Đã bán',MAINTENANCE:'Bảo trì'}[seat.state]||seat.state;b.title=`${seat.label} · ${seat.priceText} · ${held?'Quầy đang giữ':state}`;b.setAttribute('aria-label',b.title);
        b.addEventListener('click',()=>pick(seat));line.append(b);
      });const tail=head.cloneNode(true);tail.setAttribute('aria-hidden','true');line.append(tail);map.append(line);
    });
    const picked=seats.filter(s=>selected.has(s.id)).sort((a,b)=>a.label.localeCompare(b.label,"vi",{numeric:true}));const lines=one('#cart-picked-lines');lines.replaceChildren(...picked.map(s=>{const row=document.createElement('div');row.className='line';const a=document.createElement('span'),b=document.createElement('strong');a.textContent=`Ghế ${s.label} · ${typeName[s.type]||s.type}`;b.textContent=vnd(s.price);row.append(a,b);return row;}));
    one('#cart-picked-empty').hidden=picked.length>0;one('#cart-picked-total').textContent=picked.length?vnd(picked.reduce((n,s)=>n+s.price,0)):'—';one('#cart-picked-total').classList.toggle('zero',!picked.length);
    holdButton.hidden=!picked.length;one('#cart-clear').hidden=!picked.length;if(!busy)holdButton.textContent=`Giữ ${picked.length} ghế · 5 phút`;holdButton.disabled=busy||!picked.length;if(focus)one(`[data-seat-id="${focus}"]`,map)?.focus({preventScroll:true});
  }
  holdButton.addEventListener('click',async()=>{if(busy||!selected.size)return;busy=true;holdButton.textContent='Đang giữ ghế…';render();try{const data=await api('/api/staff/counter/holds',{method:'POST',body:JSON.stringify({showtimeId:showId,seats:[...selected]})});selected.clear();showOrder(data);toast('Đã giữ ghế 5 phút.');}catch(error){toast(error.message);}finally{busy=false;await loadSeats();}});
  one('#cart-clear').addEventListener('click',()=>{if(busy)return;selected.clear();render();});

  // 3 · Held order: combos, customer, cash
  function showOrder(data){
    offset=Date.parse(data.serverTime)-Date.now();order=data.order;
    if(order.status==='EXPIRED'||order.status==='CANCELLED'){reset('Hết thời gian giữ ghế. Hãy chọn ghế lại.');return;}
    if(order.status==='PAID'){done(order);return;}
    if(!requestKey)requestKey=key();deadline=Date.parse(order.expiresAt);view('held');
    if(order.showtimeId&&order.showtimeId!==showId){showId=order.showtimeId;all('.show-card').forEach(c=>c.setAttribute('aria-pressed',String(Number(c.dataset.showtime)===showId)));loadSeats();}
    one('#cart-order-code').textContent=order.code;
    const lines=one('#cart-seat-lines');lines.replaceChildren();for(const l of order.seatLines){const row=document.createElement('div');row.className='line';const a=document.createElement('span'),b=document.createElement('strong');a.textContent=`Ghế ${l.label} · ${typeName[l.seatType]||l.type}`;b.textContent=l.priceText;row.append(a,b);lines.append(row);}
    const quantities=new Map(order.productLines.map(l=>[l.productId,l.quantity]));all('.product-row').forEach(row=>{one('[data-quantity]',row).textContent=String(quantities.get(Number(row.dataset.product))||0);});
    one('#cart-total').textContent=order.totalText;one('#cart-total').classList.remove('zero');updateChange();tick();
  }
  function updateChange(){const received=digits(cash.value.trim()),total=order?.total??0;const change=one('#cash-change');const enough=received!==null&&received>=total;pay.hidden=!enough;one('#pay-hint').hidden=enough;if(received===null){change.textContent='—';change.classList.remove('short');pay.disabled=true;return;}change.textContent=received>=total?vnd(received-total):'Thiếu '+vnd(total-received);change.classList.toggle('short',received<total);pay.disabled=busy||!enough||comboTimer!==null;}
  cash.addEventListener('input',updateChange);
  all('[data-cash]').forEach(button=>button.addEventListener('click',()=>{const total=order?.total??0;if(button.dataset.cash==='exact')cash.value=String(total);else if(button.dataset.cash==='clear')cash.value='';else cash.value=String((digits(cash.value.trim())||0)+Number(button.dataset.cash));updateChange();}));
  all('.product-row').forEach(row=>all('[data-step]',row).forEach(button=>button.addEventListener('click',()=>{if(busy||!order)return;const output=one('[data-quantity]',row);const value=Math.min(20,Math.max(0,Number(output.textContent)+Number(button.dataset.step)));output.textContent=String(value);clearTimeout(comboTimer);comboTimer=setTimeout(saveCombos,350);pay.disabled=true;})));
  async function saveCombos(){comboTimer=null;if(!order)return;const quantities={};all('.product-row').forEach(row=>quantities[row.dataset.product]=Number(one('[data-quantity]',row).textContent));busy=true;try{showOrder(await api(`/api/staff/counter/orders/${order.id}/combos`,{method:'POST',body:JSON.stringify({quantities})}));}catch(error){toast(error.message);await reread();}finally{busy=false;updateChange();}}
  async function reread(){if(!order)return;try{showOrder(await api(`/api/staff/counter/orders/${order.id}`));}catch(error){toast(error.message);}}
  one('#customer-search').addEventListener('submit',async e=>{e.preventDefault();const q=one('#customer-q').value.trim();const box=one('#customer-results');box.replaceChildren();try{const data=await api('/api/staff/counter/customers?q='+encodeURIComponent(q));if(!data.customers.length){box.textContent='Không tìm thấy tài khoản. Bán cho khách vãng lai.';return;}for(const c of data.customers){const b=document.createElement('button');b.type='button';b.className='btn full';b.textContent=`Gắn ${c.name} · ${c.contact}`;b.addEventListener('click',()=>setCustomer(c));box.append(b);}}catch(error){box.textContent=error.message;}});
  function setCustomer(c){customer=c;one('#customer-results').replaceChildren();const p=one('#customer-selected');p.replaceChildren();p.textContent=c?`Gắn tài khoản: ${c.name} · ${c.contact} · tích điểm khi thu tiền `:'Khách vãng lai · không tích điểm';if(c){const x=document.createElement('button');x.type='button';x.className='link';x.textContent='Bỏ gắn';x.addEventListener('click',()=>setCustomer(null));p.append(x);}}
  pay.addEventListener('click',async()=>{if(busy||!order)return;const received=digits(cash.value.trim());if(received===null||received<order.total)return updateChange();busy=true;pay.disabled=true;pay.textContent='Đang ghi nhận…';
    try{const data=await api(`/api/staff/counter/orders/${order.id}/pay`,{method:'POST',body:JSON.stringify({received,customerId:customer?.id??null,requestKey})});order=data.order;done(order);toast('Đã thu tiền. In vé cho khách.');}
    catch(error){// A lost response may still have been recorded: read the order back before allowing another attempt with the same key.
      toast(error.message);await reread();}
    finally{busy=false;pay.textContent='Thu tiền và phát vé';updateChange();}});
  release.addEventListener('click',async()=>{if(busy||!order)return;busy=true;try{await api(`/api/staff/counter/orders/${order.id}/release`,{method:'POST'});reset('Đã hủy giữ ghế.');}catch(error){toast(error.message);await reread();}finally{busy=false;}});
  function reset(text){order=null;requestKey=null;customer=null;setCustomer(null);cash.value='';view('select');if(text)toast(text);loadSeats();}
  function tick(){if(!order||order.status!=='PENDING_PAYMENT')return;const left=Math.max(0,Math.ceil((deadline-(Date.now()+offset))/1000));one('#cart-countdown').textContent=`${String(Math.floor(left/60)).padStart(2,'0')}:${String(left%60).padStart(2,'0')}`;one('#cart-countdown').classList.toggle('urgent',left<=60);if(left===0)reread();}
  setInterval(tick,1000);

  // 4 · Paid
  function done(o){order=o;view('done');requestKey=null;one('#done-summary').textContent=`${o.code} · ${o.movieTitle} · ${o.when} · Ghế ${o.seats} · ${o.totalText} · ${o.customer}`;one('#done-received').textContent=o.received;one('#done-change').textContent=o.change;one('#done-print').href=`/staff/counter/orders/${o.id}/print`;one('#done-checkin-result').replaceChildren();one('#done-checkin').disabled=false;loadSeats();}
  one('#done-checkin').addEventListener('click',async e=>{if(!order||busy)return;busy=true;e.target.disabled=true;const box=one('#done-checkin-result');try{const data=await api(`/api/staff/counter/orders/${order.id}/check-in`,{method:'POST'});box.replaceChildren(...data.results.map(r=>{const p=document.createElement('p');p.className=r.ok?'ok':'warn';p.textContent=`Ghế ${r.seat}: ${r.message}`;return p;}));}catch(error){box.textContent=error.message;e.target.disabled=false;}finally{busy=false;}});
  one('#done-next').addEventListener('click',()=>location.reload());

  // Shift orders: cancel with reason
  const dialog=one('#cancel-dialog');let cancelId=null;
  all('.cancel-order').forEach(button=>button.addEventListener('click',()=>{cancelId=button.dataset.order;one('#cancel-text').textContent=`Hủy đơn ${button.dataset.code} và hoàn tiền mặt cho khách?`;one('#cancel-reason').value='';dialog.showModal();one('#cancel-reason').focus();}));
  one('[data-close]',dialog).addEventListener('click',()=>dialog.close());
  one('#cancel-form').addEventListener('submit',async e=>{e.preventDefault();const reason=one('#cancel-reason').value.trim();if(!reason){one('#cancel-reason').focus();return;}const submit=one('button[type="submit"]',dialog);submit.disabled=true;try{await api(`/api/staff/counter/orders/${cancelId}/cancel`,{method:'POST',body:JSON.stringify({reason})});dialog.close();location.reload();}catch(error){toast(error.message);}finally{submit.disabled=false;}});

  document.addEventListener('visibilitychange',()=>{if(!document.hidden){loadSeats();if(order?.status==='PENDING_PAYMENT')reread();}});
  setInterval(()=>{if(!busy)loadSeats();},5000);
  if(root.dataset.activeOrder)api(`/api/staff/counter/orders/${root.dataset.activeOrder}`).then(showOrder).catch(error=>toast(error.message));
})();
