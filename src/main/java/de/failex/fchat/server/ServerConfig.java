package de.failex.fchat.server;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

public class ServerConfig {

    private String type = "SERVER";
    private String motd = "";
    private ArrayList<UUID> moduuids = new ArrayList<UUID>();
    private int maxclients;
    private ArrayList<String> bannedclients = new ArrayList<>();

    public String getMotd() {
        return motd;
    }

    public void setMotd(String motd) {
        this.motd = motd;
    }

    public int getMaxclients() {
        if (maxclients == 0) return 32;
        return maxclients;
    }

    public void setMaxclients(int maxclients) {
        this.maxclients = maxclients;
    }

    public ArrayList<String> getBannedclients() {
        return bannedclients;
    }

    public void setBannedclients(ArrayList<String> bannedclients) {
        this.bannedclients = bannedclients;
    }

    public ArrayList<UUID> getModuuids() {
        return moduuids;
    }

    public void setModuuids(ArrayList<UUID> moduuids) {
        this.moduuids = moduuids;
    }
}
