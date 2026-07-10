package ma.hbdev.rh.shared.file;

import java.util.UUID;

/** Ce que les features consommatrices reçoivent après un upload — jamais {@code cheminStockage}. */
public record FichierUploade(UUID id, String nomOriginal, String typeMime, long tailleOctets) {}
