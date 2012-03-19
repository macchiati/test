package org.unicode.cldr.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.unittest.TestAll.TestInfo;
import org.unicode.cldr.util.CldrUtility.Output;
import org.unicode.cldr.util.RegexLookup.Finder;

import com.ibm.icu.impl.Row;
import com.ibm.icu.impl.Row.R2;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.RuleBasedCollator;
import com.ibm.icu.text.Transform;
import com.ibm.icu.util.ULocale;

public class PathHeader implements Comparable<PathHeader> {
    private final String section;
    private final String page;
    private final String header;
    private final String code;
    
    // Used for ordering
    private final int sectionOrder;
    private final int pageOrder;
    private final int headerOrder;
    private final int codeOrder;

    static final Pattern SEMI = Pattern.compile("\\s*;\\s*");
    static final Matcher ALT_MATCHER = Pattern.compile("\\[@alt=\"([^\"]*+)\"]").matcher("");

    static final RuleBasedCollator alphabetic = (RuleBasedCollator) Collator.getInstance(ULocale.ENGLISH);
    static {
        alphabetic.setNumericCollation(true);
    }
    static final SupplementalDataInfo supplementalDataInfo = SupplementalDataInfo.getInstance();
    static final Map<String, String> metazoneToContinent = supplementalDataInfo.getMetazoneToContinentMap();
    static final StandardCodes standardCode = StandardCodes.make();


    /**
     * @param section
     * @param sectionOrder
     * @param page
     * @param pageOrder
     * @param header
     * @param headerOrder
     * @param code
     * @param codeOrder
     */
    private PathHeader(String section, int sectionOrder, String page, int pageOrder, String header,
            int headerOrder, String code, int codeOrder) {
        this.section = section;
        this.sectionOrder = sectionOrder;
        this.page = page;
        this.pageOrder = pageOrder;
        this.header = header;
        this.headerOrder = headerOrder;
        this.code = code;
        this.codeOrder = codeOrder;
    }

    /**
     * Return a factory for use in creating the headers. This should be cached. The calls are thread-safe.
     * @param englishFile
     * @return
     */
    public static Factory getFactory(CLDRFile englishFile) {
        return new Factory(englishFile);
    }

    public String getSection() {
        return section;
    }

    public String getPage() {
        return page;
    }

    public String getHeader() {
        return header;
    }

    public String getCode() {
        return code;
    }

    @Override
    public String toString() { 
        return section + "\t(" + sectionOrder + ");\t"
        + page + "\t(" + pageOrder + ");\t"
        + header + "\t(" + headerOrder + ");\t"
        + code + "\t(" + codeOrder + ")";
    }
    @Override
    public int compareTo(PathHeader other) {
        // Within each section, order alphabetically if the integer orders are not different.
        int result;
        if (0 != (result = sectionOrder - other.sectionOrder)) {
            return result;
        }
        if (0 != (result = alphabetic.compare(section, other.section))) {
            return result;
        }
        if (0 != (result = pageOrder - other.pageOrder)) {
            return result;
        }
        if (0 != (result = alphabetic.compare(page, other.page))) {
            return result;
        }
        if (0 != (result = headerOrder - other.headerOrder)) {
            return result;
        }
        if (0 != (result = alphabetic.compare(header, other.header))) {
            return result;
        }
        if (0 != (result = codeOrder - other.codeOrder)) {
            return result;
        }
        // Question: make English value order??
        if (0 != (result = alphabetic.compare(code, other.code))) {
            return result;
        }
        return 0;
    }


    public static class Factory {
        static final RegexLookup<RawData> lookup = RegexLookup.of(new PathHeaderTransform())
        .setPatternTransform(RegexLookup.RegexFinderTransformPath)
        .loadFromFile(PathHeader.class, "PathHeader.txt");

        static final Output<String[]> args = new Output<String[]>(); // synchronized with lookup
        static final Counter<RawData> counter = new Counter<RawData>(); // synchronized with lookup
        static final Map<RawData, String> samples = new HashMap<RawData, String>(); // synchronized with lookup
        static int order; // only gets used when synchronized under lookup

        private CLDRFile englishFile;

        private Factory(CLDRFile englishFile) {
            this.englishFile = englishFile;
        }

        /**
         * Return the PathHeader for a given path.
         * @param section
         * @param page
         * @param header
         * @param code
         */
        public PathHeader fromPath(String distinguishingPath) {
            if (distinguishingPath == null) {
                throw new NullPointerException("Path cannot be null");
            }
            synchronized (lookup) {
                // special handling for alt
                String alt = null;
                int altPos = distinguishingPath.indexOf("[@alt=");
                if (altPos >= 0) {
                    if (ALT_MATCHER.reset(distinguishingPath).find()) {
                        alt = ALT_MATCHER.group(1);
                        distinguishingPath = distinguishingPath.substring(0,ALT_MATCHER.start()) + distinguishingPath.substring(ALT_MATCHER.end());
                    } else {
                        throw new IllegalArgumentException();
                    }
                }
                RawData data = lookup.get(distinguishingPath,null,args);
                if (data == null) {
                    return null;
                }
                counter.add(data, 1);
                if (!samples.containsKey(data)) {
                    samples.put(data, distinguishingPath);
                }
                try {
                    return new PathHeader(
                            fix(data.section, data.sectionOrder), order, 
                            fix(data.page, data.pageOrder), order,  
                            fix(data.header, data.headerOrder), order,
                            fix(data.code + (alt == null ? "" : "-" + alt), data.codeOrder), order);
                } catch (Exception e) {
                    throw new IllegalArgumentException("Probably too few capturing groups in regex for " + distinguishingPath, e);
                }
            }
        }


