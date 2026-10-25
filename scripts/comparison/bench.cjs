// Repeats the 2026-10-23 DyRTP vs BetterRTP command-to-teleport comparison with DyRTP 1.1.0.
// Same Paper 26.1.2 build 74, Java 25.0.4.1, world snapshot, probe, client, settings and phase order as
// docs/betterrtp-comparison.md. Each plugin runs alone on a fresh copy of the saved snapshot.
const fs=require('fs'),path=require('path'),{spawn}=require('child_process');
const T='C:/Users/alph/AppData/Local/Temp/DyRTP-BetterRTP-comparison-20261023';
const W=path.join(__dirname,'run'),OUT=__dirname;
const DYRTP='C:/Users/alph/Documents/DyRTP/build/libs/DyRTP-1.1.0.jar',BETTER=path.join(T,'BetterRTP-measured.jar');
const MODULES='C:/Users/alph/AppData/Local/Temp/DyRTP-1.0.1-live-20261023/client/node_modules';
const delay=ms=>new Promise(r=>setTimeout(r,ms));const clean=s=>s.replace(/\x1b\[[0-9;]*m/g,'');
async function until(fn,label,t=120000){const end=Date.now()+t;while(Date.now()<end){const v=fn();if(v)return v;await delay(250);}throw Error('Timeout: '+label);}
const results=[];
async function phase(name){
  fs.rmSync(W,{recursive:true,force:true});fs.mkdirSync(path.join(W,'plugins'),{recursive:true});
  fs.copyFileSync(path.join(T,'paper-26.1.2-74.jar'),path.join(W,'paper.jar'));
  for(const f of ['server.properties','eula.txt','bukkit.yml','spigot.yml','commands.yml','help.yml','permissions.yml','ops.json','whitelist.json','banned-ips.json','banned-players.json'])if(fs.existsSync(path.join(T,f)))fs.copyFileSync(path.join(T,f),path.join(W,f));
  for(const d of ['config','libraries','cache','versions'])fs.cpSync(path.join(T,d),path.join(W,d),{recursive:true});
  fs.cpSync(path.join(T,'snapshot','benchmark'),path.join(W,'benchmark'),{recursive:true});fs.rmSync(path.join(W,'benchmark','session.lock'),{force:true});
  fs.writeFileSync(path.join(W,'ops.json'),'[]');
  fs.copyFileSync(path.join(T,'plugins','RtpComparisonProbe.jar'),path.join(W,'plugins','RtpComparisonProbe.jar'));
  fs.cpSync(path.join(T,'plugins','bStats'),path.join(W,'plugins','bStats'),{recursive:true});
  const folder=path.join(W,'plugins',name==='dyrtp'?'DyRTP':'BetterRTP');
  let small,large;
  if(name==='dyrtp'){fs.copyFileSync(DYRTP,path.join(W,'plugins','DyRTP.jar'));fs.mkdirSync(folder);small=path.join(T,'dyrtp-small-config.yml');large=path.join(T,'dyrtp-large-config.yml');}
  else{fs.copyFileSync(BETTER,path.join(W,'plugins','BetterRTP.jar'));fs.cpSync(path.join(T,'plugins','BetterRTP'),folder,{recursive:true});for(const f of ['locations.yml','log.txt'])fs.rmSync(path.join(folder,f),{force:true});fs.rmSync(path.join(folder,'data'),{recursive:true,force:true});small=path.join(T,'betterrtp-small-config.yml');large=path.join(T,'betterrtp-large-config.yml');}
  fs.copyFileSync(small,path.join(folder,'config.yml'));
  let output='';const log=fs.createWriteStream(path.join(OUT,name+'-1.1.0-comparison.log'));
  const server=spawn('java',['-Xms256M','-Xmx768M','-jar','paper.jar','--nogui'],{cwd:W,windowsHide:true,stdio:['pipe','pipe','pipe']});
  const exited=new Promise(r=>server.once('exit',r));server.stdout.on('data',b=>{output+=b;log.write(b);});server.stderr.on('data',b=>{output+=b;log.write(b);});
  const cmd=t=>server.stdin.write(t+'\n');
  let client;
  try{
    await until(()=>output.includes('Done ('),'start',300000);await delay(8000);
    client=spawn('node',[path.join('C:/Users/alph/Documents/DyRTP/scripts/comparison','client.js')],{env:{...process.env,RTP_CLIENT_MODULES:MODULES,RTP_CLIENT_LOG:path.join(OUT,name+'-1.1.0-client.jsonl')},stdio:['pipe','pipe','pipe'],windowsHide:true});
    await until(()=>output.includes('DyRtpReview joined the game'),'client join',60000);await delay(15000);
    const run=async(world,count,label)=>{const from=output.length;cmd(`rtpcompare ${name} ${world} ${count} ${label}`);
      const line=await until(()=>clean(output.slice(from)).split('\n').find(l=>l.includes('COMPARE RESULT')&&l.includes('label='+label)),label,180000);
      const row={plugin:name,label};for(const m of line.matchAll(/(\w+)=([^\s]+)/g))row[m[1]]=m[2];results.push(row);console.log(line.slice(line.indexOf('COMPARE')));await delay(15000);};
    const reload=async(file)=>{fs.copyFileSync(file,path.join(folder,'config.yml'));const from=output.length;cmd(name==='dyrtp'?'dyrtp admin reload':'betterrtp:betterrtp reload');await delay(5000);console.log('reload: '+clean(output.slice(from)).trim().split('\n').slice(-1)[0]);};
    await run('benchmark',20,'small-first');await run('benchmark',20,'small-second');
    await reload(large);await run('benchmark',10,'large-first');
    await reload(small);await run('benchmark_nether',10,'nether');await run('benchmark_the_end',10,'end');
  }finally{
    try{client&&client.stdin.write('quit\n');}catch{}await delay(2000);try{client&&client.kill();}catch{}
    cmd('stop');await Promise.race([exited,delay(120000)]);if(server.exitCode===null)server.kill();log.end();
  }
}
(async()=>{
  await phase('dyrtp');await phase('betterrtp');
  fs.writeFileSync(path.join(OUT,'results-1.1.0.json'),JSON.stringify({date:new Date().toISOString(),dyrtp:DYRTP,betterrtp:BETTER,results},null,2));
  console.log('BENCH_OK '+results.length);
})().catch(e=>{console.error(e.stack);process.exitCode=1;});
