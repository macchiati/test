/*
**********************************************************************
* Copyright (c) 2002-2004, International Business Machines
* Corporation and others.  All Rights Reserved.
**********************************************************************
* Author: Mark Davis
**********************************************************************
*/
package org.unicode.cldr.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.w3c.dom.Node;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.SAXNotRecognizedException;
import org.xml.sax.SAXNotSupportedException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.DeclHandler;
import org.xml.sax.ext.LexicalHandler;
import org.xml.sax.helpers.XMLReaderFactory;

import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RuleBasedCollator;
import com.ibm.icu.util.ULocale;

//import javax.xml.parsers.*;

/**
 * This is a simple class that walks through the CLDR hierarchy and does 2 things.
 * First, it determines all the places where the CLDR is not minimal: where there
 * are redundancies with inheritance. It generates new files in the target directory.
 * Second, it gathers together all the items from all the locales that share the
 * same element chain, and thus presents a "sideways" view of the data, in files called
 * by_type/X.html, where X is a type. X may be the concatenation of more than more than
 * one element, where the file would otherwise be too large.
 * @author medavis
 */
/*
Notes:
http://xml.apache.org/xerces2-j/faq-grammars.html#faq-3
http://developers.sun.com/dev/coolstuff/xml/readme.html
http://lists.xml.org/archives/xml-dev/200007/msg00284.html
http://java.sun.com/j2se/1.4.2/docs/api/org/xml/sax/DTDHandler.html
 */
public class CLDRFile implements Lockable {
	public static boolean HACK_ORDER = false;
	private static boolean DEBUG_LOGGING = true;
	public static final String SUPPLEMENTAL_NAME = "supplementalData";
    
    private Map xpath_value;
    private String initialComment = "";
    private String finalComment = "";
    private String key;
    private XPathParts.Comments xpath_comments = new XPathParts.Comments(); // map from paths to comments.
    private boolean isSupplemental;
    
    private CLDRFile(boolean isSupplemental){
    	this.isSupplemental = isSupplemental;
    	xpath_value = isSupplemental ? new TreeMap() : new TreeMap(ldmlComparator);
    }
	
    /**
     * Create a CLDRFile for the given localename. (Normally a Factory is used to create CLDRFiles.)
     * @param localeName
     * @param optionalLog TODO
     */
    public static CLDRFile make(String localeName) {
    	CLDRFile result = new CLDRFile(localeName.equals(SUPPLEMENTAL_NAME));
		result.key = localeName;
		return result;
    }
    
    /**
     * Produce a CLDRFile from a localeName, given a directory. (Normally a Factory is used to create CLDRFiles.)
     * @param localeName
     * @param dir directory 
     * @param optionalLog TODO
     * @throws SAXNotSupportedException
     * @throws SAXNotRecognizedException
     * @throws IOException
     */
    // TODO make the directory a URL
    public static CLDRFile make(String localeName, String dir) {
        String name = dir + localeName + ".xml";
        File f = new File(name);
        try {
        	name = f.getCanonicalPath();
            if (DEBUG_LOGGING) {
             	System.out.println("Parsing: " + name);
             	Log.logln("Parsing: " + f.getCanonicalPath());
    	    }
			FileInputStream fis = new FileInputStream(f);
	    	CLDRFile result = make(localeName, fis);
			fis.close();
			return result;
		} catch (IOException e) {
			throw (IllegalArgumentException)new IllegalArgumentException("Can't read " + name).initCause(e);
		}
    }
    
    /**
     * Produce a CLDRFile from a file input stream. (Normally a Factory is used to create CLDRFiles.)
     * @param localeName
     * @param fis
     * @param log TODO
     * @throws IOException
     * @throws SAXException
     */
    public static CLDRFile make(String localeName, FileInputStream fis) {
    	try {
    		CLDRFile result = make(localeName);
			MyDeclHandler DEFAULT_DECLHANDLER = new MyDeclHandler(result);
			XMLReader xmlReader = createXMLReader(true);
			xmlReader.setContentHandler(DEFAULT_DECLHANDLER);
			xmlReader.setErrorHandler(DEFAULT_DECLHANDLER);
			xmlReader.setProperty("http://xml.org/sax/properties/lexical-handler", DEFAULT_DECLHANDLER);
			xmlReader.setProperty("http://xml.org/sax/properties/declaration-handler", DEFAULT_DECLHANDLER);
			xmlReader.parse(new InputSource(fis));
			return result;
    	} catch (SAXParseException e) {
    		System.out.println(CLDRFile.showSAX(e));
    		throw (IllegalArgumentException)new IllegalArgumentException("Can't read " + localeName).initCause(e);
		} catch (SAXException e) {
			throw (IllegalArgumentException)new IllegalArgumentException("Can't read " + localeName).initCause(e);
		} catch (IOException e) {
			throw (IllegalArgumentException)new IllegalArgumentException("Can't read " + localeName).initCause(e);
		}    	 
    }
    
    /**
     * Clone the object. Produces unlocked version (see Lockable).
     */
    public Object clone() {
    	try {
			CLDRFile result = (CLDRFile) super.clone();
			result.locked = false;
			result.xpath_value = (Map)((TreeMap)xpath_value).clone();
			result.xpath_comments = (XPathParts.Comments)xpath_comments.clone();
			return result;
		} catch (CloneNotSupportedException e) {
			throw new InternalError("should never happen");
		}
    }
	

