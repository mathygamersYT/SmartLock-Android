import { readFileSync } from 'node:fs';
import { test, before, after, beforeEach } from 'node:test';
import { initializeTestEnvironment, assertSucceeds, assertFails } from '@firebase/rules-unit-testing';
import { doc, getDoc, getDocs, collection, setDoc, updateDoc, deleteDoc, writeBatch, serverTimestamp, runTransaction } from 'firebase/firestore';
import assert from 'node:assert/strict';
let env;
const projectId='demo-smartlock';
const id='0123456789abcdef0123456789abcdef';
const database = uid => uid ? env.authenticatedContext(uid).firestore() : env.unauthenticatedContext().firestore();
const event = (actor='sim', result='PERMITIDO', reason='ACTIVA', uid='04A39C21') => ({actorUid:actor,uidRFID:uid,resultado:result,motivo:reason,origen:'SIMULACION',dispositivoId:actor,fecha:serverTimestamp()});
before(async()=>{env=await initializeTestEnvironment({projectId,firestore:{host:'127.0.0.1',port:8085,rules:readFileSync('../../firestore.rules','utf8')}})});
after(async()=>{await env?.cleanup()});
beforeEach(async()=>{
 await env.clearFirestore();
 await env.withSecurityRulesDisabled(async context=>{
  const db=context.firestore();
  await setDoc(doc(db,'administradores/admin'),{activo:true});
  await setDoc(doc(db,'simuladores/sim'),{activo:true});
  await setDoc(doc(db,'tarjetas/04A39C21'),{nombre:'Valentina',habilitada:true,fechaCreacion:new Date(),actualizadaEn:new Date(),actualizadaPor:'admin',ultimaOperacion:'abcdefghijklmnopqrst'});
 });
});
test('anonymous and ordinary accounts cannot read cards or promote themselves',async()=>{
 await assertFails(getDoc(doc(database(null),'tarjetas/04A39C21')));
 await assertFails(getDoc(doc(database('ordinary'),'tarjetas/04A39C21')));
 await assertFails(setDoc(doc(database('ordinary'),'administradores/ordinary'),{activo:true}));
 await assertFails(setDoc(doc(database('ordinary'),'simuladores/ordinary'),{activo:true,habilitadoPor:'ordinary',fecha:serverTimestamp()}));
});
test('simulator can check known or unknown UID but cannot enumerate cards or read audit',async()=>{
 const db=database('sim');
 await assertSucceeds(getDoc(doc(db,'tarjetas/04A39C21')));
 await assertSucceeds(getDoc(doc(db,'tarjetas/FFFFFFFF')));
 await assertFails(getDocs(collection(db,'tarjetas')));
 await assertFails(getDocs(collection(db,'auditoria')));
});
test('simulator cannot modify cards, its role, or physical telemetry',async()=>{
 const db=database('sim');
 await assertFails(updateDoc(doc(db,'tarjetas/04A39C21'),{habilitada:false}));
 await assertFails(setDoc(doc(db,'simuladores/sim'),{activo:true,habilitadoPor:'sim',fecha:serverTimestamp()}));
 await assertFails(setDoc(doc(db,'eventos/fake'),event()));
 await assertFails(setDoc(doc(db,'dispositivos/puerta-principal'),{puerta:'ABIERTA'}));
});
test('allowed event is immutable, visible to admin, and not readable by another simulator',async()=>{
 const db=database('sim');
 await assertSucceeds(getDoc(doc(db,'eventosSimulados/'+id)));
 await assertSucceeds(setDoc(doc(db,'eventosSimulados/'+id),event()));
 await assertSucceeds(getDocs(collection(database('admin'),'eventosSimulados')));
 await assertFails(updateDoc(doc(db,'eventosSimulados/'+id),{resultado:'RECHAZADO'}));
 await assertFails(deleteDoc(doc(db,'eventosSimulados/'+id)));
 await assertFails(getDoc(doc(database('ordinary'),'eventosSimulados/'+id)));
});
test('server rejects a forged permission for revoked or unknown credentials',async()=>{
 await env.withSecurityRulesDisabled(c=>updateDoc(doc(c.firestore(),'tarjetas/04A39C21'),{habilitada:false}));
 await assertFails(setDoc(doc(database('sim'),'eventosSimulados/'+id),event()));
 await assertSucceeds(setDoc(doc(database('sim'),'eventosSimulados/'+id),event('sim','RECHAZADO','REVOCADA')));
 const unknownId='ffffffffffffffffffffffffffffffff';
 await assertFails(setDoc(doc(database('sim'),'eventosSimulados/'+unknownId),event('sim','PERMITIDO','ACTIVA','FFFFFFFF')));
 await assertSucceeds(setDoc(doc(database('sim'),'eventosSimulados/'+unknownId),event('sim','RECHAZADO','DESCONOCIDA','FFFFFFFF')));
});
test('server rejects actor, origin, timestamp and schema spoofing',async()=>{
 const db=database('sim');
 for(const changes of [{actorUid:'admin'},{origen:'FISICO'},{fecha:new Date(0)},{dispositivoId:'another'},{extra:true},{uidRFID:'../bad'}]){
  await assertFails(setDoc(doc(db,'eventosSimulados/'+id),{...event(),...changes}));
 }
});
test('admin can revoke only with matching audit in the same transaction',async()=>{
 const db=database('admin');
 await assertFails(updateDoc(doc(db,'tarjetas/04A39C21'),{habilitada:false,actualizadaEn:serverTimestamp(),actualizadaPor:'admin',ultimaOperacion:'zyxwvutsrqponmlkjihg'}));
 const batch=writeBatch(db);
 batch.update(doc(db,'tarjetas/04A39C21'),{habilitada:false,actualizadaEn:serverTimestamp(),actualizadaPor:'admin',ultimaOperacion:'zyxwvutsrqponmlkjihg'});
 batch.set(doc(db,'auditoria/zyxwvutsrqponmlkjihg'),{actorUid:'admin',tarjetaUid:'04A39C21',accion:'REVOCAR',fecha:serverTimestamp()});
 await assertSucceeds(batch.commit());
 await assertFails(setDoc(doc(database('sim'),'eventosSimulados/'+id),event()));
});
test('same transaction ID can recover an event without creating duplicates',async()=>{
 const db=database('sim');
 const send=()=>runTransaction(db,async tx=>{
  const ref=doc(db,'eventosSimulados/'+id); const old=await tx.get(ref);
  if(old.exists())return old.data().resultado;
  await tx.get(doc(db,'tarjetas/04A39C21')); tx.set(ref,event()); return 'PERMITIDO';
 });
 assert.equal(await send(),'PERMITIDO'); assert.equal(await send(),'PERMITIDO');
 assert.equal((await getDocs(collection(database('admin'),'eventosSimulados'))).size,1);
});
test('administrator can provision and revoke simulator, simulator cannot self-restore',async()=>{
 const ref=doc(database('admin'),'simuladores/sim');
 await assertSucceeds(setDoc(ref,{activo:false,habilitadoPor:'admin',fecha:serverTimestamp()}));
 await assertFails(setDoc(doc(database('sim'),'eventosSimulados/'+id),event()));
 await assertFails(setDoc(doc(database('sim'),'simuladores/sim'),{activo:true,habilitadoPor:'sim',fecha:serverTimestamp()}));
 await assertSucceeds(setDoc(ref,{activo:true,habilitadoPor:'admin',fecha:serverTimestamp()}));
 await assertSucceeds(setDoc(doc(database('sim'),'eventosSimulados/'+id),event()));
});