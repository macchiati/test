package org.unicode.cldr.tool;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;

import org.unicode.cldr.draft.FileUtilities;
import org.unicode.cldr.test.CheckCLDR.InputMethod;
import org.unicode.cldr.test.CheckCLDR.Phase;
import org.unicode.cldr.test.CheckCLDR.StatusAction;
import org.unicode.cldr.tool.FormattedFileWriter.Anchors;
import org.unicode.cldr.tool.Option.Options;
import org.unicode.cldr.util.Annotations;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRFile.DraftStatus;
import org.unicode.cldr.util.CLDRFile.Status;
import org.unicode.cldr.util.CLDRInfo.CandidateInfo;
import org.unicode.cldr.util.CLDRInfo.PathValueInfo;
import org.unicode.cldr.util.CLDRInfo.UserInfo;
import org.unicode.cldr.util.CLDRLocale;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.CLDRURLS;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.CoreCoverageInfo;
import org.unicode.cldr.util.CoreCoverageInfo.CoreItems;
import org.unicode.cldr.util.Counter;
import org.unicode.cldr.util.Counter2;
import org.unicode.cldr.util.CoverageInfo;
import org.unicode.cldr.util.DtdType;
import org.unicode.cldr.util.LanguageTagCanonicalizer;
import org.unicode.cldr.util.LanguageTagParser;
import org.unicode.cldr.util.Level;
import org.unicode.cldr.util.Organization;
import org.unicode.cldr.util.PathHeader;
import org.unicode.cldr.util.PathHeader.Factory;
import org.unicode.cldr.util.PathHeader.SurveyToolStatus;
import org.unicode.cldr.util.PatternCache;
import org.unicode.cldr.util.RegexLookup;
import org.unicode.cldr.util.RegexLookup.LookupType;
import org.unicode.cldr.util.SimpleFactory;
import org.unicode.cldr.util.StandardCodes;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.VettingViewer;
import org.unicode.cldr.util.VettingViewer.MissingStatus;
import org.unicode.cldr.util.VoteResolver.VoterInfo;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;
import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.dev.util.UnicodeMap;
import com.ibm.icu.impl.Relation;
import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.UnicodeSet;

public class ShowLocaleCoverage {
    private static final String SPREADSHEET_MISSING = "#LCode\tEnglish Name\tScript\tEnglish Value\tNative Value\tCldr Target\tPath Level\tStatus\tAction\tSTStatus\tST Link\tSection\tPage\tHeader\tCode\tPath";
    private static final boolean DEBUG = false;
    private static final char DEBUG_FILTER = 0; // use letter to only load locales starting with that letter

    private static final String LATEST = ToolConstants.CHART_VERSION;
    private static final double CORE_SIZE = CoreItems.values().length - CoreItems.ONLY_RECOMMENDED.size();
    public static CLDRConfig testInfo = ToolConfig.getToolInstance();
    private static final StandardCodes SC = testInfo.getStandardCodes();
    private static final SupplementalDataInfo SUPPLEMENTAL_DATA_INFO = testInfo.getSupplementalDataInfo();
    private static final StandardCodes STANDARD_CODES = SC;

    static org.unicode.cldr.util.Factory factory = testInfo.getCommonAndSeedAndMainAndAnnotationsFactory();
    private static final CLDRFile ENGLISH = factory.make("en", true);

    private static UnicodeSet ENG_ANN = Annotations.getData("en").keySet();

    // added info using pattern in VettingViewer.

    static final RegexLookup<Boolean> HACK = RegexLookup.<Boolean> of(LookupType.STANDARD, RegexLookup.RegexFinderTransformPath)
        .add("//ldml/localeDisplayNames/keys/key[@type=\"(d0|em|fw|i0|k0|lw|m0|rg|s0|ss|t0|x0)\"]", true)
        .add("//ldml/localeDisplayNames/types/type[@key=\"(em|fw|kr|lw|ss)\"].*", true)
        .add("//ldml/localeDisplayNames/languages/language[@type=\".*_.*\"]", true)
        .add("//ldml/localeDisplayNames/languages/language[@type=\".*\"][@alt=\".*\"]", true)
        .add("//ldml/localeDisplayNames/territories/territory[@type=\".*\"][@alt=\".*\"]", true)
        .add("//ldml/localeDisplayNames/territories/territory[@type=\"EZ\"]", true);

    //private static final String OUT_DIRECTORY = CLDRPaths.GEN_DIRECTORY + "/coverage/"; // CldrUtility.MAIN_DIRECTORY;

    final static Options myOptions = new Options();

    enum MyOptions {
        filter(".+", ".*", "Filter the information based on id, using a regex argument."),
        //        draftStatus(".+", "unconfirmed", "Filter the information to a minimum draft status."),
        chart(null, null, "chart only"), 
        growth("true", "true", "Compute growth data"), 
        organization(".+", null, "Only locales for organization"), 
        version(".+",
            LATEST, "To get different versions"), 
        rawData(null, null, "Output the raw data from all coverage levels"), 
        targetDir(".*",
            CLDRPaths.GEN_DIRECTORY + "/statistics/", "target output file."), 
        directories("(.*:)?[a-z]+(,[a-z]+)*", "common",
            "Space-delimited list of main source directories: common,seed,exemplar.\n" +
            "Optional, <baseDir>:common,seed"),;

        // targetDirectory(".+", CldrUtility.CHART_DIRECTORY + "keyboards/", "The target directory."),
        // layouts(null, null, "Only create html files for keyboard layouts"),
        // repertoire(null, null, "Only create html files for repertoire"), ;
        // boilerplate
        final Option option;

        MyOptions(String argumentPattern, String defaultArgument, String helpText) {
            option = myOptions.add(this, argumentPattern, defaultArgument, helpText);
        }
    }

    static final RegexLookup<Boolean> SUPPRESS_PATHS_CAN_BE_EMPTY = new RegexLookup<Boolean>()
        .add("\\[@alt=\"accounting\"]", true)
        .add("\\[@alt=\"variant\"]", true)
        .add("^//ldml/localeDisplayNames/territories/territory.*@alt=\"short", true)
        .add("^//ldml/localeDisplayNames/languages/language.*_", true)
        .add("^//ldml/numbers/currencies/currency.*/symbol", true)
        .add("^//ldml/characters/exemplarCharacters", true);

    static DraftStatus minimumDraftStatus = DraftStatus.unconfirmed;
    static final Factory pathHeaderFactory = PathHeader.getFactory(ENGLISH);

    static boolean RAW_DATA = true;
    private static Set<String> COMMON_LOCALES;

