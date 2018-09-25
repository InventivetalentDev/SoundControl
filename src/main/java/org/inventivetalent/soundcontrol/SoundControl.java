package org.inventivetalent.soundcontrol;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.inventivetalent.packetlistener.handler.PacketHandler;
import org.inventivetalent.packetlistener.handler.PacketOptions;
import org.inventivetalent.packetlistener.handler.ReceivedPacket;
import org.inventivetalent.packetlistener.handler.SentPacket;
import org.inventivetalent.reflection.minecraft.Minecraft;
import org.inventivetalent.reflection.resolver.FieldResolver;
import org.inventivetalent.reflection.resolver.MethodResolver;
import org.inventivetalent.reflection.resolver.minecraft.NMSClassResolver;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SoundControl extends JavaPlugin implements Listener {

	protected PacketHandler packetHandler;

	public boolean             DEBUG              = false;
	public Map<String, String> replacements       = new HashMap<>();
	public List<String>        disabled           = new ArrayList<>();
	public List<String>        disabledCategories = new ArrayList<>();

	static NMSClassResolver nmsClassResolver = new NMSClassResolver();
	//1.9
	static Class<?>       SoundEffect;
	static Class<?>       RegistryMaterials;
	static Class<?>       RegistrySimple;
	static Class<?>       MinecraftKey;
	static FieldResolver  SoundEffectFieldResolver;
	static MethodResolver SoundEffectMethodResolver;
	static MethodResolver RegistryMaterialsMethodResolver;
	static MethodResolver RegistrySimpleMethodResolver;
	static FieldResolver  RegistrySimpleFieldResolver;
	static FieldResolver  MinecraftKeyFieldResolver;

	@Override
	public void onEnable() {

		if (!Bukkit.getPluginManager().isPluginEnabled("PacketListenerApi")) {
			getLogger().severe("********************************************");
			getLogger().severe(" ");
			getLogger().severe("  This plugin depends on PacketListenerApi  ");
			getLogger().severe("   http://www.spigotmc.org/resources/2930   ");
			getLogger().severe(" ");
			getLogger().severe("********************************************");

			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		Bukkit.getPluginManager().registerEvents(this, this);

		saveDefaultConfig();
		DEBUG = getConfig().getBoolean("debug", DEBUG);

		List<String> list = getConfig().getStringList("replace");
		if (list != null) {
			for (String s : list) {
				s = s.trim();
				String[] split = s.split(">");
				if (split.length != 2) {
					continue;
				}
				replacements.put(split[0], split[1]);
			}
		}

		list = getConfig().getStringList("disable");
		if (list != null) {
			for (String s : list) {
				s = s.trim();
				disabled.add(s);
			}
		}

		list = getConfig().getStringList("disableCategory");
		if (list != null) {
			for (String s : list) {
				s = s.trim();
				disabledCategories.add(s.toUpperCase());
			}
		}

		PacketHandler.addHandler(packetHandler = new PacketHandler(this) {

			@Override
			@PacketOptions(forcePlayer = true)
			public void onSend(SentPacket packet) {
				if ("PacketPlayOutNamedSoundEffect".equals(packet.getPacketName())) {
					String soundName = null;
					String soundCategory = null;
					Object soundObject = null;
					if (Minecraft.VERSION.olderThan(Minecraft.Version.v1_9_R1)) {
						soundName = (String) packet.getPacketValue("a");

						if (DEBUG) {
							int b = (int) packet.getPacketValue("b");
							int c = (int) packet.getPacketValue("c");
							int d = (int) packet.getPacketValue("d");
							float e = (float) packet.getPacketValue("e");
							int f = (int) packet.getPacketValue("f");

							getLogger().info("Outgoing sound '" + soundName + "' at " + b + "," + c + "," + d + " (Volume: " + e + ", Pitch: " + f + ")");
						}
					} else {
						try {
							if (SoundEffect == null) {
								SoundEffect = nmsClassResolver.resolve("SoundEffect");
							}
							if (RegistryMaterials == null) {
								RegistryMaterials = nmsClassResolver.resolve("RegistryMaterials");
							}
							if (RegistrySimple == null) {
								RegistrySimple = nmsClassResolver.resolve("RegistrySimple");
							}
							if (MinecraftKey == null) {
								MinecraftKey = nmsClassResolver.resolve("MinecraftKey");
							}
							if (SoundEffectFieldResolver == null) {
								SoundEffectFieldResolver = new FieldResolver(SoundEffect);
							}
							if (SoundEffectMethodResolver == null) {
								SoundEffectMethodResolver = new MethodResolver(SoundEffect);
							}
							if (RegistryMaterialsMethodResolver == null) {
								RegistryMaterialsMethodResolver = new MethodResolver(RegistryMaterials);
							}
							if (RegistrySimpleMethodResolver == null) {
								RegistrySimpleMethodResolver = new MethodResolver(RegistrySimple);
							}
							if (RegistrySimpleFieldResolver == null) {
								RegistrySimpleFieldResolver = new FieldResolver(RegistrySimple);
							}
							if (MinecraftKeyFieldResolver == null) {
								MinecraftKeyFieldResolver = new FieldResolver(MinecraftKey);
							}

							Method registryMaterials_b = null;
							for (Method m : RegistryMaterials.getDeclaredMethods()) {
								if (m.getName().equals("b")) {
									if (m.getParameterTypes().length == 1) {
										registryMaterials_b = m;
									}
								}
							}
							if (registryMaterials_b == null) {
								getLogger().warning("Could not find method RegistryMaterials#b(Object)");
								return;
							}

							//Sound
							soundObject = packet.getPacketValue("a");
							Object registry = SoundEffectFieldResolver.resolve("a").get(null);
							soundName = registryMaterials_b.invoke(registry, soundObject).toString();
							soundName = soundName.substring(soundName.indexOf(":") + 1);//minecraft:<sound>

							//Category
							Object categoryObject = packet.getPacketValue("b");
							soundCategory = ((Enum) categoryObject).name();

							if (DEBUG) {
								int c = (int) packet.getPacketValue("c");
								int d = (int) packet.getPacketValue("d");
								int e = (int) packet.getPacketValue("e");
								float f = (float) packet.getPacketValue("f");
								int g = (int) packet.getPacketValue("g");

								getLogger().info("Outgoing sound '" + soundName + "'-" + soundCategory + " at " + c + "," + d + "," + e + " (Volume: " + f + ", Pitch: " + g + ")");
							}
						} catch (Exception e) {
							throw new RuntimeException(e);
						}
					}

					if (soundCategory != null) {
						if (disabledCategories.contains(soundCategory)) {
							packet.setCancelled(true);

							if (DEBUG) {
								getLogger().info("Cancelled sound category " + soundCategory);
							}
							return;
						}
					}
					if (soundName != null) {
						if (disabled.contains(soundName)) {
							packet.setCancelled(true);

							if (DEBUG) {
								getLogger().info("Cancelled sound '" + soundName + "'");
							}
							return;
						}

						if (replacements.containsKey(soundName)) {
							String replace = replacements.get(soundName);

							if (Minecraft.VERSION.olderThan(Minecraft.Version.v1_9_R1)) {
								packet.setPacketValue("a", replace);
							} else {
								try {
									Object registry = SoundEffectFieldResolver.resolve("a").get(null);
									Map<?, ?> soundMap = (Map) RegistrySimpleFieldResolver.resolve("c").get(registry);
									Object newSoundObject = null;
									for (Map.Entry<?, ?> entry : soundMap.entrySet()) {
										if (MinecraftKeyFieldResolver.resolve("b").get(entry.getKey()).equals(replace)) {
											newSoundObject = entry.getValue();
											break;
										}
									}

									if (newSoundObject != null) {
										packet.setPacketValue("a", newSoundObject);
									} else {
										getLogger().warning("Failed to resolve sound '" + replace + "' back to an object!");
									}
								} catch (Exception e) {
									throw new RuntimeException(e);
								}
							}

							if (DEBUG) {
								getLogger().info("Replaced sound '" + soundName + "' with '" + replace + "'");
							}
						}

					}

				}
			}

			@Override
			public void onReceive(ReceivedPacket packet) {
			}
		});
	}

	@Override
	public void onDisable() {
		PacketHandler.removeHandler(packetHandler);
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		if ("soundcontrol".equalsIgnoreCase(command.getName())) {
			if (args.length < 2) {
				if (sender.hasPermission("soundcontrol.command.disable")) {
					sender.sendMessage("§b/sc disable <sound>");
				}
				if (sender.hasPermission("soundcontrol.command.disable.category")) {
					sender.sendMessage("§b/sc disableCategory <category>");
				}
				if (sender.hasPermission("soundcontrol.command.replace")) {
					sender.sendMessage("§b/sc replace <sound> <sound>");
				}
				return true;
			}
			if ("cancel".equalsIgnoreCase(args[0])) { args[0] = "disable"; }
			if ("disable".equalsIgnoreCase(args[0])) {
				if (!sender.hasPermission("soundcontrol.command.disable")) {
					sender.sendMessage("§cNo permission");
					return false;
				}

				String sound = args[1];
				if (disabled.contains(sound)) {
					disabled.remove(sound);
					sender.sendMessage("§aNo longer disabling sound '" + sound + "'");
				} else {
					disabled.add(sound);
					sender.sendMessage("§aDisabled sound '" + sound + "'");
				}
				List<String> list = getConfig().getStringList("disable");
				if (list != null) {
					if (list.contains(sound)) {
						list.remove(sound);
					} else {
						list.add(sound);
					}
					getConfig().set("disable", list);
					saveConfig();
				}
				return true;
			}
			if ("cancelCategory".equalsIgnoreCase(args[0])) { args[0] = "disableCategory"; }
			if ("disableCategory".equalsIgnoreCase(args[0])) {
				if (!sender.hasPermission("soundcontrol.command.disable.category")) {
					sender.sendMessage("§cNo permission");
					return false;
				}
				if (Minecraft.VERSION.olderThan(Minecraft.Version.v1_9_R1)) {
					sender.sendMessage("§cNote: Sound-Categories are only available in 1.9+");
				}

				String category = args[1];
				if (disabledCategories.contains(category)) {
					disabledCategories.remove(category);
					sender.sendMessage("§aNo longer disabling category '" + category + "'");
				} else {
					disabledCategories.add(category);
					sender.sendMessage("§aDisabled category '" + category + "'");
				}
				List<String> list = getConfig().getStringList("disableCategory");
				if (list != null) {
					if (list.contains(category)) {
						list.remove(category);
					} else {
						list.add(category);
					}
					getConfig().set("disableCategory", list);
					saveConfig();
				}
				return true;
			}
			if ("replace".equalsIgnoreCase(args[0])) {
				if (!sender.hasPermission("soundcontrol.command.replace")) {
					sender.sendMessage("§cNo permission");
					return false;
				}

				String source = args[1];
				if (args.length < 3) {
					sender.sendMessage("§c/sc replace <sound> <sound>");
					return false;
				}
				String replace = args[2];

				String oldReplace = null;
				if (replacements.containsKey(source)) {
					oldReplace = replacements.remove(source);
					sender.sendMessage("§aNo longer replacing sound '" + source + "' with '" + oldReplace + "'");
				} else {
					replacements.put(source, replace);
					sender.sendMessage("§aReplaced sound '" + source + "' with '" + replace + "'");
				}
				List<String> list = getConfig().getStringList("replace");
				if (list != null) {
					if (oldReplace != null && list.contains(source + ">" + oldReplace)) {
						list.remove(source + ">" + oldReplace);
					} else {
						list.add(source + ">" + replace);
					}
					getConfig().set("replace", list);
					saveConfig();
				}
				return true;
			}
		}

		return false;
	}

}
