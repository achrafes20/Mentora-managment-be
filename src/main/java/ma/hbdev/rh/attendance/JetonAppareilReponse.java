package ma.hbdev.rh.attendance;

/** Jeton d'appareil kiosque en clair — à stocker côté client, jamais renvoyé une seconde fois. */
public record JetonAppareilReponse(String jetonAppareil) {}
