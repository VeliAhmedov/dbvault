package io.github.veliahmedov.dbvault.db;

import io.github.veliahmedov.dbvault.exception.DbVaultException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Unit test: test on restore to check if it restores alongside constraints, other metadata, selective restoration
public class RestoreListTest {

    // shape of `pg_restore -l` output taken from commands, sample
    private static final String CONTENTS = """
            ;
            ; Archive created at 2026-11-14 10:00:00 UTC
            ;     dbname: testdb
            ;
            ; Selected TOC Entries:
            ;
            218; 1259 16388 TABLE public users dbvault
            217; 1259 16387 SEQUENCE public users_id_seq dbvault
            3367; 0 0 SEQUENCE OWNED BY public users_id_seq dbvault
            3210; 2604 16392 DEFAULT public users id dbvault
            3361; 0 16388 TABLE DATA public users dbvault
            3368; 0 0 SEQUENCE SET public users_id_seq -
            3216; 2606 16394 CONSTRAINT public users users_pkey dbvault
            220; 1259 16400 TABLE public orders dbvault
            3362; 0 16400 TABLE DATA public orders dbvault
            """;

    // test if filter only keep users related parts,
    @Test
    void keepsTableDataDefaultConstraintAndSequence(){
        String result = RestoreList.filter(CONTENTS, List.of("users"));
        assertEquals(7, result.lines().count());                  // there are 7 user related entries
        assertTrue(result.contains("TABLE DATA public users"));            // TABLE DATA users
        assertTrue(result.contains("DEFAULT public users id"));            // DEFAULT value users
        assertTrue(result.contains("CONSTRAINT public users users_pkey")); // PK CONSTRAINT users
        assertTrue(result.contains("SEQUENCE public users_id_seq"));       // SEQUENCE users
        assertTrue(result.contains("SEQUENCE SET public users_id_seq"));   // SEQUENCE SET value users
        assertFalse(result.contains("orders"));                            // there shouldn't be any order related content
        // other 2 are TABLE and SEQUENCE OWNED  users, not checked, all together is 7
    }
    // test if user can specify the table with its schema
    @Test
    void acceptsSchemeQualifiedNames(){
        assertEquals(7, RestoreList.filter(CONTENTS, List.of("public.users")).lines().count());
    }
    // test if users and orders tables included entries all is 9
    @Test
    void severalTablesAreCombined() {
        assertEquals(9, RestoreList.filter(CONTENTS, List.of("users", "orders")).lines().count());
    }
    // try to restore contents of unknown, non-existent table, it should throw intended exception
    @Test
    void unknownTableFails() {
        DbVaultException e = assertThrows(DbVaultException.class, () -> RestoreList.filter(CONTENTS, List.of("nope")));
        assertTrue(e.getMessage().contains("Table 'nope' not found in this backup"));
    }
}
