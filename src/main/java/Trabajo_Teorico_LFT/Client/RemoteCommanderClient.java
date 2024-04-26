package Trabajo_Teorico_LFT.Client;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class RemoteCommanderClient {
    private String host;
    private int port;
    private boolean useSSL;
    private String clientDirectory;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private Scanner scanner;

    public RemoteCommanderClient(String host, int port, boolean useSSL, String clientDirectory) {
        this.host = host;
        this.port = port;
        this.useSSL = useSSL;
        this.clientDirectory = clientDirectory;
    }

    public void connect() {
        try {
            socket = useSSL ? createSSLSocket() : new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            scanner = new Scanner(System.in);

            // Send ping message
            out.println("PING");
            socket.setSoTimeout(5000); // Set a timeout for response
            String response = in.readLine();
            if (response != null && response.equals("PONG")) {
                System.out.println("Server is reachable.");
            } else {
                System.out.println("Server is not reachable.");
                closeConnection();
                return;
            }

            // Handle commands
            while (true) {
                System.out.print("Enter command: ");
                String command = scanner.nextLine();
                if ("exit".equalsIgnoreCase(command)) {
                    break;
                }
                executeCommand(command);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            closeConnection();
        }
    }

    private void executeCommand(String command) {
        try {
            if (command.startsWith("SEND")) {
               sendFile(command);
            } else if (command.startsWith("RECEIVE")) {
               //TODO  receiveFile(command);
            } else if (command.startsWith("LIST")) {
                handleList(command);
            } else if (command.startsWith("EXEC")) {
                // TODO
            }else if (command.equals("FIN")){
                out.println(command);
                out.flush();
                closeConnection(); // Cierra la conexión después de enviar FIN
                // Parar la ejecucion
                System.exit(0);

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleList(String command) throws IOException {
        out.println(command); // Enviar el comando al servidor
        out.flush(); // Forzar la escritura del mensaje

        // Leer la respuesta del servidor y mostrarla en la consola
        String response;
        while ((response = in.readLine()) != null) {
            if (response.equals("END")) {
                break; // Terminar el bucle si se recibe la señal de fin
            }
            System.out.println(response);
        }
    }
    // TODO TERMINAR SEND
    private void sendFile(String command) throws IOException {
        String[] parts = command.split(" ");
        String filePath = parts[1];
        String remoteDirectory = parts[2];
        File file = new File(filePath);

        if (!file.exists() || file.isDirectory()) {
            System.out.println("El archivo no existe o es un directorio.");
            return;
        }

        long fileSize = file.length();
        DataOutputStream dataOut = new DataOutputStream(socket.getOutputStream());
        dataOut.writeUTF("SEND " + file.getName() + " " + remoteDirectory); // Envía el comando con UTF para manejar correctamente el texto
        dataOut.writeLong(fileSize); // Envía el tamaño del archivo

        try (BufferedInputStream fileInputStream = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileInputStream.read(buffer)) != -1) {
                dataOut.write(buffer, 0, bytesRead);  // Envía el contenido del archivo
            }
            dataOut.flush();
        }

        // Lee la respuesta del servidor usando DataInputStream
        DataInputStream dataIn = new DataInputStream(socket.getInputStream());
        String serverResponse = dataIn.readUTF();
        System.out.println(serverResponse);
    }


    private void receiveFile(String command) throws IOException {
        // TODO
    }

    private void closeConnection() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private SSLSocket createSSLSocket() throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        return (SSLSocket) factory.createSocket(host, port);
    }

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: java RemoteCommanderClient <modo> <host> <port> <client_directory>");
            System.exit(1);
        }

        boolean useSSL = false;
        String host = null;
        int port = 0;
        String clientDirectory = null;

        for (String arg : args) {
            String[] parts = arg.split("=");
            if (parts[0].equalsIgnoreCase("modo")) {
                useSSL = parts[1].equalsIgnoreCase("SSL");
            } else if (parts[0].equalsIgnoreCase("host")) {
                host = parts[1];
            } else if (parts[0].equalsIgnoreCase("puerto")) {
                port = Integer.parseInt(parts[1]);
            } else if (parts[0].equalsIgnoreCase("carpeta_cliente")) {
                clientDirectory = parts[1];
            }
        }

        if (host == null || port == 0 || clientDirectory == null) {
            System.out.println("Invalid arguments provided.");
            System.exit(1);
        }

        RemoteCommanderClient client = new RemoteCommanderClient(host, port, useSSL, clientDirectory);
        client.connect();
    }
}
