package io.github.veliahmedov.dbvault.db;

import io.github.veliahmedov.dbvault.exception.DbVaultException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Main point of this utility class is to fix pg_restore -t users problem where it only restores table and its rows
// ignoring  PK, id auto-increment default, sequences and constraints as they are separate entities in dump file
// so it picks entries of pg-restore contents that belong to tables
final class RestoreList {
    private RestoreList() {}

    // content is text returned after pg_restore -l that contains all objects in backup
    static String filter(String content, List<String> tables) {
        // split into lines and in overall comments (which starts with ;) removed for processing too
        List<String> lines = content.lines().filter(l -> !l.startsWith(";")).toList();
        // these are requested tables which regex alongside identify  entries that should restored too
        List<Pattern> requested = new ArrayList<>();
        for (String table : tables) {
            requested.addAll(patternsFor(table, lines));
        }

        // process each content line, filter by patterns we generated and collect them with new lines seperated
        // in summary it keeps content entries relevant to requested tables
        return lines.stream()
                .filter(l -> requested.stream().anyMatch(p -> p.matcher(l).find()))
                .collect(Collectors.joining("\n", "", "\n"));
    }

    // it generates patterns needed for table, used by scheme and table identification
    private static List<Pattern> patternsFor(String qualified, List<String> lines) {
        // . is what separates scheme and table name, and shows if there is table or not
        int dot = qualified.lastIndexOf('.');
        String schema = dot < 0 ? "public" : qualified.substring(0, dot);
        String name = dot < 0 ? qualified : qualified.substring(dot + 1);
        // (123; 1259 2200 TABLE public users postgres) from this regex try to din public users
        Pattern table = Pattern.compile("\\s" + Pattern.quote(schema) + " " + Pattern.quote(name) + "(\\s|$)");
        // checks if requested table exists or not, it contains " TABLE " and matches given table pattern
        if (lines.stream().noneMatch(l -> l.contains(" TABLE ") && table.matcher(l).find())) {
            throw new DbVaultException("Table '" + qualified + "' not found in this backup");
        }
        // list containing pattern, will be used for restoring entries
        List<Pattern> patterns = new ArrayList<>(List.of(table));
        // find DEFAULT entries of table like 126; 0 0 DEFAULT public users id nextval('users_id_seq'::regclass), regex try to find "id"
        Pattern columnDefault = Pattern.compile("\\sDEFAULT " + Pattern.quote(schema) + " " + Pattern.quote(name) + " (\\S+)");
        // for every line, regex matcher for that line, keep ones default pattern matches, extract column name, generate sequence pattern for column
        // for example it might generate SEQUENCE public users_id_seq
        lines.stream()
                .map(columnDefault::matcher)
                .filter(Matcher::find)
                .forEach(m -> patterns.add(sequencePattern(schema, name, m.group(1))));
        return patterns;
    }

    // this build regex for sequence of table
    // for example: sequencePattern("public", "users", "id"); looks for
    //SEQUENCE public users_id_seq
    //SEQUENCE OWNED BY public users_id_seq
    //SEQUENCE SET public users_id_seq
    private static Pattern sequencePattern(String schema, String table, String column) {
        return Pattern.compile("\\sSEQUENCE( OWNED BY| SET)? " + Pattern.quote(schema)
                + " " + Pattern.quote(table + "_" + column + "_seq") + "(\\s|$)");
    }
}
// overall flow is:
// user request selective restore of tables = ["public.users"]
// filter() remove comment and process requested tables
// patternsFor() generate pattern for table and its column defaults
// sequencePattern() create pattern for sequences
// end result contains only matching entries (pg_restore -L filtered.list)
