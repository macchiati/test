package org.unicode.cldr.tool;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.unicode.cldr.tool.GeneratePluralRanges.RangeSample;
import org.unicode.cldr.tool.PluralRulesFactory.SamplePatterns;
import org.unicode.cldr.tool.ShowLanguages.FormattedFileWriter;
import org.unicode.cldr.util.CLDRConfig;
import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.PluralSnapshot;
import org.unicode.cldr.util.SupplementalDataInfo;
import org.unicode.cldr.util.PluralSnapshot.Integral;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo;
import org.unicode.cldr.util.SupplementalDataInfo.PluralType;
import org.unicode.cldr.util.SupplementalDataInfo.PluralInfo.Count;
import org.unicode.cldr.util.SupplementalDataInfo.SampleList;

import com.ibm.icu.impl.Utility;
import com.ibm.icu.text.NumberFormat;
import com.ibm.icu.text.PluralRules;
import com.ibm.icu.text.PluralRules.FixedDecimal;
import com.ibm.icu.util.ULocale;

public class ShowPlurals {

    private static final String NO_PLURAL_DIFFERENCES = "<i>no plural differences</i>";
    private static final String NOT_AVAILABLE = "<i>Not available.<br>Please <a target='_blank' href='http://unicode.org/cldr/trac/newticket'>file a ticket</a> to supply.</i>";
    static SupplementalDataInfo supplementalDataInfo = CLDRConfig.getInstance().getSupplementalDataInfo();

    public static void printPlurals(CLDRFile english, String localeFilter, PrintWriter index) throws IOException {
        String section1 = "Rules";
        String section2 = "Comparison";

        final String title = "Language Plural Rules";
        final PrintWriter pw = new PrintWriter(new FormattedFileWriter(index, title, null, false));
        ShowLanguages.showContents(pw, "rules", "Rules", "comparison", "Comparison");
        
        pw.append("<h2>" + CldrUtility.getDoubleLinkedText("rules", "1. " + section1) + "</h2>\n");
        printPluralTable(english, localeFilter, pw);

        pw.append("<h2>" + CldrUtility.getDoubleLinkedText("comparison", "2. " + section2) + "</h2>\n");
        pw.append("<p style='text-align:left'>The plural forms are abbreviated by first letter, with 'x' for 'other'. "
            +
            "If values are made redundant by explicit 0 and 1, they are underlined. " +
            "The fractional and integral results are separated for clarity.</p>\n");
        PluralSnapshot.writeTables(english, pw);
        appendBlanksForScrolling(pw);
        pw.close();
    }

