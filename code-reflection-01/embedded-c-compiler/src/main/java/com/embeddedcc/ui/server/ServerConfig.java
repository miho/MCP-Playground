package com.embeddedcc.ui.server;

public record ServerConfig(ServerMode mode, int port) {

    public static ServerConfig defaultConfig() {
        return new ServerConfig(ServerMode.HTTP, 8085);
    }

    public ServerConfig {
        if (mode == null) {
            throw new IllegalArgumentException("mode must not be null");
        }
        if (mode == ServerMode.HTTP && (port < 1 || port > 65535)) {
            throw new IllegalArgumentException("port out of range: " + port);
        }
    }
}

