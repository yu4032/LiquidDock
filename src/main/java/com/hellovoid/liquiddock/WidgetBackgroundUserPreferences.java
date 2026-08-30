package com.hellovoid.liquiddock;

import com.hellovoid.liquiddock.config.ConfigSchema;

import java.util.List;

/** Config access for user-selected widget background targets. */
public final class WidgetBackgroundUserPreferences {
    public static final String BUILTIN_RULES_KEY =
            ConfigSchema.Glass.WIDGET_BACKGROUND_BUILTIN_RULES.name();
    public static final String USER_RULES_KEY =
            ConfigSchema.Glass.WIDGET_BACKGROUND_USER_RULES.name();

    private WidgetBackgroundUserPreferences() {}

    public static boolean builtInRulesEnabled() {
        return ConfigReader.load().b(
                BUILTIN_RULES_KEY,
                ConfigSchema.Glass.WIDGET_BACKGROUND_BUILTIN_RULES.runtimeFallback());
    }

    public static List<WidgetBackgroundUserRule> loadRules() {
        return WidgetBackgroundUserRuleCodec.decode(
                ConfigReader.load().s(
                        USER_RULES_KEY,
                        ConfigSchema.Glass.WIDGET_BACKGROUND_USER_RULES.runtimeFallback()));
    }
}
