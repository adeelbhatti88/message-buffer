package com.patchsimple.messagebuffer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;

public class Main {
    static final String HOST = "localhost";
    static final int PORT = 8080;
    static final int MAX_QUEUE_SIZE = 10;

    static MessageBuffer openMessageBuffer(int maxSize) {
        Path persistenceFile = Path.of("messages.dat");
        return new InMemoryMessageBuffer(maxSize, persistenceFile);
    }

    public static void main(String[] args) throws IOException {
        MessageBuffer buff = openMessageBuffer(MAX_QUEUE_SIZE);
        assert buff != null;

        HttpServer server = HttpServer.create(new InetSocketAddress(HOST, PORT), 0);
        server.createContext("/message", new MessageHandler(buff));
        server.start();
    }
}

record Message(String body) {
}

class QueueEmptyException extends Exception {
}

class QueueFullException extends Exception {
}

interface MessageBuffer {

    void push(Message msg) throws QueueFullException;
    Message pop() throws QueueEmptyException;

}

class InMemoryMessageBuffer implements MessageBuffer {

    private final ArrayBlockingQueue<Message> queue;
    private final Path persistenceFile;

     InMemoryMessageBuffer(int maxSize, Path persistenceFile) {
        this.queue = new ArrayBlockingQueue<>(maxSize);
        this.persistenceFile = persistenceFile;
        loadFromDisk();
    }

    private void loadFromDisk() {
        if (!Files.exists(persistenceFile)) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(persistenceFile, StandardCharsets.UTF_8);
            for (String line : lines) {
                String body = new String(Base64.getDecoder().decode(line), StandardCharsets.UTF_8);
                queue.offer(new Message(body));
            }
        } catch (IOException e) {
            System.err.println("Failed to load persisted messages: " + e.getMessage());
        }
    }

     private synchronized void persistToDisk() {
        try {
            List<String> lines = new ArrayList<>();
            for (Message msg : queue) {
                lines.add(Base64.getEncoder().encodeToString(msg.body().getBytes(StandardCharsets.UTF_8)));
            }
            Path parentDir = persistenceFile.toAbsolutePath().getParent();
            Path tmp = Files.createTempFile(parentDir, "buffer", ".tmp");
            Files.write(tmp, lines, StandardCharsets.UTF_8);
            Files.move(tmp, persistenceFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            System.err.println("Failed to persist messages: " + e.getMessage());
        }
    }

     public void push(Message msg) throws QueueFullException {
        if (!queue.offer(msg)) {
            throw new QueueFullException();
        }
        persistToDisk();
        

     }

     public Message pop() throws QueueEmptyException {
        Message msg = queue.poll();
        if (msg == null) {
            throw new QueueEmptyException();
        }
        persistToDisk();
        return msg;
     }
    }
     


     class MessageHandler implements HttpHandler {
        private final MessageBuffer buffer;

        MessageHandler(MessageBuffer buffer) {
            this.buffer = buffer;
        }

        public void handle(HttpExchange exchange) throws IOException {

            String method = exchange.getRequestMethod();

            if (method.equals("GET")){
                handleGet(exchange);
            } else if (method.equals("POST")) {
                handlePost(exchange);
            } else {
                exchange.sendResponseHeaders(405, -1);
                exchange.close();
            }

        }

        private void handleGet(HttpExchange exchange) throws IOException {
            try {
                Message msg = buffer.pop();
                byte[] responseBytes = msg.body().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, responseBytes.length);
                exchange.getResponseBody().write(responseBytes);
            } catch (QueueEmptyException e) {
                exchange.sendResponseHeaders(404, -1);
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        }

        private void handlePost(HttpExchange exchange) throws IOException {
            try {
                byte[] bytes = exchange.getRequestBody().readAllBytes();
                String body = new String(bytes, StandardCharsets.UTF_8);
                Message msg = new Message(body);
                buffer.push(msg);
                exchange.sendResponseHeaders(201, -1);
            } catch (QueueFullException e) {
                exchange.sendResponseHeaders(429, -1);
            } catch (Exception e) {
                exchange.sendResponseHeaders(500, -1);
            } finally {
                exchange.close();
            }
        }
     }