    public static void main(String[] args) throws IOException {
        myOptions.parse(MyOptions.filter, args, true);

        if (MyOptions.chart.option.doesOccur()) {
            showCoverage(null);
            return;
        }

        Matcher matcher = PatternCache.get(MyOptions.filter.option.getValue()).matcher("");

        if (MyOptions.growth.option.doesOccur()) {
            try (PrintWriter out = FileUtilities.openUTF8Writer(CLDRPaths.CHART_DIRECTORY + "tsv/", "locale-growth.tsv")) {
                doGrowth(matcher, out);
                return;
            }
        }

        Set<String> locales = null;
        String organization = MyOptions.organization.option.getValue();
        boolean useOrgLevel = MyOptions.organization.option.doesOccur();
        if (useOrgLevel) {
            locales = STANDARD_CODES.getLocaleCoverageLocales(organization);
        }

        if (MyOptions.version.option.doesOccur()) {
            String number = MyOptions.version.option.getValue().trim();
            if (!number.contains(".")) {
                number += ".0";
            }
            factory = org.unicode.cldr.util.Factory.make(
                CLDRPaths.ARCHIVE_DIRECTORY + "cldr-" + number + "/common/main/", ".*");
        } else {
            if (MyOptions.directories.option.doesOccur()) {
                String directories = MyOptions.directories.option.getValue().trim();
                CLDRConfig cldrConfig = CLDRConfig.getInstance();
                String base = null;
                int colonPos = directories.indexOf(':');
                if (colonPos >= 0) {
                    base = directories.substring(0, colonPos).trim();
                    directories = directories.substring(colonPos + 1).trim();
                } else {
                    base = cldrConfig.getCldrBaseDirectory().toString();
                }
                String[] items = directories.split(",\\s*");
                File[] fullDirectories = new File[items.length];
                int i = 0;
                for (String item : items) {
                    fullDirectories[i++] = new File(base + "/" + item + "/main");
                }
                factory = SimpleFactory.make(fullDirectories, ".*");
                COMMON_LOCALES = SimpleFactory.make(base + "/" + "common" + "/main", ".*").getAvailableLanguages();
            }
        }
        fixCommonLocales();

        RAW_DATA = MyOptions.rawData.option.doesOccur();

        //showEnglish();

        showCoverage(null, matcher, locales, useOrgLevel);
    }

    public static void fixCommonLocales() {
        if (COMMON_LOCALES == null) {
            COMMON_LOCALES = factory.getAvailableLanguages();
        }
    }

    private static void doGrowth(Matcher matcher, PrintWriter out) {
        TreeMap<String, List<Double>> growthData = new TreeMap<>(Ordering.natural().reverse()); // sort by version, descending
//        if (DEBUG) {
//            for (String dir : new File(CLDRPaths.ARCHIVE_DIRECTORY).list()) {
//                if (!dir.startsWith("cldr")) {
//                    continue;
//                }
//                String version = getNormalizedVersion(dir);
//                if (version == null) {
//                    continue;
//                }
//                org.unicode.cldr.util.Factory newFactory = org.unicode.cldr.util.Factory.make(
//                    CLDRPaths.ARCHIVE_DIRECTORY + "/" + dir + "/common/main/", ".*");
//                System.out.println("Reading: " + version);
//                Map<String, FoundAndTotal> currentData = addGrowth(newFactory, matcher);
//                System.out.println("Read: " + version + "\t" + currentData);
//                break;
//            }
//        }
        Map<String, FoundAndTotal> latestData = addGrowth(factory, null, matcher, DEBUG);
        addCompletionList(getYearFromVersion(LATEST, false), getCompletion(latestData, latestData), growthData);
        if (DEBUG) System.out.println(latestData);
        //System.out.println(growthData);
        List<String> dirs = new ArrayList<>(Arrays.asList(new File(CLDRPaths.ARCHIVE_DIRECTORY).list()));
        Collections.reverse(dirs);
        for (String dir : dirs) {
            if (!dir.startsWith("cldr")) {
                continue;
            }
            String version = getNormalizedVersion(dir);
            if (version == null) {
                continue;
            }
//            if (version.compareTo("12") < 0) {
//                continue;
//            }
            System.out.println("Reading: " + version);
            if (version.equals("2008")) {
                int debug = 0;
            }
            Map<String, FoundAndTotal> currentData = addGrowth(factory, dir, matcher, false);
            System.out.println("Read: " + version + "\t" + currentData);
            Counter2<String> completionData = getCompletion(latestData, currentData);
            //System.out.println(version + "\t" + completionData);
            addCompletionList(version, completionData, growthData);
            if (DEBUG) System.out.println(currentData);
        }
        boolean first = true;
        for (Entry<String, List<Double>> entry : growthData.entrySet()) {
            if (first) {
                for (int i = 0; i < entry.getValue().size(); ++i) {
                    out.print("\t" + i);
                }
                out.println();
                first = false;
            }
            out.println(entry.getKey() + "\t" + CollectionUtilities.join(entry.getValue(), "\t"));
        }
    }

    static final Map<String, String> versionToYear = new HashMap<>();
    static {
        int[][] mapping = {
            { 34, 2018 },
            { 32, 2017 },
            { 30, 2016 },
            { 28, 2015 },
            { 26, 2014 },
            { 24, 2013 },
            { 22, 2012 },
            { 20, 2011 },
            { 19, 2010 },
            { 17, 2009 },
            { 16, 2008 },
            { 15, 2007 },
            { 14, 2006 },
            { 13, 2005 },
            { 12, 2004 },
            { 10, 2003 },
        };
        for (int[] row : mapping) {
            versionToYear.put(String.valueOf(row[0]), String.valueOf(row[1]));
        }
    }

    public static String getNormalizedVersion(String dir) {
        String rawVersion = dir.substring(dir.indexOf('-') + 1);
        int firstDot = rawVersion.indexOf('.');
        int secondDot = rawVersion.indexOf('.', firstDot + 1);
        if (secondDot > 0) {
            rawVersion = rawVersion.substring(0, firstDot) + rawVersion.substring(firstDot + 1, secondDot);
        } else {
            rawVersion = rawVersion.substring(0, firstDot);
        }
        String result = getYearFromVersion(rawVersion, true);
        return result == null ? null : result.toString();
    }

    private static String getYearFromVersion(String version, boolean allowNull) {
        String result = versionToYear.get(version);
        if (!allowNull && result == null) {
            throw new IllegalArgumentException("No year for version: " + version);
        }
        return result;
    }

    public static void addCompletionList(String version, Counter2<String> completionData, TreeMap<String, List<Double>> growthData) {
        List<Double> x = new ArrayList<>();
        for (String key : completionData.getKeysetSortedByCount(false)) {
            x.add(completionData.getCount(key));
        }
        growthData.put(version, x);
        System.out.println(version + "\t" + x.size());
    }

    public static Counter2<String> getCompletion(Map<String, FoundAndTotal> latestData, Map<String, FoundAndTotal> currentData) {
        Counter2<String> completionData = new Counter2<>();
        for (Entry<String, FoundAndTotal> entry : latestData.entrySet()) {
            final String locale = entry.getKey();
            final FoundAndTotal currentRecord = currentData.get(locale);
            if (currentRecord == null) {
                continue;
            }
            double total = entry.getValue().total;
            if (total == 0) {
                continue;
            }
            double completion = currentRecord.found / total;
            completionData.add(locale, completion);
        }
        return completionData;
    }

    static class FoundAndTotal {
        final int found;
        final int total;

        public FoundAndTotal(Counter<Level>... counters) {
            final int[] count = { 0, 0, 0 };
            for (Level level : Level.values()) {
                if (level == Level.COMPREHENSIVE || level == Level.OPTIONAL) {
                    continue;
                }
                int i = 0;
                for (Counter<Level> counter : counters) {
                    count[i++] += counter.get(level);
                }
            }
            found = count[0];
            total = found + count[1] + count[2];
        }

        @Override
        public String toString() {
            return found + "/" + total;
        }
    }

