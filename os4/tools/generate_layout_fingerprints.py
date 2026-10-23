import pathlib,sys,hashlib,struct,json,argparse
p=argparse.ArgumentParser();p.add_argument('vendor_dir',type=pathlib.Path);a=p.parse_args()
r=pathlib.Path(__file__).resolve().parents[2];sys.path.insert(0,str(r/'os4/tools'))
from attest_elf import load_segments
sets=[('libapp.so','6f5274f435cc07aab165ce5315d1d35afab559a659afd6200379d309c9d08b91','layout_runtime_fingerprints.h',{
'ld_delete_item_words':(0x917d00,132),'ld_save_batch_words':(0x8eecc0,224),'ld_save_single_words':(0x8e2128,132),'ld_load_items_words':(0x15e6f6c,9476),'ld_widget_lattice_words':(0x18c4568,96),'ld_filter_words':(0x8d26c0,728),'ld_process_screen_words':(0x8eefdc,2192),'ld_no_vacant_words':(0x8eb92c,48)}),
('libapp.so','6f5274f435cc07aab165ce5315d1d35afab559a659afd6200379d309c9d08b91','rotation_fingerprint.h',{'ld_orientation_words':(0x8ee38c,1336)}),
('libapp_launcher.so','f70a2d15505abc83bbc2f03cbe9e90b635a8add6d89ae0fef764100aea1c6d8b','rust_orientation_fingerprint.h',{'ld_rust_orientation_words':(0x90ffa0,0x911890-0x90ffa0)})]
dart_entries=[]
for lib,digest,header,targets in sets:
 b=(a.vendor_dir/lib).read_bytes();assert hashlib.sha256(b).hexdigest()==digest
 out=['/* Exact fuxi 8583 whole-function bytes; generated from SHA-256 pinned ELF. */\n']; entries=[]
 for name,(v,size) in targets.items():
  offset,=[o+v-base for flags,base,o,n in load_segments(b) if flags&1 and base<=v and v+size<=base+n]
  body=b[offset:offset+size];sha=hashlib.sha256(body).hexdigest();words=struct.unpack('<'+str(size//4)+'I',body)
  out.append('/* '+hex(v)+' SHA256 '+sha+' */\nstatic const uint32_t '+name+'[] = {\n'+''.join('0x%08xu,%s'%(w,'\n' if i%6==5 else ' ') for i,w in enumerate(words))+'\n};\n')
  entries.append(dict(name=name,vaddr=hex(v),size=size,sha256=sha))
 (r/'os4/native'/header).write_text('\n'.join(line.rstrip() for line in ''.join(out).splitlines())+'\n')
 if lib=='libapp.so':dart_entries.extend(entries)
 else:(r/'os4/profiles/fuxi-8583-rust-layout.json').write_text(json.dumps(dict(elf_sha256=digest,targets=entries),indent=2)+'\n')
 print(header, len(entries),'verified functions')


profile=r/'os4/profiles/fuxi-8.01.02.8583.json'
base=json.loads(profile.read_text())
base['targets']=[t for t in base['targets'] if t['name'] in ['PadRowColHandler.handle','CellLayoutGetxController.changeCellCount']]+dart_entries
profile.write_text(json.dumps(base,indent=2)+'\n')
