package cn.org.agatha.agShare;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class AgShare extends JavaPlugin implements Listener {

    // 请求者 -> 目标接收者
    private final Map<UUID, UUID> pendingRequests = new HashMap<>();

    // 接收者 -> 请求者
    private final Map<UUID, UUID> incomingRequests = new HashMap<>();

    @Override
    public void onEnable() {
        getLogger().info("AgShare 插件已加载！");
        Bukkit.getPluginManager().registerEvents(this, this); // 注册监听器
    }

    @Override
    public void onDisable() {
        getLogger().info("AgShare 插件已卸载！");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("该命令只能由玩家使用。");
            return true;
        }

        Player player = (Player) sender;
        String cmdName = command.getName().toLowerCase();

        try {
            switch (cmdName) {
                case "share":
                    handleShareCommand(player, args);
                    break;
                case "shareacc":
                    handleAcceptCommand(player);
                    break;
                case "sharedeny":
                    handleDenyCommand(player);
                    break;
                case "sharecancel":
                    handleCancelCommand(player);
                    break;
                default:
                    player.sendMessage("未知命令，请输入有效的命令。");
                    return true;
            }
        } catch (Exception e) {
            player.sendMessage("§c发生了一个内部错误，请联系管理员。");
            getLogger().severe("执行命令时发生异常: " + e.getMessage());
            e.printStackTrace();
        }

        return true;
    }

    // 处理 /share <player>
    private void handleShareCommand(Player sender, String[] args) {
        if (args.length < 1) {
            sender.sendMessage("用法: /share <玩家名>");
            return;
        }

        Player target = getServer().getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            sender.sendMessage("目标玩家不在线或不存在。");
            return;
        }

        UUID sUUID = sender.getUniqueId();
        UUID tUUID = target.getUniqueId();

        if (sUUID.equals(tUUID)) {
            sender.sendMessage("不能向自己发送请求。");
            return;
        }

        if (pendingRequests.containsKey(sUUID)) {
            sender.sendMessage("你已经发送了一个请求，请先等待回应或输入 /sharecancel 取消。");
            return;
        }

        if (incomingRequests.containsKey(tUUID)) {
            sender.sendMessage("目标已经有待处理的请求了。");
            return;
        }

        pendingRequests.put(sUUID, tUUID);
        incomingRequests.put(tUUID, sUUID);

        sender.sendMessage("已向 §6" + target.getName() + " §r发送背包查看请求，如需取消，请输入 /sharecancel。");
        target.sendMessage("玩家 §6" + sender.getName() + " §r想查看你的背包，请输入 /shareacc 接受，/sharedeny 拒绝。");
    }

    // 处理 /shareacc
    private void handleAcceptCommand(Player receiver) {
        UUID rUUID = receiver.getUniqueId();

        if (!incomingRequests.containsKey(rUUID)) {
            receiver.sendMessage("你没有待处理的请求。");
            return;
        }

        UUID requesterUUID = incomingRequests.get(rUUID);
        Player requester = getServer().getPlayer(requesterUUID);

        if (requester == null || !requester.isOnline()) {
            receiver.sendMessage("请求者已下线。");
            clearRequests(requesterUUID, rUUID);
            return;
        }

        openCustomInventory(requester, receiver);
        requester.sendMessage("你正在查看 §6" + receiver.getName() + " §r的背包。");

        clearRequests(requesterUUID, rUUID);
    }

    // 处理 /sharedeny
    private void handleDenyCommand(Player receiver) {
        UUID rUUID = receiver.getUniqueId();

        if (!incomingRequests.containsKey(rUUID)) {
            receiver.sendMessage("你没有待处理的请求。");
            return;
        }

        UUID requesterUUID = incomingRequests.get(rUUID);
        Player requester = getServer().getPlayer(requesterUUID);

        receiver.sendMessage("你已拒绝来自 §6" + (requester != null ? requester.getName() : "未知玩家") + " §r的背包查看请求。");
        if (requester != null && requester.isOnline()) {
            requester.sendMessage("玩家 §6" + receiver.getName() + " §r拒绝了你的请求。");
        }

        clearRequests(requesterUUID, rUUID);
    }

    // 处理 /sharecancel
    private void handleCancelCommand(Player player) {
        UUID pUUID = player.getUniqueId();

        if (!pendingRequests.containsKey(pUUID)) {
            player.sendMessage("你没有待处理的请求。");
            return;
        }

        UUID targetUUID = pendingRequests.get(pUUID);
        Player target = getServer().getPlayer(targetUUID);

        if (target != null && target.isOnline()) {
            target.sendMessage("玩家 §6" + player.getName() + " §r取消了对你的请求。");
        }

        clearRequests(pUUID, targetUUID);
        player.sendMessage("你已取消发送的背包查看请求。");
    }

    // 清除两个玩家之间的请求记录
    private void clearRequests(UUID senderUUID, UUID targetUUID) {
        pendingRequests.remove(senderUUID);
        incomingRequests.remove(targetUUID);
    }

    // 自定义打开对方背包的方法（6x9 格子）
    public void openCustomInventory(Player requester, Player receiver) {
        String title = receiver.getName();
        Inventory customInventory = Bukkit.createInventory(null, 54, title);

        ItemStack[] contents = receiver.getInventory().getContents();

        // 前五行 (0~44) 放置背包内容（包含穿戴、副手）
        for (int i = 0; i < 45; i++) {
            if (i < contents.length) {
                customInventory.setItem(i, contents[i]);
            }
        }

        // 第六行 (45~53) 放装饰玻璃
        for (int i = 45; i < 54; i++) {
            customInventory.setItem(i, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
        }
        customInventory.setItem(49, new ItemStack(Material.BEDROCK));
        requester.openInventory(customInventory);
    }

    // 监听库存点击事件，禁止操作
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getItem(49) != null) {
            if (event.getInventory().getItem(49).getType() == Material.BEDROCK){
                event.setCancelled(true);
            }
        }
    }
}
