package Email_backend.Email_backend;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

class EmailBackendApplicationTests {

	@Test
	void checkDb() {
        String url = "jdbc:postgresql://13.127.174.158/emailprod";
        String user = "emailprod";
        String password = "mo970GQRa1EWF1Jl";

        try {
            Class.forName("org.postgresql.Driver");
            System.out.println("Connecting to database...");
            Connection conn = DriverManager.getConnection(url, user, password);
            System.out.println("Connected successfully!");

            System.out.println("\n--- RUNNING QUERIES ---");
            Statement stmt = conn.createStatement();
            String sql = "SELECT pid, query, state, wait_event_type, wait_event, age(clock_timestamp(), query_start) FROM pg_stat_activity WHERE state != 'idle'";
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                System.out.printf("PID: %d | Age: %s | State: %s | Wait: %s/%s\nQuery: %s\n\n",
                    rs.getInt("pid"),
                    rs.getString("age"),
                    rs.getString("state"),
                    rs.getString("wait_event_type"),
                    rs.getString("wait_event"),
                    rs.getString("query")
                );
            }

            System.out.println("\n--- LOCKS INFO ---");
            String lockSql = "SELECT blocked_locks.pid     AS blocked_pid, " +
                             "       blocked_activity.query  AS blocked_statement, " +
                             "       blocking_locks.pid    AS blocking_pid, " +
                             "       blocking_activity.query AS blocking_statement " +
                             "FROM  pg_catalog.pg_locks         blocked_locks " +
                             "JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid " +
                             "JOIN pg_catalog.pg_locks         blocking_locks " +
                             "  ON blocking_locks.locktype = blocked_locks.locktype " +
                             "  AND blocking_locks.database IS NOT DISTINCT FROM blocked_locks.database " +
                             "  AND blocking_locks.relation IS NOT DISTINCT FROM blocked_locks.relation " +
                             "  AND blocking_locks.page IS NOT DISTINCT FROM blocked_locks.page " +
                             "  AND blocking_locks.tuple IS NOT DISTINCT FROM blocked_locks.tuple " +
                             "  AND blocking_locks.virtualxid IS NOT DISTINCT FROM blocked_locks.virtualxid " +
                             "  AND blocking_locks.transactionid IS NOT DISTINCT FROM blocked_locks.transactionid " +
                             "  AND blocking_locks.classid IS NOT DISTINCT FROM blocked_locks.classid " +
                             "  AND blocking_locks.objid IS NOT DISTINCT FROM blocked_locks.objid " +
                             "  AND blocking_locks.objsubid IS NOT DISTINCT FROM blocked_locks.objsubid " +
                             "  AND blocking_locks.pid != blocked_locks.pid " +
                             "JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid " +
                             "WHERE NOT blocked_locks.granted";
            ResultSet lockRs = stmt.executeQuery(lockSql);
            boolean locksFound = false;
            java.util.List<Integer> blockingPids = new java.util.ArrayList<>();
            while (lockRs.next()) {
                locksFound = true;
                int blockingPid = lockRs.getInt("blocking_pid");
                blockingPids.add(blockingPid);
                System.out.printf("BLOCKED PID: %d executing [%s]\nBLOCKING PID: %d executing [%s]\n\n",
                    lockRs.getInt("blocked_pid"),
                    lockRs.getString("blocked_statement"),
                    blockingPid,
                    lockRs.getString("blocking_statement")
                );
            }
            if (!locksFound) {
                System.out.println("No blocked locks found.");
            } else {
                System.out.println("Attempting to terminate blocking PIDs: " + blockingPids);
                for (int pid : blockingPids) {
                    try {
                        Statement killStmt = conn.createStatement();
                        ResultSet killRs = killStmt.executeQuery("SELECT pg_terminate_backend(" + pid + ")");
                        if (killRs.next()) {
                            System.out.println("Terminated PID " + pid + ": " + killRs.getBoolean(1));
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to terminate PID " + pid + ": " + e.getMessage());
                    }
                }
            }

            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
	}

}
