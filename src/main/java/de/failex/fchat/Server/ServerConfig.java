package de.failex.fchat.Server;

import java.util.ArrayList;

public class ServerConfig {

    private String motd = "";
    private ArrayList<String> mods = new ArrayList<>();
    private int maxclients;

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
}
