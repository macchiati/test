package org.unicode.cldr.draft;

import java.util.Iterator;
import java.util.regex.Matcher;

public class RegexTransformState {
  
  public enum Status {NOMATCH, BLOCKED, MATCH};
  
  StringBuilder processedAlready = new StringBuilder();
  StringBuilder toBeProcessed;
  RegexTransform regexTransform;
  
  public RegexTransformState(RegexTransform regexTransform, CharSequence text) {
    this.regexTransform = regexTransform;
    toBeProcessed = new StringBuilder(text);
    main:
    while (true) {
      Status s = match();
      switch (s) {
        case BLOCKED:
        // we can't convert any more, so stop
        processedAlready.append(toBeProcessed);
        break main;
        case MATCH:
          // the actions have been done inside the match
          break;
        case NOMATCH:
          if (toBeProcessed.length() == 0) {
            break main;
          }
          // transfer one code point
          // TODO fix -- right now it is char
          processedAlready.append(toBeProcessed.subSequence(0, 1));
          toBeProcessed.delete(0,1);
          break;
      }
    }
  }

  public Status match() {
    for (Iterator<Rule> it = regexTransform.iterator(toBeProcessed); it.hasNext();) {
      Status status = match(it.next());
      if (status != Status.NOMATCH) { // keep going as long as we get NOMATCH
        return status;
      }
    }
    return Status.NOMATCH;
  }
  
  /**
   * return true if the rule matches at offset in text, without touching text before start or after finish
   * @param text
   * @param offset
   * @param start
   * @param finish
   */
  public Status match(Rule rule) {
    // fix to use real API
    final Matcher prematcher = rule.getPrematcher(processedAlready);
    if (prematcher != null && !prematcher.find(processedAlready.length())) {
      return Status.NOMATCH;
    }
    final Matcher postmatcher = rule.getPostmatcher(toBeProcessed);
    if (!postmatcher.lookingAt()) {
      if (postmatcher.hitEnd()) {
        return Status.BLOCKED;
      } else {
        return Status.NOMATCH;
      }
    }

    // we have a match, so do the replacement
    int newCursor = rule.append(processedAlready, prematcher, postmatcher);
    // we are going to adjust the contents of processedAlready and toBeProcessed
    // based on the new cursor, and what we are going to "eat" from the toBeProcessed.
    // first do it the slow way; optimize later
    toBeProcessed.delete(0, postmatcher.end());
    final int delta = newCursor - processedAlready.length();
    if (delta == 0) {
      // do nothing, all ok
    } else if (delta < 0) {
      // move stuff to future
      toBeProcessed.insert(0, processedAlready.subSequence(newCursor,processedAlready.length()));
      processedAlready.setLength(newCursor);
    } else { // greater
      processedAlready.append(toBeProcessed.subSequence(0,delta));
      processedAlready.delete(0,delta);
    }
    return Status.MATCH;
  }

  public String toString() {
    return processedAlready.toString();
  }
}
