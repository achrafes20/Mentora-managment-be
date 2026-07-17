package ma.hbdev.rh.shared.ai;

import java.util.List;

/**
 * Contexte de matching passé au provider — primitives uniquement, jamais d'entité {@code
 * recruitment} (shared/ ne dépend d'aucune feature, ai-instructions.md règle 4/6).
 */
public record ContexteOffre(String intitule, String description, List<String> motsClesRequis) {}