	/**
	 * Write the corresponding XML file out, with the normal formatting and indentation.
	 * @param pw
	 * @param key
	 */
	public void write(PrintWriter pw) {
		pw.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
		pw.println("<!DOCTYPE ldml SYSTEM \"http://www.unicode.org/cldr/dtd/1.2/ldml.dtd\">");
		XPathParts.writeComment(pw, 0, initialComment, false);
		
		XPathParts.Comments tempComments = (XPathParts.Comments) xpath_comments.clone();
		
		MapComparator modAttComp = attributeOrdering;
		if (HACK_ORDER) modAttComp = new MapComparator()
			.add("alt").add("draft").add(modAttComp.getOrder());

		XPathParts last = new XPathParts(attributeOrdering, defaultSuppressionMap);
		XPathParts current = new XPathParts(attributeOrdering, defaultSuppressionMap);
		XPathParts lastFiltered = new XPathParts(attributeOrdering, defaultSuppressionMap);
		XPathParts currentFiltered = new XPathParts(attributeOrdering, defaultSuppressionMap);
		for (Iterator it2 = xpath_value.keySet().iterator(); it2.hasNext();) {
			String xpath = (String)it2.next();
			Value v = (Value) xpath_value.get(xpath);
			currentFiltered.set(xpath);
			current.set(v.fullXPath);
			current.writeDifference(pw, currentFiltered, last, lastFiltered, v, tempComments);
			// exchange pairs of parts
			XPathParts temp = current;
			current = last;
			last = temp;
			temp = currentFiltered;
			currentFiltered = lastFiltered;
			lastFiltered = temp;
		}
		current.clear().writeDifference(pw, null, last, lastFiltered, null, tempComments);
		XPathParts.writeComment(pw, 0, finalComment, true);
		
		for (Iterator it = tempComments.removeFinal().iterator(); it.hasNext();) {
			String key = (String) it.next();
			Log.logln("Writing extra comment: " + key);
			XPathParts.writeComment(pw, 0, key, false);
		}
	}

	/**
	 * Get a value from an xpath.
	 */
    public Value getValue(String xpath) {
    	return (Value) xpath_value.get(xpath);
    }
    
    /**
     * Add a new element to a CLDRFile.
     * @param xpath
     * @param comment
     * @param currentFullXPath
     * @param value
     */
    public void add(String xpath, String currentFullXPath, String value) {
    	if (locked) throw new UnsupportedOperationException("Attempt to modify locked object");
    	StringValue v = new StringValue(value, currentFullXPath);
    	Log.logln("ADDING: \t" + xpath + " \t" + v);
    	xpath = xpath.intern();
        xpath_value.put(xpath, v);
    }
    
    public void addComment(String xpath, String comment, int type) {
    	if (locked) throw new UnsupportedOperationException("Attempt to modify locked object");
    	// System.out.println("Adding comment: <" + xpath + "> '" + comment + "'");
    	Log.logln("ADDING Comment: \t" + type + "\t" + xpath + " \t" + comment);
    	if (xpath == null || xpath.length() == 0) {
    		finalComment = (finalComment.length() == 0 ? "" : finalComment + XPathParts.NEWLINE) + comment;
    	} else {
	        xpath_comments.add(type, xpath, comment);
    	}
    }

    
    /**
     * Merges elements from another CLDR file. Note: when both have the same xpath key, 
     * the keepMine determines whether "my" values are kept
     * or the other files values are kept.
     * @param other
     * @param keepMine if true, keep my values in case of conflict; otherwise keep the other's values.
     */
    public void putAll(CLDRFile other, boolean keepMine) {
    	if (locked) throw new UnsupportedOperationException("Attempt to modify locked object");
    	if (keepMine) {
    		Map temp = isSupplemental ? new TreeMap() : new TreeMap(ldmlComparator);
    		temp.putAll(other.xpath_value);
    		temp.putAll(xpath_value);
    		xpath_value = temp;
    	} else {
    		xpath_value.putAll(other.xpath_value);
    	}
    }
    
    /**
     * Removes an element from a CLDRFile.
     * @param xpath
     */
    public void remove(String xpath) {
    	if (locked) throw new UnsupportedOperationException("Attempt to modify locked object");
    	xpath_value.remove(xpath);
    }
    
	/**
	 * @return Returns the finalComment.
	 */
	public String getFinalComment() {
		return finalComment;
	}
	/**
	 * @return Returns the finalComment.
	 */
	public String getInitialComment() {
		return initialComment;
	}
	/**
	 * @return Returns the xpath_comments.
	 */
	public XPathParts.Comments getXpath_comments() {
		return (XPathParts.Comments) xpath_comments.clone();
	}
	/**
	 * @return Returns the key.
	 */
	public String getKey() {
		return key;
	}

	private boolean locked;
	
	/* (non-Javadoc)
	 * @see org.unicode.cldr.util.Lockable#isLocked()
	 */
	public synchronized boolean isLocked() {
		return locked;
	}

	/* (non-Javadoc)
	 * @see org.unicode.cldr.util.Lockable#lock()
	 */
	public synchronized Object lock() {
		locked = true;
		return this;
	}
	/**
	 * @param finalComment The finalComment to set.
	 */
	public void setFinalComment(String comment) {
    	if (locked) throw new UnsupportedOperationException("Attempt to modify locked object");
		this.finalComment = comment;
	}

	/**
	 * @param finalComment The finalComment to set.
	 */
	public void setInitialComment(String comment) {
    	if (locked) throw new UnsupportedOperationException("Attempt to modify locked object");
		this.initialComment = comment;
	}

	// ========== STATIC UTILITIES ==========
	
    /**
	 * Utility to restrict to files matching a given regular expression. The expression does not contain ".xml".
	 * Note that supplementalData is always skipped, and root is always included.
	 * @param sourceDir
	 * @param localeRegex
	 * @return
	 */
    public static Set getMatchingXMLFiles(String sourceDir, String localeRegex) {
        Matcher m = Pattern.compile(localeRegex).matcher("");
        Set s = new TreeSet();
        File[] files = new File(sourceDir).listFiles();
        for (int i = 0; i < files.length; ++i) {
            String name = files[i].getName();
            if (!name.endsWith(".xml")) continue;
            if (name.startsWith(SUPPLEMENTAL_NAME)) continue;
            String locale = name.substring(0,name.length()-4); // drop .xml
            if (!locale.equals("root") && !m.reset(locale).matches()) continue;
            s.add(locale);
        }
        return s;
    }

