import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.util.*;

public class SimplePOP3ClientSSL {
    public static void main(String[] args) {
        String server = "pop.gmail.com";
        int port = 995;
        String user = "retiefphillip99@gmail.com";  // your Gmail address
        String pass = "xdsf tbzo wvuj tuyt";        // your app-specific password

        try {
            SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
            try (SSLSocket socket = (SSLSocket) factory.createSocket(server, port);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                 PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

                System.out.println(reader.readLine()); // server greeting

                // Authenticate
                writer.println("USER " + user);
                System.out.println(reader.readLine());

                writer.println("PASS " + pass);
                System.out.println(reader.readLine());

                // --- (2) STAT command: show mailbox info ---
                writer.println("STAT");
                String statResponse = reader.readLine();
                System.out.println("\n📬 Mailbox Info: " + statResponse);

                // --- List available messages ---
                writer.println("LIST");
                List<Integer> messageIds = new ArrayList<>();
                String line;
                while (!(line = reader.readLine()).equals(".")) {
                    System.out.println(line);
                    String[] parts = line.split(" ");
                    if (parts.length >= 1) {
                        try {
                            int msgId = Integer.parseInt(parts[0]);
                            messageIds.add(msgId);
                        } catch (NumberFormatException ignored) {}
                    }
                }

                // --- Fetch and store headers ---
                Map<Integer, String> messageInfo = new LinkedHashMap<>();
                for (int msgId : messageIds) {
                    writer.println("TOP " + msgId + " 0");
                    StringBuilder headers = new StringBuilder();
                    while (!(line = reader.readLine()).equals(".")) {
                        headers.append(line).append("\n");
                    }
                    String from = extractHeader(headers.toString(), "From");
                    String subject = extractHeader(headers.toString(), "Subject");
                    messageInfo.put(msgId, "ID: " + msgId + ", From: " + from + ", Subject: " + subject);
                }

                // --- Display all messages ---
                System.out.println("\n--- Email Header Summary ---");
                for (Map.Entry<Integer, String> entry : messageInfo.entrySet()) {
                    System.out.println(entry.getValue());
                }

                // --- (3) View full headers of specific message ---
                Scanner sc = new Scanner(System.in);
                System.out.println("\nEnter a message ID to view full headers (or press Enter to skip):");
                String viewId = sc.nextLine().trim();
                if (!viewId.isEmpty()) {
                    try {
                        int id = Integer.parseInt(viewId);
                        if (messageIds.contains(id)) {
                            writer.println("TOP " + id + " 0");
                            System.out.println("\n--- Full Headers for Message " + id + " ---");
                            while (!(line = reader.readLine()).equals(".")) {
                                System.out.println(line);
                            }
                        } else {
                            System.out.println("Invalid ID.");
                        }
                    } catch (NumberFormatException ignored) {}
                }

                // --- Allow deletion ---
                System.out.println("\nEnter message IDs to delete (separated by spaces), or press Enter to skip:");
                String input = sc.nextLine().trim();
                if (!input.isEmpty()) {
                    String[] idsToDelete = input.split("\\s+");
                    for (String idStr : idsToDelete) {
                        try {
                            int id = Integer.parseInt(idStr);
                            if (messageIds.contains(id)) {
                                writer.println("DELE " + id);
                                System.out.println(reader.readLine());
                            }
                        } catch (NumberFormatException ignored) {}
                    }
                }

                writer.println("QUIT");
                System.out.println(reader.readLine());

            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());

        }
    }

    private static String extractHeader(String headers, String field) {
        for (String line : headers.split("\n")) {
            if (line.toLowerCase().startsWith(field.toLowerCase() + ":")) {
                return line.substring(field.length() + 1).trim();
            }
        }
        return "(none)";
    }
}
