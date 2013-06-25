package org.unicode.cldr.util;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.unicode.cldr.draft.FileUtilities;
import org.unicode.cldr.test.CheckCLDR;
import org.unicode.cldr.test.CheckCLDR.CheckStatus;
import org.unicode.cldr.test.CheckCLDR.CheckStatus.Subtype;
import org.unicode.cldr.test.CheckCoverage;
import org.unicode.cldr.test.CheckNew;
import org.unicode.cldr.test.CoverageLevel2;
import org.unicode.cldr.test.OutdatedPaths;
import org.unicode.cldr.tool.Option;
import org.unicode.cldr.tool.ShowLocaleCoverage;
import org.unicode.cldr.tool.Option.Options;
import org.unicode.cldr.util.CLDRFile.Status;
import org.unicode.cldr.util.CLDRFile.WinningChoice;
import org.unicode.cldr.util.PathHeader.PageId;
import org.unicode.cldr.util.PathHeader.SectionId;
import org.unicode.cldr.util.StandardCodes.LocaleCoverageType;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo.Count;
import org.unicode.cldr.util.VoteResolver.Organization;

import com.ibm.icu.dev.util.BagFormatter;
import com.ibm.icu.dev.util.CollectionUtilities;
import com.ibm.icu.dev.util.Relation;
import com.ibm.icu.dev.util.TransliteratorUtilities;
import com.ibm.icu.dev.util.XEquivalenceClass;
import com.ibm.icu.impl.Row;
import com.ibm.icu.impl.Row.R2;
import com.ibm.icu.impl.Utility;
import com.ibm.icu.lang.CharSequences;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.Transform;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.Output;
import com.ibm.icu.util.ULocale;

/**
 * Provides a HTML tables showing the important issues for vetters to review for
 * a given locale. See the main for an example. Most elements have CSS styles,
 * allowing for customization of the display.
 * 
 * @author markdavis
 */
public class VettingViewer<T> {

    private static final String CONNECT_PREFIX = "₍_";
    private static final String CONNECT_SUFFIX = "₎";

    private static final String TH_AND_STYLES = "<th class='tv-th' style='text-align:left'>";

    private static final String SPLIT_CHAR = "\uFFFE";

    private static final boolean SUPPRESS = true;

    private static final String TEST_PATH = "//ldml/localeDisplayNames/territories/territory[@type=\"SX\"]";
    private static final double NANOSECS = 1000000000.0;
    private static final boolean TESTING = CldrUtility.getProperty("TEST", false);
    private static final boolean SHOW_ALL = CldrUtility.getProperty("SHOW", true);

    public static final Pattern ALT_PROPOSED = Pattern.compile("\\[@alt=\"[^\"]*proposed");

    public static Set<CheckCLDR.CheckStatus.Subtype> OK_IF_VOTED = EnumSet.of(Subtype.sameAsEnglishOrCode,
            Subtype.sameAsEnglishOrCode);

    public enum Choice {
        /**
         * There is a console-check error
         */
        error('E', "Error", "The Survey Tool detected an error in the winning value."),
        /**
         * My choice is not the winning item
         */
        weLost(
                'L',
                "Losing",
                "The value that your organization chose (overall) is either not the winning value, or doesn’t have enough votes to be approved. "
                        + "This might be due to a dispute between members of your organization."),
                        /**
                         * There is a dispute.
                         */
                        notApproved('P', "Provisional", "There are not enough votes for this item to be approved (and used)."),
                        /**
                         * There is a dispute.
                         */
                        hasDispute('D', "Disputed", "Different organizations are choosing different values. "
                                + "Please review to approve or reach consensus."),
                                /**
                                 * There is a console-check warning
                                 */
                                warning('W', "Warning", "The Survey Tool detected a warning about the winning value."),
                                /**
                                 * The English value for the path changed AFTER the current value for
                                 * the locale.
                                 */
                                englishChanged('U', "Unsync’d",
                                        "The English value changed at some point in CLDR, but the corresponding value for your language didn’t."),
                                        /**
                                         * The value changed from the last version of CLDR
                                         */
                                        changedOldValue('N', "New", "The winning value was altered from the last-released CLDR value. (Informational)"),
                                        /**
                                         * Given the users' coverage, some items are missing.
                                         */
                                        missingCoverage(
                                                'M',
                                                "Missing",
                                                "Your current coverage level requires the item to be present. (During the vetting phase, this is informational: you can’t add new values.)"),
                                                // /**
                                                // * There is a console-check error
                                                // */
                                                // other('O', "Other", "Everything else."),
                                                ;

        public final char abbreviation;
        public final String buttonLabel;
        public final String description;

        Choice(char abbreviation, String buttonLabel, String description) {
            this.abbreviation = abbreviation;
            this.buttonLabel = TransliteratorUtilities.toHTML.transform(buttonLabel);
            this.description = TransliteratorUtilities.toHTML.transform(description);
        }

        public static <T extends Appendable> T appendDisplay(Set<Choice> choices, String htmlMessage, T target) {
            try {
                boolean first = true;
                for (Choice item : choices) {
                    if (first) {
                        first = false;
                    } else {
                        target.append(", ");
                    }
                    item.appendDisplay(htmlMessage, target);
                }
                return target;
            } catch (IOException e) {
                throw new IllegalArgumentException(e); // damn'd checked
                // exceptions
            }
        }

        private <T extends Appendable> void appendDisplay(String htmlMessage, T target) throws IOException {
            target.append("<span title='")
            .append(description);
            if (!htmlMessage.isEmpty()) {
                target.append(": ")
                .append(htmlMessage);
            }
            target.append("'>")
            .append(buttonLabel)
            .append("*</span>");
        }

        public static Choice fromString(String i) {
            try {
                return valueOf(i);
            } catch (NullPointerException e) {
                throw e;
            } catch (RuntimeException e) {
                if (i.isEmpty()) {
                    throw e;
                }
                int cp = i.codePointAt(0);
                for (Choice choice : Choice.values()) {
                    if (cp == choice.abbreviation) {
                        return choice;
                    }
                }
                throw e;
            }
        }

        public static Appendable appendRowStyles(Set<Choice> choices, Appendable target) {
            try {
                if (choices.contains(Choice.changedOldValue)) {
                    int x = 0; // debugging
                }
                target.append("hide");
                for (Choice item : choices) {
                    target.append(' ').append("vv").append(Character.toLowerCase(item.abbreviation));
                }
                return target;
            } catch (IOException e) {
                throw new IllegalArgumentException(e); // damn'd checked
                // exceptions
            }
        }
    }

    static private PathHeader.Factory pathTransform;
    static final Pattern breaks = Pattern.compile("\\|");
    static final OutdatedPaths outdatedPaths = new OutdatedPaths();

    private static final UnicodeSet NEEDS_PERCENT_ESCAPED = new UnicodeSet("[[\\u0000-\\u009F]-[a-zA-z0-9]]");
    private static final Transform<String, String> percentEscape = new Transform<String, String>() {
        @Override
        public String transform(String source) {
            StringBuilder buffer = new StringBuilder();
            buffer.setLength(0);
            for (int cp : CharSequences.codePoints(source)) {
                if (NEEDS_PERCENT_ESCAPED.contains(cp)) {
                    buffer.append('%').append(Utility.hex(cp, 2));
                } else {
                    buffer.appendCodePoint(cp);
                }
            }
            return buffer.toString();
        }
    };

    /**
     * See VoteResolver getStatusForOrganization to see how this is computed.
     */
    public enum VoteStatus {
        /**
         * The value for the path is either contributed or approved, and
         * the user's organization didn't vote. (see class def for null user)
         */
        ok_novotes,

        /**
         * The value for the path is either contributed or approved, and
         * the user's organization chose the winning value. (see class def for null user)
         */
        ok,

        /**
         * The user's organization chose the winning value for the path, but
         * that value is neither contributed nor approved. (see class def for null user)
         */
        provisionalOrWorse,

        /**
         * The user's organization's choice is not winning. There may be
         * insufficient votes to overcome a previously approved value, or other
         * organizations may be voting against it. (see class def for null user)
         */
        losing,

        /**
         * There is a dispute, meaning more than one item with votes, or the item with votes didn't win.
         */
        disputed
    }

