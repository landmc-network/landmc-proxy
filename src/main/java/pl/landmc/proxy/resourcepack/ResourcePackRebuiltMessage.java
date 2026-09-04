package pl.landmc.proxy.resourcepack;

import pl.landmc.platform.messaging.message.NetworkMessage;

/**
 * Announces that the network's resource pack has been rebuilt.
 *
 * <p>This is what replaces polling. The original asked an HTTP endpoint every fifteen seconds
 * whether the pack had changed - about six thousand requests a day to hear "no" almost every
 * time, and up to fifteen seconds of delay when the answer was finally "yes". The builder now
 * says so once, and every proxy re-reads the manifest immediately.
 *
 * <p>The manifest itself is deliberately not in the payload. It carries a URL template, a hash
 * and kick messages, and keeping it in one place - the endpoint that serves it - means a proxy
 * that starts after the rebuild and one that was running both read exactly the same document.
 *
 * <p>Published by whichever project builds the pack. Until that exists, the proxy still fetches
 * the manifest once at startup, so nothing here blocks the migration.
 *
 * @param sha1 the new pack hash, logged so an operator can match a rebuild to a proxy reload
 */
public record ResourcePackRebuiltMessage(String sha1) implements NetworkMessage {

    public static final String TYPE = "resourcepack.rebuilt";

    @Override
    public String type() {
        return TYPE;
    }
}
