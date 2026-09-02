package pl.foxcraft.foxsigile.data;

import java.util.Arrays;
import java.util.List;

public final class PlayerSigilData {
    private final boolean[] unlocked = new boolean[3];
    private final String[] sigils = new String[3];
    public boolean isUnlocked(int slot) { return unlocked[slot]; }
    public void setUnlocked(int slot, boolean value) { unlocked[slot] = value; if (!value) sigils[slot] = null; }
    public String getSigil(int slot) { return sigils[slot]; }
    public void setSigil(int slot, String id) { sigils[slot] = id; }
    public List<String> activeSigils() { return Arrays.stream(sigils).filter(s -> s != null && !s.isBlank()).toList(); }
    public PlayerSigilData copy() {
        PlayerSigilData copy = new PlayerSigilData();
        System.arraycopy(unlocked, 0, copy.unlocked, 0, 3);
        System.arraycopy(sigils, 0, copy.sigils, 0, 3);
        return copy;
    }
}
