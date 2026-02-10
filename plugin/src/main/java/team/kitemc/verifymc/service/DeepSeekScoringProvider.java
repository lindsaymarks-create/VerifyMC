package team.kitemc.verifymc.service;

import org.bukkit.plugin.Plugin;

public class DeepSeekScoringProvider extends OpenAICompatibleScoringProvider {
    public DeepSeekScoringProvider(Plugin plugin, LlmScoringConfig config) {
        super(plugin, config);
    }
}
