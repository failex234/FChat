package de.failex.fchat.Server;

import java.util.ArrayList;

public class ServerConfig {

    private String motd = "";
    private ArrayList<String> mods = new ArrayList<>();

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
}