    private static Map<String, FoundAndTotal> addGrowth(org.unicode.cldr.util.Factory latestFactory, String dir, Matcher matcher, boolean showMissing) {
        org.unicode.cldr.util.Factory newFactory = dir == null ? factory
            : org.unicode.cldr.util.Factory.make(
                CLDRPaths.ARCHIVE_DIRECTORY + "/" + dir + "/common/main/", ".*");
        Map<String, FoundAndTotal> data = new HashMap<>();
        char c = 0;
        Set<String> latestAvailable = newFactory.getAvailableLanguages();
        for (String locale : newFactory.getAvailableLanguages()) {
            if (!matcher.reset(locale).matches()) {
                continue;
            }
            if (!latestAvailable.contains(locale)) {
                continue;
            }
            if (SUPPLEMENTAL_DATA_INFO.getDefaultContentLocales().contains(locale)
                || locale.equals("root")
                || locale.equals("supplementalData")) {
                continue;
            }
            char nc = locale.charAt(0);
            if (nc != c) {
                System.out.println("\t" + locale);
                c = nc;
            }
            if (DEBUG_FILTER != 0 && DEBUG_FILTER != nc) {
                continue;
            }
            CLDRFile latestFile = null;
            try {
                latestFile = latestFactory.make(locale, true);
            } catch (Exception e2) {
                continue;
            }
            final CLDRFile file = newFactory.make(locale, true);
            // HACK check bogus
//            Collection<String> extra = file.getExtraPaths();
//
//            final Iterable<String> fullIterable = file.fullIterable();
//            for (String path : fullIterable) {
//                if (path.contains("\"one[@")) {
//                    boolean inside = extra.contains(path);
//                    Status status = new Status();
//                    String loc = file.getSourceLocaleID(path, status );
//                    int debug = 0;
//                }
//            }
            // END HACK
            Counter<Level> foundCounter = new Counter<Level>();
            Counter<Level> unconfirmedCounter = new Counter<Level>();
            Counter<Level> missingCounter = new Counter<Level>();
            Set<String> unconfirmedPaths = null;
            Relation<MissingStatus, String> missingPaths = null;
            unconfirmedPaths = new LinkedHashSet<>();
            missingPaths = Relation.of(new LinkedHashMap(), LinkedHashSet.class);
            VettingViewer.getStatus(latestFile.fullIterable(), file,
                pathHeaderFactory, foundCounter, unconfirmedCounter,
                missingCounter, missingPaths, unconfirmedPaths);

            // HACK
            Set<Entry<MissingStatus, String>> missingRemovals = new HashSet<>();
            for (Entry<MissingStatus, String> e : missingPaths.keyValueSet()) {
                if (e.getKey() == MissingStatus.ABSENT) {
                    final String path = e.getValue();
                    if (HACK.get(path) != null) {
                        missingRemovals.add(e);
                        missingCounter.add(Level.MODERN, -1);
                        foundCounter.add(Level.MODERN, 1);
                    } else {
                        Status status = new Status();
                        String loc = file.getSourceLocaleID(path, status);
                        int debug = 0;
                    }
                }
            }
            for (Entry<MissingStatus, String> e : missingRemovals) {
                missingPaths.remove(e.getKey(), e.getValue());
            }
            // END HACK

            if (showMissing) {
                int count = 0;
                for (String s : unconfirmedPaths) {
                    System.out.println(++count + "\t" + locale + "\tunconfirmed\t" + s);
                }
                for (Entry<MissingStatus, String> e : missingPaths.keyValueSet()) {
                    String path = e.getValue();
                    Status status = new Status();
                    String loc = file.getSourceLocaleID(path, status);
                    int debug = 0;

                    System.out.println(++count + "\t" + locale + "\t" + CldrUtility.toString(e));
                }
                int debug = 0;
            }

            // add annotations
            System.out.println(locale + " annotations");
            try {
                UnicodeMap<Annotations> annotations = dir == null ? Annotations.getData(locale)
                    : Annotations.getData(CLDRPaths.ARCHIVE_DIRECTORY + "/" + dir + "/common/annotations/", locale);
                for (String cp : ENG_ANN) {
                    Annotations annotation = annotations.get(cp);
                    if (annotation == null) {
                        missingCounter.add(Level.MODERN, 1);
                    } else if (annotation.getShortName() == null) {
                        missingCounter.add(Level.MODERN, 1);
                    } else {
                        foundCounter.add(Level.MODERN, 1);
                    }
                }
            } catch (Exception e1) {
                missingCounter.add(Level.MODERN, ENG_ANN.size());
            }

            data.put(locale, new FoundAndTotal(foundCounter, unconfirmedCounter, missingCounter));
        }
        return Collections.unmodifiableMap(data);
    }

    public static void showCoverage(Anchors anchors) throws IOException {
        showCoverage(anchors, PatternCache.get(".*").matcher(""), null, false);
    }

    public static void showCoverage(Anchors anchors, Matcher matcher, Set<String> locales, boolean useOrgLevel) throws IOException {
        final String title = "Locale Coverage";
        try (PrintWriter pw = new PrintWriter(new FormattedFileWriter(null, title, null, anchors));
            PrintWriter tsv_summary = FileUtilities.openUTF8Writer(CLDRPaths.CHART_DIRECTORY + "tsv/", "locale-coverage.tsv");
            PrintWriter tsv_missing = FileUtilities.openUTF8Writer(CLDRPaths.CHART_DIRECTORY + "tsv/", "locale-missing.tsv");
            PrintWriter tsv_missing_summary = FileUtilities.openUTF8Writer(CLDRPaths.CHART_DIRECTORY + "tsv/", "locale-missing-summary.tsv");
            ){
            printData(pw, tsv_summary, tsv_missing, tsv_missing_summary, locales, matcher, useOrgLevel);
            new ShowPlurals().appendBlanksForScrolling(pw);
        }
    }

//    public static void showEnglish() {
//        Map<PathHeader,String> sorted = new TreeMap<>();
//        CoverageInfo coverageInfo=CLDRConfig.getInstance().getCoverageInfo();
//        for (String path : ENGLISH) {
////            Level currentLevel = SUPPLEMENTAL_DATA_INFO.getCoverageLevel(path, "en");
//            Level currentLevel=coverageInfo.getCoverageLevel(path, "en");
//            if (currentLevel.compareTo(Level.MINIMAL) <= 0) {
//                PathHeader ph = pathHeaderFactory.fromPath(path);
//                sorted.put(ph, currentLevel + "\t" + ENGLISH.getStringValue(path));
//            }
//        }
//        for (Entry<PathHeader, String> entry : sorted.entrySet()) {
//            System.out.println(entry.getKey() + "\t" + entry.getValue());
//        }
//    }

    static class IterableFilter implements Iterable<String> {
        private Iterable<String> source;

        IterableFilter(Iterable<String> source) {
            this.source = source;
        }

        /**
         * When some paths are defined after submission, we need to change them to COMPREHENSIVE in computing the vetting status.
         */

        static final Set<String> SUPPRESS_PATHS_AFTER_SUBMISSION = ImmutableSet.of(
            "//ldml/localeDisplayNames/languages/language[@type=\"ccp\"]",
            "//ldml/localeDisplayNames/territories/territory[@type=\"XA\"]",
            "//ldml/localeDisplayNames/territories/territory[@type=\"XB\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"Gy\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"Gy\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyM\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyM\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyM\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMd\"]/greatestDifference[@id=\"d\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMd\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMd\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMd\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMEd\"]/greatestDifference[@id=\"d\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMEd\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMEd\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMEd\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMM\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMM\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMM\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMd\"]/greatestDifference[@id=\"d\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMd\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMd\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMd\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMEd\"]/greatestDifference[@id=\"d\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMEd\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMEd\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"generic\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMEd\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"Gy\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"Gy\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyM\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyM\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyM\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMd\"]/greatestDifference[@id=\"d\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMd\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMd\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMd\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMEd\"]/greatestDifference[@id=\"d\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMEd\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMEd\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMEd\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMM\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMM\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMM\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMd\"]/greatestDifference[@id=\"d\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMd\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMd\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMd\"]/greatestDifference[@id=\"y\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMEd\"]/greatestDifference[@id=\"d\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMEd\"]/greatestDifference[@id=\"G\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMEd\"]/greatestDifference[@id=\"M\"]",
            "//ldml/dates/calendars/calendar[@type=\"gregorian\"]/dateTimeFormats/intervalFormats/intervalFormatItem[@id=\"GyMMMEd\"]/greatestDifference[@id=\"y\"]"
            );
        @Override
        public Iterator<String> iterator() {
            return new IteratorFilter(source.iterator());
        }

