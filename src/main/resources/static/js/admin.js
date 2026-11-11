'use strict';
(() => {
  if (!document.body.classList.contains('admin-compact')) return;
  const one=(s,r=document)=>r.querySelector(s), all=(s,r=document)=>[...r.querySelectorAll(s)];
  const sidebar=one('#ops-sidebar'), groups=all('[data-group-id]',sidebar);
  const preferenceKey=`cineprime.admin.nav.v2.${document.body.dataset.adminId}`;
  let preferences={groups:{},scroll:0};
  try {const saved=JSON.parse(localStorage.getItem(preferenceKey));if(saved && saved.groups && typeof saved.groups==='object')preferences=saved;} catch {}
  for(const group of groups)group.open=group.dataset.active==='true'||preferences.groups[group.dataset.groupId]===true;
  // Sidebar toggle (desktop): hides the whole sidebar with a slide, like a chat app panel. Mobile keeps the drawer.
  const root=document.documentElement,wide=matchMedia('(min-width:851px)'),collapseKey='cineprime.admin.navCollapsed',headerToggle=one('#open-sidebar');
  const collapsed=()=>root.classList.contains('nav-collapsed');
  function applyCollapsed(on,persist){
    root.classList.toggle('nav-collapsed',on);sidebar.inert=on&&wide.matches&&!document.body.matches('.seat-workspace,.form-focus');
    if(wide.matches){headerToggle?.setAttribute('aria-expanded',String(!on));headerToggle?.setAttribute('aria-label',on?'Hiện thanh điều hướng':'Ẩn thanh điều hướng');}
    if(persist)try{localStorage.setItem(collapseKey,on?'1':'0');}catch{}
  }
  applyCollapsed(collapsed(),false);
  requestAnimationFrame(()=>requestAnimationFrame(()=>root.classList.remove('nav-instant')));
  const toggle=()=>{if(wide.matches)applyCollapsed(!collapsed(),true);};
  document.addEventListener('toggle-nav-collapse',toggle);
  document.addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='b'&&!e.target.closest?.('input,textarea,select,[contenteditable]')){e.preventDefault();toggle();}});
  wide.addEventListener('change',e=>{if(!e.matches){root.classList.remove('nav-collapsed');sidebar.inert=false;}else{try{applyCollapsed(localStorage.getItem(collapseKey)==='1',false);}catch{}}});
  // Back / forward buttons next to the toggle; disabled when the browser reports nothing to go to.
  const back=one('#nav-back'),forward=one('#nav-forward'),nav=window.navigation;
  const syncHistory=()=>{if(back)back.disabled=nav?!nav.canGoBack:history.length<2;if(forward)forward.disabled=nav?!nav.canGoForward:false;};
  back?.addEventListener('click',()=>history.back());forward?.addEventListener('click',()=>history.forward());syncHistory();addEventListener('pageshow',syncHistory);nav?.addEventListener?.('currententrychange',syncHistory);
  // Row "⋯" menus: fixed positioning so the table's scroll container never clips them.
  const rowMenus=all('.row-menu');
  const closeMenus=except=>rowMenus.forEach(m=>{if(m!==except&&m.open)m.open=false;});
  rowMenus.forEach(menu=>menu.addEventListener('toggle',()=>{
    if(!menu.open)return;closeMenus(menu);const list=one('.row-menu-list',menu),box=one('summary',menu).getBoundingClientRect();
    list.style.position='fixed';list.style.right='auto';const height=list.offsetHeight,width=list.offsetWidth;
    list.style.top=(box.bottom+6+height<innerHeight?box.bottom+6:Math.max(8,box.top-6-height))+'px';list.style.left=Math.max(8,Math.min(innerWidth-width-8,box.right-width))+'px';
  }));
  document.addEventListener('click',e=>{if(!e.target.closest('.row-menu'))closeMenus();});
  document.addEventListener('keydown',e=>{if(e.key==='Escape')closeMenus();});
  addEventListener('scroll',()=>closeMenus(),{passive:true,capture:true});addEventListener('resize',()=>closeMenus());
  const save=()=>{try {localStorage.setItem(preferenceKey,JSON.stringify({groups:Object.fromEntries(groups.map(g=>[g.dataset.groupId,g.open])),scroll:sidebar.scrollTop}));}catch {}};
  requestAnimationFrame(()=>{sidebar.scrollTop=Math.max(0,Number(preferences.scroll)||0);groups.forEach(g=>g.addEventListener('toggle',save));sidebar.addEventListener('scroll',save,{passive:true});});
  window.addEventListener('pagehide',save);
  all('a',sidebar).forEach(link=>link.addEventListener('click',()=>{save();if(sidebar.classList.contains('open'))one('#close-sidebar').click();}));
  all('a[data-page-param]').forEach(link=>{const url=new URL(location.href);url.searchParams.set(link.dataset.pageParam,link.dataset.page);link.href=url.pathname+url.search;});

  all('[data-scope-filters]').forEach(form=>{
    one('[name="cinemaId"]',form)?.addEventListener('change',()=>{for(const name of ['roomId','movieId']){const select=one(`[name="${name}"]`,form);if(select)select.value='';}form.requestSubmit();});
    one('[name="date"]',form)?.addEventListener('change',()=>{const movie=one('[name="movieId"]',form);if(movie)movie.value='';form.requestSubmit();});
  });
  all('.ops-edit-form .form-group').forEach((group,index)=>{
    if(group.closest('[data-admin-form]'))return;
    if(index===0||group.dataset.primary==='true'||one('[required]',group)||one('.form-error'))return;
    const legend=one('legend',group);if(!legend)return;
    const button=document.createElement('button');button.type='button';button.className='form-group-toggle';button.textContent=legend.textContent+' ▾';button.setAttribute('aria-expanded','true');
    legend.replaceChildren(button);button.addEventListener('click',()=>{const collapsed=group.classList.toggle('collapsed');button.setAttribute('aria-expanded',String(!collapsed));});
  });
  all('.admin-compact form[method="post"]').forEach(form=>form.addEventListener('submit',event=>{
    if(form.id==="layout-save"&&document.body.classList.contains("seat-workspace"))return;if(event.defaultPrevented)return;if(form.dataset.uploadBlocked==='true'){event.preventDefault();return;}if(form.dataset.submitting){event.preventDefault();return;}const button=event.submitter;if(!button)return;form.dataset.submitting='true';
    // Confirm handlers in app.js run first. Retain submit values and native validation.
    requestAnimationFrame(()=>{button.disabled=true;button.setAttribute('aria-busy','true');button.dataset.originalLabel=button.textContent;button.textContent='Đang xử lý…';});
  }));
  window.addEventListener('pageshow',()=>all('form[data-submitting]').forEach(form=>{delete form.dataset.submitting;all('button[data-original-label]',form).forEach(button=>{button.disabled=false;button.textContent=button.dataset.originalLabel;button.removeAttribute('aria-busy');delete button.dataset.originalLabel;});}));

  function controls(widget){return {prev:one('[data-widget-prev]',widget),next:one('[data-widget-next]',widget),label:one('[data-widget-page]',widget),status:one('[data-widget-status]',widget),rows:one('[data-widget-rows]',widget)};}
  function pager(c,data){c.prev.disabled=data.page===0;c.next.disabled=!data.hasMore;const pages=Math.max(1,Math.ceil(data.total/5));c.label.textContent=`${data.page+1} / ${pages}`;c.label.title=`${data.total} kết quả`;const nav=c.prev.closest('.ops-pagination');if(nav)nav.dataset.single=String(data.page===0&&!data.hasMore);}
  function td(row,text,label){const cell=document.createElement('td');cell.textContent=text;if(label)cell.dataset.label=label;row.append(cell);return cell;}
  all('[data-report-widget]').forEach(widget=>{
    const c=controls(widget);let page=0,busy=false,expired=false,hasMore=!c.next.disabled;
    async function load(target){
      if(busy||expired)return;busy=true;c.prev.disabled=c.next.disabled=true;widget.setAttribute('aria-busy','true');c.status.textContent='Đang tải trang…';
      try{
        const response=await api(`/api/admin/reports/${encodeURIComponent(widget.dataset.reportId)}/${widget.dataset.reportWidget}?page=${target}`);
        if(response.snapshotId!==widget.dataset.reportId)throw new Error('Bản chụp không khớp. Vui lòng làm mới báo cáo.');
        const data=response.data,fragment=document.createDocumentFragment();
        for(const item of data.rows){
          if(widget.dataset.reportWidget==='topMovies'){
            const row=document.createElement('div');row.className='ranking-row';const title=document.createElement('strong'),amount=document.createElement('span');title.textContent=item.title;amount.textContent=item.amount;row.append(title,amount);fragment.append(row);
          }else{
            const row=document.createElement('tr');
            if(widget.dataset.reportWidget==='chart'){td(row,item.label);td(row,item.amount);}
            else{const cell=td(row,'','Phim / Rạp / Phòng'),title=document.createElement('strong'),detail=document.createElement('small');title.textContent=item.movieTitle;detail.textContent=`${item.cinemaName} · ${item.roomName}`;cell.append(title,detail);td(row,item.when,'Suất');td(row,item.occupancy,'Vé / Ghế');}
            fragment.append(row);
          }
        }
        c.rows.replaceChildren(fragment);page=data.page;hasMore=data.hasMore;pager(c,data);c.status.textContent='Cùng bản chụp và mốc chốt đang xem.';
      }catch(error){c.status.textContent=error.message+' Dữ liệu trước vẫn được giữ.';if(error.status===400||/hết hạn|Bản chụp/.test(error.message))expired=true;if(!expired){c.prev.disabled=page===0;c.next.disabled=false;}}
      finally{busy=false;if(!expired){c.prev.disabled=page===0;c.next.disabled=!hasMore;}widget.removeAttribute('aria-busy');}
    }
    c.prev.addEventListener('click',()=>load(page-1));c.next.addEventListener('click',()=>load(page+1));
  });

  let sessionExpired=false;document.addEventListener('session-expired',()=>sessionExpired=true);
  const operationWidgets=all('[data-op-widget]');
  operationWidgets.forEach(widget=>{
    const c=controls(widget),refresh=one('[data-widget-refresh]',widget),section=widget.dataset.opWidget;
    let page=0,busy=false,next=widget.dataset.next,cursors=[''],revision=Number(widget.dataset.revision),hasMore=!c.next.disabled;
    const url=cursor=>`/api/admin/operations/${section}?cinemaId=${encodeURIComponent(widget.dataset.cinema)}&cursor=${encodeURIComponent(cursor)}`;
    function renderRows(rows){
      const existing=new Map(all('[data-record-id]',c.rows).map(node=>[node.dataset.recordId,node]));
      all('.muted',c.rows).forEach(node=>node.remove());
      rows.forEach((item,index)=>{
        let node=existing.get(String(item.recordId));if(!node){node=document.createElement('a');node.className='operation-item';node.dataset.recordId=item.recordId;node.append(document.createElement('strong'),document.createElement('small'));}
        existing.delete(String(item.recordId));node.href=section==='upcoming'?'/admin/manage/showtimes/edit?id='+item.id:'/admin/orders/'+item.id;
        one('strong',node).textContent=item.label;one('small',node).textContent=item.detail;
        const current=c.rows.children[index];if(current!==node)c.rows.insertBefore(node,current||null);
      });
      existing.forEach(node=>{if(node.contains(document.activeElement))refresh.focus();node.remove();});
      if(!rows.length){const empty=document.createElement('p');empty.className='muted empty-inline';empty.textContent='Không có mục nào lúc này.';c.rows.append(empty);}
    }
    async function load(target=0,poll=false){
      if(busy||sessionExpired||(poll&&document.hidden))return;
      busy=true;if(!poll){c.prev.disabled=c.next.disabled=refresh.disabled=true;widget.setAttribute('aria-busy','true');c.status.textContent='Đang tải…';}
      try{
        const data=await api(url(poll?'':cursors[target]||''));
        all(`[data-op-count="${section}"]`).forEach(node=>node.textContent=data.total);
        all(`[data-op-amount="${section}"]`).forEach(node=>node.textContent=data.totalAmount);
        if(poll&&page>0){if(revision!==data.revision){c.status.textContent='Có cập nhật mới · Bấm Làm mới khối để xem.';refresh.classList.add('has-updates');}return;}
        renderRows(data.rows);page=data.page;next=data.nextCursor;hasMore=data.hasMore;if(page===0){cursors=[''];revision=data.revision;}
        pager(c,data);c.status.textContent='Cập nhật: '+data.updatedAt;refresh.classList.remove('has-updates');
      }catch(error){c.status.textContent='Chưa tải được: '+error.message+' Dữ liệu trước vẫn được giữ.';}
      finally{busy=false;refresh.disabled=false;c.prev.disabled=page===0;c.next.disabled=!hasMore;widget.removeAttribute('aria-busy');}
    }
    c.prev.addEventListener('click',()=>load(page-1));
    c.next.addEventListener('click',()=>{cursors[page+1]=next;load(page+1);});refresh.addEventListener('click',()=>load(0));
    setInterval(()=>load(0,true),30000);document.addEventListener('visibilitychange',()=>{if(!document.hidden)load(0,true);});
  });
})();