    public Set keySet() {
    	return Collections.unmodifiableSet(xpath_value.keySet());
    }
    
	/**
	 * Determine if an attribute is a distinguishing attribute.
	 * @param elementName
	 * @param attribute
	 * @return
	 */
	private static boolean isDistinguishing(String elementName, String attribute) {
		return attribute.equals("key") 
		|| attribute.equals("registry") 
		|| attribute.equals("alt")
		|| attribute.equals("iso4217")
		|| attribute.equals("iso3166")
		|| (attribute.equals("type") && !elementName.equals("default") && !elementName.equals("mapping"));
	}
	
	/**
	 * Utility to create a validating XML reader.
	 * @param validating
	 * @return
	 */
    public static XMLReader createXMLReader(boolean validating) {
    	String[] testList = {
    			"org.apache.xerces.parsers.SAXParser",
				"org.apache.crimson.parser.XMLReaderImpl",
				"gnu.xml.aelfred2.XmlReader",
				"com.bluecast.xml.Piccolo",
				"oracle.xml.parser.v2.SAXParser",
				""
    	};
        XMLReader result = null;
        for (int i = 0; i < testList.length; ++i) {
	        try {
	            result = (testList[i].length() != 0) 
					? XMLReaderFactory.createXMLReader(testList[i])
			        : XMLReaderFactory.createXMLReader();
	            result.setFeature("http://xml.org/sax/features/validation", validating);
	            break;
	        } catch (SAXException e1) {}
        }
        if (result == null) throw new NoClassDefFoundError("No SAX parser is available, or unable to set validation correctly");
        try {
            result.setEntityResolver(new CachingEntityResolver());
        } catch (Throwable e) {
            System.err
                    .println("WARNING: Can't set caching entity resolver  -  error "
                            + e.toString());
            e.printStackTrace();
        }
        return result;
    }

    /**
     * A factory is the normal method to produce a set of CLDRFiles from a directory of XML files.
     */
	public static class Factory {
		private String sourceDirectory;
		private String matchString;
		private Set localeList = new TreeSet();
		private Map mainCache = new TreeMap();
		private Map resolvedCache = new TreeMap();  
		private Map supplementalCache = new TreeMap();
		private Factory() {}		
		/**
		 * Create a factory from a source directory, matchingString, and an optional log file.
		 * For the matchString meaning, see getMatchingXMLFiles
		 * @param sourceDirectory
		 * @param matchString
		 * @param optionalLog
		 * @return
		 */
		public static Factory make(String sourceDirectory, String matchString) {
			Factory result = new Factory();
			result.sourceDirectory = sourceDirectory;
			result.matchString = matchString;
			result.localeList = getMatchingXMLFiles(sourceDirectory, matchString);
			return result;
		}

		/**
		 * Get a set of the available locales for the factory.
		 * @return
		 */
	    public Set getAvailable() {
	    	return Collections.unmodifiableSet(localeList);
	    }
	    
	    /**
	     * Get a set of the available language locales (according to isLanguage).
	     * @return
	     */
	    public Set getAvailableLanguages() {
	    	Set result = new TreeSet();
	    	for (Iterator it = localeList.iterator(); it.hasNext();) {
	    		String s = (String) it.next();
	    		if (XPathParts.isLanguage(s)) result.add(s);
	    	}
	    	return result;
	    }
	    
	    /**
	     * Get a set of the locales that have the given parent (according to isSubLocale())
	     * @param parent
	     * @param isProper if false, then parent itself will match
	     * @return
	     */
	    public Set getAvailableWithParent(String parent, boolean isProper) {
	    	Set result = new TreeSet();
	    	for (Iterator it = localeList.iterator(); it.hasNext();) {
	    		String s = (String) it.next();
	    		int relation = XPathParts.isSubLocale(parent, s);
	    		if (relation >= 0 && !(isProper && relation == 0)) result.add(s);
	    	}
	    	return result;
	    }
	    
	    private boolean needToReadRoot = true;
	    
	    /**
	     * Make a CLDR file. The result is a locked file, so that it can be cached. If you want to modify it,
	     * use clone().
	     * @param localeName
	     * @param resolved if true, produces a resolved version.
	     * @return
	     * @throws SAXException
	     * @throws IOException
	     */
	    // TODO resolve aliases
		public CLDRFile make(String localeName, boolean resolved) {
			// TODO fix hack: 
			// read root first so that we get the ordering right.
/*			if (needToReadRoot) {
				if (!localeName.equals("root")) make("root", false);
				needToReadRoot = false;
			}
*/			// end of hack
	    	Map cache = resolved ? resolvedCache : mainCache;
	    	CLDRFile result = (CLDRFile) cache.get(localeName);
	    	if (result == null) {
	    		if (!resolved) {
	    			result = CLDRFile.make(localeName, sourceDirectory);
	    		} else {
    				// this is a bit tricky because of aliases
    				result = (CLDRFile) make(localeName, false).clone();
    				result.fixAliases(this);
    				String currentName = localeName;
    				while (true) {
    					// we do it in this order, WITHOUT resolving the parent
    					// so that aliases work right
    					currentName = LocaleIDParser.getParent(currentName);
    					if (currentName == null) break;
    					CLDRFile parent = make(currentName, false);
    					result.putAll(parent, true);
    					result.fixAliases(this);	    					
    				}
	    		}
	    		result.lock();
	    		cache.put(localeName, result);
	    	}
	    	return result;
	    }
	}

