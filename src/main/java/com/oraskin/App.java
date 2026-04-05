package com.oraskin;

public class App {

    public static void main(String[] args) throws Exception {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : 8080;
        ChatServer server = new ChatServer(port);
        server.start();
    }
}
