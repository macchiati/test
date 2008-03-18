//
//  DataPod.java
//
//  Created by Steven R. Loomis on 18/11/2005.
//  Copyright 2005-2008 IBM. All rights reserved.
//

//  TODO: this class now has lots of knowledge about specific data types.. so does SurveyMain
//  Probably, it should be concentrated in one side or another- perhaps SurveyMain should call this
//  class to get a list of displayable items?

package org.unicode.cldr.web;
import org.unicode.cldr.util.*;
import org.unicode.cldr.icu.LDMLConstants;
import org.unicode.cldr.test.*;
import java.util.*;
import java.util.regex.*;

import com.ibm.icu.dev.test.util.ElapsedTimer;
import com.ibm.icu.text.Collator;
import com.ibm.icu.util.ULocale;
import com.ibm.icu.text.RuleBasedCollator;

/** A data section represents a group of related data that will be displayed to users in a list
 * such as, "all of the language display names contained in the en_US locale".
 * It is sortable, as well, and has some level of persistence.
 * This class was formerly, and unfortunately, named DataPod
 **/

public class DataSection extends Registerable {

    /**
     * Trace in detail time taken to populate?
     */
    private static final boolean TRACE_TIME=false;
    
    /**
     * Show time taken to populate?
     */
    private static final boolean SHOW_TIME= true || TRACE_TIME;

    /**
     * Warn user why these messages are showing up.
     */
    static {
        if(TRACE_TIME==true) {
            System.err.println("DataSection: Note, TRACE_TIME is TRUE");
        }
    }

    long touchTime = -1; // when has this pod been hit?
    
    public void touch() {
        touchTime = System.currentTimeMillis();
    }
    public long age() {
        return System.currentTimeMillis() - touchTime;
    }
    // UI strings
    boolean canName = true;
    boolean simple = false; // is it a 'simple code list'?
    
    public static final String DATASECTION_MISSING = "Inherited";
    public static final String DATASECTION_NORMAL = "Normal";
    public static final String DATASECTION_PRIORITY = "Priority";
    public static final String DATASECTION_PROPOSED = "Proposed";
    public static final String DATASECTION_VETPROB = "Vetting Issue";

    public static final String EXEMPLAR_ONLY = "//ldml/dates/timeZoneNames/zone/*/exemplarCity";
    public static final String EXEMPLAR_EXCLUDE = "!exemplarCity";
    public static final String EXEMPLAR_PARENT = "//ldml/dates/timeZoneNames/zone";
    
    public String[] LAYOUT_INTEXT_VALUES = { "titlecase-words", "titlecase-firstword", "lowercase-words", "mixed" }; // layout/inText/* - from UTS35
    public String[] LAYOUT_INLIST_VALUES = { "titlecase-words", "titlecase-firstword", "lowercase-words", "mixed"}; // layout/inList/* - from UTS35
    public String[] METAZONE_COMMONLYUSED_VALUES = { "true","false" }; // layout/inText/* - from UTS35

    public String xpathPrefix = null;
    
//    public boolean exemplarCityOnly = false;
    
    private String fieldHash; // prefix string used for calculating html fields
    private SurveyMain sm;
    
    public boolean hasExamples = false;
    
    public ExampleGenerator exampleGenerator = null;

    public String intgroup; 
    DataSection(SurveyMain sm, String loc, String prefix) {
        super(sm.lcr,loc); // initialize call to LCR

        this.sm = sm;
        xpathPrefix = prefix;
        fieldHash =  CookieSession.cheapEncode(sm.xpt.getByXpath(prefix));
        intgroup = new ULocale(loc).getLanguage(); // calculate interest group
    }
    private static int n =0;
    protected static synchronized int getN() { return ++n; } // serial number
        
    /** 
     * This class represents an Example box, so that it can be stored and restored.
     */ 
    public class ExampleEntry {

        public String hash = null;

        public DataSection section;
        public DataSection.DataRow dataRow;
        public DataRow.CandidateItem item;
        public CheckCLDR.CheckStatus status;
        
        public ExampleEntry(DataSection section, DataRow p, DataRow.CandidateItem item, CheckCLDR.CheckStatus status) {
            this.section = section;
            this.dataRow = p;
            this.item = item;
            this.status = status;

            hash = CookieSession.cheapEncode(DataSection.getN()) +  // unique serial #- covers item, status..
                this.section.fieldHash(p);   /* fieldHash ensures that we don't get the wrong field.. */
        }
    }
    Hashtable exampleHash = new Hashtable(); // hash of examples
    
    /**
     * Enregister an ExampleEntry
     */
    ExampleEntry addExampleEntry(ExampleEntry e) {
        synchronized(exampleHash) {
            exampleHash.put(e.hash,e);
        }
        return e; // for the hash.
    }
    
    /**
     * Given a hash, see addExampleEntry, retrieve the ExampleEntry which has pod, pea, item and status
     */
    ExampleEntry getExampleEntry(String hash) {
        synchronized(exampleHash) {
            return (DataSection.ExampleEntry)exampleHash.get(hash);
        }
    }
    
    /* get a short key for use in fields */
    public String fieldHash(DataRow p) {
        return fieldHash + p.fieldHash();
    }

    public String xpath(DataRow p) {
        String path = xpathPrefix;
        if(path == null) {
            throw new InternalError("Can't handle mixed peas with no prefix");
        }
        if(p.xpathSuffix == null) {
            if(p.type != null) {
                path = path + "[@type='" + p.type +"']";
            }
            if(p.altType != null) {
                path = path + "[@alt='" + p.altType +"']";
            }
        } else {
//            if(p.xpathSuffix.startsWith("[")) {
                return xpathPrefix +  p.xpathSuffix;
//            } else {
//                return xpathPrefix+"/"+p.xpathSuffix;
//            }
        }
        
        return path;
    }
        
    static Collator getOurCollator() {
        RuleBasedCollator rbc = 
            ((RuleBasedCollator)Collator.getInstance());
        rbc.setNumericCollation(true);
        return rbc;
    }
    
    final Collator myCollator = getOurCollator();
    
    /**
     * This class represents a "row" of data - a single distinguishing xpath
     * This class was formerly (and unfortunately) named "Pea"
     * @author srl
     *
     */
    public class DataRow {
        DataRow parentRow = this; // parent - defaults to self if it is a super pea (i.e. parent without any alt)
        
        // what kind of pea is this?
        public boolean confirmOnly = false; // if true: don't accept new data, this pea is something strange.
        public boolean zoomOnly = false; // if true - don't show any editing in the zoomout view, they must zoom in.
        public DataRow toggleWith = null; // pea is a TOGGLE ( true / false ) with another pea.   Special rules apply.
        public boolean toggleValue = false;
        String[] valuesList = null; // if non null - list of acceptable values.
        public AttributeChoice attributeChoice = null; // pea is an attributed list of items
        
        public String type = null;
        public String uri = null; // URI for the type
        
        public String xpathSuffix = null; // if null:  prefix+type is sufficient (simple list).  If non-null: mixed Pod, prefix+suffix is required and type is informative only.
        public String displayName = null;
        public String altType = null; // alt type (NOT to be confused with -proposedn)
        int base_xpath = -1;
        
        // true even if only the non-winning subitems have tests.
        boolean hasTests = false;

        // the xpath id of the winner. If no winner or n/a, -1. 
        int winningXpathId = -1;
        
        // these apply to the 'winning' item, if applicable
        boolean hasErrors = false;
        boolean hasWarnings = false;
        
        Vetting.Status confirmStatus = Vetting.Status.INDETERMINATE;
        
        // do any items have warnings or errs?
        boolean anyItemHasWarnings = false;
        boolean anyItemHasErrors = false;
        
        // Do some items alias to a different base xpath or locale?
        String aliasFromLocale  = null; // locale, if different
        int aliasFromXpath      = -1;   // xpath, if different
        
        boolean hasProps = false;
        boolean hasMultipleProposals = false;
        boolean hasInherited = false;
        public int allVoteType = 0; // bitmask of all voting types included
        public int voteType = 0; // status of THIS item
        public int reservedForSort = -1; // reserved to use in collator.
//        String inheritFrom = null;
//        String pathWhereFound = null;
        /**
         * The Item is a particular alternate which could be chosen 
         * It was unfortunately previously named "Item"
         */
        public class CandidateItem implements java.lang.Comparable {
            String pathWhereFound = null;
            String inheritFrom = null;
            boolean isParentFallback = false; // true if it is not actually part of this locale,but is just the parent fallback ( Pea.inheritedValue );
            public String altProposed = null; // proposed part of the name (or NULL for nondraft)
            public int submitter = -1; // if this was submitted via ST, record user id. ( NOT from XML - in other words, we won't be parsing 'proposed-uXX' items. ) 
            public String value = null; // actual value
            public int id = -1; // id of CLDR_DATA table row
            public List tests = null;
            boolean itemErrors = false;
            public Vector examples = null; 
            //public List examplesList = null;
            String references = null;
            String xpath = null;
            int xpathId = -1;
            boolean isFallback = false; // item is from the parent locale - don't consider it a win.
            
            public Set<UserRegistry.User> votes = null; // Set of Users who voted for this.
            boolean checkedVotes = false;
            
            public Set<UserRegistry.User> getVotes() {
                if(!checkedVotes) {
                    if(!isFallback) {
                        votes = sm.vet.gatherVotes(locale, xpath);
                    }
                    checkedVotes = true;
                }
                return votes;
            }
            
            public String example = "";
            
