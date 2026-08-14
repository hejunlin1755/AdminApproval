package cn.fctweb.adminapproval;

import java.util.Set;

public record CommandSettings(Set<String> dangerous, Set<String> whitelist, Set<String> blockedPatterns) {

    public CommandSettings(Set<String> dangerous, Set<String> whitelist) {
        this(dangerous, whitelist, Set.of());
    }
}
