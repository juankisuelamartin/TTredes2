package Trabajo_Teorico_LFT.Servidor;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.TrustManagerFactory;
import java.security.KeyStore;
import javax.net.ssl.SSLSocket;

public class RemoteCommanderServer {
    private static final String KEYSTORE_PATH = "path/to/keystore.jks";
    private static final String KEYSTORE_PASS = "password";
    private static final String TRUSTSTORE_PATH = "path/to/truststore.jks";
    private static final String TRUSTSTORE_PASS = "password";
    private static final String carpetaServidor = "/home/ubuntu/FolderServidor";
    // /home/ubuntu/FolderServidor
    // C:\Users\Equipo\Desktop\carpetaServidor

    private static boolean useSSL = false;
    private static int port;
    private static int maxClients;
    private static ExecutorService threadPool;

    static File logCommandsFile = new File("acciones.log");
    static File logErrorsFile = new File("errores.log");

    public static void main(String[] args) throws Exception {
        parseArguments(args);

        if (useSSL) {
            startSSLServer();
        } else {
            startServer();
        }
    }

    private static void parseArguments(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("modo=")) {
                useSSL = arg.substring(5).equalsIgnoreCase("SSL");
            } else if (arg.startsWith("puerto=")) {
                port = Integer.parseInt(arg.substring(7));
            } else if (arg.startsWith("max_clientes=")) {
                maxClients = Integer.parseInt(arg.substring(13));
            }
        }

        if (port == 0) {
            port = 8008; // default port if not specified
        }

        if (maxClients == 0) {
            maxClients = 10; // default max clients if not specified
        }

        threadPool = Executors.newFixedThreadPool(maxClients);
    }



    public void sirve(Socket cliente) {
        new Thread(() -> {
            try {
                InputStream in = cliente.getInputStream();
                OutputStream out = cliente.getOutputStream();
                Scanner sc = new Scanner(in);
                PrintWriter output = new PrintWriter(out, true);

                while (true) {
                    String recibido = sc.nextLine();
                    String[] tokens = recibido.split(" ");
                    System.out.println(tokens[0]);
                    switch (tokens[0]) {
                        case "PING":
                            handlePing(output);
                            break;
                        case "LIST":
                            System.out.println("LISTANDO: " + tokens[1]);
                            handleList(tokens[1], output); // Solo se pasa la ruta del directorio
                            break;
                        case "SEND":
                            //TODO handleSend(tokens, sc, output);
                            break;
                        case "RECEIVE":
                            //TODO handleReceive(tokens, output);
                            break;
                        case "EXEC":
                            //TODO handleExec(tokens, output);
                            break;
                        case "FIN":
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
        }
    }
}
