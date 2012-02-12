package net.slipcor.pvparena.listeners;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.core.Debug;
import net.slipcor.pvparena.definitions.Announcement;
import net.slipcor.pvparena.definitions.Announcement.type;
import net.slipcor.pvparena.definitions.Arena;
import net.slipcor.pvparena.definitions.ArenaPlayer;
import net.slipcor.pvparena.definitions.Powerup;
import net.slipcor.pvparena.definitions.PowerupEffect;
import net.slipcor.pvparena.managers.Arenas;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.ItemStack;

/**
 * entity listener class
 * 
 * -
 * 
 * PVP Arena Entity Listener
 * 
 * @author slipcor
 * 
 * @version v0.6.1
 * 
 */

public class EntityListener implements Listener {
	private Debug db = new Debug();

	@EventHandler(priority = EventPriority.LOWEST)
	public void onEntityDeath(EntityDeathEvent event) {
		Entity e = event.getEntity();
		if (e instanceof Player) {
			Player player = (Player) e;

			Arena arena = Arenas.getArenaByPlayer(player);
			if (arena == null)
				return;

			db.i("onEntityDeath: fighting player");
			if (!arena.pm.getTeam(player).equals("")) {
				event.getDrops().clear();

				commitPlayerDeath(arena, player, event);
			}
		}
	}

	/**
	 * pretend a player death
	 * 
	 * @param arena
	 *            the arena the player is playing in
	 * @param player
	 *            the player to kill
	 * @param eEvent
	 *            the event triggering the death
	 */
	private void commitPlayerDeath(Arena arena, Player player, Event eEvent) {

		String sTeam = arena.pm.getTeam(player);
		String color = arena.paTeams.get(sTeam);
		Announcement.announce(arena, type.LOSER, PVPArena.lang.parse("killed",
				player.getName()));
		arena.pm.tellEveryone(PVPArena.lang.parse("killed",
				ChatColor.valueOf(color) + player.getName()
						+ ChatColor.YELLOW));

		arena.pm.parsePlayer(player).losses++;
		arena.pm.setTeam(player, ""); // needed so player does not
													// get found when dead
		
		if (arena.isCustomClassActive()) {
			Location loc = player.getLocation();
			for (ItemStack is : player.getInventory().getArmorContents()) {
				loc.getWorld().dropItemNaturally(loc, is);
			}
			for (ItemStack is : player.getInventory().getContents()) {
				loc.getWorld().dropItemNaturally(loc, is);
			}
			player.getInventory().clear();
		}
		
		ArenaPlayer p = arena.pm.parsePlayer(player);
		p.respawn = "lose";
		arena.tpPlayerToCoordName(player, "spectator");

		if (arena.cfg.getBoolean("arenatype.flags")) {
			arena.checkEntityDeath(player);
		}

		if (arena.cfg.getInt("goal.timed") > 0) {
			db.i("timed arena!");
			Player damager = null;

			if (eEvent instanceof EntityDeathEvent) {
				EntityDeathEvent event = (EntityDeathEvent) eEvent;
				if (event.getEntity().getLastDamageCause() instanceof EntityDamageByEntityEvent) {
					try {
						EntityDamageByEntityEvent ee = (EntityDamageByEntityEvent) event
								.getEntity().getLastDamageCause();
						damager = (Player) ee.getDamager();
						db.i("damager found in arg 2");
					} catch (Exception ex) {

					}
				}
			} else if (eEvent instanceof EntityDamageByEntityEvent) {
				EntityDamageByEntityEvent event = (EntityDamageByEntityEvent) eEvent;
				try {
					damager = (Player) event.getDamager();
					db.i("damager found in arg 3");
				} catch (Exception ex) {

				}
			}
			String sKiller = "";
			String sKilled = "";
			if (arena.getType().equals("ctf") || arena.getType().equals("pumpkin")) {
				db.i("timed ctf/pumpkin arena");
				sKilled = player.getName();
				if (damager != null) {
					sKiller = damager.getName();
					db.i("killer: " + sKiller);
				}
			} else {
				sKilled = arena.pm.getTeam(player);
				if (damager != null) {
					sKiller = arena.pm.getTeam(damager);
				}
			}
			if (damager != null) {
				if (arena.pm.getKills(sKiller) > 0) {
					db.i("killer killed already");
					arena.pm.addKill(sKiller);
				} else {
					db.i("first kill");
					arena.pm.addKill(sKiller);
				}
			}

			if (arena.pm.getDeaths(sKilled) > 0) {
				db.i("already died");
				arena.pm.addDeath(sKilled);
			} else {
				db.i("first death");
				arena.pm.addDeath(sKilled);
				arena.betPossible = false;
			}
		}
		if (arena.usesPowerups) {
			if (arena.cfg.getString("game.powerups", "off").startsWith(
					"death")) {
				db.i("calculating powerup trigger death");
				arena.powerupDiffI = ++arena.powerupDiffI % arena.powerupDiff;
				if (arena.powerupDiffI == 0) {
					arena.calcPowerupSpawn();
				}
			}
		}

		if (arena.checkEndAndCommit())
			return;
	}