            public String toString() { 
                return "{Item v='"+value+"', altProposed='"+altProposed+"', inheritFrom='"+inheritFrom+"'}";
            }
			
			public int compareTo(Object other) {
                if(other == this) {
                    return 0;
                }
				CandidateItem i = (CandidateItem) other;
				int rv = value.compareTo(i.value);
				if(rv == 0) {
					rv = xpath.compareTo(i.xpath);
				}
				return rv;
			}
          
            /* return true if any valid tests were found */
            public boolean setTests(List testList) {
                tests = testList;
                // only consider non-example tests as notable.
                boolean weHaveTests = false;
                int errorCount = 0;
                int warningCount =0 ;
                for (Iterator it3 = tests.iterator(); it3.hasNext();) {
                    CheckCLDR.CheckStatus status = (CheckCLDR.CheckStatus) it3.next();
                    if(status.getType().equals(status.exampleType)) {
                        //throw new InternalError("Not supposed to be any examples here.");
                    /*
                        if(myItem.examples == null) {
                            myItem.examples = new Vector();
                        }
                        myItem.examples.add(addExampleEntry(new ExampleEntry(this,p,myItem,status)));
                        */
                    } else /* if (!(isCodeFallback &&
                        (status.getCause() instanceof org.unicode.cldr.test.CheckForExemplars))) */ { 
                        // skip codefallback exemplar complaints (i.e. 'JPY' isn't in exemplars).. they'll show up in missing
                        weHaveTests = true;
                        if(status.getType().equals(status.errorType)) {
                            errorCount++;
                        } else if(status.getType().equals(status.warningType)) {
                            warningCount++;
                        }
                    }
                }
                if(weHaveTests) {
                    /* pea */ hasTests = true;
                    parentRow.hasTests = true;
                                        
                    if(((winningXpathId==-1)&&(xpathId==base_xpath)) || (xpathId == winningXpathId)) {
                        if(errorCount>0) /* pea */ hasErrors = true;
                        if(warningCount>0) /* pea */ hasWarnings = true;
                        // propagate to parent
                        if(errorCount>0) /* pea */ parentRow.hasErrors = true;
                        if(warningCount>0) /* pea */ parentRow.hasWarnings = true;
                    }
                   
                    if(errorCount>0) /* pea */ { itemErrors=true;  anyItemHasErrors = true;  parentRow.anyItemHasErrors = true; }
                    if(warningCount>0) /* pea */ anyItemHasWarnings = true;
                    // propagate to parent
                    if(warningCount>0) /* pea */ parentRow.anyItemHasWarnings = true;
                }
                return weHaveTests;
            }
        }
        
        CandidateItem previousItem = null;
        CandidateItem inheritedValue = null; // vetted value inherited from parent
        
        public String toString() {
            return "{Pea t='"+type+"', n='"+displayName+"', x='"+xpathSuffix+"', item#='"+items.size()+"'}";
        }
        
        public Set items = new TreeSet(new Comparator() {
                    public int compare(Object o1, Object o2){
                        CandidateItem p1 = (CandidateItem) o1;
                        CandidateItem p2 = (CandidateItem) o2;
                        if(p1==p2) { 
                            return 0;
                        }
                        if((p1.altProposed==null)&&(p2.altProposed==null)) return 0;
                        if(p1.altProposed == null) return -1;
                        if(p2.altProposed == null) return 1;
                        return myCollator.compare(p1.altProposed, p2.altProposed);
                    }
                });
        public CandidateItem addItem(String value, String altProposed, List tests) {
            CandidateItem pi = new CandidateItem();
            pi.value = value;
            pi.altProposed = altProposed;
            pi.tests = tests;
            items.add(pi);
///*srl*/            if(type.indexOf("Chicago")>-1) {
//                System.out.println(type+"  v: " + pi.value);
//            }
            
            return pi;
        }
        
        String myFieldHash = null;
        public String fieldHash() { // deterministic. No need for sync.
            if(myFieldHash == null) {
                String ret = "";
                if(type != null) {
                    ret = ret + ":" + CookieSession.cheapEncode(type.hashCode());
                }
                if(xpathSuffix != null) {
                    ret = ret + ":" + CookieSession.cheapEncode(xpathSuffix.hashCode());
                }
                if(altType != null) {
                    ret = ret + ":" + CookieSession.cheapEncode(altType.hashCode());
                }
                myFieldHash = ret;
            }
            return myFieldHash;
        }
        
        Hashtable subRows = null;
        
        public DataRow getSubDataRow(String altType) {
            if(altType == null) {
                return this;
            }
            if(subRows == null) {
                subRows = new Hashtable();
            }

            DataRow p = (DataRow)subRows.get(altType);
            if(p==null) {
                p = new DataRow();
                p.type = type;
                p.altType = altType;
                p.parentRow = this;
                subRows.put(altType, p);
            }
            return p;
        }
        
        void updateInheritedValue(CLDRFile vettedParent) {
            updateInheritedValue(vettedParent,null, null);
        }
        
        void updateInheritedValue(CLDRFile vettedParent, CheckCLDR checkCldr, Map options) {
            long lastTime = System.currentTimeMillis();
            if(vettedParent == null) {
                return;
            }
            
            if(base_xpath == -1) {
                return;
            }
            

            String xpath = sm.xpt.getById(base_xpath);
            if(TRACE_TIME) System.err.println("@@0:"+(System.currentTimeMillis()-lastTime));
            if(xpath == null) {
                return;
            }
            
            if((vettedParent != null) && (inheritedValue == null)) {
                String value = vettedParent.getStringValue(xpath);
                if(TRACE_TIME) System.err.println("@@1:"+(System.currentTimeMillis()-lastTime));
                
                if(value != null) {
                    inheritedValue = new CandidateItem();
                    if(TRACE_TIME) System.err.println("@@2:"+(System.currentTimeMillis()-lastTime));
                    inheritedValue.isParentFallback=true;

                    CLDRFile.Status sourceLocaleStatus = new CLDRFile.Status();
                    if(TRACE_TIME) System.err.println("@@3:"+(System.currentTimeMillis()-lastTime));
                    String sourceLocale = vettedParent.getSourceLocaleID(xpath, sourceLocaleStatus);
                    if(TRACE_TIME) System.err.println("@@4:"+(System.currentTimeMillis()-lastTime));

                    inheritedValue.inheritFrom = sourceLocale;
                    
                    if(sourceLocaleStatus!=null && sourceLocaleStatus.pathWhereFound!=null && !sourceLocaleStatus.pathWhereFound.equals(xpath)) {
                        inheritedValue.pathWhereFound = sourceLocaleStatus.pathWhereFound;
                        if(TRACE_TIME) System.err.println("@@5:"+(System.currentTimeMillis()-lastTime));
                        
                        // set up Pod alias-ness
                        aliasFromLocale = sourceLocale;
                        aliasFromXpath = sm.xpt.xpathToBaseXpathId(sourceLocaleStatus.pathWhereFound);
                        if(TRACE_TIME) System.err.println("@@6:"+(System.currentTimeMillis()-lastTime));
                    }
                    
                    inheritedValue.value = value;
                    inheritedValue.xpath = xpath;
                    inheritedValue.xpathId = base_xpath;
                    inheritedValue.isFallback = true;                    
                } else {
                   // throw new InternalError("could not get inherited value: " + xpath);
                }
            }
            
            if((checkCldr != null) && (inheritedValue != null) && (inheritedValue.tests == null)) {
                if(TRACE_TIME) System.err.println("@@7:"+(System.currentTimeMillis()-lastTime));
                List iTests = new ArrayList();
                checkCldr.check(xpath, xpath, inheritedValue.value, options, iTests);
                if(TRACE_TIME) System.err.println("@@8:"+(System.currentTimeMillis()-lastTime));
             //   checkCldr.getExamples(xpath, fullPath, value, ctx.getOptionsMap(), examplesResult);
                if(!iTests.isEmpty()) {
                    inheritedValue.setTests(iTests);
                    if(TRACE_TIME) System.err.println("@@9:"+(System.currentTimeMillis()-lastTime));
                }
            }
            if(TRACE_TIME) System.err.println("@@10:"+(System.currentTimeMillis()-lastTime));
        }
 
        void setShimTests(int base_xpath,String base_xpath_string,CheckCLDR checkCldr,Map options) {
            CandidateItem shimItem = inheritedValue;
            
            if(shimItem == null) {
                shimItem = new CandidateItem();

                shimItem.value = null;
                shimItem.xpath = base_xpath_string;
                shimItem.xpathId = base_xpath;
                shimItem.isFallback = false;
        
                List iTests = new ArrayList();
                checkCldr.check(base_xpath_string, base_xpath_string, null, options, iTests);
                if(!iTests.isEmpty()) {
                    // Got a bite.
                    if(shimItem.setTests(iTests)) {
                        // had valid tests
                        inheritedValue = shimItem;
                        inheritedValue.isParentFallback = true;
                    }
                }
            } else {
                if(SurveyMain.isUnofficial) System.err.println("already have inherited @ " + base_xpath_string);
            }
        }
       
        private String replaceEndWith(String str, String oldEnd, String newEnd) {
            if(!str.endsWith(oldEnd)) {
                throw new InternalError("expected " + str + " to end with " + oldEnd);
            }
            return str.substring(0,str.length()-oldEnd.length())+newEnd;
        }
        
