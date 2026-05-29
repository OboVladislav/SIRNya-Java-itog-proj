package com.promoit.otp.api;

import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

/** Wraps {@link HttpServer} (com.sun.net.httpserver) and registers route contexts. */
public class HttpServerApp {

    private static final Logger log = LoggerFactory.getLogger(HttpServerApp.class);

    private final HttpServer server;

    public HttpServerApp(int port) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress(port), 0);
        this.server.setExecutor(Executors.newFixedThreadPool(8));
    }

    public HttpServerApp route(String path, HttpHandler handler) {
        server.createContext(path, handler);
        return this;
    }

    public void start() {
        server.start();
        log.info("HTTP server started on port {}", server.getAddress().getPort());
    }

    public void stop() {
        server.stop(0);
    }
}