        /**
         * Return the Sections and Pages that are in use, for display in menus. Both are ordered.
         */
        public LinkedHashMap<String,Set<String>> getSectionsToPages() {
            LinkedHashMap<String,Set<String>> sectionsToPages = new LinkedHashMap<String,Set<String>>();
            for (R2<Finder, RawData> foo : lookup) {
                RawData data = foo.get1();
                Set<String> pages = sectionsToPages.get(data.section);
                if (pages == null) {
                    sectionsToPages.put(data.section, pages = new LinkedHashSet<String>());
                }
                // Special Hack for Metazones and Calendar
                // We could make this more general by having a TransformWithRange.getValues() for each function. Not worth doing right now.
                if (!data.page.contains("&")) {
                    pages.add(data.page);
                } else if (data.page.contains("metazone")) {
                    pages.addAll(new TreeSet<String>(metazoneToContinent.values()));
                } else if (data.page.contains("calendar")) {
                    Set<String> calendars = supplementalDataInfo.getBcp47Keys().get("ca");
                    Transform<String, String> calendarFunction = functionMap.get("calendar"); 
                    for (String calendar : calendars) {
                        pages.add(calendarFunction.transform(calendar));
                    }
                }
            }
            return sectionsToPages;
        }

        private static class ChronologicalOrder<T> {
            Map<T,Integer> map = new HashMap<T,Integer>();
            int getOrder(T item) {
                Integer old = map.get(item);
                if (old != null) {
                    return old.intValue();
                }
                int result = map.size();
                map.put(item, result);
                return result;
            }
        }

        static class RawData {
            static ChronologicalOrder<String> sectionOrdering = new ChronologicalOrder<String>();
            static ChronologicalOrder<String> pageOrdering = new ChronologicalOrder<String>();
            static ChronologicalOrder<String> headerOrdering = new ChronologicalOrder<String>();
            static ChronologicalOrder<String> codeOrdering = new ChronologicalOrder<String>();

            public RawData(String source) {
                String[] split = SEMI.split(source);
                section = split[0];
                sectionOrder = sectionOrdering.getOrder(section);
                page = split[1];
                pageOrder = pageOrdering.getOrder(page);
                header = split[2];
                headerOrder = headerOrdering.getOrder(header);
                code = split[3];
                codeOrder = codeOrdering.getOrder(code);
            }
            public final String section;
            public final int sectionOrder;
            public final String page;
            public final int pageOrder;
            public final String header;
            public final int headerOrder;
            public final String code;
            public final int codeOrder;
            @Override
            public String toString() {
                return section + "\t(" + sectionOrder + ");\t"
                + page + "\t(" + pageOrder + ");\t"
                + header + "\t(" + headerOrder + ");\t"
                + code + "\t(" + codeOrder + ")";
            }
        }

        static class PathHeaderTransform implements Transform<String,RawData> {
            static ChronologicalOrder<String> pathOrder = new ChronologicalOrder<String>();
            @Override
            public RawData transform(String source) {
                return new RawData(source);
            }
        }

        class CounterData extends Row.R4<String,RawData,String, String> {
            public CounterData(String a, RawData b, String c) {
                super(a, b, c == null ? "no sample" : c, c == null ? "no sample" : fromPath(c).toString());
            }
        }

        /**
         * Get the internal data, for testing and debugging.
         * @return
         */
        public Counter<CounterData> getInternalCounter() {
            synchronized (lookup) {
                Counter<CounterData> result = new Counter<CounterData>();
                for (R2<Finder, RawData> foo : lookup) {
                    Finder finder = foo.get0();
                    RawData data = foo.get1();
                    long count = counter.get(data);
                    result.add(new CounterData(finder.toString(), data, samples.get(data)), count);
                }
                return result;
            }
        }


        static Map<String,Transform<String,String>> functionMap = new HashMap<String,Transform<String,String>>();
        static String[] months = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec", "Und"};
        static List<String> days = Arrays.asList("sun", "mon", "tue", "wed", "thu", "fri", "sat");
        static {
            functionMap.put("month", new Transform<String,String>(){
                public String transform(String source) {
                    int m = Integer.parseInt(source);
                    order = m;
                    return months[m-1];
                }});
            functionMap.put("day", new Transform<String,String>(){
                public String transform(String source) {
                    int m = days.indexOf(source);
                    order = m;
                    return source;
                }});
            functionMap.put("metazone", new Transform<String,String>(){
                public String transform(String source) {
                    String continent = metazoneToContinent.get(source);
                    return continent;
                }});
            functionMap.put("calendar", new Transform<String,String>(){
                Map<String, String> fixNames = Builder.with(new HashMap<String,String>())
                .put("islamicc","Islamic Civil")
                .put("roc", "ROC")
                .put("Ethioaa", "Ethiopic Amete Alem")
                .put("Gregory", "Gregorian")
                .put("iso8601", "ISO 8601")
                .freeze();
                public String transform(String source) {
                    String result = fixNames.get(source);
                    return result != null ? result : UCharacter.toTitleCase(source, null);
                }});
        }