        void updateToggle(String path, String attribute) {
            if(true == true) {
                confirmOnly = true;
                return; /// Disable toggles - for now.
            }
            
            
            
            XPathParts parts = new XPathParts(null,null);
            parts.initialize(path);
            String lelement = parts.getElement(-1);
            String eAtt = parts.findAttributeValue(lelement, attribute);
            if(eAtt == null) {
                System.err.println(this + " - no attribute " + attribute + " in " + path);
            }
            toggleValue = eAtt.equals("true");
            
            //System.err.println("Pea: " + type + " , toggle of val: " + myValue + " at xpath " + path);
            String myValueSuffix = "[@"+attribute+"=\""+toggleValue+"\"]";
            String notMyValueSuffix = "[@"+attribute+"=\""+!toggleValue+"\"]";
            
            if(!type.endsWith(myValueSuffix)) {
                throw new InternalError("toggle: expected "+ type + " to end with " + myValueSuffix);
            }
            
            String typeNoValue =  type.substring(0,type.length()-myValueSuffix.length());
            String notMyType = typeNoValue+notMyValueSuffix;
            
            
            DataRow notMyDataRow = getDataRow(notMyType);
            if(notMyDataRow.toggleWith == null) {
                notMyDataRow.toggleValue = !toggleValue;
                notMyDataRow.toggleWith = this;

                String my_base_xpath_string = sm.xpt.getById(base_xpath);
                String not_my_base_xpath_string = replaceEndWith(my_base_xpath_string, myValueSuffix, notMyValueSuffix);
                notMyDataRow.base_xpath = sm.xpt.getByXpath(not_my_base_xpath_string);

                notMyDataRow.xpathSuffix = replaceEndWith(xpathSuffix,myValueSuffix,notMyValueSuffix);

                //System.err.println("notMyPea.xpath = " + xpath(notMyPea));
            }
            
            toggleWith = notMyDataRow;
            
        }
    }

    Hashtable rowsHash = new Hashtable(); // hashtable of type->Pea
 
    /**
     * get all peas.. unsorted.
     */
    public Collection getAll() {
        return rowsHash.values();
    }
    
    public abstract class PartitionMembership {
        public abstract boolean isMember(DataRow p);
    };
    public class Partition {

        public PartitionMembership pm;

        public String name; // name of this partition
        public int start; // first item
        public int limit; // after last item

        public Partition(String n, int s, int l) {
            name = n;
            start = s;
            limit = l;
        }
        
        public Partition(String n, PartitionMembership pm) {
            name = n;
            this.pm = pm;
            start = -1;
            limit = -1;
        }
        
        public String toString() {
            return name + " - ["+start+".."+limit+"]";
        }

    };

    /** 
     * A class representing a list of peas, in sorted and divided order.
     */
    public class DisplaySet {
        public int size() {
            return peas.size();
        }
        String sortMode = null;
        public boolean canName = true; // can use the 'name' view?
        public List peas; // list of peas in sorted order
        public List displayPeas; // list of Strings suitable for display
        /**
         * Partitions divide up the peas into sets, such as 'proposed', 'normal', etc.
         * The 'limit' is one more than the index number of the last item.
         * In some cases, there is only one partition, and its name is null.
         */
        
        public Partition partitions[];  // display group partitions.  Might only contain one entry:  {null, 0, <end>}.  Otherwise, contains a list of entries to be named separately

        public DisplaySet(List myPeas, List myDisplayPeas, String sortMode) {
            this.sortMode = sortMode;
            
            peas = myPeas;
            displayPeas = myDisplayPeas;

            /*
            if(matcher != null) {
                List peas = new ArrayList();
                List displayPeas = new ArrayList();
                peas.addAll(myPeas);
                displayPeas.addAll(displayPeas);
                
                for(Object o : myPeas) {
                    Pea p = (Pea)o;
                    if(!matcher.matcher(p.type).matches()) {
                        peas.remove(o);
                    }
                }
                for(Object o : myDisplayPeas) {
                    Pea p = (Pea)o;
                    if(!matcher.matcher(p.type).matches()) {
                        displayPeas.remove(o);
                    }
                }
                System.err.println("now " +peas.size()+"/"+displayPeas.size()+" versus " + myPeas.size()+"/"+myDisplayPeas.size());
            }
            */
            
            // fetch partitions..
            Vector v = new Vector();
            if(sortMode.equals(SurveyMain.PREF_SORTMODE_WARNING)) { // priority
                Partition testPartitions[] = SurveyMain.isPhaseSubmit()?createSubmitPartitions():
                                                                           createVettingPartitions();
                // find the starts
                int lastGood = 0;
                DataRow peasArray[] = null;
                peasArray = (DataRow[])peas.toArray(new DataRow[0]);
                for(int i=0;i<peasArray.length;i++) {
                    DataRow p = peasArray[i];
                                        
                    for(int j=lastGood;j<testPartitions.length;j++) {
                        if(testPartitions[j].pm.isMember(p)) {
                            if(j>lastGood) {
                                lastGood = j;
                            }
                            if(testPartitions[j].start == -1) {
                                testPartitions[j].start = i;
                            }
                            break; // sit here until we fail membership
                        }
                        
                        if(testPartitions[j].start != -1) {
                            testPartitions[j].limit = i;
                        }
                    }
                }
                // catch the last item
                if((testPartitions[lastGood].start != -1) &&
                    (testPartitions[lastGood].limit == -1)) {
                    testPartitions[lastGood].limit = peas.size(); // limit = off the end.
                }
                    
                for(int j=0;j<testPartitions.length;j++) {
                    if(testPartitions[j].start != -1) {
						if(testPartitions[j].start!=0 && v.isEmpty()) {
//							v.add(new Partition("Other",0,testPartitions[j].start));
						}
                        v.add(testPartitions[j]);
                    }
                }
            } else {
                // default partition
                v.add(new Partition(null, 0, peas.size()));
            }
            partitions = (Partition[])v.toArray(new Partition[0]); // fold it up
        }

    }

	public static String PARTITION_ERRORS = "Error Values";
	public static String CHANGES_DISPUTED = "Disputed";
	public static String PARTITION_UNCONFIRMED = "Unconfirmed";
	public static String TENTATIVELY_APPROVED = "Tentatively Approved";
	public static String STATUS_QUO = "Status Quo";
    
    public static final String VETTING_PROBLEMS_LIST[] = { 
        PARTITION_ERRORS,
        CHANGES_DISPUTED,
        PARTITION_UNCONFIRMED };


    private Partition[] createVettingPartitions() {
        Partition theVetPartitions[] = 
        {                 
                new Partition(PARTITION_ERRORS, 
                    new PartitionMembership() { 
                        public boolean isMember(DataRow p) {
                            return (p.hasErrors&&!p.hasProps) && ((p.allVoteType==0) || ((p.allVoteType & Vetting.RES_NO_VOTES)>0)
                                    || ((p.allVoteType & Vetting.RES_NO_CHANGE)>0) ) ||
                                       ((p.allVoteType & Vetting.RES_ERROR)>0) || p.hasErrors;
                        }
                    }),
                new Partition(CHANGES_DISPUTED, 
                    new PartitionMembership() { 
                        public boolean isMember(DataRow p) {
                            return ((p.allVoteType & Vetting.RES_DISPUTED)>0) ;
                        }
                    }),                  
                new Partition(PARTITION_UNCONFIRMED, 
                    new PartitionMembership() { 
                        public boolean isMember(DataRow p) {
                            return (p.allVoteType>0) &&   // make sure it actually has real items.
                                (((p.confirmStatus!=Vetting.Status.APPROVED &&
                                    p.confirmStatus!=Vetting.Status.INDETERMINATE)) ||
                                    
                                    p.hasProps&&(((p.allVoteType & Vetting.RES_INSUFFICIENT)>0) ||
                                                ((p.allVoteType & Vetting.RES_NO_VOTES)>0)));
                        }
                    }),                  
                new Partition(TENTATIVELY_APPROVED, 
                    new PartitionMembership() { 
                        public boolean isMember(DataRow p) {
                            return ((p.hasProps)&&
                                ((p.allVoteType & Vetting.RES_BAD_MASK)==0)&&
                                    (p.allVoteType>0) &&
                                        (p.confirmStatus==Vetting.Status.APPROVED) ); // has proposed, and has a 'good' mark. Excludes by definition RES_NO_CHANGE
                        }
                    }),
                new Partition(STATUS_QUO, 
                    new PartitionMembership() { 
                        public boolean isMember(DataRow p) {
                            return ((!p.hasInherited&&!p.hasProps) || // nothing to change.
                                        ((p.allVoteType & Vetting.RES_NO_CHANGE)>0)) ||
                                    (p.hasInherited&&!p.hasProps) ||
                                    (p.confirmStatus==Vetting.Status.INDETERMINATE) ||
                                    (/* !p.hasErrors&&!p.hasProps&&*/(p.allVoteType == 0));
                        }
                    }),
        };
        return theVetPartitions;
    }

    private Partition[] createSubmitPartitions() {
        Partition theTestPartitions[] = 
        {                 
                new Partition("Errors", 
                    new PartitionMembership() { 
                        public boolean isMember(DataRow p) {
                            return (p.hasErrors);
                        }
                    }),
                new Partition("Warnings", 
                    new PartitionMembership() { 
                        public boolean isMember(DataRow p) {
                            return (p.hasWarnings);
                        }
                    }),
                new Partition("Unconfirmed", 
                    new PartitionMembership() { 
                        public boolean isMember(DataRow p) {
                            // == insufficient votes
                            return  (p.allVoteType == Vetting.RES_INSUFFICIENT) ||
                                (p.allVoteType == Vetting.RES_NO_VOTES);
                        }
                    }),
                new Partition("Changes Proposed: Tentatively Approved", 
                    new PartitionMembership() { 
                        public boolean isMember(DataRow p) {
                            return ((p.hasProps)&&
                                ((p.allVoteType & Vetting.RES_BAD_MASK)==0)&&
                                    (p.allVoteType>0)); // has proposed, and has a 'good' mark. Excludes by definition RES_NO_CHANGE
                        }
                    }),
                new Partition("Others", 
                    new PartitionMembership() { 
                        public boolean isMember(DataRow p) {
                            return true;
                        }
                    }),
        };
        return theTestPartitions;
    }        
    