        static class IteratorFilter implements Iterator<String> {
            Iterator<String> source;
            String peek;

            public IteratorFilter(Iterator<String> source) {
                this.source = source;
                fillPeek();
            }
            @Override
            public boolean hasNext() {
                return peek != null;
            }
            @Override
            public String next() {
                String result = peek;
                fillPeek();
                return result;
            }

            private void fillPeek() {
                peek = null;
                while (source.hasNext()) {
                    peek = source.next();
                    // if it is ok to assess, then break
                    if (!SUPPRESS_PATHS_AFTER_SUBMISSION.contains(peek)
                        && SUPPRESS_PATHS_CAN_BE_EMPTY.get(peek) != Boolean.TRUE) {
                        break;
                    }
                    peek = null;
                }
            }
        }

    }
    static void printData(PrintWriter pw, PrintWriter tsv_summary, PrintWriter tsv_missing, PrintWriter tsv_missing_summary, Set<String> locales, Matcher matcher, boolean useOrgLevel) {
//        Set<String> checkModernLocales = STANDARD_CODES.getLocaleCoverageLocales("google", EnumSet.of(Level.MODERN));
        Set<String> checkModernLocales = STANDARD_CODES.getLocaleCoverageLocales(Organization.cldr, EnumSet.of(Level.MODERN));
        Set<String> availableLanguages = new TreeSet<>(factory.getAvailableLanguages());
        availableLanguages.addAll(checkModernLocales);
        Relation<String, String> languageToRegion = Relation.of(new TreeMap(), TreeSet.class);
        LanguageTagParser ltp = new LanguageTagParser();
        LanguageTagCanonicalizer ltc = new LanguageTagCanonicalizer(true);
        for (String locale : factory.getAvailable()) {
            String country = ltp.set(locale).getRegion();
            if (!country.isEmpty()) {
                languageToRegion.put(ltc.transform(ltp.getLanguageScript()), country);
            }
        }

        fixCommonLocales();

        System.out.println(CollectionUtilities.join(languageToRegion.keyValuesSet(), "\n"));

        System.out.println("# Checking: " + availableLanguages);

        pw.println("<p style='text-align: left'>This chart shows the coverage levels for this release. </p>" +
            "<ol>"
            + "<li>Fields = fields found at a modern level</li>"
            + "<li>UC = unconfirmed values: typically treated as missing by implementations</li>"
            + "<li>Miss = missing values</li>"
            + "<li>Modern%, etc = fields/(fields + missing + unconfirmed) — at that level</li>"
            + "<li>Core Missing = missing core fields — optionals marked with *</li></ol>"
            + "<p>A high-level summary of the meaning of the coverage values are at " +
            "<a target='_blank' href='http://www.unicode.org/reports/tr35/tr35-info.html#Coverage_Levels'>Coverage Levels</a>. " +
            "The Core values are described on " +
            "<a target='_blank' href='http://cldr.unicode.org/index/cldr-spec/minimaldata'>Core Data</a>." +
            "</p>");

        Relation<MissingStatus, String> missingPaths = Relation.of(new EnumMap<MissingStatus, Set<String>>(
            MissingStatus.class), TreeSet.class, CLDRFile.getComparator(DtdType.ldml));
        Set<String> unconfirmed = new TreeSet<String>(CLDRFile.getComparator(DtdType.ldml));

        //Map<String, String> likely = testInfo.getSupplementalDataInfo().getLikelySubtags();
        Set<String> defaultContents = SUPPLEMENTAL_DATA_INFO.getDefaultContentLocales();

        // Map<String,Counter<Level>> counts = new HashMap();
        //        System.out.print("Script\tEnglish\tNative\tCode\tCode*");
        //        for (Level level : Level.values()) {
        //            if (skipPrintingLevels.contains(level)) {
        //                continue;
        //            }
        //            System.out.print("\t≤" + level + " (f)\t(u)\t(m)");
        //        }
        //        System.out.println();
        // Factory pathHeaderFactory = PathHeader.getFactory(testInfo.getCldrFactory().make("en", true));

        tsv_missing.println(SPREADSHEET_MISSING);

        Counter<Level> foundCounter = new Counter<Level>();
        Counter<Level> unconfirmedCounter = new Counter<Level>();
        Counter<Level> missingCounter = new Counter<Level>();

        List<Level> levelsToShow = new ArrayList<>(EnumSet.allOf(Level.class));
        levelsToShow.remove(Level.COMPREHENSIVE);
        levelsToShow.remove(Level.UNDETERMINED);
        levelsToShow = ImmutableList.copyOf(levelsToShow);
        List<Level> reversedLevels = new ArrayList<>(levelsToShow);
        Collections.reverse(reversedLevels);
        reversedLevels = ImmutableList.copyOf(reversedLevels);


//        PrintWriter out2;
//        try {
//            out2 = FileUtilities.openUTF8Writer(CLDRPaths.CHART_DIRECTORY + "tsv/", "showLocaleCoverage.tsv");
//        } catch (IOException e1) {
//            throw new ICUUncheckedIOException(e1);
//        }
//
//        out2.print("Code\tCom?\tEnglish Name\tNative Name\tScript\tSublocales\tStrings");
//        for (Level level : reversedLevels) {
//            out2.print("\t" + level + " %\t" + level + " UC%");
//        }
//        out2.println();
        //System.out.println("\tCore*\nCore* Missing");
        int localeCount = 0;

        final TablePrinter tablePrinter = new TablePrinter()
            .addColumn("Direct.", "class='source'", null, "class='source'", true)
            .setBreakSpans(true).setSpanRows(false)
            .addColumn("Code", "class='source'", CldrUtility.getDoubleLinkMsg(), "class='source'", true).setBreakSpans(true)
            .addColumn("English Name", "class='source'", null, "class='source'", true).setBreakSpans(true)
            .addColumn("Native Name", "class='source'", null, "class='source'", true).setBreakSpans(true)
            .addColumn("Script", "class='source'", null, "class='source'", true).setBreakSpans(true)
            .addColumn("CLDR target", "class='source'", null, "class='source'", true).setBreakSpans(true).setSortPriority(0).setSortAscending(false)
            .addColumn("ICU", "class='source'", null, "class='source'", true).setBreakSpans(true).setSortPriority(1).setSortAscending(false)
            .addColumn("Sublocales", "class='target'", null, "class='targetRight'", true).setBreakSpans(true)
            .setCellPattern("{0,number}")
            .addColumn("Fields", "class='target'", null, "class='targetRight'", true).setBreakSpans(true)
            .setCellPattern("{0,number}")
            .addColumn("UC", "class='target'", null, "class='targetRight'", true).setBreakSpans(true)
            .setCellPattern("{0,number}")
            .addColumn("Miss", "class='target'", null, "class='targetRight'", true).setBreakSpans(true)
            .setCellPattern("{0,number}")
            //.addColumn("Target Level", "class='target'", null, "class='target'", true).setBreakSpans(true)
            ;
        tsv_summary.println("Dir"
            + "\tCode"
            + "\tEnglish Name"
            + "\tNative Name"
            + "\tScript"
            + "\tCLDR target"
            + "\tICU"
            + "\tSublocales"
            + "\tFields\tUC\tMissing"
            + "\tModern\tMiss +UC"
            + "\tModerate\tMiss +UC"
            + "\tBasic\tMiss +UC"
            + "\tCore\tMiss +UC"
            + "\tCore-Missing");
        NumberFormat tsvPercent = NumberFormat.getPercentInstance(Locale.ENGLISH);
        tsvPercent.setMaximumFractionDigits(2);

        for (Level level : reversedLevels) {
            String titleLevel = level.toString();
            tablePrinter.addColumn(UCharacter.toTitleCase(titleLevel, null) + "%", "class='target'", null, "class='targetRight'", true)
            .setCellPattern("{0,number,0.0%}")
            .setBreakSpans(true);
            switch(level) {
            case CORE: 
                tablePrinter.setSortPriority(5).setSortAscending(false);
                break;
            case BASIC: 
                tablePrinter.setSortPriority(4).setSortAscending(false);
                break;
            case MODERATE: 
                tablePrinter.setSortPriority(3).setSortAscending(false);
                break;
            case MODERN: 
                tablePrinter.setSortPriority(2).setSortAscending(false);
                break;
            }
//            tablePrinter
//            .addColumn("∪ UC%", "class='target'", null, "class='targetRight'", true)
//            .setCellPattern("{0,number,0.0%}")
//            .setBreakSpans(true)
            ;
        }
        tablePrinter.addColumn("Core Missing", "class='target'", null, "class='targetRight'", true)
        .setBreakSpans(true);

        long start = System.currentTimeMillis();
        LikelySubtags likelySubtags = new LikelySubtags();

        EnumMap<Level, Double> targetLevel = new EnumMap<>(Level.class);
        targetLevel.put(Level.CORE, 2 / 100d);
        targetLevel.put(Level.BASIC, 16 / 100d);
        targetLevel.put(Level.MODERATE, 33 / 100d);
        targetLevel.put(Level.MODERN, 100 / 100d);

//        NumberFormat percentFormat = NumberFormat.getPercentInstance(ULocale.ENGLISH);
//        percentFormat.setMaximumFractionDigits(2);
//        percentFormat.setMinimumFractionDigits(2);
//        NumberFormat intFormat = NumberFormat.getIntegerInstance(ULocale.ENGLISH);

        Multimap<String, String> pathToLocale = TreeMultimap.create();

        int counter = 0;
        for (String locale : availableLanguages) {
            try {
                if (locale.contains("supplemental")) { // for old versions
                    continue;
                }
                if (locales != null && !locales.contains(locale)) {
                    String base = CLDRLocale.getInstance(locale).getLanguage();
                    if (!locales.contains(base)) {
                        continue;
                    }
                }
                if (!matcher.reset(locale).matches()) {
                    continue;
                }
                if (defaultContents.contains(locale) || "root".equals(locale) || "und".equals(locale)) {
                    continue;
                }

                boolean isSeed = new File(CLDRPaths.SEED_DIRECTORY, locale + ".xml").exists();

                //boolean capture = locale.equals("en");
                String region = ltp.set(locale).getRegion();
                if (!region.isEmpty()) continue; // skip regions

                final Level cldrLocaleLevelGoal = SC.getLocaleCoverageLevel(Organization.cldr.toString(), locale);
                final boolean cldrLevelGoalModerateOrAbove = cldrLocaleLevelGoal.compareTo(Level.MODERATE) >= 0;

                String isCommonLocale = Level.MODERN == cldrLocaleLevelGoal ? "C*"
                    : COMMON_LOCALES.contains(locale) ? "C"
                        : "";

                String max = likelySubtags.maximize(locale);
                String script = ltp.set(max).getScript();

                String language = likelySubtags.minimize(locale);
//                Level otherLevel = STANDARD_CODES.getLocaleCoverageLevel("apple", locale);
//                if (otherLevel.compareTo(currentLevel) > 0
//                    && otherLevel.compareTo(Level.MODERN) <= 0) {
//                    currentLevel = otherLevel;
//                }

                missingPaths.clear();
                unconfirmed.clear();

                final CLDRFile file = factory.make(locale, true, minimumDraftStatus);

                if (locale.equals("af")) {
                    int debug = 0;
                }

                Iterable<String> pathSource = new IterableFilter(file.fullIterable());

                VettingViewer.getStatus(pathSource, file,
                    pathHeaderFactory, foundCounter, unconfirmedCounter,
                    missingCounter, missingPaths, unconfirmed);

                Set<String> sublocales = languageToRegion.get(language);
                if (sublocales == null) {
                    //System.err.println("No Sublocales: " + language);
                    sublocales = Collections.EMPTY_SET;
                }

//                List s = Lists.newArrayList(file.fullIterable());

                String seedString = isSeed ? "seed" : "common";
                tablePrinter.addRow()
                .addCell(seedString)
                .addCell(language)
                .addCell(ENGLISH.getName(language))
                .addCell(file.getName(language))
                .addCell(script)
                .addCell(cldrLocaleLevelGoal == Level.UNDETERMINED ? "" : cldrLocaleLevelGoal.toString())
                .addCell(getIcuValue(language))
                .addCell(sublocales.size());

                tsv_summary
                .append(seedString)
                .append('\t').append(language)
                .append('\t').append(ENGLISH.getName(language))
                .append('\t').append(file.getName(language))
                .append('\t').append(script)
                .append('\t').append(cldrLocaleLevelGoal.toString())
                .append('\t').append(sublocales.size()+"");
                ;

//                String header = language
//                    + "\t" + isCommonLocale
//                    + "\t" + ENGLISH.getName(language)
//                    + "\t" + file.getName(language)
//                    + "\t" + script
//                    + "\t" + sublocales.size()
//                    //+ "\t" + currentLevel
//                    ;

                int sumFound = 0;
                int sumMissing = 0;
                int sumUnconfirmed = 0;

                // get the totals

                EnumMap<Level, Integer> totals = new EnumMap<>(Level.class);
                EnumMap<Level, Integer> confirmed = new EnumMap<>(Level.class);
//                EnumMap<Level, Integer> unconfirmedByLevel = new EnumMap<>(Level.class);
                Set<String> coreMissing = new LinkedHashSet<>();

                if (locale.equals("af")) {
                    int debug = 0;
                }

                { // CORE
                    long missingExemplarCount = missingCounter.get(Level.CORE);
                    if (missingExemplarCount > 0) {
                        for (Entry<MissingStatus, String> statusAndPath : missingPaths.entrySet()) {
                            String path = statusAndPath.getValue();
                            if (path.startsWith("//ldml/characters/exemplarCharacters")) {
                                PathHeader ph = pathHeaderFactory.fromPath(path);
                                String problem = ph.getCode().replaceAll("Others: ","").replaceAll("Main Letters", "main-letters");
                                coreMissing.add(problem);
                                // String line = spreadsheetLine(locale, script, language, cldrLevelGoal, foundLevel, missingStatus.toString(), path, file.getStringValue(path));
                                String line = spreadsheetLine(locale, language, script, "«No " + problem + "»", cldrLocaleLevelGoal, Level.CORE, "ABSENT", path, pathToLocale);
                                tsv_missing.println(line);
                            }
                        }
                    }
                    Multimap<CoreItems, String> detailedErrors = LinkedHashMultimap.create();
                    Set<CoreItems> coverage = new TreeSet<>(
                        CoreCoverageInfo.getCoreCoverageInfo(file, detailedErrors));
                    Set<CoreItems> missing = EnumSet.allOf(CoreItems.class);
                    missing.removeAll(coverage);
                    for (Entry<CoreItems, String> entry : detailedErrors.entries()) {
                        CoreItems coreItem = entry.getKey();
                        String value = entry.getValue();
                        coreMissing.add(coreItem.toString());
                        //String line = spreadsheetLine(language, script, "n/a", detailedErrors.get(entry).toString(), level, "ABSENT", "n/a", "n/a", "n/a");
                        if (cldrLevelGoalModerateOrAbove) {
                            String line = spreadsheetLine(locale, language, script, "«No " + coreItem + "»", cldrLocaleLevelGoal, coreItem.desiredLevel, "ABSENT", value, pathToLocale);
                            tsv_missing.println(line);
                        }
                    }
                    missing.removeAll(CoreItems.ONLY_RECOMMENDED);
                    foundCounter.add(Level.CORE, coverage.size());
                    missingCounter.add(Level.CORE, missing.size());

//                    sumFound += coverage.size();
//                    sumMissing += missing.size();

//                    confirmed.put(Level.CORE, (int) coverage.size());
////                    unconfirmedByLevel.put(level, (int)(foundCount + unconfirmedCount));
//                    totals.put(Level.CORE, (int)(coverage.size() + missing.size()));

                }

                if (!seedString.equals("seed")) {
                    Level goalLevel = cldrLocaleLevelGoal;
                    if (goalLevel.compareTo(Level.BASIC) < 0) {
                        goalLevel = Level.BASIC;
                    }

                    for (Entry<MissingStatus, String> entry : missingPaths.entrySet()) {
                        String path = entry.getValue();
                        String status = entry.getKey().toString();
                        Level foundLevel = coverageInfo.getCoverageLevel(path, locale);
                        if (goalLevel.compareTo(foundLevel) >= 0) {
                            String line = spreadsheetLine(locale, language, script, file.getStringValue(path), goalLevel, foundLevel, status, path, pathToLocale);
                            tsv_missing.println(line);
                        }
                    }
                    for (String path : unconfirmed) {
                        Level foundLevel = coverageInfo.getCoverageLevel(path, locale);
                        if (goalLevel.compareTo(foundLevel) >= 0) {
                            String line = spreadsheetLine(locale, language, script, file.getStringValue(path), goalLevel, foundLevel, "n/a", path, pathToLocale);
                            tsv_missing.println(line);
                        }
                    }
                }

                for (Level level : levelsToShow) {   
                    long foundCount = foundCounter.get(level);
                    long unconfirmedCount = unconfirmedCounter.get(level);
                    long missingCount = missingCounter.get(level);

                    sumFound += foundCount;
                    sumUnconfirmed += unconfirmedCount;
                    sumMissing += missingCount;

                    confirmed.put(level, (int) sumFound);
//                    unconfirmedByLevel.put(level, (int)(foundCount + unconfirmedCount));
                    totals.put(level, (int)(sumFound + sumUnconfirmed + sumMissing));
                }

                tsv_missing.flush();

                double modernTotal = totals.get(Level.MODERN);

                tablePrinter
                .addCell(sumFound)
                .addCell(sumUnconfirmed)
                .addCell(sumMissing)
                ;

                tsv_summary
                .append('\t').append(sumFound+"")
                .append('\t').append(sumUnconfirmed+"")
                .append('\t').append(sumMissing+"")
                ;


//                header += "\t" + sumFound;
//                header += "\t" + (sumFound + sumUnconfirmed);

                // print the totals

                for (Level level : reversedLevels) {
                    if (useOrgLevel && cldrLocaleLevelGoal != level) {
                        continue;
                    }
                    int confirmedCoverage = confirmed.get(level);
//                    int unconfirmedCoverage = unconfirmedByLevel.get(level);
                    double total = totals.get(level);

                    tablePrinter
                    .addCell(confirmedCoverage / total)
//                    .addCell(unconfirmedCoverage / total)
                    ;

                    tsv_summary
                    .append('\t').append(String.valueOf(confirmedCoverage))
                    .append('\t').append(String.valueOf((int)total - confirmedCoverage))
                    ;

//                    if (RAW_DATA) {
//                        header += "\t" + confirmedCoverage / total
//                            + "\t" + unconfirmedCoverage / total;
//                    } else {
//                        Double factor = targetLevel.get(level) / (total / modernTotal);
//                        header += "\t" + factor * confirmedCoverage / modernTotal
////                            + "\t" + factor * unconfirmedCoverage / modernTotal
//                            ;
//                    }
                }
                String coreMissingString = 
                    CollectionUtilities.join(coreMissing, ", ");

                tablePrinter
                .addCell(coreMissingString)
                .finishRow();

                tsv_summary
                .append('\t')
                .append(coreMissingString)
                .append('\n');

                //out2.println(header + "\t" + coreValue + "\t" + CollectionUtilities.join(missing, ", "));

                // Write missing paths (for >99% and specials

//                if (false) { // checkModernLocales.contains(locale)
//                    CoverageLevel2 coverageLevel2 = CoverageLevel2.getInstance(locale);
//
//                    for (String path : unconfirmed) {
//                        Level level = coverageLevel2.getLevel(path);
//                        if (level.compareTo(cldrLocaleLevelGoal) > 0) {
//                            continue;
//                        }
//                        String line = spreadsheetLine(locale, language, script, file.getStringValue(path), cldrLocaleLevelGoal, level, "UNCONFIRMED", path, pathToLocale);
//                        if (SUPPRESS_PATHS_CAN_BE_EMPTY.get(path) != null) {
//                            //System.out.println("\nSKIP: " + line);
//                        } else {
//                            tsv_missing.println(line);
//                        }
//                    }
//                    for (Entry<MissingStatus, String> entry : missingPaths.entrySet()) {
//                        String path = entry.getValue();
//                        Level level = coverageLevel2.getLevel(path);
//                        if (level.compareTo(cldrLocaleLevelGoal) > 0) {
//                            continue;
//                        }
//                        MissingStatus missingStatus = entry.getKey();
//                        String line = spreadsheetLine(locale, language, script, "???", cldrLocaleLevelGoal, level, missingStatus.toString(), path, pathToLocale);
//                        if (SUPPRESS_PATHS_CAN_BE_EMPTY.get(path) != null) {
//                            //System.out.println("\nSKIP: " + line);
//                        } else {
//                            tsv_missing.println(line);
//                        }
//                    }
//                }

                localeCount++;
            } catch (Exception e) {
                throw new IllegalArgumentException(e);
            }
        }
        pw.println(tablePrinter.toTable());

        Multimap<Level, String> levelToLocales = TreeMultimap.create();

        for ( Entry<String, Collection<String>> entry : pathToLocale.asMap().entrySet()) {
            String path = entry.getKey();
            Collection<String> localeSet = entry.getValue();
            levelToLocales.clear();
            for (String locale : localeSet) {
                Level foundLevel = coverageInfo.getCoverageLevel(path, locale);
                levelToLocales.put(foundLevel, locale);
            }
            for (Entry<Level, Collection<String>> entry2 : levelToLocales.asMap().entrySet()) {
                Level level = entry2.getKey();
                localeSet = entry2.getValue();
                tsv_missing_summary.println(level + "\t" + path + "\t" + CollectionUtilities.join(localeSet, " "));
            }
        }
//        out2.close();

        long end = System.currentTimeMillis();
        System.out.println((end - start) + " millis = "
            + ((end - start) / localeCount) + " millis/locale");

        //        CoverageLevel2 coverageLevel2 = CoverageLevel2.getInstance("en");
        //
        //        for (Entry<MissingStatus, Set<String>> entity : missingPaths.keyValuesSet()) {
        //            for (PathHeader s : CldrUtility.transform(entity.getValue(), pathHeaderFactory, new TreeSet<PathHeader>())) {
        //                System.out.println(entity.getKey() + "\t" + coverageLevel2.getLevel(s.getOriginalPath()) + "\t" + s
        //                        + "\t\t" + s.getOriginalPath());
        //            }
        //        }
    }

