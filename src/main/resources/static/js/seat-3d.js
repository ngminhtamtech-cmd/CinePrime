'use strict';
// Seat3D: illustrative 3D view of a room built only from rowLabel/position/type/state.
// three.js (MIT, vendored in /js/vendor) is imported lazily the first time the viewer opens.
(() => {
  const PITCH=0.62,ROW_DEPTH=1.05,RISER=0.2,FIRST_ROW=5,EYE=1.12,TRANSITION=650;
  const COLORS={STANDARD:0xd9e1ea,VIP:0x7fb6ea,COUPLE:0xc79ad9,MAINTENANCE:0x7c858f,SOLD:0x56616e,HELD:0xf2c14e,SELECTED:0xf47b20};
  const TYPE_TEXT={STANDARD:'Ghế thường',VIP:'VIP',COUPLE:'Ghế đôi'},STATE_TEXT={AVAILABLE:'Trống',HELD:'Đang giữ',SOLD:'Đã bán',MAINTENANCE:'Bảo trì'};
  const reduceMotion=()=>matchMedia('(prefers-reduced-motion: reduce)').matches;
  let THREE=null,loading=null,dialog=null,view=null,returnFocus=null;
  const load=()=>loading??=import('/js/vendor/three.module.js').then(m=>{THREE=m;return m;}).catch(e=>{loading=null;throw e;});
  const el=(tag,text,cls)=>{const n=document.createElement(tag);if(text!==undefined)n.textContent=text;if(cls)n.className=cls;return n;};

  function buildDialog(){
    dialog=el('dialog',undefined,'cine-dialog seat3d-dialog');dialog.setAttribute('aria-labelledby','seat3d-title');
    const head=el('div',undefined,'seat3d-head'),title=el('h2','Xem phòng 3D');title.id='seat3d-title';const close=el('button','×','seat3d-close');close.type='button';close.setAttribute('aria-label','Đóng xem 3D');head.append(title,close);
    const stage=el('div',undefined,'seat3d-stage');stage.tabIndex=0;stage.setAttribute('role','img');const status=el('p','Đang tải trình xem 3D…','seat3d-status');status.setAttribute('role','status');stage.append(status);
    const controls=el('div',undefined,'seat3d-controls'),overview=el('button','Toàn phòng','seat3d-mode'),seatView=el('button','Góc nhìn từ ghế','seat3d-mode');overview.type=seatView.type='button';overview.dataset.mode='overview';seatView.dataset.mode='seat';
    const label=el('label','Ghế','sr-only'),select=el('select',undefined,'seat3d-seat');select.id='seat3d-seat';label.htmlFor='seat3d-seat';
    controls.append(overview,seatView,label,select);
    const hint=el('p','Kéo để xoay hoặc nhìn quanh, cuộn hoặc chụm hai ngón để phóng to. Bấm một ghế để xem từ ghế đó.','seat3d-hint'),noteText=el('p','Hình minh họa dựng từ sơ đồ ghế, không phải kích thước thật của phòng.','seat3d-note');
    dialog.append(head,controls,stage,hint,noteText);document.body.append(dialog);
    close.addEventListener('click',()=>{dialog.close();teardown();});dialog.addEventListener('close',()=>{if(!dialog.open)teardown();});
    overview.addEventListener('click',()=>view?.setOverview());seatView.addEventListener('click',()=>view?.setSeat(view.current??view.defaultSeat()));
    select.addEventListener('change',()=>{const s=view?.seats.find(x=>x.key===select.value);if(s)view.setSeat(s);});
  }
  const part=cls=>dialog.querySelector(cls);
  function showStatus(text){const status=part('.seat3d-status');status.textContent=text;status.hidden=!text;}

  async function open(options){
    if(!options?.seats?.length)return;if(!dialog)buildDialog();if(dialog.open)return;if(view)teardown();
    returnFocus=options.returnTo||document.activeElement;part('#seat3d-title').textContent=options.title?'Xem 3D · '+options.title:'Xem phòng 3D';showStatus('Đang tải trình xem 3D…');
    fillSelect(options);dialog.showModal();part('.seat3d-stage').focus({preventScroll:true});
    try{await load();}catch{showStatus('Chưa tải được trình xem 3D. Kiểm tra kết nối rồi thử lại. Sơ đồ 2D vẫn dùng bình thường.');return;}
    if(!dialog.open)return;
    try{view=createView(options);}catch{view=null;showStatus('Thiết bị hoặc trình duyệt này chưa hỗ trợ WebGL nên không hiển thị được 3D. Sơ đồ 2D vẫn dùng bình thường.');return;}
    showStatus('');
  }
  function fillSelect(options){const select=part('.seat3d-seat');select.replaceChildren(new Option('Chọn ghế để xem góc nhìn',''));const rows=new Map();for(const s of sorted(options.seats)){if(!rows.has(s.rowLabel)){const g=el('optgroup');g.label='Hàng '+s.rowLabel;rows.set(s.rowLabel,g);select.append(g);}rows.get(s.rowLabel).append(new Option(s.label+' · '+(TYPE_TEXT[s.type]||s.type)+(s.state&&s.state!=='AVAILABLE'?' · '+(STATE_TEXT[s.state]||s.state):''),s.rowLabel+':'+s.position));}}
  const sorted=list=>[...list].sort((a,b)=>a.rowLabel.localeCompare(b.rowLabel)||a.position-b.position);
  function teardown(){view?.dispose();view=null;const r=returnFocus;returnFocus=null;if(r?.isConnected)r.focus({preventScroll:true});}

  function seatColor(s,selected){if(selected.has(s.label))return COLORS.SELECTED;const state=s.state||(s.active===false?'MAINTENANCE':'AVAILABLE');if(state==='SOLD')return COLORS.SOLD;if(state==='HELD')return COLORS.HELD;if(state==='MAINTENANCE'||s.active===false)return COLORS.MAINTENANCE;return COLORS[s.type]??COLORS.STANDARD;}

  function createView(options){
    const T=THREE,stage=part('.seat3d-stage'),select=part('.seat3d-seat'),selected=new Set(options.selectedLabels||[]);
    const renderer=new T.WebGLRenderer({antialias:true,powerPreference:'low-power'});renderer.setPixelRatio(Math.min(devicePixelRatio||1,2));renderer.outputColorSpace=T.SRGBColorSpace;stage.append(renderer.domElement);renderer.domElement.className='seat3d-canvas';
    const scene=new T.Scene();scene.background=new T.Color(0x151a22);scene.fog=new T.Fog(0x151a22,45,140);
    const camera=new T.PerspectiveCamera(55,1,0.05,200),disposables=[];const keep=x=>{disposables.push(x);return x;};
    // geometry of the room derived from the seat grid
    const rowLabels=[...new Set(options.seats.map(s=>s.rowLabel))].sort(),firstCode=rowLabels[0].charCodeAt(0),rowCount=rowLabels.at(-1).charCodeAt(0)-firstCode+1,maxPos=Math.max(...options.seats.map(s=>s.position));
    const rowOf=s=>s.rowLabel.charCodeAt(0)-firstCode,xOf=p=>(p-(maxPos+1)/2)*PITCH,zOf=r=>FIRST_ROW+r*ROW_DEPTH,floorOf=r=>r*RISER;
    const width=Math.max(8,maxPos*PITCH+4),depth=zOf(rowCount)+2.5,height=Math.max(6,floorOf(rowCount)+3.2);
    const seats=sorted(options.seats).map(s=>({...s,key:s.rowLabel+':'+s.position,row:rowOf(s),x:xOf(s.position),z:zOf(rowOf(s)),y:floorOf(rowOf(s))}));
    // shell: floor, risers, walls, ceiling
    const wallMat=keep(new T.MeshStandardMaterial({color:0x232a35,roughness:.95,side:T.BackSide})),room=new T.Mesh(keep(new T.BoxGeometry(width,height,depth+1)),wallMat);room.position.set(0,height/2-0.01,(depth-1)/2);scene.add(room);
    const floorMat=keep(new T.MeshStandardMaterial({color:0x3a2f33,roughness:1})),stepMat=keep(new T.MeshStandardMaterial({color:0x332a2e,roughness:1}));
    const floor=new T.Mesh(keep(new T.PlaneGeometry(width,depth+1)),floorMat);floor.rotation.x=-Math.PI/2;floor.position.set(0,0,(depth-1)/2);scene.add(floor);
    for(let r=1;r<=rowCount;r++){const h=floorOf(r)-0.0001,step=new T.Mesh(keep(new T.BoxGeometry(width-0.02,h,ROW_DEPTH)),stepMat);step.position.set(0,h/2,zOf(r)-ROW_DEPTH*0.45);scene.add(step);}
    // curved screen with a canvas texture
    const screenW=Math.min(width-1.5,Math.max(6,maxPos*PITCH*0.9)),screenH=screenW/2.2,screenY=1.3+screenH/2,canvas=document.createElement('canvas');canvas.width=1024;canvas.height=Math.round(1024/2.2);const g=canvas.getContext('2d'),grad=g.createLinearGradient(0,0,0,canvas.height);grad.addColorStop(0,'#e9f2ff');grad.addColorStop(1,'#bcd3ef');g.fillStyle=grad;g.fillRect(0,0,canvas.width,canvas.height);g.fillStyle='#1459a5';g.font='600 64px "Be Vietnam Pro", Inter, "Noto Sans", sans-serif';g.textAlign='center';g.textBaseline='middle';g.fillText('MÀN HÌNH',canvas.width/2,canvas.height/2);
    const texture=keep(new T.CanvasTexture(canvas));texture.colorSpace=T.SRGBColorSpace;const screenGeo=keep(new T.PlaneGeometry(screenW,screenH,48,1)),pos=screenGeo.attributes.position,radius=screenW*1.6;for(let i=0;i<pos.count;i++){const x=pos.getX(i);pos.setZ(i,(x*x)/(2*radius));}pos.needsUpdate=true;screenGeo.computeVertexNormals();
    const screen=new T.Mesh(screenGeo,keep(new T.MeshBasicMaterial({map:texture})));screen.position.set(0,screenY,0.6);scene.add(screen);
    const frame=new T.Mesh(keep(new T.PlaneGeometry(screenW+0.4,screenH+0.4)),keep(new T.MeshBasicMaterial({color:0x0b0e13})));frame.position.set(0,screenY,0.55);scene.add(frame);
    // lights
    scene.add(new T.HemisphereLight(0xdfe8f5,0x2a2226,1.4));const key=new T.DirectionalLight(0xffffff,1.2);key.position.set(3,8,depth);scene.add(key);const glow=new T.PointLight(0xcfe0ff,18,25,1.6);glow.position.set(0,screenY,2.2);scene.add(glow);
    // seats as instanced meshes grouped by colour
    const baseGeo=keep(new T.BoxGeometry(1,0.14,0.5)),backGeo=keep(new T.BoxGeometry(1,0.58,0.1)),groups=new Map(),pickers=[],matrix=new T.Matrix4();
    for(const s of seats){const color=seatColor(s,selected);if(!groups.has(color))groups.set(color,[]);groups.get(color).push(s);}
    for(const [color,list] of groups){const mat=keep(new T.MeshStandardMaterial({color,roughness:.7})),base=new T.InstancedMesh(baseGeo,mat,list.length),back=new T.InstancedMesh(backGeo,mat,list.length);list.forEach((s,i)=>{const w=s.type==='COUPLE'?PITCH-0.01:PITCH-0.1;matrix.makeScale(w,1,1).setPosition(s.x,s.y+0.42,s.z);base.setMatrixAt(i,matrix);matrix.makeScale(w,1,1).setPosition(s.x,s.y+0.72,s.z+0.24);back.setMatrixAt(i,matrix);});base.userData.seats=back.userData.seats=list;scene.add(base,back);pickers.push(base,back);}
    // marker for the seat being viewed
    const marker=new T.Mesh(keep(new T.TorusGeometry(0.32,0.04,8,32)),keep(new T.MeshBasicMaterial({color:COLORS.SELECTED})));marker.rotation.x=Math.PI/2;marker.visible=false;scene.add(marker);

    // camera state
    const center=new T.Vector3(0,floorOf(rowCount/2)+0.6,zOf((rowCount-1)/2)*0.75);
    const orbit={theta:0,phi:1.0,radius:Math.max(width,depth)*0.8},look={yaw:0,pitch:0};let mode='overview',current=null,anim=null,dirty=true,frameId=0;
    const desired=()=>{if(mode==='overview'){const p=new T.Vector3(center.x+orbit.radius*Math.sin(orbit.phi)*Math.sin(orbit.theta),center.y+orbit.radius*Math.cos(orbit.phi),center.z+orbit.radius*Math.sin(orbit.phi)*Math.cos(orbit.theta));return {pos:p,target:center.clone()};}const eye=new T.Vector3(current.x,current.y+EYE,current.z),target=new T.Vector3(0,screenY,0.6),dir=target.clone().sub(eye);const yaw=Math.atan2(dir.x,-dir.z)+look.yaw,pitch=Math.atan2(dir.y,Math.hypot(dir.x,dir.z))+look.pitch;return {pos:eye,target:eye.clone().add(new T.Vector3(Math.sin(yaw)*Math.cos(pitch),Math.sin(pitch),-Math.cos(yaw)*Math.cos(pitch)))};};
    const lookTarget=new T.Vector3();
    function place(animate){const d=desired();if(!animate||reduceMotion()){anim=null;camera.position.copy(d.pos);lookTarget.copy(d.target);}else anim={from:camera.position.clone(),fromTarget:lookTarget.clone(),to:d.pos,toTarget:d.target,start:performance.now()};dirty=true;}
    function tick(now){frameId=requestAnimationFrame(tick);if(anim){const t=Math.min(1,(now-anim.start)/TRANSITION),e=t<.5?2*t*t:1-Math.pow(-2*t+2,2)/2;camera.position.lerpVectors(anim.from,anim.to,e);lookTarget.lerpVectors(anim.fromTarget,anim.toTarget,e);if(t>=1)anim=null;dirty=true;}if(!dirty)return;camera.lookAt(lookTarget);renderer.render(scene,camera);dirty=false;}
    function resize(){const w=stage.clientWidth,h=stage.clientHeight;if(!w||!h)return;renderer.setSize(w,h,false);camera.aspect=w/h;camera.updateProjectionMatrix();dirty=true;}
    const observer=new ResizeObserver(resize);observer.observe(stage);resize();
    function syncUi(){dialog.querySelectorAll('.seat3d-mode').forEach(b=>b.setAttribute('aria-pressed',String(b.dataset.mode===mode)));select.value=mode==='seat'&&current?current.key:'';stage.setAttribute('aria-label',mode==='seat'&&current?'Góc nhìn từ ghế '+current.label+' tới màn hình':'Toàn cảnh phòng chiếu 3D');}
    const api={seats,get current(){return current;},
      defaultSeat(){const focus=seats.find(s=>s.label===options.focusLabel)||seats.find(s=>selected.has(s.label));if(focus)return focus;const mid=Math.floor(rowCount*0.6);return seats.reduce((best,s)=>Math.abs(s.row-mid)+Math.abs(s.x)*0.5<Math.abs(best.row-mid)+Math.abs(best.x)*0.5?s:best,seats[0]);},
      setOverview(){mode='overview';marker.visible=false;syncUi();place(true);},
      setSeat(s){if(!s)return;mode='seat';current=s;look.yaw=look.pitch=0;camera.fov=60;camera.updateProjectionMatrix();marker.position.set(s.x,s.y+0.52,s.z);marker.visible=true;syncUi();place(true);},
      dispose(){cancelAnimationFrame(frameId);observer.disconnect();stage.removeEventListener('pointerdown',down);stage.removeEventListener('pointermove',move);stage.removeEventListener('pointerup',up);stage.removeEventListener('pointercancel',up);stage.removeEventListener('wheel',wheel);stage.removeEventListener('keydown',keys);scene.traverse(o=>{if(o.isInstancedMesh)o.dispose();});for(const d of disposables)d.dispose();renderer.dispose();renderer.forceContextLoss();renderer.domElement.remove();}};
    // pointer and keyboard controls
    const pointers=new Map();let pinch=0,moved=0;
    function rotate(dx,dy){if(mode==='overview'){orbit.theta-=dx*0.006;orbit.phi=Math.min(1.45,Math.max(0.2,orbit.phi-dy*0.005));}else{look.yaw=Math.max(-1,Math.min(1,look.yaw+dx*0.004));look.pitch=Math.max(-0.5,Math.min(0.5,look.pitch-dy*0.004));}place(false);}
    function zoomBy(f){if(mode==='overview'){orbit.radius=Math.max(4,Math.min(Math.max(width,depth)*2,orbit.radius*f));place(false);}else{camera.fov=Math.max(30,Math.min(80,camera.fov*f));camera.updateProjectionMatrix();dirty=true;}}
    function down(e){stage.setPointerCapture(e.pointerId);pointers.set(e.pointerId,{x:e.clientX,y:e.clientY});moved=0;if(pointers.size===2){const [a,b]=[...pointers.values()];pinch=Math.hypot(a.x-b.x,a.y-b.y);}}
    function move(e){const p=pointers.get(e.pointerId);if(!p)return;const dx=e.clientX-p.x,dy=e.clientY-p.y;p.x=e.clientX;p.y=e.clientY;moved+=Math.abs(dx)+Math.abs(dy);if(pointers.size===2){const [a,b]=[...pointers.values()],d=Math.hypot(a.x-b.x,a.y-b.y);if(pinch)zoomBy(pinch/d);pinch=d;return;}rotate(dx,dy);}
    const ray=new T.Raycaster(),ndc=new T.Vector2();
    function up(e){const tap=pointers.size===1&&moved<6;pointers.delete(e.pointerId);if(pointers.size<2)pinch=0;if(!tap)return;const r=renderer.domElement.getBoundingClientRect();ndc.set((e.clientX-r.left)/r.width*2-1,-(e.clientY-r.top)/r.height*2+1);ray.setFromCamera(ndc,camera);const hit=ray.intersectObjects(pickers,false)[0];if(hit&&hit.instanceId!=null)api.setSeat(hit.object.userData.seats[hit.instanceId]);}
    function wheel(e){e.preventDefault();zoomBy(e.deltaY>0?1.1:1/1.1);}
    function keys(e){const m={ArrowLeft:[-20,0],ArrowRight:[20,0],ArrowUp:[0,-20],ArrowDown:[0,20]}[e.key];if(m){e.preventDefault();rotate(m[0],m[1]);}else if(e.key==='+'||e.key==='='){zoomBy(1/1.1);}else if(e.key==='-'){zoomBy(1.1);}}
    stage.addEventListener('pointerdown',down);stage.addEventListener('pointermove',move);stage.addEventListener('pointerup',up);stage.addEventListener('pointercancel',up);stage.addEventListener('wheel',wheel,{passive:false});stage.addEventListener('keydown',keys);
    const start=options.focusLabel&&seats.find(s=>s.label===options.focusLabel);if(start||options.mode==='seat')api.setSeat(start||api.defaultSeat());else{syncUi();place(false);}if(start||options.mode==='seat'){anim=null;place(false);}
    frameId=requestAnimationFrame(tick);
    return api;
  }
  window.Seat3D={open};
})();
