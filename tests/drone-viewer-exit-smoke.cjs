// Read-only Drone viewer exit controls; simulated offline Chromium user interaction.
const assert=require("node:assert/strict"),fs=require("node:fs"),os=require("node:os"),path=require("node:path"),vm=require("node:vm");
const {spawnSync}=require("node:child_process");
const source=fs.readFileSync(path.join(__dirname,"..","docs","drone-sunum.html"),"utf8");
for(const id of ["shareViewerControls","shareViewerStop","shareViewerExit","shareClosedLanding","shareViewerReopen","shareViewerPlay"])
 assert.equal(source.split('id="'+id+'"').length-1,1,id+" must exist once");
assert.ok(source.includes("function endSharedTour()"),"Missing stop handler");
assert.ok(source.includes("window.history.back()"),"Missing back navigation");
assert.ok(source.includes("sharedViewerClosed||!sharedParcelLoaded"),"No exit-state poll guard");
assert.ok(source.includes('const MAPBOX_PUBLIC_TOKEN=""'),"Share viewer must not access private keys");
for(const match of source.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script\s*>/gi))
 if(match[1].trim())new vm.Script(match[1]);
let html=source.replaceAll(/<link\b[^>]*https:\/\/(?:cdn\.jsdelivr\.net|api\.mapbox\.com)[^>]*>/gi,"")
 .replace("loadMapSdk();\n\n/* Sadece URL","/* offline test avoids external SDK */\n\n/* Sadece URL");
const shim=[
 "<script>",
 'document.addEventListener("DOMContentLoaded",()=>{',
 'let result="PASS";',
 'try{',
 'const el=id=>document.getElementById(id);',
 'sharedParcelLoaded=true;nativeReady=true;flying=true;sharedTourStarted=true;setButtons();',
 'el("shareLanding").hidden=true;el("shareViewerStop").hidden=false;',
 'const duringFlight=flying&&getComputedStyle(el("shareViewerStop")).display!=="none"&&getComputedStyle(el("shareViewerExit")).display!=="none"&&getComputedStyle(document.querySelector(".panel")).display==="none";',
 'el("shareViewerStop").click();',
 'const stopped=!flying&&!sharedTourStarted&&!sharedViewerClosed&&!el("shareLanding").hidden&&el("shareViewerStop").hidden&&progress===0&&el("shareViewerStatus").textContent.includes("Tur durduruldu");',
 'el("shareViewerExit").click();',
 'const exited=sharedViewerClosed&&!el("shareClosedLanding").hidden&&el("shareLanding").hidden&&el("shareViewerControls").hidden;',
 'el("shareViewerReopen").click();',
 'const reopened=!sharedViewerClosed&&el("shareClosedLanding").hidden&&!el("shareViewerControls").hidden&&!el("shareLanding").hidden;',
 'if(!(duringFlight&&stopped&&exited&&reopened))result="FAIL:"+[duringFlight,stopped,exited,reopened].join(",");',
 '}catch(e){result="FAIL:"+e.message}',
 'document.body.dataset.exitSmoke=result;',
 '});',
 '</script>'
].join("\n");
const entry='<script>\n"use strict";';
assert.ok(html.includes(entry),"Viewer inline JS entry missing");
html=html.replace(entry,shim+"\n"+entry);
const dir=fs.mkdtempSync(path.join(os.tmpdir(),"drone-exit-")),file=path.join(dir,"viewer.html");
fs.writeFileSync(file,html);
const chrome=["google-chrome","google-chrome-stable","chromium"].find(x=>spawnSync("which",[x],{encoding:"utf8"}).status===0);
assert.ok(chrome,"Chromium not installed");
const run=spawnSync(chrome,["--headless=new","--no-sandbox","--disable-gpu","--disable-dev-shm-usage","--disable-background-networking","--virtual-time-budget=2200","--dump-dom","file://"+file],{encoding:"utf8",timeout:25000,maxBuffer:7*1024*1024});
const status=(run.stdout||"").match(/data-exit-smoke="([^"]+)"/)?.[1]||"NO_RESULT";
console.log("Drone viewer exit controls:",status);
if(status!=="PASS"){console.error((run.stderr||"").slice(-800));process.exit(1)}
