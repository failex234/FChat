package de.failex.fchat.client;

import java.util.UUID;

public class ClientConfig {

    private UUID clientid;
    private String nickname;
    public String type = "CLIENT";

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public UUID getClientid() {
        return clientid;
    }

    public void setClientid(UUID clientid) {
        this.clientid = clientid;
    }
}