    private Hashtable displayHash = new Hashtable();
    
    public DisplaySet getDisplaySet(String sortMode, Pattern matcher) {
        return createDisplaySet(sortMode, matcher); // don't cache.
    }

    public DisplaySet getDisplaySet(String sortMode) {
        DisplaySet aDisplaySet = (DisplaySet)displayHash.get(sortMode);
        if(aDisplaySet == null)  {
            aDisplaySet = createDisplaySet(sortMode, null);
            displayHash.put(sortMode, aDisplaySet);
        }
        return aDisplaySet;
    }
    
    private DisplaySet createDisplaySet(String sortMode, Pattern matcher) {
        DisplaySet aDisplaySet = new DisplaySet(getList(sortMode, matcher), getDisplayList(sortMode, matcher), sortMode);
        aDisplaySet.canName = canName;
        return aDisplaySet;
    }
    
    private Hashtable listHash = new Hashtable();  // hash of sortMode->pea
    
    /**
     * get a List of peas, in sorted order 
     */
    public List getList(String sortMode) {
        List aList = (List)listHash.get(sortMode);
        if(aList == null) {
            aList = getList(sortMode, null);
        }
        listHash.put(sortMode, aList);
        return aList;
    }
        
    public List getList(String sortMode, Pattern matcher) {
    //        final boolean canName = canName;
        Set newSet;
        
    //                final com.ibm.icu.text.RuleBasedCollator rbc = 
    //                    ((com.ibm.icu.text.RuleBasedCollator)com.ibm.icu.text.Collator.getInstance());
    //                rbc.setNumericCollation(true);

        
        if(sortMode.equals(SurveyMain.PREF_SORTMODE_CODE)) {
            newSet = new TreeSet(new Comparator() {
    //                        com.ibm.icu.text.Collator myCollator = rbc;
                public int compare(Object o1, Object o2){
                    DataRow p1 = (DataRow) o1;
                    DataRow p2 = (DataRow) o2;
                    if(p1==p2) { 
                        return 0;
                    }
                    return myCollator.compare(p1.type, p2.type);
                }
            });
        } else if (sortMode.equals(SurveyMain.PREF_SORTMODE_WARNING)) {
            newSet = new TreeSet(new Comparator() {
            
                int categorizeDataRow(DataRow p, Partition partitions[]) {
                    int rv = -1;
                    for(int i=0;(rv==-1)&&(i<partitions.length);i++) {
                        if(partitions[i].pm.isMember(p)) {
                            rv = i;
                        }
                    }
                    if(rv==-1) {
                    }
                    return rv;
                }
                
                final Partition[] warningSort = SurveyMain.isPhaseSubmit()?createSubmitPartitions():
                                                                       createVettingPartitions();
    //                        com.ibm.icu.text.Collator myCollator = rbc;
                public int compare(Object o1, Object o2){
                    DataRow p1 = (DataRow) o1;
                    DataRow p2 = (DataRow) o2;

                    
                    if(p1==p2) {
                        return 0;
                    }
                    
                    int rv = 0; // neg:  a < b.  pos: a> b
                    
                    if(p1.reservedForSort==-1) {
                        p1.reservedForSort = categorizeDataRow(p1, warningSort);
                    }
                    if(p2.reservedForSort==-1) {
                        p2.reservedForSort = categorizeDataRow(p2, warningSort);
                    }
                    
                    if(rv == 0) {
                        if(p1.reservedForSort < p2.reservedForSort) {
                            return -1;
                        } else if(p1.reservedForSort > p2.reservedForSort) {
                            return 1;
                        }
                    }

                   if(rv == 0) { // try to avoid a compare
                        String p1d  = null;
                        String p2d  = null;
                        if(canName) {
                          p1d = p1.displayName;
                          p2d = p2.displayName;
                        }
                        if(p1d == null ) {
                            p1d = p1.type;
                            if(p1d == null) {
                                p1d = "(null)";
                            }
                        }
                        if(p2d == null ) {
                            p2d = p2.type;
                            if(p2d == null) {
                                p2d = "(null)";
                            }
                        }
                        rv = myCollator.compare(p1d, p2d);
                    }

                    if(rv == 0) {
                        String p1d  = p1.type;
                        String p2d  = p2.type;
                        if(p1d == null ) {
                            p1d = "(null)";
                        }
                        if(p2d == null ) {
                            p2d = "(null)";
                        }
                        rv = myCollator.compare(p1d, p2d);
                    }
                                        
                    if(rv < 0) {
                        return -1;
                    } else if(rv > 0) {
                        return 1;
                    } else {
                        return 0;
                    }
                }
            });
        } else if(sortMode.equals(SurveyMain.PREF_SORTMODE_NAME)) {
            newSet = new TreeSet(new Comparator() {
    //                        com.ibm.icu.text.Collator myCollator = rbc;
                public int compare(Object o1, Object o2){
                    DataRow p1 = (DataRow) o1;
                    DataRow p2 = (DataRow) o2;
                    if(p1==p2) { 
                        return 0;
                    }
                    String p1d = p1.displayName;
                    if(p1.displayName == null ) {
                            p1d = p1.type;
    //                                throw new InternalError("item p1 w/ null display: " + p1.type);
                    }
                    String p2d = p2.displayName;
                    if(p2.displayName == null ) {
                            p2d = p2.type;
    //                                throw new InternalError("item p2 w/ null display: " + p2.type);
                    }
                    int rv = myCollator.compare(p1d, p2d);
                    if(rv == 0) {
                        p1d  = p1.type;
                        p2d  = p2.type;
                        if(p1d == null ) {
                            p1d = "(null)";
                        }
                        if(p2d == null ) {
                            p2d = "(null)";
                        }
                        rv = myCollator.compare(p1d, p2d);
                    }
                    return rv;
                }
            });
        } else {
            throw new InternalError("Unknown or unsupported sort mode: " + sortMode);
        }
        
        if(matcher == null) {
            newSet.addAll(rowsHash.values()); // sort it    
        } else {
            for(Object o : rowsHash.values()) {
                DataRow p = (DataRow)o;
                                
///*srl*/         /*if(p.type.indexOf("Australia")!=-1)*/ {  System.err.println("xp: "+p.xpathSuffix+":"+p.type+"- match: "+(matcher.matcher(p.type).matches())); }

                if(matcher.matcher(p.type).matches()) {
                    newSet.add(p);
                }
            }
        }
        
        ArrayList aList = new ArrayList(); // list it (waste here??)
        aList.addAll(newSet);
        if(matcher != null) {
///*srl*/ System.err.println("Pruned match of " + aList.size() + " items from " + peasHash.size());
        }

        return aList;
    }
    
    /** Returns a list parallel to that of getList() but of Strings suitable for display. 
    (Alternate idea: just make toString() do so on Pea.. advantage here is we can adjust for sort mode.) **/
    public List getDisplayList(String sortMode) {
        return getDisplayList(sortMode, getList(sortMode));
    }
    
    public List getDisplayList(String sortMode, Pattern matcher) {
        return getDisplayList(sortMode, getList(sortMode, matcher));
    }
    
    public List getDisplayList(String sortMode, List inPeas) {
        final List myPeas = inPeas;
        if(sortMode.equals(SurveyMain.PREF_SORTMODE_CODE)) {
            return new AbstractList() {
                private List ps = myPeas;
                public Object get(int n) {
                  return ((DataRow)ps.get(n)).type; // always code
                }
                public int size() { return ps.size(); }
            };
        } else {
            return new AbstractList() {
                private List ps = myPeas;
                public Object get(int n) {
                  DataRow p = (DataRow)ps.get(n);
                  if(p.displayName != null) {
                    return p.displayName;
                  } else {
                    return p.type;
                  } 
                  //return ((Pea)ps.get(n)).type;
                }
                public int size() { return ps.size(); }
            };
        }
    }

