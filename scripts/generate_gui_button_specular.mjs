/* Compile-time resource source: PrismalAGSL v1.0.4 continuous corner geometry and 45-degree default specular (MIT).
 * Does NOT ship as runtime code. Run from repository root:
 *   node scripts/generate_gui_button_specular.mjs
 * 36..72dp widths (1dp steps), height 36dp, 3x pixel samples; wide buttons
 * stretch only the 2dp straight center strip of the 72dp frame.
 * Gaussian stroke blur is approximated by a 3-tap separable kernel offline.
 */
import {mkdirSync,writeFileSync} from "node:fs";
const SCALE=3,MIN_DP=36,MAX_DP=72,HEIGHT_DP=36;
function cornerBezier(tH,tV){
 const sq=Math.SQRT2,S=Math.sin(Math.PI/8),C=Math.cos(Math.PI/8),cot=1/Math.tan(Math.PI/8),s2=S*S,c2=C*C,s3=s2*S,c3=c2*C;
 const k0=27*(sq-6*C+6*sq*c2-4*c3)*cot+2*S*(-9+2*(sq-2*S)*s3+2*sq*C*(9+s2)-2*c2*(9+2*s2));
 const k1=-81*(-2+sq+4*(-1+sq)*C+2*(-2+sq)*c2)*cot-4*S*(-9+9*sq+sq*s3+(-2+sq)*C*(9+s2));
 const k2=9*(9*(-4+3*sq+(-6+4*sq)*C)*cot+(-6+4*sq)*S),k3=27*(10-7*sq)*cot;
 const cub=(a,b,c,d)=>{let f=((3*c/a)-(b*b)/(a*a))/3,g=((2*b*b*b)/(a*a*a)-(9*b*c)/(a*a)+(27*d)/a)/27,h=g*g/4+f*f*f/27;return Math.cbrt(-g/2+Math.sqrt(h))+Math.cbrt(-g/2-Math.sqrt(h))-b/(3*a)};
 const k3v=cub(k3,k2,k1-8*(2/3*tH)*s3*S,k0),k6v=cub(k3,k2,k1-8*(2/3*tV)*s3*S,k0);
 const half=(k,t)=>{let x3=1/sq+(-1/sq+S)/k,y3=1-1/sq+(1/sq-C)/k,x2=x3-y3*cot,x1=x2-1.5*k*y3*y3/s3,x0=-2/3*t;return {x3,y3,x2,x1,x0}};
 const h3=half(k3v,tH),h6=half(k6v,tV);
 const x6=1-h6.y3,y6=1-h6.x3,y7=1-h6.x2,y8=1-h6.x1,y9=1-h6.x0;
 const a=1.5*k3v,b=1.5*k6v,g=c2-s2,x36=x6-h3.x3,y36=y6-h3.y3,c=-(C*y36-S*x36),d=S*y36-C*x36;
 const pp=2*d/b,qq=g*g*g/(a*b*b),rr=(a*d*d+c*g*g)/(a*b*b),bb=-pp/2,cc=-rr,dd=rr*pp/2-qq*qq/8,ff=(3*cc-bb*bb)/3,gg=(2*bb**3-9*bb*cc+27*dd)/27,rm=Math.sqrt(-(ff**3)/27);
 const phi=Math.acos(Math.max(-1,Math.min(1,-gg/(2*rm)))),yy=2*Math.sqrt(-ff/3)*Math.cos(phi/3),z=yy-bb/3,u=Math.sqrt(2*z-pp);
 const l6=(u-Math.sqrt(u*u-4*(z+qq/(2*u))))/2,l3=(-d-b*l6*l6)/g;
 const x4=h3.x3+l3*C,y4=h3.y3+l3*S,x5=x6-l6*S,y5=y6-l6*C;
 const p=[h3.x0,0,h3.x1,0,h3.x2,0,h3.x3,h3.y3,x4,y4,x5,y5,x6,y6,1,y7,1,y8,1,y9];
 if(!p.every(Number.isFinite))throw Error("NaN source Bezier "+tH+","+tV);
 return p;
}
function sampleContour(w,h){
 const r=Math.min(w,h)/2,th=Math.max(0,Math.min(1,(w/2-r)/r)),tv=Math.max(0,Math.min(1,(h/2-r)/r)),p=cornerBezier(th,tv),pts=[];
 const N=18;
 for(let n=0;n<3;n++){
  const P=k=>[w-r+p[k*2]*r,p[k*2+1]*r];
  let A=P(n*3),B=P(n*3+1),C=P(n*3+2),D=P(n*3+3);
  for(let j=n===0?0:1;j<=N;j++){let t=j/N,u=1-t;pts.push([u**3*A[0]+3*u*u*t*B[0]+3*u*t*t*C[0]+t**3*D[0],u**3*A[1]+3*u*u*t*B[1]+3*u*t*t*C[1]+t**3*D[1]]);}
 }
 const q=(w===h?Array.from({length:73},(_,i)=>{let a=-Math.PI/2+(Math.PI/2)*i/72;return [w/2+r*Math.cos(a),h/2+r*Math.sin(a)]}):pts);
 const corners=[q,q.map(([x,y])=>[x,h-y]),q.map(([x,y])=>[w-x,h-y]),q.map(([x,y])=>[w-x,y])],segs=[];
 for(const points of corners)for(let k=1;k<points.length;k++){let [x1,y1]=points[k-1],[x2,y2]=points[k];segs.push([x1,y1,x2,y2,Math.min(x1,x2)-3,Math.max(x1,x2)+3,Math.min(y1,y2)-3,Math.max(y1,y2)+3]);}
 const topX=q[0][0],sideY=q[q.length-1][1];
 const lines=[[[w-topX,0],[topX,0]],[[topX,h],[w-topX,h]],[[0,sideY],[0,h-sideY]],[[w,sideY],[w,h-sideY]]];
 for(const [a,b] of lines){if(Math.hypot(a[0]-b[0],a[1]-b[1])<.01)continue;segs.push([a[0],a[1],b[0],b[1],Math.min(a[0],b[0])-3,Math.max(a[0],b[0])+3,Math.min(a[1],b[1])-3,Math.max(a[1],b[1])+3]);}
 const left=q.map(([x,y])=>[w-x,y]).sort((a,b)=>a[1]-b[1]);
 function leftBound(y){if(y>h/2)return leftBound(h-y);if(y>=r)return 0;if(y<=0)return left[0][0];let a=0,b=left.length-1;while(b-a>1){let m=(a+b)>>1;if(left[m][1]<y)a=m;else b=m}let t=(y-left[a][1])/(left[b][1]-left[a][1]||1);return left[a][0]+(left[b][0]-left[a][0])*t;}
 return {segs,leftBound};
}
function normalIntensity(px,py,w,h){
 const r=Math.min(w,h)/2,gr=Math.min(1.5*r,w/2,h/2),cx=px-w/2,cy=py-h/2,qx=Math.abs(cx)-(w/2-gr),qy=Math.abs(cy)-(h/2-gr),ox=Math.max(qx,0),oy=Math.max(qy,0);let gx,gy;
 if(qx>=0||qy>=0){let m=Math.hypot(ox,oy);gx=Math.sign(cx)*(m?ox/m:0);gy=Math.sign(cy)*(m?oy/m:0)}else{let v=qy<=qx?1:0;gx=Math.sign(cx)*v;gy=Math.sign(cy)*(1-v)}
 return Math.abs((gx+gy)*Math.SQRT1_2);
}
function paintRow(dp){
 const w=dp*SCALE,h=HEIGHT_DP*SCALE,{segs,leftBound}=sampleContour(w,h),tmp=new Float32Array(w*h),raw=new Float32Array(w*h),out=new Uint8Array(w*h),stroke=Math.ceil(.5*SCALE);
 for(let y=0;y<h;y++)for(let x=0;x<w;x++){
  const px=x+.5,py=y+.5,l=leftBound(py);if(px<l-.5||px>w-l+.5)continue;let dist=1e9;
  for(const e of segs){if(px<e[4]||px>e[5]||py<e[6]||py>e[7])continue;let dx=e[2]-e[0],dy=e[3]-e[1],v=Math.max(0,Math.min(1,((px-e[0])*dx+(py-e[1])*dy)/(dx*dx+dy*dy||1)));dist=Math.min(dist,Math.hypot(px-e[0]-v*dx,py-e[1]-v*dy));}
  if(dist>stroke+2)continue;
  let cov=Math.min(1,Math.max(0,px-l+.5,w-l-px+.5)),sc=Math.min(1,Math.max(0,stroke+.5-dist));
  raw[y*w+x]=127.5*normalIntensity(px,py,w,h)*cov*sc;
 }
 const kernel=[.125,.75,.125];for(let y=0;y<h;y++)for(let x=0;x<w;x++){let z=0;for(let k=-1;k<=1;k++)if(x+k>=0&&x+k<w)z+=raw[y*w+x+k]*kernel[k+1];tmp[y*w+x]=z;}
 for(let y=0;y<h;y++)for(let x=0;x<w;x++){let z=0;for(let k=-1;k<=1;k++)if(y+k>=0&&y+k<h)z+=tmp[(y+k)*w+x]*kernel[k+1];let l=leftBound(y+.5),cov=Math.min(1,Math.max(0,x+.5-l+.5,w-l-(x+.5)+.5));out[y*w+x]=Math.round(z*cov);}
 return out;
}
function writePNGIndexed(pixels,w,h){
 const table=new Uint32Array(256);for(let i=0;i<256;i++){let t=i;for(let k=0;k<8;k++)t=t&1?0xedb88320^(t>>>1):t>>>1;table[i]=t>>>0}
 const be=x=>[x>>>24&255,x>>>16&255,x>>>8&255,x&255];
 function chunk(tag,data){const b=Uint8Array.from([...tag].map(c=>c.charCodeAt(0)).concat([...data]));let crc=0xffffffff;for(const z of b)crc=table[(crc^z)&255]^(crc>>>8);return [...be(data.length),...b,...be((crc^0xffffffff)>>>0)]}
 let row=w+1,raw=new Uint8Array(h*row);for(let y=0;y<h;y++)raw.set(pixels.subarray(y*w,(y+1)*w),y*row+1);
 let z=[120,1];for(let i=0;i<raw.length;){let n=Math.min(65535,raw.length-i);z.push(i+n>=raw.length?1:0,n&255,n>>>8&255,(~n)&255,(~n)>>>8&255);for(let k=0;k<n;k++)z.push(raw[i+k]);i+=n;}
 let a=1,b=0;for(const v of raw){a=(a+v)%65521;b=(b+a)%65521}z.push(b>>>8&255,b&255,a>>>8&255,a&255);
 return Uint8Array.from([137,80,78,71,13,10,26,10,...chunk("IHDR",[...be(w),...be(h),8,3,0,0,0]),...chunk("PLTE",Array(768).fill(255)),...chunk("tRNS",Array.from({length:256},(_,i)=>i)),...chunk("IDAT",z),...chunk("IEND",[])]);
}
function makeAtlas(){
 const W=MAX_DP*SCALE,H=HEIGHT_DP*SCALE,fullH=(MAX_DP-MIN_DP+1)*H,pix=new Uint8Array(W*fullH),stats=[];
 for(let d=MIN_DP;d<=MAX_DP;d++){let p=paintRow(d),w=d*SCALE,row=(d-MIN_DP)*H;for(let y=0;y<H;y++)pix.set(p.subarray(y*w,(y+1)*w),(row+y)*W);let max=0,sum=0;for(const v of p){max=Math.max(max,v);sum+=v}stats.push({dp:d,max,sum})}
 return {bytes:writePNGIndexed(pix,W,fullH),stats,width:W,height:fullH};
}
const {bytes,stats,width,height}=makeAtlas();
mkdirSync("src/main/res/drawable-nodpi",{recursive:true});
writeFileSync("src/main/res/drawable-nodpi/gui_button_specular_atlas.png",bytes);
console.log("Prebaked GUI specular atlas",width,height,bytes.length,stats[0],stats.at(-1));
