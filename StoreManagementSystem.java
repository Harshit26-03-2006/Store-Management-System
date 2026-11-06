import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;


public class StoreManagementSystem {

    // -------------------- Models --------------------
    static class Product {
        int id;
        String name;
        double price;
        int stock;
        String code; // SKU / barcode data

        Product(int id, String name, double price, int stock, String code) {
            this.id = id; this.name = name; this.price = price; this.stock = stock; this.code = code;
        }
    }

    static class Customer {
        int id; String name, phone;
        Customer(int id, String name, String phone) { this.id=id; this.name=name; this.phone=phone; }
    }

    static class OrderItem { Product p; int qty; OrderItem(Product p,int qty){this.p=p;this.qty=qty;} }

    static class OrderRec {
        int id; Customer customer; java.util.List<OrderItem> items; Date date; double total;
        OrderRec(int id, Customer c, java.util.List<OrderItem> items, Date date){
            this.id=id; this.customer=c; this.items=items; this.date=date;
            this.total = items.stream().mapToDouble(i->i.qty*i.p.price).sum();
        }
    }

    // New: user model
    static class User {
        String username;
        String password; // plain text for simplicity (don't do this in production)
        String role; // admin, employee, user
        User(String u, String p, String r){ this.username=u; this.password=p; this.role=r;}
    }

    // -------------------- In-memory storage --------------------
    private final java.util.List<Product> products = new ArrayList<>();
    private final java.util.List<Customer> customers = new ArrayList<>();
    private final java.util.List<OrderRec> orders = new ArrayList<>();

    // users store (username -> User)
    private final Map<String, User> users = new LinkedHashMap<>();

    // auto-increment ids
    private int nextProductId = 1, nextCustomerId = 1, nextOrderId = 1;

    // current session
    private static String currentUserName = "";
    private static String currentUserRole = "";

    // GUI components shared
    private JFrame frame;
    private CardLayout cards;
    private JPanel mainPanel;
    private JLabel greetingLabel;
    private Image backgroundImage = null;

    // tables models
    private DefaultTableModel productTableModel;
    private DefaultTableModel customerTableModel;
    private DefaultTableModel orderTableModel;

    // dynamic content references - rebuilt per login
    private JPanel dashboardCardHolder; // container for role specific dashboard cards

    public static void main(String[] args){
        SwingUtilities.invokeLater(() -> new StoreManagementSystem().createAndShowGUI());
    }

    private void createAndShowGUI(){
        // sample data + initial users
        populateSampleData();
        // create default admin user if not present
        users.putIfAbsent("admin", new User("admin","admin","admin"));
        users.putIfAbsent("employee", new User("employee","employee","employee"));
        users.putIfAbsent("user", new User("user","user","user"));

        frame = new JFrame("Store Management System");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(1200, 760);
        frame.setLocationRelativeTo(null);

        cards = new CardLayout();
        mainPanel = new JPanel(cards) {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                // draw background image if available else gradient
                if (backgroundImage != null) {
                    g.drawImage(backgroundImage, 0, 0, getWidth(), getHeight(), null);
                } else {
                    Graphics2D g2 = (Graphics2D) g;
                    GradientPaint gp = new GradientPaint(0,0,new Color(40,116,166), getWidth(), getHeight(), new Color(82, 190, 128));
                    g2.setPaint(gp);
                    g2.fillRect(0,0,getWidth(),getHeight());
                }
            }
        };

        mainPanel.setLayout(cards);
        mainPanel.add(buildLoginPanel(), "login");

        // placeholder dashboard - will be created after login based on role
        dashboardCardHolder = new JPanel(new BorderLayout());
        mainPanel.add(dashboardCardHolder, "dashboard");

