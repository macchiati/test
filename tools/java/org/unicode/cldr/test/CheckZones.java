/*
 ******************************************************************************
 * Copyright (C) 2005, 2007 International Business Machines Corporation and   *
 * others. All Rights Reserved.                                               *
 ******************************************************************************
*/
package org.unicode.cldr.test;

import java.util.List;
import java.util.Map;

import org.unicode.cldr.util.CLDRFile;
import org.unicode.cldr.util.TimezoneFormatter;
import org.unicode.cldr.util.XMLSource;
import org.unicode.cldr.util.XPathParts;

import com.ibm.icu.impl.CollectionUtilities;
import com.ibm.icu.text.Collator;
import com.ibm.icu.text.UnicodeSet;
import com.ibm.icu.util.TimeZone;
import com.ibm.icu.util.ULocale;

public class CheckZones extends CheckCLDR {
	//private final UnicodeSet commonAndInherited = new UnicodeSet(CheckExemplars.Allowed).complement(); 
	// "[[:script=common:][:script=inherited:][:alphabetic=false:]]");

	private TimezoneFormatter timezoneFormatter;
	
	public CheckCLDR setCldrFileToCheck(CLDRFile cldrFile, Map options, List possibleErrors) {
		if (cldrFile == null) return this;
		super.setCldrFileToCheck(cldrFile, options, possibleErrors);
		try {
			timezoneFormatter = new TimezoneFormatter(getResolvedCldrFileToCheck(), true);
		} catch (RuntimeException e) {
			possibleErrors.add(new CheckStatus().setCause(this).setType(CheckStatus.errorType)
					.setMessage("Checking zones: " + e.getMessage()));
		}
		return this;
	}
	
	XPathParts parts = new XPathParts(null, null);
        String previousZone = new String();
        String previousFrom = new String("1970-01-01");
        String previousTo = new String("present");

	public CheckCLDR handleCheck(String path, String fullPath, String value,
			Map options, List result) {
		if (path.indexOf("timeZoneNames") < 0 || path.indexOf("usesMetazone") < 0)
			return this;
		if (timezoneFormatter == null) {
			throw new InternalError("This should not occur: setCldrFileToCheck must create a TimezoneFormatter.");
		}
		parts.set(path);

                String zone = parts.getAttributeValue(3,"type");
                String from;
                if (parts.containsAttribute("from"))
		   from=parts.getAttributeValue(4,"from");
                else
                   from="1970-01-01";
                String to;
                if (parts.containsAttribute("to"))
		   to=parts.getAttributeValue(4,"to");
                else
                   to="present";
		   
                if ( zone.equals(previousZone) ) {
		   if ( from.compareTo(previousTo) < 0 ) {
				result.add(new CheckStatus().setCause(this).setType(CheckStatus.errorType)
				      .setMessage("Multiple metazone mappings between {1} and {0}",
                                                   new Object[] {previousTo,from} ));
                   }
		   if ( from.compareTo(previousTo) > 0 ) {
				result.add(new CheckStatus().setCause(this).setType(CheckStatus.warningType)
				      .setMessage("No metazone mapping between {0} and {1}",
                                                   new Object[] {previousTo,from} ));
                   }
                }
                else {
                   if ( previousFrom.compareTo("1970-01-01") != 0 ) {
				result.add(new CheckStatus().setCause(this).setType(CheckStatus.warningType)
				      .setMessage("Zone {0} has no metazone mapping between 1970-01-01 and {1}",
                                                   new Object[] {previousZone,previousFrom} ));
                   } 
                   if ( previousTo.compareTo("present") != 0 ) {
				result.add(new CheckStatus().setCause(this).setType(CheckStatus.warningType)
				      .setMessage("Zone {0} has no metazone mapping between {1} and present.",
                                                   new Object[] {previousZone,previousTo} ));
                   }
                   previousFrom = from;
                }

                previousTo = to;
                previousZone = zone;
 
		return this;
	}

	public CheckCLDR handleGetExamples(String path, String fullPath, String value,
			Map options, List result) {
		if (path.indexOf("timeZoneNames") < 0)
			return this;
		if (timezoneFormatter == null) {
			throw new InternalError("This should not occur: setCldrFileToCheck must create a TimezoneFormatter.");
		}
		parts.set(path);
		if (parts.containsElement("zone")) {
			String id = (String) parts.getAttributeValue(3,"type");
			TimeZone tz = TimeZone.getTimeZone(id);
			String pat = "vvvv";
			if (parts.containsElement("exemplarCity")) {
			        int delim = id.indexOf('/');
                                if ( delim >= 0 ) {
                                String formatted = id.substring(delim+1).replaceAll("_"," ");
				result.add(new CheckStatus().setCause(this).setType(
						CheckStatus.exampleType).setMessage("Formatted value (if removed): \"{0}\"",
						new Object[] { formatted }));
                                }
			} else if ( !parts.containsElement("usesMetazone") ){
                           if ( parts.containsElement("generic") ) {
				pat = "vvvv";
				if (parts.containsElement("short")) pat = "v";
                           }
                           else {
				pat = "zzzz";
				if (parts.containsElement("short")) pat = "z";
                           }
                                boolean daylight = parts.containsElement("daylight");
				String formatted = timezoneFormatter.getFormattedZone(id, pat,
						daylight, tz.getRawOffset(), true);
				result.add(new CheckStatus().setCause(this).setType(CheckStatus.exampleType)
						.setMessage("Formatted value (if removed): \"{0}\"", new Object[] {formatted}));
			}
		}
		return this;
	}

}
