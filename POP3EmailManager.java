import java.io.*;
import java.net.*;
import java.util.*;
import java.util.List; // Explicit import for List interface

import javax.net.ssl.SSLSocketFactory;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;

public class POP3EmailManager extends JFrame {
    private JTextField serverField, portField, userField;
    private JPasswordField passwordField;
    private JButton connectButton, deleteButton, refreshButton;
    private JTable emailTable;
    private DefaultTableModel tableModel;
    private Socket socket;
    private BufferedReader reader;
    private PrintWriter writer;
    private ArrayList<Email> emails;
    private Set<Integer> deletedMessages; // Track deleted messages
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new POP3EmailManager().setVisible(true);
            }
        });
    }
    
    public POP3EmailManager() {
        setTitle("POP3 Email Manager");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        
        // Create connection panel
        JPanel connectionPanel = new JPanel(new GridLayout(2, 4, 5, 5));
        connectionPanel.setBorder(BorderFactory.createTitledBorder("Connection Settings"));
        
        connectionPanel.add(new JLabel("POP3 Server:"));
        serverField = new JTextField("pop.gmail.com");
        connectionPanel.add(serverField);
        
        connectionPanel.add(new JLabel("Port:"));
        portField = new JTextField("995");
        connectionPanel.add(portField);
        
        connectionPanel.add(new JLabel("Username:"));
        userField = new JTextField("retiefphillip99@gmail.com");
        connectionPanel.add(userField);
        
        connectionPanel.add(new JLabel("Password:"));
        passwordField = new JPasswordField();
        connectionPanel.add(passwordField);
        
        // Create button panel
        JPanel buttonPanel = new JPanel();
        connectButton = new JButton("Connect");
        deleteButton = new JButton("Delete Selected");
        refreshButton = new JButton("Refresh");
        deleteButton.setEnabled(false);
        refreshButton.setEnabled(false);
        buttonPanel.add(connectButton);
        buttonPanel.add(deleteButton);
        buttonPanel.add(refreshButton);
        
        // Create email table
        String[] columnNames = {"", "From", "Subject", "Size (KB)"};
        tableModel = new DefaultTableModel(columnNames, 0) {
            @Override
            public Class<?> getColumnClass(int column) {
                return column == 0 ? Boolean.class : String.class;
            }
            
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }
        };
        
        emailTable = new JTable(tableModel);
        emailTable.getColumnModel().getColumn(0).setMaxWidth(30);
        JScrollPane scrollPane = new JScrollPane(emailTable);
        
        // Add components to frame
        setLayout(new BorderLayout(5, 5));
        add(connectionPanel, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
        
        // Add action listeners
        connectButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                connectToPOP3Server();
            }
        });
        
        deleteButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                deleteSelectedEmails();
            }
        });
        
        refreshButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                refreshEmailList();
            }
        });
        
        emails = new ArrayList<>();
        deletedMessages = new HashSet<>(); // Initialize deleted messages set
    }
    
    private void connectToPOP3Server() {
        try {
            String server = serverField.getText();
            int port = Integer.parseInt(portField.getText());
            String username = userField.getText();
            String password = new String(passwordField.getPassword());
            
            // Connect to server
            SSLSocketFactory sslSocketFactory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            socket = sslSocketFactory.createSocket(server, port);

            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            writer = new PrintWriter(socket.getOutputStream(), true);
            
            // Read welcome message
            String response = reader.readLine();
            if (!response.startsWith("+OK")) {
                throw new IOException("Server did not send OK greeting: " + response);
            }
            
            // Send username
            writer.println("USER " + username);
            response = reader.readLine();
            if (!response.startsWith("+OK")) {
                throw new IOException("Error with USER command: " + response);
            }
            
            // Send password
            writer.println("PASS " + password);
            response = reader.readLine();
            if (!response.startsWith("+OK")) {
                throw new IOException("Error with PASS command: " + response);
            }
            
            // Reset deleted messages tracking
            deletedMessages.clear();
            
            // Change button states
            connectButton.setText("Disconnect");
            connectButton.removeActionListener(connectButton.getActionListeners()[0]);
            connectButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    disconnect();
                }
            });
            deleteButton.setEnabled(true);
            refreshButton.setEnabled(true);
            
            // Load emails
            refreshEmailList();
            
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, 
                "Connection error: " + ex.getMessage(), 
                "Error", JOptionPane.ERROR_MESSAGE);
            disconnect();
        }
    }
    
    private void refreshEmailList() {
        // Disable buttons during refresh
        deleteButton.setEnabled(false);
        refreshButton.setEnabled(false);
        connectButton.setEnabled(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        
        // Create loading indicator
        final JDialog loadingDialog = new JDialog(this, "Loading Emails", false);
        JPanel loadingPanel = new JPanel(new BorderLayout());
        JLabel loadingLabel = new JLabel("Loading emails, please wait...");
        loadingLabel.setHorizontalAlignment(JLabel.CENTER);
        JProgressBar indeterminateProgress = new JProgressBar();
        indeterminateProgress.setIndeterminate(true);
        
        loadingPanel.add(loadingLabel, BorderLayout.CENTER);
        loadingPanel.add(indeterminateProgress, BorderLayout.SOUTH);
        loadingPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        loadingDialog.add(loadingPanel);
        loadingDialog.setSize(250, 100);
        loadingDialog.setLocationRelativeTo(this);
        
        // Show the loading dialog after a short delay if operation is still running
        javax.swing.Timer showDialogTimer = new javax.swing.Timer(500, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadingDialog.setVisible(true);
            }
        });
        showDialogTimer.setRepeats(false);
        showDialogTimer.start();
        
        // Use SwingWorker to perform refresh in background
        SwingWorker<List<Email>, Void> worker = new SwingWorker<List<Email>, Void>() {
            @Override
            protected List<Email> doInBackground() throws Exception {
                List<Email> loadedEmails = new ArrayList<>();
                
                // Clear current data for refreshing
                emails.clear();
                
                // Get message list
                writer.println("STAT");
                String statResponse = reader.readLine();
                if (!statResponse.startsWith("+OK")) {
                    throw new IOException("Error with STAT command: " + statResponse);
                }
                
                // Parse number of messages
                String[] statParts = statResponse.split(" ");
                int messageCount = Integer.parseInt(statParts[1]);
                
                if (messageCount == 0) {
                    return loadedEmails; // Return empty list
                }
                
                writer.println("LIST");
                String listResponse = reader.readLine();
                if (!listResponse.startsWith("+OK")) {
                    throw new IOException("Error with LIST command: " + listResponse);
                }
                
                // Read message sizes
                Map<Integer, Integer> messageSizes = new HashMap<>();
                String line;
                while (!(line = reader.readLine()).equals(".")) {
                    String[] parts = line.split(" ");
                    int msgNum = Integer.parseInt(parts[0]);
                    int size = Integer.parseInt(parts[1]);
                    messageSizes.put(msgNum, size);
                }
                
                // Get headers for each message
                for (int msgNum = 1; msgNum <= messageCount; msgNum++) {
                    // Skip messages already marked for deletion
                    if (deletedMessages.contains(msgNum)) {
                        continue;
                    }
                    
                    try {
                        writer.println("TOP " + msgNum + " 0");
                        String topResponse = reader.readLine();
                        if (!topResponse.startsWith("+OK")) {
                            // If we get an error, this message might be deleted or have other issues
                            System.err.println("Error with TOP command for message " + msgNum + ": " + topResponse);
                            continue;
                        }
                        
                        StringBuilder headerData = new StringBuilder();
                        while (!(line = reader.readLine()).equals(".")) {
                            headerData.append(line).append("\r\n");
                        }
                        
                        // Parse headers
                        String from = "Unknown";
                        String subject = "No Subject";
                        
                        for (String header : headerData.toString().split("\r\n")) {
                            if (header.toLowerCase().startsWith("from:")) {
                                from = header.substring(5).trim();
                            } else if (header.toLowerCase().startsWith("subject:")) {
                                subject = header.substring(8).trim();
                            }
                        }
                        
                        // Add email to list
                        int sizeKB = messageSizes.getOrDefault(msgNum, 0) / 1024;
                        Email email = new Email(msgNum, from, subject, sizeKB);
                        loadedEmails.add(email);
                        
                        // Add a small delay to prevent overwhelming the server
                        Thread.sleep(20);
                    } catch (Exception e) {
                        // Log error but continue with other messages
                        System.err.println("Error processing message " + msgNum + ": " + e.getMessage());
                    }
                }
                
                // Sort emails by message number in descending order (newest first)
                Collections.sort(loadedEmails, new Comparator<Email>() {
                    @Override
                    public int compare(Email e1, Email e2) {
                        // Sort in descending order (newest first)
                        return Integer.compare(e2.messageNumber, e1.messageNumber);
                    }
                });
                
                return loadedEmails;
            }
            
            @Override
            protected void done() {
                // Close the loading dialog
                showDialogTimer.stop();
                loadingDialog.dispose();
                
                try {
                    List<Email> loadedEmails = get();
                    
                    // Update UI with loaded emails
                    emails.clear();
                    emails.addAll(loadedEmails);
                    
                    tableModel.setRowCount(0);
                    for (Email email : emails) {
                        tableModel.addRow(new Object[]{
                            Boolean.FALSE, email.from, email.subject, email.sizeKB
                        });
                    }
                    
                    if (emails.isEmpty()) {
                        JOptionPane.showMessageDialog(POP3EmailManager.this, 
                            "No emails in mailbox.", 
                            "Information", JOptionPane.INFORMATION_MESSAGE);
                    }
                    
                } catch (Exception ex) {
                    Throwable cause = ex.getCause();
                    JOptionPane.showMessageDialog(POP3EmailManager.this, 
                        "Error retrieving emails: " + (cause != null ? cause.getMessage() : ex.getMessage()), 
                        "Error", JOptionPane.ERROR_MESSAGE);
                } finally {
                    // Re-enable buttons
                    deleteButton.setEnabled(true);
                    refreshButton.setEnabled(true);
                    connectButton.setEnabled(true);
                    setCursor(Cursor.getDefaultCursor());
                }
            }
        };
        
        worker.execute();
    }
    
    private void deleteSelectedEmails() {
        // Find selected emails
        final List<Integer> toDelete = new ArrayList<>();
        for (int i = 0; i < tableModel.getRowCount(); i++) {
            Boolean selected = (Boolean) tableModel.getValueAt(i, 0);
            if (selected) {
                toDelete.add(emails.get(i).messageNumber);
            }
        }
        
        if (toDelete.isEmpty()) {
            JOptionPane.showMessageDialog(this, 
                "No emails selected for deletion.", 
                "Information", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        
        // Confirm deletion
        int confirm = JOptionPane.showConfirmDialog(this,
            "Delete " + toDelete.size() + " selected emails?",
            "Confirm Deletion", JOptionPane.YES_NO_OPTION);
            
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }
        
        // Disable buttons during operation
        deleteButton.setEnabled(false);
        refreshButton.setEnabled(false);
        connectButton.setEnabled(false);
        
        // Create progress dialog
        final JDialog progressDialog = new JDialog(this, "Deleting Messages", true);
        JProgressBar progressBar = new JProgressBar(0, toDelete.size());
        progressBar.setStringPainted(true);
        JLabel statusLabel = new JLabel("Deleting messages...");
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        progressDialog.add(panel);
        progressDialog.setSize(300, 100);
        progressDialog.setLocationRelativeTo(this);
        
        // Use SwingWorker to perform deletion in background
        SwingWorker<Void, Integer> worker = new SwingWorker<Void, Integer>() {
            @Override
            protected Void doInBackground() throws Exception {
                int count = 0;
                for (Integer msgNum : toDelete) {
                    writer.println("DELE " + msgNum);
                    String response = reader.readLine();
                    if (!response.startsWith("+OK")) {
                        throw new IOException("Error deleting message " + msgNum + ": " + response);
                    }
                    // Add to deleted messages set
                    deletedMessages.add(msgNum);
                    
                    count++;
                    publish(count);
                    
                    // Small delay to prevent overwhelming the server
                    Thread.sleep(50);
                }
                return null;
            }
            
            @Override
            protected void process(List<Integer> chunks) {
                // Update progress bar with the latest value
                int latestValue = chunks.get(chunks.size() - 1);
                progressBar.setValue(latestValue);
                statusLabel.setText("Deleted " + latestValue + " of " + toDelete.size() + " messages");
            }
            
            @Override
            protected void done() {
                progressDialog.dispose();
                try {
                    get(); // Check for exceptions
                    
                    JOptionPane.showMessageDialog(POP3EmailManager.this, 
                        toDelete.size() + " emails marked for deletion.\n" +
                        "They will be permanently removed when you disconnect.", 
                        "Success", JOptionPane.INFORMATION_MESSAGE);
                    
                    // Re-enable buttons
                    deleteButton.setEnabled(true);
                    refreshButton.setEnabled(true);
                    connectButton.setEnabled(true);
                    
                    // Refresh to show updated list
                    refreshEmailList();
                    
                } catch (Exception ex) {
                    // Re-enable buttons
                    deleteButton.setEnabled(true);
                    refreshButton.setEnabled(true);
                    connectButton.setEnabled(true);
                    
                    Throwable cause = ex.getCause();
                    JOptionPane.showMessageDialog(POP3EmailManager.this, 
                        "Error deleting emails: " + (cause != null ? cause.getMessage() : ex.getMessage()), 
                        "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        
        // Start the worker and show dialog
        worker.execute();
        
        // Show the progress dialog in another thread to avoid blocking
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                progressDialog.setVisible(true);
            }
        });
    }
    
    private void disconnect() {
        try {
            if (writer != null) {
                writer.println("QUIT");
                // No need to read response as connection will close
            }
            
            if (reader != null) reader.close();
            if (writer != null) writer.close();
            if (socket != null) socket.close();
            
        } catch (Exception ex) {
            // Ignore errors during disconnect
        } finally {
            reader = null;
            writer = null;
            socket = null;
            deletedMessages.clear(); // Clear deleted messages tracking
            
            // Reset UI
            connectButton.setText("Connect");
            connectButton.removeActionListener(connectButton.getActionListeners()[0]);
            connectButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    connectToPOP3Server();
                }
            });
            deleteButton.setEnabled(false);
            refreshButton.setEnabled(false);
            tableModel.setRowCount(0);
        }
    }
    
    private class Email {
        int messageNumber;
        String from;
        String subject;
        int sizeKB;
        
        public Email(int messageNumber, String from, String subject, int sizeKB) {
            this.messageNumber = messageNumber;
            this.from = from;
            this.subject = subject;
            this.sizeKB = sizeKB;
        }
    }
}