	/**
	 * @param ctx context to use (contains CLDRDBSource, etc.)
	 * @param locale locale
	 * @param prefix XPATH prefix
	 * @param simple if true, means that data is simply xpath+type. If false, all xpaths under prefix.
	 */
	public static DataSection make(WebContext ctx, String locale, String prefix, boolean simple) {
		DataSection section = new DataSection(ctx.sm, locale, prefix);
        section.simple = simple;
        SurveyMain.UserLocaleStuff uf = ctx.sm.getUserFile(ctx, ctx.session.user, ctx.locale);
  
        CLDRDBSource ourSrc = uf.dbSource;
        
        synchronized(ourSrc) {
            CheckCLDR checkCldr = uf.getCheck(ctx);
            if(checkCldr == null) {
                throw new InternalError("checkCldr == null");
            }
			
            com.ibm.icu.dev.test.util.ElapsedTimer cet;
            if(SHOW_TIME) {
                cet= new com.ibm.icu.dev.test.util.ElapsedTimer();
                System.err.println("Begin populate of " + locale + " // " + prefix+":"+ctx.defaultPtype());
            }
            CLDRFile baselineFile = ctx.sm.getBaselineFile();
            section.populateFrom(ourSrc, checkCldr, baselineFile,ctx.getOptionsMap());
			int popCount = section.getAll().size();
/*            if(SHOW_TIME) {
                System.err.println("DP: Time taken to populate " + locale + " // " + prefix +":"+ctx.defaultPtype()+ " = " + et + " - Count: " + pod.getAll().size());
            }*/
            section.ensureComplete(ourSrc, checkCldr, baselineFile, ctx.getOptionsMap());
            if(SHOW_TIME) {
				int allCount = section.getAll().size();
                System.err.println("Populate+complete " + locale + " // " + prefix +":"+ctx.defaultPtype()+ " = " + cet + " - Count: " + popCount+"+"+(allCount-popCount)+"="+allCount);
            }
            section.exampleGenerator = new ExampleGenerator(new CLDRFile(ourSrc,true), ctx.sm.fileBase + "/../supplemental/");
            section.exampleGenerator.setVerboseErrors(ctx.sm.twidBool("ExampleGenerator.setVerboseErrors"));
        }
		return section;
	}
    
    private static boolean isInitted = false;
    
    private static Pattern typeReplacementPattern;
    private static Pattern keyTypeSwapPattern;
    private static Pattern noisePattern;
    private static Pattern mostPattern;
    private static Pattern excludeAlways;
    private static Pattern needFullPathPattern; // items that need getFullXpath 
    
    private static final         String fromto[] = {   "^days/(.*)/(sun)$",  "days/1-$2/$1",
                              "^days/(.*)/(mon)$",  "days/2-$2/$1",
                              "^days/(.*)/(tue)$",  "days/3-$2/$1",
                              "^days/(.*)/(wed)$",  "days/4-$2/$1",
                              "^days/(.*)/(thu)$",  "days/5-$2/$1",
                              "^days/(.*)/(fri)$",  "days/6-$2/$1",
                              "^days/(.*)/(sat)$",  "days/7-$2/$1",
                              "^months/(.*)/month/([0-9]*)$", "months/$2/$1",
                              "^([^/]*)/months/(.*)/month/([0-9]*)$", "$1/months/$3/$2",
                              "^eras/(.*)/era/([0-9]*)$", "eras/$2/$1",
                              "^([^/]*)/eras/(.*)/era/([0-9]*)$", "$1/eras/$3/$2",
                              "^([ap]m)$","ampm/$1",
                              "^quarter/(.*)/quarter/([0-9]*)$", "quarter/$2/$1",
                              "^([^/]*)/([^/]*)/time$", "$1/time/$2",
                              "^([^/]*)/([^/]*)/date", "$1/date/$2",
                              "/alias$", "",
                              "displayName\\[@count=\"([^\"]*)\"\\]$", "displayName ($1)",
                              "dateTimes/date/availablesItem", "available date formats:",
                             /* "/date/availablesItem.*@_q=\"[0-9]*\"\\]\\[@id=\"([0-9]*)\"\\]","/availableDateFormats/$1" */
//                              "/date/availablesItem.*@_q=\"[0-9]*\"\\]","/availableDateFormats"
                            };
    private static Pattern fromto_p[] = new Pattern[fromto.length/2];
                            

    private static synchronized void init() {
        if(!isInitted) {
         typeReplacementPattern = Pattern.compile("\\[@(?:type|key)=['\"]([^'\"]*)['\"]\\]");
         keyTypeSwapPattern = Pattern.compile("([^/]*)/(.*)");
         noisePattern = Pattern.compile( // 'noise' to be removed
                                                    "^/|"+
                                                    "Formats/currencyFormatLength/currencyFormat|"+
                                                    "Formats/currencySpacing|"+
                                                    "Formats/percentFormatLength/percentFormat|"+
                                                    "Formats/decimalFormatLength/decimalFormat|"+
                                                    "Formats/scientificFormatLength/scientificFormat|"+
                                                    "dateTimes/dateTimeLength/|"+
                                                    "Formats/timeFormatLength|"+
                                                    "/timeFormats/timeFormatLength|"+
                                                    "/timeFormat|"+
                                                    "s/quarterContext|"+
                                                    "/dateFormats/dateFormatLength|"+
                                                    "/pattern|"+
                                                    "/monthContext|"+
                                                    "/monthWidth|"+
                                                    "/timeLength|"+
                                                    "/quarterWidth|"+
                                                    "/dayContext|"+
                                                    "/dayWidth|"+
//                                                    "day/|"+
//                                                    "date/|"+
                                                    "Format|"+
                                                    "s/field|"+
                                                    "\\[@draft=\"true\"\\]|"+ // ???
                                                    "\\[@alt=\"[^\"]*\"\\]|"+ // ???
                                                    "/displayName$|"+  // for currency
                                                    "/standard/standard$"     );
         mostPattern = Pattern.compile("^//ldml/localeDisplayNames.*|"+
                                              "^//ldml/characters/exemplarCharacters.*|"+
                                              "^//ldml/numbers.*|"+
                                              "^//ldml/references.*|"+
                                              "^//ldml/dates/timeZoneNames/zone.*|"+
                                              "^//ldml/dates/timeZoneNames/metazone.*|"+
                                              "^//ldml/dates/calendar.*|"+
                                              "^//ldml/identity.*");
        // what to exclude under 'misc' and calendars
         excludeAlways = Pattern.compile("^//ldml/segmentations.*|"+
                                                "^//ldml/measurement.*|"+
                                                ".*week/minDays.*|"+
                                                ".*week/firstDay.*|"+
                                                ".*/usesMetazone.*|"+
                                                ".*week/weekendEnd.*|"+
                                                ".*week/weekendStart.*|" +
//                                                "^//ldml/dates/.*localizedPatternChars.*|" +
                                                "^//ldml/posix/messages/.*expr$|" +
                                                "^//ldml/dates/timeZoneNames/.*/GMT.*exemplarCity$|" +
                                                "^//ldml/dates/.*default");// no defaults
                                                
        needFullPathPattern = Pattern.compile("^//ldml/layout/orientation$|" +
                                              ".*/alias");
        
            int pn;
            for(pn=0;pn<fromto.length/2;pn++) {
                fromto_p[pn]= Pattern.compile(fromto[pn*2]);
            }

        }
        isInitted = true;
    }
    
    public static final String FAKE_FLEX_THING = "available date formats: add NEW item";
    public static final String FAKE_FLEX_SUFFIX = "dateTimes/availableDateFormats/dateFormatItem[@id=\"NEW\"]";
    public static final String FAKE_FLEX_XPATH = "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/availableFormats/dateFormatItem";
    
