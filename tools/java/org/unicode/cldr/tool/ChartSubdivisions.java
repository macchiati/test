package org.unicode.cldr.tool;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.CLDRPaths;
import org.unicode.cldr.util.CldrUtility;
import org.unicode.cldr.util.Pair;
import org.unicode.cldr.util.XMLFileReader;
import org.unicode.cldr.util.XPathParts;

import com.ibm.icu.dev.util.TransliteratorUtilities;
import com.ibm.icu.impl.Row.R2;

public class ChartSubdivisions extends Chart {

    public static void main(String[] args) {
        new ChartSubdivisions().writeChart(null);
    }

    @Override
    public String getDirectory() {
        return FormattedFileWriter.CHART_TARGET_DIR;
    }
    @Override
    public String getTitle() {
        return "Subdivisions";
    }
    @Override
    public String getExplanation() {
        return "<p>TODO<p>";
    }

    @Override
    public void writeContents(FormattedFileWriter pw) throws IOException {

        TablePrinter tablePrinter = new TablePrinter()
        .addColumn("Region", "class='source'", null, "class='source'", true)
        .setSortPriority(1)
        .addColumn("Code", "class='source'", CldrUtility.getDoubleLinkMsg(), "class='source'", true)
        .setBreakSpans(true)

        .addColumn("Subdivision1", "class='target'", null, "class='target'", true)
        .setSortPriority(2)
        .addColumn("Code", "class='target'", CldrUtility.getDoubleLinkMsg(), "class='target'", true)
        .setBreakSpans(true)

        .addColumn("Subdivision2", "class='target'", null, "class='target'", true)
        .setSortPriority(3)
        .addColumn("Code", "class='target'", CldrUtility.getDoubleLinkMsg(), "class='target'", true)
        ;

        Map<String, R2<List<String>, String>> aliases = SDI.getLocaleAliasInfo().get("subdivision");
        
        for (String container : SDI.getContainersForSubdivisions()) {
            int pos = container.indexOf('-');
            String region = pos < 0 ? container : container.substring(0, pos);
            for (String contained : SDI.getContainedSubdivisions(container)) {
                if (SDI.getContainedSubdivisions(contained) != null) {
                    continue;
                }
                String s1 = pos < 0 ? contained : container;
                String s2 = pos < 0 ? "" : contained;

                String name1 = getName(s1);
                String name2 = getName(s2);
                
                // mark aliases
                R2<List<String>, String> a1 = aliases.get(s1);
                if (a1 != null) {
                    name1 = "= " + a1.get0().get(0) + " (" + name1 + ")";
                }
                R2<List<String>, String> a2 = aliases.get(s2);
                if (a2 != null) {
                    name2 = "= " + a2.get0().get(0) + " (" + name2 + ")";
                }
                tablePrinter.addRow()
                .addCell(ENGLISH.getName(CLDRFile.TERRITORY_NAME, region))
                .addCell(region)
                .addCell(name1)
                //.addCell(type)
                .addCell(s1)
                .addCell(name2)
                .addCell(s2)
                .finishRow();

            }
        }
        pw.write(tablePrinter.toTable());
    }

    Map<String,String> subdivisionToName = new HashMap<>();
    {
        List<Pair<String, String>> data = new ArrayList<>();
        XMLFileReader.loadPathValues(CLDRPaths.COMMON_DIRECTORY + "subdivisions/en.xml", data, true);
        for (Pair<String, String> pair : data) {
            // <subdivision type="AD-02">Canillo</subdivision>
            XPathParts path = XPathParts.getFrozenInstance(pair.getFirst());
            if (!"subdivision".equals(path.getElement(-1))) {
                continue;
            }
            String name = pair.getSecond();
            subdivisionToName.put(path.getAttributeValue(-1, "type"), name);
        }

    }
    private String getName(String s1) {
        return s1.isEmpty() ? "" : TransliteratorUtilities.toHTML.transform(subdivisionToName.get(s1));
    }
}
