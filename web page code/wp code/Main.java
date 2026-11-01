import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

public class Main {

    public static void main(String[] args) throws IOException {

        HttpServer server =
                HttpServer.create(new InetSocketAddress(8080), 0);

        server.createContext("/", exchange -> {

            String html = buildHtml();

            byte[] bytes =
                    html.getBytes(StandardCharsets.UTF_8);

            exchange.getResponseHeaders()
                    .set(
                            "Content-Type",
                            "text/html; charset=UTF-8"
                    );

            exchange.sendResponseHeaders(
                    200,
                    bytes.length
            );

            try (OutputStream os =
                         exchange.getResponseBody()) {

                os.write(bytes);
            }
        });

        // SQLite database-backed API endpoints. The existing webpage URL and UI stay unchanged.
        Database.initialize();

        server.createContext("/api/report", exchange -> {
            try {
                Map<String, String> data = readForm(exchange);
                long id = Database.createOutage(
                        data.getOrDefault("node", "Unknown area"),
                        data.getOrDefault("issue", "Unspecified issue"),
                        data.getOrDefault("remarks", ""),
                        "", "", data.getOrDefault("node", "Unknown area"),
                        null, "");
                sendJson(exchange, 200, "{\"success\":true,\"id\":" + id + "}");
            } catch (Exception e) { e.printStackTrace(); sendJson(exchange, 500, "{\"success\":false}"); }
        });

        server.createContext("/api/fault", exchange -> {
            try {
                Map<String, String> data = readForm(exchange);
                String lineId = data.getOrDefault("lineId", "");
                String nodeId = data.getOrDefault("nodeId", "");
                String area = data.getOrDefault("area", "Unknown area");
                long id = Database.createOutage(
                        area,
                        "Feeder Line Failure", "Triggered from network map",
                        lineId, nodeId, area,
                        "LINE_FAILURE_SELECTED",
                        "Failed line: " + lineId + " | Affected node: " + nodeId + " | Area: " + area);
                sendJson(exchange, 200, "{\"success\":true,\"id\":" + id + "}");
            } catch (Exception e) { e.printStackTrace(); sendJson(exchange, 500, "{\"success\":false}"); }
        });

        server.createContext("/api/dispatch", exchange -> {
            try {
                Map<String, String> data = readForm(exchange);
                Database.dispatch(
                        data.getOrDefault("lineId", ""),
                        data.getOrDefault("team", "Unknown team"),
                        data.getOrDefault("area", "Unknown area"),
                        data.getOrDefault("completionMinutes", "0"));
                sendJson(exchange, 200, "{\"success\":true}");
            } catch (Exception e) { e.printStackTrace(); sendJson(exchange, 500, "{\"success\":false}"); }
        });

        server.createContext("/api/complete", exchange -> {
            try {
                Map<String, String> data = readForm(exchange);
                Database.complete(
                        data.getOrDefault("lineId", ""),
                        data.getOrDefault("team", "Unknown team"),
                        data.getOrDefault("area", "Unknown area"),
                        data.getOrDefault("completedWithinTime", "false"));
                sendJson(exchange, 200, "{\"success\":true}");
            } catch (Exception e) { e.printStackTrace(); sendJson(exchange, 500, "{\"success\":false}"); }
        });

        server.createContext("/api/history", exchange -> {
            try { sendJson(exchange, 200, Database.historyJson()); }
            catch (Exception e) { e.printStackTrace(); sendJson(exchange, 500, "[]"); }
        });

        server.setExecutor(null);
        server.start();

        System.out.println(
                "=================================================="
        );

        System.out.println(
                " WEB APPLICATION DASHBOARD STARTED "
        );

        System.out.println(
                " URL: http://localhost:8080"
        );

        System.out.println(
                "=================================================="
        );
    }

    private static Map<String, String> readForm(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        String body;
        try (var input = exchange.getRequestBody()) {
            body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        Map<String, String> values = new LinkedHashMap<>();
        if (!body.isBlank()) {
            for (String pair : body.split("&")) {
                String[] parts = pair.split("=", 2);
                String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = parts.length > 1
                        ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
                values.put(key, value);
            }
        }
        return values;
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) { out.write(bytes); }
    }

    private static final class Database {
        private static final String URL = "jdbc:sqlite:outage_monitor.db";
        private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

        static Connection connect() throws SQLException { return DriverManager.getConnection(URL); }

        static void initialize() {
            try {
                Class.forName("org.sqlite.JDBC");
                try (Connection c = connect(); Statement st = c.createStatement()) {
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS outages (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, target TEXT NOT NULL, issue TEXT NOT NULL, remarks TEXT, " +
                            "line_id TEXT, node_id TEXT, area TEXT, team TEXT, completion_minutes INTEGER DEFAULT 0, " +
                            "status TEXT NOT NULL DEFAULT 'OPEN', created_at TEXT NOT NULL, dispatched_at TEXT, completed_at TEXT, " +
                            "completed_within_time TEXT)");
                    st.executeUpdate("CREATE TABLE IF NOT EXISTS event_history (" +
                            "id INTEGER PRIMARY KEY AUTOINCREMENT, outage_id INTEGER, event_type TEXT NOT NULL, " +
                            "details TEXT NOT NULL, event_time TEXT NOT NULL)");
                }
                System.out.println("SQLite connected: outage_monitor.db");
            } catch (Exception e) {
                throw new IllegalStateException("SQLite driver/database setup failed. Add sqlite-jdbc dependency in pom.xml.", e);
            }
        }

        static String now() { return LocalDateTime.now().format(TIME); }

        static long createOutage(String target, String issue, String remarks, String lineId, String nodeId, String area,
                                 String eventType, String eventDetails) throws SQLException {
            try (Connection c = connect(); PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO outages(target,issue,remarks,line_id,node_id,area,status,created_at) VALUES(?,?,?,?,?,?, 'OPEN',?)",
                    Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, target); ps.setString(2, issue); ps.setString(3, remarks);
                ps.setString(4, lineId); ps.setString(5, nodeId); ps.setString(6, area); ps.setString(7, now());
                ps.executeUpdate();
                long id;
                try (ResultSet rs = ps.getGeneratedKeys()) { id = rs.next() ? rs.getLong(1) : 0; }
                // Only log a selected failed line. Ordinary outage form submissions are not added to event_history.
                if (eventType != null && !eventType.isBlank()) {
                    event(c, id, eventType, eventDetails);
                }
                return id;
            }
        }

