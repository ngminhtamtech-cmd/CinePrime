// Staff function rail. Loaded without defer so a collapsed rail is applied before first paint (CSP forbids inline scripts).
(()=>{
  const key='cineprime.staff.navCollapsed',root=document.documentElement;
  try{if(localStorage.getItem(key)==='1')root.classList.add('staff-nav-collapsed','staff-nav-instant');}catch{}
  document.addEventListener('DOMContentLoaded',()=>{
    const toggle=document.getElementById('staff-nav-toggle'),nav=document.getElementById('staff-nav');
    requestAnimationFrame(()=>requestAnimationFrame(()=>root.classList.remove('staff-nav-instant')));
    if(toggle&&nav){
      const apply=on=>{root.classList.toggle('staff-nav-collapsed',on);toggle.setAttribute('aria-expanded',String(!on));toggle.title=(on?'Hiện':'Ẩn')+' thanh chức năng (Ctrl + B)';try{localStorage.setItem(key,on?'1':'0');}catch{}};
      apply(root.classList.contains('staff-nav-collapsed'));
      toggle.addEventListener('click',()=>apply(!root.classList.contains('staff-nav-collapsed')));
      document.addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&!e.altKey&&e.key.toLowerCase()==='b'&&!e.target.closest?.('input,textarea,select,[contenteditable]')){e.preventDefault();apply(!root.classList.contains('staff-nav-collapsed'));}});
    }
    // Counter: orders of the shift live in a side drawer so the sales screen never scrolls.
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
