package fan_world_me.multitoolshelp;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.UUID;

public class GivefCommand {

    public static final String KEY_TYPE   = "mth_type";
    public static final String KEY_RINGS  = "mth_rings";
    public static final String KEY_RADIUS = "mth_radius";
    public static final String KEY_UUID   = "mth_uuid";

    public static final String TYPE_NUKE    = "nuke";
    public static final String TYPE_STAB    = "stab";
    public static final String TYPE_RAILGUN = "railgun";

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(
                CommandManager.literal("givef")
                    // OP уровень 2 или синглплеер (хост)
                    .requires(src -> {
                        net.minecraft.server.MinecraftServer server = src.getServer();
                        // при отправке дерева команд на входе в мир source может быть без сервера
                        if (server == null) return true;
                        if (server.isSingleplayer()) return true;
                        if (src.getPlayer() == null) return false;
                        return server.getPlayerManager()
                            .isOperator(new net.minecraft.server.PlayerConfigEntry(src.getPlayer().getGameProfile()));
                    })

                    // /givef nuke [amount] [rings]
                    .then(CommandManager.literal("nuke")
                        .executes(ctx -> giveNuke(ctx, 1, 10))
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 64))
                            .executes(ctx -> giveNuke(ctx, IntegerArgumentType.getInteger(ctx, "amount"), 10))
                            .then(CommandManager.argument("rings", IntegerArgumentType.integer(1, 32))
                                .executes(ctx -> giveNuke(ctx,
                                    IntegerArgumentType.getInteger(ctx, "amount"),
                                    IntegerArgumentType.getInteger(ctx, "rings"))))))

                    // /givef stab [amount]
                    .then(CommandManager.literal("stab")
                        .executes(ctx -> giveStab(ctx, 1))
                        .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 64))
                            .executes(ctx -> giveStab(ctx, IntegerArgumentType.getInteger(ctx, "amount")))))

                    // /givef railgun straight <amount> [radius]
                    .then(CommandManager.literal("railgun")
                        .then(CommandManager.literal("straight")
                            .then(CommandManager.argument("amount", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> giveRailgun(ctx, IntegerArgumentType.getInteger(ctx, "amount"), 1.0))
                                .then(CommandManager.argument("radius", DoubleArgumentType.doubleArg(0.0, 50.0))
                                    .executes(ctx -> giveRailgun(ctx,
                                        IntegerArgumentType.getInteger(ctx, "amount"),
                                        DoubleArgumentType.getDouble(ctx, "radius")))))))
            )
        );

        UseItemCallback.EVENT.register((player, world, hand) -> {
            if (world.isClient()) return ActionResult.PASS;

            ItemStack stack = player.getStackInHand(hand);
            if (!stack.isOf(Items.FISHING_ROD)) return ActionResult.PASS;

            NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
            if (customData == null) return ActionResult.PASS;

            NbtCompound nbt = customData.copyNbt();
            if (!nbt.contains(KEY_TYPE)) return ActionResult.PASS;

            String type = nbt.getString(KEY_TYPE, "");
            if (type.isEmpty()) return ActionResult.PASS;

            ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;
            ServerWorld serverWorld = (ServerWorld) serverPlayer.getEntityWorld();

            BlockHitResult hit = raycastBlock(serverPlayer, 500.0);
            if (hit == null || hit.getType() != HitResult.Type.BLOCK) {
                player.sendMessage(Text.literal("§cNo block in crosshair!"), true);
                return ActionResult.PASS;
            }

            BlockPos pos = hit.getBlockPos();

            switch (type) {
                case TYPE_NUKE -> {
                    int rings = nbt.getInt(KEY_RINGS, 10);
                    NukeLogic.summon(serverWorld, pos.getX(), pos.getY() + 72, pos.getZ(), rings);
                    consumeItem(serverPlayer, hand, stack);
                    return ActionResult.SUCCESS;
                }
                case TYPE_STAB -> {
                    StabLogic.summon(serverWorld, pos.getX(), pos.getZ());
                    consumeItem(serverPlayer, hand, stack);
                    return ActionResult.SUCCESS;
                }
                case TYPE_RAILGUN -> {
                    double radius = nbt.getDouble(KEY_RADIUS, 1.0);
                    Vec3d target = Vec3d.ofCenter(pos);
                    RailgunLogic.fire(serverPlayer, target, radius);
                    consumeItem(serverPlayer, hand, stack);
                    return ActionResult.SUCCESS;
                }
            }

            return ActionResult.PASS;
        });
    }

    private static int giveNuke(CommandContext<ServerCommandSource> ctx, int amount, int rings) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            for (int i = 0; i < amount; i++) {
                player.getInventory().insertStack(buildItem(TYPE_NUKE, "§c§lNuke Shot", rings, 0));
            }
            ctx.getSource().sendFeedback(() -> Text.literal("§aGiven " + amount + " Nuke Shot (rings=" + rings + ")"), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Must be a player"));
            return 0;
        }
    }

    private static int giveStab(CommandContext<ServerCommandSource> ctx, int amount) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            for (int i = 0; i < amount; i++) {
                player.getInventory().insertStack(buildItem(TYPE_STAB, "§b§lStab Shot", 0, 0));
            }
            ctx.getSource().sendFeedback(() -> Text.literal("§aGiven " + amount + " Stab Shot"), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Must be a player"));
            return 0;
        }
    }

    private static int giveRailgun(CommandContext<ServerCommandSource> ctx, int amount, double radius) {
        try {
            ServerPlayerEntity player = ctx.getSource().getPlayerOrThrow();
            for (int i = 0; i < amount; i++) {
                player.getInventory().insertStack(buildItem(TYPE_RAILGUN, "§e§lRailgun", 0, radius));
            }
            ctx.getSource().sendFeedback(() -> Text.literal("§aGiven " + amount + " Railgun (radius=" + radius + ")"), false);
            return 1;
        } catch (Exception e) {
            ctx.getSource().sendError(Text.literal("Must be a player"));
            return 0;
        }
    }

    private static ItemStack buildItem(String type, String name, int rings, double radius) {
        ItemStack stack = new ItemStack(Items.FISHING_ROD);
        stack.set(DataComponentTypes.CUSTOM_NAME, Text.literal(name));
        NbtCompound nbt = new NbtCompound();
        nbt.putString(KEY_UUID, UUID.randomUUID().toString());
        nbt.putString(KEY_TYPE, type);
        if (rings > 0)  nbt.putInt(KEY_RINGS, rings);
        if (TYPE_RAILGUN.equals(type)) nbt.putDouble(KEY_RADIUS, radius);
        stack.set(DataComponentTypes.CUSTOM_DATA, NbtComponent.of(nbt));
        return stack;
    }

    private static BlockHitResult raycastBlock(ServerPlayerEntity player, double range) {
        Vec3d start = player.getEyePos();
        Vec3d end = start.add(player.getRotationVec(1.0f).multiply(range));
        HitResult result = ((ServerWorld) player.getEntityWorld()).raycast(new RaycastContext(
            start, end,
            RaycastContext.ShapeType.OUTLINE,
            RaycastContext.FluidHandling.NONE,
            player
        ));
        return result instanceof BlockHitResult bhr ? bhr : null;
    }

    private static void consumeItem(ServerPlayerEntity player, Hand hand, ItemStack stack) {
        player.sendEquipmentBreakStatus(stack.getItem(),
            hand == Hand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        if (hand == Hand.MAIN_HAND) {
            player.getInventory().removeStack(player.getInventory().getSelectedSlot(), 1);
        } else {
            player.getOffHandStack().decrement(1);
        }
    }
}
