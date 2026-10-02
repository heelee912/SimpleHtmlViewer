import fs from 'node:fs';
import vm from 'node:vm';
import {pathToFileURL} from 'node:url';

export function readTemplate(file) {
  const html=fs.readFileSync(file,'utf8');
  const block=id=>{
    const match=html.match(new RegExp('<script[^>]*id="'+id+'"[^>]*>([\\s\\S]*?)</script>'));
    if(!match)throw Error('Missing script block: '+id);
    return match[1];
  };
  const context=vm.createContext({});
  vm.runInContext(block('itinerary-schedule-rules'),context,{timeout:1000});
  for(const match of html.matchAll(/<script([^>]*)>([\s\S]*?)<\/script>/g)) {
    if(!match[1].includes('application/json'))new vm.Script(match[2]);
  }
  return {html,data:JSON.parse(block('trip-data')),example:JSON.parse(block('authoring-example')),rules:context.ItinerarySchedule};
}

export function validate(data,rules,{final=false}={}) {
  return rules.checkTrip(data,{final});
}

if(process.argv[1]&&import.meta.url===pathToFileURL(process.argv[1]).href){
  const args=process.argv.slice(2),final=args.includes('--final'),file=args.find(a=>!a.startsWith('--'));
  if(!file){console.error('Usage: node scripts/validate-itinerary.mjs [--final] FILE');process.exitCode=2;}
  else{
    const {data,rules}=readTemplate(file),issues=validate(data,rules,{final});
    console.log(JSON.stringify({file,mode:final?'completion':'structure',errors:issues.filter(i=>i.severity==='error').length,warnings:issues.filter(i=>i.severity==='warning').length,issues},null,2));
    if(issues.some(i=>i.severity==='error'))process.exitCode=1;
  }
}
