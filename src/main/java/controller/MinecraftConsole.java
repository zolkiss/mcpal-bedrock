package controller;

import java.io.*;

public class MinecraftConsole implements Runnable {

    private final BufferedReader consoleInputReader;
    private final BufferedWriter consoleOutputWrtier;

    public MinecraftConsole(InputStream consoleInputStream, OutputStream consoleOutputStream) {
        consoleInputReader = new BufferedReader(new InputStreamReader(consoleInputStream));
        consoleOutputWrtier = new BufferedWriter(new OutputStreamWriter(consoleOutputStream));
    }

    @Override
    public void run() {
        String line;
        try (consoleInputReader) {
            while (!Thread.currentThread().isInterrupted()) {
                if ((line = consoleInputReader.readLine()) == null) break;
                System.out.println(line);
                if (line.contains("Stopping the server")) Server.isServerRunning = false;
            }
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    public void sendCommand(String command) {
        try {
            System.out.printf("Sending command: %s%n", command);
            consoleOutputWrtier.write(String.format("%s%n", command));
            consoleOutputWrtier.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
