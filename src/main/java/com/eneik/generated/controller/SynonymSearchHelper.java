package com.eneik.generated.controller;

import java.util.*;

public class SynonymSearchHelper {

    private static final List<Set<String>> SYNONYM_GROUPS = new ArrayList<>();

    static {
        // Group 1: ФБУН / ЦНИИ Эпидемиологии / Роспотребнадзор
        Set<String> g1 = new HashSet<>(Arrays.asList(
                "фбун",
                "цнии эпидемиологии",
                "роспотребнадзор",
                "роспотребнадзора"
        ));
        SYNONYM_GROUPS.add(g1);

        // Group 2: ГЭК / Государственная экзаменационная комиссия
        Set<String> g2 = new HashSet<>(Arrays.asList(
                "гэк",
                "государственная экзаменационная комиссия",
                "экзаменационная комиссия"
        ));
        SYNONYM_GROUPS.add(g2);

        // Group 3: ГИА / Государственная итоговая аттестация
        Set<String> g3 = new HashSet<>(Arrays.asList(
                "гиа",
                "государственная итоговая аттестация",
                "итоговая аттестация"
        ));
        SYNONYM_GROUPS.add(g3);

        // Group 4: ФГОС / Федеральный государственный образовательный стандарт
        Set<String> g4 = new HashSet<>(Arrays.asList(
                "фгос",
                "федеральный государственный образовательный стандарт",
                "образовательный стандарт"
        ));
        SYNONYM_GROUPS.add(g4);
    }

    /**
     * Checks if a document (represented as a Map) matches the query q, taking synonyms into account.
     */
    public static boolean matchesQueryWithSynonyms(Map<String, Object> docMap, String query) {
        if (query == null || query.trim().isEmpty()) {
            return true;
        }

        String normQuery = query.toLowerCase().trim();
        List<String> searchTerms = new ArrayList<>();
        searchTerms.add(normQuery);

        // Expand query if it contains any of our known synonyms
        for (Set<String> group : SYNONYM_GROUPS) {
            boolean matchesGroup = false;
            for (String synonym : group) {
                if (normQuery.contains(synonym) || synonym.contains(normQuery)) {
                    matchesGroup = true;
                    break;
                }
            }
            if (matchesGroup) {
                searchTerms.addAll(group);
            }
        }

        // Get document fields to match against
        String name = docMap.get("name") != null ? ((String) docMap.get("name")).toLowerCase() : "";
        String description = docMap.get("description") != null ? ((String) docMap.get("description")).toLowerCase() : "";
        String docType = docMap.get("doc_type") != null ? ((String) docMap.get("doc_type")).toLowerCase() : "";
        List<String> tags = (List<String>) docMap.getOrDefault("tags", Collections.emptyList());

        // Check if any search term matches name, description, doc_type, or tags
        for (String term : searchTerms) {
            if (name.contains(term) || description.contains(term) || docType.contains(term)) {
                return true;
            }
            for (String tag : tags) {
                if (tag.toLowerCase().contains(term)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Generates a list of suggested queries or autocomplete hints for a given partial search query.
     */
    public static List<String> getSuggestions(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String norm = query.toLowerCase().trim();
        List<String> suggestions = new ArrayList<>();

        for (Set<String> group : SYNONYM_GROUPS) {
            boolean matchesGroup = false;
            for (String synonym : group) {
                if (synonym.contains(norm)) {
                    matchesGroup = true;
                    break;
                }
            }
            if (matchesGroup) {
                // Return capitalized elements of the group as suggestions
                for (String term : group) {
                    if (!term.equals(norm)) {
                        suggestions.add(capitalize(term));
                    }
                }
            }
        }

        // Limit to top 5 suggestions
        if (suggestions.size() > 5) {
            return suggestions.subList(0, 5);
        }
        return suggestions;
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}
