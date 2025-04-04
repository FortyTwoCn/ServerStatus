package org.ServerStatus.serverStatus;
import net.md_5.bungee.api.plugin.*;
import net.md_5.bungee.api.*;
import net.md_5.bungee.api.config.*;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class ServerStatus extends Plugin {

    // 显示名称与真实服务器ID的映射（使用LinkedHashMap保持顺序）
    private final LinkedHashMap<String, String> SERVER_MAPPING = new LinkedHashMap<String, String>() {{
        put("登录大厅", "20001-登录大厅");
        put("主城", "20002-主城");
        put("资源一区", "20003-资源一区");
        put("资源二区", "20004-资源二区");
        put("副本", "20005-副本");
        put("生存一区", "20006-生存一区");
        put("生存二区", "20007-生存二区");
        put("生存三区", "20008-生存三区");
        put("家园一区", "20009-家园一区");
        put("家园二区", "20010-家园二区");
        put("家园三区", "20011-家园三区");
    }};

    @Override
    public void onEnable() {
        getProxy().getPluginManager().registerCommand(this, new Command("status", null, "online") {
            @Override
            public void execute(CommandSender sender, String[] args) {
                checkAndSendStatus(sender);
            }
        });
    }

    private void checkAndSendStatus(CommandSender sender) {
        List<CompletableFuture<ServerData>> futures = new ArrayList<>();
        AtomicInteger total = new AtomicInteger(0);

        // 异步检测每个服务器
        for (Map.Entry<String, String> entry : SERVER_MAPPING.entrySet()) {
            String displayName = entry.getKey();
            String realServerId = entry.getValue();

            futures.add(CompletableFuture.supplyAsync(() -> {
                ServerData data = new ServerData(displayName);
                ServerInfo server = getProxy().getServerInfo(realServerId);

                // 服务器不存在的情况
                if (server == null) {
                    data.setOnline(false);
                    return data;
                }

                try {
                    // 带超时的ping检测
                    server.ping().get(2, TimeUnit.SECONDS);

                    // 获取在线玩家
                    List<ProxiedPlayer> players = new ArrayList<>(server.getPlayers());
                    data.setOnline(true);
                    data.setPlayers(players);
                    data.setPlayerCount(players.size());
                    total.addAndGet(players.size());
                } catch (Exception e) {
                    data.setOnline(false);
                }
                return data;
            }));
        }

        // 组合所有异步任务
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).thenAcceptAsync(v -> {
            ComponentBuilder builder = new ComponentBuilder("§a================\n")
                    .append("目前全服在线：§e" + total.get() + " §a人\n")
                    .append("§a---------------------------\n");

            futures.forEach(future -> {
                ServerData data = future.join();
                builder.append(data.getDisplayName() + "：");

                if (data.isOnline()) {
                    builder.append("§e" + data.getPlayerCount() + " §a人\n玩家：");
                    if (data.getPlayerCount() > 0) {
                        String players = data.getPlayers().stream()
                                .map(ProxiedPlayer::getName)
                                .collect(Collectors.joining(", "));
                        builder.append("§7" + players + "\n");
                    } else {
                        builder.append("§7暂无玩家\n");
                    }
                } else {
                    builder.append("§c离线\n");
                }
                builder.append("§a---------------------------\n");
            });

            builder.append("§a================");
            sender.sendMessage(builder.create());
        }, getProxy().getScheduler());
    }

    // 服务器数据容器类
    private static class ServerData {
        private final String displayName;
        private boolean online;
        private int playerCount;
        private List<ProxiedPlayer> players;

        public ServerData(String displayName) {
            this.displayName = displayName;
            this.online = false;
            this.playerCount = 0;
            this.players = Collections.emptyList();
        }

        // Getter & Setter
        public String getDisplayName() {
            return displayName;
        }

        public boolean isOnline() {
            return online;
        }

        public void setOnline(boolean online) {
            this.online = online;
        }

        public int getPlayerCount() {
            return playerCount;
        }

        public void setPlayerCount(int playerCount) {
            this.playerCount = playerCount;
        }

        public List<ProxiedPlayer> getPlayers() {
            return players;
        }

        public void setPlayers(List<ProxiedPlayer> players) {
            this.players = players;
        }
    }
}