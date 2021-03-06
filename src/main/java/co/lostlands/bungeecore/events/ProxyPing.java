package co.lostlands.bungeecore.events;

import co.lostlands.bungeecore.main;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.Favicon;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.ServerPing;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.event.ProxyPingEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;

import javax.imageio.ImageIO;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProxyPing implements Listener {
    private final main plugin;

    public ProxyPing(main pl) {
        plugin = pl;
    }

    @EventHandler
    public void onPing(ProxyPingEvent event) throws IOException {
        if (!plugin.getConfig().getBoolean("motds.enabled")) return;
        ServerPing response;
        String hostname = event.getConnection().getVirtualHost().getHostName();

        hostname = hostname.replace('.', '_');

        int slots;
        int online;
        ServerPing.Protocol protocol;
        BaseComponent motd;
        Favicon icon;
        ServerPing.Players playerList;

        //Players
        if (plugin.getConfig().getBoolean("motds." + hostname + ".slots.enabled")) {
            slots = plugin.getConfig().getInt("motds." + hostname + ".slots.value");
        } else {
            slots = event.getResponse().getPlayers().getMax();
        }
        if (plugin.getConfig().getBoolean("motds." + hostname + ".online.enabled")) {
            switch(plugin.getConfig().getString("motds." + hostname + ".online.type")) {
                case "override":
                    online = plugin.getConfig().getInt("motds." + hostname + ".online.value");
                    break;
                case "add":
                    int includedOnlineCount = 0;
                    List<String> servers = plugin.getConfig().getStringList("motds." + hostname + ".online.included_servers");
                    if (servers.size() > 0) {
                        for (int i = 0; i < servers.size(); i++) {
                            ServerInfo server = ProxyServer.getInstance().getServerInfo(servers.get(i));
                            if (server != null) {
                                int serverCount = server.getPlayers().size();
                                includedOnlineCount = includedOnlineCount + serverCount;
                            }
                        }
                        online = plugin.getConfig().getInt("motds." + hostname + ".online.value") + includedOnlineCount;
                    } else {
                        online = plugin.getConfig().getInt("motds." + hostname + ".online.value") + event.getResponse().getPlayers().getOnline();
                    }
                    break;
                default:
                    online = event.getResponse().getPlayers().getMax();
                    break;
            }
        } else {
            online = event.getResponse().getPlayers().getOnline();
        }
        if (plugin.getConfig().getBoolean("motds." + hostname + ".hover.enabled")) {
            List<ServerPing.PlayerInfo> hover = new ArrayList<>();
            List<String> values = plugin.getConfig().getStringList("motds." + hostname + ".hover.values");
            if (values.size() > 0) {
                for (int i = 0; i < values.size(); i++) {
                    String playerValue = values.get(i);
                    List<String> placeholderMatches = resolvePlaceholders(playerValue);
                    for (int index = 0; index < placeholderMatches.size(); index++) {
                        String placeholder = placeholderMatches.get(index);
                        if (placeholder.equals("online")) {
                            playerValue = playerValue.replace("{online}", ""+online);
                        } else if (placeholder.startsWith("online_")) {
                            String serverName = placeholder.substring(7, placeholder.length() + 0);
                            ServerInfo server = ProxyServer.getInstance().getServerInfo(serverName);
                            if (server != null) {
                                int serverCount = server.getPlayers().size();
                                playerValue = playerValue.replace("{" + placeholder + "}", "" + serverCount);
                            }
                        }
                    }
                    hover.add(new ServerPing.PlayerInfo(ChatColor.translateAlternateColorCodes('&', playerValue), String.valueOf(i)));
                }
            }
            playerList = new ServerPing.Players(slots, online, hover.toArray(new ServerPing.PlayerInfo[0]));
        } else {
            playerList = new ServerPing.Players(slots, online, event.getResponse().getPlayers().getSample());
        }


        //Protocol
        if (plugin.getConfig().getBoolean("motds." + hostname + ".protocol.enabled")) {
            //set player slots
            protocol = event.getResponse().getVersion();
            protocol.setName(plugin.getConfig().getString("motds." + hostname + ".protocol.value"));
        } else {
            protocol = event.getResponse().getVersion();
        }

        //MOTD
        if (plugin.getConfig().getBoolean("motds." + hostname + ".motd.enabled")) {
            List<String> motds = plugin.getConfig().getStringList("motds." + hostname + ".motd.values");
            if (motds.size() > 0) {
                int index = (int) (Math.random() * (motds.size()) + 0);
                String motdString = motds.get(index);
                List<String> placeholderMatches = resolvePlaceholders(motdString);
                for (int i = 0; i < placeholderMatches.size(); i++) {
                    String placeholder = placeholderMatches.get(i);
                    if (placeholder.equals("online")) {
                        motdString = motdString.replace("{online}", ""+online);
                    } else if (placeholder.startsWith("online_")) {
                        String serverName = placeholder.substring(7, placeholder.length() + 0);
                        ServerInfo server = ProxyServer.getInstance().getServerInfo(serverName);
                        if (server != null) {
                            int serverCount = server.getPlayers().size();
                            motdString = motdString.replace("{" + placeholder + "}", "" + serverCount);
                        }
                    }
                }

                motd = new TextComponent(ChatColor.translateAlternateColorCodes('&', motdString));
            } else {
                motd = event.getResponse().getDescriptionComponent();
            }
        } else {
            motd = event.getResponse().getDescriptionComponent();
        }

        //Server Icon
        if (plugin.getConfig().getBoolean("motds." + hostname + ".icon.enabled")) {
            String path = plugin.getDataFolder().toString() + "/icons/";
            String iconName = plugin.getConfig().getString("motds." + hostname + ".icon.file");
            icon = Favicon.create(ImageIO.read(new File(path, iconName)));
        } else {
            icon = event.getResponse().getFaviconObject();
        }
        response = new ServerPing(protocol, playerList, motd, icon);
        event.setResponse(response);
    }

    public static List<String> resolvePlaceholders(String s) {
        // returns all matches of p in s for first group in regular expression
        List<String> matches = new ArrayList<String>();
        Matcher m = Pattern.compile("\\{([^}]+)}").matcher(s);
        while(m.find()) {
            matches.add(m.group(1));
        }
        return matches;
    }


}
