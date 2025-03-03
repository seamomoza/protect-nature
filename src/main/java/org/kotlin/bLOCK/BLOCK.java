package org.kotlin.bLOCK;


import com.destroystokyo.paper.Title;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;


public class BLOCK extends JavaPlugin implements Listener, CommandExecutor {
    private Location explosionLocation = null;
    private int explosionTicks = 0;
    private Set<Location> placedBlocks = new HashSet<>();
    private boolean explosionStarted = false;
    private boolean cooldownActive = false;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("block") != null) {
            getCommand("block").setExecutor(this);
        } else {
            getLogger().warning("명령어 'block'이 plugin.yml에 등록되지 않았습니다.");
        }
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("block")) {
            if (sender instanceof Player) {
                Player player = (Player) sender;

                if (explosionStarted || cooldownActive) {
                    player.sendMessage(ChatColor.RED + "폭발이 이미 진행 중이거나, 쿨다운 중입니다!");
                    return false;
                }

                // 흙 1세트(64개) 지급
                player.getInventory().addItem(new ItemStack(Material.DIRT, 64));
                player.sendMessage(ChatColor.GREEN + "초기 자원을 지급받았습니다");

                player.sendMessage(ChatColor.DARK_RED + "자연 훼손 금지 5초 후 시작");
                startCountdown(player);
                return true;
            }
        }
        return false;
    }


    private void startCountdown(Player player) {
        cooldownActive = true;
        new BukkitRunnable() {
            int timeLeft = 5;

            @Override
            public void run() {
                if (timeLeft > 0) {
                    player.sendMessage(ChatColor.GREEN + "남은 시간: " + timeLeft + "초");
                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    timeLeft--;
                } else {
                    player.sendMessage(ChatColor.DARK_RED + "자연 훼손 금지!");
                    explosionStarted = true;
                    cooldownActive = false;
                    cancel();
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    @EventHandler
    public void onPlayerStep(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location blockBelow = player.getLocation().subtract(0, 1, 0).getBlock().getLocation();

        // 공기(AIR) 블록이면 무시
        if (blockBelow.getBlock().getType() == Material.AIR) {
            return;
        }

        // 플레이어가 설치한 블록이면 무시
        if (placedBlocks.contains(blockBelow) || !explosionStarted) {
            return;
        }

        // 블록을 밟았을 때 폭발
        explosionLocation = player.getLocation();
        explosionTicks = 0;
        triggerExplosion();
    }

    @EventHandler
    public void onPlayerPlaceBlock(BlockPlaceEvent event) {
        placedBlocks.add(event.getBlockPlaced().getLocation());
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();

        // 죽었을 때 카운트다운이 진행 중이면 카운트다운을 방지
        if (cooldownActive) {
            return; // 카운트다운 리셋을 방지
        }

        // 사망 시 폭발 상태 종료
        explosionStarted = false;
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();

        // 쿨다운이 활성화되어 있으면 카운트다운을 하지 않음
        if (cooldownActive) {
            return; // 카운트다운 리셋 방지
        }

        player.sendMessage(ChatColor.RED + "5초 후 재활성화");

        // 흙 1세트 지급
        player.getInventory().addItem(new ItemStack(Material.DIRT, 64));
        startCountdown(player);  // 5초 카운트다운 시작

        // 타이틀 메시지 설정
        player.sendMessage(ChatColor.DARK_GREEN + "이 더러운 생태계 파괴범");
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);

    }
    private void triggerExplosion() {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (explosionLocation == null || explosionTicks >= 100) {
                    explosionTicks = 0;
                    explosionLocation = null;
                    return;
                }

                double radiusPerTick = 1.0;
                double pointPerCircum = 6.0;

                double radius = radiusPerTick * explosionTicks;
                double circum = 2.0 * Math.PI * radius;
                int pointsCount = Math.max(1, (int) (circum / pointPerCircum));
                double angle = 360.0 / pointsCount;

                World world = explosionLocation.getWorld();

                for (int i = 0; i < pointsCount; i++) {
                    double currentAngle = Math.toRadians(i * angle);
                    double x = -Math.sin(currentAngle) * radius;
                    double z = Math.cos(currentAngle) * radius;

                    Location tntLocation = explosionLocation.clone().add(x, 0.0, z);

                    TNTPrimed tnt = (TNTPrimed) Objects.requireNonNull(world).spawnEntity(tntLocation, EntityType.TNT);
                    tnt.setFuseTicks(0);
                }

                explosionTicks++;
            }
        }.runTaskTimer(this, 0L, 1L);
    }
}