	/**
	 * parsing of damage: Entity vs Entity
	 * 
	 * @param event
	 *            the triggering event
	 */
	public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
		Entity p1 = event.getDamager();
		Entity p2 = event.getEntity();

		db.i("onEntityDamageByEntity: cause: " + event.getCause().name());

		if (event.getCause() == DamageCause.BLOCK_EXPLOSION) {

			db.i("onEntityDamageByEntity: fighting player");
			if ((p2 == null) || (!(p2 instanceof Player))) {
				return;
			}
			db.i("damaged entity is player");
			Player defender = (Player) p2;
			Arena arena = Arenas.getArenaByPlayer(defender);
			if (arena == null)
				return;

			db.i("onEntityDamageByBLOCKDAMAGE: fighting player");
			
			if (arena.pm.getPlayerTeamMap().get(defender.getName()) == null) {
				return;
			}

			db.i("processing damage!");
			if (arena.pum != null) {
				db.i("committing powerup triggers");
				Powerup p = arena.pum.puActive.get(defender);
				if ((p != null) && (p.canBeTriggered()))
					p.commit(null, defender, event);

			}

			if (event.getDamage() >= defender.getHealth()) {
				db.i("damage >= health => death");
				int lives = 3;

				lives = arena.paLives.get(defender.getName());
				db.i("lives before death: " + lives);
				if (lives < 1) {
					if (!arena.preventDeath) {
						return; // player died => commit death!
					}
					db.i("faking player death");

					commitPlayerDeath(arena, defender, event);
				} else {
					lives--;
					arena.respawnPlayer(defender, lives);
				}
				event.setCancelled(true);
			}
			return;
		}

		if (event.getCause() == DamageCause.PROJECTILE) {
			p1 = ((Projectile) p1).getShooter();
		}

		if ((p2 == null) || (!(p2 instanceof Player))) {
			return;
		}
		
		Arena arena = Arenas.getArenaByPlayer((Player) p2);
		if (arena == null) {
			// defender no arena player => out
			return;
		}

		db.i("onEntityDamageByEntity: fighting player");
		
		if ((p1 == null) || (!(p1 instanceof Player))) {
			 // attacker no player => cancel and out!
			event.setCancelled(true);
			return;
		}
		db.i("both entities are players");
		Player attacker = (Player) p1;
		Player defender = (Player) p2;

		if (arena.pm.getTeam(defender).equals(""))
			return;

		db.i("both players part of the arena");
		if ((!arena.cfg.getBoolean("game.teamKill", false))
				&& (arena.pm.getTeam(attacker))
						.equals(arena.pm.getTeam(defender))) {
			// no team fights!
			db.i("team hit, cancel!");
			event.setCancelled(true);
			return;
		}
		
