import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import javax.net.ssl.SSLSocket;
//TODO ARREGLAR EXCEPCION QUE SALTA AL REINICIAR EL CLIENTE. java.net.SocketException: Connection reset
//TODO ARREGLAR EXCEPCION NULL AL HACER EXIT. java.lang.NullPointerException LINEA 85
public class RemoteCommanderServer {
    private static int port = 8008; // Valor predeterminado para el puerto
    private static boolean useSSL = false; // Valor predeterminado para SSL
    private static int maxClients = 10; // Valor predeterminado para el máximo de clientes
    private static final String KEYSTORE_PATH = "path/to/keystore.jks";
    private static final String KEYSTORE_PASS = "password";
    private static final String TRUSTSTORE_PATH = "path/to/truststore.jks";
    private static final String TRUSTSTORE_PASS = "password";
    private static final String carpetaServidor = "/home/ubuntu/FolderServidor";
    // /home/ubuntu/FolderServidor
    // C:\Users\soyju\Documents\GitHub\TTredes2\src\main\java\Trabajo_Teorico_LFT\carpeta_prueba_servidor
    private static final int __MAX_BUFFER = 1024;
    private static ExecutorService threadPool;

    static File logCommandsFile = new File("acciones.log");
    static File logErrorsFile = new File("errores.log");

    public static void main(String[] args) throws Exception {
        if (!parseArguments(args)) {
            System.out.println("Usage: java RemoteCommanderServer modo=SSL|noSSL puerto=<port> max_clientes=<maxClients>");
            return;
        }

        if (useSSL) {
            startSSLServer();
        } else {
            startServer();
        }
    }

