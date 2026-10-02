import fs from 'node:fs';
import path from 'node:path';
import {readTemplate,validate} from './validate-itinerary.mjs';
const root=process.cwd(),{html,example,rules}=readTemplate(path.join(root,'templates/여행 일정 템플릿.html'));
const clone=value=>JSON.parse(JSON.stringify(value));
const assetDir=path.join(root,'app/src/androidTest/assets');
function fixture(count){
 const d=clone(example),prototype=clone(d.days[0]);
 d.documentId='EXAMPLE-identical-document-id'; // Different files must remain isolated even after a copied ID.
 d.days=[];d.events={};d.services={};d.timetables={};
 for(let index=0;index<count;index++){
  const n=index+1,date=new Date(Date.UTC(2000,0,3+index)).toISOString().slice(0,10),day=clone(prototype);
  day.n=n;day.date=date;day.weekday='일월화수목금토'[new Date(date+'T00:00:00Z').getUTCDay()];
  const ids=new Map([...Object.keys(example.events),...Object.keys(example.services),...Object.keys(example.timetables)].map(id=>[id,id+'-'+n]));
  const remap=value=>typeof value==='string'?(ids.get(value)||value):Array.isArray(value)?value.map(remap)
   :value&&typeof value==='object'?Object.fromEntries(Object.entries(value).map(([key,v])=>[ids.get(key)||key,remap(v)])):value;
  const part=remap({day,events:example.events,services:example.services,timetables:example.timetables});
  const applyDate=value=>{
   if(!value||typeof value!=='object')return;
   if(value.operation){value.operation.date=date;value.operation.weekdays=[new Date(date+'T00:00:00Z').getUTCDay()];}
   Object.values(value).forEach(applyDate);
  };
  applyDate(part);
  Object.assign(d.events,part.events);Object.assign(d.services,part.services);Object.assign(d.timetables,part.timetables);d.days.push(part.day);
 }
 d.places['EXAMPLE-destination'].mapUrl='https://www.google.com/maps/search/?api=1&query=0%2C0';
 const issues=validate(d,rules);if(issues.length)throw Error(JSON.stringify(issues));
 return d;
}
function monitored(source){return source.replace('<head>','<head><script>window.fixtureErrors=[];window.fixtureClicks=[];addEventListener("error",e=>{if(e.message)fixtureErrors.push(e.message)});addEventListener("click",e=>{fixtureClicks.push({tag:e.target.tagName,text:e.target.textContent.slice(0,70),x:e.clientX,y:e.clientY});if(window.fixtureExpectedSelector&&e.target.closest(window.fixtureExpectedSelector))window.fixtureExpectedClick=true},true);</script>').replace('restoreChoices();render();','window.fixtureTrip=D;window.fixtureRender=render;restoreChoices();render();').replace('</body>','<script>window.fixtureReady=true;</script></body>');}
for(const [name,count]of [['itinerary-five.html',5],['itinerary-eight.html',8]]){
 const data=fixture(count),source=html.replace(/(<script type="application\/json" id="trip-data">)[\s\S]*?(<\/script>)/,()=>'<script type="application/json" id="trip-data">'+JSON.stringify(data)+'</script>');
 fs.writeFileSync(path.join(assetDir,name),monitored(source));
}
fs.writeFileSync(path.join(assetDir,'itinerary-template.html'),monitored(html));
console.log('Generated only synthetic Android test assets: five days, eight days and unchanged blank template data.');