    public static void appendBlanksForScrolling(final Appendable pw) {
        try {
            pw.append(Utility.repeat("<br>", 100)).append('\n');
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static void printPluralTable(CLDRFile english, String localeFilter, Appendable appendable) throws IOException {

        final TablePrinter tablePrinter = new TablePrinter()
        .addColumn("Name", "class='source'", null, "class='source'", true).setSortPriority(0)
        .setBreakSpans(true).setRepeatHeader(true)
        .addColumn("Code", "class='source'", CldrUtility.getDoubleLinkMsg(), "class='source'", true)
        .addColumn("Type", "class='source'", null, "class='source'", true)
        .setBreakSpans(true)
        .addColumn("Category", "class='target'", null, "class='target'", true)
        .setSpanRows(false)
        .addColumn("Examples", "class='target'", null, "class='target'", true)
        .addColumn("Minimal Pairs", "class='target'", null, "class='target'", true)
        .addColumn("Rules", "class='target'", null, "class='target' nowrap", true)
        .setSpanRows(false)
        ;

        Map<ULocale, org.unicode.cldr.tool.PluralRulesFactory.SamplePatterns> samples = PluralRulesFactory.getLocaleToSamplePatterns();

        for (String locale : supplementalDataInfo.getPluralLocales()) {
            if (localeFilter != null && !localeFilter.equals(locale)) {
                continue;
            }
            final String name = english.getName(locale);
            for (PluralType pluralType : PluralType.values()) {
                final PluralInfo plurals = supplementalDataInfo.getPlurals(pluralType, locale);
                ULocale locale2 = new ULocale(locale);
                final SamplePatterns samplePatterns = pluralType == PluralType.ordinal ? null 
                    : CldrUtility.get(samples, locale2);
                NumberFormat nf = NumberFormat.getInstance(locale2);

                String rules = plurals.getRules();
                rules += rules.length() == 0 ? "other:<i>everything</i>" : ";other:<i>everything else</i>";
                rules = rules.replace(":", " → ").replace(";", ";<br>");
                PluralRules pluralRules = plurals.getPluralRules();
                //final Map<PluralInfo.Count, String> typeToExamples = plurals.getCountToStringExamplesMap();
                //final String examples = typeToExamples.get(type).toString().replace(";", ";<br>");
                Set<Count> counts = plurals.getCounts();
                for (PluralInfo.Count count : counts) {
                    String keyword = count.toString();
                    SampleList exampleList = plurals.getSamples9999(count);
                    String examples = exampleList.toString();
                    String rule = pluralRules.getRules(keyword);
                    rule = rule != null ? rule.replace(":", " → ")
                        .replace(" and ", " and<br>&nbsp;&nbsp;")
                        .replace(" or ", " or<br>")
                        : counts.size() == 1 ? "<i>everything</i>"
                            : "<i>everything else</i>";
                        String sample = counts.size() == 1 ? NO_PLURAL_DIFFERENCES : NOT_AVAILABLE;
                        if (samplePatterns != null) {
                            String samplePattern = CldrUtility.get(samplePatterns.keywordToPattern, Count.valueOf(keyword));
                            if (samplePattern != null) {
                                if (exampleList.getRangeCount() > 0) {
                                    int intSample = exampleList.getRangeStart(0);
                                    sample = getSample(new FixedDecimal(intSample), samplePattern, nf);
                                } else {
                                    sample = "";
                                }
                                List<FixedDecimal> fractions = exampleList.getFractions();
                                if (fractions.size() != 0) {
                                    FixedDecimal numb = fractions.iterator().next();
                                    if (sample.length() != 0) {
                                        sample += "<br>";
                                    }
                                    sample += getSample(numb, samplePattern, nf);
                                }
                            }
                        }
                        tablePrinter.addRow()
                        .addCell(name)
                        .addCell(locale)
                        .addCell(pluralType.toString())
                        .addCell(count.toString())
                        .addCell(examples.toString())
                        .addCell(sample)
                        .addCell(rule)
                        .finishRow();
                }
            }
            List<RangeSample> rangeInfoList = GeneratePluralRanges.getRangeInfo(locale);
            if (rangeInfoList != null) {
                for (RangeSample item : rangeInfoList) {
                    tablePrinter.addRow()
                    .addCell(name)
                    .addCell(locale)
                    .addCell("range")
                    .addCell(item.start + "+" + item.end)
                    .addCell(item.min + "–" + item.max)
                    .addCell(item.resultExample.replace(". ", ".<br>"))
                    .addCell(item.start + " + " + item.end + " → " + item.result)
                    .finishRow();
                }
            } else {
                String message = supplementalDataInfo.getPlurals(PluralType.cardinal, locale).getCounts().size() == 1 ? NO_PLURAL_DIFFERENCES : NOT_AVAILABLE;
                tablePrinter.addRow()
                .addCell(name)
                .addCell(locale)
                .addCell("range")
                .addCell("<i>n/a</i>")
                .addCell("<i>n/a</i>")
                .addCell(message)
                .addCell("<i>n/a</i>")
                .finishRow();
            }
        }
        appendable.append(tablePrinter.toTable()).append('\n');
    }

    private static String getSample(FixedDecimal numb, String samplePattern, NumberFormat nf) {
        String sample;
        nf.setMaximumFractionDigits(numb.getVisibleDecimalDigitCount());
        nf.setMinimumFractionDigits(numb.getVisibleDecimalDigitCount());
        sample = samplePattern
            .replace('\u00A0', '\u0020')
            .replace("{0}", nf.format(numb.source))
            .replace(". ", ".<br>");
        return sample;
    }

}
