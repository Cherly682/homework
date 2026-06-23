package Admin.View;

import Admin.Model.UsersModel;
import Login.Model.UserModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;

public class UsersView extends JPanel {
    private static final Logger log = LoggerFactory.getLogger(UsersView.class);

    // ==================== Swing 组件 ====================
    private JTable usersTable;
    private DefaultTableModel tableModel;
    private JButton refreshButton;
    private JButton addButton;
    private JButton deleteButton;
    private JButton modifyButton;
    private JTextField usernameField;
    private JTextField passwordField;
    private JComboBox<String> roleComboBox;

    public UsersView() {
        initializeView();
    }


    private void initializeView() {
        setLayout(new BorderLayout(10, 10));
        // 内边距
        setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // ==================== 创建表格 ====================


        String[] columnNames = {"用户名", "加密密码", "角色"};

        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return Object.class;
            }

            @Override
            public boolean isCellEditable(int row, int column) {
                //列 0不可编辑
                return column != 0;
            }
        };


        usersTable = new JTable(tableModel);
        usersTable.setFont(new Font("Microsoft YaHei", Font.PLAIN, 15));
        usersTable.setRowHeight(30);
        usersTable.getTableHeader().setFont(new Font("Microsoft YaHei", Font.BOLD, 15));
        usersTable.setAutoResizeMode(JTable.AUTO_RESIZE_ALL_COLUMNS);

        usersTable.getColumnModel().getColumn(0).setPreferredWidth(100);
        usersTable.getColumnModel().getColumn(1).setPreferredWidth(350);
        usersTable.getColumnModel().getColumn(2).setPreferredWidth(80);


        int roleId = 2;
        usersTable.getColumnModel().getColumn(roleId).setCellRenderer(new DefaultTableCellRenderer());
        usersTable.getColumnModel().getColumn(roleId).setCellEditor(
            new DefaultCellEditor(createRoleComboBox())
        );


        JScrollPane scrollPane = new JScrollPane(usersTable);
        scrollPane.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        add(scrollPane, BorderLayout.CENTER);

        // ==================== 创建按钮面板 ====================
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        Font btnFont = new Font("Microsoft YaHei", Font.PLAIN, 15);

        refreshButton = new JButton("刷新");
        refreshButton.setFont(btnFont);
        addButton = new JButton("添加用户");
        addButton.setFont(btnFont);
        deleteButton = new JButton("删除用户");
        deleteButton.setFont(btnFont);
        modifyButton = new JButton("修改用户");
        modifyButton.setFont(btnFont);

        buttonPanel.add(refreshButton);
        buttonPanel.add(addButton);
        buttonPanel.add(modifyButton);
        buttonPanel.add(deleteButton);

        add(buttonPanel, BorderLayout.SOUTH);

        // ==================== 创建输入面板 ====================
        JPanel inputPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        inputPanel.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        Font inputFont = new Font("Microsoft YaHei", Font.PLAIN, 15);


        JLabel usernameLabel = new JLabel("用户名:");
        usernameLabel.setFont(inputFont);
        usernameField = new JTextField(12);
        usernameField.setFont(inputFont);
        inputPanel.add(usernameLabel);
        inputPanel.add(usernameField);


        JLabel passwordLabel = new JLabel("密码:");
        passwordLabel.setFont(inputFont);
        passwordField = new JTextField(12);
        passwordField.setFont(inputFont);
        inputPanel.add(passwordLabel);
        inputPanel.add(passwordField);


        JLabel roleLabel = new JLabel("角色:");
        roleLabel.setFont(inputFont);
        roleComboBox = new JComboBox<>(UsersModel.ROLES);
        roleComboBox.setFont(inputFont);
        inputPanel.add(roleLabel);
        inputPanel.add(roleComboBox);

        add(inputPanel, BorderLayout.NORTH);
    }


    private JComboBox<String> createRoleComboBox() {
        return new JComboBox<>(UsersModel.ROLES);
    }


    public void setUsers(List<UserModel> users) {
        DefaultTableModel model = (DefaultTableModel) usersTable.getModel();
        model.setRowCount(0);
        for (UserModel user : users) {
            model.addRow(new Object[]{
                user.getUsername(),
                user.getPassword(),
                user.getRole()
            });
        }
    }

    // ==================== 事件监听器的"插座"方法 ====================

    public void addActionListener(ActionListener listener) {
        refreshButton.addActionListener(listener);
    }

    public void addRefreshButtonListener(ActionListener listener) {
        refreshButton.addActionListener(listener);
    }

    public void addAddButtonListener(ActionListener listener) {
        addButton.addActionListener(listener);
    }

    public void addDeleteButtonListener(ActionListener listener) {
        deleteButton.addActionListener(listener);
    }

    public void addModifyButtonListener(ActionListener listener) {
        modifyButton.addActionListener(listener);
    }

    public void addTableSelectionListener(javax.swing.event.ListSelectionListener listener) {
        usersTable.getSelectionModel().addListSelectionListener(listener);
    }

    // ==================== Getter / Setter ====================

    public String getUsername()    { return usernameField.getText(); }
    public String getPassword()    { return passwordField.getText(); }
    public String getRole()        { return (String) roleComboBox.getSelectedItem(); }

    public void setUsername(String username) { usernameField.setText(username); }
    public void setPassword(String password) { passwordField.setText(password); }
    public void setRole(String role)         { roleComboBox.setSelectedItem(role); }

    public JTable getUsersTable() { return usersTable; }


    public UserModel getSelectedUser() {
        int selectedRow = usersTable.getSelectedRow();
        if (selectedRow >= 0) {
            String username = (String) usersTable.getValueAt(selectedRow, 0);
            String password = (String) usersTable.getValueAt(selectedRow, 1);
            String role = (String) usersTable.getValueAt(selectedRow, 2);
            log.debug("Selected user: username={} role={}", username, role);
            return UserModel.fromRedis(username, password, role);
        }
        return null;
    }
}