    static final CoverageInfo coverageInfo = new CoverageInfo(SUPPLEMENTAL_DATA_INFO);

// userInfo.getVoterInfo().getLevel().compareTo(VoteResolver.Level.tc)
    static final VoterInfo dummyVoterInfo = new VoterInfo(Organization.cldr, org.unicode.cldr.util.VoteResolver.Level.vetter, "somename");

    static final UserInfo dummyUserInfo = new UserInfo() {
        public VoterInfo getVoterInfo() {
            return dummyVoterInfo;
        }
    };
    static final PathValueInfo dummyPathValueInfo = new PathValueInfo() {
        // pathValueInfo.getCoverageLevel().compareTo(Level.COMPREHENSIVE)
        public Collection<? extends CandidateInfo> getValues() {
            throw new UnsupportedOperationException();
        }
        public CandidateInfo getCurrentItem() {
            throw new UnsupportedOperationException();
        }
        public String getLastReleaseValue() {
            throw new UnsupportedOperationException();
        }
        public Level getCoverageLevel() {
            return Level.MODERN;
        }
        public boolean hadVotesSometimeThisRelease() {
            throw new UnsupportedOperationException();
        }
        public CLDRLocale getLocale() {
            throw new UnsupportedOperationException();
        }
        public String getXpath() {
            throw new UnsupportedOperationException();
        }
    };