    /**
     * Immutable class that defines the value at a particular xpath.
     * Normally a string, unless the item does not inherit (like collation).
     */
    static public abstract class Value {
    	//private String comment;
    	private String fullXPath;
		/**
		 * Create a value.
		 * @param value
		 * @param comment2
		 */
		public Value(String currentFullXPath) {
	        //this.comment = comment.intern();
	        this.fullXPath = currentFullXPath.intern();
		}
		/**
		 * @return Returns the comment.
		 */
/*		public String getComment() {
			return comment;
		}
*/		/**
		 * @return Returns the fullXPath.
		 */
		public String getFullXPath() {
			return fullXPath;
		}
		/**
		 * boilerplate
		 */
    	public boolean equals(Object other) {
			if (other == null || !getClass().equals(other.getClass())) return false;
    		Value that = (Value)other;
    		return fullXPath.equals(that.fullXPath);
    	}
    	/**
    	 * Must be overridden.
    	 * @return
    	 */
    	public abstract String getStringValue();
		/**
		 * boilerplate
		 */
    	public String toString() {
    		return fullXPath + ";\t" + getStringValue(); 
    	}
		/**
		 * @return
		 */
		public boolean isDraft() {
			return fullXPath.indexOf("[@draft=\"true\"]") >= 0;
		}
		/**
		 * clone, but change the path.
		 * @param string
		 * @return
		 */
		abstract public Value changePath(String string);
    }
    
    /**
     * Value that contains a single string
     */
    static public class StringValue extends Value {
    	private String stringValue;
    	/**
		 * @param value
		 * @param comment
		 * @param currentFullXPath
		 */
		public StringValue(String value, String currentFullXPath) {
			super(currentFullXPath);
	        this.stringValue = value.intern();
		}
		/**
		 * boilerplate
		 */
		public boolean equals(Object other) {
    		if (!super.equals(other)) return false;
    		return stringValue.equals(((StringValue)other).stringValue);
    	}
		/**
		 * boilerplate
		 */
		public String getStringValue() {
			return stringValue;
		}
		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.CLDRFile.Value#changePath(java.lang.String)
		 */
		public Value changePath(String string) {
			// TODO Auto-generated method stub
			return new StringValue(stringValue, string);
		}
    }
    /**
     * Value that contains a node. WARNING: this is not done yet, and may change.
     * In particular, we don't want to return a Node, since that is mutable, and makes caching unsafe!!
     */
    static public class NodeValue extends Value {
    	private Node nodeValue;
    	/**
    	 * Creation. WARNING, may change.
    	 * @param value
    	 * @param comment
    	 * @param currentFullXPath
    	 */
		public NodeValue(Node value, String currentFullXPath) {
			super(currentFullXPath);
	        this.nodeValue = value;
		}
		/**
		 * boilerplate
		 */
    	public boolean equals(Object other) {
    		if (super.equals(other)) return false;
    		return nodeValue.equals(((NodeValue)other).nodeValue);
    	}
		/**
		 * boilerplate
		 */
		public String getStringValue() {
			return nodeValue.toString();
		}
		/* (non-Javadoc)
		 * @see org.unicode.cldr.util.CLDRFile.Value#changePath(java.lang.String)
		 */
		public Value changePath(String string) {
			return new NodeValue(nodeValue, string);
		}
    }

    private static class MyDeclHandler implements DeclHandler, ContentHandler, LexicalHandler, ErrorHandler {
    	private static final boolean SHOW_ALL = false;
    	private static final boolean SHOW_START_END = true;
    	private int commentStack;
    	private boolean justPopped = false;
    	private String lastChars = "";
    	private String currentXPath = "";
    	private String currentFullXPath = "";
        private String comment = null;
    	private Map attributeOrder = new TreeMap(attributeOrdering);
    	private CLDRFile target;
    	private String lastActiveLeafNode;
    	private String lastLeafNode;
    	private boolean isSupplemental;
    	
    	MyDeclHandler(CLDRFile target) {
    		this.target = target;
    		isSupplemental = target.key.equals(SUPPLEMENTAL_NAME);
    		if (!isSupplemental) attributeOrder = new TreeMap(attributeOrdering);
    		else attributeOrder = new TreeMap();
     	}
    		
    	private String show(Attributes attributes) {
    		if (attributes == null) return "null";
    		String result = "";
    		for (int i = 0; i < attributes.getLength(); ++i) {    			
    			String attribute = attributes.getQName(i);
    			String value = attributes.getValue(i);
     			result += "[@" + attribute + "=\"" + value + "\"]"; // TODO quote the value??
    		}
    		return result;
    	}
    	
    	private void push(String qName, Attributes attributes) {
    		//SHOW_ALL && 
    		Log.logln("push\t" + qName + "\t" + show(attributes));
        	if (lastChars.length() != 0) throw new IllegalArgumentException("Internal Error");
    		currentXPath += "/" + qName;
    		currentFullXPath += "/" + qName;
    		//if (!isSupplemental) ldmlComparator.addElement(qName);
    		if (attributes.getLength() > 0) {
    			attributeOrder.clear();
	    		for (int i = 0; i < attributes.getLength(); ++i) {    			
	    			String attribute = attributes.getQName(i);
	    			String value = attributes.getValue(i);
	    			//if (!isSupplemental) ldmlComparator.addAttribute(attribute); // must do BEFORE put
	    			//ldmlComparator.addValue(value);
	    			attributeOrder.put(attribute, value);
	    		}
	    		for (Iterator it = attributeOrder.keySet().iterator(); it.hasNext();) {
	    			String attribute = (String)it.next();
	    			String value = (String)attributeOrder.get(attribute);
	    			String both = "[@" + attribute + "=\"" + value + "\"]"; // TODO quote the value??
	    			currentFullXPath += both;
	    			// distinguishing = key, registry, alt, and type (except for the type attribute on the elements default and mapping).
	    			if (isDistinguishing(qName, attribute)) {
	    				currentXPath += both;
	    			}
	    		}
    		}
    		if (comment != null) {
    			target.addComment(currentXPath, comment, XPathParts.Comments.PREBLOCK);
    			comment = null;
    		}
            justPopped = false;
            lastActiveLeafNode = null;
    		Log.logln(SHOW_ALL, "currentXPath\t" + currentXPath + "\tcurrentFullXPath\t" + currentFullXPath);
    	}
    	
