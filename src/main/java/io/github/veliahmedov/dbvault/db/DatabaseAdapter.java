package io.github.veliahmedov.dbvault.db;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

//common operation for each DBs via strategy pattern due each DB having different syntax for ops
public interface DatabaseAdapter {
    // testing database connection
    void testConnection();

    // export db, stream back into given stream for processing, then it wrapped in  gzip for reduce sizing
    void backup(OutputStream out) throws IOException;

    // import db, restore from given stream, tables are data to be restored if empty it is restored
    void restore(InputStream in, List<String> tables) throws IOException;

    // retrieve available table names
    List<String> listTables();
}

// Database adapter (produce backup bytes) -> GZIPOutputStream (compress data as flows) -> Storage (writes compressed data)
