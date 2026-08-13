-- Corrige une race condition dans QrCodeService.generer() (revoquer-puis-inserer sans verrou) qui
-- pouvait laisser deux QR codes actifs pour le meme employe. Nettoyage prealable requis : on ne
-- garde actif que le QR le plus recemment genere par employe, les autres sont revoques.
UPDATE qr_codes
SET actif = false
WHERE actif = true
  AND id NOT IN (
    SELECT DISTINCT ON (employe_id) id
    FROM qr_codes
    WHERE actif = true
    ORDER BY employe_id, genere_le DESC
  );

CREATE UNIQUE INDEX idx_qr_codes_employe_actif_unique ON qr_codes(employe_id) WHERE actif = true;
