/*
	Copyright (c) 2004-2010, The Dojo Foundation All Rights Reserved.
	Available via Academic Free License >= 2.1 OR the modified BSD license.
	see: http://dojotoolkit.org/license for details
*/


if(!dojo._hasResource["dojox.form.CheckedMultiSelect"]){
dojo._hasResource["dojox.form.CheckedMultiSelect"]=true;
dojo.provide("dojox.form.CheckedMultiSelect");
dojo.require("dijit.form.CheckBox");
dojo.require("dijit.form._FormSelectWidget");
dojo.declare("dojox.form._CheckedMultiSelectItem",[dijit._Widget,dijit._Templated],{widgetsInTemplate:true,templateString:dojo.cache("dojox.form","resources/_CheckedMultiSelectItem.html","<div class=\"dijitReset ${baseClass}\"\n\t><input class=\"${baseClass}Box\" dojoType=\"dijit.form.CheckBox\" dojoAttachPoint=\"checkBox\" \n\t\tdojoAttachEvent=\"_onClick:_changeBox\" type=\"${_type.type}\" baseClass=\"${_type.baseClass}\"\n\t/><div class=\"dijitInline ${baseClass}Label\" dojoAttachPoint=\"labelNode\" dojoAttachEvent=\"onclick:_onClick\"></div\n></div>\n"),baseClass:"dojoxMultiSelectItem",option:null,parent:null,disabled:false,readOnly:false,postMixInProperties:function(){
if(this.parent.multiple){
this._type={type:"checkbox",baseClass:"dijitCheckBox"};
}else{
this._type={type:"radio",baseClass:"dijitRadio"};
}
this.disabled=this.option.disabled=this.option.disabled||false;
this.inherited(arguments);
},postCreate:function(){
this.inherited(arguments);
this.labelNode.innerHTML=this.option.label;
},_changeBox:function(){
if(this.get("disabled")||this.get("readOnly")){
return;
}
if(this.parent.multiple){
this.option.selected=this.checkBox.attr("value")&&true;
}else{
this.parent.attr("value",this.option.value);
}
this.parent._updateSelection();
this.parent.focus();
},_onClick:function(e){
if(this.get("disabled")||this.get("readOnly")){
dojo.stopEvent(e);
}else{
this.checkBox._onClick(e);
}
},_updateBox:function(){
this.checkBox.attr("value",this.option.selected);
},_setDisabledAttr:function(_1){
this.disabled=_1||this.option.disabled;
this.checkBox.attr("disabled",this.disabled);
dojo.toggleClass(this.domNode,"dojoxMultiSelectDisabled",this.disabled);
},_setReadOnlyAttr:function(_2){
this.checkBox.attr("readOnly",_2);
this.readOnly=_2;
}});
dojo.declare("dojox.form.CheckedMultiSelect",dijit.form._FormSelectWidget,{templateString:dojo.cache("dojox.form","resources/CheckedMultiSelect.html","<div class=\"dijit dijitReset dijitInline\" dojoAttachEvent=\"onmousedown:_onMouseDown,onclick:focus\"\n\t><select class=\"${baseClass}Select\" multiple=\"true\" dojoAttachPoint=\"containerNode,focusNode\"></select\n\t><div dojoAttachPoint=\"wrapperDiv\"></div\n></div>\n"),baseClass:"dojoxMultiSelect",_onMouseDown:function(e){
dojo.stopEvent(e);
},_addOptionItem:function(_3){
this.wrapperDiv.appendChild(new dojox.form._CheckedMultiSelectItem({option:_3,parent:this}).domNode);
},_updateSelection:function(){
this.inherited(arguments);
dojo.forEach(this._getChildren(),function(c){
c._updateBox();
});
},_getChildren:function(){
return dojo.map(this.wrapperDiv.childNodes,function(n){
return dijit.byNode(n);
});
},invertSelection:function(_4){
dojo.forEach(this.options,function(i){
i.selected=!i.selected;
});
this._updateSelection();
},_setDisabledAttr:function(_5){
this.inherited(arguments);
dojo.forEach(this._getChildren(),function(_6){
if(_6&&_6.attr){
_6.attr("disabled",_5);
}
});
},_setReadOnlyAttr:function(_7){
if("readOnly" in this.attributeMap){
this._attrToDom("readOnly",_7);
}
this.readOnly=_7;
dojo.forEach(this._getChildren(),function(_8){
if(_8&&_8.attr){
_8.attr("readOnly",_7);
}
});
},uninitialize:function(){
dojo.forEach(this._getChildren(),function(_9){
_9.destroyRecursive();
});
this.inherited(arguments);
}});
}
