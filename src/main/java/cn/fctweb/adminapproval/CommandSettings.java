package cn.fctweb.adminapproval;

import java.util.Set;

public record CommandSettings(Set<String> dangerous, Set<String> whitelist) {
}