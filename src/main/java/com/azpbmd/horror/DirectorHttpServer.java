package com.azpbmd.horror;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

final class DirectorHttpServer {
    private final HorrorDirectorPlugin plugin;
    private final String token;
    private final ScareService scares;
    private HttpServer server;

    DirectorHttpServer(HorrorDirectorPlugin plugin, String bind, int port, String token, ScareService scares)
            throws IOException {
        this.plugin = plugin;
        this.token = token;
        this.scares = scares;
        server = HttpServer.create(new InetSocketAddress(bind, port), 0);
        server.createContext("/v1/health", this::health);
        server.createContext("/v1/players", this::players);
        server.createContext("/v1/campaigns", this::campaigns);
        server.createContext("/v1/types", this::types);
        server.createContext("/v1/scare", this::scare);
        server.createContext("/v1/command", this::command);
        server.setExecutor(null);
    }

    void start() {
        server.start();
    }

    void stop() {
        server.stop(0);
    }

    private void health(HttpExchange ex) throws IOException {
        if (!auth(ex)) {
            return;
        }
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, "{\"ok\":false}");
            return;
        }
        send(ex, 200, "{\"ok\":true,\"players\":" + Bukkit.getOnlinePlayers().size() + "}");
    }

    private void players(HttpExchange ex) throws IOException {
        if (!auth(ex)) {
            return;
        }
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, "{\"ok\":false}");
            return;
        }
        try {
            JsonObject snap = main(() -> plugin.snapshotAll()).get(5, TimeUnit.SECONDS);
            send(ex, 200, snap.toString());
        } catch (Exception e) {
            send(ex, 500, "{\"ok\":false,\"error\":\"" + escape(e.getMessage()) + "\"}");
        }
    }

    private void campaigns(HttpExchange ex) throws IOException {
        if (!auth(ex)) {
            return;
        }
        if (!"GET".equals(ex.getRequestMethod())) {
            send(ex, 405, "{\"ok\":false}");
            return;
        }
        try {
            JsonObject snap = main(() -> plugin.engine() == null
                    ? new JsonObject() : plugin.engine().snapshotAll()).get(5, TimeUnit.SECONDS);
            send(ex, 200, snap.toString());
        } catch (Exception e) {
            send(ex, 500, "{\"ok\":false,\"error\":\"" + escape(String.valueOf(e.getMessage())) + "\"}");
        }
    }

    private void types(HttpExchange ex) throws IOException {
        if (!auth(ex)) {
            return;
        }
        send(ex, 200, "{\"types\":[\"" + String.join("\",\"", scares.types()) + "\"]}");
    }

    private void scare(HttpExchange ex) throws IOException {
        if (!auth(ex)) {
            return;
        }
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, "{\"ok\":false}");
            return;
        }
        JsonObject body = readJson(ex);
        String type = body.has("type") ? body.get("type").getAsString() : "";
        JsonObject params = body.has("params") && body.get("params").isJsonObject()
                ? body.getAsJsonObject("params") : ScareService.params();
        try {
            List<String> results = main(() -> {
                List<String> out = new ArrayList<>();
                for (Player player : resolve(body)) {
                    out.add(player.getName() + ": " + scares.run(player, type, params));
                }
                return out;
            }).get(8, TimeUnit.SECONDS);
            send(ex, 200, "{\"ok\":true,\"results\":[\""
                    + String.join("\",\"", results.stream().map(DirectorHttpServer::escape).toList())
                    + "\"]}");
        } catch (Exception e) {
            send(ex, 500, "{\"ok\":false,\"error\":\"" + escape(String.valueOf(e.getMessage())) + "\"}");
        }
    }

    private void command(HttpExchange ex) throws IOException {
        if (!auth(ex)) {
            return;
        }
        if (!"POST".equals(ex.getRequestMethod())) {
            send(ex, 405, "{\"ok\":false}");
            return;
        }
        JsonObject body = readJson(ex);
        String cmd = body.has("command") ? body.get("command").getAsString() : "";
        if (cmd.isBlank()) {
            send(ex, 400, "{\"ok\":false,\"error\":\"missing command\"}");
            return;
        }
        try {
            boolean ok = main(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)).get(8, TimeUnit.SECONDS);
            send(ex, 200, "{\"ok\":" + ok + "}");
        } catch (Exception e) {
            send(ex, 500, "{\"ok\":false,\"error\":\"" + escape(String.valueOf(e.getMessage())) + "\"}");
        }
    }

    private List<Player> resolve(JsonObject body) {
        List<Player> out = new ArrayList<>();
        String who = "";
        if (body.has("player")) {
            who = body.get("player").getAsString();
        } else if (body.has("players") && body.get("players").isJsonPrimitive()) {
            who = body.get("players").getAsString();
        }
        if (who.isBlank() || "*".equals(who) || "all".equalsIgnoreCase(who)) {
            out.addAll(Bukkit.getOnlinePlayers());
            return out;
        }
        if (body.has("players") && body.get("players").isJsonArray()) {
            body.getAsJsonArray("players").forEach(el -> {
                Player p = Bukkit.getPlayerExact(el.getAsString());
                if (p != null) {
                    out.add(p);
                }
            });
            return out;
        }
        Player exact = Bukkit.getPlayerExact(who);
        if (exact != null) {
            out.add(exact);
        }
        return out;
    }

    private boolean auth(HttpExchange ex) throws IOException {
        String header = ex.getRequestHeaders().getFirst("Authorization");
        String alt = ex.getRequestHeaders().getFirst("X-Horror-Token");
        String got = header != null && header.toLowerCase(Locale.ROOT).startsWith("bearer ")
                ? header.substring(7).trim() : (alt == null ? "" : alt.trim());
        if (!token.isEmpty() && token.equals(got)) {
            return true;
        }
        send(ex, 401, "{\"ok\":false,\"error\":\"unauthorized\"}");
        return false;
    }

    private JsonObject readJson(HttpExchange ex) throws IOException {
        try (InputStream in = ex.getRequestBody()) {
            String raw = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                return new JsonObject();
            }
            return JsonParser.parseString(raw).getAsJsonObject();
        }
    }

    private <T> CompletableFuture<T> main(java.util.function.Supplier<T> fn) {
        CompletableFuture<T> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                future.complete(fn.get());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
