// Staff header: the toggle collapses the left column (showtime list); back / forward follow browser history.
// Loaded without defer so a collapsed column is applied before first paint (CSP forbids inline scripts).
(()=>{
  const key='cineprime.staff.leftCollapsed',root=document.documentElement;
  try{if(localStorage.getItem(key)==='1')root.classList.add('staff-left-collapsed');}catch{}
  document.addEventListener('DOMContentLoaded',()=>{
    const toggle=document.getElementById('staff-nav-toggle');
    if(toggle){
      const apply=on=>{root.classList.toggle('staff-left-collapsed',on);toggle.setAttribute('aria-expanded',String(!on));toggle.title=(on?'Hiện':'Ẩn')+' cột trái (Ctrl + B)';try{localStorage.setItem(key,on?'1':'0');}catch{}};
      apply(root.classList.contains('staff-left-collapsed'));
      toggle.addEventListener('click',()=>apply(!root.classList.contains('staff-left-collapsed')));
      document.addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&!e.altKey&&e.key.toLowerCase()==='b'&&!e.target.closest?.('input,textarea,select,[contenteditable]')){e.preventDefault();apply(!root.classList.contains('staff-left-collapsed'));}});
    }
    const back=document.getElementById('nav-back'),forward=document.getElementById('nav-forward'),nav=window.navigation;
    const sync=()=>{if(back)back.disabled=nav?!nav.canGoBack:history.length<2;if(forward)forward.disabled=nav?!nav.canGoForward:false;};
    back?.addEventListener('click',()=>history.back());forward?.addEventListener('click',()=>history.forward());sync();addEventListener('pageshow',sync);nav?.addEventListener?.('currententrychange',sync);
    // Counter: orders of the shift live in a dialog so the sales screen never scrolls.
    const drawer=document.getElementById('orders-drawer'),open=document.getElementById('orders-open');
    if(drawer&&open){
      open.addEventListener('click',()=>drawer.showModal());
      drawer.addEventListener('click',e=>{if(e.target===drawer||e.target.closest('[data-drawer-close]'))drawer.close();});
    }
    // A failed close-shift redirect opens the form again; keep the popover closing on outside click / Escape.
    const close=document.getElementById('close-shift');
    if(close){document.addEventListener('click',e=>{if(close.open&&!close.contains(e.target))close.open=false;});document.addEventListener('keydown',e=>{if(e.key==='Escape'&&close.open){close.open=false;close.querySelector('summary').focus();}});}
  });
})();
