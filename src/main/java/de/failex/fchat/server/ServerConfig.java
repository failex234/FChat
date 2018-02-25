package de.failex.fchat.server;

import java.util.ArrayList;
import java.util.HashMap;

public class ServerConfig {

    private String motd = "";
    private ArrayList<String> mods = new ArrayList<>();
    private int maxclients;
    private int passwordsize;
    private HashMap<String, String> modpasswords = new HashMap<>();
    private ArrayList<String> bannedclients = new ArrayList<>();

    public String getMotd() {
        return motd;
    }

    public void setMotd(String motd) {
        this.motd = motd;
    }

    public ArrayList<String> getMods() {
        return mods;
    }

    public void setMods(ArrayList<String> mods) {
        this.mods = mods;
    }

    public int getMaxclients() {
        if (maxclients == 0) return 32;
        return maxclients;
    }

    public void setMaxclients(int maxclients) {
        this.maxclients = maxclients;
    }

    public HashMap<String, String> getModpasswords() {
        return modpasswords;
    }

    public void setModpasswords(HashMap<String, String> modpasswords) {
        this.modpasswords = modpasswords;
    }

    public int getPasswordsize() {
        return passwordsize;
    }

    public void setPasswordsize(int passwordsize) {
        this.passwordsize = passwordsize;
    }

    public ArrayList<String> getBannedclients() {
        return bannedclients;
    }

    public void setBannedclients(ArrayList<String> bannedclients) {
        this.bannedclients = bannedclients;
    }
}
