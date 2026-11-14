import java.sql.*;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * All database access lives here (MySQL through JDBC).
 * Business-rule problems throw IllegalArgumentException with a message
 * that is safe to show to the user; real database problems throw SQLException.
 *
 * Run schema.sql in MySQL once before starting the app.
 */
class Database {
    // ---- EDIT THESE THREE LINES to match your MySQL setup -------------------
    static final String URL  = "jdbc:mysql://localhost:3306/event_resource_db"
                             + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    static final String USER = "root";
    static final String PASS = "your_mysql_password";
    // -------------------------------------------------------------------------

    private static Connection conn;

    // ---------------------------------------------------------------- connection

    static Connection get() throws SQLException {
        if (conn == null || !conn.isValid(2)) {
            conn = DriverManager.getConnection(URL, USER, PASS);
        }
        return conn;
    }

    /** Opens the connection once at start-up so problems show immediately. */
    static void init() throws SQLException {
        get();
    }

    // ---------------------------------------------------------------- helpers

    static String code(char prefix, int id) { return String.format("%c%03d", prefix, id); }

    static int idOf(String code) { return Integer.parseInt(code.substring(1)); }

    private static String nz(String s) { return s == null ? "" : s; }

    private static String required(String s, String field) {
        if (s == null || s.trim().isEmpty()) throw new IllegalArgumentException(field + " is required");
        return s.trim();
    }