    private void populateFrom(CLDRDBSource src, CheckCLDR checkCldr, CLDRFile baselineFile, Map options) {
        init();
        XPathParts xpp = new XPathParts(null,null);
//        System.out.println("[] initting from pod " + locale + " with prefix " + xpathPrefix);
        CLDRFile aFile = new CLDRFile(src, true);
        XPathParts pathParts = new XPathParts(null, null);
        XPathParts fullPathParts = new XPathParts(null, null);
        List examplesResult = new ArrayList();
        long lastTime = -1;
        long longestTime = -1;
        String longestPath = "NONE";
        long nextTime = -1;
        int count=0;
        long countStart = 0;
        if(SHOW_TIME) {
            lastTime = countStart = System.currentTimeMillis();
        }
        // what to exclude under 'misc'
        int t = 10;
        
        CLDRFile vettedParent = null;
        String parentLoc = WebContext.getParent(locale);
        if(parentLoc != null) {
            CLDRDBSource vettedParentSource = sm.makeDBSource(src.conn, null, new ULocale (parentLoc), true /*finalData*/);
            vettedParent = new CLDRFile(vettedParentSource,true);
        }
            
        int pn;
        String exclude = null;
        boolean excludeCurrencies = false;
        boolean excludeCalendars = false;
        boolean excludeLDN = false;
        boolean excludeGrego = false;
        boolean excludeTimeZones = false;
        boolean excludeMetaZones = false;
        boolean useShorten = false; // 'shorten' xpaths instead of extracting type
        boolean keyTypeSwap = false;
        boolean hackCurrencyDisplay = false;
        boolean excludeMost = false;
        boolean doExcludeAlways = true;
        boolean isReferences = false;
        String removePrefix = null;
        if(xpathPrefix.equals("//ldml")) {
            excludeMost = true;
            useShorten = true;
            removePrefix="//ldml/";
        }else if(xpathPrefix.startsWith("//ldml/numbers")) {
            if(-1 == xpathPrefix.indexOf("currencies")) {
                doExcludeAlways=false;
                excludeCurrencies=true; // = "//ldml/numbers/currencies";
                removePrefix = "//ldml/numbers/";
                canName = false;  // sort numbers by code
                useShorten = true;
            } else {
                removePrefix = "//ldml/numbers/currencies/currency";
                useShorten = true;
                canName = false; // because symbols are included
//                hackCurrencyDisplay = true;
            }
        } else if(xpathPrefix.startsWith("//ldml/dates")) {
            useShorten = true;
            if(xpathPrefix.startsWith("//ldml/dates/timeZoneNames/zone")) {
                removePrefix = "//ldml/dates/timeZoneNames/zone";
//        System.err.println("ZZ0");
                excludeTimeZones = false;
            } else if(xpathPrefix.startsWith("//ldml/dates/timeZoneNames/metazone")) {
                removePrefix = "//ldml/dates/timeZoneNames/metazone";
                excludeMetaZones = false;
//        System.err.println("ZZ1");
            } else {
                removePrefix = "//ldml/dates/calendars/calendar";
                excludeTimeZones = true;
                excludeMetaZones = true;
                if(xpathPrefix.indexOf("gregorian")==-1) {
                    excludeGrego = true; 
                    // nongreg
                } else {
                    removePrefix = "//ldml/dates/calendars/calendar[@type=\"gregorian\"]";
                    
                    // Add the fake 'dateTimes/availableDateFormats/new'
                    DataRow myp = getDataRow(FAKE_FLEX_THING);
                    String spiel = "<i>add</i>"; //Use this item to add a new availableDateFormat
                    myp.xpathSuffix = FAKE_FLEX_SUFFIX;
                    canName=false;
                    myp.displayName = spiel;
//                    myp.addItem(spiel, null, null);
                }
            }
        } else if(xpathPrefix.startsWith("//ldml/localeDisplayNames/types")) {
            useShorten = true;
            removePrefix = "//ldml/localeDisplayNames/types/type";
            keyTypeSwap = true; //these come in reverse order  (type/key) i.e. buddhist/celander, pinyin/collation.  Reverse this for sorting...
        } else if(xpathPrefix.equals("//ldml/references")) {
            isReferences = true;
            canName = false; // disable 'view by name'  for references
        }
        List checkCldrResult = new ArrayList();
        
        // iterate over everything in this prefix ..
        Set<String> baseXpaths = new HashSet<String>();
        for(Iterator it = aFile.iterator(xpathPrefix);it.hasNext();) {
            String xpath = (String)it.next();
            baseXpaths.add(xpath);
        }
        Set<String> allXpaths = new HashSet<String>();
        Set<String> extraXpaths = new HashSet<String>();

        allXpaths.addAll(baseXpaths);
        aFile.getExtraPaths(xpathPrefix,extraXpaths);
        extraXpaths.removeAll(baseXpaths);
        allXpaths.addAll(extraXpaths);

//        // Process extra paths.
//        System.err.println("@@X@ base: " + baseXpaths.size() + ", extra: " + extraXpaths.size());
//        addExtraPaths(aFile, src, checkCldr, baselineFile, options, extraXpaths);
        
        for(String xpath : allXpaths) {
            boolean confirmOnly = false;
            String isToggleFor= null;
            if(!xpath.startsWith(xpathPrefix)) {
                if(SurveyMain.isUnofficial) System.err.println("@@ BAD XPATH " + xpath);
                continue;
            } else if(aFile.isPathExcludedForSurvey(xpath)) {
                if(SurveyMain.isUnofficial) System.err.println("@@ excluded:" + xpath);
                continue;
            }
            boolean isExtraPath = extraXpaths.contains(xpath); // 'extra' paths get shim treatment
///*srl*/  if(xpath.indexOf("Adak")!=-1)
///*srl*/   {ndebug=true;System.err.println("p] "+xpath + " - xtz = "+excludeTimeZones+"..");}
                
            if(SHOW_TIME) {
                count++;
				nextTime = System.currentTimeMillis();
				if((nextTime - lastTime) > 10000) {
					lastTime = nextTime;
                    System.err.println("[] " + locale + ":"+xpathPrefix +" #"+count+", or "+
                        (((double)(System.currentTimeMillis()-countStart))/count)+"ms per.");
                }
            }

            if(doExcludeAlways && excludeAlways.matcher(xpath).matches()) {
// if(ndebug && (xpath.indexOf("Adak")!=-1))    System.err.println("ns1 1 "+(System.currentTimeMillis()-nextTime) + " " + xpath);
                continue;
            } else if(excludeMost && mostPattern.matcher(xpath).matches()) {
//if(ndebug)     System.err.println("ns1 2 "+(System.currentTimeMillis()-nextTime) + " " + xpath);
                continue;
            } else if(excludeCurrencies && (xpath.startsWith("//ldml/numbers/currencies/currency"))) {
//if(ndebug)     System.err.println("ns1 3 "+(System.currentTimeMillis()-nextTime) + " " + xpath);
                continue;
            } else if(excludeCalendars && (xpath.startsWith("//ldml/dates/calendars"))) {
//if(ndebug)     System.err.println("ns1 4 "+(System.currentTimeMillis()-nextTime) + " " + xpath);
                continue;
            } else if(excludeTimeZones && (xpath.startsWith("//ldml/dates/timeZoneNames/zone"))) {
//if(ndebug && (xpath.indexOf("Adak")!=-1))     System.err.println("ns1 5 "+(System.currentTimeMillis()-nextTime) + " " + xpath);
                continue;
/*            } else if(exemplarCityOnly && (xpath.indexOf("exemplarCity")==-1)) {
                continue;*/
            } else if(excludeMetaZones && (xpath.startsWith("//ldml/dates/timeZoneNames/metazone"))) {
//if(ndebug&& (xpath.indexOf("Adak")!=-1))     System.err.println("ns1 6 "+(System.currentTimeMillis()-nextTime) + " " + xpath);
                continue;
            } else if(!excludeCalendars && excludeGrego && (xpath.startsWith(SurveyMain.GREGO_XPATH))) {
//if(ndebug)     System.err.println("ns1 7 "+(System.currentTimeMillis()-nextTime) + " " + xpath);
                continue;
            }
            
            if(CheckCLDR.skipShowingInSurvey.matcher(xpath).matches()) {
//if(TRACE_TIME)                System.err.println("ns1 8 "+(System.currentTimeMillis()-nextTime) + " " + xpath);
                continue;
            }

            String fullPath = aFile.getFullXPath(xpath);
            //int xpath_id = src.xpt.getByXpath(fullPath);
            int base_xpath = src.xpt.xpathToBaseXpathId(xpath);
            String baseXpath = src.xpt.getById(base_xpath);

            if(fullPath == null) { 
                if(isExtraPath) {
                    fullPath=xpath; // (this is normal for 'extra' paths)
                } else {
                    throw new InternalError("DP:P Error: fullPath of " + xpath + " for locale " + locale + " returned null.");
                }
            }

            if(needFullPathPattern.matcher(xpath).matches()) {
                //  we are going to turn on shorten, in case a non-shortened xpath is added someday.
                useShorten = true;
            }           

            if(TRACE_TIME)    System.err.println("ns0  "+(System.currentTimeMillis()-nextTime));
            boolean mixedType = false;
            String type;
            String lastType = src.xpt.typeFromPathToTinyXpath(baseXpath, xpp);  // last type in the list
            String displaySuffixXpath;
            String peaSuffixXpath = null; // if non null:  write to suffixXpath
            
            // these need to work on the base
            String fullSuffixXpath = baseXpath.substring(xpathPrefix.length(),baseXpath.length());
            if((removePrefix == null)||!baseXpath.startsWith(removePrefix)) {  
                displaySuffixXpath = baseXpath;
            } else {
                displaySuffixXpath = baseXpath.substring(removePrefix.length(),baseXpath.length());
            }
            if(useShorten == false) {
                type = lastType;
                if(type == null) {
                    peaSuffixXpath = displaySuffixXpath; // Mixed pea
                    if(xpath.startsWith("//ldml/characters")) {
                        type = "standard";
                    } else {
                        type = displaySuffixXpath;
                        mixedType = true;
                    }
                }
            } else {
                // shorten
                peaSuffixXpath = displaySuffixXpath; // always mixed pea if we get here
                    
                Matcher m = typeReplacementPattern.matcher(displaySuffixXpath);
                type = m.replaceAll("/$1");
                Matcher n = noisePattern.matcher(type);
                type = n.replaceAll("");
                if(keyTypeSwap) { // see above
                    Matcher o = keyTypeSwapPattern.matcher(type);
                    type = o.replaceAll("$2/$1");
                }

                for(pn=0;pn<fromto.length/2;pn++) {
//                    String oldType = type;
                    type = fromto_p[pn].matcher(type).replaceAll(fromto[(pn*2)+1]);
                    // who caused the change?
//                    if((type.indexOf("ldmls/")>0)&&(oldType.indexOf("ldmls/")<0)) {
//                        System.err.println("ldmls @ #"+pn+", "+fromto[pn*2]+" -> " + fromto[(pn*2)+1]);
//                    }
                }

            }
            
            if(TRACE_TIME)    System.err.println("n00  "+(System.currentTimeMillis()-nextTime));
            
            String value = isExtraPath?null:aFile.getStringValue(xpath);

//if(ndebug)     System.err.println("n01  "+(System.currentTimeMillis()-nextTime));

            if( xpath.indexOf("default[@type")!=-1 ) {
                peaSuffixXpath = displaySuffixXpath;
                int n = type.lastIndexOf('/');
                if(n==-1) {
                    type = "(default type)";
                } else {
                    type = type.substring(0,n); //   blahblah/default/foo   ->  blahblah/default   ('foo' is lastType and will show up as the value)
                }
//                if(isExtraPath && SurveyMain.isUnofficial) System.err.println("About to replace ["+value+"] value: " + xpath);
                value = lastType;
                confirmOnly = true; // can't acccept new data for this.
            }
            
            if(useShorten) {
                if((xpath.indexOf("/orientation")!=-1)||
                   (xpath.indexOf("/alias")!=-1)) {
                    if((value !=null)&&(value.length()>0)) {
                        throw new InternalError("Shouldn't have a value for " + xpath + " but have '"+value+"'.");
                    }
                    peaSuffixXpath = displaySuffixXpath;
                    int n = type.indexOf('[');
                    if(n!=-1) {
                        value = type.substring(n,type.length());
                        type = type.substring(0,n); //   blahblah/default/foo   ->  blahblah/default   ('foo' is lastType and will show up as the value)                        
                        //value = lastType;
                        confirmOnly = true; // can't acccept new data for this.
                    }
                }
            }
            
            if(value == null) {
                value = "(NOTHING)";  /* This is set to prevent crashes.. */
            }
            
            // determine 'alt' param
            String alt = src.xpt.altFromPathToTinyXpath(xpath, xpp);

//    System.err.println("n03  "+(System.currentTimeMillis()-nextTime));
    
            /* FULL path processing (references.. alt proposed.. ) */
            xpp.clear();
            xpp.initialize(fullPath);
            String lelement = xpp.getElement(-1);
            String eAlt = xpp.findAttributeValue(lelement, LDMLConstants.ALT);
            String eRefs = xpp.findAttributeValue(lelement,  LDMLConstants.REFERENCES);
            String eDraft = xpp.findAttributeValue(lelement, LDMLConstants.DRAFT);
            if(TRACE_TIME) System.err.println("n04  "+(System.currentTimeMillis()-nextTime));
            
            String typeAndProposed[] = LDMLUtilities.parseAlt(alt);
            String altProposed = typeAndProposed[1];
            String altType = typeAndProposed[0];
            
            // Now we are ready to add the data
            
            // Load the 'data row' which represents one user visible row of options 
            // (may be nested in the case of alt types)
            DataRow p = getDataRow(type, altType);
            p.base_xpath = base_xpath;
            p.winningXpathId = src.getWinningPathId(base_xpath, locale);

            DataRow superP = getDataRow(type);  // the 'parent' row (sans alt) - may be the same object
            
            peaSuffixXpath = fullSuffixXpath; // for now...
            
            if(peaSuffixXpath!=null) {
                p.xpathSuffix = peaSuffixXpath;
                superP.xpathSuffix = XPathTable.removeAltFromStub(peaSuffixXpath); // initialize parent row without alt
            }

            if(CheckCLDR.FORCE_ZOOMED_EDIT.matcher(xpath).matches()) {
                p.zoomOnly = superP.zoomOnly = true;
            }
            p.confirmOnly = superP.confirmOnly = confirmOnly;

            if(isExtraPath) {
                // Set up 'shim' tests, to display coverage
                p.setShimTests(base_xpath,this.sm.xpt.getById(base_xpath),checkCldr,options);
//                System.err.println("Shimmed! "+xpath);
                continue; // SRL
            } else if(!isReferences) {
                if(p.inheritedValue == null) {
                    p.updateInheritedValue(vettedParent);
                }
                if(superP.inheritedValue == null) {
                    superP.updateInheritedValue(vettedParent);
                }
            }
            
            // voting 
            // bitwise OR in the voting types. Needed for sorting.
            if(p.voteType == 0) {
                int vtypes[] = new int[1];
                vtypes[0]=0;
                /* res = */ sm.vet.queryResult(locale, base_xpath, vtypes);
                p.confirmStatus = sm.vet.queryResultStatus(locale, base_xpath);
                p.allVoteType |= vtypes[0];
                superP.allVoteType |= p.allVoteType;
                p.voteType = vtypes[0]; // no mask
            }
            
            // Is this a toggle pair with another item?
            if(isToggleFor != null) {
                if(superP.toggleWith == null) {
                    superP.updateToggle(fullPath, isToggleFor);
                }
                if(p.toggleWith == null) {
                    p.updateToggle(fullPath, isToggleFor);
                }
            }
            
            // Is it an attribute choice? (obsolete)
/*            if(attributeChoice != null) {
                p.attributeChoice = attributeChoice;
                p.valuesList = p.attributeChoice.valuesList;

                if(superP.attributeChoice == null) {
                    superP.attributeChoice = p.attributeChoice;
                    superP.valuesList = p.valuesList;
                }
            }*/
            
            // Some special cases.. a popup menu of values
            if(p.type.startsWith("layout/inText")) {
                p.valuesList = LAYOUT_INTEXT_VALUES;
                superP.valuesList = p.valuesList;
            } else if(p.type.indexOf("commonlyUsed")!=-1) { 
                p.valuesList = METAZONE_COMMONLYUSED_VALUES;
                superP.valuesList = p.valuesList;
            } else if(p.type.startsWith("layout/inList")) {
                p.valuesList = LAYOUT_INLIST_VALUES;
                superP.valuesList = p.valuesList;
            }
            

            if(TRACE_TIME) System.err.println("n05  "+(System.currentTimeMillis()-nextTime));

            // make sure the superP has its display name
            if(isReferences) {
                String eUri = xpp.findAttributeValue(lelement,"uri");
               if((eUri!=null)&&eUri.length()>0) {
                   if(eUri.startsWith("isbn:")) {
                        // linkbaton doesn't have ads, and lets you choose which provider to go to (including LOC).  
                        // could also go to wikipedia's  ISBN special page.              
						p.uri = "http://my.linkbaton.com/isbn/"+
                            eUri.substring(5,eUri.length());
                        p.displayName = eUri;
                    } else {
						p.uri = eUri;
						p.displayName = eUri.replaceAll("(/|&)","\u200b$0");  //  put zwsp before "/" or "&"
                        //p.displayName = /*type + " - "+*/ "<a href='"+eUri+"'>"+eUri+"</a>";
                    }
                } else {
                    p.displayName = null;
                }
                if(superP.displayName == null) {
                    superP.displayName = p.displayName;
                }
            } else {
                if(superP.displayName == null) {
                    superP.displayName = baselineFile.getStringValue(xpath(superP)); 
                }
                if(p.displayName == null) {
                    p.displayName = baselineFile.getStringValue(baseXpath);
                }
            }
    
            if((superP.displayName == null) ||
                (p.displayName == null)) {
                canName = false; // disable 'view by name' if not all have names.
            }
            if(TRACE_TIME) System.err.println("n06  "+(System.currentTimeMillis()-nextTime));
            
            // If it is draft and not proposed.. make it proposed-draft 
            if( ((eDraft!=null)&&(!eDraft.equals("false"))) &&
                (altProposed == null) ) {
                altProposed = SurveyMain.PROPOSED_DRAFT;
            }
            
            // Inherit display names.
            if((superP != p) && (p.displayName == null)) {
                p.displayName = baselineFile.getStringValue(baseXpath); 
                if(p.displayName == null) {
                    p.displayName = superP.displayName; // too: unscramble this a little bit
                }
            }
            CLDRFile.Status sourceLocaleStatus = new CLDRFile.Status();
            String sourceLocale = aFile.getSourceLocaleID(xpath, sourceLocaleStatus);
            boolean isInherited = !(sourceLocale.equals(locale));

            // with xpath munging, attributeChoice items show up as code fallback. Correct it.
/*            if(attributeChoice!=null && isInherited) {
                if(sourceLocale.equals(XMLSource.CODE_FALLBACK_ID)) {
                    isInherited = false;
                    sourceLocale = locale;
                }
            }*/
            // ** IF it is inherited, do NOT add any Items.   
            if(isInherited) {
                if(!isReferences) {
                    p.updateInheritedValue(vettedParent, checkCldr, options); // update the tests
                }
                continue;
            }
            
            
            if(TRACE_TIME) System.err.println("n07  "+(System.currentTimeMillis()-nextTime));
    
            // ?? simplify this.
            if(altProposed == null) {
                if(!isInherited) {
                    //superP.hasInherited=false;
                    //p.hasInherited=false;
                } else {
                    p.hasInherited = true;
                    superP.hasInherited=true;
                }
            } else {
                if(!isInherited) {
                    p.hasProps = true;
                    if(altProposed != SurveyMain.PROPOSED_DRAFT) {  // 'draft=true'
                        p.hasMultipleProposals = true; 
                    }
                    superP.hasProps = true;
                } else {
                    // inherited, proposed
                   // p.hasProps = true; // Don't mark as a proposal.
                   // superP.hasProps = true;
                   p.hasInherited=true;
                   superP.hasInherited=true;
                }
            }
            
            
            String setInheritFrom = (isInherited)?sourceLocale:null; // no inherit if it's current.
//            boolean isCodeFallback = (setInheritFrom!=null)&&
//                (setInheritFrom.equals(XMLSource.CODE_FALLBACK_ID)); // don't flag errors from code fallback.
            
            if(isExtraPath) { // No real data items if it's an extra path.
                continue; 
            }
            
            // ***** Set up Candidate Items *****
            // These are the items users may choose between
            //
            if((checkCldr != null)/*&&(altProposed == null)*/) {
                checkCldr.check(xpath, fullPath, isExtraPath?null:value, options, checkCldrResult);
                checkCldr.getExamples(xpath, fullPath, isExtraPath?null:value, options, examplesResult);
            }
            DataSection.DataRow.CandidateItem myItem = null;
            
/*            if(p.attributeChoice != null) {
                String newValue = p.attributeChoice.valueOfXpath(fullPath);
//       System.err.println("ac:"+fullPath+" -> " + newValue);
                value = newValue;
            }*/
            if(TRACE_TIME) System.err.println("n08  "+(System.currentTimeMillis()-nextTime));
            myItem = p.addItem( value, altProposed, null);
//if("gsw".equals(type)) System.err.println(myItem + " - # " + p.items.size());
            
            myItem.xpath = xpath;
            myItem.xpathId = src.xpt.getByXpath(xpath);
            if(myItem.xpathId == base_xpath) {
                p.previousItem = myItem; // did have a previous item.
            }

            if(!checkCldrResult.isEmpty()) {
                myItem.setTests(checkCldrResult);
                // set the parent
                checkCldrResult = new ArrayList(); // can't reuse it if nonempty
            }
            /*
                Was this item submitted via SurveyTool? Let's find out.
            */
            myItem.submitter = src.getSubmitterId(locale, myItem.xpathId);
            if(myItem.submitter != -1) {
///*srl*/                System.err.println("submitter set: " + myItem.submitter + " @ " + locale + ":"+ xpath);
            }

            if(sourceLocaleStatus!=null && sourceLocaleStatus.pathWhereFound!=null && !sourceLocaleStatus.pathWhereFound.equals(xpath)) {
//System.err.println("PWF diff: " + xpath + " vs " + sourceLocaleStatus.pathWhereFound);
                myItem.pathWhereFound = sourceLocaleStatus.pathWhereFound;
                // set up Pod alias-ness
                p.aliasFromLocale = sourceLocale;
                p.aliasFromXpath = sm.xpt.xpathToBaseXpathId(sourceLocaleStatus.pathWhereFound);
            }
            myItem.inheritFrom = setInheritFrom;
            // store who voted for what. [ this could be loaded at displaytime..]
           //myItem.votes = sm.vet.gatherVotes(locale, xpath);
            
            if(!examplesResult.isEmpty()) {
                // reuse the same ArrayList  unless it contains something                
                if(myItem.examples == null) {
                    myItem.examples = new Vector();
                }
                for (Iterator it3 = examplesResult.iterator(); it3.hasNext();) {
                    CheckCLDR.CheckStatus status = (CheckCLDR.CheckStatus) it3.next();                
                    myItem.examples.add(addExampleEntry(new ExampleEntry(this,p,myItem,status)));
                }
   //             myItem.examplesList = examplesResult;
   //             examplesResult = new ArrayList(); // getExamples will clear it.
            }

            if((eRefs != null) && (!isInherited)) {
                myItem.references = eRefs;
            }
            
        }
//        aFile.close();
    }

//    /**
//     * Create a 'shim' row for each of the named paths
//     * @param extraXpaths
//     */
//    private void addExtraPaths(CLDRFile aFile, CLDRDBSource src, CheckCLDR checkCldr, CLDRFile baselineFile, Map options, Set<String> extraXpaths) {
//        
//        for(String xpath : extraXpaths) {
//            DataSection.DataRow myp = getDataRow(xpath);
//            int base_xpath = sm.xpt.getByXpath(xpath);
//            myp.base_xpath = base_xpath;
//            
//            if(myp.xpathSuffix == null) {
//                myp.xpathSuffix = xpath.substring(xpathPrefix.length());
//                
//                // set up the pea
//                if(CheckCLDR.FORCE_ZOOMED_EDIT.matcher(xpath).matches()) {
//                    myp.zoomOnly = true;
//                }
//
//                // set up tests
//                System.err.println("@@@shimmy - " + xpath);
//                myp.setShimTests(base_xpath,xpath,checkCldr,options);
//            }
//        }
//    }
    /**
     * Makes sure this pod contains the peas we'd like to see.
     */
    private void ensureComplete(CLDRDBSource src, CheckCLDR checkCldr, CLDRFile baselineFile, Map options) {
        if(xpathPrefix.startsWith("//ldml/"+"dates/timeZoneNames")) {
            // work on zones
            boolean isMetazones = xpathPrefix.startsWith("//ldml/"+"dates/timeZoneNames/metazone");
            // Make sure the pod contains the peas we'd like to see.
            // regular zone
            
            Set mzones = sm.getMetazones();
            
            Iterator zoneIterator;
            
            if(isMetazones) {
                zoneIterator = sm.getMetazones().iterator();
            } else {
                zoneIterator = StandardCodes.make().getGoodAvailableCodes("tzid").iterator();
            }
            
            final String tzsuffs[] = {  "/long/generic",
                                "/long/daylight",
                                "/long/standard",
                                "/short/generic",
                                "/short/daylight",
                                "/short/standard",
                                "/exemplarCity" };
            final String mzsuffs[] = {  "/long/generic",
                                "/long/daylight",
                                "/long/standard",
                                "/short/generic",
                                "/short/daylight",
                                "/short/standard",
                                "/commonlyUsed"
            };
            
            String suffs[];
            if(isMetazones) {
                suffs = mzsuffs;
            } else {
                suffs = tzsuffs;
            }        

            String podBase = xpathPrefix;
            CLDRFile resolvedFile = new CLDRFile(src, true);
            XPathParts parts = new XPathParts(null,null);
//            TimezoneFormatter timezoneFormatter = new TimezoneFormatter(resolvedFile, true); // TODO: expensive here.

            for(;zoneIterator.hasNext();) {
                String zone = zoneIterator.next().toString();
//                System.err.println(">> " + zone);
                /** some compatibility **/
                String ourSuffix = "[@type=\""+zone+"\"]";
                String whichMZone = null;
                if(isMetazones) {
                    whichMZone = zone;
                }

                for(int i=0;i<suffs.length;i++) {
                    String suff = suffs[i];
                    
                    // synthesize a new pea..
                    String rowXpath = zone+suff;
                    String base_xpath_string = podBase+ourSuffix+suff;
                    if(resolvedFile.isPathExcludedForSurvey(base_xpath_string)) {
                       if(SurveyMain.isUnofficial) System.err.println("@@ synthesized+excluded:" + base_xpath_string);
                       continue;
                    }
                    DataSection.DataRow myp = getDataRow(rowXpath);
                    
                    // set it up..
                    int base_xpath = sm.xpt.getByXpath(base_xpath_string);
                    myp.base_xpath = base_xpath;
                    
                    if(myp.xpathSuffix == null) {
                        myp.xpathSuffix = ourSuffix+suff;
                        
                        // set up the pea
                        if(CheckCLDR.FORCE_ZOOMED_EDIT.matcher(base_xpath_string).matches()) {
                            myp.zoomOnly = true;
                        }

                        // set up tests
                        myp.setShimTests(base_xpath,base_xpath_string,checkCldr,options);
                    }
                    
                    ///*srl*/            System.err.println("P: ["+zone+suff+"] - count: " + myp.items.size());
                    if(isMetazones) {
                        if(suff.equals("/commonlyUsed")) {
                            myp.valuesList = METAZONE_COMMONLYUSED_VALUES;
                        }
                    }
                    
                    myp.displayName = baselineFile.getStringValue(podBase+ourSuffix+suff); // use the baseline (English) data for display name.
                    
                }
            }
        } // tz
    }
// ==

