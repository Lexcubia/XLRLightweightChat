package xlingran;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.inventory.meta.PotionMeta;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.ItemTag;
import net.md_5.bungee.api.chat.hover.content.Item;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 1.21+ SHOW_ITEM：通过 NMS/Paper 序列化 components，避免客户端显示原版/不可合成药水。
 */
public final class ItemHoverUtil {

    private static final Logger LOGGER = Bukkit.getLogger();
    private static volatile Method serializeItemAsJsonMethod;
    private static volatile boolean serializeMethodResolved;
    private static volatile NmsItemJsonEncoder nmsEncoder;

    private ItemHoverUtil() {
    }

    public static JsonObject resolveItemJsonForHover(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        try {
            return resolveItemJson(stack.clone());
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[XLRLightweightChat] 解析物品 JSON 失败", t);
            return null;
        }
    }

    public static HoverEvent createShowItemHover(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        try {
            return createShowItemHoverInternal(stack.clone());
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[XLRLightweightChat] 构建物品悬浮失败: " + t.getMessage(), t);
            return null;
        }
    }

    private static HoverEvent createShowItemHoverInternal(ItemStack stack) {
        return createLegacyHover(stack);
    }

    private static JsonObject resolveItemJson(ItemStack stack) {
        JsonObject fromPaper = tryPaperSerializeItemAsJson(stack);
        if (hasComponents(fromPaper)) {
            return fromPaper;
        }
        JsonObject fromNms = tryNmsEncodeItemJson(stack);
        if (hasComponents(fromNms)) {
            return fromNms;
        }
        return tryEncodeRebuiltStack(stack);
    }

    private static JsonObject tryEncodeRebuiltStack(ItemStack stack) {
        try {
            String spec = stack.getType().getKey().toString();
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                String componentSpec = meta.getAsComponentString();
                if (componentSpec != null && !componentSpec.isEmpty() && !"[]".equals(componentSpec)) {
                    spec = spec + componentSpec;
                }
            }
            ItemStack rebuilt = Bukkit.getItemFactory().createItemStack(spec);
            rebuilt.setAmount(stack.getAmount());
            JsonObject encoded = tryNmsEncodeItemJson(rebuilt);
            if (hasComponents(encoded)) {
                return encoded;
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[XLRLightweightChat] createItemStack 重建编码失败", t);
        }
        return null;
    }

    private static boolean hasComponents(JsonObject itemJson) {
        return itemJson != null
                && itemJson.has("components")
                && itemJson.get("components").isJsonObject()
                && !itemJson.getAsJsonObject("components").isEmpty();
    }

    private static JsonObject minimalItemJson(ItemStack stack) {
        JsonObject root = new JsonObject();
        root.addProperty("id", stack.getType().getKey().toString());
        root.addProperty("count", stack.getAmount());
        return root;
    }

    private static JsonObject tryPaperSerializeItemAsJson(ItemStack stack) {
        Method method = resolveSerializeItemAsJson();
        if (method == null) {
            return null;
        }
        try {
            Object result = method.invoke(Bukkit.getUnsafe(), stack);
            if (result instanceof JsonObject jsonObject) {
                return jsonObject;
            }
            if (result instanceof String json && !json.isBlank()) {
                return JsonParser.parseString(json).getAsJsonObject();
            }
        } catch (Throwable t) {
            LOGGER.log(Level.FINE, "[XLRLightweightChat] serializeItemAsJson 不可用", t);
        }
        return null;
    }

    private static Method resolveSerializeItemAsJson() {
        if (!serializeMethodResolved) {
            synchronized (ItemHoverUtil.class) {
                if (!serializeMethodResolved) {
                    try {
                        serializeItemAsJsonMethod = Bukkit.getUnsafe().getClass()
                                .getMethod("serializeItemAsJson", ItemStack.class);
                    } catch (NoSuchMethodException ignored) {
                        serializeItemAsJsonMethod = null;
                    }
                    serializeMethodResolved = true;
                }
            }
        }
        return serializeItemAsJsonMethod;
    }

    private static JsonObject tryNmsEncodeItemJson(ItemStack stack) {
        NmsItemJsonEncoder encoder = resolveNmsEncoder();
        if (encoder == null) {
            return null;
        }
        try {
            return encoder.encode(stack);
        } catch (Throwable t) {
            LOGGER.log(Level.WARNING, "[XLRLightweightChat] NMS 物品 JSON 编码失败: " + t.getMessage(), t);
            return null;
        }
    }

    private static NmsItemJsonEncoder resolveNmsEncoder() {
        if (nmsEncoder == null) {
            synchronized (ItemHoverUtil.class) {
                if (nmsEncoder == null) {
                    nmsEncoder = NmsItemJsonEncoder.tryCreate();
                }
            }
        }
        return nmsEncoder == NmsItemJsonEncoder.UNAVAILABLE ? null : nmsEncoder;
    }

    private static HoverEvent createLegacyHover(ItemStack stack) {
        String id = stack.getType().getKey().toString();
        int count = stack.getAmount();
        ItemTag tag = null;
        if (stack.hasItemMeta()) {
            ItemMeta meta = stack.getItemMeta();
            if (meta != null) {
                try {
                    String nbt = meta.getAsString();
                    if (nbt != null && !nbt.isBlank()) {
                        tag = ItemTag.ofNbt(nbt);
                    }
                } catch (Throwable ignored) {
                    // ignore
                }
            }
        }
        return new HoverEvent(HoverEvent.Action.SHOW_ITEM, new Item(id, count, tag));
    }

    private static final class NmsItemJsonEncoder {

        static final NmsItemJsonEncoder UNAVAILABLE = new NmsItemJsonEncoder();