    /**
     * @author markdavis
     * 
     * @param <T>
     */
    public static interface UsersChoice<T> {
        /**
         * Return the value that the user's organization (as a whole) voted for,
         * or null if none of the users in the organization voted for the path. <br>
         * NOTE: Would be easier if this were a method on CLDRFile.
         * NOTE: if user = null, then it must return the absolute winning value.
         * 
         * @param locale
         */
        public String getWinningValueForUsersOrganization(CLDRFile cldrFile, String path, T user);

        /**
         * 
         * Return the vote status
         * NOTE: if user = null, then it must disregard the user and never return losing. See VoteStatus.
         * 
         * @param locale
         */
        public VoteStatus getStatusForUsersOrganization(CLDRFile cldrFile, String path, T user);
    }

    public static interface ErrorChecker {
        enum Status {
            ok, error, warning
        }

        /**
         * Initialize an error checker with a cldrFile. MUST be called before
         * any getErrorStatus.
         */
        public Status initErrorStatus(CLDRFile cldrFile);

        /**
         * Return the detailed CheckStatus information.
         */
        public List<CheckStatus> getErrorCheckStatus(String path, String value);

        /**
         * Return the status, and append the error message to the status
         * message. If there are any errors, then the warnings are not included.
         */
        public Status getErrorStatus(String path, String value, StringBuilder statusMessage);

        /**
         * Return the status, and append the error message to the status
         * message, and get the subtypes. If there are any errors, then the warnings are not included.
         */
        public Status getErrorStatus(String path, String value, StringBuilder statusMessage,
                EnumSet<Subtype> outputSubtypes);
    }

    public static class NoErrorStatus implements ErrorChecker {
        @Override
        public Status initErrorStatus(CLDRFile cldrFile) {
            return Status.ok;
        }

        @Override
        public List<CheckStatus> getErrorCheckStatus(String path, String value) {
            return Collections.emptyList();
        }

        @Override
        public Status getErrorStatus(String path, String value, StringBuilder statusMessage) {
            return Status.ok;
        }

        @Override
        public Status getErrorStatus(String path, String value, StringBuilder statusMessage,
                EnumSet<Subtype> outputSubtypes) {
            return Status.ok;
        }

    }

    public static class DefaultErrorStatus implements ErrorChecker {

        private CheckCLDR checkCldr;
        private HashMap<String, String> options = new HashMap<String, String>();
        private ArrayList<CheckStatus> result = new ArrayList<CheckStatus>();
        private CLDRFile cldrFile;
        private Factory factory;

        public DefaultErrorStatus(Factory cldrFactory) {
            this.factory = cldrFactory;
        }

        @Override
        public Status initErrorStatus(CLDRFile cldrFile) {
            this.cldrFile = cldrFile;
            options = new HashMap<String, String>();
            result = new ArrayList<CheckStatus>();
            checkCldr = CheckCLDR.getCheckAll(factory, ".*");
            checkCldr.setCldrFileToCheck(cldrFile, options, result);
            return Status.ok;
        }

        @Override
        public List<CheckStatus> getErrorCheckStatus(String path, String value) {
            String fullPath = cldrFile.getFullXPath(path);
            ArrayList<CheckStatus> result2 = new ArrayList<CheckStatus>();
            checkCldr.check(path, fullPath, value, options, result2);
            return result2;
        }

        @Override
        public Status getErrorStatus(String path, String value, StringBuilder statusMessage) {
            return getErrorStatus(path, value, statusMessage, null);
        }

        @Override
        public Status getErrorStatus(String path, String value, StringBuilder statusMessage,
                EnumSet<Subtype> outputSubtypes) {
            Status result0 = Status.ok;
            StringBuilder errorMessage = new StringBuilder();
            String fullPath = cldrFile.getFullXPath(path);
            checkCldr.check(path, fullPath, value, options, result);
            for (CheckStatus checkStatus : result) {
                final CheckCLDR cause = checkStatus.getCause();
                if (cause instanceof CheckCoverage || cause instanceof CheckNew) {
                    continue;
                }
                CheckStatus.Type statusType = checkStatus.getType();
                if (statusType.equals(CheckStatus.errorType)) {
                    // throw away any accumulated warning messages
                    if (result0 == Status.warning) {
                        errorMessage.setLength(0);
                        if (outputSubtypes != null) {
                            outputSubtypes.clear();
                        }
                    }
                    result0 = Status.error;
                    if (outputSubtypes != null) {
                        outputSubtypes.add(checkStatus.getSubtype());
                    }
                    appendToMessage(checkStatus.getMessage(), errorMessage);
                } else if (result0 != Status.error && statusType.equals(CheckStatus.warningType)) {
                    result0 = Status.warning;
                    // accumulate all the warning messages
                    if (outputSubtypes != null) {
                        outputSubtypes.add(checkStatus.getSubtype());
                    }
                    appendToMessage(checkStatus.getMessage(), errorMessage);
                }
            }
            if (result0 != Status.ok) {
                appendToMessage(errorMessage, statusMessage);
            }
            return result0;
        }
    }

    private final Factory cldrFactory;
    private final Factory cldrFactoryOld;
    private final CLDRFile englishFile;
    private final CLDRFile oldEnglishFile;
    private final UsersChoice<T> userVoteStatus;
    private final SupplementalDataInfo supplementalDataInfo;
    private final String lastVersionTitle;
    private final String currentWinningTitle;
    private final PathDescription pathDescription;
    private ErrorChecker errorChecker; // new

    private final Set<String> defaultContentLocales;

    // NoErrorStatus();
    // //
    // for
    // testing

    /**
     * Create the Vetting Viewer.
     * 
     * @param supplementalDataInfo
     * @param cldrFactory
     * @param cldrFactoryOld
     * @param lastVersionTitle
     *            The title of the last released version of CLDR.
     * @param currentWinningTitle
     *            The title of the next version of CLDR to be released.
     */
    public VettingViewer(SupplementalDataInfo supplementalDataInfo, Factory cldrFactory, Factory cldrFactoryOld,
            UsersChoice<T> userVoteStatus,
            String lastVersionTitle, String currentWinningTitle) {
        super();
        this.cldrFactory = cldrFactory;
        this.cldrFactoryOld = cldrFactoryOld;
        englishFile = cldrFactory.make("en", true);
        if (pathTransform == null) {
            pathTransform = PathHeader.getFactory(englishFile);
        }
        oldEnglishFile = cldrFactoryOld.make("en", true);
        this.userVoteStatus = userVoteStatus;
        this.supplementalDataInfo = supplementalDataInfo;
        this.defaultContentLocales = supplementalDataInfo.getDefaultContentLocales();

        this.lastVersionTitle = lastVersionTitle;
        this.currentWinningTitle = currentWinningTitle;
        Map<String, List<Set<String>>> starredPaths = new HashMap();
        Map<String, String> extras = new HashMap();
        reasonsToPaths = new Relation(new HashMap<String, Set<String>>(), HashSet.class);
        this.pathDescription = new PathDescription(supplementalDataInfo, englishFile, extras, starredPaths,
                PathDescription.ErrorHandling.CONTINUE);
        errorChecker = new DefaultErrorStatus(cldrFactory);
    }


    class WritingInfo implements Comparable<WritingInfo> {
        final PathHeader codeOutput;
        final Set<Choice> problems;
        final String htmlMessage;

        public WritingInfo(PathHeader pretty, EnumSet<Choice> problems, CharSequence htmlMessage) {
            super();
            this.codeOutput = pretty;
            this.problems = Collections.unmodifiableSet(problems.clone());
            this.htmlMessage = htmlMessage.toString();
        }

        @Override
        public int compareTo(WritingInfo other) {
            return codeOutput.compareTo(other.codeOutput);
        }

        public String getUrl(String locale) {
            return codeOutput.getUrl(baseUrl, locale);
//            return baseUrl + "?_="
//            + locale
//            + "&strid="
//            + StringId.getHexId(codeOutput.getOriginalPath());
            // String menu = PathUtilities.xpathToMenu(path);
            // String url = baseUrl + "?_=" + locale + "&amp;=" + menu;
            // return url;
        }
    }

    // public void generateHtmlErrorTablesOld(Appendable output, EnumSet<Choice> choices, String localeID, T user, Level
    // usersLevel) {
    // generateHtmlErrorTablesOld(output, choices, localeID, user, usersLevel, false);
    // }

