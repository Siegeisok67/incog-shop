package com.snipeyfresh.incogshop.discord;

import com.snipeyfresh.incogshop.IncogShopPlugin;
import com.snipeyfresh.incogshop.history.PriceHistoryManager.PricePoint;
import com.snipeyfresh.incogshop.market.MarketEntry;
import com.snipeyfresh.incogshop.util.Text;
import github.scarsz.discordsrv.DiscordSRV;
import org.bukkit.Material;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

public final class DiscordPriceBridge {
    private static final Pattern WINDOW = Pattern.compile("^(\\d+)([mhd])$", Pattern.CASE_INSENSITIVE);

    private final IncogShopPlugin plugin;
    private Object jda;
    private Object listenerProxy;
    private Method removeEventListenerMethod;
    private boolean registered;

    public DiscordPriceBridge(IncogShopPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (plugin.getConfig().isConfigurationSection("discord-price-check")
                && !plugin.getConfig().contains("discord-price-check.enabled")
                && plugin.getConfig().contains("channel-id")) {
            plugin.getLogger().severe("Discord price-check config appears to be incorrectly indented. "
                    + "Move enabled/channel-id/command-prefix/history settings under discord-price-check:.");
        }
        if (!plugin.getConfig().getBoolean("discord-price-check.enabled", false)) {
            plugin.getLogger().info("Discord price checks are disabled (discord-price-check.enabled=false or missing).");
            return;
        }
        if (plugin.getServer().getPluginManager().getPlugin("DiscordSRV") == null) {
            plugin.getLogger().warning("Discord price checks are enabled, but DiscordSRV is not installed.");
            return;
        }

        String channelId = plugin.getConfig().getString("discord-price-check.channel-id", "").trim();
        if (channelId.isEmpty()) {
            plugin.getLogger().warning("Discord price checks are enabled, but discord-price-check.channel-id is empty.");
            return;
        }

        try {
            Object discordSrv = DiscordSRV.getPlugin();

            // Use reflection for the entire JDA boundary so Incog-Shop never needs
            // a compile-time or runtime link to a particular JDA version.
            Method getJda = discordSrv.getClass().getMethod("getJda");
            jda = getJda.invoke(discordSrv);
            if (jda == null) {
                plugin.getLogger().warning("DiscordSRV is present, but its JDA instance is not ready.");
                return;
            }

            ClassLoader loader = jda.getClass().getClassLoader();
            Class<?> eventListener = Class.forName("net.dv8tion.jda.api.hooks.EventListener", true, loader);

            listenerProxy = Proxy.newProxyInstance(
                    loader,
                    new Class<?>[]{eventListener},
                    (proxy, method, args) -> {
                        if ("onEvent".equals(method.getName()) && args != null && args.length == 1 && args[0] != null) {
                            handleGenericEvent(args[0]);
                        }
                        return null;
                    }
            );

            Method add = findMethod(jda.getClass(), "addEventListener", Object[].class);
            removeEventListenerMethod = findMethod(jda.getClass(), "removeEventListener", Object[].class);

            add.invoke(jda, (Object) new Object[]{listenerProxy});
            registered = true;
            plugin.getLogger().info("Discord price checks enabled for channel ID " + channelId + ".");
        } catch (Throwable ex) {
            plugin.getLogger().warning("Could not attach to DiscordSRV/JDA: " + rootMessage(ex));
        }
    }

    public void stop() {
        if (!registered || jda == null || listenerProxy == null || removeEventListenerMethod == null) return;
        try {
            removeEventListenerMethod.invoke(jda, (Object) new Object[]{listenerProxy});
        } catch (Throwable ignored) {}
        registered = false;
        listenerProxy = null;
        jda = null;
        removeEventListenerMethod = null;
    }

    private void handleGenericEvent(Object event) {
        try {
            if (!event.getClass().getSimpleName().equals("MessageReceivedEvent")) return;

            Object author = invoke(event, "getAuthor");
            if ((boolean) invoke(author, "isBot")) return;

            boolean fromGuild = (boolean) invoke(event, "isFromGuild");
            if (!fromGuild) return;

            Object channel = invoke(event, "getChannel");
            String channelId = String.valueOf(invoke(channel, "getId"));
            String configuredChannel = plugin.getConfig().getString("discord-price-check.channel-id", "").trim();
            if (!channelId.equals(configuredChannel)) return;

            Object message = invoke(event, "getMessage");
            String raw = String.valueOf(invoke(message, "getContentRaw")).trim();
            handleMessage(channel, raw);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Discord price command failed: " + rootMessage(ex));
        }
    }

    private void handleMessage(Object channel, String raw) throws Exception {
        String prefix = plugin.getConfig().getString("discord-price-check.command-prefix", "!");
        String command = prefix + "price";
        String help = prefix + "pricehelp";

        if (raw.equalsIgnoreCase(help)) {
            send(channel, "Use `" + command + " <item>` or `" + command
                    + " <item> <window>`. Examples: `" + command + " diamond`, `"
                    + command + " netherite_ingot 24h`.");
            return;
        }

        if (!raw.regionMatches(true, 0, command, 0, command.length())) return;
        if (raw.length() > command.length() && !Character.isWhitespace(raw.charAt(command.length()))) return;

        String query = raw.substring(command.length()).trim();
        if (query.isEmpty()) {
            send(channel, "Usage: `" + command + " <item> [1h|7h|24h|7d]`");
            return;
        }

        String requestedWindow = null;
        String[] words = query.split("\\s+");
        if (words.length > 1 && WINDOW.matcher(words[words.length - 1]).matches()) {
            requestedWindow = words[words.length - 1].toLowerCase(Locale.ROOT);
            query = String.join(" ", Arrays.copyOf(words, words.length - 1));
        }

        Material material = resolveMaterial(query);
        if (material == null || !plugin.market().isTradable(material)) {
            List<Material> suggestions = suggestions(query);
            String suffix = suggestions.isEmpty() ? "" : " Did you mean: "
                    + suggestions.stream().map(m -> "`" + Text.prettyEnum(m.name()) + "`").toList() + "?";
            send(channel, "I couldn't find that market item." + suffix);
            return;
        }

        sendPrice(channel, material, requestedWindow);
    }