    public DataRow getDataRow(String type) {
        if(type == null) {
            throw new InternalError("type is null");
        }
        if(rowsHash == null) {
            throw new InternalError("peasHash is null");
        }
        DataRow p = (DataRow)rowsHash.get(type);
        if(p == null) {
            p = new DataRow();
            p.type = type;
            addDataRow(p);
        }
        return p;
    }
    
    private DataRow getDataRow(String type, String altType) {
        if(altType == null) {
            return getDataRow(type);
        } else {
            DataRow superDataRow = getDataRow(type);
            return superDataRow.getSubDataRow(altType);
        }
    }
    
    void addDataRow(DataRow p) {
        rowsHash.put(p.type, p);
    }
    
    public String toString() {
        return "{DataPod " + locale + ":" + xpathPrefix + " #" + super.toString() + ", " + getAll().size() +" items} ";
    }
    
    /** 
     * Given a (cleaned, etc) xpath, this returns the podBase, i.e. context.getPod(base), that would be used to show
     * that xpath.  
     * Keep this in sync with SurveyMain.showLocale() where there is the list of menu items.
     */
    public static String xpathToSectionBase(String xpath) {
        int n;
        String base;
        
        // is it one of the prefixes we can check statically?
        String staticBases[] = { 
            // LOCALEDISPLAYNAMES
                "//ldml/"+SurveyMain.NUMBERSCURRENCIES,
                "//ldml/"+"dates/timeZoneNames/zone",
                "//ldml/"+"dates/timeZoneNames/metazone",
            // OTHERROOTS
                SurveyMain.GREGO_XPATH,
                SurveyMain.LOCALEDISPLAYPATTERN_XPATH,
                SurveyMain.OTHER_CALENDARS_XPATH
        };
         
        // is it one of the static bases?
        for(n=0;n<staticBases.length;n++) {
            if(xpath.startsWith(staticBases[n])) {
                return staticBases[n];
            }
        }
            
        // dynamic LOCALEDISPLAYNAMES
        for(n =0 ; n < SurveyMain.LOCALEDISPLAYNAMES_ITEMS.length; n++) {   // is it a simple code list?
            base = SurveyMain.LOCALEDISPLAYNAMES+SurveyMain.LOCALEDISPLAYNAMES_ITEMS[n]+
                '/'+SurveyMain.typeToSubtype(SurveyMain.LOCALEDISPLAYNAMES_ITEMS[n]);  // see: SurveyMain.showLocaleCodeList()
            if(xpath.startsWith(base)) {
                return base;
            }
        }
        
        // OTHERROOTS
        for(n=0;n<SurveyMain.OTHERROOTS_ITEMS.length;n++) {
            base= "//ldml/"+SurveyMain.OTHERROOTS_ITEMS[n];
            if(xpath.startsWith(base)) {
                return base;
            }
        }
        
        return "//ldml"; // the "misc" pile.
    }
    
}