    // private void generateHtmlErrorTablesOld(Appendable output, EnumSet<Choice> choices, String localeID, T user,
    // Level usersLevel, boolean showAll) {
    //
    // // first gather the relevant paths
    // // each one will be marked with the choice that it triggered.
    //
    // CLDRFile sourceFile = cldrFactory.make(localeID, true);
    // Matcher altProposed = Pattern.compile("\\[@alt=\"[^\"]*proposed").matcher("");
    // EnumSet<Choice> problems = EnumSet.noneOf(Choice.class);
    //
    // // Initialize
    // CoverageLevel2 coverage = CoverageLevel2.getInstance(supplementalDataInfo, localeID);
    // CLDRFile lastSourceFile = null;
    // try {
    // lastSourceFile = cldrFactoryOld.make(localeID, true);
    // } catch (Exception e) {
    // }
    //
    // // set the following only where needed.
    // Status status = null;
    //
    // Map<String, String> options = null;
    // List<CheckStatus> result = null;
    //
    // for (Choice choice : choices) {
    // switch (choice) {
    // case changedOldValue:
    // break;
    // case missingCoverage:
    // status = new Status();
    // break;
    // case englishChanged:
    // break;
    // case error:
    // case warning:
    // errorChecker.initErrorStatus(sourceFile);
    // break;
    // case weLost:
    // case hasDispute:
    // //case other:
    // break;
    // default:
    // System.out.println(choice + " not implemented yet");
    // }
    // }
    //
    // // now look through the paths
    //
    // Relation<R2<SectionId, PageId>, WritingInfo> sorted = Relation.of(new TreeMap<R2<SectionId, PageId>,
    // Set<WritingInfo>>(), TreeSet.class);
    //
    // Counter<Choice> problemCounter = new Counter<Choice>();
    // StringBuilder htmlMessage = new StringBuilder();
    // StringBuilder statusMessage = new StringBuilder();
    //
    // for (String path : sourceFile) {
    // progressCallback.nudge(); // Let the user know we're moving along.
    //
    // // note that the value might be missing!
    //
    // // make sure we only look at the real values
    // if (altProposed.reset(path).find()) {
    // continue;
    // }
    //
    // if (path.contains("/exemplarCharacters") || path.contains("/references")) {
    // continue;
    // }
    //
    // Level level = coverage.getLevel(path);
    //
    // // skip anything above the requested level
    // if (level.compareTo(usersLevel) > 0) {
    // continue;
    // }
    //
    // String value = sourceFile.getWinningValue(path);
    //
    // problems.clear();
    // htmlMessage.setLength(0);
    // boolean haveError = false;
    // VoteStatus voteStatus = null;
    //
    // for (Choice choice : choices) {
    // switch (choice) {
    // case changedOldValue:
    // String oldValue = lastSourceFile == null ? null : lastSourceFile.getWinningValue(path);
    // if (oldValue != null && !oldValue.equals(value)) {
    // problems.add(choice);
    // problemCounter.increment(choice);
    // }
    // break;
    // case missingCoverage:
    // if (showAll && !localeID.equals("root")) {
    // if (isMissing(sourceFile, path, status)) {
    // problems.add(choice);
    // problemCounter.increment(choice);
    // }
    // }
    // break;
    // case englishChanged:
    // if (outdatedPaths.isOutdated(localeID, path)
    // // ||
    // // !CharSequences.equals(englishFile.getWinningValue(path),
    // // oldEnglishFile.getWinningValue(path))
    // ) {
    // // the outdated paths compares the base value, before
    // // data submission,
    // // so see if the value changed.
    // String lastValue = lastSourceFile == null ? null : lastSourceFile.getWinningValue(path);
    // if (CharSequences.equals(value, lastValue)) {
    // problems.add(choice);
    // problemCounter.increment(choice);
    // }
    // }
    // break;
    // case error:
    // case warning:
    // if (haveError) {
    // break;
    // }
    // statusMessage.setLength(0);
    // ErrorChecker.Status errorStatus = errorChecker.getErrorStatus(path, value, statusMessage);
    // if ((choice == Choice.error && errorStatus == ErrorChecker.Status.error)
    // || (choice == Choice.warning && errorStatus == ErrorChecker.Status.warning)) {
    // if (choice == Choice.warning) {
    // // for now, suppress cases where the English changed
    // if (outdatedPaths.isOutdated(localeID, path)) {
    // break;
    // }
    // }
    // problems.add(choice);
    // appendToMessage(statusMessage, htmlMessage);
    // problemCounter.increment(choice);
    // haveError = true;
    // break;
    // }
    // break;
    // case weLost:
    // if (voteStatus == null) {
    // voteStatus = userVoteStatus.getStatusForUsersOrganization(sourceFile, path, user);
    // }
    // switch (voteStatus) {
    // case provisionalOrWorse:
    // case losing:
    // if (choice == Choice.weLost) {
    // problems.add(choice);
    // problemCounter.increment(choice);
    // String usersValue = userVoteStatus.getWinningValueForUsersOrganization(sourceFile, path, user);
    // // appendToMessage(usersValue, testMessage);
    // }
    // break;
    // }
    // break;
    // case hasDispute:
    // if (voteStatus == null) {
    // voteStatus = userVoteStatus.getStatusForUsersOrganization(sourceFile, path, user);
    // }
    // if (voteStatus == VoteStatus.disputed) {
    // problems.add(choice);
    // problemCounter.increment(choice);
    // String usersValue = userVoteStatus.getWinningValueForUsersOrganization(sourceFile, path, user);
    // if (usersValue != null) {
    // // appendToMessage(usersValue, testMessage);
    // }
    // }
    // break;
    // }
    // }
    // if (!problems.isEmpty()) { // showAll ||
    // // if (showAll && problems.isEmpty()) {
    // // problems.add(Choice.other);
    // // problemCounter.increment(Choice.other);
    // // }
    // reasonsToPaths.clear();
    // // appendToMessage("level:" + level.toString(), testMessage);
    // // final String description =
    // // pathDescription.getDescription(path, value, level, null);
    // // if (!reasonsToPaths.isEmpty()) {
    // // appendToMessage(level + " " +
    // // TransliteratorUtilities.toHTML.transform(reasonsToPaths.toString()),
    // // testMessage);
    // // }
    // // if (description != null && !description.equals("SKIP")) {
    // // appendToMessage(TransliteratorUtilities.toHTML.transform(description),
    // // testMessage);
    // // }
    // //final String prettyPath = pathTransform.getPrettyPath(path);
    // // String[] pathParts = breaks.split(prettyPath);
    // // String section = pathParts.length == 3 ? pathParts[0] :
    // // "Unknown";
    // // String subsection = pathParts.length == 3 ? pathParts[1] :
    // // "Unknown";
    // // String code = pathParts.length == 3 ? pathParts[2] : pretty;
    //
    // PathHeader pretty = pathTransform.fromPath(path);
    // //String[] pathParts = breaks.split(pretty);
    // // String sectionOutput = pathParts.length == 3 ? pathParts[0] : "Unknown";
    // // String subsectionOutput = pathParts.length == 3 ? pathParts[1] : "Unknown";
    // // String codeOutput = pathParts.length == 3 ? pathParts[2] : pretty;
    //
    // R2<SectionId, PageId> group = Row.of(pretty.getSectionId(), pretty.getPageId());
    //
    // sorted.put(group, new WritingInfo(pretty, problems, htmlMessage));
    // }
    // }
    //
    // // now write the results out
    // writeTables(output, sourceFile, lastSourceFile, sorted, problemCounter, choices, localeID, showAll);
    // }

    /**
     * Show a table of values, filtering according to the choices here and in
     * the constructor.
     * 
     * @param output
     * @param choices
     *            See the class description for more information.
     * @param localeId
     * @param user
     * @param usersLevel
     * @param nonVettingPhase
     */
    public void generateHtmlErrorTables(Appendable output, EnumSet<Choice> choices, String localeID, T user,
            Level usersLevel, boolean nonVettingPhase) {

        // Gather the relevant paths
        // each one will be marked with the choice that it triggered.
        Relation<R2<SectionId, PageId>, WritingInfo> sorted = Relation.of(
                new TreeMap<R2<SectionId, PageId>, Set<WritingInfo>>(), TreeSet.class);
        Counter<Choice> problemCounter = new Counter<Choice>();

        CLDRFile sourceFile = cldrFactory.make(localeID, true);

        // Initialize
        CLDRFile lastSourceFile = null;
        try {
            lastSourceFile = cldrFactoryOld.make(localeID, true);
        } catch (Exception e) {
        }

        getFileInfo(sourceFile, lastSourceFile, sorted, problemCounter, choices, localeID, nonVettingPhase, user,
                usersLevel);

        // now write the results out
        writeTables(output, sourceFile, lastSourceFile, sorted, problemCounter, choices, localeID, nonVettingPhase);
    }

