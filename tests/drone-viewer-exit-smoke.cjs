// Drone sunumunda Cikis/Turu Bitir/Sunuma Geri Don davranisini
// harita veya internet acmadan gercek buton handler koduyla test eder.
const assert=require("node:assert/strict"),fs=require("node:fs"),path=require("node:path"),vm=require("node:vm");
const html=fs.readFileSync(path.join(__dirname,"..","docs","drone-sunum.html"),"utf8");
for(const id of ["shareViewerControls","shareViewerStop","shareViewerExit","shareClosedLanding","shareViewerReopen","shareViewerPlay"]){
 assert.equal(html.split('id="'+id+'"').length-1,1,id+" must be present once");
}
assert.ok(html.includes(".shareViewerControls{position:fixed;z-index:35;"),"Floating controls must remain visible during flight");
assert.ok(html.includes(".shareViewerControls button[hidden]{display:none!important}"),"Hidden controls must not occupy space");
assert.ok(!/sb_secret_|service_role/.test(html),"No private account keys in public viewer");
for(const m of html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script\s*>/gi)){
 if(m[1].trim())new vm.Script(m[1]);
}
const start=html.indexOf("let sharedParcelLoaded=false,sharedTourStarted=false,sharedViewerClosed=false");
const end=html.indexOf("void loadSharedParcel();",start);
assert.ok(start>=0&&end>start,"Cannot isolate actual viewer interaction code");
const viewerCode=html.slice(start,end);
const ids=["shareViewerStop","shareViewerExit","shareViewerReopen","shareLanding","shareClosedLanding","shareViewerControls","shareViewerPlay","shareViewerStatus","fly"];
const nodes=new Map(ids.map(id=>[id,{hidden:id==="shareClosedLanding"||id==="shareViewerStop",textContent:"",handlers:new Map(),addEventListener(type,handler){this.handlers.set(type,handler)},click(){this.handlers.get("click")?.()}}]));
const history={length:1,backs:0,back(){this.backs++}};
const frame={};
const ctx=vm.createContext({
 $:id=>{assert.ok(nodes.has(id),"Unmocked UI node "+id);return nodes.get(id)},
 stopFlight:()=>{ctx.flying=false;ctx.stops=(ctx.stops||0)+1},
 hideCaption:()=>{ctx.captionsHidden=true},
 window:{history,flightAnchor:100},
 frame,progress:.6,flying:true,mapReady:false,nativeReady:false,
 setInterval:fn=>{ctx.interval=fn;return 1},
 console,
});
vm.runInContext(viewerCode,ctx);
const get=id=>nodes.get(id);
vm.runInContext("sharedParcelLoaded=true;sharedTourStarted=true",ctx);
get("shareLanding").hidden=true;
ctx.interval();
assert.equal(get("shareViewerStop").hidden,false,"Flight stop control must be shown during playback");
get("shareViewerStop").click();
assert.equal(ctx.flying,false,"Stop must cancel flight");
assert.equal(ctx.progress,0,"Stop must reset progress");
assert.equal(ctx.window.flightAnchor,null,"Stop must clear animation anchor");
assert.equal(ctx.captionsHidden,true,"Stop must hide captions");
assert.equal(get("shareLanding").hidden,false,"Stop must display original presentation screen");
assert.equal(get("shareViewerStop").hidden,true,"Stop button should be hidden once flight stops");
assert.ok(get("shareViewerStatus").textContent.includes("Tur durduruldu"));
get("shareViewerExit").click();
assert.equal(get("shareClosedLanding").hidden,false,"Exit must display fallback closed view");
assert.equal(get("shareLanding").hidden,true,"Exit must not keep presentation open");
assert.equal(get("shareViewerControls").hidden,true,"Exit must hide floating controls");
assert.equal(history.backs,0,"Do not navigate backward if no history exists");
vm.runInContext("if(!sharedViewerClosed)throw Error('Exit state lost')",ctx);
ctx.interval();
assert.equal(get("shareLanding").hidden,true,"Background polling must not reopen viewer after Exit");
get("shareViewerReopen").click();
assert.equal(get("shareClosedLanding").hidden,true);
assert.equal(get("shareLanding").hidden,false);
assert.equal(get("shareViewerControls").hidden,false);
vm.runInContext("if(sharedViewerClosed)throw Error('Reopen state still closed')",ctx);
history.length=2;get("shareViewerExit").click();
assert.equal(history.backs,1,"Exit should return to previous app if browser history allows");
console.log("PASS: floating Turu bitir, safe Cikis fallback, previous page and Sunuma geri don");