		if (!arena.fightInProgress) {
			 // fight not started, cancel!
			event.setCancelled(true);
			return;
		}
		
		// here it comes, process the damage!

		db.i("processing damage!");
		if (arena.pum != null) {
			db.i("committing powerup triggers");
			Powerup p = arena.pum.puActive.get(attacker);
			if ((p != null) && (p.canBeTriggered()))
				p.commit(attacker, defender, event);

			p = arena.pum.puActive.get(defender);
			if ((p != null) && (p.canBeTriggered()))
				p.commit(attacker, defender, event);

		}
		if (event.getDamage() >= defender.getHealth()) {
			db.i("damage >= health => death");
			int lives = 3;

			lives = arena.paLives.get(defender.getName());
			db.i("lives before death: " + lives);
			if (lives < 1) {
				if (!arena.preventDeath) {
					return; // player died => commit death!
				}
				db.i("faking player death");

				commitPlayerDeath(arena, defender, event);
			} else {
				lives--;
				arena.respawnPlayer(defender, lives);
			}
			event.setCancelled(true);
		}

	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEntityDamage(EntityDamageEvent event) {
		if (event.isCancelled()) {
			return; // respect other plugins
		}

		if (event instanceof EntityDamageByEntityEvent) {
			onEntityDamageByEntity((EntityDamageByEntityEvent) event);
			return; // hand over damage event
		}

		Entity p1 = event.getEntity();

		if ((p1 == null) || (!(p1 instanceof Player)))
			return; // no player

		Arena arena = Arenas.getArenaByPlayer((Player) p1);
		if (arena == null)
			return;

		db.i("onEntityDamage: fighting player");
		if (!arena.fightInProgress) {
			return;
		}

		Player player = (Player) p1;
		if (arena.pm.getTeam(player).equals(""))
			return;

		// here it comes, process the damage!
		if (event.getDamage() >= player.getHealth()) {
			db.i("damage >= health => death");
			int lives = 3;

			lives = arena.paLives.get(player.getName());
			db.i("lives before death: " + lives);
			if (lives < 1) {
				if (!arena.preventDeath) {
					return; // player died => commit death!
				}
				db.i("faking player death");

				commitPlayerDeath(arena, player, event);
			} else {
				lives--;
				arena.respawnPlayer(player, lives);
			}
			event.setCancelled(true);
		}

	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEntityRegainHealth(EntityRegainHealthEvent event) {

		if (event.isCancelled()) {
			return; // respect other plugins
		}

		Entity p1 = event.getEntity();

		if ((p1 == null) || (!(p1 instanceof Player)))
			return; // no player

		Arena arena = Arenas.getArenaByPlayer((Player) p1);
		if (arena == null)
			return;

		db.i("onEntityRegainHealth => fighing player");
		if (!arena.fightInProgress) {
			return;
		}

		Player player = (Player) p1;
		if (arena.pm.getTeam(player).equals(""))
			return;

		if (arena.pum != null) {
			Powerup p = arena.pum.puActive.get(player);
			if (p != null) {
				if (p.canBeTriggered()) {
					if (p.isEffectActive(PowerupEffect.classes.HEAL)) {
						event.setCancelled(true);
						p.commit(event);
					}
				}
			}

		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onEntityExplode(EntityExplodeEvent event) {
		db.i("explosion");

		Arena arena = Arenas
				.getArenaByRegionLocation(event.getLocation());
		if (arena == null)
			return; // no arena => out

		db.i("explosion inside an arena");
		if ((!(arena.cfg.getBoolean("protection.enabled", true)))
				|| (!(arena.cfg.getBoolean("protection.blockdamage", true)))
				|| (!(event.getEntity() instanceof TNTPrimed)))
			return;

		event.setCancelled(true); // ELSE => cancel event
	}
}