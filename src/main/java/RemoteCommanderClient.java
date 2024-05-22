import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
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
    private static final int __MAX_BUFFER = 1024;

    static File logCommandsFile = new File("acciones.log");
    static File logErrorsFile = new File("errores.log");

    public static void logCommands(String command) {
        try (PrintWriter writerLogCommandsFile = new PrintWriter(new FileWriter(logCommandsFile, true))) {
            writerLogCommandsFile.println(command);
        } catch (IOException e) {
            System.err.println("Error al escribir en el archivo de comandos: " + e.getMessage());
        }
    }

    public static void logErrors(String error) {
        try (PrintWriter writerLogErrorsFile = new PrintWriter(new FileWriter(logErrorsFile, true))) {
            writerLogErrorsFile.println(error);
        } catch (IOException e) {
            System.err.println("Error al escribir en el archivo de errores: " + e.getMessage());
        }
    }

    public RemoteCommanderClient(String host, int port, boolean useSSL, String clientDirectory) {
        this.host = host;
        this.port = port;
        this.useSSL = useSSL;
        this.clientDirectory = clientDirectory;
    }

    public void connect() {
        try {
            if (useSSL) {
                configureSSL();  // Configura SSL si es necesario
                socket = createSSLSocket();  // Crea un socket SSL
            } else {
                socket = new Socket(host, port);  // Crea un socket normal
            }

            // Configuración común del socket, PrintWriter y BufferedReader
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            scanner = new Scanner(System.in);

            // Mandar PING al servidor
            out.println("PING enviado al servidor.");
            logCommands("PING enviado al servidor.");
            socket.setSoTimeout(5000); // Establecer un tiempo de espera para la respuesta del servidor

            String response = in.readLine();
            System.out.println(response);
            if (response != null && response.equals("PONG")) {
                System.out.println("Server is reachable.");
                System.out.println("Comandos aceptados: SEND, RECEIVE, LIST, EXEC o EXIT\n" +
                        "Ejemplo de uso:\n" +
                        "SEND <nombre_archivo> <ruta_destino>\n" +
                        "RECEIVE <nombre_archivo> <ruta_destino>\n" +
                        "LIST <ruta_a_listar_servidor>\n" +
                        "EXEC <comando_a_ejecutar_en_servidor>\n"+
                        "EXIT para cerrar la conexion del cliente\n");
                logCommands("PONG recibido del servidor." + "\nComandos aceptados: SEND, RECEIVE, LIST, EXEC o EXIT" +
                        "\nEjemplo de uso:\n" +
                        "SEND <nombre_archivo> <ruta_destino>\n" +
                        "RECEIVE <nombre_archivo> <ruta_destino>\n" +
                        "LIST <ruta_a_listar_servidor>\n" +
                        "EXEC <comando_a_ejecutar_en_servidor>\n"+
                        "EXIT para cerrar la conexion del cliente\n");
            } else {
                System.out.println("Server is not reachable.");
                // Vemos si el servidor ha enviado algun mensaje de porque no esta disponible
                if (response != null) {
                    System.out.println("Server message: " + response);
                    logErrors("ERROR: " + response);
                }
                logErrors("ERROR. El servidor no está disponible.");
                closeConnection();
                return;
            }

            // Handle commands
            while (true) {
                System.out.print("Enter command: ");
                logCommands("Enter command: ");
                String command = scanner.nextLine();
                logCommands(command + " enviado al servidor.");
                executeCommand(command);
            }
        } catch (Exception e) {
            System.out.println("Server is not reachable. Server might be at full capacity.");
            logErrors("ERROR, full capacity? : " + e.getMessage());

        } finally {
            closeConnection();
        }
    }

    private void executeCommand(String command) {
        try {
            if (command.startsWith("SEND")) {
               sendFile(command);
            } else if (command.startsWith("RECEIVE")) {
               receiveFile(command);
            } else if (command.startsWith("LIST")) {
                handleList(command);
            } else if (command.startsWith("EXEC")) {
                handleExec(command);
            }else if (command.equals("EXIT")){
                System.out.println("Cerrando la conexión con el servidor...");
                logCommands("Cerrando la conexión con el servidor...");
                out.println(command);
                logCommands(command + " enviado al servidor.");
                out.flush();
                 closeConnection(); // Cierra la conexión después de enviar FIN
                // Parar la ejecucion
                 System.exit(0);
            }else {
                System.err.println("Comando no reconocido. Pruebe con: SEND, RECEIVE, LIST, EXEC o EXIT");
                logErrors("ERROR: Comando no reconocido. Pruebe con: SEND, RECEIVE, LIST, EXEC o EXIT");
            }
        } catch (IOException e) {
            e.printStackTrace();
            logErrors("ERROR: " + e.getMessage());
        }
    }
    // Método para listar los archivos del servidor
    private void handleList(String command) throws IOException {
        out.println(command); // Enviar el comando al servidor
        logCommands(command + " enviado al servidor.");
        out.flush(); // Forzar la escritura del mensaje


        // Leer la respuesta del servidor y mostrarla en la consola
        String response;
        while ((response = in.readLine()) != null) {
            if (response.equals("END")) {
                break; // Terminar el bucle si se recibe la señal de fin
            }
            System.out.println(response);
            logCommands(response);
        }
    }
    // METODO DE ENVÍO DE IMAGENES DE CLIENTE A SERVIDOR
    private void sendFile(String command) throws IOException {
        // Dividimos el comando en partes para obtener el nombre del archivo y la ruta de destino
        String[] commandParts = command.split(" ");
        if (commandParts.length < 3) {
            System.out.println("Usage: SEND <file_name> <destination_path>");
            logErrors("ERROR: Usage: SEND <file_name> <destination_path>");
            return;
        }

        String fileName = commandParts[1];
        String destinationPath = commandParts[2];
        File fileToSend = new File(clientDirectory, fileName);

        if (!fileToSend.exists()) {
            System.out.println("File does not exist: " + fileName);
            logErrors("ERROR: File does not exist: " + fileName);
            return;
        }

        // Informamos al servidor sobre el archivo que se va a enviar
        out.println("SEND " + fileToSend.getName() + " " + fileToSend.length() + " " + destinationPath);
        logCommands("SEND " + fileToSend.getName() + " " + fileToSend.length() + " " + destinationPath);
        out.flush();

        // Read and send the file in chunks
        try (FileInputStream fis = new FileInputStream(fileToSend)) {
            byte[] buffer = new byte[__MAX_BUFFER];
            int bytesRead;

            // Esperamos la respuesta del servidor
            if (ServerResponse() == 1) {
                return;
            }

            System.out.println("Starting file transfer...");
            logCommands("Starting file transfer...");
            while ((bytesRead = fis.read(buffer)) != -1) {
                socket.getOutputStream().write(buffer, 0, bytesRead);
            }
            System.out.println("File transfer completed.");
            logCommands("File transfer completed.");
        } catch (IOException e) {
            System.out.println("Error during file transfer: " + e.getMessage());
            logErrors("ERROR: Error during file transfer: " + e.getMessage());
            return;
        }

        // Informamos al servidor que hemos terminado de enviar el archivo
        out.println("EOF");
        logCommands("EOF");
        out.flush();

        // Esperamos la respuesta del servidor
        ServerResponse();
    }
    private void receiveFile(String command) throws IOException {
        // Dividimos el comando en partes para obtener el nombre del archivo y la ruta de destino
        String[] commandParts = command.split(" ");
        if (commandParts.length < 3) {
            System.out.println("Usage: RECEIVE <file_name> <local_destination_path>");
            logErrors("ERROR: Usage: RECEIVE <file_name> <local_destination_path>");
            return;
        }

        String fileName = commandParts[1];
        String localPath = commandParts[2];
        File destinationDirectory = new File(localPath);

        // Verificamos que el directorio de destino exista y sea un directorio
        if (!destinationDirectory.exists() || !destinationDirectory.isDirectory()) {
            System.out.println("Error: The destination directory does not exist or is not a directory.");
            logErrors("ERROR: The destination directory does not exist or is not a directory.");
            return; // Detiene la ejecución si el directorio no es válido
        }

        // Mandamos el comando al servidor
        out.println(command);
        logCommands(command + " enviado al servidor.");
        out.flush();

        // Leemos la respuesta del servidor
        String serverResponse = in.readLine();
        if (serverResponse == null || serverResponse.contains("Error")) {
            System.out.println("Server response: " + serverResponse);
            logErrors("ERROR: " + serverResponse);
            return;
        }

        long fileSize;
        try {
            fileSize = Long.parseLong(serverResponse);
        } catch (NumberFormatException e) {
            System.out.println("Invalid file size received from server.");
            logErrors("ERROR: Invalid file size received from server.");
            return;
        }

        File fileToReceive = new File(destinationDirectory, fileName);

        // Intentamos crear el archivo para asegurarnos de que no haya errores de permisos, etc.
        if (!fileToReceive.createNewFile()) {
            System.out.println("Error: Cannot create the file in the specified directory. Check permissions.");
            logErrors("ERROR: Cannot create the file in the specified directory. Check permissions.");
            return; // Detiene la ejecución si no se puede crear el archivo
        }

        // Preparamos el archivo para recibir los datos
        try (FileOutputStream fos = new FileOutputStream(fileToReceive)) {
            byte[] buffer = new byte[__MAX_BUFFER];
            int bytesRead;
            long totalBytesRead = 0;

            while (totalBytesRead < fileSize) {
                // Leer los datos del archivo en bloques y escribirlos en el archivo de destino
                bytesRead = socket.getInputStream().read(buffer, 0, Math.min(buffer.length, (int) (fileSize - totalBytesRead)));
                if (bytesRead == -1) {
                    throw new IOException("Unexpected end of stream");
                }
                fos.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }
        }

        System.out.println("File '" + fileName + "' received successfully and saved to '" + localPath + "'");
    }


    private void handleExec(String command) throws IOException {
        // Enviar el comando completo al servidor
        out.println(command);
        logCommands(command + " enviado al servidor.");
        out.flush();

        // Esperar y mostrar la respuesta del servidor
        String responseLine;
        while (!(responseLine = in.readLine()).equals("END_OF_RESPONSE")) {
            System.out.println(responseLine);  // Imprime cada línea de la respuesta
            if (responseLine.equals("Error")) {
                logErrors("ERROR: Error en la ejecución del comando en el servidor.");
                break;
            }
        }
    }



    // Método para devolver y esperar la respuesta del servidor
    private int ServerResponse(){
        try {
            socket.setSoTimeout(5000); // Set a timeout for waiting for the server response
            String serverResponse = in.readLine();
            if (serverResponse != null) {
                System.out.println("Server response: " + serverResponse);
                if(serverResponse.contains("Error")){
                    logErrors("ERROR: " + serverResponse);
                    return 1;
                }
            } else {
                System.out.println("No response from server.");
                logErrors("ERROR: No response from server.");
            }
        } catch (SocketTimeoutException | SocketException e) {
            System.out.println("Timeout waiting for server response.");
            logErrors("ERROR: Timeout waiting for server response.");
        } catch (IOException e) {
            logErrors("ERROR: " + e.getMessage());
            throw new RuntimeException(e);
        }
        return 0;
    }


    // Metodo para cerrar la conexión
    private void closeConnection() {
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            e.printStackTrace();
            logErrors("ERROR: " + e.getMessage());
        }
    }

    public static void configureSSL() {
        System.setProperty("javax.net.ssl.trustStore", "truststore.jks");
        System.setProperty("javax.net.ssl.trustStorePassword", "TTredes2");
    }

    private SSLSocket createSSLSocket() throws IOException {
        SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
        return (SSLSocket) factory.createSocket(host, port);
    }

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: java RemoteCommanderClient <modo> <host> <port> <client_directory>");
            logErrors("ERROR: Usage: java RemoteCommanderClient <modo> <host> <port> <client_directory>");
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
            System.err.println("ERROR: Invalid arguments provided.");
            logErrors("ERROR: Invalid arguments provided.");
            System.exit(1);
        }

        RemoteCommanderClient client = new RemoteCommanderClient(host, port, useSSL, clientDirectory);
        client.connect();
    }
}