        frame.setContentPane(mainPanel);
        frame.setVisible(true);
    }

    // -------------------- UI: Login Panel --------------------
    private JPanel buildLoginPanel(){
        JPanel p = new JPanel(new GridBagLayout());
        p.setOpaque(false);
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(8,8,8,8);

        JLabel title = new JLabel("Welcome to Store Management");
        title.setFont(new Font("SansSerif", Font.BOLD, 28));
        title.setForeground(Color.white);
        gbc.gridwidth = 2; gbc.gridx=0; gbc.gridy=0;
        p.add(title, gbc);

        JLabel userL = new JLabel("Username:"); userL.setForeground(Color.white);
        gbc.gridwidth=1; gbc.gridy++; gbc.gridx=0; p.add(userL, gbc);
        JTextField userF = new JTextField(20); gbc.gridx=1; p.add(userF, gbc);

        JLabel passL = new JLabel("Password:"); passL.setForeground(Color.white);
        gbc.gridy++; gbc.gridx=0; p.add(passL, gbc);
        JPasswordField passF = new JPasswordField(20); gbc.gridx=1; p.add(passF, gbc);

        JPanel btnRow = new JPanel(); btnRow.setOpaque(false);
        JButton loginBtn = new JButton("Login");
        JButton registerBtn = new JButton("Register");
        btnRow.add(loginBtn); btnRow.add(registerBtn);
        gbc.gridy++; gbc.gridx=0; gbc.gridwidth=2; p.add(btnRow, gbc);

        JLabel info = new JLabel("Default: admin/admin   employee/employee   user/user");
        info.setForeground(Color.white);
        gbc.gridy++; p.add(info, gbc);

        loginBtn.addActionListener(e -> {
            String u = userF.getText().trim(); String pw = new String(passF.getPassword());
            if (authenticate(u, pw)){
                currentUserName = u;
                greetingLabel = new JLabel("Hello " + capitalize(currentUserName) + " — welcome to Store Management System");
                buildAndShowDashboardForRole(currentUserRole); // rebuild dashboard UI for role
                cards.show(mainPanel, "dashboard");
            } else {
                JOptionPane.showMessageDialog(frame, "Invalid credentials. Try admin/admin or employee/employee or user/user, or register.", "Login failed", JOptionPane.ERROR_MESSAGE);
            }
        });

        registerBtn.addActionListener(e -> showRegisterDialog());

        return p;
    }

    private boolean authenticate(String u, String pw){
        if (u==null || u.isEmpty()) return false;
        User usr = users.get(u);
        if (usr != null && usr.password.equals(pw)){
            currentUserRole = usr.role;
            return true;
        }
        return false;
    }

    // -------------------- Registration --------------------
    private void showRegisterDialog(){
        JPanel form = new JPanel(new GridLayout(0,2,6,6));
        JTextField userF = new JTextField(); JPasswordField passF = new JPasswordField(); JPasswordField pass2F = new JPasswordField();
        String[] roles = {"user","employee"}; // do not allow normal register to create admin
        JComboBox<String> roleBox = new JComboBox<>(roles);
        form.add(new JLabel("Username:")); form.add(userF);
        form.add(new JLabel("Password:")); form.add(passF);
        form.add(new JLabel("Confirm Password:")); form.add(pass2F);
        form.add(new JLabel("Role:")); form.add(roleBox);

        int res = JOptionPane.showConfirmDialog(frame, form, "Register new account", JOptionPane.OK_CANCEL_OPTION);
        if (res==JOptionPane.OK_OPTION){
            String un = userF.getText().trim();
            String p1 = new String(passF.getPassword());
            String p2 = new String(pass2F.getPassword());
            String role = (String)roleBox.getSelectedItem();
            if (un.isEmpty() || p1.isEmpty()){ JOptionPane.showMessageDialog(frame,"Username and password are required"); return; }
            if (!p1.equals(p2)){ JOptionPane.showMessageDialog(frame,"Passwords do not match"); return; }
            if (users.containsKey(un)){ JOptionPane.showMessageDialog(frame,"Username already exists"); return; }
            users.put(un, new User(un, p1, role));
            JOptionPane.showMessageDialog(frame, "Registered successfully. You can now login.");
        }
    }

    // -------------------- Build Dashboard For Role --------------------
    private void buildAndShowDashboardForRole(String role){
        dashboardCardHolder.removeAll();

        JPanel container = new JPanel(new BorderLayout());
        container.setOpaque(false);
        container.setBorder(new EmptyBorder(8,8,8,8));

        // Top bar
        JPanel top = new JPanel(new BorderLayout()); top.setOpaque(false);
        greetingLabel.setFont(new Font("SansSerif", Font.BOLD, 18)); greetingLabel.setForeground(Color.white);
        top.add(greetingLabel, BorderLayout.WEST);
        JPanel topBtns = new JPanel(); topBtns.setOpaque(false);
        JButton setBgBtn = new JButton("Set Background");
        JButton logoutBtn = new JButton("Logout");
        topBtns.add(setBgBtn); topBtns.add(logoutBtn);
        top.add(topBtns, BorderLayout.EAST);
        container.add(top, BorderLayout.NORTH);

        setBgBtn.addActionListener(e -> chooseAndSetBackground());
        logoutBtn.addActionListener(e -> { currentUserName=""; currentUserRole=""; cards.show(mainPanel,"login"); });

        // center split
        JSplitPane split = new JSplitPane(); split.setDividerLocation(260); split.setOpaque(false);
        split.setLeftComponent(buildMenuPanelForRole(role));
        split.setRightComponent(buildContentPanelForRole(role));
        container.add(split, BorderLayout.CENTER);

        dashboardCardHolder.add(container, BorderLayout.CENTER);
        dashboardCardHolder.revalidate();
        dashboardCardHolder.repaint();
    }

    private JPanel buildMenuPanelForRole(String role){
        JPanel menu = new JPanel(); menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS)); menu.setOpaque(false);
        menu.setBorder(new EmptyBorder(10,10,10,10));
        JLabel mtitle = new JLabel("Menu"); mtitle.setFont(new Font("SansSerif", Font.BOLD, 20)); mtitle.setForeground(Color.white);
        menu.add(mtitle); menu.add(Box.createRigidArea(new Dimension(0,12)));

        // menu items
        java.util.List<String> buttons = new ArrayList<>();
        buttons.add("Products");
        buttons.add("Customers");
        buttons.add("Orders");
        buttons.add("Place Order");
        buttons.add("Show Products");
        buttons.add("Reports");
        // admin-only user management
        if ("admin".equals(role)) buttons.add("User Management");

        for (String b : buttons){
            JButton btn = new JButton(b); btn.setAlignmentX(Component.LEFT_ALIGNMENT);
            // Disable buttons for 'user' role
            if ("user".equals(role) && (b.equals("Products") || b.equals("Customers") || b.equals("Orders") || b.equals("Reports") || b.equals("User Management"))) {
                btn.setEnabled(false);
            }
            menu.add(btn); menu.add(Box.createRigidArea(new Dimension(0,6)));
            btn.addActionListener(e -> switchContent(b));
        }
        return menu;
    }

    private JPanel contentCards; // hold content panels as before
    private JPanel buildContentPanelForRole(String role){
        contentCards = new JPanel(new CardLayout()); contentCards.setOpaque(false);

        contentCards.add(buildProductsPanel(), "Products");
        contentCards.add(buildCustomersPanel(), "Customers");
        contentCards.add(buildOrdersPanel(), "Orders");
        contentCards.add(buildPlaceOrderPanel(), "Place Order");
        contentCards.add(buildShowProductsPanel(), "Show Products");
        contentCards.add(buildReportsPanel(), "Reports");

        // admin-only user management panel
        contentCards.add(buildUserManagementPanel(), "User Management");

        // set default shown panel depending on role
        CardLayout cl = (CardLayout) contentCards.getLayout();
        if ("user".equals(role)) cl.show(contentCards, "Show Products");
        else cl.show(contentCards, "Products");

        return contentCards;
    }

    private void switchContent(String name){
        CardLayout cl = (CardLayout) contentCards.getLayout(); cl.show(contentCards, name);
    }

    // -------------------- Products Panel --------------------
    private JPanel buildProductsPanel(){
        JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false);

        productTableModel = new DefaultTableModel(new Object[]{"ID","Name","Price","Stock","Code"},0){
            public boolean isCellEditable(int r,int c){return false;}
            @Override
            public Class<?> getColumnClass(int columnIndex){
                if (columnIndex==0) return Integer.class;
                if (columnIndex==2) return Double.class;
                if (columnIndex==3) return Integer.class;
                return String.class;
            }
        };
        JTable table = new JTable(productTableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refreshProductTable();

        p.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(); bottom.setOpaque(false);
        JButton add = new JButton("Add"); JButton edit = new JButton("Edit"); JButton del = new JButton("Delete");
        bottom.add(add); bottom.add(edit); bottom.add(del);
        p.add(bottom, BorderLayout.SOUTH);

        add.addActionListener(e -> showProductDialog(null));
        edit.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0){ JOptionPane.showMessageDialog(frame, "Select a product first"); return; }
            int modelRow = table.convertRowIndexToModel(viewRow);
            int id = (int)productTableModel.getValueAt(modelRow,0);
            Product prod = findProductById(id);
            showProductDialog(prod);
        });
        del.addActionListener(e -> {
            int viewRow = table.getSelectedRow();
            if (viewRow < 0){ JOptionPane.showMessageDialog(frame, "Select a product first"); return; }
            int modelRow = table.convertRowIndexToModel(viewRow);
            int id = (int)productTableModel.getValueAt(modelRow,0);
            int confirm = JOptionPane.showConfirmDialog(frame, "Delete product ID " + id + " ?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION){
                removeProductById(id);
                refreshProductTable();
            }
        });

        return p;
    }

    private void showProductDialog(Product existing){
        JTextField nameF = new JTextField(); JTextField priceF = new JTextField(); JTextField stockF = new JTextField(); JTextField codeF = new JTextField();
        if (existing!=null){ nameF.setText(existing.name); priceF.setText(String.valueOf(existing.price)); stockF.setText(String.valueOf(existing.stock)); codeF.setText(existing.code); }
        JPanel form = new JPanel(new GridLayout(0,2,6,6)); form.add(new JLabel("Name:")); form.add(nameF);
        form.add(new JLabel("Price:")); form.add(priceF);
        form.add(new JLabel("Stock:")); form.add(stockF);
        form.add(new JLabel("Code (SKU):")); form.add(codeF);
        int res = JOptionPane.showConfirmDialog(frame, form, existing==null?"Add product":"Edit product", JOptionPane.OK_CANCEL_OPTION);
        if (res==JOptionPane.OK_OPTION){
            try{
                String nm=nameF.getText().trim(); double pr=Double.parseDouble(priceF.getText().trim()); int st=Integer.parseInt(stockF.getText().trim()); String cd = codeF.getText().trim();
                if (nm.isEmpty()){ JOptionPane.showMessageDialog(frame,"Product name required"); return; }
                if (existing==null){ products.add(new Product(nextProductId++, nm, pr, st, cd.isEmpty()?"P"+System.currentTimeMillis():cd)); }
                else { existing.name=nm; existing.price=pr; existing.stock=st; existing.code=cd; }
                refreshProductTable();
            } catch(NumberFormatException ex){ JOptionPane.showMessageDialog(frame, "Invalid numeric input: "+ex.getMessage()); }
            catch(Exception ex){ JOptionPane.showMessageDialog(frame, "Invalid input: "+ex.getMessage()); }
        }
    }

    private void refreshProductTable(){
        if (productTableModel==null) return;
        productTableModel.setRowCount(0);
        for (Product p: products) productTableModel.addRow(new Object[]{p.id,p.name,p.price,p.stock,p.code});
    }

    private Product findProductById(int id){ for (Product p: products) if (p.id==id) return p; return null; }
    private void removeProductById(int id){ products.removeIf(p->p.id==id); }

    // -------------------- Customers Panel --------------------
    private JPanel buildCustomersPanel(){
        JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false);
        customerTableModel = new DefaultTableModel(new Object[]{"ID","Name","Phone"},0){ public boolean isCellEditable(int r,int c){return false;} };
        JTable table = new JTable(customerTableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refreshCustomerTable();
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel bottom = new JPanel(); bottom.setOpaque(false);
        JButton add = new JButton("Add"); JButton edit = new JButton("Edit"); JButton del = new JButton("Delete");
        bottom.add(add); bottom.add(edit); bottom.add(del); p.add(bottom, BorderLayout.SOUTH);

        add.addActionListener(e -> {
            JTextField n=new JTextField(); JTextField ph=new JTextField();
            JPanel f=new JPanel(new GridLayout(0,2,6,6)); f.add(new JLabel("Name:")); f.add(n); f.add(new JLabel("Phone:")); f.add(ph);
            int r = JOptionPane.showConfirmDialog(frame,f,"Add customer",JOptionPane.OK_CANCEL_OPTION);
            if (r==JOptionPane.OK_OPTION){
                String name = n.getText().trim();
                String phone = ph.getText().trim();
                if (name.isEmpty()){ JOptionPane.showMessageDialog(frame,"Name required"); return; }
                customers.add(new Customer(nextCustomerId++, name, phone));
                refreshCustomerTable();
            }
        });

        edit.addActionListener(e -> {
            int viewRow = table.getSelectedRow(); if (viewRow<0){ JOptionPane.showMessageDialog(frame,"Select customer first"); return; }
            int modelRow = table.convertRowIndexToModel(viewRow);
            int id=(int)customerTableModel.getValueAt(modelRow,0); Customer c=findCustomerById(id);
            if (c==null){ JOptionPane.showMessageDialog(frame,"Customer not found"); return; }
            JTextField n=new JTextField(c.name); JTextField ph=new JTextField(c.phone);
            JPanel f=new JPanel(new GridLayout(0,2,6,6)); f.add(new JLabel("Name:")); f.add(n); f.add(new JLabel("Phone:")); f.add(ph);
            int res = JOptionPane.showConfirmDialog(frame,f,"Edit customer",JOptionPane.OK_CANCEL_OPTION);
            if (res==JOptionPane.OK_OPTION){ c.name=n.getText().trim(); c.phone=ph.getText().trim(); refreshCustomerTable(); }
        });

        del.addActionListener(e -> {
            int viewRow = table.getSelectedRow(); if (viewRow<0){ JOptionPane.showMessageDialog(frame,"Select customer first"); return; }
            int modelRow = table.convertRowIndexToModel(viewRow);
            int id=(int)customerTableModel.getValueAt(modelRow,0);
            int confirm = JOptionPane.showConfirmDialog(frame, "Delete customer ID " + id + " ?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm==JOptionPane.YES_OPTION){ customers.removeIf(c->c.id==id); refreshCustomerTable(); }
        });

        return p;
    }

    private void refreshCustomerTable(){ if (customerTableModel==null) return; customerTableModel.setRowCount(0); for (Customer c: customers) customerTableModel.addRow(new Object[]{c.id,c.name,c.phone}); }
    private Customer findCustomerById(int id){ for (Customer c: customers) if (c.id==id) return c; return null; }

    // -------------------- Orders Panel --------------------
    private JPanel buildOrdersPanel(){
        JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false);
        orderTableModel = new DefaultTableModel(new Object[]{"OrderID","Customer","Date","Total"},0){ public boolean isCellEditable(int r,int c){return false;} };
        JTable table = new JTable(orderTableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refreshOrderTable();
        p.add(new JScrollPane(table), BorderLayout.CENTER);
        JPanel bottom = new JPanel(); bottom.setOpaque(false);
        JButton view = new JButton("View Invoice"); JButton del = new JButton("Delete Order");
        bottom.add(view); bottom.add(del); p.add(bottom, BorderLayout.SOUTH);

        view.addActionListener(e -> {
            int viewRow = table.getSelectedRow(); if (viewRow<0){ JOptionPane.showMessageDialog(frame,"Select order first"); return; }
            int modelRow = table.convertRowIndexToModel(viewRow);
            int id=(int)orderTableModel.getValueAt(modelRow,0); OrderRec o=findOrderById(id);
            if (o==null){ JOptionPane.showMessageDialog(frame,"Order not found"); return; }
            showInvoiceDialog(o);
        });
        del.addActionListener(e -> {
            int viewRow = table.getSelectedRow(); if (viewRow<0){ JOptionPane.showMessageDialog(frame,"Select order first"); return; }
            int modelRow = table.convertRowIndexToModel(viewRow);
            int id=(int)orderTableModel.getValueAt(modelRow,0);
            int confirm = JOptionPane.showConfirmDialog(frame, "Delete order ID " + id + " ?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm==JOptionPane.YES_OPTION){ orders.removeIf(or->or.id==id); refreshOrderTable(); JOptionPane.showMessageDialog(frame,"Order deleted"); }
        });

        return p;
    }

    private void refreshOrderTable(){ if (orderTableModel==null) return; orderTableModel.setRowCount(0); SimpleDateFormat df=new SimpleDateFormat("yyyy-MM-dd HH:mm"); for (OrderRec o: orders) orderTableModel.addRow(new Object[]{o.id, o.customer.name, df.format(o.date), String.format("%.2f", o.total)}); }
    private OrderRec findOrderById(int id){ for (OrderRec o: orders) if (o.id==id) return o; return null; }

    private void showInvoiceDialog(OrderRec o){
        JTextArea ta = new JTextArea(20,60); ta.setEditable(false);
        ta.setText(generateInvoiceText(o));
        JScrollPane sp = new JScrollPane(ta);
        int res = JOptionPane.showOptionDialog(frame, sp, "Invoice - Order " + o.id, JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, new Object[]{"Save As Text","Close"}, "Close");
        if (res==0){ try{ JFileChooser fc = new JFileChooser(); if (fc.showSaveDialog(frame)==JFileChooser.APPROVE_OPTION){ File f=fc.getSelectedFile(); try (FileWriter fw=new FileWriter(f)){ fw.write(ta.getText()); } JOptionPane.showMessageDialog(frame,"Saved to "+f.getAbsolutePath()); } } catch(Exception ex){ JOptionPane.showMessageDialog(frame,"Failed to save: "+ex.getMessage()); } }
    }

    private String generateInvoiceText(OrderRec o){ StringBuilder sb=new StringBuilder(); sb.append("STORE MANAGEMENT SYSTEM\n"); sb.append("Invoice for Order: "+o.id+"\n"); sb.append("Date: "+ new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(o.date)+"\n"); sb.append("Customer: "+o.customer.name+" ("+o.customer.phone+")\n\n"); sb.append(String.format("%-5s %-25s %-8s %-8s\n","#","Item","Qty","Price")); sb.append("-----------------------------------------------------------\n"); int i=1; for (OrderItem it: o.items){ sb.append(String.format("%-5d %-25s %-8d %-8.2f\n", i++, it.p.name, it.qty, it.p.price*it.qty)); }
        sb.append("\nTotal: "+String.format("%.2f", o.total)+"\n"); sb.append("\nThank you for shopping!\n"); return sb.toString(); }

    // -------------------- Place Order Panel --------------------
    private JPanel buildPlaceOrderPanel(){
        JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false);
        // left: product list, right: cart
        DefaultTableModel productListModel = new DefaultTableModel(new Object[]{"ID","Name","Price","Stock"},0){
            @Override
            public boolean isCellEditable(int r,int c){return false;}
            @Override
            public Class<?> getColumnClass(int columnIndex){
                if (columnIndex==0) return Integer.class;
                if (columnIndex==2) return Double.class;
                if (columnIndex==3) return Integer.class;
                return String.class;
            }
        };
        JTable pt = new JTable(productListModel);
        pt.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        refreshProductList(productListModel);
        JPanel left = new JPanel(new BorderLayout()); left.setOpaque(false); left.add(new JLabel("Products"), BorderLayout.NORTH); left.add(new JScrollPane(pt), BorderLayout.CENTER);
        JPanel leftBtns = new JPanel(); leftBtns.setOpaque(false); JButton addToCart = new JButton("Add to Cart"); leftBtns.add(addToCart); left.add(leftBtns, BorderLayout.SOUTH);

        DefaultTableModel cartModel = new DefaultTableModel(new Object[]{"ID","Name","Qty","Price"},0){
            @Override public boolean isCellEditable(int r,int c){ return false; }
            @Override public Class<?> getColumnClass(int columnIndex){
                if (columnIndex==0) return Integer.class;
                if (columnIndex==2) return Integer.class;
                if (columnIndex==3) return Double.class;
                return String.class;
            }
        };
        JTable cartT = new JTable(cartModel);
        cartT.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JPanel right = new JPanel(new BorderLayout()); right.setOpaque(false); right.add(new JLabel("Cart"), BorderLayout.NORTH); right.add(new JScrollPane(cartT), BorderLayout.CENTER);
        JPanel rightBtns = new JPanel(); rightBtns.setOpaque(false);
        JButton removeFromCart = new JButton("Remove"); JButton place = new JButton("Place Order"); JButton barcodeBtn = new JButton("Show Barcode");
        rightBtns.add(removeFromCart); rightBtns.add(barcodeBtn); rightBtns.add(place); right.add(rightBtns, BorderLayout.SOUTH);

        JSplitPane sp = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, left, right); sp.setDividerLocation(620);
        p.add(sp, BorderLayout.CENTER);

        addToCart.addActionListener(e -> {
            int viewRow = pt.getSelectedRow(); if (viewRow<0){ JOptionPane.showMessageDialog(frame,"Select product to add"); return; }
            int modelRow = pt.convertRowIndexToModel(viewRow);
            int id = (int)productListModel.getValueAt(modelRow,0); Product pr = findProductById(id);
            if (pr == null){ JOptionPane.showMessageDialog(frame,"Product not found"); return; }
            try{
                String q = JOptionPane.showInputDialog(frame, "Quantity:", "1"); if (q==null) return; int qty = Integer.parseInt(q.trim());
                if (qty<=0 || qty>pr.stock){ JOptionPane.showMessageDialog(frame,"Invalid quantity or not enough stock"); return; }
                // add to cart (merge if same product)
                boolean found=false; for (int i=0;i<cartModel.getRowCount();i++){
                    if (((Number)cartModel.getValueAt(i,0)).intValue() == pr.id){
                        int existing = ((Number)cartModel.getValueAt(i,2)).intValue();
                        cartModel.setValueAt(existing+qty, i, 2);
                        found=true; break;
                    }
                }
                if (!found) cartModel.addRow(new Object[]{pr.id, pr.name, qty, pr.price});
            } catch(NumberFormatException ex){ JOptionPane.showMessageDialog(frame,"Invalid qty: " + ex.getMessage()); }
            catch(Exception ex){ JOptionPane.showMessageDialog(frame,"Invalid qty"); }
        });

        removeFromCart.addActionListener(e -> { int r = cartT.getSelectedRow(); if (r<0){ JOptionPane.showMessageDialog(frame,"Select cart row"); return; } cartModel.removeRow(r); });

        place.addActionListener(e -> {
            if (cartModel.getRowCount()==0){ JOptionPane.showMessageDialog(frame,"Cart is empty"); return; }
            // choose customer
            String[] names = customers.stream().map(c->c.id+" - "+c.name).toArray(String[]::new);
            String custSel = names.length>0 ? (String) JOptionPane.showInputDialog(frame, "Select customer:", "Customer", JOptionPane.PLAIN_MESSAGE, null, names, names[0]) : null;
            if (custSel==null){ JOptionPane.showMessageDialog(frame, "Please add a customer first from Customers tab"); return; }
            int cid = Integer.parseInt(custSel.split(" - ")[0]); Customer c = findCustomerById(cid);
            java.util.List<OrderItem> items = new ArrayList<>();
            for (int i=0;i<cartModel.getRowCount();i++){
                int pid = ((Number)cartModel.getValueAt(i,0)).intValue();
                int qty = ((Number)cartModel.getValueAt(i,2)).intValue();
                Product pr = findProductById(pid);
                if (pr == null){ JOptionPane.showMessageDialog(frame,"Product missing (id="+pid+"), aborting order"); return; }
                if (qty > pr.stock){ JOptionPane.showMessageDialog(frame,"Not enough stock for " + pr.name + ", aborting order"); return; }
                items.add(new OrderItem(pr, qty)); pr.stock -= qty;
            }
            OrderRec or = new OrderRec(nextOrderId++, c, items, new Date()); orders.add(or);
            refreshOrderTable(); refreshProductTable(); refreshProductList(productListModel); cartModel.setRowCount(0);
            JOptionPane.showMessageDialog(frame, "Order placed. Order ID: " + or.id);
            // show invoice
            showInvoiceDialog(or);
        });

        barcodeBtn.addActionListener(e -> {
            int viewRow = pt.getSelectedRow(); if (viewRow<0){ JOptionPane.showMessageDialog(frame,"Select a product first"); return; }
            int modelRow = pt.convertRowIndexToModel(viewRow);
            int id = (int)productListModel.getValueAt(modelRow,0); Product pr = findProductById(id);
            if (pr == null){ JOptionPane.showMessageDialog(frame,"Product not found"); return; }
            BufferedImage bi = generateSimpleBarcode(pr.code);
            ImageIcon ic = new ImageIcon(bi.getScaledInstance(bi.getWidth()*2, bi.getHeight()*2, Image.SCALE_SMOOTH));
            JLabel lbl = new JLabel(ic);
            JOptionPane.showMessageDialog(frame, lbl, "Barcode for "+pr.name, JOptionPane.PLAIN_MESSAGE);
        });

        return p;
    }

    private void refreshProductList(DefaultTableModel model){ model.setRowCount(0); for (Product p: products) model.addRow(new Object[]{p.id, p.name, p.price, p.stock}); }

    // -------------------- Show Products Panel --------------------
    private JPanel buildShowProductsPanel(){
        JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false);
        JTextArea ta = new JTextArea(); ta.setEditable(false);
        JButton refresh = new JButton("Refresh");
        p.add(new JScrollPane(ta), BorderLayout.CENTER); p.add(refresh, BorderLayout.SOUTH);
        refresh.addActionListener(e -> { StringBuilder sb=new StringBuilder(); for (Product pr: products){ sb.append(String.format("%d - %s | Price: %.2f | Stock: %d | Code: %s\n", pr.id, pr.name, pr.price, pr.stock, pr.code)); } ta.setText(sb.toString()); });
        refresh.doClick();
        return p;
    }

    // -------------------- Reports Panel --------------------
    private JPanel buildReportsPanel(){
        JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false);
        JTextArea ta = new JTextArea(); ta.setEditable(false);
        JButton gen = new JButton("Generate Report");
        p.add(new JScrollPane(ta), BorderLayout.CENTER); p.add(gen, BorderLayout.SOUTH);
        gen.addActionListener(e -> { ta.setText(generateReport()); });
        return p;
    }

    private String generateReport(){ StringBuilder sb=new StringBuilder(); sb.append("=== Simple Sales Report ===\n"); sb.append("Total Orders: "+orders.size()+"\n"); double totalSales = orders.stream().mapToDouble(o->o.total).sum(); sb.append(String.format("Total Sales: %.2f\n", totalSales));
        Map<String,Integer> sold = new HashMap<>();
        for (OrderRec o: orders){ for (OrderItem it: o.items){ sold.put(it.p.name, sold.getOrDefault(it.p.name,0)+it.qty); } }
        sb.append("\nTop Products:\n"); sold.entrySet().stream().sorted((a,b)->b.getValue()-a.getValue()).limit(10).forEach(e-> sb.append(e.getKey()+" - "+e.getValue()+" sold\n"));
        return sb.toString(); }

    // -------------------- User Management Panel (Admin) --------------------
    private JPanel buildUserManagementPanel(){
        JPanel p = new JPanel(new BorderLayout()); p.setOpaque(false);

        DefaultTableModel userModel = new DefaultTableModel(new Object[]{"Username","Role"},0){ public boolean isCellEditable(int r,int c){return false;} };
        JTable table = new JTable(userModel);
        refreshUserModel(userModel);
        p.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel bottom = new JPanel(); bottom.setOpaque(false);
        JButton add = new JButton("Add User"); JButton edit = new JButton("Edit Role/Password"); JButton del = new JButton("Delete User");
        bottom.add(add); bottom.add(edit); bottom.add(del);
        p.add(bottom, BorderLayout.SOUTH);

        add.addActionListener(e -> {
            JPanel form = new JPanel(new GridLayout(0,2,6,6));
            JTextField uname = new JTextField(); JPasswordField pwd = new JPasswordField(); JPasswordField pwd2 = new JPasswordField();
            JComboBox<String> roleBox = new JComboBox<>(new String[]{"admin","employee","user"});
            form.add(new JLabel("Username:")); form.add(uname);
            form.add(new JLabel("Password:")); form.add(pwd);
            form.add(new JLabel("Confirm Password:")); form.add(pwd2);
            form.add(new JLabel("Role:")); form.add(roleBox);
            int r = JOptionPane.showConfirmDialog(frame, form, "Add User", JOptionPane.OK_CANCEL_OPTION);
            if (r==JOptionPane.OK_OPTION){
                String un = uname.getText().trim(); String p1 = new String(pwd.getPassword()); String p2 = new String(pwd2.getPassword()); String role = (String)roleBox.getSelectedItem();
                if (un.isEmpty() || p1.isEmpty()){ JOptionPane.showMessageDialog(frame,"Username & password required"); return; }
                if (!p1.equals(p2)){ JOptionPane.showMessageDialog(frame,"Passwords do not match"); return; }
                if (users.containsKey(un)){ JOptionPane.showMessageDialog(frame,"User exists"); return; }
                users.put(un, new User(un,p1,role)); refreshUserModel(userModel);
            }
        });

        edit.addActionListener(e2 -> {
            int r = table.getSelectedRow(); if (r<0){ JOptionPane.showMessageDialog(frame,"Select a user"); return; }
            String uname = (String)userModel.getValueAt(r,0);
            User u = users.get(uname);
            if (u==null){ JOptionPane.showMessageDialog(frame,"User not found"); return; }
            JPanel form = new JPanel(new GridLayout(0,2,6,6));
            JPasswordField newPwd = new JPasswordField(); JPasswordField newPwd2 = new JPasswordField(); JComboBox<String> roleBox = new JComboBox<>(new String[]{"admin","employee","user"});
            roleBox.setSelectedItem(u.role);
            form.add(new JLabel("New Password (leave empty to keep):")); form.add(newPwd);
            form.add(new JLabel("Confirm New Password:")); form.add(newPwd2);
            form.add(new JLabel("Role:")); form.add(roleBox);
            int rr = JOptionPane.showConfirmDialog(frame, form, "Edit User", JOptionPane.OK_CANCEL_OPTION);
            if (rr==JOptionPane.OK_OPTION){
                String p1 = new String(newPwd.getPassword()); String p2 = new String(newPwd2.getPassword()); String role = (String)roleBox.getSelectedItem();
                if (!p1.isEmpty() && !p1.equals(p2)){ JOptionPane.showMessageDialog(frame,"Passwords do not match"); return; }
                if (!p1.isEmpty()) u.password = p1;
                u.role = role;
                users.put(u.username, u);
                refreshUserModel(userModel);
            }
        });

        del.addActionListener(e3 -> {
            int r = table.getSelectedRow(); if (r<0){ JOptionPane.showMessageDialog(frame,"Select a user"); return; }
            String uname = (String)userModel.getValueAt(r,0);
            if ("admin".equals(uname) && users.size()==1){ JOptionPane.showMessageDialog(frame,"Cannot delete the last admin"); return; }
            int conf = JOptionPane.showConfirmDialog(frame,"Delete user "+uname+"?","Confirm",JOptionPane.YES_NO_OPTION);
            if (conf==JOptionPane.YES_OPTION){ users.remove(uname); refreshUserModel(userModel); }
        });
        return p;
    }
    private void refreshUserModel(DefaultTableModel model){
        model.setRowCount(0);
        for (User u : users.values()){
            model.addRow(new Object[]{u.username, u.role});
        }
    }
    // -------------------- Utilities --------------------
    private void chooseAndSetBackground(){ JFileChooser fc = new JFileChooser(); if (fc.showOpenDialog(frame)==JFileChooser.APPROVE_OPTION){ File f = fc.getSelectedFile(); try{ backgroundImage = ImageIO.read(f); frame.repaint(); } catch(Exception ex){ JOptionPane.showMessageDialog(frame, "Failed to load image: "+ex.getMessage()); } } }
    private BufferedImage generateSimpleBarcode(String data){
        if (data==null) data = "000000";
        byte[] bytes = data.getBytes();
        int w = 300, h = 80; BufferedImage bi = new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
        Graphics2D g = bi.createGraphics(); g.setColor(Color.white); g.fillRect(0,0,w,h);
        g.setColor(Color.black);
        // use hash to decide pattern
        int x=10;
        for (int i=0;i<bytes.length && x<w-10;i++){
            int v = (bytes[i] & 0xFF) % 7 + 1; // 1..7
            for (int k=0;k<v;k++){
                int width = 2 + (v%3);
                g.fillRect(x, 10, width, h-20);
                x += width + 2;
                if (x>w-20) break;
            }
            x += 2;
        }
        // draw text me
        g.setFont(new Font("Monospaced", Font.PLAIN, 12)); g.drawString(data, 10, h-4);
        g.dispose(); return bi;
    }
    private String capitalize(String s){ if (s==null||s.isEmpty()) return s; return Character.toUpperCase(s.charAt(0))+s.substring(1); }

    private void populateSampleData(){
        products.add(new Product(nextProductId++, "Apple iPhone 14", 59999, 10, "IP14-001"));
        products.add(new Product(nextProductId++, "Samsung Galaxy S23", 49999, 8, "SGS23-001"));
        products.add(new Product(nextProductId++, "HP Laptop 15", 74999, 6, "HP15-101"));
        customers.add(new Customer(nextCustomerId++, "Rahul Sharma", "9876543210"));
        customers.add(new Customer(nextCustomerId++, "Anita Verma", "9123456780"));
    }
}
