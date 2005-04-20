//
//  NodeSet.java
//  cldrtools
//
//  Created by Steven R. Loomis on 3/11/2005.
//  Copyright 2005 IBM. All rights reserved.
//

package org.unicode.cldr.web;

import java.io.*;
import java.util.*;

// DOM imports
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.NodeList;

import com.ibm.icu.text.DecimalFormat;
import com.ibm.icu.text.Normalizer;
import com.ibm.icu.util.ULocale;
import com.ibm.icu.lang.UCharacter;

import org.unicode.cldr.util.*;
import org.unicode.cldr.icu.*;


import com.ibm.icu.lang.UCharacter;

public class NodeSet {
    public interface NodeSetTexter {
        abstract public String text(NodeSetEntry e);
    }
    public interface XpathFilter {
        abstract public boolean okay(String path);
    }
    
    /**
     * This class represents a single data item relative to a single locale 
     */
    public class NodeSetEntry {
        public String xpath = null;
        public String type;
        public String key; // for types
        boolean mainDraft = false;
        Vector otherAlts = new Vector();

        public Node main = null; // Main node. May be missing or draft.

        public Node proposed = null; // TODO: needs to be a list

        public Node fallback = null; // fallback from somewhere
        public String fallbackLocale = null; // fallback info
        public Object fallbackObject = null; // In case there's no real node
        
        public NodeSetEntry(String t) {
            type = t;
            key = null;
        }
        public NodeSetEntry(String t, String k) {
            type = t;
            key = k;
        }
        
        public void add(Node node, String locale) {
            if(locale == null) {
                add(node);
            } else {
                fallback = node;
                fallbackLocale = locale;
            }
        }

        public void add(Object obj, String locale) {
            fallbackObject = obj;
            fallbackLocale = locale;
        }
        
        public void add(Node node, boolean draft, String alt)
        {
            if((alt!=null)&&(alt.length()>0)) {
                if(LDMLConstants.PROPOSED.equals(alt)) {
                    proposed = node;
                } else {
                    otherAlts.add(node);
                }
            } else {
                if(main != null) {
                    throw new RuntimeException("dup main nodes- " + type + ", " + node.toString());
                }
                main = node;
                mainDraft = draft;                
            }
        }
        
        public void add(Node node) {
            String alt = LDMLUtilities.getAttributeValue(node, LDMLConstants.ALT);
            String testType = LDMLUtilities.getAttributeValue(node, LDMLConstants.TYPE);
            //String testKey = LDMLUtilities.getAttributeValue(node, LDMLConstants.KEY);
            boolean draft = LDMLUtilities.isNodeDraft(node);
            
            if(!testType.equals(type)) {
                throw new RuntimeException("Types don't match: " + type + ", " + testType);
            }
            add(node, draft, alt);
        }
    }

    /**
     * create an empty NodeSet
     */
    public NodeSet() { 
    }

    /**
     * add a fallback
     */
    public void add(String l) {
        add(new NodeSetEntry(l));
    }
    
    public void add(NodeSetEntry nse) {
        map.put(nse.type,nse);
    }
    
    public void add(Node node) {
        add(node, null);
    }
    public void add(Node node, String locale) {
        String type = LDMLUtilities.getAttributeValue(node, LDMLConstants.TYPE);
        String key = LDMLUtilities.getAttributeValue(node, LDMLConstants.KEY);
        NodeSetEntry nse = (NodeSetEntry)map.get(type);
        if(nse == null) {
            nse = new NodeSetEntry(type, key);
            map.put(type,nse);
        }
        nse.add(node, locale);            
    }
    
    /**
     * @param ctx (for debugging)
     * @param xpath xpath of the node
     * @param node the node
     * @param locale - null if this is the main (active) locale 
     */
    public void addFromXpath(WebContext ctx, ULocale u, String xpath, Node node, boolean draft, String alt, String type) {
        NodeSetEntry nse = (NodeSetEntry)map.get(xpath);
        if(nse == null) {
            nse = new NodeSetEntry((type!=null)?type:"");
            nse.xpath = xpath;
            map.put(xpath, nse);
        }
        if(u != null) {
            if((draft==false)&&(alt==null)) {
                nse.add(node, u.toString());
            }
        } else {
///*srl*/            ctx.println(" - adding -<br/>");
            nse.add(node, draft, alt);
        }
    }
    
    public Iterator iterator() {
        return map.values().iterator();
    }

