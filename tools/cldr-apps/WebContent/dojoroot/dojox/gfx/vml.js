/*
	Copyright (c) 2004-2010, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.gfx.vml"]){
dojo._hasResource["dojox.gfx.vml"]=true;
dojo.provide("dojox.gfx.vml");
dojo.require("dojox.gfx._base");
dojo.require("dojox.gfx.shape");
dojo.require("dojox.gfx.path");
dojo.require("dojox.gfx.arc");
dojo.require("dojox.gfx.gradient");
(function(){
var d=dojo,g=dojox.gfx,m=g.matrix,gs=g.shape,_1=g.vml;
_1.xmlns="urn:schemas-microsoft-com:vml";
_1.text_alignment={start:"left",middle:"center",end:"right"};
_1._parseFloat=function(_2){
return _2.match(/^\d+f$/i)?parseInt(_2)/65536:parseFloat(_2);
};
_1._bool={"t":1,"true":1};
d.extend(g.Shape,{setFill:function(_3){
if(!_3){
this.fillStyle=null;
this.rawNode.filled="f";
return this;
}
var i,f,fo,a,s;
if(typeof _3=="object"&&"type" in _3){
switch(_3.type){
case "linear":
var _4=this._getRealMatrix(),_5=this.getBoundingBox(),_6=this._getRealBBox?this._getRealBBox():this.getTransformedBoundingBox();
s=[];
if(this.fillStyle!==_3){
this.fillStyle=g.makeParameters(g.defaultLinearGradient,_3);
}
f=g.gradient.project(_4,this.fillStyle,{x:_5.x,y:_5.y},{x:_5.x+_5.width,y:_5.y+_5.height},_6[0],_6[2]);
a=f.colors;
if(a[0].offset.toFixed(5)!="0.00000"){
s.push("0 "+g.normalizeColor(a[0].color).toHex());
}
for(i=0;i<a.length;++i){
s.push(a[i].offset.toFixed(5)+" "+g.normalizeColor(a[i].color).toHex());
}
i=a.length-1;
if(a[i].offset.toFixed(5)!="1.00000"){
s.push("1 "+g.normalizeColor(a[i].color).toHex());
}
fo=this.rawNode.fill;
fo.colors.value=s.join(";");
fo.method="sigma";
fo.type="gradient";
fo.angle=(270-m._radToDeg(f.angle))%360;
fo.on=true;
break;
case "radial":
f=g.makeParameters(g.defaultRadialGradient,_3);
this.fillStyle=f;
var l=parseFloat(this.rawNode.style.left),t=parseFloat(this.rawNode.style.top),w=parseFloat(this.rawNode.style.width),h=parseFloat(this.rawNode.style.height),c=isNaN(w)?1:2*f.r/w;
a=[];
if(f.colors[0].offset>0){
a.push({offset:1,color:g.normalizeColor(f.colors[0].color)});
}
d.forEach(f.colors,function(v,i){
a.push({offset:1-v.offset*c,color:g.normalizeColor(v.color)});
});
i=a.length-1;
while(i>=0&&a[i].offset<0){
--i;
}
if(i<a.length-1){
var q=a[i],p=a[i+1];
p.color=d.blendColors(q.color,p.color,q.offset/(q.offset-p.offset));
p.offset=0;
while(a.length-i>2){
a.pop();
}
}
i=a.length-1,s=[];
if(a[i].offset>0){
s.push("0 "+a[i].color.toHex());
}
for(;i>=0;--i){
s.push(a[i].offset.toFixed(5)+" "+a[i].color.toHex());
}
fo=this.rawNode.fill;
fo.colors.value=s.join(";");
fo.method="sigma";
fo.type="gradientradial";
if(isNaN(w)||isNaN(h)||isNaN(l)||isNaN(t)){
fo.focusposition="0.5 0.5";
}else{
fo.focusposition=((f.cx-l)/w).toFixed(5)+" "+((f.cy-t)/h).toFixed(5);
}
fo.focussize="0 0";
fo.on=true;
break;
case "pattern":
f=g.makeParameters(g.defaultPattern,_3);
this.fillStyle=f;
fo=this.rawNode.fill;
fo.type="tile";
fo.src=f.src;
if(f.width&&f.height){
fo.size.x=g.px2pt(f.width);
fo.size.y=g.px2pt(f.height);
}
fo.alignShape="f";
fo.position.x=0;
fo.position.y=0;
fo.origin.x=f.width?f.x/f.width:0;
fo.origin.y=f.height?f.y/f.height:0;
fo.on=true;
break;
}
this.rawNode.fill.opacity=1;
return this;
}
this.fillStyle=g.normalizeColor(_3);
fo=this.rawNode.fill;
if(!fo){
fo=this.rawNode.ownerDocument.createElement("v:fill");
}
fo.method="any";
fo.type="solid";
fo.opacity=this.fillStyle.a;
this.rawNode.fillcolor=this.fillStyle.toHex();
this.rawNode.filled=true;
return this;
},setStroke:function(_7){
if(!_7){
this.strokeStyle=null;
this.rawNode.stroked="f";
return this;
}
if(typeof _7=="string"||d.isArray(_7)||_7 instanceof d.Color){
_7={color:_7};
}
var s=this.strokeStyle=g.makeParameters(g.defaultStroke,_7);
s.color=g.normalizeColor(s.color);
var rn=this.rawNode;
rn.stroked=true;
rn.strokecolor=s.color.toCss();
rn.strokeweight=s.width+"px";
if(rn.stroke){
rn.stroke.opacity=s.color.a;
rn.stroke.endcap=this._translate(this._capMap,s.cap);
if(typeof s.join=="number"){
rn.stroke.joinstyle="miter";
rn.stroke.miterlimit=s.join;
}else{
rn.stroke.joinstyle=s.join;
}
rn.stroke.dashstyle=s.style=="none"?"Solid":s.style;
}
return this;
},_capMap:{butt:"flat"},_capMapReversed:{flat:"butt"},_translate:function(_8,_9){
return (_9 in _8)?_8[_9]:_9;
},_applyTransform:function(){
var _a=this._getRealMatrix();
if(_a){
var _b=this.rawNode.skew;
if(typeof _b=="undefined"){
for(var i=0;i<this.rawNode.childNodes.length;++i){
if(this.rawNode.childNodes[i].tagName=="skew"){
_b=this.rawNode.childNodes[i];
break;
}
}
}
if(_b){
_b.on="f";
var mt=_a.xx.toFixed(8)+" "+_a.xy.toFixed(8)+" "+_a.yx.toFixed(8)+" "+_a.yy.toFixed(8)+" 0 0",_c=Math.floor(_a.dx).toFixed()+"px "+Math.floor(_a.dy).toFixed()+"px",s=this.rawNode.style,l=parseFloat(s.left),t=parseFloat(s.top),w=parseFloat(s.width),h=parseFloat(s.height);
if(isNaN(l)){
l=0;
}
if(isNaN(t)){
t=0;
}
if(isNaN(w)||!w){
w=1;
}
if(isNaN(h)||!h){
h=1;
}
var _d=(-l/w-0.5).toFixed(8)+" "+(-t/h-0.5).toFixed(8);
_b.matrix=mt;
_b.origin=_d;
_b.offset=_c;
_b.on=true;
}
}
if(this.fillStyle&&this.fillStyle.type=="linear"){
this.setFill(this.fillStyle);
}
return this;
},_setDimensions:function(_e,_f){
return this;
},setRawNode:function(_10){
_10.stroked="f";
_10.filled="f";
this.rawNode=_10;
},_moveToFront:function(){
this.rawNode.parentNode.appendChild(this.rawNode);
return this;
},_moveToBack:function(){
var r=this.rawNode,p=r.parentNode,n=p.firstChild;
p.insertBefore(r,n);
if(n.tagName=="rect"){
n.swapNode(r);
}
return this;
},_getRealMatrix:function(){
return this.parentMatrix?new g.Matrix2D([this.parentMatrix,this.matrix]):this.matrix;
}});
dojo.declare("dojox.gfx.Group",g.Shape,{constructor:function(){
_1.Container._init.call(this);
},_applyTransform:function(){
var _11=this._getRealMatrix();
for(var i=0;i<this.children.length;++i){
this.children[i]._updateParentMatrix(_11);
}
return this;
},_setDimensions:function(_12,_13){
var r=this.rawNode,rs=r.style,bs=this.bgNode.style;
rs.width=_12;
rs.height=_13;
r.coordsize=_12+" "+_13;
bs.width=_12;
bs.height=_13;
for(var i=0;i<this.children.length;++i){
this.children[i]._setDimensions(_12,_13);
}
return this;
}});
g.Group.nodeType="group";
dojo.declare("dojox.gfx.Rect",gs.Rect,{setShape:function(_14){
var _15=this.shape=g.makeParameters(this.shape,_14);
this.bbox=null;
var r=Math.min(1,(_15.r/Math.min(parseFloat(_15.width),parseFloat(_15.height)))).toFixed(8);
var _16=this.rawNode.parentNode,_17=null;
if(_16){
if(_16.lastChild!==this.rawNode){
for(var i=0;i<_16.childNodes.length;++i){
if(_16.childNodes[i]===this.rawNode){
_17=_16.childNodes[i+1];
break;
}
}
}
_16.removeChild(this.rawNode);
}
if(d.isIE>7){
var _18=this.rawNode.ownerDocument.createElement("v:roundrect");
_18.arcsize=r;
_18.style.display="inline-block";
this.rawNode=_18;
}else{
this.rawNode.arcsize=r;
}
if(_16){
if(_17){
_16.insertBefore(this.rawNode,_17);
}else{
_16.appendChild(this.rawNode);
}
}
var _19=this.rawNode.style;
_19.left=_15.x.toFixed();
_19.top=_15.y.toFixed();
_19.width=(typeof _15.width=="string"&&_15.width.indexOf("%")>=0)?_15.width:_15.width.toFixed();
_19.height=(typeof _15.width=="string"&&_15.height.indexOf("%")>=0)?_15.height:_15.height.toFixed();
return this.setTransform(this.matrix).setFill(this.fillStyle).setStroke(this.strokeStyle);
}});
g.Rect.nodeType="roundrect";
dojo.declare("dojox.gfx.Ellipse",gs.Ellipse,{setShape:function(_1a){
var _1b=this.shape=g.makeParameters(this.shape,_1a);
this.bbox=null;
var _1c=this.rawNode.style;
_1c.left=(_1b.cx-_1b.rx).toFixed();
_1c.top=(_1b.cy-_1b.ry).toFixed();
_1c.width=(_1b.rx*2).toFixed();
_1c.height=(_1b.ry*2).toFixed();
return this.setTransform(this.matrix);
}});
g.Ellipse.nodeType="oval";
dojo.declare("dojox.gfx.Circle",gs.Circle,{setShape:function(_1d){
var _1e=this.shape=g.makeParameters(this.shape,_1d);
this.bbox=null;
var _1f=this.rawNode.style;
_1f.left=(_1e.cx-_1e.r).toFixed();
_1f.top=(_1e.cy-_1e.r).toFixed();
_1f.width=(_1e.r*2).toFixed();
_1f.height=(_1e.r*2).toFixed();
return this;
}});
g.Circle.nodeType="oval";
dojo.declare("dojox.gfx.Line",gs.Line,{constructor:function(_20){
if(_20){
_20.setAttribute("dojoGfxType","line");
}
},setShape:function(_21){
var _22=this.shape=g.makeParameters(this.shape,_21);
this.bbox=null;
this.rawNode.path.v="m"+_22.x1.toFixed()+" "+_22.y1.toFixed()+"l"+_22.x2.toFixed()+" "+_22.y2.toFixed()+"e";
return this.setTransform(this.matrix);
}});
g.Line.nodeType="shape";
dojo.declare("dojox.gfx.Polyline",gs.Polyline,{constructor:function(_23){
if(_23){
_23.setAttribute("dojoGfxType","polyline");
}
},setShape:function(_24,_25){
if(_24&&_24 instanceof Array){
this.shape=g.makeParameters(this.shape,{points:_24});
if(_25&&this.shape.points.length){
this.shape.points.push(this.shape.points[0]);
}
}else{
this.shape=g.makeParameters(this.shape,_24);
}
this.bbox=null;
this._normalizePoints();
var _26=[],p=this.shape.points;
if(p.length>0){
_26.push("m");
_26.push(p[0].x.toFixed(),p[0].y.toFixed());
if(p.length>1){
_26.push("l");
for(var i=1;i<p.length;++i){
_26.push(p[i].x.toFixed(),p[i].y.toFixed());
}
}
}
_26.push("e");
this.rawNode.path.v=_26.join(" ");
return this.setTransform(this.matrix);
}});
g.Polyline.nodeType="shape";
dojo.declare("dojox.gfx.Image",gs.Image,{setShape:function(_27){
var _28=this.shape=g.makeParameters(this.shape,_27);
this.bbox=null;
this.rawNode.firstChild.src=_28.src;
return this.setTransform(this.matrix);
},_applyTransform:function(){
var _29=this._getRealMatrix(),_2a=this.rawNode,s=_2a.style,_2b=this.shape;
if(_29){
_29=m.multiply(_29,{dx:_2b.x,dy:_2b.y});
}else{
_29=m.normalize({dx:_2b.x,dy:_2b.y});
}
if(_29.xy==0&&_29.yx==0&&_29.xx>0&&_29.yy>0){
s.filter="";
s.width=Math.floor(_29.xx*_2b.width);
s.height=Math.floor(_29.yy*_2b.height);
s.left=Math.floor(_29.dx);
s.top=Math.floor(_29.dy);
}else{
var ps=_2a.parentNode.style;
s.left="0px";
s.top="0px";
s.width=ps.width;
s.height=ps.height;
_29=m.multiply(_29,{xx:_2b.width/parseInt(s.width),yy:_2b.height/parseInt(s.height)});
var f=_2a.filters["DXImageTransform.Microsoft.Matrix"];
if(f){
f.M11=_29.xx;
f.M12=_29.xy;
f.M21=_29.yx;
f.M22=_29.yy;
f.Dx=_29.dx;
f.Dy=_29.dy;
}else{
s.filter="progid:DXImageTransform.Microsoft.Matrix(M11="+_29.xx+", M12="+_29.xy+", M21="+_29.yx+", M22="+_29.yy+", Dx="+_29.dx+", Dy="+_29.dy+")";
}
}
return this;
},_setDimensions:function(_2c,_2d){
var r=this.rawNode,f=r.filters["DXImageTransform.Microsoft.Matrix"];
if(f){
var s=r.style;
s.width=_2c;
s.height=_2d;
return this._applyTransform();
}
return this;
}});
g.Image.nodeType="rect";
dojo.declare("dojox.gfx.Text",gs.Text,{constructor:function(_2e){
if(_2e){
_2e.setAttribute("dojoGfxType","text");
}
this.fontStyle=null;
},_alignment:{start:"left",middle:"center",end:"right"},setShape:function(_2f){
this.shape=g.makeParameters(this.shape,_2f);
this.bbox=null;
var r=this.rawNode,s=this.shape,x=s.x,y=s.y.toFixed(),_30;
switch(s.align){
case "middle":
x-=5;
break;
case "end":
x-=10;
break;
}
_30="m"+x.toFixed()+","+y+"l"+(x+10).toFixed()+","+y+"e";
var p=null,t=null,c=r.childNodes;
for(var i=0;i<c.length;++i){
var tag=c[i].tagName;
if(tag=="path"){
p=c[i];
if(t){
break;
}
}else{
if(tag=="textpath"){
t=c[i];
if(p){
break;
}
}
}
}
if(!p){
p=r.ownerDocument.createElement("v:path");
r.appendChild(p);
}
if(!t){
t=r.ownerDocument.createElement("v:textpath");
r.appendChild(t);
}
p.v=_30;
p.textPathOk=true;
t.on=true;
var a=_1.text_alignment[s.align];
t.style["v-text-align"]=a?a:"left";
t.style["text-decoration"]=s.decoration;
t.style["v-rotate-letters"]=s.rotated;
t.style["v-text-kern"]=s.kerning;
t.string=s.text;
return this.setTransform(this.matrix);
},_setFont:function(){
var f=this.fontStyle,c=this.rawNode.childNodes;
for(var i=0;i<c.length;++i){
if(c[i].tagName=="textpath"){
c[i].style.font=g.makeFontString(f);
break;
}
}
this.setTransform(this.matrix);
},_getRealMatrix:function(){
var _31=g.Shape.prototype._getRealMatrix.call(this);
if(_31){
_31=m.multiply(_31,{dy:-g.normalizedLength(this.fontStyle?this.fontStyle.size:"10pt")*0.35});
}
return _31;
},getTextWidth:function(){
var _32=this.rawNode,_33=_32.style.display;
_32.style.display="inline";
var _34=g.pt2px(parseFloat(_32.currentStyle.width));
_32.style.display=_33;
return _34;
}});
g.Text.nodeType="shape";
dojo.declare("dojox.gfx.Path",g.path.Path,{constructor:function(_35){
if(_35&&!_35.getAttribute("dojoGfxType")){
_35.setAttribute("dojoGfxType","path");
}
this.vmlPath="";
this.lastControl={};
},_updateWithSegment:function(_36){
var _37=d.clone(this.last);
g.Path.superclass._updateWithSegment.apply(this,arguments);
if(arguments.length>1){
return;
}
var _38=this[this.renderers[_36.action]](_36,_37);
if(typeof this.vmlPath=="string"){
this.vmlPath+=_38.join("");
this.rawNode.path.v=this.vmlPath+" r0,0 e";
}else{
Array.prototype.push.apply(this.vmlPath,_38);
}
},setShape:function(_39){
this.vmlPath=[];
this.lastControl.type="";
g.Path.superclass.setShape.apply(this,arguments);
this.vmlPath=this.vmlPath.join("");
this.rawNode.path.v=this.vmlPath+" r0,0 e";
return this;
},_pathVmlToSvgMap:{m:"M",l:"L",t:"m",r:"l",c:"C",v:"c",qb:"Q",x:"z",e:""},renderers:{M:"_moveToA",m:"_moveToR",L:"_lineToA",l:"_lineToR",H:"_hLineToA",h:"_hLineToR",V:"_vLineToA",v:"_vLineToR",C:"_curveToA",c:"_curveToR",S:"_smoothCurveToA",s:"_smoothCurveToR",Q:"_qCurveToA",q:"_qCurveToR",T:"_qSmoothCurveToA",t:"_qSmoothCurveToR",A:"_arcTo",a:"_arcTo",Z:"_closePath",z:"_closePath"},_addArgs:function(_3a,_3b,_3c,_3d){
var n=_3b instanceof Array?_3b:_3b.args;
for(var i=_3c;i<_3d;++i){
_3a.push(" ",n[i].toFixed());
}
},_adjustRelCrd:function(_3e,_3f,_40){
var n=_3f instanceof Array?_3f:_3f.args,l=n.length,_41=new Array(l),i=0,x=_3e.x,y=_3e.y;
if(typeof x!="number"){
_41[0]=x=n[0];
_41[1]=y=n[1];
i=2;
}
if(typeof _40=="number"&&_40!=2){
var j=_40;
while(j<=l){
for(;i<j;i+=2){
_41[i]=x+n[i];
_41[i+1]=y+n[i+1];
}
x=_41[j-2];
y=_41[j-1];
j+=_40;
}
}else{
for(;i<l;i+=2){
_41[i]=(x+=n[i]);
_41[i+1]=(y+=n[i+1]);
}
}
return _41;
},_adjustRelPos:function(_42,_43){
var n=_43 instanceof Array?_43:_43.args,l=n.length,_44=new Array(l);
for(var i=0;i<l;++i){
_44[i]=(_42+=n[i]);
}
return _44;
},_moveToA:function(_45){
var p=[" m"],n=_45 instanceof Array?_45:_45.args,l=n.length;
this._addArgs(p,n,0,2);
if(l>2){
p.push(" l");
this._addArgs(p,n,2,l);
}
this.lastControl.type="";
return p;
},_moveToR:function(_46,_47){
return this._moveToA(this._adjustRelCrd(_47,_46));
},_lineToA:function(_48){
var p=[" l"],n=_48 instanceof Array?_48:_48.args;
this._addArgs(p,n,0,n.length);
this.lastControl.type="";
return p;
},_lineToR:function(_49,_4a){
return this._lineToA(this._adjustRelCrd(_4a,_49));
},_hLineToA:function(_4b,_4c){
var p=[" l"],y=" "+_4c.y.toFixed(),n=_4b instanceof Array?_4b:_4b.args,l=n.length;
for(var i=0;i<l;++i){
p.push(" ",n[i].toFixed(),y);
}
this.lastControl.type="";
return p;
},_hLineToR:function(_4d,_4e){
return this._hLineToA(this._adjustRelPos(_4e.x,_4d),_4e);
},_vLineToA:function(_4f,_50){
var p=[" l"],x=" "+_50.x.toFixed(),n=_4f instanceof Array?_4f:_4f.args,l=n.length;
for(var i=0;i<l;++i){
p.push(x," ",n[i].toFixed());
}
this.lastControl.type="";
return p;
},_vLineToR:function(_51,_52){
return this._vLineToA(this._adjustRelPos(_52.y,_51),_52);
},_curveToA:function(_53){
var p=[],n=_53 instanceof Array?_53:_53.args,l=n.length,lc=this.lastControl;
for(var i=0;i<l;i+=6){
p.push(" c");
this._addArgs(p,n,i,i+6);
}
lc.x=n[l-4];
lc.y=n[l-3];
lc.type="C";
return p;
},_curveToR:function(_54,_55){
return this._curveToA(this._adjustRelCrd(_55,_54,6));
},_smoothCurveToA:function(_56,_57){
var p=[],n=_56 instanceof Array?_56:_56.args,l=n.length,lc=this.lastControl,i=0;
if(lc.type!="C"){
p.push(" c");
this._addArgs(p,[_57.x,_57.y],0,2);
this._addArgs(p,n,0,4);
lc.x=n[0];
lc.y=n[1];
lc.type="C";
i=4;
}
for(;i<l;i+=4){
p.push(" c");
this._addArgs(p,[2*_57.x-lc.x,2*_57.y-lc.y],0,2);
this._addArgs(p,n,i,i+4);
lc.x=n[i];
lc.y=n[i+1];
}
return p;
},_smoothCurveToR:function(_58,_59){
return this._smoothCurveToA(this._adjustRelCrd(_59,_58,4),_59);
},_qCurveToA:function(_5a){
var p=[],n=_5a instanceof Array?_5a:_5a.args,l=n.length,lc=this.lastControl;
for(var i=0;i<l;i+=4){
p.push(" qb");
this._addArgs(p,n,i,i+4);
}
lc.x=n[l-4];
lc.y=n[l-3];
lc.type="Q";
return p;
},_qCurveToR:function(_5b,_5c){
return this._qCurveToA(this._adjustRelCrd(_5c,_5b,4));
},_qSmoothCurveToA:function(_5d,_5e){
var p=[],n=_5d instanceof Array?_5d:_5d.args,l=n.length,lc=this.lastControl,i=0;
if(lc.type!="Q"){
p.push(" qb");
this._addArgs(p,[lc.x=_5e.x,lc.y=_5e.y],0,2);
lc.type="Q";
this._addArgs(p,n,0,2);
i=2;
}
for(;i<l;i+=2){
p.push(" qb");
this._addArgs(p,[lc.x=2*_5e.x-lc.x,lc.y=2*_5e.y-lc.y],0,2);
this._addArgs(p,n,i,i+2);
}
return p;
},_qSmoothCurveToR:function(_5f,_60){
return this._qSmoothCurveToA(this._adjustRelCrd(_60,_5f,2),_60);
},_arcTo:function(_61,_62){
var p=[],n=_61.args,l=n.length,_63=_61.action=="a";
for(var i=0;i<l;i+=7){
var x1=n[i+5],y1=n[i+6];
if(_63){
x1+=_62.x;
y1+=_62.y;
}
var _64=g.arc.arcAsBezier(_62,n[i],n[i+1],n[i+2],n[i+3]?1:0,n[i+4]?1:0,x1,y1);
for(var j=0;j<_64.length;++j){
p.push(" c");
var t=_64[j];
this._addArgs(p,t,0,t.length);
this._updateBBox(t[0],t[1]);
this._updateBBox(t[2],t[3]);
this._updateBBox(t[4],t[5]);
}
_62.x=x1;
_62.y=y1;
}
this.lastControl.type="";
return p;
},_closePath:function(){
this.lastControl.type="";
return ["x"];
}});
g.Path.nodeType="shape";
dojo.declare("dojox.gfx.TextPath",g.Path,{constructor:function(_65){
if(_65){
_65.setAttribute("dojoGfxType","textpath");
}
this.fontStyle=null;
if(!("text" in this)){
this.text=d.clone(g.defaultTextPath);
}
if(!("fontStyle" in this)){
this.fontStyle=d.clone(g.defaultFont);
}
},setText:function(_66){
this.text=g.makeParameters(this.text,typeof _66=="string"?{text:_66}:_66);
this._setText();
return this;
},setFont:function(_67){
this.fontStyle=typeof _67=="string"?g.splitFontString(_67):g.makeParameters(g.defaultFont,_67);
this._setFont();
return this;
},_setText:function(){
this.bbox=null;
var r=this.rawNode,s=this.text,p=null,t=null,c=r.childNodes;
for(var i=0;i<c.length;++i){
var tag=c[i].tagName;
if(tag=="path"){
p=c[i];
if(t){
break;
}
}else{
if(tag=="textpath"){
t=c[i];
if(p){
break;
}
}
}
}
if(!p){
p=this.rawNode.ownerDocument.createElement("v:path");
r.appendChild(p);
}
if(!t){
t=this.rawNode.ownerDocument.createElement("v:textpath");
r.appendChild(t);
}
p.textPathOk=true;
t.on=true;
var a=_1.text_alignment[s.align];
t.style["v-text-align"]=a?a:"left";
t.style["text-decoration"]=s.decoration;
t.style["v-rotate-letters"]=s.rotated;
t.style["v-text-kern"]=s.kerning;
t.string=s.text;
},_setFont:function(){
var f=this.fontStyle,c=this.rawNode.childNodes;
for(var i=0;i<c.length;++i){
if(c[i].tagName=="textpath"){
c[i].style.font=g.makeFontString(f);
break;
}
}
}});
g.TextPath.nodeType="shape";
dojo.declare("dojox.gfx.Surface",gs.Surface,{constructor:function(){
_1.Container._init.call(this);
},setDimensions:function(_68,_69){
this.width=g.normalizedLength(_68);
this.height=g.normalizedLength(_69);
if(!this.rawNode){
return this;
}
var cs=this.clipNode.style,r=this.rawNode,rs=r.style,bs=this.bgNode.style,ps=this._parent.style,i;
ps.width=_68;
ps.height=_69;
cs.width=_68;
cs.height=_69;
cs.clip="rect(0px "+_68+"px "+_69+"px 0px)";
rs.width=_68;
rs.height=_69;
r.coordsize=_68+" "+_69;
bs.width=_68;
bs.height=_69;
for(i=0;i<this.children.length;++i){
this.children[i]._setDimensions(_68,_69);
}
return this;
},getDimensions:function(){
var t=this.rawNode?{width:g.normalizedLength(this.rawNode.style.width),height:g.normalizedLength(this.rawNode.style.height)}:null;
if(t.width<=0){
t.width=this.width;
}
if(t.height<=0){
t.height=this.height;
}
return t;
}});
g.createSurface=function(_6a,_6b,_6c){
if(!_6b&&!_6c){
var pos=d.position(_6a);
_6b=_6b||pos.w;
_6c=_6c||pos.h;
}
if(typeof _6b=="number"){
_6b=_6b+"px";
}
if(typeof _6c=="number"){
_6c=_6c+"px";
}
var s=new g.Surface(),p=d.byId(_6a),c=s.clipNode=p.ownerDocument.createElement("div"),r=s.rawNode=p.ownerDocument.createElement("v:group"),cs=c.style,rs=r.style;
if(d.isIE>7){
rs.display="inline-block";
}
s._parent=p;
s._nodes.push(c);
p.style.width=_6b;
p.style.height=_6c;
cs.position="absolute";
cs.width=_6b;
cs.height=_6c;
cs.clip="rect(0px "+_6b+" "+_6c+" 0px)";
rs.position="absolute";
rs.width=_6b;
rs.height=_6c;
r.coordsize=(_6b==="100%"?_6b:parseFloat(_6b))+" "+(_6c==="100%"?_6c:parseFloat(_6c));
r.coordorigin="0 0";
var b=s.bgNode=r.ownerDocument.createElement("v:rect"),bs=b.style;
bs.left=bs.top=0;
bs.width=rs.width;
bs.height=rs.height;
b.filled=b.stroked="f";
r.appendChild(b);
c.appendChild(r);
p.appendChild(c);
s.width=g.normalizedLength(_6b);
s.height=g.normalizedLength(_6c);
return s;
};
_1.Container={_init:function(){
gs.Container._init.call(this);
},add:function(_6d){
if(this!=_6d.getParent()){
this.rawNode.appendChild(_6d.rawNode);
if(!_6d.getParent()){
_6d.setFill(_6d.getFill());
_6d.setStroke(_6d.getStroke());
}
gs.Container.add.apply(this,arguments);
}
return this;
},remove:function(_6e,_6f){
if(this==_6e.getParent()){
if(this.rawNode==_6e.rawNode.parentNode){
this.rawNode.removeChild(_6e.rawNode);
}
gs.Container.remove.apply(this,arguments);
}
return this;
},clear:function(){
var r=this.rawNode;
while(r.firstChild!=r.lastChild){
if(r.firstChild!=this.bgNode){
r.removeChild(r.firstChild);
}
if(r.lastChild!=this.bgNode){
r.removeChild(r.lastChild);
}
}
return gs.Container.clear.apply(this,arguments);
},_moveChildToFront:gs.Container._moveChildToFront,_moveChildToBack:gs.Container._moveChildToBack};
dojo.mixin(gs.Creator,{createGroup:function(){
var _70=this.createObject(g.Group,null);
var r=_70.rawNode.ownerDocument.createElement("v:rect");
r.style.left=r.style.top=0;
r.style.width=_70.rawNode.style.width;
r.style.height=_70.rawNode.style.height;
r.filled=r.stroked="f";
_70.rawNode.appendChild(r);
_70.bgNode=r;
return _70;
},createImage:function(_71){
if(!this.rawNode){
return null;
}
var _72=new g.Image(),doc=this.rawNode.ownerDocument,_73=doc.createElement("v:rect");
_73.stroked="f";
_73.style.width=this.rawNode.style.width;
_73.style.height=this.rawNode.style.height;
var img=doc.createElement("v:imagedata");
_73.appendChild(img);
_72.setRawNode(_73);
this.rawNode.appendChild(_73);
_72.setShape(_71);
this.add(_72);
return _72;
},createRect:function(_74){
if(!this.rawNode){
return null;
}
var _75=new g.Rect,_76=this.rawNode.ownerDocument.createElement("v:roundrect");
if(d.isIE>7){
_76.style.display="inline-block";
}
_75.setRawNode(_76);
this.rawNode.appendChild(_76);
_75.setShape(_74);
this.add(_75);
return _75;
},createObject:function(_77,_78){
if(!this.rawNode){
return null;
}
var _79=new _77(),_7a=this.rawNode.ownerDocument.createElement("v:"+_77.nodeType);
_79.setRawNode(_7a);
this.rawNode.appendChild(_7a);
switch(_77){
case g.Group:
case g.Line:
case g.Polyline:
case g.Image:
case g.Text:
case g.Path:
case g.TextPath:
this._overrideSize(_7a);
}
_79.setShape(_78);
this.add(_79);
return _79;
},_overrideSize:function(_7b){
var s=this.rawNode.style,w=s.width,h=s.height;
_7b.style.width=w;
_7b.style.height=h;
_7b.coordsize=parseInt(w)+" "+parseInt(h);
}});
d.extend(g.Group,_1.Container);
d.extend(g.Group,gs.Creator);
d.extend(g.Surface,_1.Container);
d.extend(g.Surface,gs.Creator);
})();
}