    private void getFileInfo(CLDRFile sourceFile, CLDRFile lastSourceFile, Relation<R2<SectionId, PageId>,
            WritingInfo> sorted, Counter<Choice> problemCounter,
            EnumSet<Choice> choices, String localeID, boolean nonVettingPhase,
            T user, Level usersLevel) {

        Status status = new Status();
        errorChecker.initErrorStatus(sourceFile);
        Matcher altProposed = ALT_PROPOSED.matcher("");
        EnumSet<Choice> problems = EnumSet.noneOf(Choice.class);

        // now look through the paths

        StringBuilder htmlMessage = new StringBuilder();
        StringBuilder statusMessage = new StringBuilder();
        EnumSet<Subtype> subtypes = EnumSet.noneOf(Subtype.class);
        Set<String> seenSoFar = new HashSet<String>();

        boolean latin = VettingViewer.isLatinScriptLocale(sourceFile);

        for (String path : sourceFile.fullIterable()) {
            if (seenSoFar.contains(path)) {
                continue;
            }
            seenSoFar.add(path);
            progressCallback.nudge(); // Let the user know we're moving along.

            PathHeader pretty = pathTransform.fromPath(path);
            if (pretty.getSurveyToolStatus() == PathHeader.SurveyToolStatus.HIDE) {
                continue;
            }

            // note that the value might be missing!

            // make sure we only look at the real values
            if (altProposed.reset(path).find()) {
                continue;
            }

            if (path.contains("/references")) {
                continue;
            }

            Level level = supplementalDataInfo.getCoverageLevel(path,sourceFile.getLocaleID());

            // skip anything above the requested level
            if (level.compareTo(usersLevel) > 0) {
                continue;
            }

            String value = sourceFile.getWinningValue(path);

            problems.clear();
            htmlMessage.setLength(0);
            String oldValue = lastSourceFile == null ? null : lastSourceFile.getWinningValue(path);

            if (choices.contains(Choice.changedOldValue)) {
                if (oldValue != null && !oldValue.equals(value)) {
                    problems.add(Choice.changedOldValue);
                    problemCounter.increment(Choice.changedOldValue);
                }
            }
            VoteStatus voteStatus = userVoteStatus.getStatusForUsersOrganization(sourceFile, path, user);

            MissingStatus missingStatus = getMissingStatus(sourceFile, path, status, latin);
            if (choices.contains(Choice.missingCoverage) && missingStatus == MissingStatus.ABSENT) {
                problems.add(Choice.missingCoverage);
                problemCounter.increment(Choice.missingCoverage);
            }
            boolean itemsOkIfVoted = SUPPRESS
                    && voteStatus == VoteStatus.ok;

            if (!itemsOkIfVoted
                    && outdatedPaths.isOutdated(localeID, path)) {
                // the outdated paths compares the base value, before
                // data submission,
                // so see if the value changed.
                // String lastValue = lastSourceFile == null ? null : lastSourceFile.getWinningValue(path);
                if (CharSequences.equals(value, oldValue) && choices.contains(Choice.englishChanged)) {
                    // check to see if we voted
                    problems.add(Choice.englishChanged);
                    problemCounter.increment(Choice.englishChanged);
                }
            }

            statusMessage.setLength(0);
            subtypes.clear();
            ErrorChecker.Status errorStatus = errorChecker.getErrorStatus(path, value, statusMessage, subtypes);
            Choice choice = errorStatus == ErrorChecker.Status.error ? Choice.error
                    : errorStatus == ErrorChecker.Status.warning ? Choice.warning
                            : null;
            if (choice == Choice.error && choices.contains(Choice.error)
                    && (!itemsOkIfVoted
                            || !OK_IF_VOTED.containsAll(subtypes))) {
                problems.add(choice);
                appendToMessage(statusMessage, htmlMessage);
                problemCounter.increment(choice);
            } else if (choice == Choice.warning && choices.contains(Choice.warning)
                    && (!itemsOkIfVoted
                            || !OK_IF_VOTED.containsAll(subtypes))) {
                problems.add(choice);
                appendToMessage(statusMessage, htmlMessage);
                problemCounter.increment(choice);
            }

            switch (voteStatus) {
            case losing:
                if (choices.contains(Choice.weLost)) {
                    problems.add(Choice.weLost);
                    problemCounter.increment(Choice.weLost);
                }
                 String usersValue = userVoteStatus.getWinningValueForUsersOrganization(sourceFile, path, user);
                 if (usersValue != null) {
                     usersValue = "Losing value: <" + TransliteratorUtilities.toHTML.transform(usersValue) + ">";
                     appendToMessage(usersValue, htmlMessage);
                 }
                break;
            case disputed:
                if (choices.contains(Choice.hasDispute)) {
                    problems.add(Choice.hasDispute);
                    problemCounter.increment(Choice.hasDispute);
                }
                break;
            case provisionalOrWorse:
                if (missingStatus == MissingStatus.PRESENT && choices.contains(Choice.notApproved)) {
                    problems.add(Choice.notApproved);
                    problemCounter.increment(Choice.notApproved);
                }
                break;
            }

            if (!problems.isEmpty()) {
                // showAll ||
                // if (showAll && problems.isEmpty()) {
                // problems.add(Choice.other);
                // problemCounter.increment(Choice.other);
                // }
                if (sorted != null) {
                    reasonsToPaths.clear();
                    // final String prettyPath = pathTransform.getPrettyPath(path);

                    // String[] pathParts = breaks.split(pretty);
                    // String sectionOutput = pathParts.length == 3 ? pathParts[0] : "Unknown";
                    // String subsectionOutput = pathParts.length == 3 ? pathParts[1] : "Unknown";
                    // String codeOutput = pathParts.length == 3 ? pathParts[2] : pretty;

                    R2<SectionId, PageId> group = Row.of(pretty.getSectionId(), pretty.getPageId());

                    sorted.put(group, new WritingInfo(pretty, problems, htmlMessage));
                }
            }

        }
    }

    public static final class LocalesWithExplicitLevel implements Predicate<String> {
        private final String org;
        private final Level desiredLevel;        
        public LocalesWithExplicitLevel(Organization org, Level level) {  
            this.org = org.toString();
            this.desiredLevel = level;
        }
        @Override
        public boolean is(String localeId) {
            Output<LocaleCoverageType> output = new Output<LocaleCoverageType>();
            // For admin - return true if SOME organization has explicit coverage for the locale
            // TODO: Make admin pick up any locale that has a vote
            if (org.equals(Organization.surveytool.toString())) {
                for ( Organization checkorg : Organization.values()) {
                    StandardCodes.make().getLocaleCoverageLevel(checkorg.toString(), localeId, output);
                    if (output.value == StandardCodes.LocaleCoverageType.explicit) {
                        return true;
                    }
                }
                return false;
            } else {
                Level level = StandardCodes.make().getLocaleCoverageLevel(org, localeId, output);
                return desiredLevel == level && output.value == StandardCodes.LocaleCoverageType.explicit;
            }
        }
    };

