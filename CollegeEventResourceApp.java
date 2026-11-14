import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.SQLException;
import java.util.List;

public class CollegeEventResourceApp extends JFrame {
    CardLayout cl = new CardLayout();
    JPanel cards = new JPanel(cl);
    EventsPanel ep = new EventsPanel();
    ResourcesPanel rp = new ResourcesPanel();
    BookingsPanel bp = new BookingsPanel();

    CollegeEventResourceApp() {
        setTitle("College Event and Resources Management");
        setSize(900, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        JPanel top = new JPanel();
        JButton eB = new JButton("Events"), rB = new JButton("Resources"),
                bB = new JButton("Bookings"), xB = new JButton("Exit");
        top.add(eB); top.add(rB); top.add(bB); top.add(xB);
        add(top, BorderLayout.NORTH);

        cards.add(ep, "e"); cards.add(rp, "r"); cards.add(bp, "b");
        add(cards, BorderLayout.CENTER);

        eB.addActionListener(e -> { ep.refresh(); cl.show(cards, "e"); });
        rB.addActionListener(e -> { rp.refresh(); cl.show(cards, "r"); });
        bB.addActionListener(e -> { bp.refresh(); cl.show(cards, "b"); });
        xB.addActionListener(e -> System.exit(0));
        cl.show(cards, "e");
    }

    public static void main(String[] a) {
        try {
            Database.init();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(null,
                    "Could not open the database:\n" + ex.getMessage() +
                    "\n\nCheck that:\n" +
                    "1) MySQL is running and schema.sql has been executed,\n" +
                    "2) the user/password at the top of Database.java are correct,\n" +
                    "3) the mysql-connector-j jar is on the classpath.",
                    "Database error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        SwingUtilities.invokeLater(() -> new CollegeEventResourceApp().setVisible(true));
    }
}

/** Small helpers shared by the panels. */
final class Ui {
    interface Action { void run() throws Exception; }

    /** Runs a database action; shows a dialog and returns false if it fails. */
    static boolean attempt(Component parent, Action a) {
        try {
            a.run();
            return true;
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage());
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent, "Database error: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Error: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    static int number(String text, String field) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(field + " must be a number");
        }
    }
}

class EventsPanel extends JPanel {
    DefaultTableModel m = new DefaultTableModel(new Object[]{"Event ID","Name","Date","Venue","Organizer"},0){
        public boolean isCellEditable(int r,int c){return false;}
    };
    JTable t = new JTable(m);
    JTextField name=new JTextField(), date=new JTextField(), venue=new JTextField(), org=new JTextField();
    Integer selId=null;

    EventsPanel() {
        setLayout(new BorderLayout());
        add(new JScrollPane(t), BorderLayout.CENTER);

        JPanel f = new JPanel(new GridLayout(2,4,5,5));
        f.add(new JLabel("Name")); f.add(new JLabel("Date (YYYY-MM-DD)")); f.add(new JLabel("Venue")); f.add(new JLabel("Organizer"));
        f.add(name); f.add(date); f.add(venue); f.add(org);

        JPanel btn = new JPanel();
        JButton add=new JButton("Add"), upd=new JButton("Update"), del=new JButton("Delete"), clr=new JButton("Clear");
        btn.add(add); btn.add(upd); btn.add(del); btn.add(clr);

        JPanel s = new JPanel(new BorderLayout());
        s.add(f, BorderLayout.CENTER); s.add(btn, BorderLayout.SOUTH);
        add(s, BorderLayout.SOUTH);

        add.addActionListener(e -> addEv());
        upd.addActionListener(e -> updEv());
        del.addActionListener(e -> delEv());
        clr.addActionListener(e -> clear());
        t.getSelectionModel().addListSelectionListener(e -> load());
        refresh();
    }

    void addEv() {
        if (Ui.attempt(this, () -> Database.addEvent(name.getText(), date.getText(), venue.getText(), org.getText()))) {
            refresh(); clear();
        }
    }

    void updEv() {
        if (selId==null) { JOptionPane.showMessageDialog(this,"Select an event first"); return; }
        if (Ui.attempt(this, () -> Database.updateEvent(selId, name.getText(), date.getText(), venue.getText(), org.getText()))) {
            refresh(); clear();
        }
    }

    void delEv() {
        if (selId==null) { JOptionPane.showMessageDialog(this,"Select an event first"); return; }
        final int id = selId;
        boolean[] ok = {true};
        if (!Ui.attempt(this, () -> {
            int n = Database.bookingCountForEvent(id);
            if (n > 0 && JOptionPane.showConfirmDialog(this,
                    "This event has " + n + " booking(s). Deleting it will cancel them too. Continue?",
                    "Confirm delete", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
                ok[0] = false;
                return;
            }
            Database.deleteEvent(id);
        })) return;
        if (ok[0]) { refresh(); clear(); }
    }

    void clear() {
        name.setText(""); date.setText(""); venue.setText(""); org.setText("");
        selId=null; t.clearSelection();
    }

    void load() {
        int r=t.getSelectedRow(); if (r<0) return;
        selId=Database.idOf((String)m.getValueAt(r,0));
        name.setText((String)m.getValueAt(r,1)); date.setText((String)m.getValueAt(r,2));
        venue.setText((String)m.getValueAt(r,3)); org.setText((String)m.getValueAt(r,4));
    }

    void refresh() {
        Ui.attempt(this, () -> {
            List<Database.Event> list = Database.listEvents();
            m.setRowCount(0);
            for (Database.Event ev: list) m.addRow(new Object[]{Database.code('E', ev.id), ev.name, ev.date, ev.venue, ev.organizer});
        });
    }
}

class ResourcesPanel extends JPanel {
    DefaultTableModel m = new DefaultTableModel(new Object[]{"Resource ID","Name","Type","Total Qty","Available","Status"},0){
        public boolean isCellEditable(int r,int c){return false;}
    };
    JTable t = new JTable(m);
    JTextField name=new JTextField(), qty=new JTextField();
    JComboBox<String> type=new JComboBox<>(new String[]{"Hall","Projector","Chair","Microphone","Other"});
    Integer selId=null;

    ResourcesPanel() {
        setLayout(new BorderLayout());
        add(new JScrollPane(t), BorderLayout.CENTER);

        JPanel f = new JPanel(new GridLayout(2,3,5,5));
        f.add(new JLabel("Name")); f.add(new JLabel("Type")); f.add(new JLabel("Total Quantity"));
        f.add(name); f.add(type); f.add(qty);

        JPanel btn = new JPanel();
        JButton add=new JButton("Add"), upd=new JButton("Update"), del=new JButton("Delete"), clr=new JButton("Clear");
        btn.add(add); btn.add(upd); btn.add(del); btn.add(clr);

        JPanel s = new JPanel(new BorderLayout());
        s.add(f, BorderLayout.CENTER); s.add(btn, BorderLayout.SOUTH);
        add(s, BorderLayout.SOUTH);

        add.addActionListener(e -> addRes());
        upd.addActionListener(e -> updRes());
        del.addActionListener(e -> delRes());
        clr.addActionListener(e -> clear());
        t.getSelectionModel().addListSelectionListener(e -> load());
        refresh();
    }

    void addRes() {
        if (Ui.attempt(this, () -> Database.addResource(name.getText(), (String)type.getSelectedItem(), Ui.number(qty.getText(), "Quantity")))) {
            refresh(); clear();
        }
    }

    void updRes() {
        if (selId==null) { JOptionPane.showMessageDialog(this,"Select a resource first"); return; }
        if (Ui.attempt(this, () -> Database.updateResource(selId, name.getText(), (String)type.getSelectedItem(), Ui.number(qty.getText(), "Quantity")))) {
            refresh(); clear();
        }
    }

    void delRes() {
        if (selId==null) { JOptionPane.showMessageDialog(this,"Select a resource first"); return; }
        if (Ui.attempt(this, () -> Database.deleteResource(selId))) {
            refresh(); clear();
        }
    }

    void clear() {
        name.setText(""); qty.setText(""); type.setSelectedIndex(0);
        selId=null; t.clearSelection();
    }

    void load() {
        int r=t.getSelectedRow(); if (r<0) return;
        selId=Database.idOf((String)m.getValueAt(r,0));
        name.setText((String)m.getValueAt(r,1)); type.setSelectedItem((String)m.getValueAt(r,2));
        qty.setText(String.valueOf(m.getValueAt(r,3)));
    }

    void refresh() {
        Ui.attempt(this, () -> {
            List<Database.Resource> list = Database.listResources();
            m.setRowCount(0);
            for (Database.Resource r: list) m.addRow(new Object[]{Database.code('R', r.id), r.name, r.type, r.total, r.available, r.status});
        });
    }
}

class BookingsPanel extends JPanel {
    /** Combo box entry: shows "E001 - Name" but remembers the database id. */
    static class Item {
        final int id; final String label;
        Item(int id, String label) { this.id=id; this.label=label; }
        public String toString() { return label; }
    }

    DefaultTableModel m = new DefaultTableModel(new Object[]{"Booking ID","Event","Resource","Quantity","Date"},0){
        public boolean isCellEditable(int r,int c){return false;}
    };
    JTable t = new JTable(m);
    JComboBox<Item> evCb=new JComboBox<>(), resCb=new JComboBox<>();
    JTextField qty=new JTextField(), date=new JTextField();
    Integer selId=null;

    BookingsPanel() {
        setLayout(new BorderLayout());
        add(new JScrollPane(t), BorderLayout.CENTER);

        JPanel f = new JPanel(new GridLayout(2,4,5,5));
        f.add(new JLabel("Event")); f.add(new JLabel("Resource")); f.add(new JLabel("Quantity")); f.add(new JLabel("Date (YYYY-MM-DD)"));
        f.add(evCb); f.add(resCb); f.add(qty); f.add(date);

        JPanel btn = new JPanel();
        JButton book=new JButton("Book"), cancel=new JButton("Cancel Booking"), clr=new JButton("Clear");
        btn.add(book); btn.add(cancel); btn.add(clr);

        JPanel s = new JPanel(new BorderLayout());
        s.add(f, BorderLayout.CENTER); s.add(btn, BorderLayout.SOUTH);
        add(s, BorderLayout.SOUTH);

        book.addActionListener(e -> book());
        cancel.addActionListener(e -> cancel());
        clr.addActionListener(e -> clear());
        t.getSelectionModel().addListSelectionListener(e -> load());
        refresh();
    }

    void book() {
        Item ev=(Item)evCb.getSelectedItem(), res=(Item)resCb.getSelectedItem();
        if (ev==null || res==null) {
            JOptionPane.showMessageDialog(this,"Add events and resources first"); return;
        }
        if (Ui.attempt(this, () -> Database.book(ev.id, res.id, Ui.number(qty.getText(), "Quantity"), date.getText()))) {
            refresh(); clear();
        }
    }

    void cancel() {
        if (selId==null) { JOptionPane.showMessageDialog(this,"Select a booking first"); return; }
        if (Ui.attempt(this, () -> Database.cancelBooking(selId))) {
            refresh(); clear();
        }
    }

    void clear() {
        qty.setText(""); date.setText("");
        if (evCb.getItemCount()>0) evCb.setSelectedIndex(0);
        if (resCb.getItemCount()>0) resCb.setSelectedIndex(0);
        selId=null; t.clearSelection();
    }

    void load() {
        int r=t.getSelectedRow(); if (r<0) return;
        selId=Database.idOf((String)m.getValueAt(r,0));
        qty.setText(String.valueOf(m.getValueAt(r,3))); date.setText((String)m.getValueAt(r,4));
    }

    void refresh() {
        Ui.attempt(this, () -> {
            List<Database.Event> events = Database.listEvents();
            List<Database.Resource> resources = Database.listResources();
            List<Database.Booking> bookings = Database.listBookings();

            evCb.removeAllItems();
            for (Database.Event ev: events) evCb.addItem(new Item(ev.id, Database.code('E', ev.id)+" - "+ev.name));
            resCb.removeAllItems();
            for (Database.Resource r: resources) resCb.addItem(new Item(r.id, Database.code('R', r.id)+" - "+r.name+" ("+r.available+" free)"));

            m.setRowCount(0);
            for (Database.Booking b: bookings) m.addRow(new Object[]{Database.code('B', b.id), b.eventName, b.resourceName, b.qty, b.date});
        });
    }
}
