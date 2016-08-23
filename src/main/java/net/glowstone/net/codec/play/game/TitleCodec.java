package net.glowstone.net.codec.play.game;

import com.flowpowered.network.Codec;
import com.flowpowered.network.util.ByteBufUtils;
import io.netty.buffer.ByteBuf;
import net.glowstone.net.message.play.game.TitlePacket;
import net.glowstone.net.message.play.game.TitlePacket.Action;
import net.glowstone.util.TextMessage;

import java.io.IOException;

public final class TitleCodec implements Codec<TitlePacket> {

    @Override
    public TitlePacket decode(ByteBuf buffer) throws IOException {
        int actionId = ByteBufUtils.readVarInt(buffer);
        Action action = Action.getAction(actionId);
        switch (action) {
            case TITLE:
            case SUBTITLE:
                String text = ByteBufUtils.readUTF8(buffer);
                return new TitlePacket(action, TextMessage.decode(text));
            case TIMES:
                int fadeIn = buffer.readInt();
                int stay = buffer.readInt();
                int fadeOut = buffer.readInt();
                return new TitlePacket(action, fadeIn, stay, fadeOut);
            default:
                return new TitlePacket(action);
        }
    }

    @Override
    public ByteBuf encode(ByteBuf buf, TitlePacket message) throws IOException {
        ByteBufUtils.writeVarInt(buf, message.getAction().ordinal());
        switch (message.getAction()) {
            case TITLE:
            case SUBTITLE:
                ByteBufUtils.writeUTF8(buf, message.getText().encode());
                break;
            case TIMES:
                buf.writeInt(message.getFadeIn());
                buf.writeInt(message.getStay());
                buf.writeInt(message.getFadeOut());
                break;
        }
        return buf;
    }
}