		private void pop(String qName) {
			Log.logln("pop\t" + qName);
            if (lastChars.length() != 0 || justPopped == false) {
                target.add(currentXPath, currentFullXPath, lastChars);
                lastChars = "";
                lastLeafNode = lastActiveLeafNode = currentXPath;
            } else {
            	Log.logln(lastActiveLeafNode != null, "pop: zeroing last leafNode: " + lastActiveLeafNode);
            	lastActiveLeafNode = null;
        		if (comment != null) {
        			target.addComment(lastLeafNode, comment, XPathParts.Comments.POSTBLOCK);
        			comment = null;
        		}
            }
			currentXPath = stripAfter(currentXPath, qName);
    		currentFullXPath = stripAfter(currentFullXPath, qName);    
            justPopped = true;
    	}
    	
		private static String stripAfter(String input, String qName) {
			int pos = findLastSlash(input);
			if (qName != null) assert input.substring(pos+1).startsWith(qName);
			return input.substring(0,pos);
		}
		
		private static int findLastSlash(String input) {
			int braceStack = 0;
			for (int i = input.length()-1; i >= 0; --i) {
				char ch = input.charAt(i);
				switch(ch) {
				case '/': if (braceStack == 0) return i; break;
				case '[': --braceStack; break;
				case ']': ++braceStack; break;
				}
			}
			return -1;
		}

		// SAX items we need to catch
		
        public void startElement(
            String uri,
            String localName,
            String qName,
            Attributes attributes)
            throws SAXException {
        		Log.logln(SHOW_ALL || SHOW_START_END, "startElement uri\t" + uri
        				+ "\tlocalName " + localName
        				+ "\tqName " + qName
        				+ "\tattributes " + show(attributes)
						);
        		try {
            		push(qName, attributes);                    
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    throw e;
                }
        }
        public void endElement(String uri, String localName, String qName)
            throws SAXException {
    			Log.logln(SHOW_ALL || SHOW_START_END, "endElement uri\t" + uri + "\tlocalName " + localName
    				+ "\tqName " + qName);
                try {
                    pop(qName);
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    throw e;
                }
            }
        public void characters(char[] ch, int start, int length)
            throws SAXException {
                try {
                    String value = new String(ch,start,length);
                    Log.logln(SHOW_ALL, "characters:\t" + value);
                    lastChars += value;
                    justPopped = false;
                } catch (RuntimeException e) {
                    e.printStackTrace();
                    throw e;
                }
            }

        public void startDTD(String name, String publicId, String systemId) throws SAXException {
            Log.logln(SHOW_ALL, "startDTD name: " + name
                    + ", publicId: " + publicId
                    + ", systemId: " + systemId
            );
            commentStack++;
        }
        public void endDTD() throws SAXException {
            Log.logln(SHOW_ALL, "endDTD");
            commentStack--;
        }
        
        public void comment(char[] ch, int start, int length) throws SAXException {
            Log.logln(SHOW_ALL, commentStack + " comment " + new String(ch, start,length));
            try {
				if (commentStack != 0) return;
				String comment0 = new String(ch, start,length);
				if (lastActiveLeafNode != null) {
					target.addComment(lastActiveLeafNode, comment0, XPathParts.Comments.LINE);
				} else {
					comment = (comment == null ? comment0 : comment + XPathParts.NEWLINE + comment0);
				}
			} catch (RuntimeException e) {
                e.printStackTrace();
                throw e;
			}
        }
        
        public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
            Log.logln(SHOW_ALL, "ignorableWhitespace length: " + length);
            for (int i = 0; i < ch.length; ++i) {
            	if (ch[i] == '\n') {
            		Log.logln(lastActiveLeafNode != null, "\\n: zeroing last leafNode: " + lastActiveLeafNode);
            		lastActiveLeafNode = null;
            	}
            }
        }
        public void startDocument() throws SAXException {
            Log.logln(SHOW_ALL, "startDocument");
            commentStack = 0; // initialize
        }

        public void endDocument() throws SAXException {
            Log.logln(SHOW_ALL, "endDocument");
            try {
				if (comment != null) target.addComment(null, comment, XPathParts.Comments.LINE);
			} catch (RuntimeException e) {
                e.printStackTrace();
                throw e;
			}
        }

        // ==== The following are just for debuggin =====

		public void elementDecl(String name, String model) throws SAXException {
        	Log.logln(SHOW_ALL, "Attribute\t" + name + "\t" + model);
        }
        public void attributeDecl(String eName, String aName, String type, String mode, String value) throws SAXException {
            Log.logln(SHOW_ALL, "Attribute\t" + eName + "\t" + aName + "\t" + type + "\t" + mode + "\t" + value);
        }
        public void internalEntityDecl(String name, String value) throws SAXException {
        	Log.logln(SHOW_ALL, "Internal Entity\t" + name + "\t" + value);
        }
        public void externalEntityDecl(String name, String publicId, String systemId) throws SAXException {
        	Log.logln(SHOW_ALL, "Internal Entity\t" + name + "\t" + publicId + "\t" + systemId);
        }

        public void notationDecl (String name, String publicId, String systemId)
        throws SAXException {
            Log.logln(SHOW_ALL, "notationDecl: " + name
            + ", " + publicId
            + ", " + systemId
            );
        }

        public void processingInstruction (String target, String data)
        throws SAXException {
            Log.logln(SHOW_ALL, "processingInstruction: " + target + ", " + data);
        }

