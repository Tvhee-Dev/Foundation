package org.mineacademy.fo.remain;

import java.lang.reflect.Method;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.MinecraftVersion.V;
import org.mineacademy.fo.ReflectionUtil;
import org.mineacademy.fo.exception.FoException;

import lombok.Getter;

// Internal class for spawning entities, used by Boss to
// tag them before they appear in the world
//
// Not intended for public use
public final class NmsEntity {

	private final World bukkitWorld;

	@Getter
	private final Object nmsEntity;

	public NmsEntity(Location loc, Class<?> cl) {
		try {
			NmsAccessor.call();
		} catch (final Throwable t) {
			throw new FoException(t, "Failed to setup entity reflection! MC version: " + MinecraftVersion.getCurrent());
		}

		this.bukkitWorld = loc.getWorld();
		this.nmsEntity = MinecraftVersion.equals(V.v1_7) ? handle(loc, cl) : createEntity(loc, cl);
	}

	private static Object handle(Location loc, Class<?> cl) {
		final Entity en = new Location(loc.getWorld(), -1, 0, -1).getWorld().spawn(loc, (Class<? extends Entity>) cl);

		try {
			return en.getClass().getMethod("getHandle").invoke(en);
		} catch (final ReflectiveOperationException ex) {
			throw new Error(ex);
		}
	}

	private Object createEntity(Location loc, Class<?> cl) {
		try {
			return NmsAccessor.createEntity.invoke(bukkitWorld, loc, cl);

		} catch (final ReflectiveOperationException e) {
			throw new FoException(e, "Error creating entity " + cl + " at " + loc);
		}
	}

	public <T extends Entity> T addEntity(SpawnReason reason) {
		try {

			return (T) NmsAccessor.addEntity(bukkitWorld, nmsEntity, reason);

		} catch (final ReflectiveOperationException e) {
			throw new FoException(e, "Error creating entity " + nmsEntity + " for " + reason);
		}
	}

	public Entity getBukkitEntity() {
		try {
			return (Entity) NmsAccessor.bukkitEntity.invoke(nmsEntity);

		} catch (final ReflectiveOperationException e) {
			throw new FoException(e, "Error getting bukkit entity from " + nmsEntity);
		}
	}
}

final class NmsAccessor {

	static final Method createEntity;
	static final Method bukkitEntity;
	static final Method addEntity;

	private static volatile boolean addEntityConsumer = false;
	private static volatile boolean olderThen18;

	static void call() {
	}

	static {
		try {
			final Class<?> nmsEntity = ReflectionUtil.getNMSClass("Entity");
			final Class<?> ofcWorld = ReflectionUtil.getOBCClass("CraftWorld");

			createEntity = MinecraftVersion.newerThan(V.v1_7) ? ofcWorld.getDeclaredMethod("createEntity", Location.class, Class.class) : null;
			bukkitEntity = nmsEntity.getMethod("getBukkitEntity");

			if (MinecraftVersion.newerThan(V.v1_10)) {
				addEntityConsumer = true;
				addEntity = ofcWorld.getDeclaredMethod("addEntity", nmsEntity, SpawnReason.class, Class.forName("org.bukkit.util.Consumer"));

			} else
				addEntity = ofcWorld.getDeclaredMethod("addEntity", nmsEntity, SpawnReason.class);

			olderThen18 = MinecraftVersion.olderThan(V.v1_8);

		} catch (final ReflectiveOperationException ex) {
			throw new FoException(ex, "Error setting up nms entity accessor!");
		}
	}

	static Object addEntity(World bukkitWorld, Object nmsEntity, SpawnReason reason) throws ReflectiveOperationException {
		if (olderThen18) {
			addEntity.invoke(bukkitWorld, nmsEntity, reason);

			return bukkitEntity.invoke(nmsEntity);
		}

		if (addEntityConsumer)
			return addEntity.invoke(bukkitWorld, nmsEntity, reason, null);

		return addEntity.invoke(bukkitWorld, nmsEntity, reason);
	}
}
