import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Scanner;
// TODO CONTROLAR EL NUM MAX_CLIENTES.
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
            try (PrintWriter writerLogErrorsFile = new PrintWriter(new FileWriter(logErrorsFile, true))) {
                writerLogErrorsFile.println("ERROR: Error al escribir en el archivo de comandos: " + e.getMessage());
            } catch (IOException ex) {
                System.err.println("Error al escribir en el archivo de errores: " + ex.getMessage());
            }
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
            socket = useSSL ? createSSLSocket() : new Socket(host, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            scanner = new Scanner(System.in);

            // Mandar PING al servidor
            out.println("PING");
            logCommands("PING enviado al servidor.");
            socket.setSoTimeout(5000); // Establecer un tiempo de espera para la respuesta del servidor
            String response = in.readLine();
            if (response != null && response.equals("PONG")) {
                System.out.println("Server is reachable.");
                System.out.println("Comandos aceptados: SEND, RECEIVE, LIST, EXEC o EXIT\n" +
                        "Ejemplo de uso:\n" +
                        "SEND <nombre_archivo> <ruta_destino>\n" +
                        "RECEIVE <nombre_archivo> <ruta_destino>\n" +
                        "LIST <ruta_a_listar_servidor>\n" +
                        "EXEC <comando_a_ejecutar_en_servidor>\n"+
                        "EXIT para cerrar la conexion del cliente\n");

            } else {
                System.out.println("Server is not reachable.");
                logErrors("ERROR. El servidor no está disponible.");
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
                logCommands(command + " enviado al servidor.");
                executeCommand(command);
            }
        } catch (Exception e) {
            e.printStackTrace();
            logErrors("ERROR: " + e.getMessage());
        } finally {
            closeConnection();
        }
    }

    private void executeCommand(String command) {
        try {
            if (command.startsWith("SEND")) {
                logCommands(command + " enviado al servidor.");
               sendFile(command);
            } else if (command.startsWith("RECEIVE")) {
                logCommands(command + " enviado al servidor.");
               receiveFile(command);
            } else if (command.startsWith("LIST")) {
                logCommands(command + " enviado al servidor.");
                handleList(command);
            } else if (command.startsWith("EXEC")) {
                logCommands(command + " enviado al servidor.");
                handleExec(command);
            }else if (command.equals("EXIT")){
                logCommands(command + " enviado al servidor.");
                out.println(command);
                out.flush();
                closeConnection(); // Cierra la conexión después de enviar FIN
                // Parar la ejecucion
                System.exit(0);
            }else {
                System.out.println("Comando no reconocido. Pruebe con: SEND, RECEIVE, LIST, EXEC o EXIT");
                logErrors("ERROR: Comando no reconocido.");
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
            while ((bytesRead = fis.read(buffer)) != -1) {
                socket.getOutputStream().write(buffer, 0, bytesRead);
            }
            System.out.println("File transfer completed.");
        } catch (IOException e) {
            System.out.println("Error during file transfer: " + e.getMessage());
            logErrors("ERROR: Error during file transfer: " + e.getMessage());
            return;
        }

        // Informamos al servidor que hemos terminado de enviar el archivo
        out.println("EOF");
        logCommands("EOF enviado al servidor.");
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

        File fileToReceive = new File(destinationDirectory, fileName);

        // Intentamos crear el archivo para asegurarnos de que no haya errores de permisos, etc.
        if (!fileToReceive.createNewFile()) {
            System.out.println("Error: Cannot create the file in the specified directory. Check permissions.");
            logErrors("ERROR: Cannot create the file in the specified directory. Check permissions.");
            return; // Detiene la ejecución si no se puede crear el archivo
        }

        // Mandamos el comando al servidor
        out.println(command);
        logCommands(command + " enviado al servidor.");
        out.flush();

        // Esperamos la respuesta del servidor y preparamos el archivo para recibir los datos
        long fileSize = Long.parseLong(in.readLine()); // Tamaño del archivo
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
            System.out.println("Invalid arguments provided.");
            logErrors("ERROR: Invalid arguments provided.");
            System.exit(1);
        }

        RemoteCommanderClient client = new RemoteCommanderClient(host, port, useSSL, clientDirectory);
        client.connect();
    }
}
