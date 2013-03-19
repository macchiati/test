//>>built
define("dojox/dgauges/ScaleIndicatorBase",["dojo/_base/lang","dojo/_base/declare","dojo/_base/window","dojo/on","dojo/_base/connect","dojo/_base/fx","dojox/gfx","dojox/widget/_Invalidating","./IndicatorBase"],function(_1,_2,_3,on,_4,fx,_5,_6,_7){
return _2("dojox.dgauges.ScaleIndicatorBase",_7,{scale:null,value:0,interactionArea:"gauge",interactionMode:"mouse",animationDuration:0,animationEaser:null,_indicatorShapeFuncFlag:true,_interactionAreaFlag:true,_downListeners:null,_cursorListeners:null,_moveAndUpListeners:null,_transitionValue:NaN,_preventAnimation:false,_animation:null,constructor:function(){
this.watch("value",_1.hitch(this,function(){
this.valueChanged(this);
}));
this.watch("value",_1.hitch(this,this._startAnimation));
this.watch("interactionArea",_1.hitch(this,function(){
this._interactionAreaFlag=true;
}));
this.watch("interactionMode",_1.hitch(this,function(){
this._interactionAreaFlag=true;
}));
this.watch("indicatorShapeFunc",_1.hitch(this,function(){
this._indicatorShapeFuncFlag=true;
}));
this.addInvalidatingProperties(["scale","value","indicatorShapeFunc","interactionArea","interactionMode"]);
this._downListeners=[];
this._moveAndUpListeners=[];
this._cursorListeners=[];
},_startAnimation:function(_8,_9,_a){
if(this.animationDuration==0){
return;
}
if(this._animation&&(this._preventAnimation||_9==_a)){
this._animation.stop();
return;
}
this._animation=new fx.Animation({curve:[_9,_a],duration:this.animationDuration,easing:this.animationEaser?this.animationEaser:fx._defaultEasing,onAnimate:_1.hitch(this,this._updateAnimation),onEnd:_1.hitch(this,this._endAnimation),onStop:_1.hitch(this,this._endAnimation)});
this._animation.play();
},_updateAnimation:function(v){
this._transitionValue=v;
this.invalidateRendering();
},_endAnimation:function(){
this._transitionValue=NaN;
this.invalidateRendering();
},refreshRendering:function(){
if(this._gfxGroup==null||this.scale==null){
return;
}else{
if(this._indicatorShapeFuncFlag&&_1.isFunction(this.indicatorShapeFunc)){
this._gfxGroup.clear();
this.indicatorShapeFunc(this._gfxGroup,this);
this._indicatorShapeFuncFlag=false;
}
if(this._interactionAreaFlag){
this._interactionAreaFlag=this._connectDownListeners();
}
}
},valueChanged:function(_b){
on.emit(this,"valueChanged",{target:this,bubbles:true,cancelable:true});
},_disconnectDownListeners:function(){
for(var i=0;i<this._downListeners.length;i++){
_4.disconnect(this._downListeners[i]);
}
this._downListeners=[];
},_disconnectMoveAndUpListeners:function(){
for(var i=0;i<this._moveAndUpListeners.length;i++){
_4.disconnect(this._moveAndUpListeners[i]);
}
this._moveAndUpListeners=[];
},_disconnectListeners:function(){
this._disconnectDownListeners();
this._disconnectMoveAndUpListeners();
this._disconnectCursorListeners();
},_connectCursorListeners:function(_c){
var _d=_c.connect("onmouseover",this,function(){
this.scale._gauge._setCursor("pointer");
});
this._cursorListeners.push(_d);
_d=_c.connect("onmouseout",this,function(_e){
this.scale._gauge._setCursor("");
});
this._cursorListeners.push(_d);
},_disconnectCursorListeners:function(){
for(var i=0;i<this._cursorListeners.length;i++){
_4.disconnect(this._cursorListeners[i]);
}
this._cursorListeners=[];
},_connectDownListeners:function(){
this._disconnectDownListeners();
this._disconnectCursorListeners();
var _f=null;
var _10;
if(this.interactionMode=="mouse"){
_10="onmousedown";
}else{
if(this.interactionMode=="touch"){
_10="ontouchstart";
}
}
if(this.interactionMode=="mouse"||this.interactionMode=="touch"){
if(this.interactionArea=="indicator"){
_f=this._gfxGroup.connect(_10,this,this._onMouseDown);
this._downListeners.push(_f);
if(this.interactionMode=="mouse"){
this._connectCursorListeners(this._gfxGroup);
}
}else{
if(this.interactionArea=="gauge"){
if(!this.scale||!this.scale._gauge||!this.scale._gauge._gfxGroup){
return true;
}
_f=this.scale._gauge._gfxGroup.connect(_10,this,this._onMouseDown);
this._downListeners.push(_f);
_f=this._gfxGroup.connect(_10,this,this._onMouseDown);
this._downListeners.push(_f);
if(this.interactionMode=="mouse"){
this._connectCursorListeners(this.scale._gauge._gfxGroup);
}
}else{
if(this.interactionArea=="area"){
if(!this.scale||!this.scale._gauge||!this.scale._gauge._baseGroup){
return true;
}
_f=this.scale._gauge._baseGroup.connect(_10,this,this._onMouseDown);
this._downListeners.push(_f);
_f=this._gfxGroup.connect(_10,this,this._onMouseDown);
this._downListeners.push(_f);
if(this.interactionMode=="mouse"){
this._connectCursorListeners(this.scale._gauge._baseGroup);
}
}
}
}
}
return false;
},_connectMoveAndUpListeners:function(){
var _11=null;
var _12;
var _13;
if(this.interactionMode=="mouse"){
_12="onmousemove";
_13="onmouseup";
}else{
if(this.interactionMode=="touch"){
_12="ontouchmove";
_13="ontouchend";
}
}
_11=_4.connect(_3.doc,_12,this,this._onMouseMove);
this._moveAndUpListeners.push(_11);
_11=_4.connect(_3.doc,_13,this,this._onMouseUp);
this._moveAndUpListeners.push(_11);
},_onMouseDown:function(_14){
this._connectMoveAndUpListeners();
this._startEditing();
},_onMouseMove:function(_15){
this._preventAnimation=true;
if(this._animation){
this._animation.stop();
}
},_onMouseUp:function(_16){
this._disconnectMoveAndUpListeners();
this._preventAnimation=false;
this._endEditing();
},_startEditing:function(){
if(!this.scale||!this.scale._gauge){
return;
}else{
this.scale._gauge.onStartEditing({indicator:this});
}
},_endEditing:function(){
if(!this.scale||!this.scale._gauge){
return;
}else{
this.scale._gauge.onEndEditing({indicator:this});
}
}});
});
