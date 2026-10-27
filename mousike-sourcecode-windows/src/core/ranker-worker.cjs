'use strict';
const {parentPort}=require('node:worker_threads');
const {rank,musicFirst,homeFeed}=require('./algorithms.cjs');
parentPort.on('message',({id,state,currentId})=>{try{const candidates=state.tracks.slice(0,3000),current=state.tracks.find(t=>t.id===currentId),ranked=musicFirst(rank(candidates,state.tracks,state.likedSongs,state.history,state.feedback,state.prefs,current),state.prefs.musicFirst);parentPort.postMessage({id,result:{ranked,feed:homeFeed(ranked,state.albums,state.prefs)}});}catch(e){parentPort.postMessage({id,error:e.message});}});
