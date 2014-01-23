//>>built
define("dojox/mdnd/AutoScroll",["dojo/_base/kernel","dojo/_base/declare","dojo/_base/lang","dojo/_base/connect","dojo/_base/sniff","dojo/ready","dojo/_base/window"],function(_1,_2,_3,_4,_5,_6){
var as=_2("dojox.mdnd.AutoScroll",null,{interval:3,recursiveTimer:10,marginMouse:50,constructor:function(){
this.resizeHandler=_4.connect(_1.global,"onresize",this,function(){
this.getViewport();
});
_6(_3.hitch(this,"init"));
},init:function(){
this._html=(_5("webkit"))?_1.body():_1.body().parentNode;
this.getViewport();
},getViewport:function(){
var d=_1.doc,dd=d.documentElement,w=window,b=_1.body();
if(_1.isMozilla){
this._v={"w":dd.clientWidth,"h":w.innerHeight};
}else{
if(!_1.isOpera&&w.innerWidth){
this._v={"w":w.innerWidth,"h":w.innerHeight};
}else{
if(!_1.isOpera&&dd&&dd.clientWidth){
this._v={"w":dd.clientWidth,"h":dd.clientHeight};
}else{
if(b.clientWidth){
this._v={"w":b.clientWidth,"h":b.clientHeight};
}
}
}
}
},setAutoScrollNode:function(_7){
this._node=_7;
},setAutoScrollMaxPage:function(){
this._yMax=this._html.scrollHeight;
this._xMax=this._html.scrollWidth;
},checkAutoScroll:function(e){
if(this._autoScrollActive){
this.stopAutoScroll();
}
this._y=e.pageY;
this._x=e.pageX;
if(e.clientX<this.marginMouse){
this._autoScrollActive=true;
this._autoScrollLeft(e);
}else{
if(e.clientX>this._v.w-this.marginMouse){
this._autoScrollActive=true;
this._autoScrollRight(e);
}
}
if(e.clientY<this.marginMouse){
this._autoScrollActive=true;
this._autoScrollUp(e);
}else{
if(e.clientY>this._v.h-this.marginMouse){
this._autoScrollActive=true;
this._autoScrollDown();
}
}
},_autoScrollDown:function(){
if(this._timer){
clearTimeout(this._timer);
}
if(this._autoScrollActive&&this._y+this.marginMouse<this._yMax){
this._html.scrollTop+=this.interval;
this._node.style.top=(parseInt(this._node.style.top)+this.interval)+"px";
this._y+=this.interval;
this._timer=setTimeout(_3.hitch(this,"_autoScrollDown"),this.recursiveTimer);
}
},_autoScrollUp:function(){
if(this._timer){
clearTimeout(this._timer);
}
if(this._autoScrollActive&&this._y-this.marginMouse>0){
this._html.scrollTop-=this.interval;
this._node.style.top=(parseInt(this._node.style.top)-this.interval)+"px";
this._y-=this.interval;
this._timer=setTimeout(_3.hitch(this,"_autoScrollUp"),this.recursiveTimer);
}
},_autoScrollRight:function(){
if(this._timer){
clearTimeout(this._timer);
}
if(this._autoScrollActive&&this._x+this.marginMouse<this._xMax){
this._html.scrollLeft+=this.interval;
this._node.style.left=(parseInt(this._node.style.left)+this.interval)+"px";
this._x+=this.interval;
this._timer=setTimeout(_3.hitch(this,"_autoScrollRight"),this.recursiveTimer);
}
},_autoScrollLeft:function(e){
if(this._timer){
clearTimeout(this._timer);
}
if(this._autoScrollActive&&this._x-this.marginMouse>0){
this._html.scrollLeft-=this.interval;
this._node.style.left=(parseInt(this._node.style.left)-this.interval)+"px";
this._x-=this.interval;
this._timer=setTimeout(_3.hitch(this,"_autoScrollLeft"),this.recursiveTimer);
}
},stopAutoScroll:function(){
if(this._timer){
clearTimeout(this._timer);
}
this._autoScrollActive=false;
},destroy:function(){
_4.disconnect(this.resizeHandler);
}});
dojox.mdnd.autoScroll=null;
dojox.mdnd.autoScroll=new dojox.mdnd.AutoScroll();
return as;
});