    public static String spreadsheetLine(String locale, String language, String script, String nativeValue, Level cldrLocaleLevelGoal, 
        Level itemLevel, String status, String path, Multimap<String, String> pathToLocale) {
        if (pathToLocale != null) {
            pathToLocale.put(path, locale);
        }
        String phString = "n/a\tn/a\tn/a\tn/a";
        String stLink = "n/a";
        String englishValue = "n/a";
        StatusAction action = null;
        SurveyToolStatus surveyToolStatus = null;
        String icuValue = getIcuValue(locale);
        try {
            PathHeader ph = pathHeaderFactory.fromPath(path);
            phString = ph.toString();
            surveyToolStatus = ph.getSurveyToolStatus();
            action = Phase.SUBMISSION.getShowRowAction(dummyPathValueInfo, InputMethod.DIRECT, surveyToolStatus, dummyUserInfo);
            stLink = URLS.forXpath(locale, ph.getOriginalPath());
            englishValue = ENGLISH.getStringValue(path);
        } catch (Exception e) {
        }
        String line = language
            + "\t" + ENGLISH.getName(language)
            + "\t" + ENGLISH.getName("script", script)
            //+ "\t" + englishValue
            //+ "\t" + nativeValue
            + "\t" + cldrLocaleLevelGoal
            + "\t" + icuValue
            + "\t" + itemLevel
            + "\t" + status
            + "\t" + (action == null ? "?" : action.toString())
            + "\t" + (surveyToolStatus == null ? "?" : surveyToolStatus.toString())
            //+ "\t" + stLink
            + "\t" + phString
            //+ "\t" + path
            ;
        return line;
    }