        public void skippedEntity (String name)
        throws SAXException {
            Log.logln(SHOW_ALL, "skippedEntity: " + name);
        }

        public void unparsedEntityDecl (String name, String publicId,
                        String systemId, String notationName) {
            Log.logln(SHOW_ALL, "unparsedEntityDecl: " + name
            + ", " + publicId
            + ", " + systemId
            + ", " + notationName
            );
        }
        
        public void setDocumentLocator(Locator locator) {
            Log.logln(SHOW_ALL, "setDocumentLocator Locator " + locator);
        }
        public void startPrefixMapping(String prefix, String uri) throws SAXException {
            Log.logln(SHOW_ALL, "startPrefixMapping prefix: " + prefix +
                    ", uri: " + uri);
        }
        public void endPrefixMapping(String prefix) throws SAXException {
            Log.logln(SHOW_ALL, "endPrefixMapping prefix: " + prefix);
        }
        public void startEntity(String name) throws SAXException {
            Log.logln(SHOW_ALL, "startEntity name: " + name);
        }
        public void endEntity(String name) throws SAXException {
            Log.logln(SHOW_ALL, "endEntity name: " + name);
        }
        public void startCDATA() throws SAXException {
            Log.logln(SHOW_ALL, "startCDATA");
        }
        public void endCDATA() throws SAXException {
            Log.logln(SHOW_ALL, "endCDATA");
        }

		/* (non-Javadoc)
		 * @see org.xml.sax.ErrorHandler#error(org.xml.sax.SAXParseException)
		 */
		public void error(SAXParseException exception) throws SAXException {
			Log.logln(SHOW_ALL, "error: " + showSAX(exception));
			throw exception;
		}

		/* (non-Javadoc)
		 * @see org.xml.sax.ErrorHandler#fatalError(org.xml.sax.SAXParseException)
		 */
		public void fatalError(SAXParseException exception) throws SAXException {
			Log.logln(SHOW_ALL, "fatalError: " + showSAX(exception));
			throw exception;
		}

