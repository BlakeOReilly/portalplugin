package com.blake.portalplugin.listeners;

import com.blake.portalplugin.BlastMinigameManager;
import com.blake.portalplugin.BlastTeam;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class BlastFriendlyFireListener implements Listener {

    private final BlastMinigameManager blast;

    public BlastFriendlyFireListener(BlastMinigameManager blast) {
        this.blast = blast;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (blast == null || !blast.isInProgress()) return;

        if (!(e.getEntity() instanceof Player victim)) return;

        Player attacker = null;

        if (e.getDamager() instanceof Player p) {
            attacker = p;
        } else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p2) {
            attacker = p2;
        }

        if (attacker == null) return;

        if (!blast.isParticipant(attacker) || !blast.isParticipant(victim)) return;

        BlastTeam at = blast.getTeam(attacker);
        BlastTeam vt = blast.getTeam(victim);

        if (at != null && at == vt) {
            e.setCancelled(true);
        }
    }
}