        /**
         * This converts "functions", like &month, and sets the order.
         * @param input
         * @param order
         * @return
         */
        private static String fix(String input, int orderIn) {
            input = RegexLookup.replace(input, args.value);
            order = orderIn;
            int functionStart = input.indexOf("&");
            if (functionStart >= 0) {
                int functionEnd = input.indexOf('(', functionStart);
                int argEnd = input.indexOf(')', functionEnd);
                Transform<String, String> func = functionMap.get(input.substring(functionStart+1, functionEnd));
                String temp = func.transform(input.substring(functionEnd+1, argEnd));
                input = input.substring(0,functionStart) + temp + input.substring(argEnd+1);
            }
            return input;
        }
    }
    
    public static void main(String[] args) {

        PathStarrer pathStarrer = new PathStarrer();
        pathStarrer.setSubstitutionPattern("%A");

        // move to test
        CLDRFile english = TestInfo.getInstance().getEnglish();
        Factory factory = getFactory(english);
        
        Map<PathHeader, String> sorted = new TreeMap<PathHeader, String>();
        Map<String,String> missing = new TreeMap<String,String>();
        Map<String,String> skipped = new TreeMap<String,String>();
        Map<String,String> collide = new TreeMap<String,String>();

        System.out.println("Traversing Paths");
        for (String path : english) {
            PathHeader pathHeader = factory.fromPath(path);
            String value = english.getStringValue(path);
            if (pathHeader == null) {
                final String starred = pathStarrer.set(path);
                missing.put(starred, value + "\t" + path);
                continue;
            }
            if (pathHeader.section.equalsIgnoreCase("skip")) {
                final String starred = pathStarrer.set(path);
                skipped.put(starred, value + "\t" + path);
                continue;
            }
            String old = sorted.get(pathHeader);
            if (old != null && !path.equals(old)) {
                collide.put(path, old + "\t" + pathHeader);
            }
            sorted.put(pathHeader, value + ";\t" + path);
        }
        System.out.println("\nConverted:\t" + sorted.size());
        String lastHeader = "";
        String lastPage = "";
        String lastSection = "";
        List<String> threeLevel = new ArrayList<String>();
        for (Entry<PathHeader, java.lang.String> entry : sorted.entrySet()) {
            final PathHeader pathHeader = entry.getKey();
            if (!lastSection.equals(pathHeader.section)) {
                System.out.println();
                threeLevel.add(pathHeader.section);
                lastSection = pathHeader.section;
                lastPage = lastHeader = "";
            } else if (!lastPage.equals(pathHeader.page)) {
                System.out.println();
                threeLevel.add("\t" + pathHeader.page);
                lastHeader = "";
                lastPage = pathHeader.page;
            } else if (!lastHeader.equals(pathHeader.header)) {
                System.out.println();
                threeLevel.add("\t\t" + (pathHeader.header.equals("null") ? "<no-header>" : pathHeader.header));
                lastHeader = pathHeader.header;
            }
            System.out.println(pathHeader + ";\t" + entry.getValue());
        }
        System.out.println("\nCollide:\t" + collide.size());
        for (Entry<String, String> item : collide.entrySet()) {
            System.out.println("\t" + item);
        }
        System.out.println("\nMissing:\t" + missing.size());
        for (Entry<String, String> item : missing.entrySet()) {
            System.out.println("\t" + item.getKey() + "\tvalue:\t" + item.getValue());
        }
        System.out.println("\nSkipped:\t" + skipped.size());
        for (Entry<String, String> item : skipped.entrySet()) {
            System.out.println("\t" + item);
        }
        Counter<Factory.CounterData> counterData = factory.getInternalCounter();
        System.out.println("\nInternal Counter:\t" + counterData.size());
        for (Factory.CounterData item : counterData.keySet()) {
            System.out.println("\t" + counterData.getCount(item)
                    + "\t" + item.get2() // externals
                    + "\t" + item.get3()
                    + "\t" + item.get0() // internals
                    + "\t" + item.get1() 
            );
        }
        LinkedHashMap<String, Set<String>> sectionsToPages = factory.getSectionsToPages();
        System.out.println("\nMenus:\t" + sectionsToPages.size());
        for (Entry<String, Set<String>> item : sectionsToPages.entrySet()) {
            System.out.println("\t" + item.getKey() + "\t" + item.getValue());
        }
        System.out.println("\nMenus/Headers:\t" + threeLevel.size());
        for (String item : threeLevel) {
            System.out.println(item);
        }
    }
}
