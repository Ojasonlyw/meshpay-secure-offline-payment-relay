package com.demo.upimesh;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class DatabaseMigrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    private Flyway migrate(boolean demo) {
        return migrate(demo, null);
    }

    private Flyway migrate(boolean demo, String target) {
        String schema = "test_" + UUID.randomUUID().toString().replace("-", "");
        var config = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .schemas(schema).defaultSchema(schema);
        config.locations(demo
                ? new String[]{"classpath:db/migration", "classpath:db/demo-migration"}
                : new String[]{"classpath:db/migration"});
        if (target != null) config.target(target);
        Flyway flyway = config.load();
        flyway.migrate();
        return flyway;
    }

    private Connection connect(Flyway flyway) throws SQLException {
        Connection connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        connection.setSchema(flyway.getConfiguration().getDefaultSchema());
        return connection;
    }

    private long count(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private void rejected(Connection connection, String sql, String state) {
        // Each statement auto-commits, so an expected failure cannot poison the next check.
        SQLException exception = assertThrows(SQLException.class, () -> execute(connection, sql));
        assertEquals(state, exception.getSQLState());
    }

    @Test
    void productionSchemaHasNoDemoDataAndCanBeMigratedAgain() throws Exception {
        Flyway flyway = migrate(false);
        assertEquals(12, java.util.Arrays.stream(flyway.info().applied())
                .filter(migration -> migration.getVersion() != null).count());
        try (Connection connection = connect(flyway)) {
            assertEquals(0, count(connection, "SELECT count(*) FROM accounts"));
            assertEquals(10, count(connection, "SELECT count(*) FROM information_schema.tables "
                    + "WHERE table_schema = current_schema() AND table_name IN "
                    + "('accounts','transactions','payments','ledger_entries','devices','users',"
                    + "'device_keys','payment_signatures','mesh_connections','packet_routes')"));
        }
        assertEquals(0, flyway.migrate().migrationsExecuted);
        flyway.validate();
    }

    @Test
    void demoSeedAndMigrationRerunsPreserveBalances() throws Exception {
        Flyway flyway = migrate(true);
        assertEquals(14, java.util.Arrays.stream(flyway.info().applied())
                .filter(migration -> migration.getVersion() != null).count());
        try (Connection connection = connect(flyway)) {
            assertEquals(5, count(connection, "SELECT count(*) FROM accounts"));
            assertEquals(5, count(connection, "SELECT count(*) FROM users WHERE status='ACTIVE'"));
            assertEquals(5, count(connection, "SELECT count(*) FROM devices WHERE status='ACTIVE'"));
            assertEquals(4, count(connection, "SELECT count(*) FROM mesh_connections WHERE status='ACTIVE'"));
            execute(connection, "UPDATE accounts SET balance=4321.00 WHERE vpa='alice@demo'");
            assertEquals(0, flyway.migrate().migrationsExecuted);
            try (var stream = getClass().getResourceAsStream("/db/demo-migration/V6__seed_demo_accounts.sql")) {
                assertNotNull(stream);
                execute(connection, new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            }
            assertEquals(4321, count(connection, "SELECT balance FROM accounts WHERE vpa='alice@demo'"));
            assertEquals(5, count(connection, "SELECT count(*) FROM accounts"));
        }
    }

    @Test
    void databaseEnforcesFinancialConstraintsAndIndexes() throws Exception {
        Flyway flyway = migrate(true, "6");
        try (Connection c = connect(flyway)) {
            rejected(c, "UPDATE accounts SET balance=-1 WHERE vpa='alice@demo'", "23514");
            rejected(c, "INSERT INTO accounts(vpa,balance) VALUES ('missing-name',1)", "23502");
            String insert = "INSERT INTO transactions(packet_hash,sender_vpa,receiver_vpa,amount,"
                    + "signed_at,settled_at,bridge_node_id,hop_count,status) VALUES "
                    + "('%s','alice@demo','%s',%s,now(),now(),'bridge',%s,'%s')";
            execute(c, insert.formatted("valid", "bob@demo", "1", "0", "SETTLED"));
            rejected(c, insert.formatted("valid", "bob@demo", "1", "0", "SETTLED"), "23505");
            rejected(c, insert.formatted("amount", "bob@demo", "0", "0", "SETTLED"), "23514");
            rejected(c, insert.formatted("hop", "bob@demo", "1", "-1", "SETTLED"), "23514");
            rejected(c, insert.formatted("self", "alice@demo", "1", "0", "SETTLED"), "23514");
            rejected(c, insert.formatted("status", "bob@demo", "1", "0", "INVALID"), "23514");
            rejected(c, "UPDATE transactions SET signed_at=NULL", "23502");
            long id = count(c, "SELECT id FROM transactions WHERE packet_hash='valid'");
            String ledger = "INSERT INTO ledger(transaction_id,account_vpa,direction,amount) "
                    + "VALUES (%s,'%s','%s',%s)";
            execute(c, ledger.formatted(id, "alice@demo", "DEBIT", "1"));
            execute(c, ledger.formatted(id, "bob@demo", "CREDIT", "1"));
            rejected(c, ledger.formatted(-1, "alice@demo", "DEBIT", "1"), "23503");
            rejected(c, ledger.formatted(id, "missing", "DEBIT", "1"), "23503");
            rejected(c, ledger.formatted(id, "alice@demo", "OTHER", "1"), "23514");
            rejected(c, ledger.formatted(id, "alice@demo", "DEBIT", "0"), "23514");
            rejected(c, "DELETE FROM transactions WHERE id=" + id, "23503");
            rejected(c, "DELETE FROM accounts WHERE vpa='alice@demo'", "23503");
            rejected(c, "INSERT INTO devices(device_id) VALUES ('missing-type')", "23502");
            execute(c, "INSERT INTO devices(device_id,device_type) VALUES ('phone','PHONE')");
            rejected(c, "INSERT INTO devices(device_id,device_type) VALUES ('phone','PHONE')", "23505");
            assertEquals(1, count(c, "SELECT count(*) FROM devices WHERE NOT is_bridge "
                    + "AND last_seen_at IS NULL AND created_at IS NOT NULL"));
            assertEquals(9, count(c, "SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() "
                    + "AND indexname IN ('idx_transactions_sender','idx_transactions_receiver',"
                    + "'idx_transactions_settled_at','idx_transactions_status','idx_ledger_account',"
                    + "'idx_ledger_transaction','idx_devices_last_seen',"
                    + "'transactions_packet_hash_unique','devices_pkey')"));
        }
    }

    private Flyway upgrade(Flyway old) {
        Flyway next = Flyway.configure().configuration(old.getConfiguration()).target("9").load();
        next.migrate();
        return next;
    }

    private void seedHistory(Connection c) throws SQLException {
        execute(c, """
                INSERT INTO accounts(vpa,holder_name,balance,version) VALUES
                ('alice@demo','Alice',4999,1),('bob@demo','Bob',1001,1)
                ON CONFLICT(vpa) DO UPDATE SET balance=excluded.balance, version=excluded.version;
                INSERT INTO transactions(packet_hash,sender_vpa,receiver_vpa,amount,signed_at,
                settled_at,bridge_node_id,hop_count,status) VALUES
                ('historical-settled','alice@demo','bob@demo',1,now(),now(),'bridge',1,'SETTLED'),
                ('historical-rejected','alice@demo','bob@demo',99999,now(),now(),'bridge',1,'REJECTED');
                """);
    }

    @Test
    void populatedProductionAndDemoHistoryBackfillsWithoutChangingBalances() throws Exception {
        for (boolean demo : new boolean[]{false, true}) {
            Flyway old = migrate(demo, demo ? "6" : "5");
            try (Connection c = connect(old)) {
                seedHistory(c);
                // Preserve an existing debit and reconstruct only its missing credit.
                execute(c, """
                        INSERT INTO ledger(transaction_id,account_vpa,direction,amount)
                        SELECT id,'alice@demo','DEBIT',amount FROM transactions WHERE status='SETTLED'
                        """);
                long preservedId = count(c, "SELECT id FROM ledger");
                Flyway next = upgrade(old);
                assertEquals(2, count(c, "SELECT count(*) FROM payments"));
                assertEquals(2, count(c, "SELECT count(*) FROM ledger_entries"));
                assertEquals(preservedId, count(c, "SELECT id FROM ledger_entries WHERE direction='DEBIT'"));
                assertEquals(4999, count(c, "SELECT balance FROM accounts WHERE vpa='alice@demo'"));
                assertEquals(1001, count(c, "SELECT balance FROM accounts WHERE vpa='bob@demo'"));
                assertEquals(2, count(c, "SELECT count(*) FROM payments WHERE nonce IS NULL"));
                assertEquals(0, count(c, "SELECT count(*) FROM transactions WHERE payment_id IS NULL"));
                assertEquals(0, next.migrate().migrationsExecuted);
            }
        }
    }

    @Test
    void inconsistentLegacyLedgerAbortsUpgradeWithoutDiscardingRecords() throws Exception {
        Flyway old = migrate(true, "6");
        try (Connection c = connect(old)) {
            seedHistory(c);
            execute(c, """
                    INSERT INTO ledger(transaction_id,account_vpa,direction,amount)
                    SELECT id,'bob@demo','DEBIT',amount FROM transactions WHERE status='SETTLED'
                    """);
            assertThrows(org.flywaydb.core.api.FlywayException.class, () -> upgrade(old));
            assertEquals(1, count(c, "SELECT count(*) FROM ledger"));
            assertEquals(4999, count(c, "SELECT balance FROM accounts WHERE vpa='alice@demo'"));
        }
    }

    @Test
    void missingHistoricalAccountAbortsBackfillWithoutChangingBalances() throws Exception {
        Flyway old = migrate(true, "6");
        try (Connection c = connect(old)) {
            seedHistory(c);
            execute(c, "DELETE FROM accounts WHERE vpa='bob@demo'");
            assertThrows(org.flywaydb.core.api.FlywayException.class, () -> upgrade(old));
            assertEquals(0, count(c, "SELECT count(*) FROM ledger"));
            assertEquals(4999, count(c, "SELECT balance FROM accounts WHERE vpa='alice@demo'"));
            assertEquals(2, count(c, "SELECT count(*) FROM transactions"));
        }
    }

    @Test
    void openingBaselinePreservesHistoryAndGuardsFinancialRecords() throws Exception {
        Flyway old = migrate(true, "6");
        try (Connection c = connect(old)) {
            seedHistory(c);
            Flyway v9 = upgrade(old);
            Flyway v10 = Flyway.configure().configuration(v9.getConfiguration()).target("10").load();
            v10.migrate();
            assertEquals(5000, count(c, "SELECT opening_balance FROM accounts WHERE vpa='alice@demo'"));
            assertEquals(1000, count(c, "SELECT opening_balance FROM accounts WHERE vpa='bob@demo'"));
            assertEquals(2500, count(c, "SELECT opening_balance FROM accounts WHERE vpa='carol@demo'"));
            assertEquals(4999, count(c, "SELECT balance FROM accounts WHERE vpa='alice@demo'"));
            assertEquals(2, count(c, "SELECT count(*) FROM ledger_entries"));
            rejected(c, "UPDATE accounts SET opening_balance=0 WHERE vpa='alice@demo'", "23514");
            rejected(c, "UPDATE ledger_entries SET created_at=now()", "23514");
            rejected(c, "DELETE FROM ledger_entries", "23514");
            rejected(c, "TRUNCATE ledger_entries", "23514");
            assertEquals(2, count(c, "SELECT count(*) FROM ledger_entries"));
            assertEquals(1, count(c, "SELECT count(*) FROM pg_indexes WHERE schemaname=current_schema() "
                    + "AND indexname='idx_ledger_entries_direction'"));
            assertEquals(0, v10.migrate().migrationsExecuted);
        }
    }

    @Test
    void legacyDeviceUpgradePreservesRowsWithoutGrantingTrust() throws Exception {
        Flyway old = migrate(false, "5");
        try (Connection c = connect(old)) {
            execute(c, "INSERT INTO devices(device_id,device_type,is_bridge) VALUES ('old-phone','PHONE',true)");
            Flyway upgraded = Flyway.configure().configuration(old.getConfiguration()).target("13").load();
            upgraded.migrate();
            assertEquals(1, count(c, "SELECT count(*) FROM devices WHERE device_id='old-phone' "
                    + "AND status='INACTIVE' AND trust_status='UNTRUSTED' AND internet_capability"));
            assertEquals(1, count(c, "SELECT count(*) FROM devices d JOIN users u ON u.id=d.user_id "
                    + "WHERE d.device_id='old-phone' AND u.status='INACTIVE'"));
            rejected(c, "INSERT INTO mesh_connections(source_device_id,target_device_id,status,link_type) "
                    + "SELECT id,id,'ACTIVE','BLUETOOTH' FROM devices WHERE device_id='old-phone'", "23514");
            rejected(c, "INSERT INTO packet_routes(packet_id,source_device_id,destination_device_id,hop_number,ttl_after_hop) "
                    + "SELECT 'packet',id,id,-1,0 FROM devices WHERE device_id='old-phone'", "23514");
        }
    }

    @Test
    void deferredConstraintsProtectCompleteAndMatchingLedgerPairs() throws Exception {
        Flyway old = migrate(true, "6");
        try (Connection c = connect(old)) {
            seedHistory(c);
            upgrade(old);
            rejected(c, "DELETE FROM ledger_entries WHERE direction='CREDIT'", "23514");
            rejected(c, "UPDATE ledger_entries SET amount=2 WHERE direction='DEBIT'", "23514");
            rejected(c, "UPDATE ledger_entries SET account_vpa='bob@demo' WHERE direction='DEBIT'", "23514");
            rejected(c, "UPDATE payments SET status='FAILED' WHERE status='SETTLED'", "23514");
            rejected(c, "UPDATE transactions SET payment_id=(SELECT id FROM payments WHERE status='REJECTED') "
                    + "WHERE status='SETTLED'", "23505");
            rejected(c, """
                    INSERT INTO ledger_entries(payment_id,transaction_id,account_vpa,direction,amount)
                    SELECT payment_id,id,'alice@demo','DEBIT',amount FROM transactions WHERE status='REJECTED'
                    """, "23514");
            rejected(c, """
                    INSERT INTO ledger_entries(payment_id,transaction_id,account_vpa,direction,amount)
                    SELECT payment_id,id,'alice@demo','DEBIT',amount FROM transactions WHERE status='SETTLED'
                    """, "23505");
            rejected(c, "UPDATE payments SET amount=0 WHERE status='REJECTED'", "23514");
            rejected(c, "UPDATE payments SET receiver_vpa=sender_vpa WHERE status='REJECTED'", "23514");
            rejected(c, "UPDATE payments SET status='UNKNOWN' WHERE status='REJECTED'", "23514");
            rejected(c, "UPDATE payments SET packet_hash='historical-settled' WHERE status='REJECTED'", "23505");
            rejected(c, "UPDATE payments SET payment_id=(SELECT payment_id FROM payments WHERE status='SETTLED') "
                    + "WHERE status='REJECTED'", "23505");
            assertEquals(2, count(c, "SELECT count(*) FROM ledger_entries"));
        }
    }
}