    private static String getIcuValue(String locale) {
        return ICU_Locales.contains(locale) ? "ICU" : "";
    }

    static final Set<String> ICU_Locales = ImmutableSet.of("af", "af_NA", "af_ZA", "agq", "agq_CM", "ak", "ak_GH", "am", "am_ET", "ar", "ar_001", "ar_AE",
        "ar_BH", "ar_DJ", "ar_DZ", "ar_EG", "ar_EH", "ar_ER", "ar_IL", "ar_IQ", "ar_JO", "ar_KM", "ar_KW", "ar_LB", "ar_LY", "ar_MA", "ar_MR", "ar_OM", "ar_PS",
        "ar_QA", "ar_SA", "ar_SD", "ar_SO", "ar_SS", "ar_SY", "ar_TD", "ar_TN", "ar_YE", "ars", "as", "as_IN", "asa", "asa_TZ", "ast", "ast_ES", "az", "az_AZ",
        "az_Cyrl", "az_Cyrl_AZ", "az_Latn", "az_Latn_AZ", "bas", "bas_CM", "be", "be_BY", "bem", "bem_ZM", "bez", "bez_TZ", "bg", "bg_BG", "bm", "bm_ML", "bn",
        "bn_BD", "bn_IN", "bo", "bo_CN", "bo_IN", "br", "br_FR", "brx", "brx_IN", "bs", "bs_Cyrl", "bs_Cyrl_BA", "bs_Latn", "bs_Latn_BA", "bs_BA", "ca",
        "ca_AD", "ca_ES", "ca_FR", "ca_IT", "ccp", "ccp_BD", "ccp_IN", "ce", "ce_RU", "cgg", "cgg_UG", "chr", "chr_US", "ckb", "ckb_IQ", "ckb_IR", "cs",
        "cs_CZ", "cy", "cy_GB", "da", "da_DK", "da_GL", "dav", "dav_KE", "de", "de_AT", "de_BE", "de_CH", "de_DE", "de_IT", "de_LI", "de_LU", "dje", "dje_NE",
        "dsb", "dsb_DE", "dua", "dua_CM", "dyo", "dyo_SN", "dz", "dz_BT", "ebu", "ebu_KE", "ee", "ee_GH", "ee_TG", "el", "el_CY", "el_GR", "en", "en_001",
        "en_150", "en_AG", "en_AI", "en_AS", "en_AT", "en_AU", "en_BB", "en_BE", "en_BI", "en_BM", "en_BS", "en_BW", "en_BZ", "en_CA", "en_CC", "en_CH",
        "en_CK", "en_CM", "en_CX", "en_CY", "en_DE", "en_DG", "en_DK", "en_DM", "en_ER", "en_FI", "en_FJ", "en_FK", "en_FM", "en_GB", "en_GD", "en_GG", "en_GH",
        "en_GI", "en_GM", "en_GU", "en_GY", "en_HK", "en_IE", "en_IL", "en_IM", "en_IN", "en_IO", "en_JE", "en_JM", "en_KE", "en_KI", "en_KN", "en_KY", "en_LC",
        "en_LR", "en_LS", "en_MG", "en_MH", "en_MO", "en_MP", "en_MS", "en_MT", "en_MU", "en_MW", "en_MY", "en_NA", "en_NF", "en_NG", "en_NL", "en_NR", "en_NU",
        "en_NZ", "en_PG", "en_PH", "en_PK", "en_PN", "en_PR", "en_PW", "en_RH", "en_RW", "en_SB", "en_SC", "en_SD", "en_SE", "en_SG", "en_SH", "en_SI", "en_SL",
        "en_SS", "en_SX", "en_SZ", "en_TC", "en_TK", "en_TO", "en_TT", "en_TV", "en_TZ", "en_UG", "en_UM", "en_US", "en_US_POSIX", "en_VC", "en_VG", "en_VI",
        "en_VU", "en_WS", "en_ZA", "en_ZM", "en_ZW", "eo", "es", "es_003", "es_419", "es_AR", "es_BO", "es_BR", "es_BZ", "es_CL", "es_CO", "es_CR", "es_CU",
        "es_DO", "es_EA", "es_EC", "es_ES", "es_GQ", "es_GT", "es_HN", "es_IC", "es_MX", "es_NI", "es_PA", "es_PE", "es_PH", "es_PR", "es_PY", "es_SV", "es_US",
        "es_UY", "es_VE", "et", "et_EE", "eu", "eu_ES", "ewo", "ewo_CM", "fa", "fa_AF", "fa_IR", "ff", "ff_CM", "ff_GN", "ff_MR", "ff_SN", "fi", "fi_FI", "fil",
        "fil_PH", "fo", "fo_DK", "fo_FO", "fr", "fr_BE", "fr_BF", "fr_BI", "fr_BJ", "fr_BL", "fr_CA", "fr_CD", "fr_CF", "fr_CG", "fr_CH", "fr_CI", "fr_CM",
        "fr_DJ", "fr_DZ", "fr_FR", "fr_GA", "fr_GF", "fr_GN", "fr_GP", "fr_GQ", "fr_HT", "fr_KM", "fr_LU", "fr_MA", "fr_MC", "fr_MF", "fr_MG", "fr_ML", "fr_MQ",
        "fr_MR", "fr_MU", "fr_NC", "fr_NE", "fr_PF", "fr_PM", "fr_RE", "fr_RW", "fr_SC", "fr_SN", "fr_SY", "fr_TD", "fr_TG", "fr_TN", "fr_VU", "fr_WF", "fr_YT",
        "fur", "fur_IT", "fy", "fy_NL", "ga", "ga_IE", "gd", "gd_GB", "gl", "gl_ES", "gsw", "gsw_CH", "gsw_FR", "gsw_LI", "gu", "gu_IN", "guz", "guz_KE", "gv",
        "gv_IM", "ha", "ha_GH", "ha_NE", "ha_NG", "haw", "haw_US", "he", "he_IL", "hi", "hi_IN", "hr", "hr_BA", "hr_HR", "hsb", "hsb_DE", "hu", "hu_HU", "hy",
        "hy_AM", "ia", "ia_001", "id", "id_ID", "ig", "ig_NG", "ii", "ii_CN", "in", "in_ID", "is", "is_IS", "it", "it_CH", "it_IT", "it_SM", "it_VA", "iw",
        "iw_IL", "ja", "ja_JP", "jgo", "jgo_CM", "jmc", "jmc_TZ", "jv", "jv_ID", "ka", "ka_GE", "kab", "kab_DZ", "kam", "kam_KE", "kde", "kde_TZ", "kea",
        "kea_CV", "khq", "khq_ML", "ki", "ki_KE", "kk", "kk_KZ", "kkj", "kkj_CM", "kl", "kl_GL", "kln", "kln_KE", "km", "km_KH", "kn", "kn_IN", "ko", "ko_KP",
        "ko_KR", "kok", "kok_IN", "ks", "ks_IN", "ksb", "ksb_TZ", "ksf", "ksf_CM", "ksh", "ksh_DE", "ku", "ku_TR", "kw", "kw_GB", "ky", "ky_KG", "lag",
        "lag_TZ", "lb", "lb_LU", "lg", "lg_UG", "lkt", "lkt_US", "ln", "ln_AO", "ln_CD", "ln_CF", "ln_CG", "lo", "lo_LA", "lrc", "lrc_IQ", "lrc_IR", "lt",
        "lt_LT", "lu", "lu_CD", "luo", "luo_KE", "luy", "luy_KE", "lv", "lv_LV", "mas", "mas_KE", "mas_TZ", "mer", "mer_KE", "mfe", "mfe_MU", "mg", "mg_MG",
        "mgh", "mgh_MZ", "mgo", "mgo_CM", "mi", "mi_NZ", "mk", "mk_MK", "ml", "ml_IN", "mn", "mn_MN", "mo", "mr", "mr_IN", "ms", "ms_BN", "ms_MY", "ms_SG",
        "mt", "mt_MT", "mua", "mua_CM", "my", "my_MM", "mzn", "mzn_IR", "naq", "naq_NA", "nb", "nb_NO", "nb_SJ", "nd", "nd_ZW", "nds", "nds_DE", "nds_NL", "ne",
        "ne_IN", "ne_NP", "nl", "nl_AW", "nl_BE", "nl_BQ", "nl_CW", "nl_NL", "nl_SR", "nl_SX", "nmg", "nmg_CM", "nn", "nn_NO", "nnh", "nnh_CM", "no", "no_NO",
        "nus", "nus_SS", "nyn", "nyn_UG", "om", "om_ET", "om_KE", "or", "or_IN", "os", "os_GE", "os_RU", "pa", "pa_Arab", "pa_Arab_PK", "pa_Guru", "pa_Guru_IN",
        "pa_IN", "pa_PK", "pl", "pl_PL", "ps", "ps_AF", "pt", "pt_AO", "pt_BR", "pt_CH", "pt_CV", "pt_GQ", "pt_GW", "pt_LU", "pt_MO", "pt_MZ", "pt_PT", "pt_ST",
        "pt_TL", "qu", "qu_BO", "qu_EC", "qu_PE", "rm", "rm_CH", "rn", "rn_BI", "ro", "ro_MD", "ro_RO", "rof", "rof_TZ", "root", "ru", "ru_BY", "ru_KG",
        "ru_KZ", "ru_MD", "ru_RU", "ru_UA", "rw", "rw_RW", "rwk", "rwk_TZ", "sah", "sah_RU", "saq", "saq_KE", "sbp", "sbp_TZ", "sd", "sd_PK", "se", "se_FI",
        "se_NO", "se_SE", "seh", "seh_MZ", "ses", "ses_ML", "sg", "sg_CF", "sh", "sh_BA", "sh_CS", "sh_YU", "shi", "shi_Latn", "shi_Latn_MA", "shi_Tfng",
        "shi_Tfng_MA", "shi_MA", "si", "si_LK", "sk", "sk_SK", "sl", "sl_SI", "smn", "smn_FI", "sn", "sn_ZW", "so", "so_DJ", "so_ET", "so_KE", "so_SO", "sq",
        "sq_AL", "sq_MK", "sq_XK", "sr", "sr_Cyrl", "sr_Cyrl_BA", "sr_Cyrl_ME", "sr_Cyrl_RS", "sr_Cyrl_CS", "sr_Cyrl_XK", "sr_Cyrl_YU", "sr_Latn", "sr_Latn_BA",
        "sr_Latn_ME", "sr_Latn_RS", "sr_Latn_CS", "sr_Latn_XK", "sr_Latn_YU", "sr_BA", "sr_ME", "sr_RS", "sr_CS", "sr_YU", "sv", "sv_AX", "sv_FI", "sv_SE",
        "sw", "sw_CD", "sw_KE", "sw_TZ", "sw_UG", "ta", "ta_IN", "ta_LK", "ta_MY", "ta_SG", "te", "te_IN", "teo", "teo_KE", "teo_UG", "tg", "tg_TJ", "th",
        "th_TH", "ti", "ti_ER", "ti_ET", "tk", "tk_TM", "tl", "tl_PH", "to", "to_TO", "tr", "tr_CY", "tr_TR", "tt", "tt_RU", "twq", "twq_NE", "tzm", "tzm_MA",
        "ug", "ug_CN", "uk", "uk_UA", "ur", "ur_IN", "ur_PK", "uz", "uz_AF", "uz_Arab", "uz_Arab_AF", "uz_Cyrl", "uz_Cyrl_UZ", "uz_Latn", "uz_Latn_UZ", "uz_UZ",
        "vai", "vai_Latn", "vai_Latn_LR", "vai_LR", "vai_Vaii", "vai_Vaii_LR", "vi", "vi_VN", "vun", "vun_TZ", "wae", "wae_CH", "wo", "wo_SN", "xh", "xh_ZA",
        "xog", "xog_UG", "yav", "yav_CM", "yi", "yi_001", "yo", "yo_BJ", "yo_NG", "yue", "yue_Hans", "yue_Hans_CN", "yue_Hant", "yue_Hant_HK", "zgh", "zgh_MA",
        "zh", "zh_Hans", "zh_Hans_CN", "zh_Hans_HK", "zh_Hans_MO", "zh_Hans_SG", "zh_Hant", "zh_Hant_HK", "zh_Hant_MO", "zh_Hant_TW", "zh_CN", "zh_HK", "zh_MO",
        "zh_SG", "zh_TW", "zu", "zu_ZA");
    private static CLDRURLS URLS = CLDRConfig.getInstance().urls();

}
