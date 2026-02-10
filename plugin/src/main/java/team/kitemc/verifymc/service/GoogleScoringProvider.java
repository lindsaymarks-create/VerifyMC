package team.kitemc.verifymc.service;

import org.bukkit.plugin.Plugin;

public class GoogleScoringProvider extends OpenAICompatibleScoringProvider {
    public GoogleScoringProvider(Plugin plugin, LlmScoringConfig config) {
        super(plugin, config);
    }
}
