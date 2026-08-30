package com.hellovoid.liquiddock;

import java.util.List;

/** Independent config keys for user-selected widget background targets. */
public final class WidgetBackgroundUserPreferences {
    public static final String BUILTIN_RULES_KEY = "liquid_widget_background_builtin_rules";
    public static final String USER_RULES_KEY = "liquid_widget_background_user_rules";

    private WidgetBackgroundUserPreferences() {}

    public static boolean builtInRulesEnabled() {
        return ConfigReader.load().b(BUILTIN_RULES_KEY, true);
    }

    public static List<WidgetBackgroundUserRule> loadRules() {
        return WidgetBackgroundUserRuleCodec.decode(
                ConfigReader.load().s(USER_RULES_KEY, ""));
    }
}
