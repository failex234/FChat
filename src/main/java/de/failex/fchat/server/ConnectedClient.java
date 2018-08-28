package de.failex.fchat.server;

import java.net.Socket;
import java.util.UUID;

public class ConnectedClient {

    private String nickname;
    private UUID clientid;
    private Socket socket;
    private boolean moderator;

    public ConnectedClient(String nickname, UUID clientid, Socket socket) {
        this.nickname = nickname;
        this.clientid = clientid;
        this.socket = socket;
    }

    public boolean isModerator() {
        return moderator;
    }

    public void setModerator(boolean moderator) {
        this.moderator = moderator;
    }

    public String getNickname() {
        return nickname;
    }

    public UUID getUniqueId() {
        return clientid;
    }

    public Socket getSocket() {
        return socket;
    }

    public String getRemoteAddress() {
        return socket.getRemoteSocketAddress().toString();
    }
}