    /** Blank -> null. Otherwise must be a real date in YYYY-MM-DD. */
    private static String date(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return LocalDate.parse(s.trim()).toString();
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Date must be a real date in YYYY-MM-DD format (e.g. 2026-11-14)");
        }
    }

    private static String blankToNull(String s) {
        return (s == null || s.trim().isEmpty()) ? null : s.trim();
    }

    // ---------------------------------------------------------------- events

    static List<Event> listEvents() throws SQLException {
        List<Event> out = new ArrayList<>();
        try (Statement st = get().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT event_id, name, event_date, venue, organizer FROM events ORDER BY event_id")) {
            while (rs.next())
                out.add(new Event(rs.getInt(1), rs.getString(2), nz(rs.getString(3)),
                        nz(rs.getString(4)), nz(rs.getString(5))));
        }
        return out;
    }

    static void addEvent(String name, String date, String venue, String organizer) throws SQLException {
        name = required(name, "Name");
        String d = date(date);
        try (PreparedStatement ps = get().prepareStatement(
                "INSERT INTO events (name, event_date, venue, organizer) VALUES (?,?,?,?)")) {
            ps.setString(1, name);
            ps.setString(2, d);
            ps.setString(3, blankToNull(venue));
            ps.setString(4, blankToNull(organizer));
            ps.executeUpdate();
        }
    }

    static void updateEvent(int id, String name, String date, String venue, String organizer) throws SQLException {
        name = required(name, "Name");
        String d = date(date);
        try (PreparedStatement ps = get().prepareStatement(
                "UPDATE events SET name=?, event_date=?, venue=?, organizer=? WHERE event_id=?")) {
            ps.setString(1, name);
            ps.setString(2, d);
            ps.setString(3, blankToNull(venue));
            ps.setString(4, blankToNull(organizer));
            ps.setInt(5, id);
            ps.executeUpdate();
        }
    }

    static int bookingCountForEvent(int eventId) throws SQLException {
        return count("SELECT COUNT(*) FROM bookings WHERE event_id = ?", eventId);
    }

    /** Deleting an event also deletes its bookings (ON DELETE CASCADE), which frees the resources. */
    static void deleteEvent(int id) throws SQLException {
        try (PreparedStatement ps = get().prepareStatement("DELETE FROM events WHERE event_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- resources

    static List<Resource> listResources() throws SQLException {
        List<Resource> out = new ArrayList<>();
        try (Statement st = get().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT resource_id, name, type, total_qty, available_qty, status " +
                     "FROM resource_availability ORDER BY resource_id")) {
            while (rs.next())
                out.add(new Resource(rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getInt(4), rs.getInt(5), rs.getString(6)));
        }
        return out;
    }

    static void addResource(String name, String type, int qty) throws SQLException {
        name = required(name, "Name");
        if (qty < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        try (PreparedStatement ps = get().prepareStatement(
                "INSERT INTO resources (name, type, quantity) VALUES (?,?,?)")) {
            ps.setString(1, name);
            ps.setString(2, type);
            ps.setInt(3, qty);
            ps.executeUpdate();
        }
    }

    static void updateResource(int id, String name, String type, int qty) throws SQLException {
        name = required(name, "Name");
        if (qty < 0) throw new IllegalArgumentException("Quantity cannot be negative");
        int booked = count("SELECT COALESCE(SUM(quantity),0) FROM bookings WHERE resource_id = ?", id);
        if (qty < booked)
            throw new IllegalArgumentException("Quantity cannot be less than the " + booked + " already booked");
        try (PreparedStatement ps = get().prepareStatement(
                "UPDATE resources SET name=?, type=?, quantity=? WHERE resource_id=?")) {
            ps.setString(1, name);
            ps.setString(2, type);
            ps.setInt(3, qty);
            ps.setInt(4, id);
            ps.executeUpdate();
        }
    }

    static void deleteResource(int id) throws SQLException {
        int n = count("SELECT COUNT(*) FROM bookings WHERE resource_id = ?", id);
        if (n > 0)
            throw new IllegalArgumentException("This resource has " + n + " booking(s). Cancel them first.");
        try (PreparedStatement ps = get().prepareStatement("DELETE FROM resources WHERE resource_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- bookings

    static List<Booking> listBookings() throws SQLException {
        List<Booking> out = new ArrayList<>();
        try (Statement st = get().createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT booking_id, event_name, resource_name, quantity, booking_date " +
                     "FROM booking_details ORDER BY booking_id")) {
            while (rs.next())
                out.add(new Booking(rs.getInt(1), rs.getString(2), rs.getString(3),
                        rs.getInt(4), nz(rs.getString(5))));
        }
        return out;
    }

    /**
     * Reserves qty units of a resource for an event.
     * The resource row is locked (FOR UPDATE) so two users cannot both take the last units.
     */
    static void book(int eventId, int resourceId, int qty, String date) throws SQLException {
        if (qty <= 0) throw new IllegalArgumentException("Quantity must be greater than 0");
        String d = date(date);
        Connection c = get();
        c.setAutoCommit(false);
        try {
            int available;
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT r.quantity - COALESCE((SELECT SUM(b.quantity) FROM bookings b " +
                    "WHERE b.resource_id = r.resource_id), 0) " +
                    "FROM resources r WHERE r.resource_id = ? FOR UPDATE")) {
                ps.setInt(1, resourceId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) throw new IllegalArgumentException("Resource not found");
                    available = rs.getInt(1);
                }
            }
            if (qty > available)
                throw new IllegalArgumentException("Not enough quantity available (only " + available + " left)");
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO bookings (event_id, resource_id, quantity, booking_date) VALUES (?,?,?,?)")) {
                ps.setInt(1, eventId);
                ps.setInt(2, resourceId);
                ps.setInt(3, qty);
                ps.setString(4, d);
                ps.executeUpdate();
            }
            c.commit();
        } catch (SQLException | RuntimeException e) {
            c.rollback();
            throw e;
        } finally {
            c.setAutoCommit(true);
        }
    }

    /** Cancelling simply deletes the row; availability is calculated, so the stock is released automatically. */
    static void cancelBooking(int id) throws SQLException {
        try (PreparedStatement ps = get().prepareStatement("DELETE FROM bookings WHERE booking_id = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- internal

    private static int count(String sql, int id) throws SQLException {
        try (PreparedStatement ps = get().prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // ---------------------------------------------------------------- models

    static class Event {
        int id; String name, date, venue, organizer;
        Event(int i, String n, String d, String v, String o) { id=i; name=n; date=d; venue=v; organizer=o; }
    }

    static class Resource {
        int id; String name, type, status; int total, available;
        Resource(int i, String n, String t, int tot, int av, String s) { id=i; name=n; type=t; total=tot; available=av; status=s; }
    }

    static class Booking {
        int id, qty; String eventName, resourceName, date;
        Booking(int i, String en, String rn, int q, String d) { id=i; eventName=en; resourceName=rn; qty=q; date=d; }
    }
}
