package net.glowstone.net.handler.play.player;

import com.flowpowered.network.MessageHandler;
import net.glowstone.EventFactory;
import net.glowstone.net.GlowSession;
import net.glowstone.net.message.play.player.TabCompletePacket;
import net.glowstone.net.message.play.player.TabCompleteResponsePacket;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerChatTabCompleteEvent;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TabCompleteHandler implements MessageHandler<GlowSession, TabCompletePacket> {
    @Override
    public void handle(GlowSession session, TabCompletePacket message) {
        Player sender = session.getPlayer();
        String buffer = message.getText();
        List<String> completions = new ArrayList<>();

        // complete command or username
        if (buffer.startsWith("/") || message.isAssumeCommand()) {
            List<String> items;
            if (buffer.startsWith("/")) {
                items = session.getServer().getCommandMap().tabComplete(sender, buffer.substring(1));
            } else {
                items = session.getServer().getCommandMap().tabComplete(sender, buffer);
            }
            if (items != null) {
                completions.addAll(items);
            }
        } else {
            int space = buffer.lastIndexOf(' ');
            String lastWord;
            if (space == -1) {
                lastWord = buffer;
            } else {
                lastWord = buffer.substring(space + 1);
            }

            // from Command
            for (Player player : session.getServer().getOnlinePlayers()) {
                String name = player.getName();
                if (sender.canSee(player) && StringUtil.startsWithIgnoreCase(name, lastWord)) {
                    completions.add(name);
                }
            }
            Collections.sort(completions, String.CASE_INSENSITIVE_ORDER);
        }

        // call event and send response
        EventFactory.callEvent(new PlayerChatTabCompleteEvent(sender, buffer, completions));
        session.send(new TabCompleteResponsePacket(completions));
    }
}
