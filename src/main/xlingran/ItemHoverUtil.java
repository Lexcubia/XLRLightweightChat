package xlingran;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Content;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * 1.21+ 聊天物品悬浮（SHOW_ITEM），保留自定义 Lore / 药水 NBT。
 */
public final class ItemHoverUtil {

    private static final Logger LOGGER = Bukkit.getLogger();
    private static volatile Method serializeItemAsJsonMethod;
    private static volatile boolean serializeMethodResolved;
    private static volatile NmsItemEncoder nmsItemEncoder;

    private ItemHoverUtil() {
    }

    public static HoverEvent createShowItemHover(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return null;
        }
        JsonObject itemJson = buildItemJson(stack.clone());
        if (itemJson == null) {
            return null;
        }
        HoverEvent event = new HoverEvent(HoverEvent.Action.SHOW_ITEM, new ModernShowItem(itemJson));
        event.setV1_21_5(true);
        return event;
    }

    static JsonObject buildItemJson(ItemStack stack) {
        JsonObject fromUnsafe = trySerializeItemAsJson(stack);
        if (hasComponents(fromUnsafe)) {
            return fromUnsafe;
        }
        JsonObject fromNms = tryNmsCodecEncode(stack);
        if (hasComponents(fromNms)) {
            return fromNms;
        }
        JsonObject fromFactory = buildItemJsonFromItemFactory(stack);
        if (hasComponents(fromFactory)) {
            return fromFactory;
        }
        JsonObject merged = mergeItemJson(pickBaseJson(fromNms, fromUnsafe, fromFactory, stack), stack);
        if (merged != null && hasComponents(merged)) {
            return merged;
        }
        return merged != null ? merged : minimalItemJson(stack);
    }

    private static JsonObject pickBaseJson(JsonObject a, JsonObject b, JsonObject c, ItemStack stack) {
        if (a != null) {
            return a;
        }
        if (b != null) {
            return b;
        }
        if (c != null) {
            return c;
        }
        return minimalItemJson(stack);
    }

    private static boolean hasComponents(JsonObject itemJson) {
        return itemJson != null
                && itemJson.has("components")
                && itemJson.get("components").isJsonObject()
                && !itemJson.getAsJsonObject("components").isEmpty();
    }

    private static JsonObject mergeItemJson(JsonObject base, ItemStack stack) {
        JsonObject root = base != null ? base.deepCopy() : minimalItemJson(stack);
        JsonObject components = buildComponentsFromMeta(stack);
        if (components.isEmpty()) {
            return hasComponents(root) ? root : null;
        }
        JsonObject existing = root.has("components") && root.get("components").isJsonObject()
                ? root.getAsJsonObject("components")
                : new JsonObject();
        for (String key : components.keySet()) {
            existing.add(key, components.get(key));
        }
        root.add("components", existing);
        return root;
    }

    private static JsonObject buildComponentsFromMeta(ItemStack stack) {
        JsonObject components = new JsonObject();
        if (!stack.hasItemMeta()) {
            return components;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return components;
        }
        if (meta.hasDisplayName()) {
            String name = meta.getDisplayName();
            if (name != null && !name.isEmpty()) {
                components.add("minecraft:custom_name", textComponent(name));
            }
        }
        if (meta.hasLore()) {
            List<String> lore = meta.getLore();
            if (lore != null && !lore.isEmpty()) {
                JsonArray loreArray = new JsonArray();
                for (String line : lore) {
                    if (line != null) {
                        loreArray.add(textComponent(line));
                    }
                }
                if (!loreArray.isEmpty()) {
                    components.add("minecraft:lore", loreArray);
                }
            }
        }
        if (meta instanceof PotionMeta potionMeta) {
            addPotionContentsComponent(components, potionMeta);
        }
        return components;
    }

    private static JsonObject textComponent(String legacyText) {
        JsonObject comp = new JsonObject();
        comp.addProperty("text", legacyText);
        return comp;
    }

    private static void addPotionContentsComponent(JsonObject components, PotionMeta meta) {
        if (meta.getBasePotionType() != null) {
            String key = meta.getBasePotionType().getKey().getKey();
            if (!"uncraftable".equals(key)) {
                JsonObject potionContents = new JsonObject();
                potionContents.addProperty("potion", "minecraft:" + key);
                components.add("minecraft:potion_contents", potionContents);
                return;
            }
        }
        if (meta.hasCustomEffects() && !meta.getCustomEffects().isEmpty()) {
            JsonObject potionContents = new JsonObject();
            JsonArray customEffects = new JsonArray();
            meta.getCustomEffects().forEach(effect -> {
                JsonObject entry = new JsonObject();
                entry.addProperty("id", effect.getType().getKey().toString());
                entry.addProperty("amplifier", effect.getAmplifier());
                entry.addProperty("duration", effect.getDuration());
                entry.addProperty("ambient", effect.isAmbient());
                entry.addProperty("show_particles", effect.hasParticles());
                entry.addProperty("show_icon", effect.hasIcon());
                customEffects.add(entry);
            });
            potionContents.add("custom_effects", customEffects);
            components.add("minecraft:potion_contents", potionContents);
        }
    }

    private static JsonObject trySerializeItemAsJson(ItemStack stack) {
        Method method = resolveSerializeItemAsJson();
        if (method == null) {
            return null;
        }
        try {
            String json = (String) method.invoke(Bukkit.getUnsafe(), stack);
            if (json == null || json.isBlank()) {
                return null;
            }
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.log(Level.FINE, "[XLRLightweightChat] serializeItemAsJson 失败", e);
            return null;
        }
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

    private static JsonObject tryNmsCodecEncode(ItemStack stack) {
        NmsItemEncoder encoder = resolveNmsEncoder();
        if (encoder == null) {
            return null;
        }
        try {
            Object jsonElement = encoder.encode(stack);
            if (jsonElement == null) {
                return null;
            }
            return JsonParser.parseString(jsonElement.toString()).getAsJsonObject();
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.log(Level.FINE, "[XLRLightweightChat] NMS 物品序列化失败", e);
            return null;
        }
    }

    private static NmsItemEncoder resolveNmsEncoder() {
        if (nmsItemEncoder == null) {
            synchronized (ItemHoverUtil.class) {
                if (nmsItemEncoder == null) {
                    nmsItemEncoder = NmsItemEncoder.tryCreate();
                    if (nmsItemEncoder == NmsItemEncoder.UNAVAILABLE) {
                        LOGGER.log(Level.FINE, "[XLRLightweightChat] NMS 物品编码器不可用");
                    }
                }
            }
        }
        return nmsItemEncoder == NmsItemEncoder.UNAVAILABLE ? null : nmsItemEncoder;
    }

    private static JsonObject buildItemJsonFromItemFactory(ItemStack stack) {
        try {
            String spec = stack.getType().getKey().toString();
            if (stack.hasItemMeta()) {
                ItemMeta meta = stack.getItemMeta();
                if (meta != null) {
                    String componentSpec = meta.getAsComponentString();
                    if (componentSpec != null && !componentSpec.isEmpty() && !"[]".equals(componentSpec)) {
                        spec = spec + componentSpec;
                    }
                }
            }
            ItemStack rebuilt = Bukkit.getItemFactory().createItemStack(spec);
            rebuilt.setAmount(stack.getAmount());
            JsonObject serialized = trySerializeItemAsJson(rebuilt);
            if (hasComponents(serialized)) {
                return serialized;
            }
            serialized = tryNmsCodecEncode(rebuilt);
            if (hasComponents(serialized)) {
                return serialized;
            }
            return mergeItemJson(serialized != null ? serialized : minimalItemJson(rebuilt), rebuilt);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[XLRLightweightChat] 无法构建物品悬浮数据: " + e.getMessage());
            return mergeItemJson(minimalItemJson(stack), stack);
        }
    }

    private static JsonObject minimalItemJson(ItemStack stack) {
        JsonObject root = new JsonObject();
        root.addProperty("id", stack.getType().getKey().toString());
        root.addProperty("count", stack.getAmount());
        return root;
    }

    public static final class ModernShowItem extends Content {

        private final String id;
        private final int count;
        private final JsonObject components;

        ModernShowItem(JsonObject itemJson) {
            this.id = itemJson.has("id") ? itemJson.get("id").getAsString() : "minecraft:stone";
            this.count = itemJson.has("count") ? itemJson.get("count").getAsInt() : 1;
            if (itemJson.has("components") && itemJson.get("components").isJsonObject()) {
                this.components = itemJson.getAsJsonObject("components");
            } else {
                this.components = new JsonObject();
            }
        }

        @Override
        public HoverEvent.Action requiredAction() {
            return HoverEvent.Action.SHOW_ITEM;
        }

        public String getId() {
            return id;
        }

        public int getCount() {
            return count;
        }

        public JsonObject getComponents() {
            return components;
        }
    }

    private static final class NmsItemEncoder {

        static final NmsItemEncoder UNAVAILABLE = new NmsItemEncoder();

        private final Method asNmsCopy;
        private final Method getRegistry;
        private final Method createContext;
        private final Object jsonOpsInstance;
        private final Method encodeStart;
        private final Object codec;
        private final Method getOrThrow;

        private NmsItemEncoder() {
            this.asNmsCopy = null;
            this.getRegistry = null;
            this.createContext = null;
            this.jsonOpsInstance = null;
            this.encodeStart = null;
            this.codec = null;
            this.getOrThrow = null;
        }

        private NmsItemEncoder(Method asNmsCopy, Method getRegistry, Method createContext, Object jsonOpsInstance,
                               Method encodeStart, Object codec, Method getOrThrow) {
            this.asNmsCopy = asNmsCopy;
            this.getRegistry = getRegistry;
            this.createContext = createContext;
            this.jsonOpsInstance = jsonOpsInstance;
            this.encodeStart = encodeStart;
            this.codec = codec;
            this.getOrThrow = getOrThrow;
        }

        static NmsItemEncoder tryCreate() {
            try {
                Class<?> craftItemStack = Class.forName("org.bukkit.craftbukkit.inventory.CraftItemStack");
                Method asNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);

                Class<?> craftRegistry = Class.forName("org.bukkit.craftbukkit.CraftRegistry");
                Method getRegistry = craftRegistry.getMethod("getMinecraftRegistry");

                Class<?> jsonOpsClass = Class.forName("com.mojang.serialization.JsonOps");
                Object jsonOpsInstance = jsonOpsClass.getField("INSTANCE").get(null);

                Class<?> dynamicOpsClass = Class.forName("com.mojang.serialization.DynamicOps");
                Class<?> nmsItemStackClass = Class.forName("net.minecraft.world.item.ItemStack");
                Object codec = resolveItemStackCodec(nmsItemStackClass);
                if (codec == null) {
                    return UNAVAILABLE;
                }

                Method createContext = getRegistry.getReturnType().getMethod("createSerializationContext", dynamicOpsClass);
                Method encodeStart = codec.getClass().getMethod("encodeStart", dynamicOpsClass, Object.class);
                Method getOrThrow = Class.forName("com.mojang.serialization.DataResult").getMethod("getOrThrow");

                return new NmsItemEncoder(asNmsCopy, getRegistry, createContext, jsonOpsInstance,
                        encodeStart, codec, getOrThrow);
            } catch (ReflectiveOperationException e) {
                return UNAVAILABLE;
            }
        }

        Object encode(ItemStack stack) throws ReflectiveOperationException {
            if (asNmsCopy == null) {
                return null;
            }
            Object nmsStack = asNmsCopy.invoke(null, stack);
            Object registry = getRegistry.invoke(null);
            Object ops = createContext.invoke(registry, jsonOpsInstance);
            Object dataResult = encodeStart.invoke(codec, ops, nmsStack);
            return getOrThrow.invoke(dataResult);
        }

        private static Object resolveItemStackCodec(Class<?> nmsItemStackClass) throws NoSuchFieldException, IllegalAccessException {
            for (String fieldName : new String[]{"CODEC", "OPTIONAL_CODEC", "MAP_CODEC"}) {
                try {
                    Field field = nmsItemStackClass.getField(fieldName);
                    Object codec = field.get(null);
                    if (codec != null) {
                        return codec;
                    }
                } catch (NoSuchFieldException ignored) {
                    // try next
                }
            }
            return null;
        }
    }
}