		/* (non-Javadoc)
		 * @see org.xml.sax.ErrorHandler#warning(org.xml.sax.SAXParseException)
		 */
		public void warning(SAXParseException exception) throws SAXException {
			Log.logln(SHOW_ALL, "warning: " + showSAX(exception));
			throw exception;
		}
    }

	/**
	 * @param exception
	 * @return
	 */
	public static String showSAX(SAXParseException exception) {
		return exception.getMessage() 
		+ ";\t SystemID: " + exception.getSystemId() 
		+ ";\t PublicID: " + exception.getPublicId() 
		+ ";\t LineNumber: " + exception.getLineNumber() 
		+ ";\t ColumnNumber: " + exception.getColumnNumber() 
		;
	}

	/**
	 * Only gets called on (mostly) resolved stuff
	 */
	private void fixAliases(Factory factory) {
		// walk through the entire tree. If we ever find an alias, 
		// remove every peer of that alias,
		// then add everything from the resolved source of the alias.
		List aliases = new ArrayList();
		for (Iterator it = xpath_value.keySet().iterator(); it.hasNext();) {
			String xpath = (String) it.next();
			if (xpath.indexOf("/alias") >= 0) { // quick check; have more rigorous one later.
				aliases.add(xpath);
			}
		}
		if (aliases.size() == 0) return;
		XPathParts parts = new XPathParts(attributeOrdering, defaultSuppressionMap);
		XPathParts fullParts = new XPathParts(attributeOrdering, defaultSuppressionMap);
		XPathParts otherParts = new XPathParts(attributeOrdering, defaultSuppressionMap);
		for (Iterator it = aliases.iterator(); it.hasNext();) {
			String xpathKey = (String) it.next();
			Value v = (Value) xpath_value.get(xpathKey);
			parts.set(xpathKey);
			int index = parts.findElement("alias"); // can have no children
			if (index < 0) continue;
			parts.trim();
			fullParts.set(v.getFullXPath());
			Map attributes = fullParts.getAttributes(index);
			fullParts.trim();
			// <alias source="<locale_ID>" path="..."/>
			String source = (String) attributes.get("source");
			if (source == null) source = key;
			otherParts.set(parts);
			String otherPath = (String) attributes.get("path");
			if (otherPath != null) {
				otherParts.addRelative(otherPath);
			}
			removeChildren(parts); 
			CLDRFile other;
			if (source.equals(key)) {
				other = this; 
			} else {
				other = factory.make(source,true);
			}
			addChildren(parts, fullParts, other, otherParts);
		}		
	}

	/**
	 * @param parts
	 * @param other
	 * @param otherParts
	 */
	private void addChildren(XPathParts parts, XPathParts fullParts, CLDRFile other, XPathParts otherParts) {
		String otherPath = otherParts + "/";
		XPathParts temp = new XPathParts(attributeOrdering, defaultSuppressionMap);
		XPathParts fullTemp = new XPathParts(attributeOrdering, defaultSuppressionMap);
		Map tempMap = new HashMap();
		for (Iterator it = other.xpath_value.keySet().iterator(); it.hasNext();) {
			String path = (String)it.next();
			if (path.startsWith(otherPath)) {
				Value value = (Value) other.xpath_value.get(path);
				temp.set(path);
				temp.replace(otherParts.size(), parts);
				fullTemp.set(value.getFullXPath());
				fullTemp.replace(otherParts.size(), fullParts);
				String newPath = temp.toString();
				value = value.changePath(fullTemp.toString());
				System.out.println("Adding*: " + path + ";\r\n\t" + newPath + ";\r\n\t" + value);
				tempMap.put(newPath, value);
				// to do, fix path
			}
		}
		xpath_value.putAll(tempMap);
	}

	/**
	 * @param parts
	 */
	private void removeChildren(XPathParts parts) {
		String mypath = parts + "/";
		for (Iterator it = xpath_value.keySet().iterator(); it.hasNext();) {
			String path = (String)it.next();
			if (path.startsWith(mypath)) {
				System.out.println("Removing: " + xpath_value.get(path));
				it.remove();
			}
		}
	}

	/**
	 * @return
	 */
	public boolean isDraft() {
		String item = (String) xpath_value.keySet().iterator().next();
		return item.indexOf("[@draft=\"true\"]") >= 0;
	}
	
	private static final String[][] NameTable = {
			{"/ldml/localeDisplayNames/languages/language[@type=\"", "\"]", "language"},
			{"/ldml/localeDisplayNames/scripts/script[@type=\"", "\"]", "script"},
			{"/ldml/localeDisplayNames/territories/territory[@type=\"", "\"]", "territory"},
			{"/ldml/localeDisplayNames/variants/variant[@type=\"", "\"]", "variant"},
			{"/ldml/numbers/currencies/currency[@type=\"", "\"]/displayName", "currency"},
			{"/ldml/numbers/currencies/currency[@type=\"", "\"]/symbol", "currency-symbol"}
	};

	public static final int LANGUAGE_NAME = 0, SCRIPT_NAME = 1, TERRITORY_NAME = 2, VARIANT_NAME = 3,
		CURRENCY_NAME = 4, CURRENCY_SYMBOL = 5;
	
	public String getName(int choice, String type, boolean skipDraft) {
		Value v = getValue(NameTable[choice][0] + type + NameTable[choice][1]);
		if (v == null || skipDraft && v.isDraft()) return null;
		return v.getStringValue();
	}
	
	LocaleIDParser lparser = new LocaleIDParser();
	
	public synchronized String getName(String locale, boolean skipDraft) {
		lparser.set(locale);
		String name = getName(LANGUAGE_NAME, lparser.getLanguage(), skipDraft);
		String sname = lparser.getScript();
		if (sname.length() != 0) name += " - " + getName(SCRIPT_NAME, sname, skipDraft);
		String extras = "";
		sname = lparser.getRegion();
		if (sname.length() != 0) {
			if (extras.length() != 0) extras += ", ";
			extras += getName(TERRITORY_NAME, sname, skipDraft);
		}
		String[] variants = lparser.getVariants();
		for (int i = 0; i < variants.length; ++i) {
			if (extras.length() != 0) extras += ", ";
			extras += getName(VARIANT_NAME, variants[i], skipDraft);
		}
		return name + (extras.length() == 0 ? "" : "(" + extras + ")");
	}
	
	public String getNameName(int choice) {
		return NameTable[choice][2];
	}
	
	public static Collection getElementOrder() {
		return elementOrdering.getOrder(); // already unmodifiable
	}
	public static Collection getAttributeOrder() {
		return attributeOrdering.getOrder(); // already unmodifiable
	}
	public static Collection getValueOrder() {
		return valueOrdering.getOrder(); // already unmodifiable
	}
	
	static MapComparator elementOrdering = (MapComparator) new MapComparator().add(new String[] {
			"ldml", "identity", "alias",
			"localeDisplayNames", "layout", "characters", "delimiters",
			"measurement", "dates", "numbers", "collations", "posix",
			"version", "generation", "language", "script", "territory",
			"variant", "languages", "scripts", "territories", "variants",
			"keys", "types", "key", "type", "orientation",
			"exemplarCharacters", "mapping", "cp", "quotationStart",
			"quotationEnd", "alternateQuotationStart",
			"alternateQuotationEnd", "measurementSystem", "paperSize",
			"height", "width", "localizedPatternChars", "calendars",
			"timeZoneNames", "months", "monthNames", "monthAbbr", "days",
			"dayNames", "dayAbbr", "week", "am", "pm", "eras",
			"dateFormats", "timeFormats", "dateTimeFormats", "fields",
			"month", "day", "minDays", "firstDay", "weekendStart",
			"weekendEnd", "eraNames", "eraAbbr", "era", "pattern",
			"displayName", "hourFormat", "hoursFormat", "gmtFormat",
			"regionFormat", "fallbackFormat", "abbreviationFallback",
			"preferenceOrdering", "default", "calendar", "monthContext",
			"monthWidth", "dayContext", "dayWidth", "dateFormatLength",
			"dateFormat", "timeFormatLength", "timeFormat",
			"dateTimeFormatLength", "dateTimeFormat", "zone", "long",
			"short", "exemplarCity", "generic", "standard", "daylight",
			"field", "relative", "symbols", "decimalFormats",
			"scientificFormats", "percentFormats", "currencyFormats",
			"currencies", "decimalFormatLength", "decimalFormat",
			"scientificFormatLength", "scientificFormat",
			"percentFormatLength", "percentFormat", "currencyFormatLength",
			"currencyFormat", "currency", "symbol", "decimal", "group",
			"list", "percentSign", "nativeZeroDigit", "patternDigit",
			"plusSign", "minusSign", "exponential", "perMille", "infinity",
			"nan", "collation", "messages", "yesstr", "nostr",
			"yesexpr", "noexpr",
			"special", }).lock();
	
	static MapComparator attributeOrdering = (MapComparator) new MapComparator().add(new String[] {
			"type", "key", "registry", "alt",
			"source", "path",
			"day", "date",
			"version", "count",
			"lines", "characters",
			"before",
			"number", "time",
			"validSubLocales",
			"standard", "references",				
			"draft",
			}).lock();
	static MapComparator valueOrdering = (MapComparator) new MapComparator().setErrorOnMissing(false).lock();
	/*
	
	//RuleBasedCollator valueOrdering = (RuleBasedCollator) Collator.getInstance(ULocale.ENGLISH);
    static {

    	
    	// others are alphabetical
       	String[] valueOrder = {
       			"full", "long", "medium", "short",
       			"abbreviated", "narrow", "wide",
    			//"collation", "calendar", "currency",
				"buddhist", "chinese", "gregorian", "hebrew", "islamic", "islamic-civil", "japanese", "direct",				
				//"japanese", "buddhist", "islamic", "islamic-civil", "hebrew", "chinese", "gregorian", "phonebook", "traditional", "direct",

				"sun", "mon", "tue", "wed", "thu", "fri", "sat", // removed, since it is a language tag
				"America/Vancouver",
				"America/Los_Angeles",
				"America/Edmonton",
				"America/Denver",
				"America/Phoenix",
				"America/Winnipeg",
				"America/Chicago",
				"America/Montreal",
				"America/New_York",
				"America/Indianapolis",
				"Pacific/Honolulu",
				"America/Anchorage",
				"America/Halifax",
				"America/St_Johns",
				"Europe/Paris",
				"Europe/Belfast",
				"Europe/Dublin",
				"Etc/GMT",
				"Africa/Casablanca",
				"Asia/Jerusalem",
				"Asia/Tokyo",
				"Europe/Bucharest",
				"Asia/Shanghai",
				};       	
    	valueOrdering.add(valueOrder).lock();
    	//StandardCodes sc = StandardCodes.make();
    }
    */
    static MapComparator dayValueOrder = (MapComparator) new MapComparator().add(new String[] {
    		"sun", "mon", "tue", "wed", "thu", "fri", "sat"}).lock();
    static MapComparator widthOrder = (MapComparator) new MapComparator().add(new String[] {
    		"abbreviated", "narrow", "wide"}).lock();
    static MapComparator lengthOrder = (MapComparator) new MapComparator().add(new String[] {
    		"full", "long", "medium", "short"}).lock();
    static MapComparator dateFieldOrder = (MapComparator) new MapComparator().add(new String[] {
    		"era", "year", "month", "week", "day", " weekday", "dayperiod",
			"hour", "minute", "second", "zone"}).lock();
    static Comparator zoneOrder = StandardCodes.make().getTZIDComparator();
    
	private static LDMLComparator ldmlComparator = new LDMLComparator();

	static class LDMLComparator implements Comparator {

		transient XPathParts a = new XPathParts(attributeOrdering, null);
		transient XPathParts b = new XPathParts(attributeOrdering, null);
		
		public void addElement(String a) {
			//elementOrdering.add(a);
		}
		public void addAttribute(String a) {
			if ( false && (a.equals("buddhist") ||
					a.equals("gregorian"))) {
				System.out.println("here2");
			}
			//attributeOrdering.add(a);
		}
		public void addValue(String a) {
			//valueOrdering.add(a);
		}
		public int compare(Object o1, Object o2) {
			int result;
			if (false && (o1.toString().indexOf("alt") >= 0 ||
					o2.toString().indexOf("alt") >= 0)) {
				System.out.println("here");
			}
			a.set((String)o1);
			b.set((String)o2);
			int minSize = a.size();
			if (b.size() < minSize) minSize = b.size();
			for (int i = 0; i < minSize; ++i) {
				String aname = a.getElement(i);
				String bname = b.getElement(i);
				if (0 != (result = elementOrdering.compare(aname, bname))) return result;
				Map am = a.getAttributes(i);
				Map bm = b.getAttributes(i);
				int minMapSize = am.size();
				if (bm.size() < minMapSize) minMapSize = bm.size();
				if (minMapSize != 0) {
					Iterator ait = am.keySet().iterator();
					Iterator bit = bm.keySet().iterator();
					for (int j = 0; j < minMapSize; ++j) {
						String akey = (String) ait.next();
						String bkey = (String) bit.next();
						if (0 != (result = attributeOrdering.compare(akey, bkey))) return result;
						String avalue = (String) am.get(akey);
						String bvalue = (String) bm.get(bkey);
						Comparator comp = valueOrdering;
						if (akey.equals("day") && aname.startsWith("weekend")) {
							comp = dayValueOrder;
						} else if (akey.equals("type")) {
							if (aname.endsWith("FormatLength ")) comp = lengthOrder;
							else if (aname.endsWith("Width")) comp = widthOrder;
							else if (aname.equals("day")) comp = dayValueOrder;
							else if (aname.equals("field")) comp = dateFieldOrder;
							else if (aname.equals("zone")) comp = zoneOrder;
						}
						if (0 != (result = comp.compare(avalue, bvalue))) return result;
					}
				}
				if (am.size() < bm.size()) return -1;
				if (am.size() > bm.size()) return 1;				
			}
			if (a.size() < b.size()) return -1;
			if (a.size() > b.size()) return 1;
			return 0;
		}		
	}
	
	public final static Map defaultSuppressionMap; 
	static {
		String[][] data = {
				{"ldml", "version", "1.2"},
				{"orientation", "characters", "left-to-right"},
				{"orientation", "lines", "top-to-bottom"},
				{"weekendStart", "time", "00:00"},
				{"weekendEnd", "time", "24:00"},
				{"dateFormat", "type", "standard"},
				{"timeFormat", "type", "standard"},
				{"dateTimeFormat", "type", "standard"},
				{"decimalFormat", "type", "standard"},
				{"scientificFormat", "type", "standard"},
				{"percentFormat", "type", "standard"},
				{"currencyFormat", "type", "standard"},
				{"pattern", "type", "standard"},
				{"currency", "type", "standard"},
				{"collation", "type", "standard"},
		};
		Map tempmain = new HashMap();
		for (int i = 0; i < data.length; ++i) {
			Map temp = (Map) tempmain.get(data[i][0]);
			if (temp == null) {
				temp = new HashMap();
				tempmain.put(data[i][0], temp);
			}
			temp.put(data[i][1], data[i][2]);
		}
		defaultSuppressionMap = Collections.unmodifiableMap(tempmain);
	}
}
