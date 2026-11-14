// Public RE/MAX İlyada 3 listing snapshot updater; never touches customer data or Firebase.
// On a partial/blocking response, exits without overwriting the last verified snapshot.
const fs=require("node:fs");
const path=require("node:path");
const puppeteer=require("puppeteer");
const URL_BASE="https://remax.com.tr/tr/ofis/detay/ilyada-3";
const OUT=path.join(__dirname,"..","docs","data","ilyada3-portfoyler.json");
const CODE=/\/(?:tr\/)?portfoy\/(P\d{6,9})(?:[/?#]|$)/i;
const normalize=s=>String(s||"").replace(/\s+/g," ").trim();
function extractFlight(html){
 const matches=[...html.matchAll(/self\.__next_f\.push\(\[1,"((?:\\.|[^"\\])*)"\]\)/g)];
 return matches.map(m=>{try{return JSON.parse('"'+m[1]+'"')}catch{return""}}).join("");
}
function extractObject(flight,key){
 const k=flight.indexOf('"'+key+'"');if(k<0)return null;
 const start=flight.indexOf("{",k);if(start<0)return null;
 let depth=0,quote=false,escape=false;
 for(let i=start;i<flight.length;i++){
  const c=flight[i];
  if(quote){if(escape)escape=false;else if(c==="\\")escape=true;else if(c==='"')quote=false;continue;}
  if(c==='"')quote=true;
  else if(c==="{")depth++;
  else if(c==="}"&&!--depth){try{return JSON.parse(flight.slice(start,i+1))}catch{return null}}
 }
 return null;
}
function translateFlightListings(html){
 const f=extractFlight(html);
 let root=null;
 for(const key of ["officeDetailPropertyListingData","searchPropertyListingData","propertyListingData"]){
  root=extractObject(f,key);if(root)break;
 }
 if(!root)return[];
 const list=root?.data?.data||root?.data||root?.listings||[];
 if(!Array.isArray(list))return[];
 return list.map(o=>{
  if(!o||typeof o!=="object")return null;
  const code=String(o.code||"");
  if(!/^P\d{6,9}$/.test(code))return null;
  const getTitle=()=>Array.isArray(o.title)?(o.title.find(x=>x.languageId===1)||o.title[0])?.text:String(o.title||"");
  let picture=String(o.imageUrl||o.coverPhoto?.url||o.coverPhoto||o.thumbnail||"");
  if(picture.startsWith("//"))picture="https:"+picture;
  if(picture.startsWith("/"))picture="https://remax.com.tr"+picture;
  const cat=String(o.categoryName||"Daire");
  return {id:"remax_"+code,title:normalize(getTitle()),price:String(o.priceInfo?.amount||"")+" "+String(o.priceInfo?.amountTypeSymbol||"₺"),rooms:normalize(o.roomOptions),area:String(o.m2Area||""),location:[o.cityName,o.townName,o.neighborhoodName].filter(Boolean).join(", "),consultantName:normalize(o.employeeName),consultantPhone:normalize(o.employeePhone||o.employeeMobilePhone),officeName:"RE/MAX İlyada 3",type:/kira/i.test(o.operationName||"")?"Kiralık":"Satılık",propertyType:cat,imageUrl:picture,link:"https://remax.com.tr/tr/portfoy/"+code,createdAt:Date.now()};
 }).filter(Boolean);
}
function normalizeFromCard(p){
 const code=p.code;
 const text=normalize(p.text||"");
 let body=text.replace(/Portföy No\s*:?\s*P\d{6,9}/i,"").trim();
 const number=body.match(/(\d[\d.,\s]*)\s*₺/);
 const price=number?normalize(number[1])+" ₺":"";
 const area=(body.match(/(\d[\d.,]*)\s*m(?:²|2)/i)||[])[1]||"";
 const pre=number?body.slice(0,number.index):body;
 const seg=pre.split("/").map(x=>normalize(x)).filter(Boolean);
 const title=normalize(seg.length>3?seg.slice(3).join(" / "):seg.at(-1)||pre);
 return {id:"remax_"+code,title:title||code,price,rooms:"",area,location:seg.length>=3?seg.slice(0,3).join(", "):"",consultantName:"",consultantPhone:"",officeName:"RE/MAX İlyada 3",type:/kiralık/i.test(text)?"Kiralık":"Satılık",propertyType:/tarla|arsa|arazi|zeytinlik|bahçe/i.test(title)?"Tarla":/işyeri|dükkan|ofis|mağaza|depo/i.test(title)?"İşyeri":"Daire",imageUrl:p.image||"",link:"https://remax.com.tr/tr/portfoy/"+code,createdAt:Date.now()};
}
(async()=>{
 const old=JSON.parse(fs.readFileSync(OUT,"utf8"));
 if(!Array.isArray(old)||old.length<20)throw Error("Existing trusted feed absent");
 const oldMap=new Map(old.map(p=>[p.id,p]));
 const browser=await puppeteer.launch({headless:true,args:["--no-sandbox","--disable-setuid-sandbox"]});
 let newMap=new Map(),previousSize=0,failures=0;
 try{
  const page=await browser.newPage();
  await page.setViewport({width:1440,height:1000});
  await page.setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36");
  for(let number=1;number<=25;number++){
   const url=URL_BASE+(number>1?"?page="+number:"");
   try{
    const response=await page.goto(url,{waitUntil:"domcontentloaded",timeout:25000});
    if(!response||response.status()>=400)throw Error("HTTP "+response?.status());
    await new Promise(r=>setTimeout(r,1000));
    const parsed=translateFlightListings(await page.content());
    const dom=await page.evaluate(()=>{
     const found=new Map();
     for(const a of document.querySelectorAll('a[href*="/portfoy/P"]')){
      const m=a.href.match(/\/(?:tr\/)?portfoy\/(P\d{6,9})(?:[/?#]|$)/i);
      if(!m)continue;
      let node=a;
      for(let depth=0;depth<5&&node.parentElement;depth++){
       const parent=node.parentElement;
       const content=(parent.innerText||"").trim();
       if(/Portföy No/i.test(content)&&content.length<1600){node=parent;break;}
       node=parent;
      }
      const img=node.querySelector("img");
      found.set(m[1],{code:m[1],text:node.innerText||a.innerText||"",image:img?.src||""});
     }
     return [...found.values()];
    });
    const parsedByCode=new Map(parsed.map(p=>[p.id,p]));
    for(const d of dom){
      const id="remax_"+d.code;
      const fromDom=normalizeFromCard(d);
      const structured=parsedByCode.get(id);
      const previous=oldMap.get(id);
      const merged={...fromDom,...(structured||{})};
      for(const key of ["title","price","rooms","area","location","consultantName","consultantPhone","propertyType","imageUrl"]){
       if(!merged[key]||!String(merged[key]).trim())merged[key]=previous?.[key]||fromDom[key]||"";
      }
      if(!merged.imageUrl.startsWith("https://"))merged.imageUrl=previous?.imageUrl||"";
      newMap.set(id,merged);
    }
    for(const p of parsed)if(!newMap.has(p.id))newMap.set(p.id,{...oldMap.get(p.id),...p});
    console.log("Page",number,"HTTP",response.status(),"DOM",dom.length,"Flight",parsed.length,"unique total",newMap.size);
    if(newMap.size===previousSize){if(++failures>=2)break;}else failures=0;
    previousSize=newMap.size;
    if(number>1&&dom.length<5&&parsed.length<5)break;
   }catch(err){console.warn("Page",number,"read problem",String(err.message||err));if(++failures>=2)break;}
  }
 }finally{await browser.close();}
 const incoming=[...newMap.values()].filter(p=>/^remax_P\d{6,9}$/.test(p.id)&&p.title&&p.link.startsWith("https://remax.com.tr/tr/portfoy/"));
 const threshold=Math.max(25,Math.floor(old.length*.65));
 if(incoming.length<threshold){
  console.warn("Safety threshold: only",incoming.length,"of",old.length,"expected >= ",threshold,". Old data preserved.");
  process.exit(0);
 }
 incoming.sort((a,b)=>b.createdAt-a.createdAt);
 fs.writeFileSync(OUT,JSON.stringify(incoming,null,2)+"\n","utf8");
 console.log("Updated public RE/MAX feed:",incoming.length,"listings. No CRM data touched.");
})().catch(error=>{console.error(error.stack||error);process.exitCode=1});
