package com.studytracker.storage;

import java.util.ArrayList;
import java.util.List;

/**
 * Just enough CSV to save tasks safely. The tricky part is that a title can contain commas
 * or quotes ("Read ch. 3, then do 1-10"), so those fields get wrapped in quotes and any
 * quote inside is doubled, which is the same convention Excel and Google Sheets use.
 */
final class Csv {

    private Csv() {
    }

    static String escape(String field) {
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    static List<String> parseLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    boolean doubledQuote = i + 1 < line.length() && line.charAt(i + 1) == '"';
                    if (doubledQuote) {
                        current.append('"');
                        i++; // skip the second quote
                    } else {
                        inQuotes = false;
                    }
                } else {
                    current.append(c);
                }
            } else if (c == '"') {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }

        if (inQuotes) {
            throw new IllegalArgumentException("a quote was opened but never closed");
        }
        fields.add(current.toString());
        return fields;
    }
}
