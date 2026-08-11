package ma.hbdev.rh.attendance;

import jakarta.validation.constraints.NotBlank;

/** EF-ATT-19 : lien de révocation à usage unique envoyé par e-mail. */
record RevocationParJetonRequete(@NotBlank String jeton) {}
