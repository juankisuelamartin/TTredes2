import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import java.util.concurrent.Executors;
import javax.net.ssl.SSLSocket;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Semaphore;
//TODO ARREGLAR EXCEPCION QUE SALTA AL REINICIAR EL CLIENTE. java.net.SocketException: Connection reset
public class RemoteCommanderServer {
    private static int port = 8008; // Valor predeterminado para el puerto
    private static boolean useSSL = false; // Valor predeterminado para SSL
    private static int maxClients = 10; // Valor predeterminado para el máximo de clientes
    private static ExecutorService threadPool;
    private static AtomicInteger currentClients = new AtomicInteger(0);
    private static final String KEYSTORE_PATH = "path/to/keystore.jks";
    private static final String KEYSTORE_PASS = "password";
    private static final String TRUSTSTORE_PATH = "path/to/truststore.jks";
    private static final String TRUSTSTORE_PASS = "password";
    private static final String carpetaServidor = "/home/ubuntu/FolderServidor";
    // /home/ubuntu/FolderServidor
    // C:\Users\Juanki\Documents\GitHub\TTredes2\src\main\java\Trabajo_Teorico_LFT\carpeta_prueba_servidor
    private static final int __MAX_BUFFER = 1024;

    static File logCommandsFile = new File("acciones.log");
    static File logErrorsFile = new File("../../../errores.log");

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

    public static void main(String[] args) throws Exception {
        if (!parseArguments(args)) {
            System.out.println("Usage: java RemoteCommanderServer modo=SSL|noSSL puerto=<port> max_clientes=<maxClients>");
            logErrors("ERROR: Usage: java RemoteCommanderServer modo=SSL|noSSL puerto=<port> max_clientes=<maxClients>");
            return;
        } else {
            logCommands("Comando de inicio: java RemoteCommanderServer " + String.join(" ", args));
        }

        // Inicialización del ThreadPool con el número máximo de clientes
        threadPool = Executors.newFixedThreadPool(maxClients);
        logCommands("ThreadPool inicializado con " + maxClients + " clientes máximos.");
        System.out.println("ThreadPool inicializado con " + maxClients + " clientes máximos.");

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
                    // Extraer el número de clientes máximos permitidos.
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
            try (Socket cl = cliente;
                 InputStream inRaw = cl.getInputStream();
                 OutputStream outRaw = cl.getOutputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(inRaw));
                 DataInputStream in = new DataInputStream(new BufferedInputStream(inRaw));
                 PrintWriter output = new PrintWriter(new OutputStreamWriter(outRaw), true)) {

                String recibido;
                while ((recibido = reader.readLine()) != null) {  // Continuar leyendo hasta que el cliente cierre la conexión
                    String[] tokens = recibido.split(" ");
                    switch (tokens[0]) {
                        case "PING":
                            logCommands("PING recibido del cliente.");
                            handlePing(output);
                            logCommands("PONG enviado al cliente.");
                            break;
                        case "LIST":
                            logCommands("LIST recibido del cliente.");
                            System.out.println("LISTANDO: " + tokens[1]);
                            handleList(tokens[1], output); // Solo se pasa la ruta del directorio
                            logCommands("LIST enviado al cliente.");
                            break;
                        case "SEND":
                            logCommands("SEND recibido del cliente.");
                            System.out.println("RECIBIENDO ARCHIVO: " + tokens[1]);
                            handleSend(tokens, in, output);
                            logCommands("SEND enviado al cliente.");
                            break;
                        case "RECEIVE":
                            logCommands("RECEIVE recibido del cliente.");
                            System.out.println("ENVIANDO ARCHIVO: " + tokens[1]);
                            handleReceive(cl, recibido);
                            logCommands("RECEIVE enviado al cliente.");
                            break;
                        case "EXEC":
                            logCommands("EXEC recibido del cliente.");
                            System.out.println("EJECUTANDO COMANDO: " + tokens[1]);
                            handleExec(recibido, output);
                            logCommands("EXEC enviado al cliente.");
                            break;
                        case "EXIT":
                            logCommands("EXIT recibido del cliente.");
                            System.out.println("Cerrando conexión por solicitud del cliente.");
                            return; // Salir del bucle y del hilo, los recursos se cerrarán automáticamente
                        default:
                            logCommands("Comando no reconocido: " + recibido);
                            output.println("Comando no reconocido.");
                            break;
                    }
                }
            } catch (IOException e) {
                System.err.println("IOException: " + e.getMessage());
                logErrors("Cliente ha cerrado la conexion de forma abrupta: " + e.getMessage());
            } finally {
                try {
                    if (!cliente.isClosed()) {
                        cliente.close();
                    }
                } catch (IOException e) {
                    System.err.println("Error al cerrar el socket: " + e.getMessage());
                    logErrors("Error al cerrar el socket: " + e.getMessage());
                }
                logCommands("Conexión con cliente cerrada.");

                currentClients.decrementAndGet(); // Decrementa el contador de clientes conectados
            }
        }).start();
    }


    private void handlePing(PrintWriter output) {
        System.out.println("PING recibido del cliente.");
        System.out.println("Numero de clientes conectados: " + currentClients.get());
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
            logErrors("ERROR: No existe la carpeta");
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
           logErrors("ERROR: The destination directory does not exist or is not a directory.");
           return; // Detiene la ejecución si el directorio no es válido
       }

       File file = new File(directoryPath, fileName);

       // Intenta crear el archivo para asegurarse de que no hay errores de permisos, etc.
       if (!file.createNewFile()) {
           output.println("Error: Cannot create file in the specified directory. Check permissions or if a file already Exists.");
           logErrors("ERROR: Cannot create file in the specified directory. Check permissions or if a file already Exists.");
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
            logErrors("ERROR: File not found");
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
            logErrors("ERROR: Invalid command format.");
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
            logErrors("ERROR: Error executing command: " + e.getMessage());
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
        try (SSLServerSocket serverSocket = (SSLServerSocket) ssf.createServerSocket(port)) {
            System.out.println("SSL server started on port " + port);
            while (true) {
                SSLSocket clientSocket = (SSLSocket) serverSocket.accept();
                // Utiliza el ExecutorService para manejar las conexiones entrantes
                threadPool.execute(() -> new RemoteCommanderServer().sirve(clientSocket));
            }
        } catch (IOException e) {
            System.out.println("Error al crear el socket del servidor SSL: " + e.getMessage());
            logErrors("ERROR: Error al crear el socket del servidor SSL: " + e.getMessage());
        }
    }
    private static void startServer() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server started on port " + port + " in non-SSL mode.");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                maxclientcheck(clientSocket);
            }
        } catch (IOException e) {
            System.out.println("Error al crear el socket del servidor: " + e.getMessage());
            logErrors("ERROR: Error al crear el socket del servidor: " + e.getMessage());
        }
    }
    private static void maxclientcheck(Socket clientSocket) {
        if (currentClients.get() < maxClients) {
            threadPool.execute(() -> {
                // Incrementa el contador de clientes conectados
                currentClients.incrementAndGet();
                new RemoteCommanderServer().sirve(clientSocket);
            });
        } else {
            rejectClient(clientSocket);
        }
    }
    private static void rejectClient(Socket clientSocket) {
        try (PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)) {
            out.println("Connection rejected: server is at full capacity.");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}