    private void sendPrice(Object channel, Material material, String requestedWindow) throws Exception {
        MarketEntry entry = plugin.market().entry(material);
        if (entry == null) return;

        double current = plugin.market().buyUnitPrice(material);
        double currentSell = plugin.market().sellUnitPrice(material);
        double configuredBase = entry.basePrice();
        double startingPrice = com.snipeyfresh.incogshop.market.MarketManager.defaultBasePrice(material);
        double versusStart = percentChange(startingPrice, current);

        StringBuilder out = new StringBuilder();
        out.append("**").append(Text.prettyEnum(material.name())).append(" — Market Price**\n")
                .append("Current Buy: **").append(plugin.money(current)).append("**\n")
                .append("Current Sell: **").append(plugin.money(currentSell)).append("**\n")
                .append("Original Starting Price: **").append(plugin.money(startingPrice)).append("**\n")
                .append("Vs. Starting Price: **").append(signedPercent(versusStart)).append("**\n");

        if (Math.abs(configuredBase - startingPrice) >= 0.005) {
            out.append("Admin Base Price: **").append(plugin.money(configuredBase)).append("**\n");
        }

        out.append("Stored Stock: **").append(entry.stock());
        if (plugin.market().isInfiniteStockEnabled()) out.append(" (∞ buying)");
        out.append("**\n")
                .append("Stock Mode: **")
                .append(plugin.market().isInfiniteStockEnabled() ? "Infinite" : "Limited")
                .append("**\n");

        List<String> windows = new ArrayList<>();
        if (requestedWindow != null) {
            windows.add(requestedWindow);
        } else {
            windows.addAll(plugin.getConfig().getStringList("discord-price-check.default-windows"));
            if (windows.isEmpty()) windows.addAll(List.of("1h", "7h", "24h", "7d"));
        }

        long now = System.currentTimeMillis();
        for (String window : windows) {
            long duration = plugin.history().parseWindowMillis(window);
            if (duration <= 0) continue;

            PricePoint old = plugin.history().atOrBefore(material, now - duration);
            out.append("\n").append(window.toUpperCase(Locale.ROOT)).append(" Change: ");
            if (old == null) {
                out.append("`Not enough history yet`");
            } else {
                double change = percentChange(old.buyPrice(), current);
                out.append("**").append(plugin.money(old.buyPrice()))
                        .append(" → ").append(plugin.money(current))
                        .append(" (").append(signedPercent(change)).append(")**");
            }
        }

        PricePoint oldest = plugin.history().oldest(material);
        if (oldest != null) {
            out.append("\n\n_Incog-Shop history since ").append(Instant.ofEpochMilli(oldest.timestamp())).append("_");
        } else {
            out.append("\n\n_Incog-Shop price history begins after 1.5.8 is installed._");
        }

        send(channel, out.toString());
    }

    private void send(Object channel, String text) throws Exception {
        Object action = invoke(channel, "sendMessage", String.class, text);
        invoke(action, "queue");
    }

    private Material resolveMaterial(String query) {
        String normalized = query.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        Material exact = Material.matchMaterial(normalized);
        if (exact != null) return exact;

        String compact = normalized.replace("_", "");
        for (Material m : plugin.market().tradableMaterials()) {
            if (m.name().replace("_", "").equalsIgnoreCase(compact)) return m;
            if (Text.prettyEnum(m.name()).replace(" ", "").equalsIgnoreCase(query.replace(" ", ""))) return m;
        }
        return null;
    }

    private List<Material> suggestions(String query) {
        String q = query.toLowerCase(Locale.ROOT).replace("_", " ").trim();
        return plugin.market().tradableMaterials().stream()
                .filter(m -> Text.prettyEnum(m.name()).toLowerCase(Locale.ROOT).contains(q)
                        || m.name().toLowerCase(Locale.ROOT).contains(q.replace(" ", "_")))
                .limit(5)
                .toList();
    }

    private double percentChange(double oldValue, double newValue) {
        if (oldValue <= 0) return 0;
        return ((newValue - oldValue) / oldValue) * 100.0;
    }

    private String signedPercent(double value) {
        return String.format(Locale.US, "%+.2f%%", value);
    }

    private Object invoke(Object target, String methodName) throws Exception {
        return target.getClass().getMethod(methodName).invoke(target);
    }

    private Object invoke(Object target, String methodName, Class<?> parameterType, Object argument) throws Exception {
        return target.getClass().getMethod(methodName, parameterType).invoke(target, argument);
    }

    private Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        try {
            return type.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException ex) {
            for (Method method : type.getMethods()) {
                if (method.getName().equals(name) && method.getParameterCount() == parameterTypes.length) return method;
            }
            throw ex;
        }
    }

    private String rootMessage(Throwable throwable) {
        Throwable t = throwable;
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + ": " + String.valueOf(t.getMessage());
    }
}