    /**
     * @param ctx (for debugging)
     */
    static void traverseTree(WebContext ctx, NodeSet s, ULocale u, Node root, String xpath,
            boolean draft, String alt, String type, XpathFilter filter) {
        for(Node node=root.getFirstChild(); node!=null; node=node.getNextSibling()){
            if(node.getNodeType()!=Node.ELEMENT_NODE){
                continue;
            }
            String nodeName = node.getNodeName();
            String newPath = xpath + "/" + nodeName;
            String newAlt = LDMLUtilities.getAttributeValue(node, LDMLConstants.ALT);
            if(newAlt != null) {
                alt = newAlt;
            }
            String newType = LDMLUtilities.getAttributeValue(node, LDMLConstants.TYPE);
            if(newType != null) {
                type = newType;
            }
            String newDraft = LDMLUtilities.getAttributeValue(node, LDMLConstants.DRAFT);
            if((newDraft != null)&&(newDraft.equals("true"))) {
                draft = true;
            }
            
            for(int i=0;i<SurveyMain.distinguishingAttributes.length;i++) {
                String da = SurveyMain.distinguishingAttributes[i];
                String nodeAtt = LDMLUtilities.getAttributeValue(node,da);
                if((nodeAtt != null) && 
                    !(da.equals(LDMLConstants.ALT)
                            /* &&nodeAtt.equals(LDMLConstants.PROPOSED) */ )) { // no alts for now
                    newPath = newPath + "[@"+da+"='" + nodeAtt + "']";
                }
            }
            // any values here?
            String value = LDMLUtilities.getNodeValue(node);
            if((value != null) && (value.length()>0)) {
                if(filter.okay(newPath)) {
                    if(value.startsWith(" ") || value.startsWith("\n") || value.startsWith("\t") || 
                        value.startsWith("\r")) {
    ///*srl*/            ctx.println("<tt>O " + newPath + " " + (draft?"<b>draft</b>":"") + " " + ((alt==null)?"":("<b>alt="+alt +"</b>")) + "</tt><br/>");
    ///*srl*/            ctx.println("<tt>+ (" + value + ") #" + value.length() + "</tt><br/>");
                    } else {
                        s.addFromXpath(ctx, u, newPath, node, draft, alt, type);
                    }
                }
            }
            
            traverseTree(ctx, s, u, node, newPath, draft, alt, type, filter);
        }
    }

    static public NodeSet loadFromXpaths(WebContext ctx, TreeMap allXpaths, XpathFilter filter) {
        NodeSet s = new NodeSet();
        Node roots[] = new Node[ctx.doc.length];
        int i;
        // Load the doc root for each item. 
        // This is starting to sound suspiciously like a fuil resolver.
        for(i=0;i<ctx.doc.length;i++) {
            roots[i] = ctx.doc[i];
        }

        // add all the items from the document
        if(roots[0] != null) {
            traverseTree(ctx, s, null, roots[0], "/", false, null, null, filter);
        }

/*        for(Iterator li = allXpaths.keySet().iterator();li.hasNext();) {
            String ln = (String)li.next();
            // throw out junk
            if(ln.startsWith(LOCALEDISPLAYNAMES)) {
                continue;
            }
            doLocaleCodeList(ctx, ln, null, null);
            
            NodeSet mySet = loadFromPath(ctx, ln, null);
            if(mySet.isEmpty()) {
                continue;
            }
        }
    */
        return s;
    }
    
    /**
     * load a NodeSet from the associated WebContext, given an Xpath and an optional set of full items
     * @param ctx the WebContext
     * @param xpath xpath to the requested items
     * @param fullSet optional Set listing the keys expected to be found, otherwise null
     */
    static public NodeSet loadFromPath(WebContext ctx, String xpath, Set fullSet) {
        NodeSet s = new NodeSet();
        Node roots[] = new Node[ctx.doc.length];
        int i;
        // Load the doc root for each item. 
        // This is starting to sound suspiciously like a fuil resolver.
        for(i=0;i<ctx.doc.length;i++) {
            roots[i] = LDMLUtilities.getNode(ctx.doc[i], xpath);
        }
        
        // add all the items from the document
        if(roots[0] != null) {
            for(Node node=roots[0].getFirstChild(); node!=null; node=node.getNextSibling()){
                if(node.getNodeType()!=Node.ELEMENT_NODE){
                    continue;
                }
                s.add(node); // add regular item
            }
        }

        // Sigh.  May as well do this.
        for(i=(roots.length)-1;i>0;i--) {
            //ctx.println("Loading from: " + ctx.docLocale[i] + "<br/>");
            if(roots[i] != null) for(Node node=roots[i].getFirstChild(); node!=null; node=node.getNextSibling()){
                if(node.getNodeType()!=Node.ELEMENT_NODE){
                    continue;
                }
                if(LDMLUtilities.isNodeDraft(node)) {
                    continue;
                }
                s.add(node, ctx.docLocale[i]); // add an item as a fallback ( w/ a locale )
            }
        }

        if(fullSet != null) {
            for(Iterator e = fullSet.iterator();e.hasNext();) {
                String f = (String)e.next();
                NodeSetEntry nse = s.get(f);
                if(nse == null) {
                    s.add(f); // adds an empty item, no data.
                }
            }
        }
        return s;
    }
    
    NodeSetEntry get(String type) {
        return (NodeSetEntry)map.get(type);
    }
    
    int count() {
        return map.size();
    }
    
    /**
     * return the elements sorted according to the texter 
     */
    Map getSorted(NodeSetTexter nst) {
        if(nst == null) {
            nst = new NullTexter();
        }
        TreeMap m = new TreeMap();
        for(Iterator e = iterator();e.hasNext();) {
            NodeSet.NodeSetEntry f = (NodeSet.NodeSetEntry)e.next();
            m.put(nst.text(f),f);
        }
        return m;
    }

    private HashMap map = new HashMap();
    private NodeSetTexter dispTexter = null;
}