        static void dispatch(String lineId, String team, String area, String minutes) throws SQLException {
            try (Connection c = connect()) {
                long id = latestOpenId(c, lineId);
                if (id != 0) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE outages SET team=?, completion_minutes=?, status='DISPATCHED', dispatched_at=? WHERE id=?")) {
                        ps.setString(1, team); ps.setInt(2, parseInt(minutes)); ps.setString(3, now()); ps.setLong(4, id); ps.executeUpdate();
                    }
                }
                event(c, id == 0 ? null : id, "TEAM_DISPATCHED", team + " dispatched to " + area + " for " + minutes + " minute(s)");
            }
        }

        static void complete(String lineId, String team, String area, String withinTime) throws SQLException {
            try (Connection c = connect()) {
                long id = latestDispatchedId(c, lineId);
                if (id != 0) {
                    try (PreparedStatement ps = c.prepareStatement("UPDATE outages SET status='COMPLETED', completed_at=?, completed_within_time=? WHERE id=?")) {
                        ps.setString(1, now()); ps.setString(2, withinTime); ps.setLong(3, id); ps.executeUpdate();
                    }
                }
                event(c, id == 0 ? null : id, "REPAIR_COMPLETED", team + " completed repair at " + area + " (within time: " + withinTime + ")");
            }
        }

        private static int parseInt(String value) { try { return Integer.parseInt(value); } catch (Exception e) { return 0; } }
        private static long latestOpenId(Connection c, String lineId) throws SQLException {
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM outages WHERE line_id=? AND status='OPEN' ORDER BY id DESC LIMIT 1")) {
                ps.setString(1, lineId); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0; }
            }
        }
        private static long latestDispatchedId(Connection c, String lineId) throws SQLException {
            try (PreparedStatement ps = c.prepareStatement("SELECT id FROM outages WHERE line_id=? AND status='DISPATCHED' ORDER BY id DESC LIMIT 1")) {
                ps.setString(1, lineId); try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0; }
            }
        }
        private static void event(Connection c, Long outageId, String type, String details) throws SQLException {
            try (PreparedStatement ps = c.prepareStatement("INSERT INTO event_history(outage_id,event_type,details,event_time) VALUES(?,?,?,?)")) {
                if (outageId == null) ps.setNull(1, java.sql.Types.INTEGER); else ps.setLong(1, outageId);
                ps.setString(2, type); ps.setString(3, details); ps.setString(4, now()); ps.executeUpdate();
            }
        }
        static String historyJson() throws SQLException {
            StringBuilder out = new StringBuilder("[");
            try (Connection c = connect(); Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("SELECT event_type,details,event_time FROM event_history ORDER BY id DESC LIMIT 100")) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) out.append(','); first = false;
                    out.append("{\"type\":\"").append(jsonEscape(rs.getString(1)))
                            .append("\",\"details\":\"").append(jsonEscape(rs.getString(2)))
                            .append("\",\"time\":\"").append(jsonEscape(rs.getString(3))).append("\"}");
                }
            }
            return out.append(']').toString();
        }
        private static String jsonEscape(String s) {
            if (s == null) return "";
            return s.replace("\\", "\\\\").replace("\"", "\\\"")
                    .replace("\n", "\\n").replace("\r", "\\r");
        }
    }

    /*
     * ============================================================
     * BUILD HTML
     * ============================================================
     *
     * The HTML is intentionally divided into multiple text blocks.
     *
     * DO NOT combine these blocks into one giant Java text block.
     * This prevents:
     *
     *     java: constant string too long
     *
     * Each append() contains a separate Java string constant.
     */
    private static String buildHtml() {

        StringBuilder html =
                new StringBuilder(120000);

        // =========================================================
        // PART 1 - HTML HEAD + CSS
        // =========================================================

        html.append("""
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Smart Electricity Outage Monitoring System</title>

                    <style>
                        * {
                            box-sizing: border-box;
                            margin: 0;
                            padding: 0;
                            font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        }

                        body {
                            background-color: #0b132b;
                            color: #f8fafc;
                        }

                        .navbar {
                            background: #1c2541;
                            padding: 16px 30px;
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                            border-bottom: 1px solid #3a506b;
                        }

                        .navbar h1 {
                            font-size: 20px;
                            color: #48cae4;
                            display: flex;
                            align-items: center;
                            gap: 10px;
                        }

                        .status-badge {
                            background: #10b981;
                            color: white;
                            padding: 5px 12px;
                            border-radius: 20px;
                            font-size: 12px;
                            font-weight: 600;
                        }

                        .main-container {
                            padding: 25px 30px;
                            max-width: 1400px;
                            margin: 0 auto;
                        }

                        .metrics-grid {
                            display: grid;
                            grid-template-columns: repeat(4, 1fr);
                            gap: 20px;
                            margin-bottom: 25px;
                        }

                        .metric-card {
                            background: #1c2541;
                            padding: 20px;
                            border-radius: 12px;
                            border: 1px solid #3a506b;
                        }

                        .metric-card p {
                            font-size: 13px;
                            color: #94a3b8;
                            margin-bottom: 6px;
                        }

                        .metric-card h3 {
                            font-size: 24px;
                            color: #f8fafc;
                        }

                        .content-grid {
                            display: grid;
                            grid-template-columns: 2fr 1.2fr;
                            gap: 25px;
                        }

                        .card {
                            background: #1c2541;
                            border-radius: 12px;
                            padding: 20px;
                            border: 1px solid #3a506b;
                        }

                        .card-title {
                            font-size: 16px;
                            font-weight: 600;
                            color: #cbd5e1;
                            margin-bottom: 15px;
                            display: flex;
                            justify-content: space-between;
                            align-items: center;
                        }

                        .network-box {
                            background: #070e20;
                            border-radius: 10px;
                            border: 1px dashed #3a506b;
                            padding: 10px;
                            display: flex;
                            justify-content: center;
                            overflow: hidden;
                        }

                        .form-group {
                            margin-bottom: 12px;
                        }

                        .form-group label {
                            display: block;
                            font-size: 12px;
                            color: #94a3b8;
                            margin-bottom: 5px;
                        }

                        .form-group select,
                        .form-group input {
                            width: 100%;
                            padding: 10px;
                            border-radius: 6px;
                            border: 1px solid #3a506b;
                            background: #0b132b;
                            color: white;
                            font-size: 13px;
                        }

                        .btn-danger,
                        .btn-submit,
                        .btn-repair {
                            width: 100%;
                            color: white;
                            padding: 11px;
                            border: none;
                            border-radius: 6px;
                            font-weight: 600;
                            cursor: pointer;
                            transition: 0.2s;
                            margin-bottom: 6px;
                        }

                        .btn-danger {
                            background: #e63946;
                        }

                        .btn-danger:hover {
                            background: #d62828;
                        }

                        .btn-submit {
                            background: #0077b6;
                        }

                        .btn-submit:hover {
                            background: #023e8a;
                        }

                        .btn-repair {
                            background: #10b981;
                            display: none;
                        }

                        .btn-repair:hover {
                            background: #059669;
                        }

                        #dangerAlert,
                        #dispatchAlert,
                        #repairSuccessAlert {
                            display: none;
                            margin-top: 10px;
                            padding: 12px;
                            border-radius: 8px;
                            font-size: 13px;
                            line-height: 1.5;
                            animation: fadeIn 0.3s ease-in-out;
                        }

                        #dangerAlert {
                            background: rgba(230, 57, 70, 0.2);
                            border: 2px solid #e63946;
                            color: #ffb4a2;
                        }

                        #dispatchAlert {
                            background: rgba(0, 119, 182, 0.18);
                            border: 1px solid #38bdf8;
                            color: #bae6fd;
                        }

                        #repairSuccessAlert {
                            background: rgba(16, 185, 129, 0.15);
                            border: 2px solid #10b981;
                            color: #a7f3d0;
                        }

                        @keyframes fadeIn {
                            from {
                                opacity: 0;
                                transform: translateY(-5px);
                            }

                            to {
                                opacity: 1;
                                transform: translateY(0);
                            }
                        }

                        .line-cut {
                            stroke: #e63946 !important;
                            stroke-dasharray: 6 !important;
                            animation: blink 1s infinite alternate;
                        }

                        .node-danger {
                            fill: #991b1b !important;
                            stroke: #ef4444 !important;
                        }

                        @keyframes blink {
                            from {
                                opacity: 0.4;
                            }

                            to {
                                opacity: 1;
                            }
                        }

                        .table-container {
                            margin-top: 25px;
                        }

                        table {
                            width: 100%;
                            border-collapse: collapse;
                            text-align: left;
                            font-size: 13px;
                            margin-top: 10px;
                        }

                        th {
                            background: #0b132b;
                            padding: 12px;
                            color: #94a3b8;
                            font-weight: 600;
                            border-bottom: 1px solid #3a506b;
                        }

                        td {
                            padding: 12px;
                            border-bottom: 1px solid #3a506b;
                            color: #cbd5e1;
                        }

                        .badge-ok {
                            color: #34d399;
                            font-weight: 600;
                        }

                        .badge-offline {
                            color: #f87171;
                            font-weight: 600;
                        }

                        .alternate-line {
                            stroke: #38bdf8 !important;
                            stroke-width: 6 !important;
                            stroke-dasharray: 10 5;
                            animation: alternateFlow 0.8s linear infinite;
                        }

                        .power-safe {
                            stroke: #38bdf8 !important;
                            stroke-width: 5 !important;
                            stroke-dasharray: none !important;
                            animation: none !important;
                        }

                        @keyframes alternateFlow {
                            to {
                                stroke-dashoffset: -15;
                            }
                        }

                        #alternateAlert {
                            display: none;
                            background: rgba(56, 189, 248, 0.15);
                            border: 1px solid #38bdf8;
                            color: #bae6fd;
                            padding: 12px;
                            margin-top: 10px;
                            border-radius: 8px;
                            line-height: 1.6;
                            font-size: 13px;
                        }
                    </style>
                </head>

                <body>

                    <div class="navbar">
                        <h1>⚡ SMART ELECTRICITY OUTAGE MONITOR</h1>

                        <span class="status-badge" id="gridStatus">
                            ● Grid Online
                        </span>
                    </div>

                    <div class="main-container">

                        <div class="metrics-grid">

                            <div class="metric-card">
                                <p>Total Sub-stations</p>
                                <h3>2 Units (S1, S2)</h3>
                            </div>

                            <div class="metric-card">
                                <p>Active Distribution Nodes</p>
                                <h3 id="activeAreas">8 Areas</h3>
                            </div>

                            <div class="metric-card">
                                <p>Critical Facilities</p>
                                <h3 id="criticalFacilities">2 Protected</h3>
                            </div>

                            <div class="metric-card">
                                <p>Current System Status</p>

                                <h3 id="systemHealthText"
                                    style="color: #34d399;">
                                    Healthy
                                </h3>
                            </div>

                        </div>

                        <div class="content-grid">

                            <div class="card">

                                <div class="card-title">
                                    <span>
                                        Live Network Distribution Topology
                                    </span>
                                </div>

                                <div class="network-box">

                                    <svg width="100%"
                                         height="450"
                                         viewBox="0 0 650 450">

                                        <!-- Feeder / Power Lines -->

                                        <line id="line_S1_A"
                                              x1="60" y1="90"
                                              x2="60" y2="160"
                                              stroke="#10b981"
                                              stroke-width="4" />

                                        <line id="line_A_B"
                                              x1="85" y1="180"
                                              x2="215" y2="180"
                                              stroke="#10b981"
                                              stroke-width="4" />

                                        <line id="line_Hosp_B"
                                              x1="240" y1="95"
                                              x2="240" y2="155"
                                              stroke="#10b981"
                                              stroke-width="4" />

                                        <line id="line_B_D"
                                              x1="265" y1="180"
                                              x2="335" y2="180"
                                              stroke="#10b981"
                                              stroke-width="4" />

                                        <line id="line_D_E"
                                              x1="385" y1="180"
                                              x2="455" y2="180"
                                              stroke="#10b981"
                                              stroke-width="4" />

                                        <line id="line_B_H"
                                              x1="240" y1="205"
                                              x2="240" y2="275"
                                              stroke="#10b981"
                                              stroke-width="4" />

                                        <line id="line_D_G"
                                              x1="360" y1="205"
                                              x2="360" y2="275"
                                              stroke="#10b981"
                                              stroke-width="4" />

                                        <line id="line_H_G"
                                              x1="265" y1="300"
                                              x2="335" y2="300"
                                              stroke="#10b981"
                                              stroke-width="4" />

                                        <line id="line_H_I"
                                              x1="240" y1="325"
                                              x2="240" y2="385"
                                              stroke="#10b981"
                                              stroke-width="4" />

                                        <line id="line_I_J"
                                              x1="262" y1="410"
                                              x2="398" y2="410"
                                              stroke="#10b981"
                                              stroke-width="4" />

                                        <line id="line_G_Emerg"
                                              x1="385" y1="300"
                                              x2="425" y2="300"
                                              stroke="#10b981"
                                              stroke-width="4" />

                                        <line id="line_Emerg_S2"
                                              x1="472" y1="322"
                                              x2="570" y2="350"
                                              stroke="#10b981"
                                              stroke-width="4" />

                                        <!-- Substation S1 -->

                                        <rect x="30" y="50"
                                              width="60"
                                              height="40"
                                              rx="8"
                                              fill="#14532d"
                                              stroke="#22c55e"
                                              stroke-width="2"/>

                                        <text x="60" y="75"
                                              fill="#f8fafc"
                                              font-size="14"
                                              font-weight="bold"
                                              text-anchor="middle">
                                            S1
                                        </text>

                                        <!-- City Hospital -->

                                        <rect id="node_Hospital" x="180" y="45"
                                              width="120"
                                              height="50"
                                              rx="8"
                                              fill="#581c87"
                                              stroke="#c084fc"
                                              stroke-width="2"/>

                                        <text x="240" y="75"
                                              fill="#e9d5ff"
                                              font-size="12"
                                              font-weight="bold"
                                              text-anchor="middle">
                                            🏥 City Hospital
                                        </text>

                                        <!-- Emergency Centre -->
                                        <rect x="425" y="278"
                                              width="125"
                                              height="44"
                                              rx="8"
                                              fill="#581c87"
                                              stroke="#c084fc"
                                              stroke-width="2"/>
                
                                        <text x="487.5" y="305"
                                              fill="#e9d5ff"
                                              font-size="11"
                                              font-weight="bold"
                                              text-anchor="middle">
                                            🚨 Emergency Centre
                                        </text>

                                        <!-- Substation S2 -->

                                        <rect x="540" y="350"
                                              width="60"
                                              height="40"
                                              rx="8"
                                              fill="#14532d"
                                              stroke="#22c55e"
                                              stroke-width="2"/>

                                        <text x="570" y="375"
                                              fill="#f8fafc"
                                              font-size="14"
                                              font-weight="bold"
                                              text-anchor="middle">
                                            S2
                                        </text>

                                        <!-- Nodes -->

                                        <circle id="node_A"
                                                cx="60" cy="180"
                                                r="22"
                                                fill="#1e3a8a"
                                                stroke="#38bdf8"
                                                stroke-width="2"/>

                                        <text x="60" y="186"
                                              fill="white"
                                              font-weight="bold"
                                              font-size="14"
                                              text-anchor="middle">
                                            A
                                        </text>

                                        <circle id="node_B"
                                                cx="240" cy="180"
                                                r="22"
                                                fill="#1e3a8a"
                                                stroke="#38bdf8"
                                                stroke-width="2"/>

                                        <text x="240" y="186"
                                              fill="white"
                                              font-weight="bold"
                                              font-size="14"
                                              text-anchor="middle">
                                            B
                                        </text>

                                        <circle id="node_D"
                                                cx="360" cy="180"
                                                r="22"
                                                fill="#1e3a8a"
                                                stroke="#38bdf8"
                                                stroke-width="2"/>

                                        <text x="360" y="186"
                                              fill="white"
                                              font-weight="bold"
                                              font-size="14"
                                              text-anchor="middle">
                                            D
                                        </text>

                                        <circle id="node_E"
                                                cx="480" cy="180"
                                                r="22"
                                                fill="#1e3a8a"
                                                stroke="#38bdf8"
                                                stroke-width="2"/>

                                        <text x="480" y="186"
                                              fill="white"
                                              font-weight="bold"
                                              font-size="14"
                                              text-anchor="middle">
                                            E
                                        </text>

                                        <circle id="node_H"
                                                cx="240" cy="300"
                                                r="22"
                                                fill="#1e3a8a"
                                                stroke="#38bdf8"
                                                stroke-width="2"/>

                                        <text x="240" y="306"
                                              fill="white"
                                              font-weight="bold"
                                              font-size="14"
                                              text-anchor="middle">
                                            H
                                        </text>

                                        <circle id="node_G"
                                                cx="360" cy="300"
                                                r="22"
                                                fill="#1e3a8a"
                                                stroke="#38bdf8"
                                                stroke-width="2"/>

                                        <text x="360" y="306"
                                              fill="white"
                                              font-weight="bold"
                                              font-size="14"
                                              text-anchor="middle">
                                            G
                                        </text>

                                        <circle id="node_I"
                                                cx="240" cy="410"
                                                r="22"
                                                fill="#1e3a8a"
                                                stroke="#38bdf8"
                                                stroke-width="2"/>

                                        <text x="240" y="416"
                                              fill="white"
                                              font-weight="bold"
                                              font-size="14"
                                              text-anchor="middle">
                                            I
                                        </text>

                                        <circle id="node_J"
                                                cx="420" cy="410"
                                                r="22"
                                                fill="#1e3a8a"
                                                stroke="#38bdf8"
                                                stroke-width="2"/>

                                        <text x="420" y="416"
                                              fill="white"
                                              font-weight="bold"
                                              font-size="14"
                                              text-anchor="middle">
                                            J
                                        </text>

                                    </svg>

                                </div>

                            </div>
                """);

        // =========================================================
        // PART 2 - SIMULATION / REPAIR / REPORT UI
        // =========================================================

        html.append("""
                            <div class="card">

                                <div class="card-title">
                                    Simulate Feeder / Power Line Break
                                </div>

                                <div class="form-group">

                                    <label>
                                        Select Feeder / Power Line to Cut
                                    </label>

                                    <select id="lineSelect"
                                            multiple
                                            size="6"
                                            title="Select one or more lines (use Ctrl to select multiple)">

                                        <option value="line_S1_A|node_A|Area A">
                                            primary station ➔ Area A
                                        </option>

                                        <option value="line_A_B|node_B|Area B">
                                            Feeder Area A ➔ Area B
                                        </option>

                                        <option value="line_Hosp_B|node_B|City Hospital">
                                            City Hospital ➔ Area B
                                        </option>

                                        <option value="line_B_D|node_D|Area D">
                                            Feeder Area B ➔ Area D
                                        </option>

                                        <option value="line_D_E|node_E|Area E">
                                            Feeder Area D ➔ Area E
                                        </option>

                                        <option value="line_B_H|node_H|Area H">
                                            Approved Backup Area B ➔ Area H
                                        </option>

                                        <option value="line_D_G|node_G|Area G">
                                            Approved Backup Area D ➔ Area G
                                        </option>

                                        <option value="line_H_I|node_I|Area I">
                                            Feeder Area H ➔ Area I
                                        </option>

                                        <option value="line_I_J|node_J|Area J">
                                            Feeder Area I ➔ Area J
                                        </option>

                                        <option value="line_G_Emerg|node_G|Emergency Centre">
                                            Emergency Centre Feeder ➔ Node G
                                        </option>

                                        <option value="line_Emerg_S2|node_G|Substation S2">
                                            s2 ➔ Emergency Centre
                                        </option>

                                    </select>

                                </div>

                                <button type="button"
                                        class="btn-danger"
                                        onclick="triggerLineCut()">

                                    ⚡ Trigger Feeder Line Cut (Outage)

                                </button>

                                <div id="dangerAlert">

                                    <strong style="color: #ff4d4d; font-size: 14px;">
                                        ⚠️ DANGER: POWER LINE FAULT DETECTED!
                                    </strong>

                                    <br>

                                    <span id="dangerMessage"></span>

                                </div>

                                <div id="alternateAlert">

                                    <strong>⚡ Alternate Power Supply</strong>

                                    <p id="alternateMessage"
                                       style="margin: 8px 0 12px;">
                                    </p>

                                    <button type="button"
                                            class="btn-submit"
                                            onclick="connectAlternateLine()">

                                        ✔ Activate Approved Backup

                                    </button>

                                    <button type="button"
                                            class="btn-danger"
                                            onclick="cancelAlternateLine()">

                                        ✖ No, Cancel

                                    </button>

                                </div>

                                <div style="margin: 20px 0;
                                            border-top: 1px solid #3a506b;">
                                </div>

                                <div class="card-title">
                                    Dispatch Repair Team
                                </div>

                                <div class="form-group">

                                    <label>Select Repair Team</label>

                                    <select id="repairTeam">

                                        <option value="Team Alpha">
                                            Team Alpha
                                        </option>

                                        <option value="Team Beta">
                                            Team Beta
                                        </option>

                                        <option value="Team Gamma">
                                            Team Gamma
                                        </option>

                                    </select>

                                </div>

                                <div class="form-group">

                                    <label>
                                        Work Completion Time (minutes)
                                    </label>

                                    <input type="number"
                                           id="completionTime"
                                           min="1"
                                           placeholder="Enter time in minutes">

                                </div>

                                <button type="button"
                                        class="btn-submit"
                                        onclick="dispatchRepairTeam()">

                                    🚑 Dispatch Selected Team

                                </button>

                                <div id="dispatchAlert">

                                    <strong style="color: #38bdf8;">
                                        🚑 Repair Team Dispatched!
                                    </strong>

                                    <br>

                                    <span id="dispatchDetails"></span>

                                </div>

                                <button type="button"
                                        id="completeRepairBtn"
                                        class="btn-repair"
                                        onclick="completeRepair()">

                                    🔧 Mark Repair Completed

                                </button>

                                <div id="repairSuccessAlert">

                                    <strong style="color: #34d399; font-size: 14px;">
                                        ✔ REPAIR COMPLETED SUCCESSFULLY!
                                    </strong>

                                    <br>

                                    <span id="repairSuccessDetails"></span>

                                </div>

                                <div style="margin-top: 20px;
                                            border-top: 1px solid #3a506b;
                                            padding-top: 12px;">

                                    <div class="card-title"
                                         style="margin-bottom: 8px;">

                                        Report / Trigger Outage

                                    </div>

                                    <form id="outageForm"
                                          onsubmit="handleDispatch(event)">

                                        <div class="form-group">

                                            <label>
                                                Select Target Area Node
                                            </label>

                                            <select id="targetNode">

                                                <option value="Area A">
                                                    Area A
                                                </option>

                                                <option value="Area B">
                                                    Area B
                                                </option>

                                                <option value="City Hospital">
                                                    City Hospital
                                                </option>

                                                <option value="Area D">
                                                    Area D
                                                </option>

                                                <option value="Area E">
                                                    Area E
                                                </option>

                                                <option value="Area G">
                                                    Area G
                                                </option>

                                                <option value="Area H">
                                                    Area H
                                                </option>

                                                <option value="Area I">
                                                    Area I
                                                </option>

                                                <option value="Area J">
                                                    Area J
                                                </option>

                                                <option value="Emergency Centre">
                                                    Emergency Centre
                                                </option>

                                                <option value="Substation S2">
                                                    Substation S2
                                                </option>

                                            </select>

                                        </div>

                                        <div class="form-group">

                                            <label>
                                                Issue Category
                                            </label>

                                            <select id="issueType">

                                                <option value="Transformer Failure">
                                                    Transformer Failure
                                                </option>

                                                <option value="Feeder Line Failure">
                                                    Feeder Line Failure
                                                </option>

                                                <option value="Line Maintenance">
                                                    Line Maintenance
                                                </option>

                                                <option value="Voltage Fluctuation">
                                                    Voltage Fluctuation
                                                </option>

                                            </select>

                                        </div>

                                        <div class="form-group">

                                            <label>Remarks</label>

                                            <input type="text"
                                                   id="remarks"
                                                   placeholder="e.g. Tree fall near pole">

                                        </div>

                                        <button type="submit"
                                                class="btn-submit">

                                            Report Outage

                                        </button>

                                    </form>

                                </div>

                                <div id="dispatchReportAlert"
                                     style="display:none;
                                            margin-top:10px;
                                            padding:12px;
                                            background:rgba(16,185,129,0.15);
                                            border:1px solid #10b981;
                                            border-radius:8px;
                                            color:#a7f3d0;
                                            font-size:13px;
                                            line-height:1.5;">

                                    <strong>
                                        ✔ Outage Report Registered!
                                    </strong>

                                    <br>

                                    <span id="alertDetails"></span>

                                </div>

                                <div style="margin-top: 20px;
                                            border-top: 1px solid #3a506b;
                                            padding-top: 12px;">

                                    <div class="card-title"
                                         style="margin-bottom: 8px;">

                                        Network Log

                                    </div>

                                    <p id="liveLog"
                                       style="font-size: 12px;
                                              color: #94a3b8;
                                              line-height: 1.6;">

                                        ✔ Substation S1 connected to Node A
                                        <br>

                                        ✔ Emergency Centre connected to Node G
                                        <br>

                                        ✔ Substation S2 connected to Emergency Centre
                                        <br>

                                        ✔ Node I connected to Node J
                                        <br>

                                        ✔ Hospital power line isolated & secure

                                    </p>

                                </div>

                            </div>

                        </div>
                """);

        // =========================================================
        // PART 3 - TELEMETRY TABLE
        // =========================================================

        html.append("""
                        <div class="card table-container">

                            <div class="card-title">
                                Live Node Telemetry Status
                            </div>

                            <table>

                                <thead>

                                    <tr>
                                        <th>Node ID</th>
                                        <th>Feed Type</th>
                                        <th>Linked Connections</th>
                                        <th>Load (%)</th>
                                        <th>Current Status</th>
                                    </tr>

                                </thead>

                                <tbody>

                                    <tr>

                                        <td>
                                            <strong>Node B</strong>
                                        </td>

                                        <td>
                                            Primary Hub
                                        </td>

                                        <td>
                                            Area A, City Hospital, Area D, Area H
                                        </td>

                                        <td>
                                            74%
                                        </td>

                                        <td>
                                            <span class="badge-ok">
                                                ● Operational
                                            </span>
                                        </td>

                                    </tr>

                                    <tr>

                                        <td>
                                            <strong>Node G</strong>
                                        </td>

                                        <td>
                                            Distributor
                                        </td>

                                        <td>
                                            Emergency Centre, Area H, Area D
                                        </td>

                                        <td>
                                            61%
                                        </td>

                                        <td>
                                            <span class="badge-ok">
                                                ● Operational
                                            </span>
                                        </td>

                                    </tr>

                                    <tr>

                                        <td>
                                            <strong>Node I</strong>
                                        </td>

                                        <td>
                                            End Terminal
                                        </td>

                                        <td>
                                            Area H, Area J
                                        </td>

                                        <td>
                                            42%
                                        </td>

                                        <td>
                                            <span class="badge-ok">
                                                ● Operational
                                            </span>
                                        </td>

                                    </tr>

                                    <tr>

                                        <td>
                                            <strong>Node J</strong>
                                        </td>

                                        <td>
                                            End Terminal
                                        </td>

                                        <td>
                                            Area I
                                        </td>

                                        <td>
                                            35%
                                        </td>

                                        <td>
                                            <span class="badge-ok">
                                                ● Operational
                                            </span>
                                        </td>

                                    </tr>

                                </tbody>

                            </table>

                        </div>

                    </div>

                    <script>
                """);

        // =========================================================
        // PART 4 - JAVASCRIPT STATE + GRAPH + ALTERNATE ROUTES
        // =========================================================

        html.append("""
                        let selectedLineId = null;
                        let selectedNodeId = null;
                        let affectedArea = null;
                        let repairTeamDispatched = false;

                        let activeAreas = 8;
                        let cutLines = new Set();
                        let criticalFacilities = 2;
                        let repairDeadline = null;
                        let repairTimer = null;

                        // Stores every currently broken line.
                        let pendingFaults = [];

                        // Alternate-feed confirmation and active backup route.
                        let alternateTarget = null;
                        let alternateRoute = [];

                        const networkGraph = {
                            S1: [
                                { node: 'A', line: 'line_S1_A' }
                            ],

                            A: [
                                { node: 'S1', line: 'line_S1_A' },
                                { node: 'B', line: 'line_A_B' }
                            ],

                            B: [
                                { node: 'A', line: 'line_A_B' },
                                { node: 'D', line: 'line_B_D' },
                                { node: 'H', line: 'line_B_H' },
                                { node: 'Hospital', line: 'line_Hosp_B' }
                            ],

                            Hospital: [
                                { node: 'B', line: 'line_Hosp_B' }
                            ],

                            D: [
                                { node: 'B', line: 'line_B_D' },
                                { node: 'E', line: 'line_D_E' },
                                { node: 'G', line: 'line_D_G' }
                            ],

                            E: [
                                { node: 'D', line: 'line_D_E' }
                            ],

                            H: [
                                { node: 'B', line: 'line_B_H' },
                                { node: 'G', line: 'line_H_G' },
                                { node: 'I', line: 'line_H_I' }
                            ],

                            G: [
                                { node: 'D', line: 'line_D_G' },
                                { node: 'H', line: 'line_H_G' },
                                { node: 'Emergency', line: 'line_G_Emerg' }
                            ],

                            I: [
                                { node: 'H', line: 'line_H_I' },
                                { node: 'J', line: 'line_I_J' }
                            ],

                            J: [
                                { node: 'I', line: 'line_I_J' }
                            ],

                            Emergency: [
                                { node: 'G', line: 'line_G_Emerg' },
                                { node: 'S2', line: 'line_Emerg_S2' }
                            ],

                            S2: [
                                { node: 'Emergency', line: 'line_Emerg_S2' }
                            ]
                        };

                        function findAlternateRoute(start, target) {

                            const queue = [
                                {
                                    node: start,
                                    path: []
                                }
                            ];

                            const visited =
                                new Set([start]);

                            while (queue.length > 0) {

                                const current =
                                    queue.shift();

                                if (current.node === target) {
                                    return current.path;
                                }

                                for (const edge of
                                    (networkGraph[current.node] || [])) {

                                    if (visited.has(edge.node) ||
                                        cutLines.has(edge.line)) {
                                        continue;
                                    }

                                    visited.add(edge.node);

                                    queue.push({
                                        node: edge.node,
                                        path: [
                                            ...current.path,
                                            edge.line
                                        ]
                                    });
                                }
                            }

                            return null;
                        }

                        function askAlternateConnection(fault) {

                            /*
                             * SPECIAL CASE 1:
                             *
                             * S1 -> Area A fails.
                             *
                             * Alternate route:
                             *
                             * S2 -> Emergency -> G -> H -> B -> A
                             *
                             * Branches:
                             *
                             * B -> D -> E
                             * B -> City Hospital
                             */
                            if (fault.lineId === 'line_S1_A') {

                                const backupLines = [
                                    'line_Emerg_S2',
                                    'line_G_Emerg',
                                    'line_H_G',
                                    'line_B_H',
                                    'line_A_B',
                                    'line_B_D',
                                    'line_D_E',
                                    'line_Hosp_B'
                                ];

                                if (backupLines.some(
                                    id => cutLines.has(id)
                                )) {

                                    alternateTarget = null;

                                    document
                                        .getElementById('alternateAlert')
                                        .style.display = 'none';

                                    document
                                        .getElementById('liveLog')
                                        .innerHTML =
                                        '⚠ S2 alternate supply cannot be activated because one of the required backup lines is also cut.<br>' +
                                        document.getElementById('liveLog').innerHTML;

                                    return;
                                }

                                alternateTarget = {

                                    lineIds: backupLines,

                                    label:
                                        'S2 → Emergency → G → H → B → A; branches B → D → E and B → City Hospital',

                                    areas: [
                                        'Area A',
                                        'Area B',
                                        'City Hospital',
                                        'Area D',
                                        'Area E'
                                    ]
                                };

                                document
                                    .getElementById('alternateMessage')
                                    .innerText =
                                    'S1 → Area A feeder line is cut. Do you want to supply alternate current from S2 through Emergency → G → H → B → A, with B → D → E and B → City Hospital?';

                                document
                                    .getElementById('alternateAlert')
                                    .style.display = 'block';

                                return;
                            }

                            /*
                             * SPECIAL CASE 2:
                             *
                             * S2 -> Emergency Centre fails.
                             *
                             * Alternate route:
                             *
                             * S1 -> A -> B -> D -> G -> Emergency Centre
                             *
                             * Branch:
                             *
                             * G -> H -> I -> J
                             */
                            if (fault.lineId === 'line_Emerg_S2') {

                                const backupLines = [
                                    'line_S1_A',
                                    'line_A_B',
                                    'line_B_D',
                                    'line_D_G',
                                    'line_G_Emerg',
                                    'line_H_G',
                                    'line_H_I',
                                    'line_I_J'
                                ];

                                if (backupLines.some(
                                    id => cutLines.has(id)
                                )) {

                                    alternateTarget = null;

                                    document
                                        .getElementById('alternateAlert')
                                        .style.display = 'none';

                                    document
                                        .getElementById('liveLog')
                                        .innerHTML =
                                        '⚠ S1 alternate supply cannot be activated because one of the required lines is also cut.<br>' +
                                        document.getElementById('liveLog').innerHTML;

                                    return;
                                }

                                alternateTarget = {

                                    lineIds: backupLines,

                                    label:
                                        'S1 → A → B → D → G → Emergency Centre; branch G → H → I → J',

                                    areas: [
                                        'Emergency Centre',
                                        'Area G',
                                        'Area H',
                                        'Area I',
                                        'Area J'
                                    ]
                                };

                                document
                                    .getElementById('alternateMessage')
                                    .innerText =
                                    'S2 → Emergency Centre feeder is cut. Do you want to supply alternate current from S1 through A → B → D → G → Emergency Centre, with G → H → I → J?';

                                document
                                    .getElementById('alternateAlert')
                                    .style.display = 'block';

                                return;
                            }

                            /*
                             * Existing alternate-feed behaviour
                             * for all other line failures.
                             *
                             * line_Emerg_S2 is intentionally NOT
                             * included here because it has its own
                             * special-case route above.
                             */
                            const backupMap = {

                                line_D_E: {
                                    target: 'G',
                                    line: 'line_D_G',
                                    label: 'Area D → Area G'
                                },

                                line_G_Emerg: {
                                    target: 'G',
                                    line: 'line_D_G',
                                    label: 'Area D → Area G'
                                },

                                line_H_I: {
                                    target: 'H',
                                    line: 'line_B_H',
                                    label: 'Area B → Area H'
                                },

                                line_I_J: {
                                    target: 'H',
                                    line: 'line_B_H',
                                    label: 'Area B → Area H'
                                },

                                line_B_H: {
                                    target: 'G',
                                    line: 'line_D_G',
                                    label: 'Area D → Area G'
                                },

                                line_D_G: {
                                    target: 'H',
                                    line: 'line_B_H',
                                    label: 'Area B → Area H'
                                }
                            };

                            const backup =
                                backupMap[fault.lineId];

                            if (!backup ||
                                cutLines.has(backup.line)) {

                                alternateTarget = null;

                                document
                                    .getElementById('alternateAlert')
                                    .style.display = 'none';

                                return;
                            }

                            alternateTarget = {

                                lineId: backup.line,
                                label: backup.label,
                                target: backup.target
                            };

                            document
                                .getElementById('alternateMessage')
                                .innerText =
                                'Power line fault detected at ' +
                                fault.area +
                                '. The approved backup connection is ' +
                                backup.label +
                                '. Do you want to activate this alternate connection?';

                            document
                                .getElementById('alternateAlert')
                                .style.display = 'block';
                        }

                        function markDangerGroup(lineId) {

                            const groups = {

                                line_Emerg_S2: {

                                    nodes: [
                                        'node_G',
                                        'node_H',
                                        'node_I',
                                        'node_J'
                                    ],

                                    lines: [
                                        // These are normal downstream lines from G/H/I/J.
                                        // The approved backup links D->G and B->H remain available
                                        // when S2->Emergency Centre fails.
                                        'line_G_Emerg',
                                        'line_H_G',
                                        'line_H_I',
                                        'line_I_J'
                                    ],

                                    areas: [
                                        'Area G',
                                        'Area H',
                                        'Area I',
                                        'Area J'
                                    ]
                                },

                                line_S1_A: {

                                    nodes: [
                                        'node_B',
                                        'node_D',
                                        'node_E',
                                        'node_Hospital'
                                    ],

                                    lines: [
                                        'line_A_B',
                                        'line_Hosp_B',
                                        'line_B_D',
                                        'line_D_E'
                                    ],

                                    areas: [
                                        'Area B',
                                        'Area D',
                                        'Area E',
                                        'City Hospital'
                                    ]
                                }
                            };

                            const group =
                                groups[lineId];

                            if (!group) {
                                return [];
                            }

                            (group.lines || [])
                                .forEach(id => {

                                    const line =
                                        document.getElementById(id);

                                    if (line) {
                                        line.classList.add('line-cut');
                                    }
                                });

                            if (lineId !== 'line_S1_A' &&
                                lineId !== 'line_Emerg_S2') {

                                group.nodes.forEach(id => {

                                    const node =
                                        document.getElementById(id);

                                    if (node) {
                                        node.classList.add('node-danger');
                                    }
                                });
                            }

                            return group.areas;
                        }

                        function connectAlternateLine() {

                            if (!alternateTarget) {
                                return;
                            }

                            const backupLabel =
                                alternateTarget.label;

                            const backupLines =
                                alternateTarget.lineIds ||
                                [alternateTarget.lineId];

                            if (backupLines.some(
                                lineId => cutLines.has(lineId)
                            )) {

                                alert(
                                    'One of the approved backup lines is also cut. Repair it before using alternate supply.'
                                );

                                return;
                            }

                            alternateRoute.forEach(
                                lineId => {

                                    const line =
                                        document.getElementById(lineId);

                                    if (line) {
                                        line.classList.remove(
                                            'alternate-line'
                                        );
                                    }
                                }
                            );

                            alternateRoute =
                                [...backupLines];

                            alternateRoute.forEach(
                                lineId => {

                                    const line =
                                        document.getElementById(lineId);

                                    if (line) {

                                        line.classList.remove(
                                            'power-safe'
                                        );

                                        line.classList.remove(
                                            'line-cut'
                                        );

                                        line.classList.add(
                                            'alternate-line'
                                        );
                                    }
                                }
                            );

                            document
                                .getElementById('alternateAlert')
                                .style.display = 'none';

                            const suppliedAreas =
                                alternateTarget.areas || [];

                            const areaMessage =
                                suppliedAreas.length
                                    ? '<br>Alternate current supplied to: ' +
                                      suppliedAreas.join(', ')
                                    : '';

                            document
                                .getElementById('liveLog')
                                .innerHTML =
                                '✔ Approved alternate connection activated: ' +
                                backupLabel +
                                areaMessage +
                                '<br>' +
                                document.getElementById('liveLog').innerHTML;

                            document
                                .getElementById('alternateMessage')
                                .innerText =
                                'Approved alternate connection activated: ' +
                                backupLabel +
                                (
                                    suppliedAreas.length
                                        ? '. Alternate current supplied to ' +
                                          suppliedAreas.join(', ') +
                                          '.'
                                        : '.'
                                );

                            alternateTarget = null;
                        }

                        function cancelAlternateLine() {

                            document
                                .getElementById('alternateAlert')
                                .style.display = 'none';

                            alternateTarget = null;
                        }

                        function isCriticalLine(lineId) {

                            return lineId === 'line_Hosp_B' ||
                                   lineId === 'line_G_Emerg' ||
                                   lineId === 'line_Emerg_S2';
                        }

                        function updateCriticalFacilities() {

                            const hospitalFailed =
                                pendingFaults.some(
                                    f =>
                                        f.lineId === 'line_Hosp_B'
                                );

                            const emergencyFailed =
                                pendingFaults.some(
                                    f =>
                                        f.lineId === 'line_G_Emerg' ||
                                        f.lineId === 'line_Emerg_S2'
                                );

                            criticalFacilities =
                                (hospitalFailed ? 0 : 1) +
                                (emergencyFailed ? 0 : 1);

                            document
                                .getElementById('criticalFacilities')
                                .innerText =
                                criticalFacilities +
                                ' Protected';
                        }
                """);

        // =========================================================
        // PART 5 - OUTAGE + REPAIR FUNCTIONS
        // =========================================================

        html.append("""
                        async function saveToBackend(path, values) {
                            try {
                                const response = await fetch(path, {
                                    method: 'POST',
                                    headers: {'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'},
                                    body: new URLSearchParams(values)
                                });
                                if (!response.ok) throw new Error('HTTP ' + response.status);
                                return await response.json();
                            } catch (error) {
                                console.error('Database save failed:', error);
                                alert('Database save failed. Check IntelliJ console and SQLite JDBC setup.');
                                return null;
                            }
                        }

                        async function loadSavedHistory() {
                            try {
                                const response = await fetch('/api/history');
                                const events = await response.json();
                                const liveLog = document.getElementById('liveLog');
                                if (!liveLog || !events.length) return;
                                const escapeText = value => String(value).replace(/[&<>"']/g, ch => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[ch]));
                                liveLog.innerHTML = events.map(e => '<div style="margin-bottom:6px;"><span style="color:#94a3b8;">[' + escapeText(e.time) + ']</span> <strong>' + escapeText(e.type.replaceAll('_',' ')) + '</strong> — ' + escapeText(e.details) + '</div>').join('') + liveLog.innerHTML;
                            } catch (error) { console.error('Could not load saved history:', error); }
                        }
                        window.addEventListener('load', loadSavedHistory);

                        function triggerLineCut() {

                            const selector =
                                document.getElementById('lineSelect');

                            const selectedOptions =
                                Array.from(
                                    selector.selectedOptions
                                );

                            if (selectedOptions.length === 0) {

                                alert(
                                    'Please select at least one feeder/power line. Use Ctrl to select two lines.'
                                );

                                return;
                            }

                            let addedFaults = [];

                            selectedOptions.forEach(
                                option => {

                                    const val =
                                        option.value.split('|');

                                    const fault = {

                                        lineId: val[0],
                                        nodeId: val[1],
                                        area: val[2]
                                    };

                                    if (!pendingFaults.some(
                                        f =>
                                            f.lineId === fault.lineId
                                    )) {

                                        pendingFaults.push(
                                            fault
                                        );

                                        addedFaults.push(
                                            fault
                                        );
                                    }

                                    if (!cutLines.has(
                                        fault.lineId
                                    )) {

                                        activeAreas =
                                            Math.max(
                                                0,
                                                activeAreas - 1
                                            );

                                        cutLines.add(
                                            fault.lineId
                                        );
                                    }

                                    document
                                        .getElementById(
                                            fault.lineId
                                        )
                                        .classList.add(
                                            'line-cut'
                                        );

                                    const node =
                                        document.getElementById(
                                            fault.nodeId
                                        );

                                    if (
                                        node &&
                                        fault.lineId !== 'line_S1_A' &&
                                        fault.lineId !== 'line_Emerg_S2'
                                    ) {

                                        node.classList.add(
                                            'node-danger'
                                        );
                                    }

                                    fault.extraDangerAreas =
                                        markDangerGroup(
                                            fault.lineId
                                        );
                                }
                            );

                            addedFaults.forEach(fault => {
                                saveToBackend('/api/fault', {
                                    lineId: fault.lineId,
                                    nodeId: fault.nodeId,
                                    area: fault.area
                                });
                            });

                            document
                                .getElementById('activeAreas')
                                .innerText =
                                activeAreas + ' Areas';

                            updateCriticalFacilities();

                            pendingFaults.sort(
                                (a, b) =>
                                    Number(
                                        isCriticalLine(
                                            b.lineId
                                        )
                                    ) -
                                    Number(
                                        isCriticalLine(
                                            a.lineId
                                        )
                                    )
                            );

                            selectedLineId = null;
                            selectedNodeId = null;
                            affectedArea = null;
                            repairTeamDispatched = false;

                            clearInterval(
                                repairTimer
                            );

                            repairDeadline = null;

                            document
                                .getElementById('dispatchAlert')
                                .style.display = 'none';

                            document
                                .getElementById('repairSuccessAlert')
                                .style.display = 'none';

                            document
                                .getElementById('completeRepairBtn')
                                .style.display = 'none';

                            document
                                .getElementById('dangerAlert')
                                .style.display = 'block';

                            document
                                .getElementById('systemHealthText')
                                .innerText =
                                'Fault Alert';

                            document
                                .getElementById('systemHealthText')
                                .style.color =
                                '#ef4444';

                            document
                                .getElementById('gridStatus')
                                .innerText =
                                '● Outage Active';

                            document
                                .getElementById('gridStatus')
                                .style.background =
                                '#ef4444';

                            const criticalFault =
                                addedFaults.find(
                                    f =>
                                        isCriticalLine(
                                            f.lineId
                                        )
                                );

                            const priorityText =
                                criticalFault
                                    ? '<br><strong style="color:#fbbf24;">Priority: Critical facility fault detected. Repair team will be sent here first.</strong>'
                                    : '';

                            document
                                .getElementById('dangerMessage')
                                .innerHTML =

                                pendingFaults
                                    .map(
                                        f =>
                                            'Feeder / Power Line <strong>' +
                                            f.area +
                                            '</strong> is cut!<br><span style="color:#ff6b6b;font-weight:bold;">' +
                                            f.area +
                                            ' is currently in DANGER (Power Cut)!</span>'
                                    )
                                    .join('<br>') +

                                pendingFaults
                                    .flatMap(
                                        f =>
                                            f.extraDangerAreas || []
                                    )
                                    .filter(
                                        (
                                            area,
                                            index,
                                            arr
                                        ) =>
                                            arr.indexOf(area) === index
                                    )
                                    .map(
                                        area =>
                                            '<br><strong style="color:#ff6b6b;">' +
                                            area +
                                            ' is currently in DANGER (Power Cut)!</strong>'
                                    )
                                    .join('') +

                                priorityText;

                            const now =
                                new Date()
                                    .toLocaleTimeString();

                            const logEntries =
                                addedFaults
                                    .map(
                                        f =>
                                            '⚠️ <span style="color:#ff6b6b;">[' +
                                            now +
                                            '] Power line fault at ' +
                                            f.area +
                                            '</span>'
                                    )
                                    .join('<br>');

                            if (logEntries) {

                                document
                                    .getElementById('liveLog')
                                    .innerHTML =
                                    logEntries +
                                    '<br>' +
                                    document
                                        .getElementById(
                                            'liveLog'
                                        )
                                        .innerHTML;
                            }

                            if (addedFaults.length > 0) {
                                askAlternateConnection(
                                    addedFaults[0]
                                );
                            }
                        }

                        function dispatchRepairTeam() {

                            if (pendingFaults.length === 0) {

                                alert(
                                    'First select one or more feeder/power lines and trigger the outage.'
                                );

                                return;
                            }

                            if (repairTeamDispatched) {

                                alert(
                                    'Complete the current repair before dispatching a team to another line.'
                                );

                                return;
                            }

                            const completionMinutes =
                                parseInt(
                                    document
                                        .getElementById(
                                            'completionTime'
                                        )
                                        .value
                                );

                            if (
                                !completionMinutes ||
                                completionMinutes <= 0
                            ) {

                                alert(
                                    'Please enter a valid work completion time in minutes.'
                                );

                                return;
                            }

                            pendingFaults.sort(
                                (a, b) =>
                                    Number(
                                        isCriticalLine(
                                            b.lineId
                                        )
                                    ) -
                                    Number(
                                        isCriticalLine(
                                            a.lineId
                                        )
                                    )
                            );

                            const nextFault =
                                pendingFaults[0];

                            selectedLineId =
                                nextFault.lineId;

                            selectedNodeId =
                                nextFault.nodeId;

                            affectedArea =
                                nextFault.area;

                            const team =
                                document
                                    .getElementById(
                                        'repairTeam'
                                    )
                                    .value;

                            saveToBackend('/api/dispatch', {
                                lineId: selectedLineId,
                                team: team,
                                area: affectedArea,
                                completionMinutes: completionMinutes
                            });

                            const now =
                                new Date();

                            repairDeadline =
                                new Date(
                                    now.getTime() +
                                    completionMinutes *
                                    60 *
                                    1000
                                );

                            clearInterval(
                                repairTimer
                            );

                            repairTeamDispatched = true;

                            document
                                .getElementById(
                                    'dispatchAlert'
                                )
                                .style.display = 'block';

                            const priorityLabel =
                                isCriticalLine(
                                    selectedLineId
                                )
                                    ? '<br><strong style="color:#fbbf24;">⭐ FIRST PRIORITY: Critical facility line</strong>'
                                    : '';

                            document
                                .getElementById(
                                    'dispatchDetails'
                                )
                                .innerHTML =
                                '<strong>' +
                                team +
                                '</strong> has been dispatched to <strong>' +
                                affectedArea +
                                '</strong>.' +
                                priorityLabel +
                                '<br>' +
                                'Team is travelling to the affected location for inspection and repair.<br>' +
                                '<span style="color:#fbbf24;">Given completion time: ' +
                                completionMinutes +
                                ' minute(s)</span><br>' +
                                '<span id="remainingTime" style="color:#34d399;font-weight:bold;">Time Remaining: ' +
                                completionMinutes +
                                ':00</span><br>' +
                                '<span>Pending faulty lines: ' +
                                pendingFaults.length +
                                '</span>';

                            document
                                .getElementById(
                                    'completeRepairBtn'
                                )
                                .style.display = 'block';

                            document
                                .getElementById(
                                    'liveLog'
                                )
                                .innerHTML =
                                '🚑 <span style="color:#38bdf8;">' +
                                team +
                                ' dispatched to ' +
                                affectedArea +
                                (
                                    isCriticalLine(
                                        selectedLineId
                                    )
                                        ? ' (FIRST PRIORITY)'
                                        : ''
                                ) +
                                '</span><br>' +
                                document
                                    .getElementById(
                                        'liveLog'
                                    )
                                    .innerHTML;

                            repairTimer =
                                setInterval(
                                    function () {

                                        const difference =
                                            repairDeadline.getTime() -
                                            new Date().getTime();

                                        if (difference > 0) {

                                            const totalSeconds =
                                                Math.floor(
                                                    difference / 1000
                                                );

                                            const remaining =
                                                document
                                                    .getElementById(
                                                        'remainingTime'
                                                    );

                                            if (remaining) {

                                                remaining.innerText =
                                                    'Time Remaining: ' +
                                                    Math.floor(
                                                        totalSeconds / 60
                                                    ) +
                                                    ':' +
                                                    String(
                                                        totalSeconds % 60
                                                    ).padStart(
                                                        2,
                                                        '0'
                                                    );
                                            }

                                        } else {

                                            /*
                                             * =================================================
                                             * AUTOMATIC REPAIR COMPLETION
                                             * =================================================
                                             *
                                             * When the operator's entered repair time
                                             * reaches zero, automatically complete the
                                             * repair instead of leaving the line in the
                                             * red "line-cut" state.
                                             */

                                            clearInterval(
                                                repairTimer
                                            );

                                            repairTimer = null;

                                            const remaining =
                                                document
                                                    .getElementById(
                                                        'remainingTime'
                                                    );

                                            if (remaining) {

                                                remaining.innerText =
                                                    '⏰ 0:00 — REPAIR TIME COMPLETED';

                                                remaining.style.color =
                                                    '#34d399';
                                            }

                                            document
                                                .getElementById(
                                                    'dispatchDetails'
                                                )
                                                .innerHTML +=
                                                '<br><span style="color:#34d399;font-weight:bold;">✔ REPAIR TIME COMPLETED — LINE RESTORING...</span>';

                                            /*
                                             * The existing completeRepair()
                                             * function performs the actual restoration:
                                             *
                                             * - removes line-cut
                                             * - removes node-danger
                                             * - removes the fault from pendingFaults
                                             * - updates active areas
                                             * - updates critical facilities
                                             * - updates system health
                                             * - restores Grid Online when appropriate
                                             * - preserves remaining faults
                                             */
                                            completeRepair();
                                        }

                                    },
                                    1000
                                );
                        }
                """);

        // =========================================================
        // PART 6 - COMPLETE REPAIR + OUTAGE REPORT
        // =========================================================

        html.append("""
                        function completeRepair() {

                            if (
                                !repairTeamDispatched ||
                                !selectedLineId
                            ) {

                                alert(
                                    'Please dispatch a repair team first.'
                                );

                                return;
                            }

                            const team =
                                document
                                    .getElementById(
                                        'repairTeam'
                                    )
                                    .value;

                            const completedWithinTime =
                                repairDeadline &&
                                new Date() <= repairDeadline;

                            saveToBackend('/api/complete', {
                                lineId: selectedLineId,
                                team: team,
                                area: affectedArea,
                                completedWithinTime: String(Boolean(completedWithinTime))
                            });

                            clearInterval(
                                repairTimer
                            );

                            repairTimer = null;

                            document
                                .getElementById(
                                    selectedLineId
                                )
                                .classList.remove(
                                    'line-cut'
                                );

                            document
                                .getElementById(
                                    selectedLineId
                                )
                                .classList.remove(
                                    'power-safe'
                                );

                            const targetNode =
                                document
                                    .getElementById(
                                        selectedNodeId
                                    );

                            if (targetNode) {

                                targetNode.classList.remove(
                                    'node-danger'
                                );
                            }

                            const repairedGroups = {

                                line_S1_A: {

                                    nodes: [
                                        'node_A',
                                        'node_B',
                                        'node_D',
                                        'node_E',
                                        'node_Hospital'
                                    ],

                                    lines: [
                                        'line_S1_A',
                                        'line_A_B',
                                        'line_B_D',
                                        'line_D_E',
                                        'line_Hosp_B'
                                    ]
                                },

                                line_Emerg_S2: {

                                    nodes: [
                                        'node_G',
                                        'node_H',
                                        'node_I',
                                        'node_J'
                                    ],

                                    lines: [
                                        'line_G_Emerg',
                                        'line_D_G',
                                        'line_H_G',
                                        'line_H_I',
                                        'line_I_J',
                                        'line_Emerg_S2'
                                    ]
                                }
                            };

                            const repairedGroup =
                                repairedGroups[
                                    selectedLineId
                                ];

                            if (repairedGroup) {

                                if (
                                    selectedLineId ===
                                        'line_S1_A' ||
                                    selectedLineId ===
                                        'line_Emerg_S2'
                                ) {

                                    alternateRoute.forEach(
                                        id => {

                                            const line =
                                                document
                                                    .getElementById(
                                                        id
                                                    );

                                            if (line) {

                                                line.classList.remove(
                                                    'alternate-line'
                                                );
                                            }
                                        }
                                    );

                                    alternateRoute = [];

                                    alternateTarget = null;

                                    document
                                        .getElementById(
                                            'alternateAlert'
                                        )
                                        .style.display =
                                        'none';
                                }

                                repairedGroup.nodes.forEach(
                                    id => {

                                        const node =
                                            document
                                                .getElementById(
                                                    id
                                                );

                                        if (node) {

                                            node.classList.remove(
                                                'node-danger'
                                            );
                                        }
                                    }
                                );

                                repairedGroup.lines.forEach(
                                    id => {

                                        const line =
                                            document
                                                .getElementById(
                                                    id
                                                );

                                        if (
                                            line &&
                                            !cutLines.has(id)
                                        ) {

                                            line.classList.remove(
                                                'line-cut'
                                            );

                                            line.classList.remove(
                                                'power-safe'
                                            );
                                        }
                                    }
                                );

                                const safeAreaNames =
                                    selectedLineId ===
                                        'line_S1_A'
                                        ? [
                                            'Area A',
                                            'Area B',
                                            'Area D',
                                            'Area E',
                                            'City Hospital'
                                        ]
                                        : [
                                            'Area G',
                                            'Area H',
                                            'Area I',
                                            'Area J'
                                        ];

                                pendingFaults =
                                    pendingFaults.filter(
                                        f =>
                                            f.lineId !==
                                                selectedLineId &&
                                            !safeAreaNames.includes(
                                                f.area
                                            )
                                    );

                                document
                                    .getElementById(
                                        'dangerMessage'
                                    )
                                    .innerHTML =
                                    safeAreaNames
                                        .map(
                                            area =>
                                                '<span style="color:#38bdf8;font-weight:bold;">✔ ' +
                                                area +
                                                ' — SAFE (Power Restored)</span>'
                                        )
                                        .join('<br>');

                            } else {

                                pendingFaults =
                                    pendingFaults.filter(
                                        f =>
                                            f.lineId !==
                                            selectedLineId
                                    );
                            }

                            if (
                                cutLines.has(
                                    selectedLineId
                                )
                            ) {

                                cutLines.delete(
                                    selectedLineId
                                );

                                activeAreas =
                                    Math.min(
                                        8,
                                        activeAreas + 1
                                    );

                                document
                                    .getElementById(
                                        'activeAreas'
                                    )
                                    .innerText =
                                    activeAreas +
                                    ' Areas';
                            }

                            updateCriticalFacilities();

                            document
                                .getElementById(
                                    'dangerAlert'
                                )
                                .style.display = 'none';

                            document
                                .getElementById(
                                    'dispatchAlert'
                                )
                                .style.display = 'none';

                            document
                                .getElementById(
                                    'completeRepairBtn'
                                )
                                .style.display = 'none';

                            document
                                .getElementById(
                                    'repairSuccessAlert'
                                )
                                .style.display = 'block';

                            document
                                .getElementById(
                                    'repairSuccessDetails'
                                )
                                .innerHTML =
                                '<strong>' +
                                team +
                                '</strong> successfully repaired <strong>' +
                                affectedArea +
                                '</strong>.<br>Power line restored.' +
                                (
                                    completedWithinTime
                                        ? '<br><span style="color:#34d399;font-weight:bold;">✔ WORK COMPLETED WITHIN GIVEN TIME</span>'
                                        : '<br><span style="color:#f87171;font-weight:bold;">⚠ WORK COMPLETED AFTER GIVEN TIME</span>'
                                );

                            document
                                .getElementById(
                                    'liveLog'
                                )
                                .innerHTML =
                                '✔ <span style="color:#34d399;">' +
                                team +
                                ' completed repair at ' +
                                affectedArea +
                                ' - Power Restored</span><br>' +
                                document
                                    .getElementById(
                                        'liveLog'
                                    )
                                    .innerHTML;

                            repairTeamDispatched = false;

                            selectedLineId = null;
                            selectedNodeId = null;
                            affectedArea = null;
                            repairDeadline = null;

                            if (pendingFaults.length > 0) {

                                pendingFaults.sort(
                                    (a, b) =>
                                        Number(
                                            isCriticalLine(
                                                b.lineId
                                            )
                                        ) -
                                        Number(
                                            isCriticalLine(
                                                a.lineId
                                            )
                                        )
                                );

                                document
                                    .getElementById(
                                        'dangerAlert'
                                    )
                                    .style.display = 'block';

                                document
                                    .getElementById(
                                        'dangerMessage'
                                    )
                                    .innerHTML =
                                    '<strong>Remaining faults:</strong><br>' +
                                    pendingFaults
                                        .map(
                                            f =>
                                                f.area +
                                                ' is still offline'
                                        )
                                        .join('<br>') +
                                    '<br><strong style="color:#fbbf24;">Dispatch the next repair team. Critical lines receive first preference.</strong>';

                                document
                                    .getElementById(
                                        'systemHealthText'
                                    )
                                    .innerText =
                                    'Fault Alert';

                                document
                                    .getElementById(
                                        'systemHealthText'
                                    )
                                    .style.color =
                                    '#ef4444';

                                document
                                    .getElementById(
                                        'gridStatus'
                                    )
                                    .innerText =
                                    '● Outage Active';

                                document
                                    .getElementById(
                                        'gridStatus'
                                    )
                                    .style.background =
                                    '#ef4444';

                            } else {

                                document
                                    .getElementById(
                                        'systemHealthText'
                                    )
                                    .innerText =
                                    'Healthy';

                                document
                                    .getElementById(
                                        'systemHealthText'
                                    )
                                    .style.color =
                                    '#34d399';

                                document
                                    .getElementById(
                                        'gridStatus'
                                    )
                                    .innerText =
                                    '● Grid Online';

                                document
                                    .getElementById(
                                        'gridStatus'
                                    )
                                    .style.background =
                                    '#10b981';
                            }
                        }

                        function handleDispatch(event) {

                            event.preventDefault();

                            const node =
                                document
                                    .getElementById(
                                        'targetNode'
                                    )
                                    .value;

                            const issue =
                                document
                                    .getElementById(
                                        'issueType'
                                    )
                                    .value;

                            const remarks =
                                document
                                    .getElementById(
                                        'remarks'
                                    )
                                    .value ||
                                'Immediate Inspection';

                            saveToBackend('/api/report', {
                                node: node,
                                issue: issue,
                                remarks: remarks
                            });

                            const now =
                                new Date()
                                    .toLocaleTimeString();

                            const alertBox =
                                document
                                    .getElementById(
                                        'dispatchReportAlert'
                                    );

                            const alertDetails =
                                document
                                    .getElementById(
                                        'alertDetails'
                                    );

                            const liveLog =
                                document
                                    .getElementById(
                                        'liveLog'
                                    );

                            alertDetails.innerHTML =
                                'Target: <strong>' +
                                node +
                                '</strong> | Issue: ' +
                                issue +
                                '<br>Remarks: "' +
                                remarks +
                                '" at ' +
                                now;

                            alertBox.style.display =
                                'block';

                            liveLog.innerHTML =
                                '⚡ <em>[' +
                                now +
                                '] Outage report registered for ' +
                                node +
                                ' (' +
                                issue +
                                ')</em><br>' +
                                liveLog.innerHTML;
                        }
                """);

        // =========================================================
        // PART 7 - CLOSE HTML
        // =========================================================

        html.append("""
                    </script>

                </body>
                </html>
                """);

        return html.toString();
    }
}