    private static boolean parseArguments(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("modo=")) {
                useSSL = "SSL".equalsIgnoreCase(arg.substring(5));
            } else if (arg.startsWith("puerto=")) {
                try {
                    port = Integer.parseInt(arg.substring(7));
                } catch (NumberFormatException e) {
                    System.out.println("Invalid port number.");
                    return false;
                }
            } else if (arg.startsWith("max_clientes=")) {
                try {
                    maxClients = Integer.parseInt(arg.substring(13));
                } catch (NumberFormatException e) {
                    System.out.println("Invalid number of max clients.");
                    return false;
                }
            } else {
                System.out.println("Unknown argument: " + arg);
                return false;
            }
        }
        return true;
    }



    public void sirve(Socket cliente) {
        new Thread(() -> {
            try {
                InputStream inRaw = cliente.getInputStream();
                OutputStream outRaw = cliente.getOutputStream();
                DataInputStream in = new DataInputStream(new BufferedInputStream(inRaw));
                PrintWriter output = new PrintWriter(new OutputStreamWriter(outRaw), true);

                while (true) {
                    // Asumiendo que el comando inicial viene como un string de texto
                    // Usar readLine del BufferedReader para leer el comando si es texto, no DataInputStream
                    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                    String recibido = reader.readLine();
                    String[] tokens = recibido.split(" ");
                    switch (tokens[0]) {
                        case "PING":
                            handlePing(output);
                            break;
                        case "LIST":
                            System.out.println("LISTANDO: " + tokens[1]);
                            handleList(tokens[1], output); // Solo se pasa la ruta del directorio
                            break;
                        case "SEND":
                            System.out.println("RECIBIENDO ARCHIVO: " + tokens[1]);
                            handleSend(tokens, in, output);
                            break;
                        case "RECEIVE":
                            System.out.println("ENVIANDO ARCHIVO: " + tokens[1]);
                            handleReceive(cliente, recibido);
                            break;
                        case "EXEC":
                            System.out.println("EJECUTANDO COMANDO: " + tokens[1]);
                            handleExec(recibido, output);
                            break;
                        case "EXIT":
                            System.out.println("Cerrando conexión por solicitud del cliente.");
                            cliente.close();
                            return; // Salir del bucle y del hilo
                        default:
                            // Comando no conocido, enviar al servidor
                            System.out.println(recibido);
                            break;
                    }
                }

            } catch (Exception e) {
                e.printStackTrace();
            }finally {
                try {
                    cliente.close(); // Asegúrate de cerrar el socket al final.
                } catch (IOException e) {
                    System.err.println("Error al cerrar el socket: " + e.getMessage());
                }
            }
        }).start();
    }

    private void handlePing(PrintWriter output) {
        output.println("PONG");
    }
    private void handleList(String directoryPath, PrintWriter output) {
        StringBuilder enviar = new StringBuilder();
        File dir = new File(directoryPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    enviar.append("Archivo: ").append(file.getName()).append(" con tamaño ").append("(").append(file.length()).append(")").append("\n");
                }
            }
            output.println(enviar);
        } else {
            output.println("No existe la carpeta");
        }
        output.println("END"); // Indicador de fin de envío
        output.flush();
    }
   private void handleSend(String[] tokens, DataInputStream in, PrintWriter output) throws IOException {
       String directoryPath = tokens[3].trim();
       String fileName = tokens[1].trim();
       File directory = new File(directoryPath);

       // Verifica que el directorio exista y sea un directorio
       if (!directory.exists() || !directory.isDirectory()) {
           System.out.println(directory);
           output.println("Error: The destination directory does not exist or is not a directory.");
           return; // Detiene la ejecución si el directorio no es válido
       }

       File file = new File(directoryPath, fileName);

       // Intenta crear el archivo para asegurarse de que no hay errores de permisos, etc.
       if (!file.createNewFile()) {
           output.println("Error: Cannot create file in the specified directory. Check permissions or if a file already Exists.");
           return; // Detiene la ejecución si no se puede crear el archivo
       }


       output.println("Ready to receive file: " + fileName);
       FileOutputStream fos = new FileOutputStream(file);
       byte[] buffer = new byte[__MAX_BUFFER];

       int expectedBytes = Integer.parseInt(tokens[2]); // el tamaño del archivo viene como segundo token.
       int bytesRead;
       int totalBytesRead = 0;

       while (totalBytesRead < expectedBytes) {
           bytesRead = in.read(buffer);
           if (bytesRead == -1) {
               break;
           }
           fos.write(buffer, 0, bytesRead);
           totalBytesRead += bytesRead;
       }
       fos.close();

        // TODO ARREGLAR EL EXCESO DE 5 BYTES
       if (totalBytesRead == expectedBytes + 5) {
           output.println("File received successfully.");
       } else {
           output.println("File transfer incomplete. Expected: " + expectedBytes + " bytes, but got: " + totalBytesRead + " bytes.");
       }
   }


    public void handleReceive(Socket clientSocket, String command) throws IOException {
        String[] parts = command.split(" ");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Command format error. Usage: RECEIVE <file_name>");
        }

        String fileName = parts[1];
        File fileToSend = new File(carpetaServidor, fileName);
        if (!fileToSend.exists()) {
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
            out.println("File not found");
            return;
        }

        long fileSize = fileToSend.length();
        PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
        out.println(fileSize);  // Send the file size to the client

        // Send the file
        try (FileInputStream fis = new FileInputStream(fileToSend)) {
            byte[] buffer = new byte[__MAX_BUFFER];
            int bytesRead;
            OutputStream clientOutput = clientSocket.getOutputStream();

            while ((bytesRead = fis.read(buffer)) != -1) {
                clientOutput.write(buffer, 0, bytesRead);
            }
        }

        System.out.println("File '" + fileName + "' sent successfully to client.");
    }
    public void handleExec(String inputLine, PrintWriter output) {
        if (!inputLine.startsWith("EXEC ")) {
            output.println("Error: Invalid command format.");
            return;
        }

        // Extraer el comando completo después de "EXEC "
        String command = inputLine.substring(5);  // después de "EXEC "

        // Como el servidor estará alojado en Linux, se debe ejecutar el comando en una shell
        String[] cmdArray = {"/bin/sh", "-c", command};

        try {
            Process process = Runtime.getRuntime().exec(cmdArray);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                output.println(line);
            }
            process.waitFor();  // Espera a que el proceso termine
            output.println("END_OF_RESPONSE");  // Marca el fin de la salida del comando
        } catch (IOException | InterruptedException e) {
            output.println("Error executing command: " + e.getMessage());
        }
    }


    private static void startSSLServer() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("TLS");
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());

        KeyStore ks = KeyStore.getInstance("JKS");
        ks.load(new FileInputStream(KEYSTORE_PATH), KEYSTORE_PASS.toCharArray());
        kmf.init(ks, KEYSTORE_PASS.toCharArray());

        KeyStore ts = KeyStore.getInstance("JKS");
        ts.load(new FileInputStream(TRUSTSTORE_PATH), TRUSTSTORE_PASS.toCharArray());
        tmf.init(ts);

        sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
        SSLServerSocketFactory ssf = sslContext.getServerSocketFactory();
        SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(port);
        System.out.println("SSL server started on port " + port);

        while (true) {
            SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
            new RemoteCommanderServer().sirve(clientSocket);
        }
    }
    private static void startServer() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port + " in non-SSL mode.");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                new RemoteCommanderServer().sirve(clientSocket);
            }
        }catch (IOException e){
            System.out.println("Error al crear el socket del servidor: " + e.getMessage());
        }
    }
}