        private final Method asNmsCopy;
        private final Method createSerializationContext;
        private final Object registryAccess;
        private final Object jsonOpsInstance;
        private final Method encodeStart;
        private final Object codec;

        private NmsItemJsonEncoder() {
            this.asNmsCopy = null;
            this.createSerializationContext = null;
            this.registryAccess = null;
            this.jsonOpsInstance = null;
            this.encodeStart = null;
            this.codec = null;
        }

        private NmsItemJsonEncoder(Method asNmsCopy, Method createSerializationContext, Object registryAccess,
                                   Object jsonOpsInstance, Method encodeStart, Object codec) {
            this.asNmsCopy = asNmsCopy;
            this.createSerializationContext = createSerializationContext;
            this.registryAccess = registryAccess;
            this.jsonOpsInstance = jsonOpsInstance;
            this.encodeStart = encodeStart;
            this.codec = codec;
        }

        static NmsItemJsonEncoder tryCreate() {
            try {
                Class<?> craftItemStack = SpigotReflection.craftClass("inventory.CraftItemStack");
                Method asNmsCopy = SpigotReflection.resolveMethod(craftItemStack, "asNMSCopy", ItemStack.class);

                Object registryAccess = resolveRegistryAccess();
                if (registryAccess == null) {
                    return UNAVAILABLE;
                }

                Class<?> jsonOpsClass = SpigotReflection.serverClass("com.mojang.serialization.JsonOps");
                Object jsonOpsInstance = jsonOpsClass.getField("INSTANCE").get(null);
                Class<?> dynamicOpsClass = SpigotReflection.serverClass("com.mojang.serialization.DynamicOps");

                Class<?> nmsItemStackClass = SpigotReflection.serverClass("net.minecraft.world.item.ItemStack");
                Object codec = resolveItemStackCodec(nmsItemStackClass);
                if (codec == null) {
                    return UNAVAILABLE;
                }

                Method createSerializationContext = registryAccess.getClass()
                        .getMethod("createSerializationContext", dynamicOpsClass);
                Method encodeStart = codec.getClass().getMethod("encodeStart", dynamicOpsClass, Object.class);

                return new NmsItemJsonEncoder(asNmsCopy, createSerializationContext, registryAccess,
                        jsonOpsInstance, encodeStart, codec);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, "[XLRLightweightChat] NMS 物品编码器初始化失败: " + t.getMessage());
                return UNAVAILABLE;
            }
        }

        JsonObject encode(ItemStack stack) throws ReflectiveOperationException {
            Object nmsStack = asNmsCopy.invoke(null, stack);
            Object ops = createSerializationContext.invoke(registryAccess, jsonOpsInstance);
            Object dataResult = encodeStart.invoke(codec, ops, nmsStack);
            JsonElement element = unwrapDataResult(dataResult);
            if (element == null || !element.isJsonObject()) {
                return null;
            }
            JsonObject encoded = element.getAsJsonObject();
            if (encoded.has("item") && encoded.get("item").isJsonObject()) {
                encoded = encoded.getAsJsonObject("item");
            }
            return encoded;
        }

        private static Object resolveRegistryAccess() {
            try {
                Object craftServer = Bukkit.getServer();
                Object minecraftServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);
                if (minecraftServer != null) {
                    return minecraftServer.getClass().getMethod("registryAccess").invoke(minecraftServer);
                }
            } catch (Throwable ignored) {
                // try static getServer
            }
            try {
                Object craftServer = Bukkit.getServer();
                Object minecraftServer = craftServer.getClass().getMethod("getServer").invoke(craftServer);
                if (minecraftServer != null) {
                    return minecraftServer.getClass().getMethod("registryAccess").invoke(minecraftServer);
                }
            } catch (Throwable ignored) {
                // try CraftRegistry
            }
            try {
                Class<?> craftRegistry = SpigotReflection.craftClass("CraftRegistry");
                return craftRegistry.getMethod("getMinecraftRegistry").invoke(null);
            } catch (Throwable ignored) {
                return null;
            }
        }

        private static Object resolveItemStackCodec(Class<?> nmsItemStackClass) throws ReflectiveOperationException {
            for (String fieldName : new String[]{"CODEC", "MAP_CODEC"}) {
                try {
                    Field field = nmsItemStackClass.getField(fieldName);
                    Object codec = field.get(null);
                    if (codec != null) {
                        return codec;
                    }
                } catch (NoSuchFieldException ignored) {
                    // next
                }
            }
            return null;
        }

        private static JsonElement unwrapDataResult(Object dataResult) throws ReflectiveOperationException {
            if (dataResult == null) {
                return null;
            }
            try {
                Method resultMethod = dataResult.getClass().getMethod("result");
                Object optional = resultMethod.invoke(dataResult);
                if (optional instanceof Optional<?> opt && opt.isPresent()) {
                    Object value = opt.get();
                    if (value instanceof JsonElement jsonElement) {
                        return jsonElement;
                    }
                    return JsonParser.parseString(value.toString());
                }
            } catch (NoSuchMethodException ignored) {
                // try getOrThrow
            }
            try {
                Method getOrThrow = dataResult.getClass().getMethod("getOrThrow");
                Object value = getOrThrow.invoke(dataResult);
                if (value instanceof JsonElement jsonElement) {
                    return jsonElement;
                }
                return JsonParser.parseString(value.toString());
            } catch (NoSuchMethodException e) {
                Method getOrThrow = dataResult.getClass().getMethod("getOrThrow", java.util.function.Function.class);
                Object value = getOrThrow.invoke(dataResult, (java.util.function.Function<Object, ?>) err -> {
                    throw new IllegalStateException(String.valueOf(err));
                });
                if (value instanceof JsonElement jsonElement) {
                    return jsonElement;
                }
                return JsonParser.parseString(value.toString());
            }
        }
    }
}