    public void generateSummaryHtmlErrorTables(Appendable output, EnumSet<Choice> choices,
            Predicate<String> includeLocale, T organization) {
        try {

            output
            .append("<p>The following summarizes the Priority Items across locales, " +
                    "using the default coverage levels for your organization for each locale. " +
                    "Before using, please read the instructions at " +
                    "<a target='CLDR_ST_DOCS' href='http://cldr.unicode.org/translation/vetting-summary'>Priority " +
                    "Items Summary</a>.</p>\n");

            StringBuilder headerRow = new StringBuilder();
            headerRow
            .append("<tr class='tvs-tr'>")
            .append(TH_AND_STYLES)
            .append("Locale</th>")
            .append(TH_AND_STYLES)
            .append("Codes</th>");
            for (Choice choice : choices) {
                headerRow.append("<th class='tv-th'>");
                choice.appendDisplay("", headerRow);
                headerRow.append("</th>");
            }
            headerRow.append("</tr>\n");
            String header = headerRow.toString();

            if (organization.equals(Organization.surveytool)) {
                writeSummaryTable(output, header, Level.COMPREHENSIVE, choices, organization);
            } else {
                for (Level level : Level.values()) {
                    writeSummaryTable(output, header, level, choices, organization);
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e); // dang'ed checked exceptions
        }

    }

    private void writeSummaryTable(Appendable output, String header, Level desiredLevel, 
            EnumSet<Choice> choices, T organization) throws IOException {

        Counter<Choice> totalProblemCounter = new Counter<Choice>();
        Map<String, String> sortedNames = new TreeMap(Collator.getInstance());

        // Gather the relevant paths
        // Each one will be marked with the choice that it triggered.

        // TODO Fix HACK
        // We are going to ignore the predicate for now, just using the locales that have explicit coverage.
        // in that locale, or allow all locales for admin@
        LocalesWithExplicitLevel includeLocale = new LocalesWithExplicitLevel((Organization) organization, desiredLevel);

        for (String localeID : cldrFactory.getAvailable()) {
            if (defaultContentLocales.contains(localeID)
                    || localeID.equals("en")
                    || !includeLocale.is(localeID)) {
                continue;
            }

            sortedNames.put(getName(localeID), localeID);
        }
        if (sortedNames.isEmpty()) {
            return;
        }

        EnumSet<Choice> thingsThatRequireOldFile = EnumSet.of(Choice.englishChanged,Choice.missingCoverage,Choice.changedOldValue);
        EnumSet<Choice> ourChoicesThatRequireOldFile = choices.clone();
        ourChoicesThatRequireOldFile.retainAll(thingsThatRequireOldFile);
        output.append("<h2>Level: ").append(desiredLevel.toString()).append("</h2>");
        output.append("<table class='tvs-table'>\n");
        Counter<Choice> problemCounter = new Counter<Choice>();
        char lastChar = ' ';
        for (Entry<String, String> entry : sortedNames.entrySet()) {
            String name = entry.getKey();
            String localeID = entry.getValue();
            // Initialize

            CLDRFile sourceFile = cldrFactory.make(localeID, true);

            CLDRFile lastSourceFile = null;
            if (!ourChoicesThatRequireOldFile.isEmpty()) {
                try {
                    lastSourceFile = cldrFactoryOld.make(localeID, true);
                } catch (Exception e) {
                }
            }
            problemCounter.clear();
            Level level = Level.MODERN;
            if (organization != null) {
                level = StandardCodes.make().getLocaleCoverageLevel(organization.toString(), localeID);
            }
            getFileInfo(sourceFile, lastSourceFile, null, problemCounter, choices, localeID, true, organization,
                    level);

            char nextChar = name.charAt(0);
            if (lastChar != nextChar) {
                output.append(header);
                lastChar = nextChar;
            }

            writeSummaryRow(output, choices, problemCounter, name, localeID);
            totalProblemCounter.addAll(problemCounter);

            if (output instanceof Writer) {
                ((Writer) output).flush();
            }
        }
        output.append(header);
        writeSummaryRow(output, choices, totalProblemCounter, "Total", null);
        output.append("</table>");
    }

    private void writeSummaryRow(Appendable output, EnumSet<Choice> choices, Counter<Choice> problemCounter, 
            String name, String localeID) throws IOException {
        output
        .append("<tr>")
        .append(TH_AND_STYLES);
        if (localeID == null) {
            output
            .append("<i>")
            .append(name)
            .append("</i>")
            .append("</th>")
            .append(TH_AND_STYLES);
        } else {
            String[] names = name.split(SPLIT_CHAR);
            output
            .append("<a target='CLDR-ST-LOCALE' href='" + baseUrl + "?_=")
            .append(localeID)
            .append("&x=r_vetting&p_covlev=default'>")
            .append(TransliteratorUtilities.toHTML.transform(names[0]))
            .append("</a>")
            .append("</th>")
            .append(TH_AND_STYLES)
            .append("<code>")
            .append(names[1])
            .append("</code>");
        }
        output.append("</th>\n");
        for (Choice choice : choices) {
            long count = problemCounter.get(choice);
            output.append("<td class='tvs-count'>");
            // if (choice == Choice.weLost) {
            // output.append("<i>n/a</i>");
            // } else {
            output.append(nf.format(count));
            // }
            output.append("</td>\n");
        }
        output.append("</tr>\n");
    }

    LanguageTagParser ltp = new LanguageTagParser();

    private String getName(String localeID) {
        Set<String> contents = supplementalDataInfo.getEquivalentsForLocale(localeID);
        // put in special character that can be split on later
        String name = englishFile.getName(localeID, true, CLDRFile.SHORT_ALTS) + SPLIT_CHAR + gatherCodes(contents); 
        return name;
    }

    /**
     * Collapse the names
     {en_Cyrl, en_Cyrl_US} => en_Cyrl(_US)
     {en_GB, en_Latn_GB} => en(_Latn)_GB
     {en, en_US, en_Latn, en_Latn_US} => en(_Latn)(_US)
     {az_IR, az_Arab, az_Arab_IR} => az_IR, az_Arab(_IR)
     */
    public static String gatherCodes(Set<String> contents) {
        Set<Set<String>> source = new LinkedHashSet<Set<String>>();
        for (String s : contents) {
            source.add(new LinkedHashSet<String>(Arrays.asList(s.split("_"))));
        }
        Set<Set<String>> oldSource = new LinkedHashSet<Set<String>>();

        do {
            // exchange source/target
            oldSource.clear();
            oldSource.addAll(source);
            source.clear();
            Set<String> last = null;
            for (Set<String> ss : oldSource) {
                if (last == null) {
                    last = ss;
                } else {
                    if (ss.containsAll(last)) {
                        last = combine(last,ss);
                    } else {
                        source.add(last);
                        last = ss;
                    }
                }
            }
            source.add(last);
        } while (oldSource.size() != source.size());
        
        StringBuilder b = new StringBuilder();
        for (Set<String> stringSet : source) {
            if (b.length() != 0) {
                b.append(", ");
            }
            String sep = "";
            for (String string : stringSet) {
                if (string.startsWith(CONNECT_PREFIX)) {
                    b.append(string + CONNECT_SUFFIX);
                } else {
                    b.append(sep + string);
                }
                sep = "_";
            }
        }
        return b.toString();
    }

    private static Set<String> combine(Set<String> last, Set<String> ss) {
        LinkedHashSet<String> result = new LinkedHashSet<String>();
        for (String s : ss) {
            if (last.contains(s)) {
                result.add(s);
            } else {
                result.add(CONNECT_PREFIX + s);
            }
        }
        return result;
    }

    public enum MissingOK {ok, latin, alias, compact}
    public static Transform<String, MissingOK> MISSING_STATUS_TRANSFORM = new Transform<String,MissingOK>() {
        public MissingOK transform(String source) {
            return MissingOK.valueOf(source);
        }
    };
    
    static final RegexLookup<MissingOK> missingOk = new RegexLookup<MissingOK>()
            .setPatternTransform(RegexLookup.RegexFinderTransformPath)
            .setValueTransform(MISSING_STATUS_TRANSFORM)
                    .loadFromFile(
                            VettingViewer.class,
                            "data/paths/missingOk.txt");

    private static boolean isMissingOk(CLDRFile sourceFile, String path, boolean latin, boolean aliased) {
        Output<String[]> arguments = new Output();
        MissingOK value = missingOk.get(path,null,arguments);
        if (value == null) {
            return false;
        }
        switch(value) {
        case ok: return true;
        case latin: return latin;
        case alias: return aliased;
        case compact: 
            // special processing for compact numbers
            if (path.contains("[@count=\"other\"]")) {
                return false; // the 'other' class always counts as missing
            }
            String otherPath = "//ldml/numbers/decimalFormats[@numberSystem=\"" + arguments.value[1]
            		+ "\"]/decimalFormatLength[@type=\"" + arguments.value[2]
                    + "\"]/decimalFormat[@type=\"standard\"]/pattern[@type=\"" + arguments.value[3]
                    + "\"][@count=\"other\"]";
            String otherValue = sourceFile.getWinningValue(otherPath);
            if (otherValue == null) {
                return false; // something's wrong, bail
            }
            int digits = countZeros(otherValue);
            if (digits > 4) { // we can only handle to 4 digits
                return false;
            }
            // if there are no possible Count values for this many digits, then it is ok to be missing.
            Count c = Count.valueOf(arguments.value[4]);
            SupplementalDataInfo supplementalDataInfo2 = SupplementalDataInfo.getInstance(sourceFile.getSupplementalDirectory());
            PluralInfo plurals = supplementalDataInfo2.getPlurals(sourceFile.getLocaleID());
            UnicodeSet uset = plurals.getSamples9999(c, digits);
            return uset.isEmpty(); // ok if no samples
            // TODO: handle fractions
        default: throw new IllegalArgumentException();
        }
    }
    
    private static int countZeros(String otherValue) {
        int result = 0;
        for (int i = 0; i < otherValue.length(); ++i) {
            if (otherValue.charAt(i) == '0') {
                ++result;
            }
        }
        return result;
    }

    public enum MissingStatus {
        PRESENT, ALIASED, MISSING_OK, ROOT_OK, ABSENT
    }

    public static MissingStatus getMissingStatus(CLDRFile sourceFile, String path, Status status, boolean latin) {
        if (sourceFile == null) {
            return MissingStatus.ABSENT;
        }
        if ("root".equals(sourceFile.getLocaleID()) || path.startsWith("//ldml/layout/orientation/")) {
            return MissingStatus.MISSING_OK;
        }
        if (path.equals(TEST_PATH)) {
            int debug = 1;
        }
        MissingStatus result;

        String value = sourceFile.getStringValue(path);
        boolean isAliased = path.equals(status.pathWhereFound);

        if (value == null) {
            result = isMissingOk(sourceFile, path, latin, isAliased) ? MissingStatus.MISSING_OK : MissingStatus.ABSENT;
        } else {
            String localeFound = sourceFile.getSourceLocaleID(path, status);

            // only count it as missing IF the (localeFound is root or codeFallback)
            // AND the aliasing didn't change the path
            if (localeFound.equals("root")
                    || localeFound.equals(XMLSource.CODE_FALLBACK_ID)
                    // || voteStatus == VoteStatus.provisionalOrWorse
                    ) {
                result = isMissingOk(sourceFile, path, latin, isAliased) 
                        || sourceFile.getLocaleID().equals("en") ? MissingStatus.ROOT_OK : MissingStatus.ABSENT;
            } else if (isAliased) {
                result = MissingStatus.PRESENT;
                // } else if (path.contains("decimalFormatLength[@type=\"long\"]") &&
                // path.contains("pattern[@type=\"1")) { // aliased
                // // special case compact numbers
                // //
                // ldml/numbers/decimalFormats[@numberSystem="latn"]/decimalFormatLength[@type="long"]/decimalFormat[@type="standard"]/pattern[@type="10000000"]
                // result = MissingStatus.ABSENT;
            } else {
                result = MissingStatus.ALIASED;
            }
        }
        return result;
    }

    public static final UnicodeSet LATIN = new UnicodeSet("[:sc=Latn:]").freeze();

    public static boolean isLatinScriptLocale(CLDRFile sourceFile) {
        UnicodeSet main = sourceFile.getExemplarSet("", WinningChoice.WINNING);
        return LATIN.containsSome(main);
    }

    private static StringBuilder appendToMessage(CharSequence usersValue, StringBuilder testMessage) {
        if (usersValue.length() == 0) {
            return testMessage;
        }
        if (testMessage.length() != 0) {
            testMessage.append("<br>");
        }
        return testMessage.append(usersValue);
    }

    static final NumberFormat nf = NumberFormat.getIntegerInstance(ULocale.ENGLISH);
    private Relation<String, String> reasonsToPaths;
    private String baseUrl = null;
    static {
        nf.setGroupingUsed(true);
    }

    /**
     * Set the base URL, equivalent to 'http://unicode.org/cldr/apps/survey' for
     * generated URLs.
     * 
     * @param url
     * @author srl
     */
    public void setBaseUrl(String url) {
        baseUrl = url;
    }

    /**
     * Class that allows the relaying of progress information
     * 
     * @author srl
     * 
     */
    public static class ProgressCallback {
        /**
         * Note any progress. This will be called before any output is printed.
         * It will be called approximately once per xpath.
         */
        public void nudge() {
        }

        /**
         * Called when all operations are complete.
         */
        public void done() {
        }
    }

    private ProgressCallback progressCallback = new ProgressCallback(); // null

    // instance
    // by
    // default

    /**
     * Select a new callback. Must be set before running.
     * 
     * @return
     * 
     */
    public VettingViewer<T> setProgressCallback(ProgressCallback newCallback) {
        progressCallback = newCallback;
        return this;
    }

    public ErrorChecker getErrorChecker() {
        return errorChecker;
    }

    /**
     * Select a new error checker. Must be set before running.
     * 
     * @return
     * 
     */
    public VettingViewer<T> setErrorChecker(ErrorChecker errorChecker) {
        this.errorChecker = errorChecker;
        return this;
    }

    /**
     * Provide the styles for inclusion into the ST &lt;head&gt; element.
     * 
     * @return
     */
    public static String getHeaderStyles() {
        return "<style type='text/css'>\n"
                + ".hide {display:none}\n"
                + ".vve {}\n"
                + ".vvn {}\n"
                + ".vvp {}\n"
                + ".vvl {}\n"
                + ".vvm {}\n"
                + ".vvu {}\n"
                + ".vvw {}\n"
                + ".vvd {}\n"
                + ".vvo {}\n"
                + "</style>";
    }

    private void writeTables(Appendable output, CLDRFile sourceFile, CLDRFile lastSourceFile,
            Relation<R2<SectionId, PageId>, WritingInfo> sorted,
            Counter<Choice> problemCounter,
            EnumSet<Choice> choices,
            String localeID,
            boolean nonVettingPhase) {
        try {
            boolean latin = VettingViewer.isLatinScriptLocale(sourceFile);

            Status status = new Status();

            output.append("<h2>Summary</h2>\n")
            .append("<p><i>It is important that you read " +
                    "<a target='CLDR-ST-DOCS' href='http://cldr.unicode.org/translation/vetting-view'>" +
                    "Priority Items</a> before starting!</i></p>")
                    .append("<form name='checkboxes' action='#'>\n")
                    .append("<table class='tvs-table'>\n")
                    .append("<tr class='tvs-tr'>" +
                            "<th class='tv-th'>Count</th>" +
                            "<th class='tv-th'>Issue</th>" +
                            "<th class='tv-th'>Description</th>" +
                            "</tr>\n");

            // find the choice to check
            // OLD if !vetting and missing != 0, use missing. Otherwise pick first.
            Choice checkedItem = null;
            // if (nonVettingPhase && problemCounter.get(Choice.missingCoverage) != 0) {
            // checkedItem = Choice.missingCoverage;
            // }

            for (Choice choice : choices) {
                long count = problemCounter.get(choice);
                output.append("<tr><td class='tvs-count'>")
                .append(nf.format(count))
                .append("</td>\n\t<td nowrap class='tvs-abb'>")
                .append("<input type='checkbox' name='")
                .append(Character.toLowerCase(choice.abbreviation))
                .append("' onclick='setStyles()'");
                if (checkedItem == choice || checkedItem == null && count != 0) {
                    output.append(" checked");
                    checkedItem = choice;
                }
                output.append(">");
                choice.appendDisplay("", output);
                output.append("</td>\n\t<td class='tvs-desc'>")
                .append(choice.description)
                .append("</td></tr>\n");
            }
            output.append("</table>\n</form>\n"
                    + "<script type='text/javascript'>\n" +
                    "<!-- \n" +
                    "setStyles()\n" +
                    "-->\n"
                    + "</script>");

            // gather information on choices on each page

            Relation<Row.R3<SectionId, PageId, String>, Choice> choicesForHeader = Relation.of(
                    new HashMap<Row.R3<SectionId, PageId, String>, Set<Choice>>(), HashSet.class);

            Relation<Row.R2<SectionId, PageId>, Choice> choicesForSection = Relation.of(
                    new HashMap<R2<SectionId, PageId>, Set<Choice>>(), HashSet.class);

            for (Entry<R2<SectionId, PageId>, Set<WritingInfo>> entry0 : sorted.keyValuesSet()) {
                SectionId section = entry0.getKey().get0();
                PageId subsection = entry0.getKey().get1();
                final Set<WritingInfo> rows = entry0.getValue();
                for (WritingInfo pathInfo : rows) {
                    String header = pathInfo.codeOutput.getHeader();
                    Set<Choice> choicesForPath = pathInfo.problems;
                    choicesForSection.putAll(Row.of(section, subsection), choicesForPath);
                    choicesForHeader.putAll(Row.of(section, subsection, header), choicesForPath);
                }
            }

            final String localeId = sourceFile.getLocaleID();
            int count = 0;
            for (Entry<R2<SectionId, PageId>, Set<WritingInfo>> entry0 : sorted.keyValuesSet()) {
                SectionId section = entry0.getKey().get0();
                PageId subsection = entry0.getKey().get1();
                final Set<WritingInfo> rows = entry0.getValue();

                rows.iterator().next().getUrl(localeId);
                // http://kwanyin.unicode.org/cldr-apps/survey?_=ur&x=scripts
                // http://unicode.org/cldr-apps/survey?_=ur&x=scripts

                output.append("\n<h2 class='tv-s'>Section: ")
                .append(section.toString())
                .append(" — <i><a target='CLDR_ST-SECTION' href='")
                .append(getPageUrl(localeId, subsection))
                .append("'>Page: ")
                .append(subsection.toString())
                .append("</a></i> (" + rows.size() + ")</h2>\n");
                startTable(choicesForSection.get(Row.of(section, subsection)), output);

                String oldHeader = "";
                for (WritingInfo pathInfo : rows) {
                    String header = pathInfo.codeOutput.getHeader();
                    String code = pathInfo.codeOutput.getCode();
                    String path = pathInfo.codeOutput.getOriginalPath();
                    Set<Choice> choicesForPath = pathInfo.problems;

                    if (!header.equals(oldHeader)) {
                        Set<Choice> headerChoices = choicesForHeader.get(Row.of(section, subsection, header));
                        output.append("<tr class='");
                        Choice.appendRowStyles(headerChoices, output);
                        output.append("'>\n");
                        output.append(" <th class='partsection' colSpan='6'>");
                        output.append(header);
                        output.append("</th>\n</tr>\n");
                        oldHeader = header;
                    }

                    output.append("<tr class='");
                    Choice.appendRowStyles(choicesForPath, output);
                    output.append("'>\n");
                    addCell(output, nf.format(++count), null, "tv-num", HTMLType.plain);
                    // path
                    addCell(output, code, null, "tv-code", HTMLType.plain);
                    // English value
                    if (choicesForPath.contains(Choice.englishChanged)) {
                        String winning = englishFile.getWinningValue(path);
                        String cellValue = winning == null ? "<i>missing</i>" : TransliteratorUtilities.toHTML
                                .transform(winning);
                        String previous = outdatedPaths.getPreviousEnglish(path);
                        if (previous != null) {
                            cellValue += "<br><span style='color:#900'><b>OLD: </b>"
                                    + TransliteratorUtilities.toHTML.transform(previous) + "</span>";
                        } else {
                            cellValue += "<br><b><i>missing</i></b>";
                        }
                        addCell(output, cellValue, null, "tv-eng", HTMLType.markup);
                    } else {
                        addCell(output, englishFile.getWinningValue(path), null, "tv-eng", HTMLType.plain);
                    }
                    // value for last version
                    final String oldStringValue = lastSourceFile == null ? null : lastSourceFile.getWinningValue(path);
                    MissingStatus oldValueMissing = getMissingStatus(lastSourceFile, path, status, latin);

                    addCell(output, oldStringValue, null, oldValueMissing != MissingStatus.PRESENT ? "tv-miss"
                            : "tv-last", HTMLType.plain);
                    // value for last version
                    String newWinningValue = sourceFile.getWinningValue(path);
                    if (CharSequences.equals(newWinningValue, oldStringValue)) {
                        newWinningValue = "=";
                    }
                    addCell(output, newWinningValue, null, choicesForPath.contains(Choice.missingCoverage) ? "tv-miss"
                            : "tv-win", HTMLType.plain);
                    // Fix?
                    // http://unicode.org/cldr/apps/survey?_=az&xpath=%2F%2Fldml%2FlocaleDisplayNames%2Flanguages%2Flanguage%5B%40type%3D%22az%22%5D
                    output.append(" <td class='tv-fix'><a target='CLDR-ST-ZOOMED' href='")
                    .append(pathInfo.getUrl(localeId)) // .append(c)baseUrl + "?_=")
                    // .append(localeID)
                    // .append("&amp;xpath=")
                    // .append(percentEscape.transform(path))
                    .append("'>");
                    Choice.appendDisplay(choicesForPath, "", output);
                    // String otherUrl = pathInfo.getUrl(sourceFile.getLocaleID());
                    output.append("</a></td>");
                    // if (!otherUrl.equals(url)) {
                    // output.append("<td class='tv-test'><a target='CLDR_ST-SECTION' href='")
                    // .append(otherUrl)
                    // .append("'><i>Section*</i></a></td>");
                    // }
                    if (!pathInfo.htmlMessage.isEmpty()) {
                        addCell(output, pathInfo.htmlMessage, null, "tv-test", HTMLType.markup);
                    }
                    output.append("</tr>\n");
                }
                output.append("</table>\n");
            }
        } catch (IOException e) {
            throw new IllegalArgumentException(e); // damn'ed checked exceptions
        }
    }

    private String getPageUrl(String localeId, PageId subsection) {
        return PathHeader.getPageUrl(baseUrl, localeId, subsection);
    }

    private void startTable(Set<Choice> choices, Appendable output) throws IOException {
        output.append("<table class='tv-table'>\n");
        output.append("<tr class='");
        Choice.appendRowStyles(choices, output);
        output.append("'>" +
                "<th class='tv-th'>No.</th>" +
                "<th class='tv-th'>Code</th>" +
                "<th class='tv-th'>English</th>" +
                "<th class='tv-th'>" + lastVersionTitle + "</th>" +
                "<th class='tv-th'>" + currentWinningTitle + "</th>" +
                "<th class='tv-th'>Fix?</th>" +
                "<th class='tv-th'>Comment</th>" +
                "</tr>\n");
    }

    enum HTMLType {
        plain, markup
    }

    private void addCell(Appendable output, String value, String title, String classValue, HTMLType htmlType)
            throws IOException {
        output.append(" <td class='")
        .append(classValue);
        if (value == null) {
            output.append(" tv-null'><i>missing</i></td>");
        } else {
            if (title != null && !title.equals(value)) {
                output.append("title='").append(TransliteratorUtilities.toHTML.transform(title)).append('\'');
            }
            output
            .append("'>")
            .append(htmlType == HTMLType.markup ? value : TransliteratorUtilities.toHTML.transform(value))
            .append("</td>\n");
        }
    }

    /**
     * Find the status of the items in the file.
     * @param file the source. Must be a resolved file, made with minimalDraftStatus = unconfirmed
     * @param pathHeaderFactory PathHeaderFactory.
     * @param foundCounter output counter of the number of paths with values having contributed or approved status
     * @param unconfirmedCounter output counter of the number of paths with values, but neither contributed nor approved status
     * @param missingCounter output counter of the number of paths without values
     * @param missingPaths output if not null, the specific paths that are missing.
     */
    public static void getStatus(CLDRFile file, PathHeader.Factory pathHeaderFactory, 
            Counter<Level> foundCounter, Counter<Level> unconfirmedCounter,
            Counter<Level> missingCounter, Relation<MissingStatus, String> missingPaths) {

        if (!file.isResolved()) {
            throw new IllegalArgumentException("File must be resolved, no minimal draft status");
        }
        foundCounter.clear();
        unconfirmedCounter.clear();
        missingCounter.clear();

        Status status = new Status();
        boolean latin = VettingViewer.isLatinScriptLocale(file);
        CoverageLevel2 coverageLevel2 = CoverageLevel2.getInstance(file.getLocaleID());

        for (String path : file.fullIterable()) {

            PathHeader ph = pathHeaderFactory.fromPath(path);
            if (ph.getSectionId() == SectionId.Special) {
                continue;
            }

            Level level = coverageLevel2.getLevel(path);
            String localeFound = file.getSourceLocaleID(path, status);
            // String value = file.getSourceLocaleID(path, status);
            MissingStatus missingStatus = VettingViewer.getMissingStatus(file, path, status, latin);

            switch (missingStatus) {
            case ABSENT:
                missingCounter.add(level, 1);
                if (missingPaths != null && level.compareTo(Level.MODERN) <= 0) {
                    missingPaths.put(missingStatus, path);
                }
                break;
            case ALIASED:
            case PRESENT:
                String fullPath = file.getFullXPath(path);
                if (fullPath.contains("unconfirmed")
                        || fullPath.contains("provisional")) {
                    unconfirmedCounter.add(level, 1);
                } else {
                    foundCounter.add(level, 1);
                }
                break;
            case MISSING_OK:
            case ROOT_OK:
                break;
            default:
                throw new IllegalArgumentException();
            }
        }
    }

    /**
     * Simple example of usage
     * 
     * @param args
     * @throws IOException
     */
    final static Options myOptions = new Options();

    enum MyOptions {
        filter(".*", ".*", "Filter files"),
        locale(".*", "af", "Single locale for testing"),
        source(".*", CldrUtility.TMP2_DIRECTORY + "/vxml/common/main",
            "if summary, creates filtered version (eg -d main): does a find in the name, which is of the form dir/file"),
        verbose(null, null, "verbose debugging messages"),
        output(".*", CldrUtility.TMP_DIRECTORY + "dropbox/mark/vetting/", "filter the raw files (non-summary, mostly for debugging)"), ;
        // boilerplate
        final Option option;

        MyOptions(String argumentPattern, String defaultArgument, String helpText) {
            option = myOptions.add(this, argumentPattern, defaultArgument, helpText);
        }
    }

    public static void main(String[] args) throws IOException {
        myOptions.parse(MyOptions.source, args, true);
        String fileFilter = MyOptions.filter.option.getValue();
        String myOutputDir = MyOptions.output.option.getValue();
        String LOCALE = MyOptions.locale.option.getValue();
        String CURRENT_MAIN = MyOptions.source.option.getValue();
        Timer timer = new Timer();
        timer.start();
        final String version = "23.0";
        final String lastMain = "/Users/markdavis/Google Drive/Backup-2012-10-09/Documents/indigo/cldr-archive/cldr-" +
                version + "/common/main";

        Factory cldrFactory = Factory.make(CURRENT_MAIN, fileFilter);
        cldrFactory.setSupplementalDirectory(new File(CldrUtility.SUPPLEMENTAL_DIRECTORY));
        Factory cldrFactoryOld = Factory.make(lastMain, fileFilter);
        SupplementalDataInfo supplementalDataInfo = SupplementalDataInfo
                .getInstance(CldrUtility.SUPPLEMENTAL_DIRECTORY);
        CheckCLDR.setDisplayInformation(cldrFactory.make("en", true));

        // FAKE this, because we don't have access to ST data

        UsersChoice<Organization> usersChoice = new UsersChoice<Organization>() {
            // Fake values for now
            public String getWinningValueForUsersOrganization(CLDRFile cldrFile, String path, Organization user) {
                if (path.contains("USD")) {
                    return "&dummy ‘losing’ value";
                }
                return null; // assume we didn't vote on anything else.
            }

            // Fake values for now
            public VoteStatus getStatusForUsersOrganization(CLDRFile cldrFile, String path, Organization user) {
                String usersValue = getWinningValueForUsersOrganization(cldrFile, path, user);
                String winningValue = cldrFile.getWinningValue(path);
                if (usersValue != null && !CharSequences.equals(usersValue, winningValue)) {
                    return VoteStatus.losing;
                }
                String fullPath = cldrFile.getFullXPath(path);
                if (fullPath.contains("AMD") || fullPath.contains("unconfirmed") || fullPath.contains("provisional")) {
                    return VoteStatus.provisionalOrWorse;
                } else if (fullPath.contains("AED")) {
                    return VoteStatus.disputed;
                } else if (fullPath.contains("AED")) {
                    return VoteStatus.ok_novotes;
                }
                return VoteStatus.ok;
            }
        };

        // create the tableView and set the options desired.
        // The Options should come from a GUI; from each you can get a long
        // description and a button label.
        // Assuming user can be identified by an int
        VettingViewer<Organization> tableView = new VettingViewer<Organization>(supplementalDataInfo, cldrFactory,
                cldrFactoryOld, usersChoice, "CLDR " + version,
                "Winning Proposed");

        // here are per-view parameters

        final EnumSet<Choice> choiceSet = EnumSet.allOf(Choice.class);
        String localeStringID = LOCALE;
        int userNumericID = 666;
        Level usersLevel = Level.MODERN;
        tableView.setBaseUrl("http://st.unicode.org/smoketest/survey");
        // http: // unicode.org/cldr-apps/survey?_=ur

        FileUtilities.copyFile(VettingViewer.class, "vettingView.css", myOutputDir);
        FileUtilities.copyFile(VettingViewer.class, "vettingView.js", myOutputDir);
        System.out.println(timer.getDuration() / NANOSECS + " secs");

        // timer.start();
        // writeFile(tableView, choiceSet, "", localeStringID, userNumericID, usersLevel, CodeChoice.oldCode);
        // System.out.println(timer.getDuration() / NANOSECS + " secs");

        timer.start();
        writeFile(myOutputDir, tableView, choiceSet, "", localeStringID, userNumericID, usersLevel, CodeChoice.newCode, null);
        System.out.println(timer.getDuration() / NANOSECS + " secs");

        timer.start();
        writeFile(myOutputDir, tableView, choiceSet, "", localeStringID, userNumericID, usersLevel, CodeChoice.summary,
                Organization.google);
        System.out.println(timer.getDuration() / NANOSECS + " secs");

//        timer.start();
//        writeFile(tableView, choiceSet, "", localeStringID, userNumericID, usersLevel, CodeChoice.summary,
//                Organization.ibm);
//        System.out.println(timer.getDuration() / NANOSECS + " secs");

        // // check that the choices work.
        // for (Choice choice : choiceSet) {
        // timer.start();
        // writeFile(tableView, EnumSet.of(choice), "-" + choice.abbreviation, localeStringID, userNumericID,
        // usersLevel);
        // System.out.println(timer.getDuration() / NANOSECS + " secs");
        // }
    }

    enum CodeChoice {
        /** For the normal (locale) view of data **/
        newCode,
        // /** @deprecated **/
        // oldCode,
        /** For a summary view of data **/
        summary
    }

    private static void writeFile(String myOutputDir, VettingViewer<Organization> tableView, final EnumSet<Choice> choiceSet,
            String name, String localeStringID, int userNumericID,
            Level usersLevel,
            CodeChoice newCode, Organization organization)
                    throws IOException {
        // open up a file, and output some of the styles to control the table
        // appearance
        PrintWriter out = BagFormatter.openUTF8Writer(myOutputDir, "vettingView"
                + name
                + (newCode == CodeChoice.newCode ? "" : newCode == CodeChoice.summary ? "-summary" : "")
                + (organization == null ? "" : "-" + organization.toString())
                + ".html");
        FileUtilities.appendFile(VettingViewer.class, "vettingViewerHead.txt", out);
        out.append(getHeaderStyles());
        out.append("</head><body>\n");

        out.println("<p>Note: this is just a sample run. The user, locale, user's coverage level, and choices of tests will change the output. In a real ST page using these, the first three would "
                + "come from context, and the choices of tests would be set with radio buttons. Demo settings are: </p>\n<ol>"
                + "<li>choices: "
                + choiceSet
                + "</li><li>localeStringID: "
                + localeStringID
                + "</li><li>userNumericID: "
                + userNumericID
                + "</li><li>usersLevel: "
                + usersLevel
                + "</ol>"
                + "<p>Notes: This is a static version, using old values and faked values (L) just for testing."
                + (TESTING ? "Also, the white cell after the Fix column is just for testing." : "")
                + "</p><hr>\n");

        // now generate the table with the desired options
        // The options should come from a GUI; from each you can get a long
        // description and a button label.
        // Assuming user can be identified by an int

        switch (newCode) {
        case newCode:
            tableView.generateHtmlErrorTables(out, choiceSet, localeStringID, organization, usersLevel, SHOW_ALL);
            break;
            // case oldCode:
            // tableView.generateHtmlErrorTablesOld(out, choiceSet, localeStringID, userNumericID, usersLevel, SHOW_ALL);
            // break;
        case summary:
            System.out.println(tableView.getName("zh_Hant_HK"));
            tableView.generateSummaryHtmlErrorTables(out, choiceSet, null, organization);
            break;
        }
        out.println("</body>\n</html>\n");
        out.close();
    }
}
