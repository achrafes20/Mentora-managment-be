package ma.hbdev.rh.shared.config;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigurationService {

  private final ConfigurationParametreRepository repository;

  @Transactional(readOnly = true)
  public JsonNode getJsonNode(String key) {
    return repository.findByCle(key).map(ConfigurationParametre::getValeur).orElse(null);
  }

  @Transactional(readOnly = true)
  public int getInteger(String key, int defaultValue) {
    JsonNode node = getJsonNode(key);
    if (node == null) {
      return defaultValue;
    }
    if (node.isNumber() || node.isTextual()) {
      return node.asInt(defaultValue);
    }
    return defaultValue;
  }

  @Transactional(readOnly = true)
  public String getString(String key, String defaultValue) {
    JsonNode node = getJsonNode(key);
    if (node == null) {
      return defaultValue;
    }
    return node.asText(defaultValue);
  }

  @Transactional(readOnly = true)
  public int getTentativesMax() {
    JsonNode node = getJsonNode("politique_verrouillage_compte");
    if (node != null && node.has("tentatives_max")) {
      return node.get("tentatives_max").asInt(5);
    }
    return 5;
  }

  @Transactional(readOnly = true)
  public int getDelaiDeverrouillageMinutes() {
    JsonNode node = getJsonNode("politique_verrouillage_compte");
    if (node != null && node.has("delai_deverrouillage_minutes")) {
      return node.get("delai_deverrouillage_minutes").asInt(30);
    }
    return 30;
  }

  @Transactional(readOnly = true)
  public int getDureeSessionInactiviteMinutes() {
    return getInteger("duree_session_inactivite_minutes", 30);
  }

  @Transactional(readOnly = true)
  public boolean validatePasswordStrength(String password) {
    if (password == null) {
      return false;
    }
    JsonNode node = getJsonNode("politique_mot_de_passe");
    int minLength = 10;
    boolean requireUpper = true;
    boolean requireLower = true;
    boolean requireDigit = true;

    if (node != null) {
      minLength = node.path("longueur_min").asInt(10);
      requireUpper = node.path("majuscule").asBoolean(true);
      requireLower = node.path("minuscule").asBoolean(true);
      requireDigit = node.path("chiffre").asBoolean(true);
    }

    if (password.length() < minLength) {
      return false;
    }
    if (requireUpper && !password.matches(".*[A-Z].*")) {
      return false;
    }
    if (requireLower && !password.matches(".*[a-z].*")) {
      return false;
    }
    if (requireDigit && !password.matches(".*[0-9].*")) {
      return false;
    }
    return true;
  }
}
